package com.cp.ecoflux.init;

/**
 * Debug and administration commands under {@code /ecoflux}.
 *
 * <p>Structure: registers a {@code /ecoflux} command tree with subcommands for
 * per-chunk auto processing ({@code auto on/off/status}), lifecycle
 * inspection ({@code lifecycle inspect/track/observe/untrack}), speed control
 * ({@code speed}), tree testing ({@code tree}), and biome sampling ({@code sample}).
 */

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.config.plant.PlantRegistry;
import com.cp.ecoflux.config.SuccessionSpeedConfig;
import com.cp.ecoflux.api.config.PlantDefinition;
import com.cp.ecoflux.network.ModNetworking;
import com.cp.ecoflux.util.sample.ChunkGeneratorAccessor;
import com.cp.ecoflux.util.sample.BiomePlantSampler;
import com.cp.ecoflux.util.sample.SamplingBiomeSource;
import com.cp.ecoflux.plant.VegetationTracker;
import com.cp.ecoflux.plant.tree.TreeShapeUtils;
import com.cp.ecoflux.plant.tree.spacecolonization.SpaceColonizationGenerator;
import com.cp.ecoflux.plant.tree.spacecolonization.SpaceColonizationParams;
import com.cp.ecoflux.succession.SuccessionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

public final class ModCommands {
    private ModCommands() {
    }

    private static int batchOffsetX = 0;
    private static int batchOffsetZ = 0;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ecoflux")
                .requires(source -> source.hasPermission(2))
                .then(registerAutoCommands())
                .then(registerSpeedCommand())
                .then(registerLifecycleCommands())
                .then(registerTreeCommands())
                .then(registerSampleCommands()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerAutoCommands() {
        return Commands.literal("auto")
                .then(Commands.literal("on").executes(context -> enableAuto(context.getSource())))
                .then(Commands.literal("off").executes(context -> disableAuto(context.getSource())))
                .then(Commands.literal("status").executes(context -> autoStatus(context.getSource())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerSpeedCommand() {
        return Commands.literal("speed")
                .then(Commands.argument("multiplier", FloatArgumentType.floatArg(0.1f, 1000.0f))
                        .executes(context -> setSpeed(
                                context.getSource(),
                                FloatArgumentType.getFloat(context, "multiplier"))))
                .then(Commands.literal("status").executes(context -> speedStatus(context.getSource())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerLifecycleCommands() {
        return Commands.literal("lifecycle")
                .then(Commands.literal("inspect").then(positionArguments(LifecycleAction.INSPECT)))
                .then(Commands.literal("track").then(positionArguments(LifecycleAction.TRACK)))
                .then(Commands.literal("observe").then(positionArguments(LifecycleAction.OBSERVE)))
                .then(Commands.literal("untrack").then(positionArguments(LifecycleAction.UNTRACK)))
                .then(Commands.literal("kill").then(positionArguments(LifecycleAction.KILL)))
                .then(Commands.literal("observe_chunk").executes(context -> runLifecycleChunkAction(context.getSource(), LifecycleChunkAction.OBSERVE)))
                .then(Commands.literal("sync_chunk").executes(context -> runLifecycleChunkAction(context.getSource(), LifecycleChunkAction.SYNC)))
                .then(Commands.literal("chunk").executes(context -> runLifecycleChunk(context.getSource())));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> positionArguments(LifecycleAction action) {
        return Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(context -> runLifecycle(
                                        context.getSource(),
                                        action,
                                        new BlockPos(
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "y"),
                                                IntegerArgumentType.getInteger(context, "z"))))));
    }

    private static int runLifecycle(CommandSourceStack source, LifecycleAction action, BlockPos pos)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        LevelChunk chunk = level.getChunkAt(pos);
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);

        String message = switch (action) {
            case INSPECT -> VegetationTracker.INSTANCE.inspect(level, pos);
            case TRACK -> {
                net.minecraft.resources.ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                PlantDefinition plantDef = PlantRegistry.INSTANCE.getDefinition(blockId)
                        .orElse(null);
                yield VegetationTracker.INSTANCE.trackAt(
                        level,
                        chunk,
                        pos,
                        chunkData.getCurrentBiome().map(key -> key.location()),
                        chunkData.getActivePathId(),
                        plantDef);
            }
            case OBSERVE -> VegetationTracker.INSTANCE.observeTracked(level, chunk, pos);
            case UNTRACK -> VegetationTracker.INSTANCE.untrack(chunk, pos);
            case KILL -> VegetationTracker.INSTANCE.forceKill(level, chunk, pos);
        };

        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int runLifecycleChunk(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LevelChunk chunk = player.serverLevel().getChunkAt(player.blockPosition());
        source.sendSuccess(() -> Component.literal(VegetationTracker.INSTANCE.describeChunk(chunk)), false);
        return 1;
    }

    private static int runLifecycleChunkAction(CommandSourceStack source, LifecycleChunkAction action)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        String message = switch (action) {
            case OBSERVE -> VegetationTracker.INSTANCE.observeChunk(level, chunk);
            case SYNC -> {
                ModNetworking.syncChunkToTracking(level, chunk);
                yield "已同步区块 " + chunk.getPos() + " 的生命周期视觉状态。";
            }
        };
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int enableAuto(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        ModChunkEvents.setGlobalAutoEnabled(true);

        // Bootstrap all already-loaded chunks, not just the player's current chunk
        String allChunksResult = ModChunkEvents.bootstrapAllLoadedChunks(level);

        // Also handle the player's current chunk for immediate feedback
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getCurrentBiome().isEmpty()) {
            SuccessionService.initializeChunk(chunk);
        }
        String bootstrap = String.join(" ",
                SuccessionService.pruneChunk(level, chunk),
                SuccessionService.spawnInChunk(level, chunk),
                VegetationTracker.INSTANCE.observeChunk(level, chunk));
        source.sendSuccess(
                () -> Component.literal("Ecoflux 全局自动演替已开启。 " + allChunksResult + " " + bootstrap + " " + SuccessionService.describeChunk(chunk)),
                true);
        return 1;
    }

    private static int disableAuto(CommandSourceStack source) {
        ModChunkEvents.setGlobalAutoEnabled(false);
        source.sendSuccess(
                () -> Component.literal("Ecoflux 全局自动演替已关闭。"),
                true);
        return 1;
    }

    private static int autoStatus(CommandSourceStack source) {
        boolean enabled = ModChunkEvents.isGlobalAutoEnabled();
        source.sendSuccess(
                () -> Component.literal("Ecoflux 全局自动演替当前" + (enabled ? "已开启" : "已关闭") + "。"),
                false);
        return 1;
    }

    private static int setSpeed(CommandSourceStack source, float multiplier) {
        SuccessionSpeedConfig.setSpeedMultiplier(multiplier);
        source.sendSuccess(
                () -> Component.literal("Ecoflux 演替速度倍率已设置为 " + String.format("%.1f", multiplier) + "x。"),
                true);
        return 1;
    }

    private static int speedStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "Ecoflux 当前演替速度倍率为 " + String.format("%.1f", SuccessionSpeedConfig.getSpeedMultiplier()) + "x。"),
                false);
        return 1;
    }

    // ── Tree commands ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerTreeCommands() {
        var presetArg = Commands.argument("preset", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    builder.suggest("birch");
                    builder.suggest("oak");
                    builder.suggest("cherry");
                    builder.suggest("spruce");
                    return builder.buildFuture();
                });

        return Commands.literal("tree")
                .then(Commands.literal("instant")
                        .then(presetArg
                                .then(Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(ctx -> treeInstant(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "preset"),
                                                IntegerArgumentType.getInteger(ctx, "seed"))))
                                .executes(ctx -> treeInstant(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "preset"),
                                        0))))
                .then(Commands.literal("grid")
                        .then(presetArg
                                .then(Commands.argument("param", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("branchAngle");
                                            builder.suggest("branchLengthRatio");
                                            builder.suggest("leafDensity");
                                            builder.suggest("leafRadius");
                                            builder.suggest("topWeight");
                                            builder.suggest("trunkLean");
                                            builder.suggest("secondaryChance");
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("min", FloatArgumentType.floatArg())
                                                .then(Commands.argument("max", FloatArgumentType.floatArg())
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                                                .executes(ctx -> treeGrid(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "preset"),
                                                                        StringArgumentType.getString(ctx, "param"),
                                                                        FloatArgumentType.getFloat(ctx, "min"),
                                                                        FloatArgumentType.getFloat(ctx, "max"),
                                                                        IntegerArgumentType.getInteger(ctx, "count")))))))))
                .then(Commands.literal("stats")
                        .then(presetArg
                                .then(Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(ctx -> treeStats(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "preset"),
                                                IntegerArgumentType.getInteger(ctx, "seed"))))
                                .executes(ctx -> treeStats(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "preset"),
                                        0))));
    }

    private static int treeInstant(CommandSourceStack source, String presetName, int seed)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos basePos = player.blockPosition();

        boolean is2x2 = presetName.endsWith("_2x2");
        String lookupName = is2x2 ? presetName.substring(0, presetName.length() - 4) : presetName;
        SpaceColonizationParams params = resolveTreePreset(lookupName);
        if (params == null) {
            source.sendFailure(Component.literal("Unknown preset: " + presetName + ". Available: birch, oak, cherry, spruce, jungle, dark_oak, acacia, mangrove (add _2x2 suffix for 2x2)"));
            return 0;
        }

        RandomSource random = seed != 0
                ? RandomSource.create(seed)
                : TreeShapeUtils.positionRandom(basePos, level.getSeed());

        int height = params.resolveTrunkHeight(random);
        SpaceColonizationGenerator.FullTreePlan plan = SpaceColonizationGenerator.generateFull(basePos, params, height, is2x2, random);

        Block logBlock = resolveLogBlock(lookupName);
        Block leavesBlock = resolveLeavesBlock(lookupName);

        for (BlockPos logPos : plan.logPositions()) {
            TreeShapeUtils.tryPlaceLog(level, logPos, logBlock, Direction.Axis.Y);
        }
        for (BlockPos leafPos : plan.leafPositions()) {
            BlockState existing = level.getBlockState(leafPos);
            if (existing.isAir() || existing.is(BlockTags.LEAVES) || existing.is(BlockTags.REPLACEABLE)) {
                level.setBlock(leafPos, leavesBlock.defaultBlockState()
                        .setValue(LeavesBlock.DISTANCE, 1)
                        .setValue(LeavesBlock.PERSISTENT, true), 3);
            }
        }

        int floating = SpaceColonizationGenerator.countFloatingLeaves(plan);
        boolean connected = SpaceColonizationGenerator.verifyConnectivity(plan);

        source.sendSuccess(() -> Component.literal(
                String.format("Tree [%s] seed=%d height=%d logs=%d leaves=%d floating=%d connected=%s",
                        presetName, seed != 0 ? seed : TreeShapeUtils.positionRandom(basePos, level.getSeed()).nextInt(),
                        height, plan.logPositions().size(), plan.leafPositions().size(),
                        floating, connected)),
                true);
        return 1;
    }

    private static int treeGrid(CommandSourceStack source, String presetName,
                                 String paramName, float minVal, float maxVal, int count)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();

        boolean is2x2 = presetName.endsWith("_2x2");
        String lookupName = is2x2 ? presetName.substring(0, presetName.length() - 4) : presetName;
        SpaceColonizationParams baseParams = resolveTreePreset(lookupName);
        if (baseParams == null) {
            source.sendFailure(Component.literal("Unknown preset: " + presetName));
            return 0;
        }

        int spacing = 12;
        Block logBlock = resolveLogBlock(lookupName);
        Block leavesBlock = resolveLeavesBlock(lookupName);
        StringBuilder report = new StringBuilder();

        for (int i = 0; i < count; i++) {
            float t = count > 1 ? (float) i / (count - 1) : 0.5f;
            float value = minVal + t * (maxVal - minVal);
            SpaceColonizationParams modified = applyParam(baseParams, paramName, value);

            BlockPos treePos = origin.east(i * spacing);
            RandomSource random = TreeShapeUtils.positionRandom(treePos, level.getSeed());
            int height = modified.resolveTrunkHeight(random);
            SpaceColonizationGenerator.FullTreePlan plan = SpaceColonizationGenerator.generateFull(treePos, modified, height, is2x2, random);

            for (BlockPos logPos : plan.logPositions()) {
                TreeShapeUtils.tryPlaceLog(level, logPos, logBlock, Direction.Axis.Y);
            }
            for (BlockPos leafPos : plan.leafPositions()) {
                BlockState existing = level.getBlockState(leafPos);
                if (existing.isAir() || existing.is(BlockTags.LEAVES) || existing.is(BlockTags.REPLACEABLE)) {
                    level.setBlock(leafPos, leavesBlock.defaultBlockState()
                            .setValue(LeavesBlock.DISTANCE, 1)
                            .setValue(LeavesBlock.PERSISTENT, true), 3);
                }
            }

            report.append(String.format("  #%d %s=%.2f h=%d logs=%d leaves=%d\n",
                    i, paramName, value, height,
                    plan.logPositions().size(), plan.leafPositions().size()));
        }

        String header = String.format("Tree grid [%s] param=%s range=[%.2f, %.2f] count=%d\n",
                presetName, paramName, minVal, maxVal, count);
        source.sendSuccess(() -> Component.literal(header + report.toString()), true);
        return 1;
    }

    private static int treeStats(CommandSourceStack source, String presetName, int seed)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        boolean is2x2 = presetName.endsWith("_2x2");
        String lookupName = is2x2 ? presetName.substring(0, presetName.length() - 4) : presetName;
        SpaceColonizationParams params = resolveTreePreset(lookupName);
        if (params == null) {
            source.sendFailure(Component.literal("Unknown preset: " + presetName));
            return 0;
        }

        RandomSource random = seed != 0
                ? RandomSource.create(seed)
                : TreeShapeUtils.positionRandom(player.blockPosition(), level.getSeed());

        BlockPos basePos = player.blockPosition();
        int height = params.resolveTrunkHeight(random);
        SpaceColonizationGenerator.FullTreePlan plan = SpaceColonizationGenerator.generateFull(basePos, params, height, is2x2, random);

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : plan.logPositions()) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }

        int floating = SpaceColonizationGenerator.countFloatingLeaves(plan);
        boolean connected = SpaceColonizationGenerator.verifyConnectivity(plan);

        String stats = String.format(
                "Stats [%s] seed=%d\n  Height: %d\n  Log count: %d\n  Leaf count: %d\n" +
                "  X spread: %d\n  Y spread: %d\n  Z spread: %d\n  Total blocks: %d\n" +
                "  Floating leaves: %d\n  Logs connected: %s",
                presetName, seed, height,
                plan.logPositions().size(), plan.leafPositions().size(),
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1,
                plan.logPositions().size() + plan.leafPositions().size(),
                floating, connected);

        source.sendSuccess(() -> Component.literal(stats), false);
        return 1;
    }

    @Nullable
    private static SpaceColonizationParams resolveTreePreset(String name) {
        return switch (name.toLowerCase()) {
            case "birch" -> SpaceColonizationParams.birch();
            case "oak" -> SpaceColonizationParams.oak();
            case "cherry" -> SpaceColonizationParams.cherry();
            case "spruce" -> SpaceColonizationParams.spruce();
            case "jungle" -> SpaceColonizationParams.jungle();
            case "dark_oak" -> SpaceColonizationParams.darkOak();
            case "acacia" -> SpaceColonizationParams.acacia();
            case "mangrove" -> SpaceColonizationParams.mangrove();
            default -> null;
        };
    }

    private static Block resolveLogBlock(String presetName) {
        return switch (presetName.toLowerCase()) {
            case "birch" -> Blocks.BIRCH_LOG;
            case "oak" -> Blocks.OAK_LOG;
            case "cherry" -> Blocks.CHERRY_LOG;
            case "spruce" -> Blocks.SPRUCE_LOG;
            case "jungle" -> Blocks.JUNGLE_LOG;
            case "dark_oak" -> Blocks.DARK_OAK_LOG;
            case "acacia" -> Blocks.ACACIA_LOG;
            case "mangrove" -> Blocks.MANGROVE_LOG;
            default -> Blocks.OAK_LOG;
        };
    }

    private static Block resolveLeavesBlock(String presetName) {
        return switch (presetName.toLowerCase()) {
            case "birch" -> Blocks.BIRCH_LEAVES;
            case "oak" -> Blocks.OAK_LEAVES;
            case "cherry" -> Blocks.CHERRY_LEAVES;
            case "spruce" -> Blocks.SPRUCE_LEAVES;
            case "jungle" -> Blocks.JUNGLE_LEAVES;
            case "dark_oak" -> Blocks.DARK_OAK_LEAVES;
            case "acacia" -> Blocks.ACACIA_LEAVES;
            case "mangrove" -> Blocks.MANGROVE_LEAVES;
            default -> Blocks.OAK_LEAVES;
        };
    }

    private static SpaceColonizationParams applyParam(SpaceColonizationParams base, String paramName, float value) {
        return switch (paramName.toLowerCase()) {
            case "upprobability" -> new SpaceColonizationParams(
                    base.envelopeType(), base.envelopeRadiusXZ(), base.envelopeHeight(),
                    base.envelopeCenterYOffset(), (int) value, base.splitChance(),
                    base.branchLengthRatio(), base.secondaryChance(),
                    base.lowestBranchHeight(), base.leafRadius(), base.leafDensity(), base.canopyStages());
            case "splitchance" -> new SpaceColonizationParams(
                    base.envelopeType(), base.envelopeRadiusXZ(), base.envelopeHeight(),
                    base.envelopeCenterYOffset(), base.upProbability(), value,
                    base.branchLengthRatio(), base.secondaryChance(),
                    base.lowestBranchHeight(), base.leafRadius(), base.leafDensity(), base.canopyStages());
            case "branchlengthratio" -> new SpaceColonizationParams(
                    base.envelopeType(), base.envelopeRadiusXZ(), base.envelopeHeight(),
                    base.envelopeCenterYOffset(), base.upProbability(), base.splitChance(),
                    value, base.secondaryChance(),
                    base.lowestBranchHeight(), base.leafRadius(), base.leafDensity(), base.canopyStages());
            case "secondarychance" -> new SpaceColonizationParams(
                    base.envelopeType(), base.envelopeRadiusXZ(), base.envelopeHeight(),
                    base.envelopeCenterYOffset(), base.upProbability(), base.splitChance(),
                    base.branchLengthRatio(), value,
                    base.lowestBranchHeight(), base.leafRadius(), base.leafDensity(), base.canopyStages());
            case "lowestbranchheight" -> new SpaceColonizationParams(
                    base.envelopeType(), base.envelopeRadiusXZ(), base.envelopeHeight(),
                    base.envelopeCenterYOffset(), base.upProbability(), base.splitChance(),
                    base.branchLengthRatio(), base.secondaryChance(),
                    (int) value, base.leafRadius(), base.leafDensity(), base.canopyStages());
            case "leafradius" -> new SpaceColonizationParams(
                    base.envelopeType(), base.envelopeRadiusXZ(), base.envelopeHeight(),
                    base.envelopeCenterYOffset(), base.upProbability(), base.splitChance(),
                    base.branchLengthRatio(), base.secondaryChance(),
                    base.lowestBranchHeight(), (int) value, base.leafDensity(), base.canopyStages());
            case "leafdensity" -> new SpaceColonizationParams(
                    base.envelopeType(), base.envelopeRadiusXZ(), base.envelopeHeight(),
                    base.envelopeCenterYOffset(), base.upProbability(), base.splitChance(),
                    base.branchLengthRatio(), base.secondaryChance(),
                    base.lowestBranchHeight(), base.leafRadius(), value, base.canopyStages());
            case "enveloperadius" -> new SpaceColonizationParams(
                    base.envelopeType(), value, base.envelopeHeight(),
                    base.envelopeCenterYOffset(), base.upProbability(), base.splitChance(),
                    base.branchLengthRatio(), base.secondaryChance(),
                    base.lowestBranchHeight(), base.leafRadius(), base.leafDensity(), base.canopyStages());
            case "envelopeheight" -> new SpaceColonizationParams(
                    base.envelopeType(), base.envelopeRadiusXZ(), value,
                    base.envelopeCenterYOffset(), base.upProbability(), base.splitChance(),
                    base.branchLengthRatio(), base.secondaryChance(),
                    base.lowestBranchHeight(), base.leafRadius(), base.leafDensity(), base.canopyStages());
            default -> base;
        };
    }

    // ── Sample commands ─────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerSampleCommands() {
        return Commands.literal("sample")
                .executes(context -> runSample(context.getSource(), 5, false))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 20))
                        .executes(context -> runSample(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "radius"), false))
                        .then(Commands.literal("apply")
                                .executes(context -> runSample(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius"), true))))
                .then(Commands.literal("batchall")
                        .executes(context -> runBatchAll(context.getSource(), 5, 0, ""))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 20))
                                .executes(context -> runBatchAll(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius"), 0, ""))
                                .then(Commands.argument("startFrom", IntegerArgumentType.integer(0))
                                        .executes(context -> runBatchAll(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "radius"),
                                                IntegerArgumentType.getInteger(context, "startFrom"), ""))
                                        .then(Commands.argument("biomeFilter", StringArgumentType.word())
                                                .executes(context -> runBatchAll(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "radius"),
                                                        IntegerArgumentType.getInteger(context, "startFrom"),
                                                        StringArgumentType.getString(context, "biomeFilter")))))));
    }

    private static int runSample(CommandSourceStack source, int radius, boolean apply)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        LevelChunk centerChunk = level.getChunkAt(player.blockPosition());

        source.sendSuccess(() -> Component.literal(
                "正在采样群系 (半径=" + radius + (apply ? ", 自动生成配置)" : ")") + "..."), true);

        if (apply) {
            String result = BiomePlantSampler.sampleAndApply(level, centerChunk, radius);
            EcofluxConstants.LOGGER.info("[Ecoflux] {}", result);
            source.sendSuccess(() -> Component.literal(result), false);
        } else {
            BiomePlantSampler.BiomePlantSample sample = BiomePlantSampler.sample(level, centerChunk, radius);
            if (sample == null) {
                source.sendFailure(Component.literal("无法确定当前区块的群系。"));
                return 0;
            }

            String report = sample.formatReport();
            EcofluxConstants.LOGGER.info("[Ecoflux] 群系采样报告:\n{}", report);
            source.sendSuccess(() -> Component.literal(report), false);
        }
        return 1;
    }

    private static int runBatchAll(CommandSourceStack source, int radius, int startFrom, String biomeFilter)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        source.getPlayerOrException();
        ServerLevel level = source.getServer().overworld();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource originalSource = generator.getBiomeSource();
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);

        ModChunkEvents.setGlobalAutoEnabled(false);

        int success = 0;
        int fail = 0;
        List<String> failures = new ArrayList<>();

        List<Holder.Reference<Biome>> biomes = biomeRegistry.holders()
                .filter(h -> h.unwrapKey()
                        .map(k -> k.location().getNamespace().equals("minecraft"))
                        .orElse(false))
                .toList();

        int total = biomes.size();
        int remaining = total - startFrom;

        source.sendSuccess(() -> Component.literal(
                "开始批量采样群系 (半径=" + radius + ", 起始=" + startFrom
                        + ", 剩余=" + remaining + "/" + total + ")..."), true);

        if (startFrom == 0) {
            batchOffsetX = (int) (System.nanoTime() % 100000);
            batchOffsetZ = (int) ((System.nanoTime() / 100000) % 100000);
            EcofluxConstants.LOGGER.info("[Ecoflux] 新批次采样偏移: X={}, Z={}", batchOffsetX, batchOffsetZ);
        } else {
            EcofluxConstants.LOGGER.info("[Ecoflux] 续跑批次采样，使用偏移: X={}, Z={}", batchOffsetX, batchOffsetZ);
        }

        ChunkGeneratorAccessor accessor = (ChunkGeneratorAccessor) (Object) generator;

        for (int i = startFrom; i < total; i++) {
            Holder<Biome> biomeHolder = biomes.get(i);
            ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElseThrow();
            ResourceLocation biomeId = biomeKey.location();

            if (!biomeFilter.isEmpty() && !biomeId.toString().contains(biomeFilter)) {
                continue;
            }

            boolean isWaterBiome = biomeHolder.is(BiomeTags.IS_OCEAN) || biomeHolder.is(BiomeTags.IS_RIVER);
            boolean isNonOverworld = biomeHolder.is(BiomeTags.IS_NETHER) || biomeHolder.is(BiomeTags.IS_END);

            if (isWaterBiome || isNonOverworld) {
                success++;
                EcofluxConstants.LOGGER.info("[Ecoflux] 跳过群系 [{}/{}] {}: 水域/非主世界群系，生成空规则", i + 1, total, biomeId);
                String emptyJson = String.format(
                        "{\n  \"schema_version\": 1,\n  \"biome_id\": \"%s\",\n"
                                + "  \"min_plant_count\": 0,\n  \"max_plant_count\": 0,\n"
                                + "  \"consuming\": 0,\n  \"queue_fill_factor\": 0.0,\n  \"plants\": []\n}",
                        biomeId);
                BiomePlantSampler.writeRulesJson(level, biomeId, emptyJson);
                continue;
            }

            SamplingBiomeSource samplingSource = new SamplingBiomeSource(biomeHolder);
            accessor.ecoflux$swapBiomeSourceForSampling(samplingSource);
            generator.refreshFeaturesPerStep();

            int centerX = batchOffsetX + (i % 16) * 256 + 8;
            int centerZ = batchOffsetZ + (i / 16) * 256 + 8;
            BlockPos samplePos = new BlockPos(centerX, 64, centerZ);

            try {
                LevelChunk sampleChunk = level.getChunkAt(samplePos);
                String result = BiomePlantSampler.sampleAndApply(level, sampleChunk, radius);
                if (result.contains("采样失败")) {
                    fail++;
                    failures.add(biomeId.toString());
                    EcofluxConstants.LOGGER.warn("[Ecoflux] 批量采样失败 [{}/{}] {}: {}", i + 1, total, biomeId, result);
                } else {
                    success++;
                    EcofluxConstants.LOGGER.info("[Ecoflux] 批量采样完成 [{}/{}] {}", i + 1, total, biomeId);
                }
            } catch (Exception e) {
                fail++;
                failures.add(biomeId.toString());
                EcofluxConstants.LOGGER.error("[Ecoflux] 批量采样异常 [{}/{}] {}: {}", i + 1, total, biomeId, e.getMessage());
            }
        }

        // Restore original biome source
        accessor.ecoflux$swapBiomeSourceForSampling(originalSource);
        generator.refreshFeaturesPerStep();
        ModChunkEvents.setGlobalAutoEnabled(true);

        String summary = String.format(
                "批量采样完成: 成功=%d 失败=%d 总计=%d%s",
                success, fail, total,
                failures.isEmpty() ? "" : " 失败群系: " + String.join(", ", failures));
        EcofluxConstants.LOGGER.info("[Ecoflux] {}", summary);
        source.sendSuccess(() -> Component.literal(summary), true);
        return 1;
    }

    private enum LifecycleAction {
        INSPECT,
        TRACK,
        OBSERVE,
        UNTRACK,
        KILL
    }

    private enum LifecycleChunkAction {
        OBSERVE,
        SYNC
    }
}
