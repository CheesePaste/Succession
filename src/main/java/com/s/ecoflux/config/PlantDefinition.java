package com.s.ecoflux.config;

import net.minecraft.resources.ResourceLocation;

public record PlantDefinition(
        ResourceLocation plantId,
        String category,
        int weight,
        int pointValue,
        long maxAgeTicks,
        PlantSpawnRules spawnRules) {
    public PlantDefinition {
        if (plantId == null) {
            throw new IllegalArgumentException("plantId cannot be null");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category cannot be blank");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
        if (pointValue < 0) {
            throw new IllegalArgumentException("pointValue must be non-negative");
        }
        if (maxAgeTicks <= 0L) {
            throw new IllegalArgumentException("maxAgeTicks must be positive");
        }
        if (spawnRules == null) {
            throw new IllegalArgumentException("spawnRules cannot be null");
        }
    }
}
