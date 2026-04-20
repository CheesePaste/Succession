package com.s.ecoflux.client.visual;

import com.s.ecoflux.EcofluxConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public final class GenericVisualLifecycleAdapter implements VisualLifecycleAdapter {
    public static final GenericVisualLifecycleAdapter INSTANCE = new GenericVisualLifecycleAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("generic_tracked_visual");

    private GenericVisualLifecycleAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public boolean matches(BlockState state) {
        return !state.isAir();
    }

    @Override
    public VisualLifecycleProfile createProfile(BlockState state) {
        return new VisualLifecycleProfile(
                40,
                120,
                180,
                140,
                0.35F,
                1.0F,
                0.90F,
                -0.05F,
                0.75F,
                0.82F);
    }

    @Override
    public String supportSummary() {
        return "any tracked non-air block";
    }
}
