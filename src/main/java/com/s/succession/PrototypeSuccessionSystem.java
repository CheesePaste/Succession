package com.s.succession;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class PrototypeSuccessionSystem {
    static final float EARLY_STAGE_THRESHOLD = 0.34F;
    static final float MID_STAGE_THRESHOLD = 0.67F;
    private static final int MAX_FILL_HEIGHT_PER_PASS = 64;
    private static final int TRACKED_CHUNK_MARGIN = 1;
    private static final int TARGET_CHUNKS_PER_PART = 12;
    private static final int MIN_PART_RADIUS_CHUNKS = 3;
    private static final int MAX_PART_RADIUS_CHUNKS = 7;
    private static final float PART_RATE_VARIATION = 0.12F;
    private static final int CONVERSION_ANIMATION_TICKS = 28;
    private static final String SOURCE_BIOME = "minecraft:plains";
    private static final String TARGET_BIOME = "minecraft:forest";
    private static final Map<Long, ConversionAnimation> ACTIVE_CONVERSIONS = new HashMap<>();

    private PrototypeSuccessionSystem() {
    }

    static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("succession")
                .then(Commands.literal("status").executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("scan").executes(context -> scanCurrentChunk(context.getSource())))
                .then(Commands.literal("accelerate")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F, 1.0F))
                                .executes(context -> accelerateCurrentChunk(context.getSource(), FloatArgumentType.getFloat(context, "amount")))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> resetCurrentChunk(context.getSource())));
        event.getDispatcher().register(root);
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.ENABLE_PROTOTYPE_PATH.get()) {
            return;
        }

        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) {
            return;
        }

        long gameTime = overworld.getGameTime();
        tickActiveConversions(overworld);
        if (gameTime % Config.PROTOTYPE_SCAN_INTERVAL_TICKS.get() != 0L) {
            return;
        }

        Set<Long> seenChunks = new HashSet<>();
        List<ChunkPos> loadedChunks = new ArrayList<>();
        int progressedChunks = 0;
        int deferredChunks = 0;
        int viewDistance = event.getServer().getPlayerList().getViewDistance() + TRACKED_CHUNK_MARGIN;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!player.serverLevel().dimension().equals(Level.OVERWORLD)) {
                continue;
            }

            ChunkPos center = player.chunkPosition();
            for (int chunkX = center.x - viewDistance; chunkX <= center.x + viewDistance; chunkX++) {
                for (int chunkZ = center.z - viewDistance; chunkZ <= center.z + viewDistance; chunkZ++) {
                    long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                    if (!seenChunks.add(chunkKey)) {
                        continue;
                    }

                    var loadedChunk = player.serverLevel().getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (loadedChunk == null) {
                        continue;
                    }

                    loadedChunks.add(loadedChunk.getPos());
                }
            }
        }

        if (loadedChunks.isEmpty()) {
            return;
        }

        long scanCycle = gameTime / Config.PROTOTYPE_SCAN_INTERVAL_TICKS.get();
        PartLayout partLayout = buildPartLayout(overworld, loadedChunks, scanCycle);
        for (ChunkPos loadedChunkPos : loadedChunks) {
            PartAssignment assignment = partLayout.assignmentFor(loadedChunkPos);
            if (!assignment.dueThisCycle()) {
                deferredChunks++;
                continue;
            }

            ProgressResult result = progressChunk(overworld, loadedChunkPos, true, assignment);
            if (result.changed()) {
                progressedChunks++;
            }
        }

        if (Config.VERBOSE_LOGGING.get()) {
            Succession.LOGGER.info(
                    "[Succession:scan_cycle:{}] scanned {} loaded chunks, scattered {} parts ({} active this cycle), deferred {} chunks, advanced {} chunk states with globalRate={}",
                    overworld.dimension().location(),
                    loadedChunks.size(),
                    partLayout.partCount(),
                    partLayout.activePartCount(),
                    deferredChunks,
                    progressedChunks,
                    Config.PROTOTYPE_GLOBAL_RATE.get());
        }
    }

    private static int showStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos chunkPos = player.chunkPosition();
        PrototypeSuccessionSavedData data = PrototypeSuccessionSavedData.get(level);
        PrototypeChunkState state = data.get(chunkPos);
        ScanResult scan = scanChunk(level, chunkPos);

        if (state == null) {
            source.sendSuccess(() -> Component.literal(buildStatusMessage(chunkPos, null, scan)), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(buildStatusMessage(chunkPos, state, scan)), false);
        return 1;
    }

    private static String buildStatusMessage(ChunkPos chunkPos, PrototypeChunkState state, ScanResult scan) {
        float progress = state == null ? 0.0F : state.progress();
        float storedScore = state == null ? 0.0F : state.lastScore();
        String stage = state == null ? "dormant" : state.stageName();
        String reason = state == null ? "never_scanned" : state.lastScanReason();
        return String.format(
                Locale.ROOT,
                "[Succession] chunk=(%d,%d) biome=%s eligible=%s progress=%.0f%% storedScore=%.2f liveScore=%.2f stage=%s reason=%s globalRate=%.2f path=%s -> %s",
                chunkPos.x,
                chunkPos.z,
                scan.biomeId(),
                scan.eligible(),
                progress * 100.0F,
                storedScore,
                scan.score(),
                stage,
                reason,
                Config.PROTOTYPE_GLOBAL_RATE.get(),
                SOURCE_BIOME,
                TARGET_BIOME);
    }

    private static int scanCurrentChunk(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ProgressResult result = progressChunk(player.serverLevel(), player.chunkPosition(), false);
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int accelerateCurrentChunk(CommandSourceStack source, float amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos chunkPos = player.chunkPosition();
        PrototypeSuccessionSavedData data = PrototypeSuccessionSavedData.get(level);
        PrototypeChunkState state = data.getOrCreate(chunkPos);
        logEvent(level, chunkPos, "manual_accelerate", "requested manual acceleration amount={} currentProgress={}", amount, state.progress());
        state.setProgress(state.progress() + amount);
        state.setLastScanReason("manual_accelerate");
        state.setLastScore(Math.max(state.lastScore(), amount));
        applyStageMarkers(level, chunkPos, state);
        tryComplete(level, chunkPos, state);
        data.setDirty();

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[Succession] accelerated chunk (%d,%d) by %.0f%% -> %.0f%% (%s)",
                chunkPos.x,
                chunkPos.z,
                amount * 100.0F,
                state.progress() * 100.0F,
                state.stageName())), true);
        return 1;
    }

    private static int resetCurrentChunk(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos chunkPos = player.chunkPosition();
        PrototypeSuccessionSavedData data = PrototypeSuccessionSavedData.get(level);
        logEvent(level, chunkPos, "reset", "reset requested by command; removing stored state and restoring biome if enabled");
        ACTIVE_CONVERSIONS.remove(chunkPos.toLong());
        data.remove(chunkPos);
        if (Config.ENABLE_BIOME_SWAP.get()) {
            runFillBiome(level, chunkPos, SOURCE_BIOME, TARGET_BIOME);
        }

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[Succession] reset chunk (%d,%d) back to prototype start state.",
                chunkPos.x,
                chunkPos.z)), true);
        return 1;
    }

    private static ProgressResult progressChunk(ServerLevel level, ChunkPos chunkPos, boolean automaticTick) {
        float manualRate = Mth.clamp(Config.PROTOTYPE_GLOBAL_RATE.get().floatValue(), 0.1F, 3.0F);
        return progressChunk(level, chunkPos, automaticTick, PartAssignment.manual(manualRate));
    }

    private static ProgressResult progressChunk(ServerLevel level, ChunkPos chunkPos, boolean automaticTick, PartAssignment assignment) {
        if (ACTIVE_CONVERSIONS.containsKey(chunkPos.toLong())) {
            return new ProgressResult(false, String.format(Locale.ROOT,
                    "[Succession] chunk (%d,%d) is animating conversion outward from the center.",
                    chunkPos.x,
                    chunkPos.z));
        }

        PrototypeSuccessionSavedData data = PrototypeSuccessionSavedData.get(level);
        PrototypeChunkState state = data.getOrCreate(chunkPos);
        ScanResult scan = scanChunk(level, chunkPos);
        long gameTime = level.getGameTime();

        if (automaticTick && state.lastScanTime() == gameTime) {
            return new ProgressResult(false, "[Succession] skipped duplicate scan.");
        }

        state.setLastScanTime(gameTime);
        state.setLastScore(scan.score());
        state.setLastScanReason(scan.reason());

        float rateMultiplier = assignment.deltaMultiplier();
        if (scan.eligible() && !state.completed()) {
            float delta = (float) (Config.PROTOTYPE_PROGRESS_PER_SCAN.get() * scan.score() * rateMultiplier);
            logEvent(level, chunkPos, "progress", "eligible scan produced delta={} previousProgress={} score={} reason={} part={} center=({}, {}) radius={} localRate={} effectiveRate={} stride={} phase={}",
                    delta,
                    state.progress(),
                    scan.score(),
                    scan.reason(),
                    assignment.partIdLabel(),
                    assignment.centerChunkX(),
                    assignment.centerChunkZ(),
                    assignment.radiusChunks(),
                    assignment.localRate(),
                    assignment.effectiveRate(),
                    assignment.cycleStride(),
                    assignment.phaseOffset());
            state.setProgress(state.progress() + delta);
            applyStageMarkers(level, chunkPos, state);
            tryComplete(level, chunkPos, state);
        } else if (!state.completed()) {
            float decay = Config.PROTOTYPE_DECAY_PER_SCAN.get().floatValue() * rateMultiplier;
            logEvent(level, chunkPos, "decay", "ineligible scan caused decay={} previousProgress={} reason={} part={} center=({}, {}) radius={} localRate={} effectiveRate={} stride={} phase={}",
                    decay,
                    state.progress(),
                    scan.reason(),
                    assignment.partIdLabel(),
                    assignment.centerChunkX(),
                    assignment.centerChunkZ(),
                    assignment.radiusChunks(),
                    assignment.localRate(),
                    assignment.effectiveRate(),
                    assignment.cycleStride(),
                    assignment.phaseOffset());
            state.setProgress(state.progress() - decay);
        }

        data.setDirty();

        if (Config.VERBOSE_LOGGING.get()) {
            Succession.LOGGER.info(
                    "Prototype scan chunk=({}, {}) eligible={} score={} progress={} reason={} part={} center=({}, {}) radius={} effectiveRate={} stride={} phase={}",
                    chunkPos.x,
                    chunkPos.z,
                    scan.eligible(),
                    scan.score(),
                    state.progress(),
                    scan.reason(),
                    assignment.partIdLabel(),
                    assignment.centerChunkX(),
                    assignment.centerChunkZ(),
                    assignment.radiusChunks(),
                    assignment.effectiveRate(),
                    assignment.cycleStride(),
                    assignment.phaseOffset());
        }

        String message = String.format(
                Locale.ROOT,
                "[Succession] scanned chunk (%d,%d): biome=%s eligible=%s score=%.2f progress=%.0f%% stage=%s reason=%s part=%s rate=%.2f stride=%d",
                chunkPos.x,
                chunkPos.z,
                scan.biomeId(),
                scan.eligible(),
                scan.score(),
                state.progress() * 100.0F,
                state.stageName(),
                scan.reason(),
                assignment.partIdLabel(),
                assignment.effectiveRate(),
                assignment.cycleStride());
        return new ProgressResult(true, message);
    }

    private static PartLayout buildPartLayout(ServerLevel level, List<ChunkPos> loadedChunks, long scanCycle) {
        List<ChunkPos> sortedChunks = new ArrayList<>(loadedChunks);
        sortedChunks.sort((left, right) -> {
            if (left.x != right.x) {
                return Integer.compare(left.x, right.x);
            }
            return Integer.compare(left.z, right.z);
        });

        int partCount = Mth.clamp(Mth.ceil((float) sortedChunks.size() / (float) TARGET_CHUNKS_PER_PART), 1, sortedChunks.size());
        RandomSource random = RandomSource.create(level.getSeed() ^ (scanCycle * 971_761_241L) ^ ((long) sortedChunks.size() * 31L));
        List<PartProfile> parts = new ArrayList<>(partCount);
        for (int partId = 0; partId < partCount; partId++) {
            float sampleOffset = (partId + random.nextFloat()) / (float) partCount;
            int centerIndex = Mth.clamp((int) (sampleOffset * sortedChunks.size()), 0, sortedChunks.size() - 1);
            ChunkPos center = sortedChunks.get(centerIndex);
            int radius = Mth.nextInt(random, MIN_PART_RADIUS_CHUNKS, MAX_PART_RADIUS_CHUNKS);
            float localRate = 1.0F + ((random.nextFloat() * 2.0F) - 1.0F) * PART_RATE_VARIATION;
            float effectiveRate = Mth.clamp((float) Config.PROTOTYPE_GLOBAL_RATE.get().doubleValue() * localRate, 0.1F, 3.0F);
            int cycleStride = computeCycleStride(effectiveRate);
            int phaseOffset = cycleStride <= 1 ? 0 : random.nextInt(cycleStride);
            parts.add(new PartProfile(partId, center, radius, localRate, effectiveRate, cycleStride, phaseOffset));
        }

        Map<Long, PartAssignment> assignments = new HashMap<>();
        Set<Integer> activeParts = new HashSet<>();
        for (ChunkPos chunkPos : sortedChunks) {
            PartProfile part = selectPart(parts, chunkPos);
            boolean dueThisCycle = part.isDue(scanCycle);
            if (dueThisCycle) {
                activeParts.add(part.partId());
            }

            assignments.put(chunkPos.toLong(), PartAssignment.fromProfile(part, dueThisCycle));
        }

        return new PartLayout(assignments, parts.size(), activeParts.size());
    }

    private static int computeCycleStride(float effectiveRate) {
        if (effectiveRate >= 0.85F) {
            return 1;
        }
        return Mth.clamp(Mth.ceil(0.85F / Math.max(0.1F, effectiveRate)), 2, 8);
    }

    private static PartProfile selectPart(List<PartProfile> parts, ChunkPos chunkPos) {
        PartProfile selected = parts.get(0);
        double bestScore = Double.MAX_VALUE;
        for (PartProfile part : parts) {
            double dx = chunkPos.x - part.center().x;
            double dz = chunkPos.z - part.center().z;
            double distanceScore = (dx * dx + dz * dz) / Math.max(1.0D, (double) part.radiusChunks() * (double) part.radiusChunks());
            if (distanceScore < bestScore) {
                bestScore = distanceScore;
                selected = part;
            }
        }
        return selected;
    }

    private static ScanResult scanChunk(ServerLevel level, ChunkPos chunkPos) {
        BlockPos surface = getSurfaceCenter(level, chunkPos);
        String biomeId = level.getBiome(surface).unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");

        if (!level.getBiome(surface).is(Biomes.PLAINS)) {
            return new ScanResult(false, 0.0F, biomeId, surface, "source_biome_mismatch");
        }

        BlockState groundState = level.getBlockState(surface);
        if (!groundState.is(Blocks.GRASS_BLOCK)) {
            return new ScanResult(false, 0.0F, biomeId, surface, "surface_not_grass_block");
        }

        int samples = 0;
        int lushSamples = 0;
        int treeSamples = 0;
        int waterSamples = 0;

        for (int dx = -6; dx <= 6; dx += 3) {
            for (int dz = -6; dz <= 6; dz += 3) {
                BlockPos sampleGround = getSurface(level, surface.getX() + dx, surface.getZ() + dz);
                BlockState sampleGroundState = level.getBlockState(sampleGround);
                BlockState above = level.getBlockState(sampleGround.above());
                samples++;

                if (sampleGroundState.is(Blocks.GRASS_BLOCK)) {
                    lushSamples++;
                }
                if (above.is(Blocks.SHORT_GRASS) || above.is(Blocks.TALL_GRASS) || above.is(BlockTags.FLOWERS) || above.is(Blocks.FERN)) {
                    lushSamples++;
                }
                if (containsNearbyTree(level, sampleGround.above(), 2)) {
                    treeSamples++;
                }
                if (hasNearbyWater(level, sampleGround, 2)) {
                    waterSamples++;
                }
            }
        }

        float lushRatio = samples == 0 ? 0.0F : Mth.clamp((float) lushSamples / (float) samples, 0.0F, 1.0F);
        float treeRatio = samples == 0 ? 0.0F : Mth.clamp((float) treeSamples / (float) samples, 0.0F, 1.0F);
        float waterRatio = samples == 0 ? 0.0F : Mth.clamp((float) waterSamples / (float) samples, 0.0F, 1.0F);
        float score = Mth.clamp(0.35F + lushRatio * 0.35F + treeRatio * 0.20F + waterRatio * 0.10F, 0.0F, 1.0F);

        return new ScanResult(true, score, biomeId, surface, "prototype_conditions_met");
    }

    private static boolean containsNearbyTree(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius), center.offset(radius, 6, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNearbyWater(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
            if (level.getFluidState(pos).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    private static void applyStageMarkers(ServerLevel level, ChunkPos chunkPos, PrototypeChunkState state) {
        if (!Config.ENABLE_VISUAL_MARKERS.get()) {
            return;
        }

        if (state.progress() >= EARLY_STAGE_THRESHOLD && !state.earlyMarkersPlaced()) {
            logEvent(level, chunkPos, "stage_markers", "placing early-stage vegetation markers across chunk");
            placeEarlyMarkers(level, chunkPos);
            state.setEarlyMarkersPlaced(true);
        }
        if (state.progress() >= MID_STAGE_THRESHOLD && !state.midMarkersPlaced()) {
            logEvent(level, chunkPos, "stage_markers", "placing mid-stage vegetation markers across chunk");
            placeMidMarkers(level, chunkPos);
            state.setMidMarkersPlaced(true);
        }
    }

    private static void placeEarlyMarkers(ServerLevel level, ChunkPos chunkPos) {
        RandomSource random = createChunkRandom(level, chunkPos, 11L);
        placeRandomPlants(level, chunkPos, random, Blocks.SHORT_GRASS.defaultBlockState(), 5);
        placeRandomPlants(level, chunkPos, random, Blocks.FERN.defaultBlockState(), 3);
    }

    private static void placeMidMarkers(ServerLevel level, ChunkPos chunkPos) {
        RandomSource random = createChunkRandom(level, chunkPos, 29L);
        placeRandomPlants(level, chunkPos, random, Blocks.OAK_SAPLING.defaultBlockState(), 4);
        placeRandomPlants(level, chunkPos, random, Blocks.POPPY.defaultBlockState(), 3);
        placeRandomPlants(level, chunkPos, random, Blocks.SHORT_GRASS.defaultBlockState(), 4);
    }

    private static void tryComplete(ServerLevel level, ChunkPos chunkPos, PrototypeChunkState state) {
        if (state.completed() || state.progress() < 1.0F || ACTIVE_CONVERSIONS.containsKey(chunkPos.toLong())) {
            return;
        }

        logEvent(level, chunkPos, "completion_queue", "progress reached {} and queued an outward conversion animation {} -> {} over {} ticks",
                state.progress(),
                SOURCE_BIOME,
                TARGET_BIOME,
                CONVERSION_ANIMATION_TICKS);
        state.setLastScanReason("animating_conversion");
        queueConversionAnimation(level, chunkPos);
    }

    private static boolean runFillBiome(ServerLevel level, ChunkPos chunkPos, String targetBiome, String replaceBiome) {
        boolean changed = runFillBiomeArea(level, chunkPos, targetBiome, replaceBiome, chunkPos.getMinBlockX(), chunkPos.getMaxBlockX(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockZ(), "fillbiome");
        if (changed) {
            resendChunkBiomes(level, chunkPos);
        }
        return changed;
    }

    private static boolean runFillBiomeArea(ServerLevel level, ChunkPos chunkPos, String targetBiome, String replaceBiome, int minX, int maxX, int minZ, int maxZ, String eventPrefix) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        var biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        ResourceKey<Biome> targetKey = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(targetBiome));
        ResourceKey<Biome> replaceKey = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(replaceBiome));
        var targetHolder = biomeRegistry.getOrThrow(targetKey);
        var chunk = level.getChunk(chunkPos.x, chunkPos.z);
        int changedCells = 0;

        logEvent(level, chunkPos, eventPrefix + "_start", "starting segmented biome conversion {} -> {} within x={}..{} z={}..{} from y={} to y={} with sliceHeight={}",
                replaceBiome,
                targetBiome,
                minX,
                maxX,
                minZ,
                maxZ,
                minY,
                maxY,
                MAX_FILL_HEIGHT_PER_PASS);
        for (int sliceMinY = minY; sliceMinY <= maxY; sliceMinY += MAX_FILL_HEIGHT_PER_PASS) {
            int sliceMaxY = Math.min(sliceMinY + MAX_FILL_HEIGHT_PER_PASS - 1, maxY);
            var result = FillBiomeCommand.fill(
                    level,
                    new BlockPos(minX, sliceMinY, minZ),
                    new BlockPos(maxX, sliceMaxY, maxZ),
                    targetHolder,
                    holder -> holder.is(replaceKey),
                    output -> {
                    });

            if (result.right().isPresent()) {
                CommandSyntaxException exception = result.right().get();
                logEvent(level, chunkPos, eventPrefix + "_slice_failed", "slice y={}..{} within x={}..{} z={}..{} failed with {}",
                        sliceMinY,
                        sliceMaxY,
                        minX,
                        maxX,
                        minZ,
                        maxZ,
                        exception.getMessage());
                return false;
            }

            int sliceChanged = result.left().orElse(0);
            changedCells += sliceChanged;
            logEvent(level, chunkPos, eventPrefix + "_slice", "slice y={}..{} within x={}..{} z={}..{} converted {} biome cells",
                    sliceMinY,
                    sliceMaxY,
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    sliceChanged);
        }

        if (changedCells <= 0) {
            logEvent(level, chunkPos, eventPrefix + "_noop", "segmented biome conversion within x={}..{} z={}..{} finished but changed 0 biome cells", minX, maxX, minZ, maxZ);
            return false;
        }

        chunk.setUnsaved(true);
        logEvent(level, chunkPos, eventPrefix + "_done", "segmented biome conversion within x={}..{} z={}..{} completed with {} changed biome cells", minX, maxX, minZ, maxZ, changedCells);
        return true;
    }

    private static void queueConversionAnimation(ServerLevel level, ChunkPos chunkPos) {
        long chunkKey = chunkPos.toLong();
        if (ACTIVE_CONVERSIONS.containsKey(chunkKey)) {
            return;
        }

        int centerX = chunkPos.getMinBlockX() + 8;
        int centerZ = chunkPos.getMinBlockZ() + 8;
        ConversionAnimation animation = new ConversionAnimation(chunkPos, centerX, centerZ, 8, 0, 0);
        ACTIVE_CONVERSIONS.put(chunkKey, animation);
        logEvent(level, chunkPos, "conversion_queued", "queued conversion animation from center ({}, {}) with maxLayer={} over {} ticks", centerX, centerZ, animation.maxLayer(), CONVERSION_ANIMATION_TICKS);
    }

    private static void tickActiveConversions(ServerLevel level) {
        if (ACTIVE_CONVERSIONS.isEmpty()) {
            return;
        }

        List<Long> finishedKeys = new ArrayList<>();
        for (Map.Entry<Long, ConversionAnimation> entry : ACTIVE_CONVERSIONS.entrySet()) {
            AnimationTickResult tickResult = tickConversionAnimation(level, entry.getValue());
            if (tickResult.finished()) {
                finishedKeys.add(entry.getKey());
            } else if (tickResult.updatedAnimation() != null) {
                entry.setValue(tickResult.updatedAnimation());
            }
        }

        for (Long finishedKey : finishedKeys) {
            ACTIVE_CONVERSIONS.remove(finishedKey);
        }
    }

    private static AnimationTickResult tickConversionAnimation(ServerLevel level, ConversionAnimation animation) {
        ChunkPos chunkPos = animation.chunkPos();
        PrototypeSuccessionSavedData data = PrototypeSuccessionSavedData.get(level);
        PrototypeChunkState state = data.get(chunkPos);
        if (state == null) {
            logEvent(level, chunkPos, "conversion_cancelled", "animation cancelled because chunk state no longer exists");
            return AnimationTickResult.done();
        }

        int nextElapsedTick = animation.elapsedTicks() + 1;
        int targetLayer = Mth.clamp((nextElapsedTick * (animation.maxLayer() + 1)) / CONVERSION_ANIMATION_TICKS, 0, animation.maxLayer());
        boolean biomeChangedThisTick = false;
        int surfaceChangesThisTick = 0;
        int processedLayers = 0;
        int nextLayer = animation.nextLayer();

        while (nextLayer <= targetLayer) {
            LayerResult layerResult = processConversionLayer(level, animation, nextLayer);
            if (!layerResult.success()) {
                state.setProgress(0.95F);
                state.setLastScanReason("fillbiome_failed");
                data.setDirty();
                logEvent(level, chunkPos, "conversion_failed", "conversion layer {} failed; progress rolled back to {}", nextLayer, state.progress());
                return AnimationTickResult.done();
            }

            biomeChangedThisTick |= layerResult.biomeChanged();
            surfaceChangesThisTick += layerResult.surfaceChanges();
            processedLayers++;
            nextLayer++;
        }

        if (biomeChangedThisTick) {
            resendChunkBiomes(level, chunkPos);
        }

        animation = animation.withProgress(nextLayer, nextElapsedTick, processedLayers == 0 ? animation.lastAnimatedLayer() : nextLayer - 1);
        state.setLastScanReason("animating_conversion");
        data.setDirty();

        logEvent(level, chunkPos, "conversion_tick", "animation tick {}/{} processedLayers={} currentLayer={} biomeChanged={} surfaceChanges={}",
                animation.elapsedTicks(),
                CONVERSION_ANIMATION_TICKS,
                processedLayers,
                animation.lastAnimatedLayer(),
                biomeChangedThisTick,
                surfaceChangesThisTick);

        if (animation.elapsedTicks() < CONVERSION_ANIMATION_TICKS && animation.nextLayer() <= animation.maxLayer()) {
            return AnimationTickResult.running(animation);
        }

        if (Config.ENABLE_VISUAL_MARKERS.get()) {
            logEvent(level, chunkPos, "conversion_finish_decorate", "final animation pass reached edge; applying forest re-decoration");
            decorateCompletedChunk(level, chunkPos);
        }

        state.setCompleted(true);
        state.setProgress(1.0F);
        state.setLastScanReason("converted_to_forest");
        data.setDirty();
        logEvent(level, chunkPos, "conversion_completed", "outward conversion animation completed successfully; biome should now be {}", TARGET_BIOME);
        return AnimationTickResult.done();
    }

    private static LayerResult processConversionLayer(ServerLevel level, ConversionAnimation animation, int layer) {
        ChunkPos chunkPos = animation.chunkPos();
        int minChunkX = chunkPos.getMinBlockX();
        int maxChunkX = chunkPos.getMaxBlockX();
        int minChunkZ = chunkPos.getMinBlockZ();
        int maxChunkZ = chunkPos.getMaxBlockZ();
        int minX = Math.max(minChunkX, animation.centerX() - layer);
        int maxX = Math.min(maxChunkX, animation.centerX() + layer);
        int minZ = Math.max(minChunkZ, animation.centerZ() - layer);
        int maxZ = Math.min(maxChunkZ, animation.centerZ() + layer);
        boolean biomeChanged = false;

        logEvent(level, chunkPos, "conversion_layer", "processing outward layer {} with bounds x={}..{} z={}..{}", layer, minX, maxX, minZ, maxZ);
        if (Config.ENABLE_BIOME_SWAP.get()) {
            if (layer == 0) {
                boolean stripChanged = runFillBiomeArea(level, chunkPos, TARGET_BIOME, SOURCE_BIOME, minX, maxX, minZ, maxZ, "fillbiome_layer_" + layer);
                biomeChanged |= stripChanged;
            } else {
                biomeChanged |= runFillBiomeArea(level, chunkPos, TARGET_BIOME, SOURCE_BIOME, minX, maxX, minZ, minZ, "fillbiome_layer_" + layer + "_north");
                biomeChanged |= runFillBiomeArea(level, chunkPos, TARGET_BIOME, SOURCE_BIOME, minX, maxX, maxZ, maxZ, "fillbiome_layer_" + layer + "_south");
                if (minZ + 1 <= maxZ - 1) {
                    biomeChanged |= runFillBiomeArea(level, chunkPos, TARGET_BIOME, SOURCE_BIOME, minX, minX, minZ + 1, maxZ - 1, "fillbiome_layer_" + layer + "_west");
                    biomeChanged |= runFillBiomeArea(level, chunkPos, TARGET_BIOME, SOURCE_BIOME, maxX, maxX, minZ + 1, maxZ - 1, "fillbiome_layer_" + layer + "_east");
                }
            }
        }

        int surfaceChanges = animateSurfaceLayer(level, chunkPos, animation, layer);
        int animatedTrees = growAnimatedTreesOnLayer(level, chunkPos, animation, layer);
        logEvent(level, chunkPos, "conversion_layer_result", "layer {} finished with biomeChanged={} surfaceChanges={} animatedTrees={}", layer, biomeChanged, surfaceChanges, animatedTrees);
        return new LayerResult(true, biomeChanged, surfaceChanges);
    }

    private static int animateSurfaceLayer(ServerLevel level, ChunkPos chunkPos, ConversionAnimation animation, int layer) {
        int changed = 0;
        if (!Config.ENABLE_VISUAL_MARKERS.get()) {
            return changed;
        }

        int minX = Math.max(chunkPos.getMinBlockX(), animation.centerX() - layer);
        int maxX = Math.min(chunkPos.getMaxBlockX(), animation.centerX() + layer);
        int minZ = Math.max(chunkPos.getMinBlockZ(), animation.centerZ() - layer);
        int maxZ = Math.min(chunkPos.getMaxBlockZ(), animation.centerZ() + layer);

        for (int x = minX; x <= maxX; x++) {
            changed += animateSurfaceColumn(level, chunkPos, x, minZ, layer);
            if (maxZ != minZ) {
                changed += animateSurfaceColumn(level, chunkPos, x, maxZ, layer);
            }
        }

        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            changed += animateSurfaceColumn(level, chunkPos, minX, z, layer);
            if (maxX != minX) {
                changed += animateSurfaceColumn(level, chunkPos, maxX, z, layer);
            }
        }

        return changed;
    }

    private static int animateSurfaceColumn(ServerLevel level, ChunkPos chunkPos, int x, int z, int layer) {
        RandomSource random = RandomSource.create(level.getSeed() ^ (long) x * 341873128712L ^ (long) z * 132897987541L ^ ((long) layer << 16));
        BlockPos ground = getSurface(level, x, z);
        BlockPos topPos = ground.above();
        BlockState groundState = level.getBlockState(ground);

        if (!groundState.is(Blocks.GRASS_BLOCK) && !groundState.is(Blocks.DIRT)) {
            return 0;
        }

        int changed = 0;
        int roll = random.nextInt(100);
        if (roll < 20) {
            if (setSurfaceBlock(level, chunkPos, ground, Blocks.PODZOL.defaultBlockState(), "animation_podzol")) {
                changed++;
            }
        } else if (roll < 35) {
            if (setSurfaceBlock(level, chunkPos, ground, Blocks.ROOTED_DIRT.defaultBlockState(), "animation_rooted_dirt")) {
                changed++;
            }
        } else if (roll < 45) {
            if (setSurfaceBlock(level, chunkPos, ground, Blocks.MOSS_BLOCK.defaultBlockState(), "animation_moss")) {
                changed++;
            }
        }

        if (level.isEmptyBlock(topPos)) {
            BlockState cover = random.nextInt(5) == 0 ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState();
            if (cover.canSurvive(level, topPos) && placeSurfaceCover(level, chunkPos, topPos, cover, "animation_cover")) {
                changed++;
            }
        }

        return changed;
    }

    private static int growAnimatedTreesOnLayer(ServerLevel level, ChunkPos chunkPos, ConversionAnimation animation, int layer) {
        RandomSource random = RandomSource.create(level.getSeed() ^ chunkPos.toLong() ^ (977L * (layer + 1L)));
        int attempts = layer <= 1 ? 1 : 1 + random.nextInt(2);
        int placedTrees = 0;

        for (int attempt = 0; attempt < attempts; attempt++) {
            BlockPos basePos = pickLayerTreeCandidate(animation, chunkPos, layer, random);
            BlockPos ground = findGround(level, basePos);
            BlockState groundState = level.getBlockState(ground);
            if (!isForestSoil(groundState)) {
                logEvent(level, chunkPos, "animation_tree_skip", "skip animated tree at {} because ground is {}", ground, groundState);
                continue;
            }

            if (normalizeTreeSoil(level, chunkPos, ground)) {
                groundState = level.getBlockState(ground);
            }

            if (!clearTreePlacementColumn(level, chunkPos, ground.above(), 8)) {
                logEvent(level, chunkPos, "animation_tree_skip", "skip animated tree at {} because canopy column could not be cleared", ground);
                continue;
            }

            ResourceKey<ConfiguredFeature<?, ?>> treeFeature = pickAnimatedTreeFeature(random, layer);
            boolean placed = placeConfiguredTree(level, chunkPos, ground.above(), treeFeature, random, "animated_" + treeFeature.location().getPath());
            if (placed) {
                placedTrees++;
            }
        }

        return placedTrees;
    }

    private static BlockPos pickLayerTreeCandidate(ConversionAnimation animation, ChunkPos chunkPos, int layer, RandomSource random) {
        if (layer <= 0) {
            return new BlockPos(animation.centerX(), 0, animation.centerZ());
        }

        int minX = Math.max(chunkPos.getMinBlockX(), animation.centerX() - layer);
        int maxX = Math.min(chunkPos.getMaxBlockX(), animation.centerX() + layer);
        int minZ = Math.max(chunkPos.getMinBlockZ(), animation.centerZ() - layer);
        int maxZ = Math.min(chunkPos.getMaxBlockZ(), animation.centerZ() + layer);
        int side = random.nextInt(4);
        return switch (side) {
            case 0 -> new BlockPos(Mth.nextInt(random, minX, maxX), 0, minZ);
            case 1 -> new BlockPos(Mth.nextInt(random, minX, maxX), 0, maxZ);
            case 2 -> new BlockPos(minX, 0, Mth.nextInt(random, minZ, maxZ));
            default -> new BlockPos(maxX, 0, Mth.nextInt(random, minZ, maxZ));
        };
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> pickAnimatedTreeFeature(RandomSource random, int layer) {
        if (layer >= 4 && random.nextInt(5) == 0) {
            return TreeFeatures.FANCY_OAK;
        }
        return random.nextBoolean() ? TreeFeatures.OAK : TreeFeatures.BIRCH;
    }

    private static boolean placeConfiguredTree(ServerLevel level, ChunkPos chunkPos, BlockPos pos, ResourceKey<ConfiguredFeature<?, ?>> featureKey, RandomSource random, String featureName) {
        try {
            var featureHolder = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(featureKey);
            boolean placed = featureHolder.value().place(level, level.getChunkSource().getGenerator(), random, pos);
            logEvent(level, chunkPos, placed ? "animation_tree_place" : "animation_tree_noop", "configured tree feature {} at {} -> {}", featureName, pos, placed);
            return placed;
        } catch (RuntimeException exception) {
            logEvent(level, chunkPos, "animation_tree_failed", "configured tree feature {} at {} failed with {}", featureName, pos, exception.getMessage());
            return false;
        }
    }

    private static void resendChunkBiomes(ServerLevel level, ChunkPos chunkPos) {
        var chunk = level.getChunk(chunkPos.x, chunkPos.z);
        chunk.setUnsaved(true);
        level.getChunkSource().chunkMap.resendBiomesForChunks(java.util.List.of(chunk));
        logEvent(level, chunkPos, "biome_resend", "resent biome data for chunk after animated conversion step");
    }

    private static void decorateCompletedChunk(ServerLevel level, ChunkPos chunkPos) {
        logEvent(level, chunkPos, "decorate_start", "starting forest re-decoration via vanilla placed features");
        int clearedSurfacePlants = prepareChunkForTreeFeatures(level, chunkPos);
        logEvent(level, chunkPos, "decorate_prepare", "cleared {} surface plants before running vanilla tree features", clearedSurfacePlants);

        int successfulPlacements = 0;
        if (placePlacedFeature(level, chunkPos, 71L, VegetationPlacements.TREES_BIRCH_AND_OAK, "trees_birch_and_oak")) {
            successfulPlacements++;
        }
        if (placePlacedFeature(level, chunkPos, 79L, VegetationPlacements.TREES_BIRCH_AND_OAK, "trees_birch_and_oak_second_pass")) {
            successfulPlacements++;
        }
        if (placePlacedFeature(level, chunkPos, 83L, VegetationPlacements.PATCH_GRASS_FOREST, "patch_grass_forest")) {
            successfulPlacements++;
        }
        if (placePlacedFeature(level, chunkPos, 89L, VegetationPlacements.PATCH_GRASS_FOREST, "patch_grass_forest_second_pass")) {
            successfulPlacements++;
        }
        if (placePlacedFeature(level, chunkPos, 97L, VegetationPlacements.FOREST_FLOWERS, "forest_flowers")) {
            successfulPlacements++;
        }
        if (placePlacedFeature(level, chunkPos, 101L, VegetationPlacements.BROWN_MUSHROOM_NORMAL, "brown_mushroom_normal")) {
            successfulPlacements++;
        }
        if (placePlacedFeature(level, chunkPos, 103L, VegetationPlacements.RED_MUSHROOM_NORMAL, "red_mushroom_normal")) {
            successfulPlacements++;
        }
        if (placePlacedFeature(level, chunkPos, 107L, MiscOverworldPlacements.FOREST_ROCK, "forest_rock")) {
            successfulPlacements++;
        }

        int surfaceChanges = applyForestFloorVariation(level, chunkPos);
        level.getChunk(chunkPos.x, chunkPos.z).setUnsaved(true);
        logEvent(level, chunkPos, "decorate_done", "forest re-decoration finished with {} successful placed-feature passes and {} surface changes", successfulPlacements, surfaceChanges);
    }

    private static int prepareChunkForTreeFeatures(ServerLevel level, ChunkPos chunkPos) {
        int cleared = 0;
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                BlockPos ground = getSurface(level, x, z);
                BlockState groundState = level.getBlockState(ground);
                if (!isForestSoil(groundState)) {
                    continue;
                }

                BlockPos topPos = ground.above();
                BlockState topState = level.getBlockState(topPos);
                if (!isTreePlacementBlocker(topState)) {
                    continue;
                }

                logEvent(level, chunkPos, "tree_prep_clear", "clearing {} at {} before tree feature placement", topState, topPos);
                level.setBlockAndUpdate(topPos, Blocks.AIR.defaultBlockState());
                cleared++;

                if (topState.is(Blocks.TALL_GRASS) || topState.is(Blocks.LARGE_FERN)) {
                    BlockPos upperPos = topPos.above();
                    if (level.getBlockState(upperPos).is(topState.getBlock())) {
                        level.setBlockAndUpdate(upperPos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        return cleared;
    }

    private static void placeRandomPlants(ServerLevel level, ChunkPos chunkPos, RandomSource random, BlockState plantState, int attempts) {
        for (int i = 0; i < attempts; i++) {
            placePlantIfPossible(level, randomChunkBlock(chunkPos, random, 1), plantState);
        }
    }

    private static boolean placePlacedFeature(ServerLevel level, ChunkPos chunkPos, long salt, ResourceKey<PlacedFeature> featureKey, String featureName) {
        RandomSource random = createChunkRandom(level, chunkPos, salt);
        BlockPos origin = new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ());

        try {
            var featureHolder = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(featureKey);
            boolean placed = featureHolder.value().placeWithBiomeCheck(level, level.getChunkSource().getGenerator(), random, origin);
            logEvent(level, chunkPos, placed ? "feature_place" : "feature_noop", "placed feature {} at origin {} -> {}", featureName, origin, placed);
            return placed;
        } catch (RuntimeException exception) {
            logEvent(level, chunkPos, "feature_failed", "placed feature {} failed with {}", featureName, exception.getMessage());
            return false;
        }
    }

    private static int applyForestFloorVariation(ServerLevel level, ChunkPos chunkPos) {
        RandomSource random = createChunkRandom(level, chunkPos, 131L);
        int attempts = 18 + random.nextInt(10);
        int changed = 0;

        for (int i = 0; i < attempts; i++) {
            BlockPos ground = findGround(level, randomChunkBlock(chunkPos, random, 1));
            BlockPos topPos = ground.above();
            BlockState groundState = level.getBlockState(ground);
            if (!groundState.is(Blocks.GRASS_BLOCK) && !groundState.is(Blocks.DIRT)) {
                logEvent(level, chunkPos, "surface_skip", "skip surface variation at {} because ground is {}", ground, groundState);
                continue;
            }

            int roll = random.nextInt(100);
            if (roll < 35) {
                if (setSurfaceBlock(level, chunkPos, ground, Blocks.PODZOL.defaultBlockState(), "podzol_patch")) {
                    changed++;
                }
                continue;
            }

            if (roll < 60) {
                if (setSurfaceBlock(level, chunkPos, ground, Blocks.COARSE_DIRT.defaultBlockState(), "coarse_dirt_patch")) {
                    changed++;
                }
                continue;
            }

            if (roll < 82) {
                if (setSurfaceBlock(level, chunkPos, ground, Blocks.ROOTED_DIRT.defaultBlockState(), "rooted_dirt_patch")) {
                    changed++;
                }
                continue;
            }

            if (setSurfaceBlock(level, chunkPos, ground, Blocks.MOSS_BLOCK.defaultBlockState(), "moss_patch")) {
                changed++;
            }

            if (random.nextBoolean() && level.isEmptyBlock(topPos)) {
                if (placeSurfaceCover(level, chunkPos, topPos, Blocks.MOSS_CARPET.defaultBlockState(), "moss_carpet")) {
                    changed++;
                }
            }
        }

        return changed;
    }

    private static boolean setSurfaceBlock(ServerLevel level, ChunkPos chunkPos, BlockPos pos, BlockState newState, String changeName) {
        BlockState currentState = level.getBlockState(pos);
        if (currentState.equals(newState)) {
            logEvent(level, chunkPos, "surface_noop", "skip {} at {} because block is already {}", changeName, pos, newState);
            return false;
        }

        logEvent(level, chunkPos, "surface_set", "setting {} at {} from {} to {}", changeName, pos, currentState, newState);
        level.setBlockAndUpdate(pos, newState);
        return true;
    }

    private static boolean placeSurfaceCover(ServerLevel level, ChunkPos chunkPos, BlockPos pos, BlockState state, String changeName) {
        if (!level.isEmptyBlock(pos)) {
            logEvent(level, chunkPos, "surface_cover_skip", "skip {} at {} because occupied by {}", changeName, pos, level.getBlockState(pos));
            return false;
        }
        if (!state.canSurvive(level, pos)) {
            logEvent(level, chunkPos, "surface_cover_skip", "skip {} at {} because survival check failed", changeName, pos);
            return false;
        }

        logEvent(level, chunkPos, "surface_cover_place", "placing {} at {}", changeName, pos);
        level.setBlockAndUpdate(pos, state);
        return true;
    }

    private static boolean isForestSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MOSS_BLOCK);
    }

    private static boolean isTreePlacementBlocker(BlockState state) {
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.MOSS_CARPET);
    }

    private static boolean normalizeTreeSoil(ServerLevel level, ChunkPos chunkPos, BlockPos ground) {
        BlockState groundState = level.getBlockState(ground);
        if (groundState.is(Blocks.GRASS_BLOCK) || groundState.is(Blocks.DIRT) || groundState.is(Blocks.PODZOL)) {
            return false;
        }

        logEvent(level, chunkPos, "tree_soil_normalize", "normalizing tree soil at {} from {} to {}", ground, groundState, Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(ground, Blocks.DIRT.defaultBlockState());
        return true;
    }

    private static boolean clearTreePlacementColumn(ServerLevel level, ChunkPos chunkPos, BlockPos startPos, int height) {
        for (int dy = 0; dy < height; dy++) {
            BlockPos pos = startPos.above(dy);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                return false;
            }
            if (!isTreePlacementBlocker(state)) {
                return false;
            }

            logEvent(level, chunkPos, "tree_column_clear", "clearing {} at {} for progressive tree placement", state, pos);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        return true;
    }

    private static BlockPos randomChunkBlock(ChunkPos chunkPos, RandomSource random, int margin) {
        int x = chunkPos.getMinBlockX() + margin + random.nextInt(Math.max(1, 16 - margin * 2));
        int z = chunkPos.getMinBlockZ() + margin + random.nextInt(Math.max(1, 16 - margin * 2));
        return new BlockPos(x, 0, z);
    }

    private static void placePlantIfPossible(ServerLevel level, BlockPos requestedPos, BlockState plantState) {
        BlockPos ground = findGround(level, requestedPos);
        BlockPos plantPos = ground.above();
        if (!level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) {
            logEvent(level, new ChunkPos(ground), "plant_skip", "skip {} at {} because ground is {}", plantState.getBlock(), plantPos, level.getBlockState(ground));
            return;
        }
        if (!level.isEmptyBlock(plantPos)) {
            logEvent(level, new ChunkPos(ground), "plant_skip", "skip {} at {} because block is occupied by {}", plantState.getBlock(), plantPos, level.getBlockState(plantPos));
            return;
        }
        if (!plantState.canSurvive(level, plantPos)) {
            logEvent(level, new ChunkPos(ground), "plant_skip", "skip {} at {} because survival check failed", plantState.getBlock(), plantPos);
            return;
        }
        logEvent(level, new ChunkPos(ground), "plant_place", "placing {} at {}", plantState.getBlock(), plantPos);
        level.setBlockAndUpdate(plantPos, plantState);
    }

    private static BlockPos findGround(ServerLevel level, BlockPos aroundPos) {
        return getSurface(level, aroundPos.getX(), aroundPos.getZ());
    }

    private static BlockPos getSurfaceCenter(ServerLevel level, ChunkPos chunkPos) {
        int centerX = chunkPos.getMinBlockX() + 8;
        int centerZ = chunkPos.getMinBlockZ() + 8;
        return getSurface(level, centerX, centerZ);
    }

    private static BlockPos getSurface(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        return new BlockPos(x, y, z);
    }

    private static RandomSource createChunkRandom(ServerLevel level, ChunkPos chunkPos, long salt) {
        long seed = level.getSeed() ^ (chunkPos.toLong() * 341873128712L) ^ salt;
        return RandomSource.create(seed);
    }

    private record PartProfile(int partId, ChunkPos center, int radiusChunks, float localRate, float effectiveRate, int cycleStride, int phaseOffset) {
        boolean isDue(long scanCycle) {
            return Math.floorMod(scanCycle + phaseOffset, cycleStride) == 0;
        }
    }

    private record PartAssignment(String partIdLabel, int centerChunkX, int centerChunkZ, int radiusChunks, float localRate, float effectiveRate, int cycleStride, int phaseOffset, float deltaMultiplier, boolean dueThisCycle) {
        static PartAssignment fromProfile(PartProfile profile, boolean dueThisCycle) {
            return new PartAssignment(
                    Integer.toString(profile.partId()),
                    profile.center().x,
                    profile.center().z,
                    profile.radiusChunks(),
                    profile.localRate(),
                    profile.effectiveRate(),
                    profile.cycleStride(),
                    profile.phaseOffset(),
                    profile.effectiveRate() * profile.cycleStride(),
                    dueThisCycle);
        }

        static PartAssignment manual(float globalRate) {
            return new PartAssignment("manual", 0, 0, 0, 1.0F, globalRate, 1, 0, globalRate, true);
        }
    }

    private record PartLayout(Map<Long, PartAssignment> assignments, int partCount, int activePartCount) {
        PartAssignment assignmentFor(ChunkPos chunkPos) {
            return assignments.getOrDefault(chunkPos.toLong(), PartAssignment.manual(Mth.clamp(Config.PROTOTYPE_GLOBAL_RATE.get().floatValue(), 0.1F, 3.0F)));
        }
    }

    private record ConversionAnimation(ChunkPos chunkPos, int centerX, int centerZ, int maxLayer, int nextLayer, int elapsedTicks, int lastAnimatedLayer) {
        ConversionAnimation(ChunkPos chunkPos, int centerX, int centerZ, int maxLayer, int nextLayer, int elapsedTicks) {
            this(chunkPos, centerX, centerZ, maxLayer, nextLayer, elapsedTicks, -1);
        }

        ConversionAnimation withProgress(int updatedNextLayer, int updatedElapsedTicks, int updatedLastAnimatedLayer) {
            return new ConversionAnimation(chunkPos, centerX, centerZ, maxLayer, updatedNextLayer, updatedElapsedTicks, updatedLastAnimatedLayer);
        }
    }

    private record ScanResult(boolean eligible, float score, String biomeId, BlockPos surface, String reason) {
    }

    private record LayerResult(boolean success, boolean biomeChanged, int surfaceChanges) {
    }

    private record AnimationTickResult(boolean finished, ConversionAnimation updatedAnimation) {
        static AnimationTickResult done() {
            return new AnimationTickResult(true, null);
        }

        static AnimationTickResult running(ConversionAnimation updatedAnimation) {
            return new AnimationTickResult(false, updatedAnimation);
        }
    }

    private record ProgressResult(boolean changed, String message) {
    }

    private static void logEvent(ServerLevel level, ChunkPos chunkPos, String event, String template, Object... args) {
        if (!Config.VERBOSE_LOGGING.get()) {
            return;
        }
        Succession.LOGGER.info("[Succession:{}:{}:{}:{}] " + template, prependArgs(level, chunkPos, event, args));
    }

    private static Object[] prependArgs(ServerLevel level, ChunkPos chunkPos, String event, Object[] args) {
        Object[] combined = new Object[args.length + 4];
        combined[0] = event;
        combined[1] = level.dimension().location();
        combined[2] = chunkPos.x;
        combined[3] = chunkPos.z;
        System.arraycopy(args, 0, combined, 4, args.length);
        return combined;
    }
}
