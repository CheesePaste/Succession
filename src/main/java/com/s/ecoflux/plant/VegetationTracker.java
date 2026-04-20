package com.s.ecoflux.plant;

import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.network.ModNetworking;
import com.s.ecoflux.network.VegetationVisualSyncEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public final class VegetationTracker {
    public static final VegetationTracker INSTANCE = new VegetationTracker(List.of(
            SaplingAdapter.INSTANCE,
            TreeStructureAdapter.INSTANCE,
            SimplePlantAdapter.INSTANCE));

    private final List<VegetationTypeAdapter> adapters;

    public VegetationTracker(List<VegetationTypeAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public List<VegetationTypeAdapter> adapters() {
        return adapters;
    }

    public Optional<VegetationTypeAdapter> findAdapter(BlockState state) {
        return adapters.stream().filter(adapter -> adapter.matches(state)).findFirst();
    }

    public Optional<VegetationTypeAdapter> findAdapter(ResourceLocation adapterTypeId) {
        return adapters.stream().filter(adapter -> adapter.typeId().equals(adapterTypeId)).findFirst();
    }

    public String inspect(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Optional<VegetationTypeAdapter> adapter = findAdapter(state);
        if (adapter.isEmpty()) {
            return "No vegetation adapter matches " + pos + " (" + state.getBlock().getDescriptionId() + ").";
        }

        ActiveVegetationRecord preview = adapter.get().captureBirth(level, pos, state, level.getGameTime(), Optional.empty(), Optional.empty());
        VegetationObservation observation = adapter.get().observe(level, preview, state, level.getGameTime());
        return "Adapter=" + adapter.get().typeId()
                + " category=" + adapter.get().category()
                + " stage=" + observation.stage()
                + " present=" + observation.present()
                + " points=" + observation.currentPointValue()
                + " detail=" + observation.detail();
    }

    public String trackAt(
            ServerLevel level,
            LevelChunk chunk,
            BlockPos pos,
            Optional<ResourceLocation> sourceBiomeId,
            Optional<ResourceLocation> sourcePathId) {
        BlockState state = level.getBlockState(pos);
        Optional<VegetationTypeAdapter> adapter = findAdapter(state);
        if (adapter.isEmpty()) {
            return "Track skipped for " + pos + ": no vegetation adapter matches " + state.getBlock().getDescriptionId() + ".";
        }

        ActiveVegetationRecord record = adapter.get().captureBirth(
                level,
                pos,
                state,
                level.getGameTime(),
                sourceBiomeId,
                sourcePathId);
        chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA).trackVegetation(record);
        ModNetworking.syncChunkToTracking(level, chunk);
        return "Tracked vegetation at " + pos + " with adapter " + adapter.get().typeId() + ".";
    }

    public String observeTracked(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        ObserveResult result = observeTrackedInternal(level, chunk, pos);
        ModNetworking.syncChunkToTracking(level, chunk);
        return result.message();
    }

    public String observeChunk(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getVegetationRecords().isEmpty()) {
            return "Observe chunk skipped for " + chunk.getPos() + ": no tracked vegetation records.";
        }

        List<BlockPos> snapshot = new ArrayList<>(chunkData.getVegetationRecords().keySet());
        int removed = 0;
        int transformed = 0;
        int updated = 0;
        for (BlockPos pos : snapshot) {
            ObserveResult result = observeTrackedInternal(level, chunk, pos);
            if (result.removed()) {
                removed++;
            } else if (result.transformed()) {
                transformed++;
            } else if (result.updated()) {
                updated++;
            }
        }

        ModNetworking.syncChunkToTracking(level, chunk);
        return "Observed chunk " + chunk.getPos()
                + ": updated=" + updated
                + " transformed=" + transformed
                + " removed=" + removed
                + " tracked=" + chunkData.getVegetationRecords().size() + ".";
    }

    public List<VegetationVisualSyncEntry> buildVisualSyncEntries(LevelChunk chunk, long gameTime) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        List<VegetationVisualSyncEntry> entries = new ArrayList<>(chunkData.getVegetationRecords().size());
        for (ActiveVegetationRecord record : chunkData.getVegetationRecords().values()) {
            Optional<VegetationTypeAdapter> adapter = findAdapter(record.adapterType());
            if (adapter.isEmpty()) {
                continue;
            }

            VegetationVisualState visualState = adapter.get().visualState(record, gameTime);
            entries.add(new VegetationVisualSyncEntry(record.position(), visualState.stage(), visualState.stageProgress()));
        }
        return List.copyOf(entries);
    }

    private ObserveResult observeTrackedInternal(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        ActiveVegetationRecord record = chunkData.getVegetationRecords().get(pos);
        if (record == null) {
            return ObserveResult.noop("Observe skipped for " + pos + ": no tracked vegetation record.");
        }

        BlockState state = level.getBlockState(pos);
        Optional<VegetationTypeAdapter> adapter = findAdapter(record.adapterType());
        if (adapter.isEmpty()) {
            return ObserveResult.noop("Observe failed for " + pos + ": adapter " + record.adapterType() + " is not registered.");
        }

        VegetationObservation observation = adapter.get().observe(level, record, state, level.getGameTime());
        if (!observation.present()) {
            chunkData.removeVegetation(pos);
            return new ObserveResult(
                    "Observed " + pos + ": vegetation died/vanished. detail=" + observation.detail(),
                    true,
                    false,
                    false);
        }

        if (observation.transformation().isPresent()) {
            VegetationTransformation transformation = observation.transformation().get();
            ActiveVegetationRecord transformedRecord = record.withTransformation(
                    transformation.targetVegetationId(),
                    transformation.targetAdapterType(),
                    transformation.targetCategory(),
                    transformation.targetStage(),
                    transformation.targetBasePointValue(),
                    transformation.targetCurrentPointValue(),
                    level.getGameTime());
            chunkData.trackVegetation(transformedRecord);
            return new ObserveResult(
                    "Observed " + pos + ": transformed to " + transformation.targetVegetationId() + ".",
                    false,
                    true,
                    true);
        }

        chunkData.trackVegetation(record.withObservation(
                observation.stage(),
                observation.currentPointValue(),
                level.getGameTime()));
        return new ObserveResult(
                "Observed " + pos + ": stage=" + observation.stage()
                        + " points=" + observation.currentPointValue()
                        + " detail=" + observation.detail(),
                false,
                false,
                true);
    }

    public String untrack(LevelChunk chunk, BlockPos pos) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        ActiveVegetationRecord removed = chunkData.removeVegetation(pos);
        return removed == null
                ? "Untrack skipped for " + pos + ": no tracked vegetation record."
                : "Untracked vegetation at " + pos + ".";
    }

    public String describeChunk(LevelChunk chunk) {
        SuccessionChunkData chunkData = chunk.getData(com.s.ecoflux.init.ModAttachments.SUCCESSION_CHUNK_DATA);
        if (chunkData.getVegetationRecords().isEmpty()) {
            return "Chunk " + chunk.getPos() + " has no tracked vegetation records.";
        }

        String joined = chunkData.getVegetationRecords().values().stream()
                .limit(8)
                .map(record -> record.position() + ":" + record.vegetationId() + ":" + record.lifeStage() + ":" + record.currentPointValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        return "Chunk " + chunk.getPos()
                + " trackedVegetation=" + chunkData.getVegetationRecords().size()
                + " totalPoints=" + chunkData.getTotalVegetationPoints()
                + " samples=[" + joined + "]";
    }

    private record ObserveResult(String message, boolean removed, boolean transformed, boolean updated) {
        private static ObserveResult noop(String message) {
            return new ObserveResult(message, false, false, false);
        }
    }
}
