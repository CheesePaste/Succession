package com.s.ecoflux.init;

import com.s.ecoflux.config.SuccessionConfigLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class ModReloadListeners {
    private ModReloadListeners() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModReloadListeners::onAddReloadListener);
    }

    private static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(SuccessionConfigLoader.INSTANCE);
    }
}
