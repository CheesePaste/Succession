package com.s.ecoflux.plant;

import net.minecraft.util.Mth;

public record VegetationVisualState(VegetationLifecycleStage stage, float stageProgress) {
    public VegetationVisualState {
        stageProgress = Mth.clamp(stageProgress, 0.0F, 1.0F);
    }
}
