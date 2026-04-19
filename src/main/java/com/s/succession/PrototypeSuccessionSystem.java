package com.s.succession;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class PrototypeSuccessionSystem {
    static final float EARLY_STAGE_THRESHOLD = 0.34F;
    static final float MID_STAGE_THRESHOLD = 0.67F;
    private static final int MAX_FILL_HEIGHT_PER_PASS = 64;
    private static final int TRACKED_CHUNK_MARGIN = 1;
    private static final String SOURCE_BIOME = "minecraft:plains";
    private static final String TARGET_BIOME = "minecraft:forest";

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
        if (gameTime % Config.PROTOTYPE_SCAN_INTERVAL_TICKS.get() != 0L) {
            return;
        }

        Set<Long> seenChunks = new HashSet<>();
        int loadedChunkChecks = 0;
        int progressedChunks = 0;
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

                    loadedChunkChecks++;
                    ProgressResult result = progressChunk(player.serverLevel(), loadedChunk.getPos(), true);
                    if (result.changed()) {
                        progressedChunks++;
                    }
                }
            }
        }

        if (Config.VERBOSE_LOGGING.get()) {
            Succession.LOGGER.info(
                    "[Succession:scan_cycle:{}] scanned {} loaded chunks and advanced {} chunk states",
                    overworld.dimension().location(),
                    loadedChunkChecks,
                    progressedChunks);
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
                "[Succession] chunk=(%d,%d) biome=%s eligible=%s progress=%.0f%% storedScore=%.2f liveScore=%.2f stage=%s reason=%s path=%s -> %s",
                chunkPos.x,
                chunkPos.z,
                scan.biomeId(),
                scan.eligible(),
                progress * 100.0F,
                storedScore,
                scan.score(),
                stage,
                reason,
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

        if (scan.eligible() && !state.completed()) {
            float delta = (float) (Config.PROTOTYPE_PROGRESS_PER_SCAN.get() * scan.score());
            logEvent(level, chunkPos, "progress", "eligible scan produced delta={} previousProgress={} score={} reason={}", delta, state.progress(), scan.score(), scan.reason());
            state.setProgress(state.progress() + delta);
            applyStageMarkers(level, chunkPos, state);
            tryComplete(level, chunkPos, state);
        } else if (!state.completed()) {
            float decay = Config.PROTOTYPE_DECAY_PER_SCAN.get().floatValue();
            logEvent(level, chunkPos, "decay", "ineligible scan caused decay={} previousProgress={} reason={}", decay, state.progress(), scan.reason());
            state.setProgress(state.progress() - Config.PROTOTYPE_DECAY_PER_SCAN.get().floatValue());
        }

        data.setDirty();

        if (Config.VERBOSE_LOGGING.get()) {
            Succession.LOGGER.info(
                    "Prototype scan chunk=({}, {}) eligible={} score={} progress={} reason={}",
                    chunkPos.x,
                    chunkPos.z,
                    scan.eligible(),
                    scan.score(),
                    state.progress(),
                    scan.reason());
        }

        String message = String.format(
                Locale.ROOT,
                "[Succession] scanned chunk (%d,%d): biome=%s eligible=%s score=%.2f progress=%.0f%% stage=%s reason=%s",
                chunkPos.x,
                chunkPos.z,
                scan.biomeId(),
                scan.eligible(),
                scan.score(),
                state.progress() * 100.0F,
                state.stageName(),
                scan.reason());
        return new ProgressResult(true, message);
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
        if (state.completed() || state.progress() < 1.0F) {
            return;
        }

        logEvent(level, chunkPos, "completion", "progress reached {} and will attempt biome conversion {} -> {}", state.progress(), SOURCE_BIOME, TARGET_BIOME);
        boolean biomeSwapSucceeded = !Config.ENABLE_BIOME_SWAP.get() || runFillBiome(level, chunkPos, TARGET_BIOME, SOURCE_BIOME);
        if (!biomeSwapSucceeded) {
            state.setProgress(0.95F);
            state.setLastScanReason("fillbiome_failed");
            logEvent(level, chunkPos, "completion_failed", "biome conversion failed; progress rolled back to {}", state.progress());
            return;
        }

        if (Config.ENABLE_VISUAL_MARKERS.get()) {
            logEvent(level, chunkPos, "completion", "decorating completed chunk with randomized forest cover");
            decorateCompletedChunk(level, chunkPos);
        }
        state.setCompleted(true);
        state.setProgress(1.0F);
        state.setLastScanReason("converted_to_forest");

        logEvent(level, chunkPos, "completed", "prototype path completed successfully; biome should now be {}", TARGET_BIOME);
    }

    private static boolean runFillBiome(ServerLevel level, ChunkPos chunkPos, String targetBiome, String replaceBiome) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        var biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        ResourceKey<Biome> targetKey = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(targetBiome));
        ResourceKey<Biome> replaceKey = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(replaceBiome));
        var targetHolder = biomeRegistry.getOrThrow(targetKey);
        var chunk = level.getChunk(chunkPos.x, chunkPos.z);
        int changedCells = 0;

        logEvent(level, chunkPos, "fillbiome_start", "starting segmented biome conversion {} -> {} from y={} to y={} with sliceHeight={}", replaceBiome, targetBiome, minY, maxY, MAX_FILL_HEIGHT_PER_PASS);
        for (int sliceMinY = minY; sliceMinY <= maxY; sliceMinY += MAX_FILL_HEIGHT_PER_PASS) {
            int sliceMaxY = Math.min(sliceMinY + MAX_FILL_HEIGHT_PER_PASS - 1, maxY);
            var result = FillBiomeCommand.fill(
                    level,
                    new BlockPos(chunkPos.getMinBlockX(), sliceMinY, chunkPos.getMinBlockZ()),
                    new BlockPos(chunkPos.getMaxBlockX(), sliceMaxY, chunkPos.getMaxBlockZ()),
                    targetHolder,
                    holder -> holder.is(replaceKey),
                    output -> {
                    });

            if (result.right().isPresent()) {
                CommandSyntaxException exception = result.right().get();
                logEvent(level, chunkPos, "fillbiome_slice_failed", "slice y={}..{} failed with {}", sliceMinY, sliceMaxY, exception.getMessage());
                return false;
            }

            int sliceChanged = result.left().orElse(0);
            changedCells += sliceChanged;
            logEvent(level, chunkPos, "fillbiome_slice", "slice y={}..{} converted {} biome cells", sliceMinY, sliceMaxY, sliceChanged);
        }

        if (changedCells <= 0) {
            logEvent(level, chunkPos, "fillbiome_noop", "segmented biome conversion finished but changed 0 biome cells");
            return false;
        }

        chunk.setUnsaved(true);
        level.getChunkSource().chunkMap.resendBiomesForChunks(java.util.List.of(chunk));
        logEvent(level, chunkPos, "fillbiome_done", "segmented biome conversion completed with {} changed biome cells and resend triggered", changedCells);
        return true;
    }

    private static void decorateCompletedChunk(ServerLevel level, ChunkPos chunkPos) {
        logEvent(level, chunkPos, "decorate_start", "starting forest re-decoration via vanilla placed features");

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

    private record ScanResult(boolean eligible, float score, String biomeId, BlockPos surface, String reason) {
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
