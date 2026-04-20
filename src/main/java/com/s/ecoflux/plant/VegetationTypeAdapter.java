package com.s.ecoflux.plant;

import com.s.ecoflux.attachment.ActiveVegetationRecord;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public interface VegetationTypeAdapter {
    ResourceLocation typeId();

    VegetationCategory category();

    boolean matches(BlockState state);

    ActiveVegetationRecord captureBirth(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            long gameTime,
            Optional<ResourceLocation> sourceBiomeId,
            Optional<ResourceLocation> sourcePathId);

    VegetationObservation observe(ServerLevel level, ActiveVegetationRecord record, BlockState state, long gameTime);

    default VegetationVisualState visualState(ActiveVegetationRecord record, long gameTime) {
        return new VegetationVisualState(record.lifeStage(), 1.0F);
    }

    default Optional<VegetationTransformation> detectTransformation(
            ServerLevel level,
            ActiveVegetationRecord record,
            BlockState state,
            long gameTime) {
        return Optional.empty();
    }
}
