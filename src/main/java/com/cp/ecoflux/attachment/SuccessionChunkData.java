package com.cp.ecoflux.attachment;

/**
 * Core per-chunk state attached via NeoForge {@code DataAttachment}.
 *
 * <p>Structure: holder for current/target/previous biome keys, succession progress
 * (double), consuming threshold, plant count limits, a plant spawn queue, a
 * vegetation records map keyed by position, and active tree growth sessions.
 * Implements {@code INBTSerializable&lt;CompoundTag&gt;} for full NBT persistence
 * across chunk unload/reload cycles.
 *
 * <p>Role in Ecoflux: this is the central data class for the entire succession
 * system. Every chunk-scoped operation -- initialization, evaluation, planting,
 * pruning, biome transition -- reads from and writes to an instance of this class.
 */

import com.cp.ecoflux.api.data.ActiveVegetationRecord;
import com.cp.ecoflux.api.data.VegetationLifecycleStage;
import com.cp.ecoflux.plant.tree.TreeGrowthSession;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final String ACTIVE_BIOME_RULES_ID = "active_biome_rules_id";
    private static final String PROGRESS = "progress";
    private static final String CONSUMING_VALUE = "consuming_value";
    private static final String MAX_PLANT_COUNT = "max_plant_count";
    private static final String LAST_EVALUATION_GAME_TIME = "last_evaluation_game_time";
    private static final String NEXT_SPAWN_GAME_TIME = "next_spawn_game_time";
    private static final String PLANT_QUEUE = "plant_queue";
    private static final String VEGETATION_RECORDS = "vegetation_records";
    private static final String TREE_GROWTH_SESSIONS = "tree_growth_sessions";

    private static final Set<VegetationLifecycleStage> CONTRIBUTING_STAGES = Set.of(
            VegetationLifecycleStage.GROWING,
            VegetationLifecycleStage.MATURE,
            VegetationLifecycleStage.AGING);

    private final ChunkAccess owner;
    private @Nullable ResourceKey<Biome> currentBiome;
    private @Nullable ResourceKey<Biome> targetBiome;
    private final Deque<ResourceKey<Biome>> biomeStack = new ArrayDeque<>();
    private @Nullable ResourceLocation activePathId;
    private @Nullable ResourceLocation activeBiomeRulesId;
    private double progress;
    private int consumingValue;
    private int maxPlantCount;
    private long lastEvaluationGameTime;
    private long nextSpawnGameTime;
    private final Deque<PlantQueueEntry> plantQueue = new ArrayDeque<>();
    private final Map<BlockPos, ActiveVegetationRecord> vegetationRecords = new LinkedHashMap<>();
    private final Map<BlockPos, TreeGrowthSession> treeGrowthSessions = new LinkedHashMap<>();
    private boolean needsVisualSync;

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

    public void pushBiome(ResourceKey<Biome> biomeKey) {
        if (biomeKey != null) {
            biomeStack.push(biomeKey);
            markDirty();
        }
    }

    public Optional<ResourceKey<Biome>> popBiome() {
        if (biomeStack.isEmpty()) {
            return Optional.empty();
        }
        ResourceKey<Biome> popped = biomeStack.pop();
        markDirty();
        return Optional.of(popped);
    }

    public Optional<ResourceKey<Biome>> peekBiome() {
        return Optional.ofNullable(biomeStack.peek());
    }

    public List<ResourceKey<Biome>> getBiomeStack() {
        return List.copyOf(biomeStack);
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

    public Optional<ResourceLocation> getActiveBiomeRulesId() {
        return Optional.ofNullable(activeBiomeRulesId);
    }

    public void setActiveBiomeRulesId(@Nullable ResourceLocation biomeRulesId) {
        this.activeBiomeRulesId = biomeRulesId;
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
        return vegetationRecords.size();
    }

    public long getLastEvaluationGameTime() {
        return lastEvaluationGameTime;
    }

    public void setLastEvaluationGameTime(long lastEvaluationGameTime) {
        this.lastEvaluationGameTime = lastEvaluationGameTime;
        markDirty();
    }

    public long getNextSpawnGameTime() {
        return nextSpawnGameTime;
    }

    public void setNextSpawnGameTime(long nextSpawnGameTime) {
        this.nextSpawnGameTime = nextSpawnGameTime;
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

    public Map<BlockPos, ActiveVegetationRecord> getVegetationRecords() {
        return vegetationRecords;
    }

    public int getTotalVegetationPoints() {
        return vegetationRecords.values().stream().mapToInt(ActiveVegetationRecord::currentPointValue).sum();
    }

    public int getContributingVegetationPoints() {
        return vegetationRecords.values().stream()
                .filter(record -> CONTRIBUTING_STAGES.contains(record.lifeStage()))
                .mapToInt(ActiveVegetationRecord::currentPointValue)
                .sum();
    }

    public boolean hasContributingVegetation() {
        return vegetationRecords.values().stream()
                .anyMatch(record -> CONTRIBUTING_STAGES.contains(record.lifeStage()));
    }

    public long countContributingVegetation() {
        return vegetationRecords.values().stream()
                .filter(record -> CONTRIBUTING_STAGES.contains(record.lifeStage()))
                .count();
    }

    public void trackVegetation(ActiveVegetationRecord record) {
        vegetationRecords.put(record.position(), record);
        needsVisualSync = true;
        markDirty();
    }

    /** Updates lastObservedGameTime without triggering visual sync. */
    public void touchVegetation(ActiveVegetationRecord oldRecord, long gameTime) {
        vegetationRecords.put(oldRecord.position(),
                oldRecord.withObservation(oldRecord.lifeStage(), oldRecord.currentPointValue(), gameTime));
        // does NOT set needsVisualSync
        markDirty();
    }

    public @Nullable ActiveVegetationRecord removeVegetation(BlockPos pos) {
        ActiveVegetationRecord removed = vegetationRecords.remove(pos);
        if (removed != null) {
            needsVisualSync = true;
            markDirty();
        }
        return removed;
    }

    public void clearVegetationRecords() {
        vegetationRecords.clear();
        markDirty();
    }

    public Map<BlockPos, TreeGrowthSession> getTreeGrowthSessions() {
        return treeGrowthSessions;
    }

    public void addTreeGrowthSession(BlockPos pos, TreeGrowthSession session) {
        treeGrowthSessions.put(pos, session);
        markDirty();
    }

    public void removeTreeGrowthSession(BlockPos pos) {
        treeGrowthSessions.remove(pos);
        markDirty();
    }

    public boolean hasTreeGrowthSessions() {
        return !treeGrowthSessions.isEmpty();
    }

    public boolean isNeedsVisualSync() {
        return needsVisualSync;
    }

    public void markNeedsVisualSync() {
        this.needsVisualSync = true;
    }

    public void clearNeedsVisualSync() {
        this.needsVisualSync = false;
    }

    public void clearRuntimeState() {
        activePathId = null;
        activeBiomeRulesId = null;
        targetBiome = null;
        consumingValue = 0;
        maxPlantCount = 0;
        progress = 0.0D;
        lastEvaluationGameTime = 0L;
        nextSpawnGameTime = 0L;
        plantQueue.clear();
        vegetationRecords.clear();
        treeGrowthSessions.clear();
        markDirty();
    }

    public void softReset() {
        activePathId = null;
        activeBiomeRulesId = null;
        targetBiome = null;
        consumingValue = 0;
        maxPlantCount = 0;
        progress = 0.0D;
        lastEvaluationGameTime = 0L;
        nextSpawnGameTime = 0L;
        plantQueue.clear();
        markDirty();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        writeBiomeKey(tag, CURRENT_BIOME, currentBiome);
        writeBiomeKey(tag, TARGET_BIOME, targetBiome);
        
        ListTag stackTag = new ListTag();
        for (ResourceKey<Biome> biomeKey : biomeStack) {
            // Because it's a stack (Deque), iterating normally goes top to bottom.
            // We want to write it out in order, but ListTag doesn't matter as long as we read it back properly.
            net.minecraft.nbt.StringTag stringTag = net.minecraft.nbt.StringTag.valueOf(biomeKey.location().toString());
            stackTag.add(stringTag);
        }
        tag.put(PREVIOUS_BIOME, stackTag); // Reuse PREVIOUS_BIOME key for backwards compatibility conceptually, or just use it.
        
        if (activePathId != null) {
            tag.putString(ACTIVE_PATH_ID, activePathId.toString());
        }
        if (activeBiomeRulesId != null) {
            tag.putString(ACTIVE_BIOME_RULES_ID, activeBiomeRulesId.toString());
        }
        tag.putDouble(PROGRESS, progress);
        tag.putInt(CONSUMING_VALUE, consumingValue);
        tag.putInt(MAX_PLANT_COUNT, maxPlantCount);
        tag.putLong(LAST_EVALUATION_GAME_TIME, lastEvaluationGameTime);
        tag.putLong(NEXT_SPAWN_GAME_TIME, nextSpawnGameTime);

        ListTag queueTag = new ListTag();
        for (PlantQueueEntry entry : plantQueue) {
            queueTag.add(entry.toTag());
        }
        tag.put(PLANT_QUEUE, queueTag);

        ListTag vegetationTag = new ListTag();
        for (ActiveVegetationRecord record : vegetationRecords.values()) {
            vegetationTag.add(record.toTag());
        }
        tag.put(VEGETATION_RECORDS, vegetationTag);

        ListTag sessionsTag = new ListTag();
        for (TreeGrowthSession session : treeGrowthSessions.values()) {
            sessionsTag.add(session.toTag());
        }
        tag.put(TREE_GROWTH_SESSIONS, sessionsTag);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        currentBiome = readBiomeKey(tag, CURRENT_BIOME);
        targetBiome = readBiomeKey(tag, TARGET_BIOME);
        
        biomeStack.clear();
        if (tag.contains(PREVIOUS_BIOME, Tag.TAG_LIST)) {
            ListTag stackTag = tag.getList(PREVIOUS_BIOME, Tag.TAG_STRING);
            for (Tag t : stackTag) {
                String biomeId = t.getAsString();
                if (!biomeId.isEmpty()) {
                    biomeStack.addLast(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId)));
                }
            }
        } else if (tag.contains(PREVIOUS_BIOME, Tag.TAG_STRING)) {
            ResourceKey<Biome> prev = readBiomeKey(tag, PREVIOUS_BIOME);
            if (prev != null) {
                biomeStack.push(prev);
            }
        }
        
        String storedPathId = tag.getString(ACTIVE_PATH_ID);
        activePathId = storedPathId.isEmpty() ? null : ResourceLocation.parse(storedPathId);
        String storedBiomeRulesId = tag.getString(ACTIVE_BIOME_RULES_ID);
        activeBiomeRulesId = storedBiomeRulesId.isEmpty() ? null : ResourceLocation.parse(storedBiomeRulesId);
        progress = tag.getDouble(PROGRESS);
        consumingValue = tag.getInt(CONSUMING_VALUE);
        maxPlantCount = tag.getInt(MAX_PLANT_COUNT);
        lastEvaluationGameTime = tag.getLong(LAST_EVALUATION_GAME_TIME);
        nextSpawnGameTime = tag.getLong(NEXT_SPAWN_GAME_TIME);

        plantQueue.clear();
        ListTag queueTag = tag.getList(PLANT_QUEUE, Tag.TAG_COMPOUND);
        for (Tag entryTag : queueTag) {
            plantQueue.addLast(PlantQueueEntry.fromTag((CompoundTag) entryTag));
        }

        vegetationRecords.clear();
        ListTag vegetationTag = tag.getList(VEGETATION_RECORDS, Tag.TAG_COMPOUND);
        for (Tag recordTag : vegetationTag) {
            ActiveVegetationRecord record = ActiveVegetationRecord.fromTag((CompoundTag) recordTag);
            vegetationRecords.put(record.position(), record);
        }

        treeGrowthSessions.clear();
        ListTag sessionsTag = tag.getList(TREE_GROWTH_SESSIONS, Tag.TAG_COMPOUND);
        for (Tag sessionTag : sessionsTag) {
            TreeGrowthSession session = TreeGrowthSession.fromTag((CompoundTag) sessionTag);
            treeGrowthSessions.put(session.saplingPos(), session);
        }

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
