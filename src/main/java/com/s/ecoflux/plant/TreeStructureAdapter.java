package com.s.ecoflux.plant;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class TreeStructureAdapter implements VegetationTypeAdapter {
    public static final TreeStructureAdapter INSTANCE = new TreeStructureAdapter();
    public static final ResourceLocation TYPE_ID = EcofluxConstants.id("tree_structure");

    private TreeStructureAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public VegetationCategory category() {
        return VegetationCategory.TREE;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
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
                VegetationCategory.TREE,
                pos.immutable(),
                VegetationLifecycleStage.MATURE,
                gameTime,
                gameTime,
                gameTime + 288000L,
                4,
                5,
                sourceBiomeId.orElse(null),
                sourcePathId.orElse(null));
    }

    @Override
    public VegetationObservation observe(ServerLevel level, ActiveVegetationRecord record, BlockState state, long gameTime) {
        if (state.isAir() || !matches(state)) {
            return VegetationObservation.absent("树木结构已不存在。");
        }

        long age = Math.max(0L, gameTime - record.birthGameTime());
        VegetationLifecycleStage stage = age < 96000L ? VegetationLifecycleStage.MATURE : VegetationLifecycleStage.AGING;
        int pointValue = stage == VegetationLifecycleStage.MATURE ? 5 : 3;
        return new VegetationObservation(
                true,
                stage,
                pointValue,
                stage == VegetationLifecycleStage.MATURE,
                stage == VegetationLifecycleStage.AGING,
                Optional.empty(),
                "树木结构年龄为 " + age + " tick。");
    }

    @Override
    public VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) {
        long age = Math.max(0L, gameTime - record.birthGameTime());
        long totalLifetime = Math.max(1L, record.expireGameTime() - record.birthGameTime());
        return switch (record.lifeStage()) {
            case MATURE -> new VegetationVisualState(VegetationLifecycleStage.MATURE, progress(age, 0L, 96000L));
            case AGING -> new VegetationVisualState(VegetationLifecycleStage.AGING, progress(age, 96000L, totalLifetime));
            default -> new VegetationVisualState(record.lifeStage(), 1.0F);
        };
    }

    private static float progress(long age, long start, long endExclusive) {
        if (endExclusive <= start) {
            return 1.0F;
        }
        return (float) (Math.max(0L, age - start)) / (float) (endExclusive - start);
    }
}
