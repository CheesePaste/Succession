package com.s.ecoflux.client.visual;

public record VisualLifecycleRenderState(
        VisualLifecycleStage stage,
        float stageProgress,
        float scale,
        int tintedColor) {
}
