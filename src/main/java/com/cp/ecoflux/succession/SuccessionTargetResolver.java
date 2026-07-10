package com.cp.ecoflux.succession;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.config.biome.BiomeRules;
import com.cp.ecoflux.config.biome.BiomeRulesRegistry;
import com.cp.ecoflux.config.succession.SuccessionConfigRegistry;
import com.cp.ecoflux.api.config.SuccessionPathDefinition;
import com.cp.ecoflux.init.ModAttachments;
import com.cp.ecoflux.plant.PlantSpawner;
import com.cp.ecoflux.world.ChunkSamplingHelper;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class SuccessionTargetResolver {
    private SuccessionTargetResolver() {
    }

    public static void resolveTarget(ChunkAccess chunk) {
        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        Optional<ResourceKey<Biome>> currentBiomeKey = ChunkSamplingHelper.sampleChunkCenterBiome(chunk);
        if (currentBiomeKey.isEmpty()) {
            EcofluxConstants.LOGGER.warn("无法解析区块 {} 中心点的群系", chunk.getPos());
            return;
        }

        ChunkSamplingHelper.ChunkClimateSample climateSample = ChunkSamplingHelper.sampleChunkClimate(
                chunk, currentBiomeKey.get());
        Optional<SuccessionPathDefinition> matchedPath = SuccessionConfigRegistry.findBestMatch(
                climateSample.biomeKey(),
                climateSample.temperature(),
                climateSample.downfall());

        chunkData.setCurrentBiome(climateSample.biomeKey());

        ResourceLocation biomeId = climateSample.biomeKey().location();
        Optional<BiomeRules> biomeRules = BiomeRulesRegistry.getRules(biomeId);

        if (matchedPath.isPresent() && biomeRules.isPresent()) {
            SuccessionPathDefinition path = matchedPath.get();
            BiomeRules rules = biomeRules.get();
            chunkData.setActivePathId(path.pathId());
            chunkData.setTargetBiome(ChunkSamplingHelper.toBiomeKey(path.targetBiome()));
            chunkData.setConsumingValue(rules.consuming());
            chunkData.setMaxPlantCount(rules.samplePlantCount(new java.util.Random(chunk.getPos().toLong())));
            chunkData.setActiveBiomeRulesId(biomeId);
            chunkData.replacePlantQueue(PlantSpawner.buildWeightedQueue(
                    rules, new java.util.Random(chunk.getPos().toLong())));
            return;
        }

        if (matchedPath.isEmpty() && biomeRules.isEmpty()) {
            EcofluxConstants.LOGGER.debug(
                    "区块 {} 的群系 {} 没有匹配的演替路径和群系规则", chunk.getPos(), biomeId);
        } else if (matchedPath.isEmpty()) {
            EcofluxConstants.LOGGER.warn(
                    "区块 {} 的群系 {} 没有匹配的演替路径", chunk.getPos(), biomeId);
        } else {
            EcofluxConstants.LOGGER.warn(
                    "区块 {} 的群系 {} 没有群系规则配置", chunk.getPos(), biomeId);
        }

        // History is preserved.
    }
}
