package com.s.ecoflux.client.visual;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class VisualLifecycleRegistry {
    public static final VisualLifecycleRegistry INSTANCE = new VisualLifecycleRegistry(List.of(
            GrassVisualLifecycleAdapter.INSTANCE,
            FlowerVisualLifecycleAdapter.INSTANCE,
            SaplingVisualLifecycleAdapter.INSTANCE,
            GenericVisualLifecycleAdapter.INSTANCE));

    private final List<VisualLifecycleAdapter> adapters;

    public VisualLifecycleRegistry(List<VisualLifecycleAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public Optional<VisualLifecycleAdapter> find(BlockState state) {
        return adapters.stream().filter(adapter -> adapter.matches(state)).findFirst();
    }

    public Block[] colorHandledBlocks() {
        return BuiltInRegistries.BLOCK.stream()
                .filter(block -> {
                    Optional<VisualLifecycleAdapter> adapter = find(block.defaultBlockState());
                    return adapter.isPresent() && adapter.get() != GenericVisualLifecycleAdapter.INSTANCE;
                })
                .toArray(Block[]::new);
    }

    public String supportSummary() {
        return adapters.stream()
                .filter(adapter -> adapter != GenericVisualLifecycleAdapter.INSTANCE)
                .map(VisualLifecycleAdapter::supportSummary)
                .collect(Collectors.joining("; "));
    }
}
