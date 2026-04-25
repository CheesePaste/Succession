package com.s.ecoflux.prototype;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActivePlantRecord;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.PlantQueueEntry;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.PlantDefinition;
import com.s.ecoflux.config.PlantSpawnRules;
import com.s.ecoflux.config.SuccessionConfigRegistry;
import com.s.ecoflux.config.SuccessionPathDefinition;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.init.ModChunkEvents;
import com.s.ecoflux.plant.VegetationLifecycleStage;
import com.s.ecoflux.plant.VegetationTracker;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.Nullable;

public final class PrototypeChunkController {
    public static final ResourceLocation PROTOTYPE_PATH_ID = EcofluxConstants.id("plains_to_forest");
    public static final int PROTOTYPE_DAY_TICKS = 24000;
    private static final int ACCELERATED_SYNC_INTERVAL_TICKS = 5;
    private static final int ACCELERATED_PLANT_INTERVAL_TICKS = 12;
    private static final long SIMPLE_PLANT_BORN_TICKS = 200L;
    private static final long SIMPLE_PLANT_GROWING_START_TICKS = 200L;
    private static final long SIMPLE_PLANT_MATURE_START_TICKS = 1200L;
    private static final long SIMPLE_PLANT_AGING_START_TICKS = 48000L;
    private static final long SIMPLE_PLANT_EXPIRE_TICKS = 72000L;

    private PrototypeChunkController() {
    }

    public static ChunkAccess initializeChunkData(ChunkAccess chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<ResourceKey<Biome>> currentBiomeKey = sampleChunkCenterBiome(chunk);
        if (currentBiomeKey.isEmpty()) {
            EcofluxConstants.LOGGER.warn("无法解析区块 {} 中心点的群系", chunk.getPos());
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
                    "已初始化 Ecoflux 区块 {}：路径={}，植物队列={} 个",
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
                "已初始化 Ecoflux 区块 {}：群系 {} 没有匹配的演替路径（温度={}，降水={}）",
                chunk.getPos(),
                climateSample.biomeKey().location(),
                String.format("%.3f", climateSample.temperature()),
                String.format("%.3f", climateSample.downfall()));
        return chunk;
    }

    public static String describeChunk(LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        return String.format(
                "区块=%s 当前群系=%s 演替路径=%s 目标群系=%s 进度=%.2f 原型植物积分=%d 植被积分=%d 消耗阈值=%d 队列=%d 原型植物=%d 已追踪植被=%d",
                chunk.getPos(),
                chunkData.getCurrentBiome().map(key -> key.location().toString()).orElse("未设置"),
                chunkData.getActivePathId().map(ResourceLocation::toString).orElse("无"),
                chunkData.getTargetBiome().map(key -> key.location().toString()).orElse("无"),
                chunkData.getProgress(),
                chunkData.getTotalPlantPoints(),
                chunkData.getTotalVegetationPoints(),
                chunkData.getConsumingValue(),
                chunkData.getPlantQueue().size(),
                chunkData.getActivePlants().size(),
                chunkData.getVegetationRecords().size());
    }

    public static String pruneTrackedPlants(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        int before = chunkData.getActivePlants().size();
        pruneInvalidPlants(level, chunkData, level.getGameTime());
        return "已清理区块 " + chunk.getPos() + " 的原型植物：移除 " + (before - chunkData.getActivePlants().size()) + " 个。";
    }

    public static String spawnOnce(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = getActivePath(chunkData, chunk.getPos());
        if (pathOptional.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有激活的演替路径。";
        }

        pruneInvalidPlants(level, chunkData, level.getGameTime());
        ensurePrototypeQueue(chunkData, pathOptional.get());
        return trySpawnPrototypePlant(level, chunk, chunkData, pathOptional.get(), level.getGameTime());
    }

    public static String evaluateNow(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = getActivePath(chunkData, chunk.getPos());
        if (pathOptional.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有激活的演替路径。";
        }

        return evaluatePrototypeProgress(level, chunk, chunkData, pathOptional.get(), level.getGameTime(), true);
    }

    public static String step(ServerLevel level, LevelChunk chunk) {
        List<String> messages = new ArrayList<>();
        messages.add(pruneTrackedPlants(level, chunk));
        messages.add(spawnOnce(level, chunk));
        messages.add(VegetationTracker.INSTANCE.observeChunk(level, chunk));
        messages.add(evaluateNow(level, chunk));
        return String.join(" ", messages);
    }

    public static String processAutoTick(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = getActivePath(chunkData, chunk.getPos());
        if (pathOptional.isEmpty()) {
            return "自动演替跳过区块 " + chunk.getPos() + "：没有激活的演替路径。";
        }

        SuccessionPathDefinition path = pathOptional.get();
        long gameTime = level.getGameTime();
        if (gameTime % path.chunkRules().processingIntervalTicks() != 0L) {
            return "自动演替跳过区块 " + chunk.getPos() + "：等待处理间隔。";
        }

        List<String> messages = new ArrayList<>();
        messages.add(pruneTrackedPlants(level, chunk));
        messages.add(spawnOnce(level, chunk));
        messages.add(VegetationTracker.INSTANCE.observeChunk(level, chunk));
        messages.add(evaluatePrototypeProgress(level, chunk, chunkData, path, gameTime, false));
        return String.join(" ", messages);
    }

    public static String forceTransition(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        return applyPrototypeTransition(level, chunk, chunkData);
    }

    public static String accelerate(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getCurrentBiome().isEmpty()) {
            initializeChunkData(chunk);
        }

        Optional<SuccessionPathDefinition> pathOptional = getActivePath(chunkData, chunk.getPos());
        if (pathOptional.isEmpty()) {
            return "区块 " + chunk.getPos() + " 没有可加速的演替路径。";
        }

        pruneInvalidPlants(level, chunkData, level.getGameTime());
        ensurePrototypeQueue(chunkData, pathOptional.get());
        if (chunkData.getVegetationRecords().isEmpty()) {
            fillPrototypePlants(level, chunk, chunkData, pathOptional.get(), 2, 2);
        }

        if (chunkData.getVegetationRecords().isEmpty()) {
            return "区块 " + chunk.getPos() + " 无法启动加速：没有可追踪的小草。";
        }

        chunkData.setProgress(0.0D);
        setAcceleratedVegetationStage(chunkData, level.getGameTime(), 0.0D);
        com.s.ecoflux.network.ModNetworking.syncChunkToTracking(level, chunk);
        ModChunkEvents.startAcceleratedTransition(level, chunk);
        return "已启动区块 " + chunk.getPos() + " 的 10 秒完整加速演替：小草会依次经历出生、生长、成熟、衰老，然后转化为森林。";
    }

    public static boolean processAcceleratedTick(ServerLevel level, LevelChunk chunk, long startGameTime, int durationTicks) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<SuccessionPathDefinition> pathOptional = getActivePath(chunkData, chunk.getPos());
        if (pathOptional.isEmpty()) {
            return true;
        }

        long elapsedTicks = Math.max(0L, level.getGameTime() - startGameTime);
        double progress = Mth.clamp((double) elapsedTicks / (double) Math.max(1, durationTicks), 0.0D, 1.0D);
        if (chunkData.getVegetationRecords().isEmpty()) {
            ensurePrototypeQueue(chunkData, pathOptional.get());
            fillPrototypePlants(level, chunk, chunkData, pathOptional.get(), 2, 1);
        }

        int targetPlantCount = acceleratedTargetPlantCount(chunkData, progress);
        if (elapsedTicks % ACCELERATED_PLANT_INTERVAL_TICKS == 0L) {
            fillPrototypePlants(level, chunk, chunkData, pathOptional.get(), targetPlantCount, 1);
        }
        setAcceleratedVegetationStage(chunkData, level.getGameTime(), progress);
        chunkData.setProgress(Math.min(0.99D, progress));
        if (elapsedTicks % ACCELERATED_SYNC_INTERVAL_TICKS == 0L) {
            com.s.ecoflux.network.ModNetworking.syncChunkToTracking(level, chunk);
        }

        if (progress < 1.0D) {
            return false;
        }

        chunkData.setProgress(0.0D);
        chunkData.setProgress(1.0D);
        com.s.ecoflux.network.ModNetworking.syncChunkToTracking(level, chunk);
        applyPrototypeTransition(level, chunk, chunkData);
        return true;
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
            EcofluxConstants.LOGGER.warn("区块 {} 加载期间，原型演替路径 {} 已不存在", chunkPos, activePathId.get());
        }
        return pathOptional;
    }

    private static void ensurePrototypeQueue(SuccessionChunkData chunkData, SuccessionPathDefinition path) {
        if (!chunkData.getPlantQueue().isEmpty() || chunkData.getCurrentPlantCount() >= chunkData.getMaxPlantCount()) {
            return;
        }

        chunkData.replacePlantQueue(buildWeightedQueue(path, chunkData.getMaxPlantCount(), new Random()));
    }

    private static void fillPrototypePlants(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            SuccessionPathDefinition path,
            int targetPlantCount) {
        fillPrototypePlants(level, chunk, chunkData, path, targetPlantCount, Integer.MAX_VALUE);
    }

    private static void fillPrototypePlants(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            SuccessionPathDefinition path,
            int targetPlantCount,
            int maxNewPlants) {
        int clampedTarget = Mth.clamp(targetPlantCount, 0, chunkData.getMaxPlantCount());
        int guard = Math.max(8, clampedTarget * 4);
        int planted = 0;
        while (chunkData.getCurrentPlantCount() < clampedTarget && planted < maxNewPlants && guard-- > 0) {
            ensurePrototypeQueue(chunkData, path);
            int before = chunkData.getCurrentPlantCount();
            trySpawnPrototypePlant(level, chunk, chunkData, path, level.getGameTime() + guard);
            if (chunkData.getCurrentPlantCount() > before) {
                planted++;
            }
            if (chunkData.getCurrentPlantCount() == before && chunkData.getPlantQueue().isEmpty()) {
                break;
            }
        }
    }

    private static String trySpawnPrototypePlant(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            SuccessionPathDefinition path,
            long gameTime) {
        if (chunkData.getCurrentPlantCount() >= chunkData.getMaxPlantCount()) {
            return "区块 " + chunk.getPos() + " 跳过生成：原型植物数量已达上限。";
        }

        Optional<PlantQueueEntry> nextEntry = chunkData.pollPlant();
        if (nextEntry.isEmpty()) {
            return "区块 " + chunk.getPos() + " 跳过生成：植物队列为空。";
        }

        Optional<PlantDefinition> plantDefinition = findPlantDefinition(path, nextEntry.get().plantId());
        if (plantDefinition.isEmpty()) {
            return "区块 " + chunk.getPos() + " 生成失败：路径中找不到植物 " + nextEntry.get().plantId() + "。";
        }

        Optional<BlockPos> spawnPos = findSpawnPos(level, chunk, chunkData, plantDefinition.get(), gameTime);
        if (spawnPos.isEmpty()) {
            EcofluxConstants.LOGGER.debug(
                    "区块 {} 跳过原型植物生成：找不到 {} 的合法位置",
                    chunk.getPos(),
                    nextEntry.get().plantId());
            chunkData.enqueuePlant(nextEntry.get());
            return "区块 " + chunk.getPos() + " 跳过生成：找不到 " + nextEntry.get().plantId() + " 的合法位置。";
        }

        Block block = BuiltInRegistries.BLOCK.getOptional(nextEntry.get().plantId()).orElse(null);
        if (block == null) {
            EcofluxConstants.LOGGER.warn("原型植物 {} 不是已注册方块", nextEntry.get().plantId());
            return "区块 " + chunk.getPos() + " 生成失败：方块 " + nextEntry.get().plantId() + " 未注册。";
        }

        BlockState state = block.defaultBlockState();
        BlockPos pos = spawnPos.get();
        if (!state.canSurvive(level, pos) || !level.setBlock(pos, state, Block.UPDATE_ALL)) {
            chunkData.enqueuePlant(nextEntry.get());
            return "区块 " + chunk.getPos() + " 生成失败：世界拒绝在 " + pos + " 放置。";
        }

        chunkData.trackPlant(new ActivePlantRecord(
                nextEntry.get().plantId(),
                pos.immutable(),
                nextEntry.get().pointValue(),
                gameTime,
                gameTime + nextEntry.get().maxAgeTicks(),
                chunkData.getCurrentBiome().map(ResourceKey::location).orElse(null)));
        VegetationTracker.INSTANCE.trackAt(
                level,
                chunk,
                pos,
                chunkData.getCurrentBiome().map(ResourceKey::location),
                chunkData.getActivePathId());

        EcofluxConstants.LOGGER.debug(
                "已在区块 {} 的 {} 种下原型植物 {}",
                chunk.getPos(),
                pos,
                nextEntry.get().plantId());
        return "已在区块 " + chunk.getPos() + " 的 " + pos + " 种下 " + nextEntry.get().plantId() + "。";
    }

    private static void setAcceleratedVegetationStage(SuccessionChunkData chunkData, long gameTime, double totalProgress) {
        AcceleratedVisualStage visualStage = acceleratedVisualStage(totalProgress);
        long syntheticAge = visualStage.syntheticAge();
        long syntheticBirthTime = Math.max(0L, gameTime - syntheticAge);
        List<ActivePlantRecord> plantSnapshot = List.copyOf(chunkData.getActivePlants().values());
        for (ActivePlantRecord record : plantSnapshot) {
            chunkData.trackPlant(new ActivePlantRecord(
                    record.plantId(),
                    record.position(),
                    record.pointValue(),
                    syntheticBirthTime,
                    syntheticBirthTime + SIMPLE_PLANT_EXPIRE_TICKS,
                    record.sourceBiomeId()));
        }

        List<ActiveVegetationRecord> vegetationSnapshot = List.copyOf(chunkData.getVegetationRecords().values());
        for (ActiveVegetationRecord record : vegetationSnapshot) {
            chunkData.trackVegetation(new ActiveVegetationRecord(
                    record.vegetationId(),
                    record.adapterType(),
                    record.category(),
                    record.position(),
                    visualStage.stage(),
                    syntheticBirthTime,
                    gameTime,
                    syntheticBirthTime + SIMPLE_PLANT_EXPIRE_TICKS,
                    record.basePointValue(),
                    visualStage.pointValue(record.basePointValue()),
                    record.sourceBiomeId(),
                    record.sourcePathId()));
        }
    }

    private static AcceleratedVisualStage acceleratedVisualStage(double totalProgress) {
        if (totalProgress < 0.20D) {
            double localProgress = totalProgress / 0.20D;
            return new AcceleratedVisualStage(
                    VegetationLifecycleStage.BORN,
                    Math.round(localProgress * (SIMPLE_PLANT_BORN_TICKS - 1L)),
                    0);
        }
        if (totalProgress < 0.50D) {
            double localProgress = (totalProgress - 0.20D) / 0.30D;
            long age = SIMPLE_PLANT_GROWING_START_TICKS
                    + Math.round(localProgress * (SIMPLE_PLANT_MATURE_START_TICKS - SIMPLE_PLANT_GROWING_START_TICKS - 1L));
            return new AcceleratedVisualStage(VegetationLifecycleStage.GROWING, age, 1);
        }
        if (totalProgress < 0.80D) {
            double localProgress = (totalProgress - 0.50D) / 0.30D;
            long age = SIMPLE_PLANT_MATURE_START_TICKS
                    + Math.round(localProgress * (SIMPLE_PLANT_AGING_START_TICKS - SIMPLE_PLANT_MATURE_START_TICKS - 1L));
            return new AcceleratedVisualStage(VegetationLifecycleStage.MATURE, age, 2);
        }

        double localProgress = (totalProgress - 0.80D) / 0.20D;
        long age = SIMPLE_PLANT_AGING_START_TICKS
                + Math.round(localProgress * (SIMPLE_PLANT_EXPIRE_TICKS - SIMPLE_PLANT_AGING_START_TICKS - 1L));
        return new AcceleratedVisualStage(VegetationLifecycleStage.AGING, age, 1);
    }

    private static int acceleratedTargetPlantCount(SuccessionChunkData chunkData, double totalProgress) {
        int maxPlantCount = chunkData.getMaxPlantCount();
        if (totalProgress < 0.20D) {
            return Math.max(1, maxPlantCount / 4);
        }
        if (totalProgress < 0.45D) {
            return Math.max(2, maxPlantCount / 2);
        }
        if (totalProgress < 0.70D) {
            return Math.max(3, (maxPlantCount * 3) / 4);
        }
        return maxPlantCount;
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
            chunkData.removeVegetation(record.position());
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
            return "区块 " + chunk.getPos() + " 跳过评估：等待评估间隔。";
        }

        int totalVegetationPoints = chunkData.getTotalVegetationPoints();
        boolean hasAgingVegetation = hasAgingVegetation(chunkData);
        if (!hasAgingVegetation) {
            chunkData.setLastEvaluationGameTime(gameTime);
            EcofluxConstants.LOGGER.info(
                    "区块 {} 演替评估：植被积分={}，消耗阈值={}，进度={}，状态=等待植被衰老",
                    chunk.getPos(),
                    totalVegetationPoints,
                    chunkData.getConsumingValue(),
                    String.format("%.2f", chunkData.getProgress()));
            return String.format(
                    "已评估区块 %s：植被尚未衰老，进度保持 %.2f。",
                    chunk.getPos(),
                    chunkData.getProgress());
        }

        double delta = totalVegetationPoints >= chunkData.getConsumingValue()
                ? path.chunkRules().positiveProgressStep()
                : -path.chunkRules().negativeProgressStep();
        double nextProgress = Mth.clamp(chunkData.getProgress() + delta, -1.0D, 1.0D);
        chunkData.setLastEvaluationGameTime(gameTime);
        chunkData.setProgress(nextProgress);

        EcofluxConstants.LOGGER.info(
                "区块 {} 演替评估：植被积分={}，消耗阈值={}，进度={}",
                chunk.getPos(),
                totalVegetationPoints,
                chunkData.getConsumingValue(),
                String.format("%.2f", nextProgress));

        if (nextProgress >= 1.0D) {
            return "已评估区块 " + chunk.getPos() + "：" + applyPrototypeTransition(level, chunk, chunkData);
        }

        return String.format(
                "已评估区块 %s：植被积分=%d，消耗阈值=%d，进度=%.2f。",
                chunk.getPos(),
                totalVegetationPoints,
                chunkData.getConsumingValue(),
                nextProgress);
    }

    private static boolean hasAgingVegetation(SuccessionChunkData chunkData) {
        return chunkData.getVegetationRecords().values().stream()
                .anyMatch(record -> record.lifeStage() == VegetationLifecycleStage.AGING);
    }

    private static String applyPrototypeTransition(ServerLevel level, LevelChunk chunk, SuccessionChunkData chunkData) {
        Optional<ResourceKey<Biome>> targetBiome = chunkData.getTargetBiome();
        if (targetBiome.isEmpty()) {
            return "区块 " + chunk.getPos() + " 跳过群系转化：没有目标群系。";
        }

        Holder<Biome> biomeHolder = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(targetBiome.get());
        chunk.fillBiomesFromNoise((x, y, z, sampler) -> biomeHolder, level.getChunkSource().randomState().sampler());
        chunk.setUnsaved(true);
        level.getServer()
                .getPlayerList()
                .broadcastAll(ClientboundChunksBiomesPacket.forChunks(List.of(chunk)), level.dimension());
        int plantedTrees = plantForestTrees(level, chunk, level.getGameTime());

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
        chunkData.clearVegetationRecords();
        com.s.ecoflux.network.ModNetworking.syncChunkToTracking(level, chunk);

        EcofluxConstants.LOGGER.info(
                "区块 {} 原型演替完成：{} -> {}，生成树木 {} 棵",
                chunk.getPos(),
                oldBiome == null ? "未知" : oldBiome.location(),
                targetBiome.get().location(),
                plantedTrees);
        return "区块 " + chunk.getPos() + " 已从 "
                + (oldBiome == null ? "未知" : oldBiome.location())
                + " 转化为 " + targetBiome.get().location()
                + "，生成树木 " + plantedTrees + " 棵。";
    }

    private static int plantForestTrees(ServerLevel level, LevelChunk chunk, long gameTime) {
        Random random = new Random(chunk.getPos().toLong() ^ gameTime ^ 0x5DEECE66DL);
        int targetCount = 5 + random.nextInt(4);
        int planted = 0;
        for (int attempts = 0; attempts < 72 && planted < targetCount; attempts++) {
            int localX = 2 + random.nextInt(12);
            int localZ = 2 + random.nextInt(12);
            int worldX = chunk.getPos().getBlockX(localX);
            int worldZ = chunk.getPos().getBlockZ(localZ);
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
            BlockPos basePos = new BlockPos(worldX, surfaceY, worldZ);
            if (placeSimpleTree(level, basePos, random, randomTreeBlocks(random))) {
                planted++;
            }
        }
        return planted;
    }

    private static TreeBlocks randomTreeBlocks(Random random) {
        return random.nextBoolean()
                ? new TreeBlocks(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState())
                : new TreeBlocks(Blocks.BIRCH_LOG.defaultBlockState(), Blocks.BIRCH_LEAVES.defaultBlockState());
    }

    private static boolean placeSimpleTree(ServerLevel level, BlockPos basePos, Random random, TreeBlocks treeBlocks) {
        BlockState ground = level.getBlockState(basePos.below());
        if (!ground.is(Blocks.GRASS_BLOCK) && !ground.is(Blocks.DIRT) && !ground.is(Blocks.PODZOL)) {
            return false;
        }

        int height = 4 + random.nextInt(2);
        if (!hasTreeSpace(level, basePos, height)) {
            return false;
        }

        for (int y = 0; y < height; y++) {
            level.setBlock(basePos.above(y), treeBlocks.log(), Block.UPDATE_ALL);
        }

        int leafStart = height - 2;
        int leafEnd = height + 1;
        for (int y = leafStart; y <= leafEnd; y++) {
            int radius = y >= height ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && (y >= height || random.nextBoolean())) {
                        continue;
                    }
                    BlockPos leafPos = basePos.offset(dx, y, dz);
                    if (canReplaceForTree(level.getBlockState(leafPos))) {
                        level.setBlock(leafPos, treeBlocks.leaves(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasTreeSpace(ServerLevel level, BlockPos basePos, int height) {
        for (int y = 0; y <= height + 1; y++) {
            int radius = y < height - 2 ? 0 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!canReplaceForTree(level.getBlockState(basePos.offset(dx, y, dz)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean canReplaceForTree(BlockState state) {
        return state.isAir()
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.LARGE_FERN);
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

        long seed = chunk.getPos().toLong()
                ^ (gameTime * 0x9E3779B97F4A7C15L)
                ^ ((long) plant.plantId().hashCode() << 32)
                ^ ((long) chunkData.getCurrentPlantCount() * 0xBF58476D1CE4E5B9L);
        Random random = new Random(seed);
        for (int attempt = 0; attempt < 64; attempt++) {
            int localX = random.nextInt(16);
            int localZ = random.nextInt(16);
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
        int queueCapacity = path.chunkRules().queueCapacity();
        int totalWeight = path.plants().stream().mapToInt(PlantDefinition::weight).sum();
        Random random = new Random(chunkPos.toLong());
        return buildWeightedQueue(path, queueCapacity, random, totalWeight);
    }

    private static List<PlantQueueEntry> buildWeightedQueue(SuccessionPathDefinition path, int queueCapacity, Random random) {
        int totalWeight = path.plants().stream().mapToInt(PlantDefinition::weight).sum();
        return buildWeightedQueue(path, queueCapacity, random, totalWeight);
    }

    private static List<PlantQueueEntry> buildWeightedQueue(
            SuccessionPathDefinition path,
            int queueCapacity,
            Random random,
            int totalWeight) {
        List<PlantQueueEntry> queue = new ArrayList<>(queueCapacity);
        for (int i = 0; i < queueCapacity; i++) {
            PlantDefinition plant = pickWeightedPlant(path.plants(), totalWeight, random);
            queue.add(toQueueEntry(plant));
        }
        return List.copyOf(queue);
    }

    private static Optional<PlantDefinition> findPlantDefinition(SuccessionPathDefinition path, ResourceLocation plantId) {
        return path.plants().stream()
                .filter(plant -> plant.plantId().equals(plantId))
                .findFirst();
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

    private record AcceleratedVisualStage(VegetationLifecycleStage stage, long syntheticAge, int pointBonus) {
        int pointValue(int basePointValue) {
            return Math.max(1, basePointValue + pointBonus);
        }
    }

    private record TreeBlocks(BlockState log, BlockState leaves) {
    }
}
