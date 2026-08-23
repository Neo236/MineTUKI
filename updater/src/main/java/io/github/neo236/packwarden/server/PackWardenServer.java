package io.github.neo236.packwarden.server;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Entrada del lado servidor.
 *
 * <p>Los comandos se registran en los dos lados, porque en un mundo de un jugador
 * tambien hay servidor integrado; el ciclo de reinicio automatico, en cambio, solo
 * tiene sentido en un servidor dedicado.
 */
public class PackWardenServer {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        WardenCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (event.getServer().isDedicatedServer()) {
            ServerUpdateManager.start(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ServerUpdateManager.stop();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        ServerUpdateManager manager = ServerUpdateManager.get();
        if (manager != null) {
            manager.tick();
        }
    }
}
