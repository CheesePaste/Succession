package com.s.ecoflux.config;

import com.s.ecoflux.client.visual.VisualLifecycleProfile;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class VisualLifecycleClientConfig {
    public static final ModConfigSpec SPEC;
    private static volatile Float debugUniformScaleOverride;

    private static final ModConfigSpec.BooleanValue USE_CONFIGURED_STAGE_SCALES;
    private static final ModConfigSpec.DoubleValue BORN_SCALE;
    private static final ModConfigSpec.DoubleValue GROWING_START_SCALE;
    private static final ModConfigSpec.DoubleValue MATURE_SCALE;
    private static final ModConfigSpec.DoubleValue AGING_SCALE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("visual_lifecycle");
        USE_CONFIGURED_STAGE_SCALES = builder
                .comment("When true, tracked blocks use the configured stage scales below instead of adapter defaults.")
                .define("use_configured_stage_scales", true);
        BORN_SCALE = builder
                .comment("Scale used at the born stage.")
                .defineInRange("born_scale", 0.2D, 0.05D, 100.0D);
        GROWING_START_SCALE = builder
                .comment("Scale at the start of the growing stage.")
                .defineInRange("growing_start_scale", 0.65D, 0.05D, 100.0D);
        MATURE_SCALE = builder
                .comment("Scale used at the mature stage.")
                .defineInRange("mature_scale", 3.0D, 0.05D, 100.0D);
        AGING_SCALE = builder
                .comment("Scale used at the aging stage.")
                .defineInRange("aging_scale", 0.9D, 0.05D, 100.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private VisualLifecycleClientConfig() {
    }

    public static float bornScale(VisualLifecycleProfile profile) {
        if (debugUniformScaleOverride != null) {
            return debugUniformScaleOverride;
        }
        return USE_CONFIGURED_STAGE_SCALES.get() ? BORN_SCALE.get().floatValue() : profile.bornScale();
    }

    public static float growingStartScale(VisualLifecycleProfile profile) {
        if (debugUniformScaleOverride != null) {
            return debugUniformScaleOverride;
        }
        return USE_CONFIGURED_STAGE_SCALES.get()
                ? GROWING_START_SCALE.get().floatValue()
                : profile.matureScale() * 0.65F;
    }

    public static float matureScale(VisualLifecycleProfile profile) {
        if (debugUniformScaleOverride != null) {
            return debugUniformScaleOverride;
        }
        return USE_CONFIGURED_STAGE_SCALES.get() ? MATURE_SCALE.get().floatValue() : profile.matureScale();
    }

    public static float agingScale(VisualLifecycleProfile profile) {
        if (debugUniformScaleOverride != null) {
            return debugUniformScaleOverride;
        }
        return USE_CONFIGURED_STAGE_SCALES.get() ? AGING_SCALE.get().floatValue() : profile.agingScale();
    }

    public static void setDebugUniformScaleOverride(float scale) {
        debugUniformScaleOverride = scale;
    }

    public static void clearDebugUniformScaleOverride() {
        debugUniformScaleOverride = null;
    }

    public static Float debugUniformScaleOverride() {
        return debugUniformScaleOverride;
    }
}
