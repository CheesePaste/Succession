package com.s.ecoflux.client.visual;

import com.s.ecoflux.EcofluxConstants;
import java.util.List;
import net.minecraft.tags.BlockTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class FlowerVisualLifecycleAdapter implements VisualLifecycleAdapter {
    public static final FlowerVisualLifecycleAdapter INSTANCE = new FlowerVisualLifecycleAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("flower_visual_growth");

    private FlowerVisualLifecycleAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(BlockTags.SMALL_FLOWERS);
    }

    @Override
    public VisualLifecycleProfile createProfile(BlockState state) {
        return new VisualLifecycleProfile(
                30,
                100,
                180,
                120,
                0.30F,
                1.0F,
                0.86F,
                -0.03F,
                0.72F,
                0.84F);
    }

    @Override
    public List<Block> demoBlocks() {
        return List.of(
                Blocks.DANDELION,
                Blocks.POPPY,
                Blocks.BLUE_ORCHID,
                Blocks.ALLIUM,
                Blocks.AZURE_BLUET,
                Blocks.OXEYE_DAISY,
                Blocks.CORNFLOWER,
                Blocks.LILY_OF_THE_VALLEY,
                Blocks.TORCHFLOWER);
    }

    @Override
    public String supportSummary() {
        return "#minecraft:small_flowers";
    }
}
