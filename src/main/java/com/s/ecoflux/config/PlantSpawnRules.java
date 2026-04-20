package com.s.ecoflux.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record PlantSpawnRules(
        String placement,
        boolean requireSky,
        int maxLocalDensity,
        List<ResourceLocation> allowedBaseBlocks) {
    public PlantSpawnRules {
        if (placement == null || placement.isBlank()) {
            throw new IllegalArgumentException("placement cannot be blank");
        }
        if (maxLocalDensity <= 0) {
            throw new IllegalArgumentException("maxLocalDensity must be positive");
        }
        allowedBaseBlocks = List.copyOf(allowedBaseBlocks);
    }
}
