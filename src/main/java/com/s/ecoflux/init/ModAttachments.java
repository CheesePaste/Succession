package com.s.ecoflux.init;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.SuccessionChunkData;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, EcofluxConstants.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SuccessionChunkData>> SUCCESSION_CHUNK_DATA =
            ATTACHMENT_TYPES.register("succession_chunk_data", () -> AttachmentType
                    .serializable(holder -> new SuccessionChunkData(asChunk(holder)))
                    .build());

    private ModAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    private static ChunkAccess asChunk(IAttachmentHolder holder) {
        if (holder instanceof ChunkAccess chunk) {
            return chunk;
        }
        throw new IllegalStateException("Ecoflux chunk attachment was created for a non-chunk holder: " + holder.getClass().getName());
    }
}
