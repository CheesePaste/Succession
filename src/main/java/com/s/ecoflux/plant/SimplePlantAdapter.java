package com.s.ecoflux.plant;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SimplePlantAdapter implements VegetationTypeAdapter {
    public static final SimplePlantAdapter INSTANCE = new SimplePlantAdapter();
    private static final ResourceLocation TYPE_ID = EcofluxConstants.id("simple_plant");

    private SimplePlantAdapter() {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public VegetationCategory category() {
        return VegetationCategory.OTHER;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.LARGE_FERN)
                || state.is(BlockTags.SMALL_FLOWERS)
                || state.is(BlockTags.TALL_FLOWERS)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM);
    }

    @Override
    public ActiveVegetationRecord captureBirth(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            long gameTime,
            Optional<ResourceLocation> sourceBiomeId,
            Optional<ResourceLocation> sourcePathId) {
        VegetationCategory derivedCategory = deriveCategory(state);
        int basePointValue = derivedCategory == VegetationCategory.FLOWER ? 2 : 1;
        return new ActiveVegetationRecord(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                typeId(),
                derivedCategory,
                pos.immutable(),
                VegetationLifecycleStage.BORN,
                gameTime,
                gameTime,
                gameTime + 72000L,
                basePointValue,
                Math.max(0, basePointValue / 2),
                sourceBiomeId.orElse(null),
                sourcePathId.orElse(null));
    }

    @Override
    public VegetationObservation observe(ServerLevel level, ActiveVegetationRecord record, BlockState state, long gameTime) {
        if (state.isAir() || !matches(state)) {
            return VegetationObservation.absent("Simple plant is no longer present.");
        }

        long age = Math.max(0L, gameTime - record.birthGameTime());
        VegetationLifecycleStage stage;
        int pointValue;
        boolean mature = false;
        boolean aging = false;
        if (age < 200L) {
            stage = VegetationLifecycleStage.BORN;
            pointValue = Math.max(0, record.basePointValue() / 2);
        } else if (age < 1200L) {
            stage = VegetationLifecycleStage.GROWING;
            pointValue = record.basePointValue();
        } else if (age < 48000L) {
            stage = VegetationLifecycleStage.MATURE;
            pointValue = record.basePointValue() + 1;
            mature = true;
        } else {
            stage = VegetationLifecycleStage.AGING;
            pointValue = Math.max(1, record.basePointValue());
            aging = true;
        }

        return new VegetationObservation(
                true,
                stage,
                pointValue,
                mature,
                aging,
                Optional.empty(),
                "Observed simple plant at age " + age + " ticks.");
    }

    @Override
    public VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) {
        long age = Math.max(0L, gameTime - record.birthGameTime());
        return switch (record.lifeStage()) {
            case BORN -> new VegetationVisualState(VegetationLifecycleStage.BORN, progress(age, 0L, 200L));
            case GROWING -> new VegetationVisualState(VegetationLifecycleStage.GROWING, progress(age, 200L, 1200L));
            case MATURE -> new VegetationVisualState(VegetationLifecycleStage.MATURE, progress(age, 1200L, 48000L));
            case AGING -> new VegetationVisualState(VegetationLifecycleStage.AGING, progress(age, 48000L, record.expireGameTime() - record.birthGameTime()));
            default -> new VegetationVisualState(record.lifeStage(), 1.0F);
        };
    }

    private static VegetationCategory deriveCategory(BlockState state) {
        if (state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS) || state.is(BlockTags.FLOWERS)) {
            return VegetationCategory.FLOWER;
        }
        if (state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)) {
            return VegetationCategory.MUSHROOM;
        }
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) {
            return VegetationCategory.GROUND_COVER;
        }
        return VegetationCategory.OTHER;
    }

    private static float progress(long age, long start, long endExclusive) {
        if (endExclusive <= start) {
            return 1.0F;
        }
        return (float) (Math.max(0L, age - start)) / (float) (endExclusive - start);
    }
}
