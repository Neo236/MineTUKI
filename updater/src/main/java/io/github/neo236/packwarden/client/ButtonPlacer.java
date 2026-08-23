package io.github.neo236.packwarden.client;

import io.github.neo236.packwarden.config.WardenConfig;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Decide donde va el boton del menu principal.
 *
 * <p>Esto no se puede resolver al construir la pantalla. Todos los mods agregan
 * sus botones durante el mismo evento, y el orden depende de en que orden se
 * registraron: cuando nos toca mirar, el lugar puede figurar libre y terminar
 * ocupado un instante despues. El resultado era un boton escondido debajo de
 * otro, que ademas le robaba los clics.
 *
 * <p>Por eso la ubicacion se resuelve en el primer render, cuando ya estan todos.
 */
final class ButtonPlacer {

    private static final int GAP = 4;

    private ButtonPlacer() {}

    /**
     * Busca un lugar libre para un boton de {@code size} pixeles.
     *
     * @return las coordenadas elegidas, o {@code null} si no hizo falta moverse
     */
    static int[] findFreeSpot(Screen screen, AbstractWidget self, int size) {
        List<? extends GuiEventListener> widgets = screen.children();

        // Primero el anclaje pedido, despues el resto, y por ultimo la esquina.
        WardenConfig.ButtonAnchor preferred = WardenConfig.CLIENT.buttonAnchor.get();
        for (WardenConfig.ButtonAnchor anchor : ordered(preferred)) {
            int[] spot = trySlide(screen, self, size, anchor);
            if (spot != null) {
                return spot;
            }
        }
        return bottomLeft(screen, size);
    }

    private static WardenConfig.ButtonAnchor[] ordered(WardenConfig.ButtonAnchor preferred) {
        return switch (preferred) {
            case REALMS -> new WardenConfig.ButtonAnchor[] {
                WardenConfig.ButtonAnchor.REALMS, WardenConfig.ButtonAnchor.MULTIPLAYER
            };
            case MULTIPLAYER -> new WardenConfig.ButtonAnchor[] {
                WardenConfig.ButtonAnchor.MULTIPLAYER, WardenConfig.ButtonAnchor.REALMS
            };
            case BOTTOM_LEFT -> new WardenConfig.ButtonAnchor[0];
        };
    }

    /** Se corre hacia la izquierda desde el boton de referencia hasta hallar hueco. */
    private static int[] trySlide(
            Screen screen, AbstractWidget self, int size, WardenConfig.ButtonAnchor anchor) {

        AbstractWidget reference = findMenuButton(screen, anchor);
        if (reference == null) {
            return null;
        }

        int y = reference.getY() + (reference.getHeight() - size) / 2;
        int x = reference.getX() - size - GAP;

        for (int attempt = 0; attempt < 6 && x >= 0; attempt++) {
            if (isFree(screen, self, x, y, size)) {
                return new int[] {x, y};
            }
            x -= size + GAP;
        }
        return null;
    }

    private static AbstractWidget findMenuButton(Screen screen, WardenConfig.ButtonAnchor anchor) {
        String[] needles = switch (anchor) {
            case REALMS -> new String[] {"menu.online", "realms"};
            case MULTIPLAYER -> new String[] {"menu.multiplayer", "multijugador", "multiplayer"};
            case BOTTOM_LEFT -> new String[0];
        };

        for (GuiEventListener listener : screen.children()) {
            if (!(listener instanceof AbstractWidget widget) || widget.getWidth() < 100) {
                continue;
            }
            String label = widget.getMessage().getString().toLowerCase();
            String key = widget.getMessage().getContents().toString().toLowerCase();
            for (String needle : needles) {
                if (label.contains(needle) || key.contains(needle)) {
                    return widget;
                }
            }
        }
        return null;
    }

    /**
     * Esquina inferior izquierda, por encima del texto de version.
     *
     * <p>Es el ultimo recurso, pero tambien el unico lugar que ningun mod suele
     * disputar.
     */
    private static int[] bottomLeft(Screen screen, int size) {
        return new int[] {GAP, screen.height - size - 22};
    }

    private static boolean isFree(Screen screen, AbstractWidget self, int x, int y, int size) {
        for (GuiEventListener listener : screen.children()) {
            if (listener == self || !(listener instanceof AbstractWidget widget) || !widget.visible) {
                continue;
            }
            boolean overlaps = x < widget.getX() + widget.getWidth()
                    && x + size > widget.getX()
                    && y < widget.getY() + widget.getHeight()
                    && y + size > widget.getY();
            if (overlaps) {
                return false;
            }
        }
        return true;
    }
}
