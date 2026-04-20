package com.s.ecoflux.client.visual;

import net.minecraft.util.Mth;

public record VisualLifecycleExternalState(VisualLifecycleStage stage, float stageProgress) {
    public VisualLifecycleExternalState {
        stageProgress = Mth.clamp(stageProgress, 0.0F, 1.0F);
    }
}
