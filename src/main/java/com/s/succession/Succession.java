package com.s.succession;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Succession.MODID)
public class Succession {
    public static final String MODID = "succession";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Succession(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(PrototypeSuccessionSystem::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(PrototypeSuccessionSystem::onServerTick);

//        LOGGER.info("Succession loaded with prototype plains -> forest path enabled={}", Config.ENABLE_PROTOTYPE_PATH.get());
    }
}
