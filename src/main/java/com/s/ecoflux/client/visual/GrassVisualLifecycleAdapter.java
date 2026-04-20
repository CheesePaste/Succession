package com.s.ecoflux.client.visual;

import com.s.ecoflux.EcofluxConstants;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class GrassVisualLifecycleAdapter implements VisualLifecycleAdapter {
    public static final GrassVisualLifecycleAdapter INSTANCE = new GrassVisualLifecycleAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("grass_visual_growth");

    private GrassVisualLifecycleAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.DEAD_BUSH);
    }

    @Override
    public VisualLifecycleProfile createProfile(BlockState state) {
        return new VisualLifecycleProfile(
                40,
                120,
                160,
                120,
                0.35F,
                1.0F,
                0.92F,
                -0.08F,
                0.55F,
                0.72F);
    }

    @Override
    public List<Block> demoBlocks() {
        return List.of(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.DEAD_BUSH);
    }

    @Override
    public String supportSummary() {
        return "minecraft:short_grass, minecraft:fern, minecraft:dead_bush";
    }
}
