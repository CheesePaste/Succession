package com.s.ecoflux.attachment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record PlantQueueEntry(ResourceLocation plantId, int pointValue, int weight, long maxAgeTicks) {
    private static final String PLANT_ID = "plant_id";
    private static final String POINT_VALUE = "point_value";
    private static final String WEIGHT = "weight";
    private static final String MAX_AGE_TICKS = "max_age_ticks";

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(PLANT_ID, plantId.toString());
        tag.putInt(POINT_VALUE, pointValue);
        tag.putInt(WEIGHT, weight);
        tag.putLong(MAX_AGE_TICKS, maxAgeTicks);
        return tag;
    }

    public static PlantQueueEntry fromTag(CompoundTag tag) {
        return new PlantQueueEntry(
                ResourceLocation.parse(tag.getString(PLANT_ID)),
                tag.getInt(POINT_VALUE),
                tag.getInt(WEIGHT),
                tag.getLong(MAX_AGE_TICKS));
    }
}
