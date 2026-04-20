package com.s.ecoflux.plant;

import net.minecraft.resources.ResourceLocation;

public record VegetationTransformation(
        ResourceLocation targetVegetationId,
        ResourceLocation targetAdapterType,
        VegetationCategory targetCategory,
        VegetationLifecycleStage targetStage,
        int targetBasePointValue,
        int targetCurrentPointValue) {
}
