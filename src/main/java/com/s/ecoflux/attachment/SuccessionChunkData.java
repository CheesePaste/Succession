package com.s.ecoflux.attachment;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

public final class SuccessionChunkData implements INBTSerializable<CompoundTag> {
    private static final String CURRENT_BIOME = "current_biome";
    private static final String TARGET_BIOME = "target_biome";
    private static final String PREVIOUS_BIOME = "previous_biome";
    private static final String ACTIVE_PATH_ID = "active_path_id";
    private static final String PROGRESS = "progress";
    private static final String CONSUMING_VALUE = "consuming_value";
    private static final String MAX_PLANT_COUNT = "max_plant_count";
    private static final String CURRENT_PLANT_COUNT = "current_plant_count";
    private static final String LAST_EVALUATION_GAME_TIME = "last_evaluation_game_time";
    private static final String PLANT_QUEUE = "plant_queue";
    private static final String ACTIVE_PLANTS = "active_plants";

    private final ChunkAccess owner;
    private @Nullable ResourceKey<Biome> currentBiome;
    private @Nullable ResourceKey<Biome> targetBiome;
    private @Nullable ResourceKey<Biome> previousBiome;
    private @Nullable ResourceLocation activePathId;
    private double progress;
    private int consumingValue;
    private int maxPlantCount;
    private int currentPlantCount;
    private long lastEvaluationGameTime;
    private final Deque<PlantQueueEntry> plantQueue = new ArrayDeque<>();
    private final Map<BlockPos, ActivePlantRecord> activePlants = new LinkedHashMap<>();

    public SuccessionChunkData(ChunkAccess owner) {
        this.owner = owner;
    }

    public Optional<ResourceKey<Biome>> getCurrentBiome() {
        return Optional.ofNullable(currentBiome);
    }

    public void setCurrentBiome(@Nullable ResourceKey<Biome> currentBiome) {
        this.currentBiome = currentBiome;
        markDirty();
    }

    public Optional<ResourceKey<Biome>> getTargetBiome() {
        return Optional.ofNullable(targetBiome);
    }

    public void setTargetBiome(@Nullable ResourceKey<Biome> targetBiome) {
        this.targetBiome = targetBiome;
        markDirty();
    }

    public Optional<ResourceKey<Biome>> getPreviousBiome() {
        return Optional.ofNullable(previousBiome);
    }

    public void setPreviousBiome(@Nullable ResourceKey<Biome> previousBiome) {
        this.previousBiome = previousBiome;
        markDirty();
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
        markDirty();
    }

    public Optional<ResourceLocation> getActivePathId() {
        return Optional.ofNullable(activePathId);
    }

    public void setActivePathId(@Nullable ResourceLocation activePathId) {
        this.activePathId = activePathId;
        markDirty();
    }

    public int getConsumingValue() {
        return consumingValue;
    }

    public void setConsumingValue(int consumingValue) {
        this.consumingValue = consumingValue;
        markDirty();
    }

    public int getMaxPlantCount() {
        return maxPlantCount;
    }

    public void setMaxPlantCount(int maxPlantCount) {
        this.maxPlantCount = maxPlantCount;
        markDirty();
    }

    public int getCurrentPlantCount() {
        return currentPlantCount;
    }

    public void setCurrentPlantCount(int currentPlantCount) {
        this.currentPlantCount = currentPlantCount;
        markDirty();
    }

    public long getLastEvaluationGameTime() {
        return lastEvaluationGameTime;
    }

    public void setLastEvaluationGameTime(long lastEvaluationGameTime) {
        this.lastEvaluationGameTime = lastEvaluationGameTime;
        markDirty();
    }

    public Collection<PlantQueueEntry> getPlantQueue() {
        return plantQueue;
    }

    public void replacePlantQueue(Collection<PlantQueueEntry> entries) {
        plantQueue.clear();
        plantQueue.addAll(entries);
        markDirty();
    }

    public void enqueuePlant(PlantQueueEntry entry) {
        plantQueue.addLast(entry);
        markDirty();
    }

    public Optional<PlantQueueEntry> pollPlant() {
        PlantQueueEntry entry = plantQueue.pollFirst();
        if (entry != null) {
            markDirty();
        }
        return Optional.ofNullable(entry);
    }

    public Map<BlockPos, ActivePlantRecord> getActivePlants() {
        return activePlants;
    }

    public int getTotalPlantPoints() {
        return activePlants.values().stream().mapToInt(ActivePlantRecord::pointValue).sum();
    }

    public void trackPlant(ActivePlantRecord record) {
        activePlants.put(record.position(), record);
        currentPlantCount = activePlants.size();
        markDirty();
    }

    public @Nullable ActivePlantRecord removeTrackedPlant(BlockPos pos) {
        ActivePlantRecord removed = activePlants.remove(pos);
        if (removed != null) {
            currentPlantCount = activePlants.size();
            markDirty();
        }
        return removed;
    }

    public void clearTrackedPlants() {
        activePlants.clear();
        currentPlantCount = 0;
        markDirty();
    }

    public void clearRuntimeState() {
        activePathId = null;
        progress = 0.0D;
        currentPlantCount = 0;
        lastEvaluationGameTime = 0L;
        plantQueue.clear();
        activePlants.clear();
        markDirty();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        writeBiomeKey(tag, CURRENT_BIOME, currentBiome);
        writeBiomeKey(tag, TARGET_BIOME, targetBiome);
        writeBiomeKey(tag, PREVIOUS_BIOME, previousBiome);
        if (activePathId != null) {
            tag.putString(ACTIVE_PATH_ID, activePathId.toString());
        }
        tag.putDouble(PROGRESS, progress);
        tag.putInt(CONSUMING_VALUE, consumingValue);
        tag.putInt(MAX_PLANT_COUNT, maxPlantCount);
        tag.putInt(CURRENT_PLANT_COUNT, currentPlantCount);
        tag.putLong(LAST_EVALUATION_GAME_TIME, lastEvaluationGameTime);

        ListTag queueTag = new ListTag();
        for (PlantQueueEntry entry : plantQueue) {
            queueTag.add(entry.toTag());
        }
        tag.put(PLANT_QUEUE, queueTag);

        ListTag activePlantTag = new ListTag();
        for (ActivePlantRecord record : activePlants.values()) {
            activePlantTag.add(record.toTag());
        }
        tag.put(ACTIVE_PLANTS, activePlantTag);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        currentBiome = readBiomeKey(tag, CURRENT_BIOME);
        targetBiome = readBiomeKey(tag, TARGET_BIOME);
        previousBiome = readBiomeKey(tag, PREVIOUS_BIOME);
        String storedPathId = tag.getString(ACTIVE_PATH_ID);
        activePathId = storedPathId.isEmpty() ? null : ResourceLocation.parse(storedPathId);
        progress = tag.getDouble(PROGRESS);
        consumingValue = tag.getInt(CONSUMING_VALUE);
        maxPlantCount = tag.getInt(MAX_PLANT_COUNT);
        currentPlantCount = tag.getInt(CURRENT_PLANT_COUNT);
        lastEvaluationGameTime = tag.getLong(LAST_EVALUATION_GAME_TIME);

        plantQueue.clear();
        ListTag queueTag = tag.getList(PLANT_QUEUE, Tag.TAG_COMPOUND);
        for (Tag entryTag : queueTag) {
            plantQueue.addLast(PlantQueueEntry.fromTag((CompoundTag) entryTag));
        }

        activePlants.clear();
        ListTag activePlantTag = tag.getList(ACTIVE_PLANTS, Tag.TAG_COMPOUND);
        for (Tag recordTag : activePlantTag) {
            ActivePlantRecord record = ActivePlantRecord.fromTag((CompoundTag) recordTag);
            activePlants.put(record.position(), record);
        }

        currentPlantCount = activePlants.isEmpty() ? currentPlantCount : activePlants.size();
        markDirty();
    }

    private void markDirty() {
        owner.setUnsaved(true);
    }

    private static void writeBiomeKey(CompoundTag tag, String key, @Nullable ResourceKey<Biome> biomeKey) {
        if (biomeKey != null) {
            tag.putString(key, biomeKey.location().toString());
        }
    }

    private static @Nullable ResourceKey<Biome> readBiomeKey(CompoundTag tag, String key) {
        String biomeId = tag.getString(key);
        if (biomeId.isEmpty()) {
            return null;
        }
        return ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId));
    }
}
