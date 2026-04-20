package com.s.ecoflux;

import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.init.ModChunkEvents;
import com.s.ecoflux.init.ModReloadListeners;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(EcofluxConstants.MOD_ID)
public final class EcofluxMod {
    public EcofluxMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.register(modEventBus);
        ModChunkEvents.register();
        ModReloadListeners.register();
        EcofluxConstants.LOGGER.info("{} is initializing", modContainer.getModInfo().getDisplayName());
    }
}
