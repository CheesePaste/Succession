package com.s.ecoflux.client.visual;

import com.s.ecoflux.config.VisualLifecycleClientConfig;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public interface VisualLifecycleAdapter {
    ResourceLocation typeId();

    boolean matches(BlockState state);

    VisualLifecycleProfile createProfile(BlockState state);

    default List<Block> demoBlocks() {
        return List.of();
    }

    default String supportSummary() {
        return typeId().toString();
    }

    default VisualLifecycleRenderState resolveState(VisualLifecycleInstance instance, long gameTime, int baseColor) {
        VisualLifecycleStage stage = instance.forcedStage();
        float progress = 1.0F;
        VisualLifecycleProfile profile = instance.profile();
        if (stage == null) {
            VisualLifecycleExternalState externalState = instance.externalState();
            if (externalState != null) {
                stage = externalState.stage();
                progress = externalState.stageProgress();
            } else {
                long age = Math.max(0L, gameTime - instance.startGameTime());
                if (age < profile.bornTicks()) {
                    stage = VisualLifecycleStage.BORN;
                    progress = profile.bornTicks() <= 0 ? 1.0F : (float) age / profile.bornTicks();
                } else if (age < profile.bornTicks() + profile.growingTicks()) {
                    stage = VisualLifecycleStage.GROWING;
                    int localAge = (int) (age - profile.bornTicks());
                    progress = profile.growingTicks() <= 0 ? 1.0F : (float) localAge / profile.growingTicks();
                } else if (age < profile.bornTicks() + profile.growingTicks() + profile.matureTicks()) {
                    stage = VisualLifecycleStage.MATURE;
                    int localAge = (int) (age - profile.bornTicks() - profile.growingTicks());
                    progress = profile.matureTicks() <= 0 ? 1.0F : (float) localAge / profile.matureTicks();
                } else {
                    stage = VisualLifecycleStage.AGING;
                    long agingAge = age - profile.bornTicks() - profile.growingTicks() - profile.matureTicks();
                    progress = profile.agingTicks() <= 0 ? 1.0F : (float) agingAge / profile.agingTicks();
                }
            }
        }

        float bornScale = VisualLifecycleClientConfig.bornScale(profile);
        float growingStartScale = VisualLifecycleClientConfig.growingStartScale(profile);
        float matureScale = VisualLifecycleClientConfig.matureScale(profile);
        float agingScale = VisualLifecycleClientConfig.agingScale(profile);
        float scale = switch (stage) {
            case BORN -> lerp(bornScale, growingStartScale, progress);
            case GROWING -> lerp(growingStartScale, matureScale, progress);
            case MATURE -> matureScale;
            case AGING -> lerp(matureScale, agingScale, progress);
        };
        int tintedColor = stage == VisualLifecycleStage.AGING
                ? shiftColor(baseColor, profile, progress)
                : baseColor;
        return new VisualLifecycleRenderState(stage, clamp01(progress), scale, tintedColor);
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * clamp01(progress);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int shiftColor(int baseColor, VisualLifecycleProfile profile, float progress) {
        float clampedProgress = clamp01(progress);
        float red = ((baseColor >> 16) & 0xFF) / 255.0F;
        float green = ((baseColor >> 8) & 0xFF) / 255.0F;
        float blue = (baseColor & 0xFF) / 255.0F;
        float[] hsb = java.awt.Color.RGBtoHSB(
                Math.round(red * 255.0F),
                Math.round(green * 255.0F),
                Math.round(blue * 255.0F),
                null);
        float shiftedHue = hsb[0] + profile.agingHueShift() * clampedProgress;
        while (shiftedHue < 0.0F) {
            shiftedHue += 1.0F;
        }
        while (shiftedHue > 1.0F) {
            shiftedHue -= 1.0F;
        }
        float saturationMultiplier = lerp(1.0F, profile.agingSaturationMultiplier(), clampedProgress);
        float brightnessMultiplier = lerp(1.0F, profile.agingBrightnessMultiplier(), clampedProgress);
        float saturation = clamp01(hsb[1] * saturationMultiplier);
        float brightness = clamp01(hsb[2] * brightnessMultiplier);
        return java.awt.Color.HSBtoRGB(shiftedHue, saturation, brightness) & 0xFFFFFF;
    }
}
