package com.s.ecoflux;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EcofluxConstants {
    public static final String MOD_ID = "ecoflux";
    public static final String MOD_NAME = "Ecoflux";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private EcofluxConstants() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
