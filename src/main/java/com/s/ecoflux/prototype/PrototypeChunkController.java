package com.s.ecoflux.prototype;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActivePlantRecord;
import com.s.ecoflux.attachment.PlantQueueEntry;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.PlantDefinition;
import com.s.ecoflux.config.PlantSpawnRules;
import com.s.ecoflux.config.SuccessionConfigRegistry;
import com.s.ecoflux.config.SuccessionPathDefinition;
import com.s.ecoflux.init.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

public final class PrototypeChunkController {
    public static final ResourceLocation PROTOTYPE_PATH_ID = EcofluxConstants.id("plains_to_forest");
    public static final int PROTOTYPE_DAY_TICKS = 200;

    private PrototypeChunkController() {
    }

    public static ChunkAccess initializeChunkData(ChunkAccess chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<ResourceKey<Biome>> currentBiomeKey = sampleChunkCenterBiome(chunk);
        if (currentBiomeKey.isEmpty()) {
            EcofluxConstants.LOGGER.warn("Could not resolve center biome for chunk {}", chunk.getPos());
            return chunk;
        }

        ChunkClimateSample climateSample = sampleChunkClimate(chunk, currentBiomeKey.get());
        Optional<SuccessionPathDefinition> matchedPath = SuccessionConfigRegistry.findBestMatch(
                climateSample.biomeKey(),
                climateSample.temperature(),
                climateSample.downfall());

        chunkData.clearRuntimeState();
        chunkData.setCurrentBiome(climateSample.biomeKey());

        if (matchedPath.isPresent()) {
            SuccessionPathDefinition path = matchedPath.get();
            chunkData.setActivePathId(path.pathId());
            chunkData.setPreviousBiome(toBiomeKey(path.fallbackBiome()));
            chunkData.setTargetBiome(toBiomeKey(path.targetBiome()));
            chunkData.setConsumingValue(path.chunkRules().consuming());
            chunkData.setMaxPlantCount(path.chunkRules().maxPlantCount());
            chunkData.replacePlantQueue(buildInitialQueue(chunk.getPos(), path));
            EcofluxConstants.LOGGER.debug(
                    "Initialized Ecoflux chunk {} with path {} and {} queued plants",
                    chunk.getPos(),
                    path.pathId(),
                    chunkData.getPlantQueue().size());
            return chunk;
        }

        chunkData.setActivePathId(null);
        chunkData.setPreviousBiome(null);
        chunkData.setTargetBiome(null);
        chunkData.setConsumingValue(0);
        chunkData.setMaxPlantCount(0);
        chunkData.replacePlantQueue(List.of());
        EcofluxConstants.LOGGER.debug(
                "Initialized Ecoflux chunk {} without a matching succession path for biome {} (temperature={}, downfall={})",
                chunk.getPos(),
                climateSample.biomeKey().location(),
                String.format("%.3f", climateSample.temperature()),
                String.format("%.3f", climateSample.downfall()));
        return chunk;
    }

    public static String describeChunk(LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        return String.format(
                "chunk=%s currentBiome=%s activePath=%s targetBiome=%s progress=%.2f points=%d consuming=%d queue=%d activePlants=%d",
                chunk.getPos(),
                chunkData.getCurrentBiome().map(key -> key.location().toString()).orElse("unset"),
                chunkData.getActivePathId().map(ResourceLocation::toString).orElse("none"),
                chunkData.getTargetBiome().map(key -> key.location().toString()).orElse("none"),
                chunkData.getProgress(),
                chunkData.getTotalPlantPoints(),
                chunkData.getConsumingValue(),
                chunkData.getPlantQueue().size(),
                chunkData.getActivePlants().size());
    }

    public static String pruneTrackedPlants(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        int before = chunkData.getActivePlants().size();
        pruneInvalidPlants(level, chunkData, level.getGameTime());
        return "Pruned tracked plants for " + chunk.getPos() + ": removed " + (before - chunkData.getActivePlants().size()) + ".";
    }

    public static String spawnOnce(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = getActivePath(chunkData, chunk.getPos());
        if (pathOptional.isEmpty()) {
            return "Chunk " + chunk.getPos() + " has no active path.";
        }

        pruneInvalidPlants(level, chunkData, level.getGameTime());
        ensurePrototypeQueue(chunkData, pathOptional.get());
        return trySpawnPrototypePlant(level, chunk, chunkData, pathOptional.get(), level.getGameTime());
    }

    public static String evaluateNow(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = getActivePath(chunkData, chunk.getPos());
        if (pathOptional.isEmpty()) {
            return "Chunk " + chunk.getPos() + " has no active path.";
        }

        return evaluatePrototypeProgress(level, chunk, chunkData, pathOptional.get(), level.getGameTime(), true);
    }

    public static String step(ServerLevel level, LevelChunk chunk) {
        List<String> messages = new ArrayList<>();
        messages.add(pruneTrackedPlants(level, chunk));
        messages.add(spawnOnce(level, chunk));
        messages.add(evaluateNow(level, chunk));
        return String.join(" ", messages);
    }

    public static String processAutoTick(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = getActivePath(chunkData, chunk.getPos());
        if (pathOptional.isEmpty()) {
            return "Auto tick skipped for " + chunk.getPos() + ": no active path.";
        }

        SuccessionPathDefinition path = pathOptional.get();
        long gameTime = level.getGameTime();
        if (gameTime % path.chunkRules().processingIntervalTicks() != 0L) {
            return "Auto tick skipped for " + chunk.getPos() + ": waiting for processing interval.";
        }

        List<String> messages = new ArrayList<>();
        messages.add(pruneTrackedPlants(level, chunk));
        messages.add(spawnOnce(level, chunk));
        messages.add(evaluatePrototypeProgress(level, chunk, chunkData, path, gameTime, false));
        return String.join(" ", messages);
    }

    public static String forceTransition(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        return applyPrototypeTransition(level, chunk, chunkData);
    }

    public static boolean isPrototypeChunk(SuccessionChunkData chunkData) {
        return chunkData.getActivePathId().filter(PROTOTYPE_PATH_ID::equals).isPresent();
    }

    private static Optional<SuccessionPathDefinition> getActivePath(SuccessionChunkData chunkData, ChunkPos chunkPos) {
        Optional<ResourceLocation> activePathId = chunkData.getActivePathId();
        if (activePathId.isEmpty()) {
            return Optional.empty();
        }

        Optional<SuccessionPathDefinition> pathOptional = SuccessionConfigRegistry.getPath(activePathId.get());
        if (pathOptional.isEmpty()) {
            EcofluxConstants.LOGGER.warn("Prototype path {} disappeared while chunk {} was loaded", activePathId.get(), chunkPos);
        }
        return pathOptional;
    }

    private static void ensurePrototypeQueue(SuccessionChunkData chunkData, SuccessionPathDefinition path) {
        if (!chunkData.getPlantQueue().isEmpty() || chunkData.getCurrentPlantCount() >= chunkData.getMaxPlantCount()) {
            return;
        }

        PlantDefinition prototypePlant = getPrototypePlant(path);
        chunkData.enqueuePlant(toQueueEntry(prototypePlant));
    }

    private static String trySpawnPrototypePlant(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            SuccessionPathDefinition path,
            long gameTime) {
        if (chunkData.getCurrentPlantCount() >= chunkData.getMaxPlantCount()) {
            return "Spawn skipped for " + chunk.getPos() + ": active plant cap already reached.";
        }

        Optional<PlantQueueEntry> nextEntry = chunkData.pollPlant();
        if (nextEntry.isEmpty()) {
            return "Spawn skipped for " + chunk.getPos() + ": queue is empty.";
        }

        PlantDefinition prototypePlant = getPrototypePlant(path);
        Optional<BlockPos> spawnPos = findSpawnPos(level, chunk, chunkData, prototypePlant, gameTime);
        if (spawnPos.isEmpty()) {
            EcofluxConstants.LOGGER.debug(
                    "Prototype spawn skipped for chunk {} because no valid position was found for {}",
                    chunk.getPos(),
                    nextEntry.get().plantId());
            chunkData.enqueuePlant(nextEntry.get());
            return "Spawn skipped for " + chunk.getPos() + ": no valid position for " + nextEntry.get().plantId() + ".";
        }

        Block block = BuiltInRegistries.BLOCK.getOptional(nextEntry.get().plantId()).orElse(null);
        if (block == null) {
            EcofluxConstants.LOGGER.warn("Prototype plant {} is not a registered block", nextEntry.get().plantId());
            return "Spawn failed for " + chunk.getPos() + ": block " + nextEntry.get().plantId() + " is not registered.";
        }

        BlockState state = block.defaultBlockState();
        BlockPos pos = spawnPos.get();
        if (!state.canSurvive(level, pos) || !level.setBlock(pos, state, Block.UPDATE_ALL)) {
            chunkData.enqueuePlant(nextEntry.get());
            return "Spawn failed for " + chunk.getPos() + ": world rejected placement at " + pos + ".";
        }

        chunkData.trackPlant(new ActivePlantRecord(
                nextEntry.get().plantId(),
                pos.immutable(),
                nextEntry.get().pointValue(),
                gameTime,
                gameTime + nextEntry.get().maxAgeTicks(),
                chunkData.getCurrentBiome().map(ResourceKey::location).orElse(null)));

        EcofluxConstants.LOGGER.info(
                "Prototype planted {} in chunk {} at {}",
                nextEntry.get().plantId(),
                chunk.getPos(),
                pos);
        return "Planted " + nextEntry.get().plantId() + " in " + chunk.getPos() + " at " + pos + ".";
    }

    private static void pruneInvalidPlants(ServerLevel level, SuccessionChunkData chunkData, long gameTime) {
        List<ActivePlantRecord> snapshot = List.copyOf(chunkData.getActivePlants().values());
        for (ActivePlantRecord record : snapshot) {
            BlockState state = level.getBlockState(record.position());
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            boolean expired = gameTime >= record.expireGameTime();
            boolean missing = state.isAir() || !record.plantId().equals(blockId);
            if (!expired && !missing) {
                continue;
            }

            if (expired && !state.isAir()) {
                level.removeBlock(record.position(), false);
            }

            chunkData.removeTrackedPlant(record.position());
        }
    }

    private static String evaluatePrototypeProgress(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            SuccessionPathDefinition path,
            long gameTime,
            boolean ignoreInterval) {
        int evaluationInterval = path.chunkRules().resolvedEvaluationIntervalTicks(PROTOTYPE_DAY_TICKS);
        if (!ignoreInterval && gameTime - chunkData.getLastEvaluationGameTime() < evaluationInterval) {
            return "Evaluation skipped for " + chunk.getPos() + ": waiting for interval.";
        }

        int totalPlantPoints = chunkData.getTotalPlantPoints();
        double delta = totalPlantPoints >= chunkData.getConsumingValue()
                ? path.chunkRules().positiveProgressStep()
                : -path.chunkRules().negativeProgressStep();
        double nextProgress = Mth.clamp(chunkData.getProgress() + delta, -1.0D, 1.0D);
        chunkData.setLastEvaluationGameTime(gameTime);
        chunkData.setProgress(nextProgress);

        EcofluxConstants.LOGGER.info(
                "Prototype progress chunk={} points={} consuming={} progress={}",
                chunk.getPos(),
                totalPlantPoints,
                chunkData.getConsumingValue(),
                String.format("%.2f", nextProgress));

        if (nextProgress >= 1.0D) {
            return "Evaluated " + chunk.getPos() + ": " + applyPrototypeTransition(level, chunk, chunkData);
        }

        return String.format(
                "Evaluated %s: points=%d consuming=%d progress=%.2f.",
                chunk.getPos(),
                totalPlantPoints,
                chunkData.getConsumingValue(),
                nextProgress);
    }

    private static String applyPrototypeTransition(ServerLevel level, LevelChunk chunk, SuccessionChunkData chunkData) {
        Optional<ResourceKey<Biome>> targetBiome = chunkData.getTargetBiome();
        if (targetBiome.isEmpty()) {
            return "Transition skipped for " + chunk.getPos() + ": no target biome.";
        }

        Holder<Biome> biomeHolder = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(targetBiome.get());
        chunk.fillBiomesFromNoise((x, y, z, sampler) -> biomeHolder, level.getChunkSource().randomState().sampler());
        chunk.setUnsaved(true);
        level.getServer()
                .getPlayerList()
                .broadcastAll(ClientboundChunksBiomesPacket.forChunks(List.of(chunk)), level.dimension());

        ResourceKey<Biome> oldBiome = chunkData.getCurrentBiome().orElse(null);
        chunkData.setPreviousBiome(oldBiome);
        chunkData.setCurrentBiome(targetBiome.get());
        chunkData.setActivePathId(null);
        chunkData.setTargetBiome(null);
        chunkData.setConsumingValue(0);
        chunkData.setMaxPlantCount(0);
        chunkData.setProgress(0.0D);
        chunkData.setLastEvaluationGameTime(level.getGameTime());
        chunkData.replacePlantQueue(List.of());
        chunkData.clearTrackedPlants();

        EcofluxConstants.LOGGER.info(
                "Prototype succession completed for chunk {}: {} -> {}",
                chunk.getPos(),
                oldBiome == null ? "unknown" : oldBiome.location(),
                targetBiome.get().location());
        return "Transitioned " + chunk.getPos() + " from "
                + (oldBiome == null ? "unknown" : oldBiome.location())
                + " to " + targetBiome.get().location() + ".";
    }

    private static Optional<BlockPos> findSpawnPos(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            PlantDefinition plant,
            long gameTime) {
        PlantSpawnRules spawnRules = plant.spawnRules();
        Block block = BuiltInRegistries.BLOCK.getOptional(plant.plantId()).orElse(null);
        if (block == null) {
            return Optional.empty();
        }

        Random random = new Random(chunk.getPos().toLong() ^ gameTime);
        int startIndex = random.nextInt(16 * 16);
        for (int offset = 0; offset < 16 * 16; offset++) {
            int index = (startIndex + offset) % (16 * 16);
            int localX = index & 15;
            int localZ = index >> 4;
            int worldX = chunk.getPos().getBlockX(localX);
            int worldZ = chunk.getPos().getBlockZ(localZ);
            Optional<BlockPos> placePos = findSpawnPosAtColumn(level, chunkData, block, spawnRules, worldX, worldZ);
            if (placePos.isPresent()) {
                return placePos;
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findSpawnPosAtColumn(
            ServerLevel level,
            SuccessionChunkData chunkData,
            Block block,
            PlantSpawnRules spawnRules,
            int worldX,
            int worldZ) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
        for (int y = surfaceY - 1; y <= surfaceY + 2; y++) {
            BlockPos placePos = new BlockPos(worldX, y, worldZ);
            if (canPlantAt(level, chunkData, block, spawnRules, placePos)) {
                return Optional.of(placePos);
            }
        }

        return Optional.empty();
    }

    private static boolean canPlantAt(
            ServerLevel level,
            SuccessionChunkData chunkData,
            Block block,
            PlantSpawnRules spawnRules,
            BlockPos placePos) {
        if (!level.isEmptyBlock(placePos)) {
            return false;
        }
        if (spawnRules.requireSky() && !level.canSeeSky(placePos)) {
            return false;
        }
        if (!isAllowedBaseBlock(level.getBlockState(placePos.below()), spawnRules)) {
            return false;
        }
        if (countNearbyTrackedPlants(chunkData, placePos, 4) >= spawnRules.maxLocalDensity()) {
            return false;
        }

        return block.defaultBlockState().canSurvive(level, placePos);
    }

    private static boolean isAllowedBaseBlock(BlockState baseState, PlantSpawnRules spawnRules) {
        ResourceLocation baseBlockId = BuiltInRegistries.BLOCK.getKey(baseState.getBlock());
        return spawnRules.allowedBaseBlocks().isEmpty() || spawnRules.allowedBaseBlocks().contains(baseBlockId);
    }

    private static int countNearbyTrackedPlants(SuccessionChunkData chunkData, BlockPos center, int radius) {
        int radiusSquared = radius * radius;
        int count = 0;
        for (ActivePlantRecord record : chunkData.getActivePlants().values()) {
            if (record.position().distSqr(center) <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    private static Optional<ResourceKey<Biome>> sampleChunkCenterBiome(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int sampleY = sampleSurfaceY(chunk);
        ResourceKey<Biome> biomeKey = chunk.getNoiseBiome(
                        QuartPos.fromBlock(chunkPos.getMiddleBlockX()),
                        QuartPos.fromBlock(sampleY),
                        QuartPos.fromBlock(chunkPos.getMiddleBlockZ()))
                .unwrapKey()
                .orElse(null);
        return Optional.ofNullable(biomeKey);
    }

    private static ChunkClimateSample sampleChunkClimate(ChunkAccess chunk, ResourceKey<Biome> biomeKey) {
        ChunkPos chunkPos = chunk.getPos();
        int sampleY = sampleSurfaceY(chunk);
        BlockPos samplePos = chunkPos.getMiddleBlockPosition(sampleY);
        Biome biome = chunk.getNoiseBiome(
                        QuartPos.fromBlock(samplePos.getX()),
                        QuartPos.fromBlock(samplePos.getY()),
                        QuartPos.fromBlock(samplePos.getZ()))
                .value();
        return new ChunkClimateSample(
                biomeKey,
                biome.getBaseTemperature(),
                biome.getModifiedClimateSettings().downfall());
    }

    private static int sampleSurfaceY(ChunkAccess chunk) {
        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        return Math.max(minY, Math.min(surface, maxY));
    }

    private static @Nullable ResourceKey<Biome> toBiomeKey(@Nullable ResourceLocation biomeId) {
        return biomeId == null ? null : ResourceKey.create(Registries.BIOME, biomeId);
    }

    private static List<PlantQueueEntry> buildInitialQueue(ChunkPos chunkPos, SuccessionPathDefinition path) {
        if (PROTOTYPE_PATH_ID.equals(path.pathId())) {
            return buildPrototypeQueue(path);
        }

        int queueCapacity = path.chunkRules().queueCapacity();
        int totalWeight = path.plants().stream().mapToInt(PlantDefinition::weight).sum();
        Random random = new Random(chunkPos.toLong());
        List<PlantQueueEntry> queue = new ArrayList<>(queueCapacity);
        for (int i = 0; i < queueCapacity; i++) {
            PlantDefinition plant = pickWeightedPlant(path.plants(), totalWeight, random);
            queue.add(toQueueEntry(plant));
        }
        return List.copyOf(queue);
    }

    private static List<PlantQueueEntry> buildPrototypeQueue(SuccessionPathDefinition path) {
        PlantQueueEntry entry = toQueueEntry(getPrototypePlant(path));
        int queueCapacity = Math.max(1, path.chunkRules().queueCapacity());
        List<PlantQueueEntry> queue = new ArrayList<>(queueCapacity);
        for (int i = 0; i < queueCapacity; i++) {
            queue.add(entry);
        }
        return List.copyOf(queue);
    }

    private static PlantDefinition getPrototypePlant(SuccessionPathDefinition path) {
        return path.plants().get(0);
    }

    private static PlantQueueEntry toQueueEntry(PlantDefinition plant) {
        return new PlantQueueEntry(plant.plantId(), plant.pointValue(), plant.weight(), plant.maxAgeTicks());
    }

    private static PlantDefinition pickWeightedPlant(List<PlantDefinition> plants, int totalWeight, Random random) {
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (PlantDefinition plant : plants) {
            cursor += plant.weight();
            if (roll < cursor) {
                return plant;
            }
        }
        return plants.get(plants.size() - 1);
    }

    private record ChunkClimateSample(ResourceKey<Biome> biomeKey, double temperature, double downfall) {
    }
}
