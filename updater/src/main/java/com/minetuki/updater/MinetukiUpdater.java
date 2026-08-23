package com.minetuki.updater;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("minetuki_updater")
public class MinetukiUpdater {
    public static final Logger LOGGER = LoggerFactory.getLogger(MinetukiUpdater.class);
    public static boolean updateRequired = false;

    public MinetukiUpdater(IEventBus modEventBus) {
        LOGGER.info("MineTUKI Updater inicializando...");
        
        Thread checkThread = new Thread(() -> {
            updateRequired = com.minetuki.updater.network.UpdateChecker.checkForUpdates();
        });
        checkThread.setDaemon(true);
        checkThread.start();
        
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new com.minetuki.updater.gui.UpdateEventHandler());
    }
}
