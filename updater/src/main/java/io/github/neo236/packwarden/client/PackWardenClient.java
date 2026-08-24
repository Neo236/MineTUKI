package io.github.neo236.packwarden.client;

import io.github.neo236.packwarden.PackWarden;
import io.github.neo236.packwarden.config.WardenConfig;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Entrada del lado cliente.
 *
 * <p>Separada del mod comun y marcada con {@code dist = Dist.CLIENT} para que nada
 * de interfaz grafica se cargue en un servidor dedicado.
 */
@Mod(value = PackWarden.MOD_ID, dist = Dist.CLIENT)
public final class PackWardenClient {

    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_GAP = 4;

    public PackWardenClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, WardenConfig.CLIENT_SPEC);

        // El registro del canal vive en codigo comun; el manejador se instala aca
        // para que ninguna clase de cliente se cargue en un servidor dedicado.
        io.github.neo236.packwarden.net.WardenNetwork.setClientHandler(ClientPackState::handle);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }

        // Posicion provisoria. La definitiva la resuelve el boton en su primer
        // render, cuando ya estan todos los widgets de todos los mods.
        int[] position = placeNextToMultiplayer(event, screen);
        event.addListener(new WardenButton(position[0], position[1], BUTTON_SIZE));

        if (WardenConfig.CLIENT.checkOnStartup.get()) {
            ClientUpdateState.checkInBackground(true);
        }
    }

    /**
     * Ubica el boton pegado al de multijugador.
     *
     * <p>Se busca el widget en vez de calcular coordenadas fijas porque otros mods
     * reordenan el menu principal. Si no aparece, se cae a la posicion vanilla.
     */
    private static int[] placeNextToMultiplayer(ScreenEvent.Init.Post event, TitleScreen screen) {
        for (var listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget) {
                String label = widget.getMessage().getString().toLowerCase();
                String key = widget.getMessage().getContents().toString().toLowerCase();
                if (label.contains("multijugador") || label.contains("multiplayer") || key.contains("menu.multiplayer")) {
                    return new int[] {widget.getX() - BUTTON_SIZE - BUTTON_GAP, widget.getY()};
                }
            }
        }
        return new int[] {
            screen.width / 2 - 100 - BUTTON_SIZE - BUTTON_GAP, screen.height / 4 + 48 + 24
        };
    }

}
