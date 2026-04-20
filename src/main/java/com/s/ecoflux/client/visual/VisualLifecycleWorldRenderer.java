package com.s.ecoflux.client.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.s.ecoflux.EcofluxConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = EcofluxConstants.MOD_ID, value = Dist.CLIENT)
public final class VisualLifecycleWorldRenderer {
    private VisualLifecycleWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getBlockRenderer() == null) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();

        for (VisualLifecycleInstance instance : VisualLifecycleClientRuntime.INSTANCE.trackedInCurrentLevel()) {
            BlockPos pos = instance.pos();
            BlockState state = minecraft.level.getBlockState(pos);
            VisualLifecycleRenderState renderState = VisualLifecycleClientRuntime.INSTANCE.getRenderState(pos, state);
            if (renderState == null || Math.abs(renderState.scale() - 1.0F) < 0.0001F) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(
                    pos.getX() - cameraPosition.x(),
                    pos.getY() - cameraPosition.y(),
                    pos.getZ() - cameraPosition.z());
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.scale(renderState.scale(), renderState.scale(), renderState.scale());
            poseStack.translate(-0.5F, 0.0F, -0.5F);

            VisualLifecycleClientRuntime.INSTANCE.beginManualWorldRenderPass();
            try {
                RandomSource random = RandomSource.create(state.getSeed(pos));
                minecraft.getBlockRenderer().renderBatched(
                        state,
                        pos,
                        minecraft.level,
                        poseStack,
                        bufferSource.getBuffer(ItemBlockRenderTypes.getChunkRenderType(state)),
                        false,
                        random);
            } finally {
                VisualLifecycleClientRuntime.INSTANCE.endManualWorldRenderPass();
            }
            poseStack.popPose();
        }

        bufferSource.endBatch();
    }
}
