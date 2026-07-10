package com.cp.ecoflux.succession;

/**
 * Biome replacement service for chunk succession transitions and regressions.
 *
 * <p>Structure: static utility class providing {@code applyTransition()} to
 * advance a chunk to its target biome and {@code applyRegression()} to revert a
 * chunk to its fallback biome. Both methods call
 * {@code ChunkAccess.fillBiomesFromNoise()} to overwrite the chunk's biome data,
 * broadcast {@code ClientboundChunksBiomesPacket} to update all clients, soft-reset
 * chunk runtime state (preserving existing vegetation and tree growth sessions),
 * re-resolve the succession target for the new biome, and push a visual sync via
 * {@code ModNetworking.syncChunkToTracking()}.
 *
 * <p>Role in Ecoflux: the final step in the succession pipeline, invoked by
 * {@code SuccessionService} when evaluation determines a chunk has reached or
 * fallen below the transition/regression threshold.
 */

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.api.event.SuccessionEvent;
import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.api.config.SuccessionPathDefinition;
import com.cp.ecoflux.network.ModNetworking;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;

public final class BiomeTransitionService {
    private BiomeTransitionService() {
    }

    public static String applyRegression(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            SuccessionPathDefinition path) {
        ResourceKey<Biome> fallbackKey = chunkData.popBiome().orElse(null);
        if (fallbackKey == null && path.fallbackBiome() != null) {
            ResourceLocation fallbackId = path.fallbackBiome();
            fallbackKey = ResourceKey.create(Registries.BIOME, fallbackId);
        }

        if (fallbackKey == null) {
            return "区块 " + chunk.getPos() + " 跳过群系回退：没有回退目标群系。";
        }

        ResourceLocation fromBiomeLoc = chunkData.getCurrentBiome().map(key -> key.location()).orElse(null);
        SuccessionEvent.PreRegression preEvent = new SuccessionEvent.PreRegression(
                level, chunk, fromBiomeLoc, fallbackKey.location());
        NeoForge.EVENT_BUS.post(preEvent);
        if (preEvent.isCanceled()) {
            return "区块 " + chunk.getPos() + " 跳过群系回退：已被外部事件取消。";
        }

        Holder<Biome> biomeHolder = level.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .getOrThrow(fallbackKey);
        chunk.fillBiomesFromNoise(
                (x, y, z, sampler) -> biomeHolder,
                level.getChunkSource().randomState().sampler());
        chunk.setUnsaved(true);
        level.getServer()
                .getPlayerList()
                .broadcastAll(
                        ClientboundChunksBiomesPacket.forChunks(List.of(chunk)),
                        level.dimension());

        ResourceKey<Biome> oldBiome = chunkData.getCurrentBiome().orElse(null);
        // Popping was already done above for regression, so we don't push it. We just transition.
        chunkData.setCurrentBiome(fallbackKey);
        chunkData.softReset();
        chunkData.setLastEvaluationGameTime(level.getGameTime());
        SuccessionTargetResolver.resolveTarget(chunk);
        ModNetworking.syncChunkToTracking(level, chunk);
        NeoForge.EVENT_BUS.post(new SuccessionEvent.PostTransition(
                level, chunk, fromBiomeLoc, fallbackKey.location()));

        EcofluxConstants.LOGGER.info(
                "区块 {} 演替回退：{} -> {}",
                chunk.getPos(),
                oldBiome == null ? "未知" : oldBiome.location(),
                fallbackKey.location());
        return "区块 " + chunk.getPos() + " 已从 "
                + (oldBiome == null ? "未知" : oldBiome.location())
                + " 回退到 " + fallbackKey.location() + "。";
    }

    public static String applyTransition(ServerLevel level, LevelChunk chunk, SuccessionChunkData chunkData) {
        SuccessionChunkData data = chunkData;
        java.util.Optional<ResourceKey<Biome>> targetBiome = data.getTargetBiome();
        if (targetBiome.isEmpty()) {
            return "区块 " + chunk.getPos() + " 跳过群系转化：没有目标群系。";
        }

        ResourceLocation fromBiomeLoc = chunkData.getCurrentBiome().map(key -> key.location()).orElse(null);
        SuccessionEvent.PreTransition preEvent = new SuccessionEvent.PreTransition(
                level, chunk, fromBiomeLoc, targetBiome.get().location());
        NeoForge.EVENT_BUS.post(preEvent);
        if (preEvent.isCanceled()) {
            return "区块 " + chunk.getPos() + " 跳过群系转化：已被外部事件取消。";
        }

        Holder<Biome> biomeHolder = level.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .getOrThrow(targetBiome.get());
        chunk.fillBiomesFromNoise(
                (x, y, z, sampler) -> biomeHolder,
                level.getChunkSource().randomState().sampler());
        chunk.setUnsaved(true);
        level.getServer()
                .getPlayerList()
                .broadcastAll(
                        ClientboundChunksBiomesPacket.forChunks(List.of(chunk)),
                        level.dimension());
        ResourceKey<Biome> oldBiome = data.getCurrentBiome().orElse(null);
        data.pushBiome(oldBiome);
        data.setCurrentBiome(targetBiome.get());
        data.softReset();
        data.setLastEvaluationGameTime(level.getGameTime());
        SuccessionTargetResolver.resolveTarget(chunk);
        ModNetworking.syncChunkToTracking(level, chunk);
        NeoForge.EVENT_BUS.post(new SuccessionEvent.PostTransition(
                level, chunk, fromBiomeLoc, targetBiome.get().location()));

        EcofluxConstants.LOGGER.info(
                "区块 {} 演替完成：{} -> {}",
                chunk.getPos(),
                oldBiome == null ? "未知" : oldBiome.location(),
                targetBiome.get().location());
        return "区块 " + chunk.getPos() + " 已从 "
                + (oldBiome == null ? "未知" : oldBiome.location())
                + " 转化为 " + targetBiome.get().location() + "。";
    }
}
