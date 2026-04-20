package com.s.ecoflux.client.visual;

import com.s.ecoflux.EcofluxConstants;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SaplingVisualLifecycleAdapter implements VisualLifecycleAdapter {
    public static final SaplingVisualLifecycleAdapter INSTANCE = new SaplingVisualLifecycleAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("sapling_visual_growth");

    private SaplingVisualLifecycleAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(BlockTags.SAPLINGS);
    }

    @Override
    public VisualLifecycleProfile createProfile(BlockState state) {
        return new VisualLifecycleProfile(
                50,
                180,
                240,
                180,
                0.45F,
                1.08F,
                0.94F,
                -0.08F,
                0.70F,
                0.76F);
    }

    @Override
    public List<Block> demoBlocks() {
        return List.of(
                Blocks.OAK_SAPLING,
                Blocks.BIRCH_SAPLING,
                Blocks.SPRUCE_SAPLING,
                Blocks.JUNGLE_SAPLING,
                Blocks.ACACIA_SAPLING,
                Blocks.DARK_OAK_SAPLING,
                Blocks.CHERRY_SAPLING);
    }

    @Override
    public String supportSummary() {
        return "#minecraft:saplings";
    }
}
