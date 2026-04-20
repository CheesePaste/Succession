package com.s.ecoflux.client.visual;

public record VisualLifecycleProfile(
        int bornTicks,
        int growingTicks,
        int matureTicks,
        int agingTicks,
        float bornScale,
        float matureScale,
        float agingScale,
        float agingHueShift,
        float agingSaturationMultiplier,
        float agingBrightnessMultiplier) {
}
