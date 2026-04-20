package com.s.ecoflux;

import com.s.ecoflux.config.VisualLifecycleClientConfig;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.init.ModChunkEvents;
import com.s.ecoflux.init.ModCommands;
import com.s.ecoflux.init.ModReloadListeners;
import com.s.ecoflux.network.ModNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

@Mod(EcofluxConstants.MOD_ID)
public final class EcofluxMod {
    public EcofluxMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.register(modEventBus);
        ModNetworking.register(modEventBus);
        ModChunkEvents.register();
        ModCommands.register();
        ModReloadListeners.register();
        modContainer.registerConfig(ModConfig.Type.CLIENT, VisualLifecycleClientConfig.SPEC, "ecoflux-client.toml");
        EcofluxConstants.LOGGER.info("{} is initializing", modContainer.getModInfo().getDisplayName());
    }
}
