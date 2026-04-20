package com.s.ecoflux.init;

import com.mojang.brigadier.CommandDispatcher;
import com.s.ecoflux.prototype.PrototypeChunkController;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ModCommands {
    private ModCommands() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        registerPrototypeCommands(event.getDispatcher());
    }

    private static void registerPrototypeCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ecoflux")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("prototype")
                        .then(Commands.literal("auto")
                                .then(Commands.literal("on").executes(context -> setAuto(context.getSource(), true)))
                                .then(Commands.literal("off").executes(context -> setAuto(context.getSource(), false)))
                                .then(Commands.literal("status").executes(context -> autoStatus(context.getSource()))))
                        .then(Commands.literal("init").executes(context -> run(context.getSource(), Action.INIT)))
                        .then(Commands.literal("status").executes(context -> run(context.getSource(), Action.STATUS)))
                        .then(Commands.literal("prune").executes(context -> run(context.getSource(), Action.PRUNE)))
                        .then(Commands.literal("spawn").executes(context -> run(context.getSource(), Action.SPAWN)))
                        .then(Commands.literal("evaluate").executes(context -> run(context.getSource(), Action.EVALUATE)))
                        .then(Commands.literal("step").executes(context -> run(context.getSource(), Action.STEP)))
                        .then(Commands.literal("transition").executes(context -> run(context.getSource(), Action.TRANSITION)))));
    }

    private static int run(CommandSourceStack source, Action action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        String message = switch (action) {
            case INIT -> {
                PrototypeChunkController.initializeChunkData(chunk);
                ModChunkEvents.syncChunkTracking(level, chunk);
                yield "Reinitialized current chunk. " + PrototypeChunkController.describeChunk(chunk);
            }
            case STATUS -> PrototypeChunkController.describeChunk(chunk);
            case PRUNE -> PrototypeChunkController.pruneTrackedPlants(level, chunk) + " " + PrototypeChunkController.describeChunk(chunk);
            case SPAWN -> PrototypeChunkController.spawnOnce(level, chunk) + " " + PrototypeChunkController.describeChunk(chunk);
            case EVALUATE -> PrototypeChunkController.evaluateNow(level, chunk) + " " + PrototypeChunkController.describeChunk(chunk);
            case STEP -> PrototypeChunkController.step(level, chunk) + " " + PrototypeChunkController.describeChunk(chunk);
            case TRANSITION -> {
                String result = PrototypeChunkController.forceTransition(level, chunk);
                ModChunkEvents.syncChunkTracking(level, chunk);
                yield result + " " + PrototypeChunkController.describeChunk(chunk);
            }
        };

        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setAuto(CommandSourceStack source, boolean enabled) {
        ModChunkEvents.setAutomaticProcessingEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal("Ecoflux prototype auto processing " + (enabled ? "enabled" : "disabled") + "."),
                true);
        return 1;
    }

    private static int autoStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "Ecoflux prototype auto processing is "
                                + (ModChunkEvents.isAutomaticProcessingEnabled() ? "enabled" : "disabled") + "."),
                false);
        return 1;
    }

    private enum Action {
        INIT,
        STATUS,
        PRUNE,
        SPAWN,
        EVALUATE,
        STEP,
        TRANSITION
    }
}
