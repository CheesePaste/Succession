package com.s.succession;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

final class PrototypeSuccessionSavedData extends SavedData {
    private static final String DATA_NAME = Succession.MODID + "_prototype_succession";
    private static final String ENTRIES_KEY = "entries";
    private static final String CHUNK_X_KEY = "chunk_x";
    private static final String CHUNK_Z_KEY = "chunk_z";
    private static final String STATE_KEY = "state";

    private final Map<Long, PrototypeChunkState> chunkStates = new HashMap<>();

    static PrototypeSuccessionSavedData get(ServerLevel level) {
        SavedData.Factory<PrototypeSuccessionSavedData> factory = new SavedData.Factory<>(
                PrototypeSuccessionSavedData::new,
                PrototypeSuccessionSavedData::load);
        return level.getDataStorage().computeIfAbsent(factory, DATA_NAME);
    }

    private static PrototypeSuccessionSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        PrototypeSuccessionSavedData data = new PrototypeSuccessionSavedData();
        ListTag entries = tag.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entries) {
            if (!(rawEntry instanceof CompoundTag entryTag)) {
                continue;
            }

            int chunkX = entryTag.getInt(CHUNK_X_KEY);
            int chunkZ = entryTag.getInt(CHUNK_Z_KEY);
            CompoundTag stateTag = entryTag.getCompound(STATE_KEY);
            data.chunkStates.put(ChunkPos.asLong(chunkX, chunkZ), PrototypeChunkState.fromTag(stateTag));
        }
        return data;
    }

    PrototypeChunkState getOrCreate(ChunkPos chunkPos) {
        return chunkStates.computeIfAbsent(chunkPos.toLong(), ignored -> new PrototypeChunkState());
    }

    PrototypeChunkState get(ChunkPos chunkPos) {
        return chunkStates.get(chunkPos.toLong());
    }

    void remove(ChunkPos chunkPos) {
        if (chunkStates.remove(chunkPos.toLong()) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        for (Map.Entry<Long, PrototypeChunkState> entry : chunkStates.entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt(CHUNK_X_KEY, chunkPos.x);
            entryTag.putInt(CHUNK_Z_KEY, chunkPos.z);
            entryTag.put(STATE_KEY, entry.getValue().toTag());
            entries.add(entryTag);
        }
        tag.put(ENTRIES_KEY, entries);
        return tag;
    }
}
