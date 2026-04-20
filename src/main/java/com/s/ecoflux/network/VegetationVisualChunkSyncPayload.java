package com.s.ecoflux.network;

import com.s.ecoflux.EcofluxConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

public record VegetationVisualChunkSyncPayload(
        ResourceLocation dimensionId,
        ChunkPos chunkPos,
        List<VegetationVisualSyncEntry> entries) implements CustomPacketPayload {
    public static final Type<VegetationVisualChunkSyncPayload> TYPE =
            new Type<>(EcofluxConstants.id("vegetation_visual_chunk_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VegetationVisualChunkSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VegetationVisualChunkSyncPayload decode(RegistryFriendlyByteBuf buf) {
                    ResourceLocation dimensionId = buf.readResourceLocation();
                    ChunkPos chunkPos = buf.readChunkPos();
                    int size = buf.readVarInt();
                    List<VegetationVisualSyncEntry> entries = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        entries.add(VegetationVisualSyncEntry.STREAM_CODEC.decode(buf));
                    }
                    return new VegetationVisualChunkSyncPayload(dimensionId, chunkPos, List.copyOf(entries));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, VegetationVisualChunkSyncPayload value) {
                    buf.writeResourceLocation(value.dimensionId());
                    buf.writeChunkPos(value.chunkPos());
                    buf.writeVarInt(value.entries().size());
                    for (VegetationVisualSyncEntry entry : value.entries()) {
                        VegetationVisualSyncEntry.STREAM_CODEC.encode(buf, entry);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
