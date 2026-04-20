package com.s.ecoflux.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record SuccessionPathDefinition(
        ResourceLocation pathId,
        int priority,
        List<ResourceLocation> sourceBiomes,
        ResourceLocation targetBiome,
        @Nullable ResourceLocation fallbackBiome,
        ClimateCondition climate,
        ChunkRules chunkRules,
        List<PlantDefinition> plants) {
    public SuccessionPathDefinition {
        if (pathId == null) {
            throw new IllegalArgumentException("pathId cannot be null");
        }
        if (sourceBiomes == null || sourceBiomes.isEmpty()) {
            throw new IllegalArgumentException("sourceBiomes cannot be empty");
        }
        if (targetBiome == null) {
            throw new IllegalArgumentException("targetBiome cannot be null");
        }
        if (climate == null) {
            throw new IllegalArgumentException("climate cannot be null");
        }
        if (chunkRules == null) {
            throw new IllegalArgumentException("chunkRules cannot be null");
        }
        if (plants == null || plants.isEmpty()) {
            throw new IllegalArgumentException("plants cannot be empty");
        }
        sourceBiomes = List.copyOf(sourceBiomes);
        plants = List.copyOf(plants);
    }

    public boolean matches(ResourceLocation biomeId, double temperature, double downfall) {
        return sourceBiomes.contains(biomeId) && climate.matches(temperature, downfall);
    }
}
