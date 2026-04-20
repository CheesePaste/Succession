package com.s.ecoflux.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.s.ecoflux.client.visual.VisualLifecycleClientRuntime;
import com.s.ecoflux.client.visual.VisualLifecycleRenderState;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {
    @Inject(method = "renderBatched", at = @At("HEAD"), cancellable = true)
    private void ecoflux$skipBaseRenderForScaledTrackedBlocks(
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter level,
            PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer consumer,
            boolean checkSides,
            RandomSource random,
            CallbackInfo ci) {
        if (VisualLifecycleClientRuntime.INSTANCE.isManualWorldRenderPass()) {
            return;
        }

        VisualLifecycleRenderState renderState = VisualLifecycleClientRuntime.INSTANCE.getRenderState(pos, state);
        if (renderState != null && Math.abs(renderState.scale() - 1.0F) >= 0.0001F) {
            ci.cancel();
        }
    }
}
