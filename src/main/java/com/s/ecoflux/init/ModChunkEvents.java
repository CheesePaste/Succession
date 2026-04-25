package com.s.ecoflux.init;

import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.prototype.PrototypeChunkController;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class ModChunkEvents {
    private static final Map<ResourceKey<Level>, LinkedHashSet<Long>> TRACKED_CHUNKS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Long, Long>> ACCELERATED_CHUNKS = new HashMap<>();
    private static final int ACCELERATED_TRANSITION_TICKS = 200;
    private static volatile boolean automaticProcessingEnabled;

    private ModChunkEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModChunkEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ModChunkEvents::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(ModChunkEvents::onLevelTick);
    }

    public static boolean isAutomaticProcessingEnabled() {
        return automaticProcessingEnabled;
    }

    public static void setAutomaticProcessingEnabled(boolean enabled) {
        automaticProcessingEnabled = enabled;
    }

    public static void syncChunkTracking(ServerLevel level, ChunkAccess chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        updateTrackedChunk(level, chunk.getPos().toLong(), PrototypeChunkController.isPrototypeChunk(chunkData));
    }

    public static void startAcceleratedTransition(ServerLevel level, LevelChunk chunk) {
        ACCELERATED_CHUNKS
                .computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .put(chunk.getPos().toLong(), level.getGameTime());
        updateTrackedChunk(level, chunk.getPos().toLong(), true);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkAccess chunk = event.getChunk();
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getCurrentBiome().isEmpty()) {
            PrototypeChunkController.initializeChunkData(chunk);
        }

        syncChunkTracking(serverLevel, chunk);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        updateTrackedChunk(serverLevel, event.getChunk().getPos().toLong(), false);
        Map<Long, Long> acceleratedChunks = ACCELERATED_CHUNKS.get(serverLevel.dimension());
        if (acceleratedChunks != null) {
            acceleratedChunks.remove(event.getChunk().getPos().toLong());
            if (acceleratedChunks.isEmpty()) {
                ACCELERATED_CHUNKS.remove(serverLevel.dimension());
            }
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        processAcceleratedChunks(serverLevel);
        if (!automaticProcessingEnabled) {
            return;
        }

        LinkedHashSet<Long> trackedChunks = TRACKED_CHUNKS.get(serverLevel.dimension());
        if (trackedChunks == null || trackedChunks.isEmpty()) {
            return;
        }

        Map<Long, Long> acceleratedChunks = ACCELERATED_CHUNKS.get(serverLevel.dimension());
        List<Long> snapshot = new ArrayList<>(trackedChunks);
        for (long chunkPosLong : snapshot) {
            if (acceleratedChunks != null && acceleratedChunks.containsKey(chunkPosLong)) {
                continue;
            }

            LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(chunkPosLong), net.minecraft.world.level.ChunkPos.getZ(chunkPosLong));
            if (chunk == null) {
                trackedChunks.remove(chunkPosLong);
                continue;
            }

            SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
            if (!PrototypeChunkController.isPrototypeChunk(chunkData)) {
                trackedChunks.remove(chunkPosLong);
                continue;
            }

            PrototypeChunkController.processAutoTick(serverLevel, chunk);
            if (!PrototypeChunkController.isPrototypeChunk(chunkData)) {
                trackedChunks.remove(chunkPosLong);
            }
        }

        if (trackedChunks.isEmpty()) {
            TRACKED_CHUNKS.remove(serverLevel.dimension());
        }
    }

    private static void processAcceleratedChunks(ServerLevel level) {
        Map<Long, Long> acceleratedChunks = ACCELERATED_CHUNKS.get(level.dimension());
        if (acceleratedChunks == null || acceleratedChunks.isEmpty()) {
            return;
        }

        List<Map.Entry<Long, Long>> snapshot = new ArrayList<>(acceleratedChunks.entrySet());
        for (Map.Entry<Long, Long> entry : snapshot) {
            long chunkPosLong = entry.getKey();
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    net.minecraft.world.level.ChunkPos.getX(chunkPosLong),
                    net.minecraft.world.level.ChunkPos.getZ(chunkPosLong));
            if (chunk == null) {
                acceleratedChunks.remove(chunkPosLong);
                continue;
            }

            boolean done = PrototypeChunkController.processAcceleratedTick(
                    level,
                    chunk,
                    entry.getValue(),
                    ACCELERATED_TRANSITION_TICKS);
            if (done) {
                acceleratedChunks.remove(chunkPosLong);
                syncChunkTracking(level, chunk);
            }
        }

        if (acceleratedChunks.isEmpty()) {
            ACCELERATED_CHUNKS.remove(level.dimension());
        }
    }

    private static void updateTrackedChunk(ServerLevel level, long chunkPosLong, boolean tracked) {
        LinkedHashSet<Long> trackedChunks = TRACKED_CHUNKS.computeIfAbsent(level.dimension(), ignored -> new LinkedHashSet<>());
        if (tracked) {
            trackedChunks.add(chunkPosLong);
            return;
        }

        trackedChunks.remove(chunkPosLong);
        if (trackedChunks.isEmpty()) {
            TRACKED_CHUNKS.remove(level.dimension());
        }
    }
}
