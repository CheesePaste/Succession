package com.s.ecoflux.network;

import com.s.ecoflux.plant.VegetationLifecycleStage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record VegetationVisualSyncEntry(BlockPos pos, VegetationLifecycleStage stage, float stageProgress) {
    public static final StreamCodec<RegistryFriendlyByteBuf, VegetationVisualSyncEntry> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VegetationVisualSyncEntry decode(RegistryFriendlyByteBuf buf) {
            return new VegetationVisualSyncEntry(
                    buf.readBlockPos(),
                    buf.readEnum(VegetationLifecycleStage.class),
                    Mth.clamp(buf.readFloat(), 0.0F, 1.0F));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, VegetationVisualSyncEntry value) {
            buf.writeBlockPos(value.pos());
            buf.writeEnum(value.stage());
            buf.writeFloat(Mth.clamp(value.stageProgress(), 0.0F, 1.0F));
        }
    };
}
