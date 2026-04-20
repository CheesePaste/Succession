package com.s.ecoflux.attachment;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record ActivePlantRecord(
        ResourceLocation plantId,
        BlockPos position,
        int pointValue,
        long birthGameTime,
        long expireGameTime,
        @Nullable ResourceLocation sourceBiomeId) {
    private static final String PLANT_ID = "plant_id";
    private static final String POSITION = "position";
    private static final String POINT_VALUE = "point_value";
    private static final String BIRTH_GAME_TIME = "birth_game_time";
    private static final String EXPIRE_GAME_TIME = "expire_game_time";
    private static final String SOURCE_BIOME_ID = "source_biome_id";

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(PLANT_ID, plantId.toString());
        tag.putLong(POSITION, position.asLong());
        tag.putInt(POINT_VALUE, pointValue);
        tag.putLong(BIRTH_GAME_TIME, birthGameTime);
        tag.putLong(EXPIRE_GAME_TIME, expireGameTime);
        if (sourceBiomeId != null) {
            tag.putString(SOURCE_BIOME_ID, sourceBiomeId.toString());
        }
        return tag;
    }

    public static ActivePlantRecord fromTag(CompoundTag tag) {
        String sourceBiome = tag.getString(SOURCE_BIOME_ID);
        return new ActivePlantRecord(
                ResourceLocation.parse(tag.getString(PLANT_ID)),
                BlockPos.of(tag.getLong(POSITION)),
                tag.getInt(POINT_VALUE),
                tag.getLong(BIRTH_GAME_TIME),
                tag.getLong(EXPIRE_GAME_TIME),
                sourceBiome.isEmpty() ? null : ResourceLocation.parse(sourceBiome));
    }
}
