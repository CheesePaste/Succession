package com.s.ecoflux.network;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.plant.VegetationTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private static final String NETWORK_VERSION = "1";

    private ModNetworking() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetworking::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(ModNetworking::onChunkSent);
        NeoForge.EVENT_BUS.addListener(ModNetworking::onChunkUnwatch);
    }

    public static void syncChunkToTracking(ServerLevel level, LevelChunk chunk) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk.getPos(), buildChunkSyncPayload(level, chunk));
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(
                VegetationVisualChunkSyncPayload.TYPE,
                VegetationVisualChunkSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleClientSync(payload)));
    }

    private static void onChunkSent(ChunkWatchEvent.Sent event) {
        syncChunkToPlayer(event.getPlayer(), event.getLevel(), event.getChunk());
    }

    private static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        PacketDistributor.sendToPlayer(
                event.getPlayer(),
                new VegetationVisualChunkSyncPayload(
                        event.getLevel().dimension().location(),
                        event.getPos(),
                        java.util.List.of()));
    }

    private static void syncChunkToPlayer(ServerPlayer player, ServerLevel level, LevelChunk chunk) {
        PacketDistributor.sendToPlayer(player, buildChunkSyncPayload(level, chunk));
    }

    private static VegetationVisualChunkSyncPayload buildChunkSyncPayload(ServerLevel level, LevelChunk chunk) {
        return new VegetationVisualChunkSyncPayload(
                level.dimension().location(),
                chunk.getPos(),
                VegetationTracker.INSTANCE.buildVisualSyncEntries(chunk, level.getGameTime()));
    }

    private static void handleClientSync(VegetationVisualChunkSyncPayload payload) {
        if (!FMLEnvironment.dist.isClient()) {
            EcofluxConstants.LOGGER.debug("已在专用服务器忽略区块 {} 的客户端视觉同步载荷", payload.chunkPos());
            return;
        }
        ClientHooks.handle(payload);
    }

    private static final class ClientHooks {
        private ClientHooks() {
        }

        private static void handle(VegetationVisualChunkSyncPayload payload) {
            com.s.ecoflux.client.visual.VisualLifecycleClientRuntime.INSTANCE.syncVegetationChunk(
                    payload.dimensionId(),
                    payload.chunkPos(),
                    payload.entries());
        }
    }
}
