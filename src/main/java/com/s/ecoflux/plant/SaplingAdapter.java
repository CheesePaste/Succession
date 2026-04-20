package com.s.ecoflux.plant;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class SaplingAdapter implements VegetationTypeAdapter {
    public static final SaplingAdapter INSTANCE = new SaplingAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("sapling");

    private SaplingAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public VegetationCategory category() {
        return VegetationCategory.SAPLING;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.getBlock() instanceof SaplingBlock || state.is(BlockTags.SAPLINGS);
    }

    @Override
    public ActiveVegetationRecord captureBirth(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            long gameTime,
            Optional<ResourceLocation> sourceBiomeId,
            Optional<ResourceLocation> sourcePathId) {
        return new ActiveVegetationRecord(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                typeId(),
                VegetationCategory.SAPLING,
                pos.immutable(),
                VegetationLifecycleStage.BORN,
                gameTime,
                gameTime,
                gameTime + 144000L,
                2,
                1,
                sourceBiomeId.orElse(null),
                sourcePathId.orElse(null));
    }

    @Override
    public VegetationObservation observe(ServerLevel level, ActiveVegetationRecord record, BlockState state, long gameTime) {
        if (state.isAir()) {
            return VegetationObservation.absent("Sapling disappeared.");
        }

        Optional<VegetationTransformation> transformation = detectTransformation(level, record, state, gameTime);
        if (transformation.isPresent()) {
            return new VegetationObservation(
                    true,
                    VegetationLifecycleStage.TRANSFORMED,
                    transformation.get().targetCurrentPointValue(),
                    true,
                    false,
                    transformation,
                    "Sapling transformed into tree structure.");
        }

        if (!matches(state)) {
            return VegetationObservation.absent("Sapling block was replaced by an unsupported block.");
        }

        long age = Math.max(0L, gameTime - record.birthGameTime());
        VegetationLifecycleStage stage = age < 1200L ? VegetationLifecycleStage.JUVENILE : VegetationLifecycleStage.GROWING;
        int pointValue = age < 24000L ? 1 : 2;
        return new VegetationObservation(
                true,
                stage,
                pointValue,
                false,
                false,
                Optional.empty(),
                "Observed sapling at age " + age + " ticks.");
    }

    @Override
    public VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) {
        long age = Math.max(0L, gameTime - record.birthGameTime());
        long totalLifetime = Math.max(1L, record.expireGameTime() - record.birthGameTime());
        return switch (record.lifeStage()) {
            case BORN, JUVENILE -> new VegetationVisualState(VegetationLifecycleStage.JUVENILE, progress(age, 0L, 1200L));
            case GROWING -> new VegetationVisualState(VegetationLifecycleStage.GROWING, progress(age, 1200L, totalLifetime));
            case MATURE -> new VegetationVisualState(VegetationLifecycleStage.MATURE, 1.0F);
            case AGING -> new VegetationVisualState(VegetationLifecycleStage.AGING, progress(age, 24000L, totalLifetime));
            default -> new VegetationVisualState(record.lifeStage(), 1.0F);
        };
    }

    @Override
    public Optional<VegetationTransformation> detectTransformation(
            ServerLevel level,
            ActiveVegetationRecord record,
            BlockState state,
            long gameTime) {
        if (matches(state)) {
            return Optional.empty();
        }

        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return Optional.of(new VegetationTransformation(
                    blockId,
                    TreeStructureAdapter.TYPE_ID,
                    VegetationCategory.TREE,
                    VegetationLifecycleStage.MATURE,
                    4,
                    5));
        }

        return Optional.empty();
    }

    private static float progress(long age, long start, long endExclusive) {
        if (endExclusive <= start) {
            return 1.0F;
        }
        return (float) (Math.max(0L, age - start)) / (float) (endExclusive - start);
    }
}
