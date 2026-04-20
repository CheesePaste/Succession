package com.s.ecoflux.client.visual;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record VisualLifecycleInstance(
        ResourceLocation adapterId,
        ResourceLocation blockId,
        BlockPos pos,
        long startGameTime,
        VisualLifecycleProfile profile,
        VisualLifecycleStage forcedStage,
        VisualLifecycleExternalState externalState,
        VisualLifecycleTrackingSource source) {
    public VisualLifecycleInstance withForcedStage(VisualLifecycleStage nextStage) {
        return new VisualLifecycleInstance(adapterId, blockId, pos, startGameTime, profile, nextStage, externalState, source);
    }

    public VisualLifecycleInstance withExternalState(VisualLifecycleExternalState nextState) {
        return new VisualLifecycleInstance(adapterId, blockId, pos, startGameTime, profile, forcedStage, nextState, source);
    }
}
