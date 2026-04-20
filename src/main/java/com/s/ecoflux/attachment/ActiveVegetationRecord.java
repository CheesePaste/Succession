package com.s.ecoflux.attachment;

import com.s.ecoflux.plant.VegetationCategory;
import com.s.ecoflux.plant.VegetationLifecycleStage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record ActiveVegetationRecord(
        ResourceLocation vegetationId,
        ResourceLocation adapterType,
        VegetationCategory category,
        BlockPos position,
        VegetationLifecycleStage lifeStage,
        long birthGameTime,
        long lastObservedGameTime,
        long expireGameTime,
        int basePointValue,
        int currentPointValue,
        @Nullable ResourceLocation sourceBiomeId,
        @Nullable ResourceLocation sourcePathId) {
    private static final String VEGETATION_ID = "vegetation_id";
    private static final String ADAPTER_TYPE = "adapter_type";
    private static final String CATEGORY = "category";
    private static final String POSITION = "position";
    private static final String LIFE_STAGE = "life_stage";
    private static final String BIRTH_GAME_TIME = "birth_game_time";
    private static final String LAST_OBSERVED_GAME_TIME = "last_observed_game_time";
    private static final String EXPIRE_GAME_TIME = "expire_game_time";
    private static final String BASE_POINT_VALUE = "base_point_value";
    private static final String CURRENT_POINT_VALUE = "current_point_value";
    private static final String SOURCE_BIOME_ID = "source_biome_id";
    private static final String SOURCE_PATH_ID = "source_path_id";

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(VEGETATION_ID, vegetationId.toString());
        tag.putString(ADAPTER_TYPE, adapterType.toString());
        tag.putString(CATEGORY, category.name());
        tag.putLong(POSITION, position.asLong());
        tag.putString(LIFE_STAGE, lifeStage.name());
        tag.putLong(BIRTH_GAME_TIME, birthGameTime);
        tag.putLong(LAST_OBSERVED_GAME_TIME, lastObservedGameTime);
        tag.putLong(EXPIRE_GAME_TIME, expireGameTime);
        tag.putInt(BASE_POINT_VALUE, basePointValue);
        tag.putInt(CURRENT_POINT_VALUE, currentPointValue);
        if (sourceBiomeId != null) {
            tag.putString(SOURCE_BIOME_ID, sourceBiomeId.toString());
        }
        if (sourcePathId != null) {
            tag.putString(SOURCE_PATH_ID, sourcePathId.toString());
        }
        return tag;
    }

    public ActiveVegetationRecord withObservation(VegetationLifecycleStage nextStage, int nextPointValue, long observedGameTime) {
        return new ActiveVegetationRecord(
                vegetationId,
                adapterType,
                category,
                position,
                nextStage,
                birthGameTime,
                observedGameTime,
                expireGameTime,
                basePointValue,
                nextPointValue,
                sourceBiomeId,
                sourcePathId);
    }

    public ActiveVegetationRecord withTransformation(
            ResourceLocation nextVegetationId,
            ResourceLocation nextAdapterType,
            VegetationCategory nextCategory,
            VegetationLifecycleStage nextStage,
            int nextBasePointValue,
            int nextCurrentPointValue,
            long observedGameTime) {
        return new ActiveVegetationRecord(
                nextVegetationId,
                nextAdapterType,
                nextCategory,
                position,
                nextStage,
                birthGameTime,
                observedGameTime,
                expireGameTime,
                nextBasePointValue,
                nextCurrentPointValue,
                sourceBiomeId,
                sourcePathId);
    }

    public static ActiveVegetationRecord fromTag(CompoundTag tag) {
        String sourceBiome = tag.getString(SOURCE_BIOME_ID);
        String sourcePath = tag.getString(SOURCE_PATH_ID);
        return new ActiveVegetationRecord(
                ResourceLocation.parse(tag.getString(VEGETATION_ID)),
                ResourceLocation.parse(tag.getString(ADAPTER_TYPE)),
                VegetationCategory.valueOf(tag.getString(CATEGORY)),
                BlockPos.of(tag.getLong(POSITION)),
                VegetationLifecycleStage.valueOf(tag.getString(LIFE_STAGE)),
                tag.getLong(BIRTH_GAME_TIME),
                tag.getLong(LAST_OBSERVED_GAME_TIME),
                tag.getLong(EXPIRE_GAME_TIME),
                tag.getInt(BASE_POINT_VALUE),
                tag.getInt(CURRENT_POINT_VALUE),
                sourceBiome.isEmpty() ? null : ResourceLocation.parse(sourceBiome),
                sourcePath.isEmpty() ? null : ResourceLocation.parse(sourcePath));
    }
}
