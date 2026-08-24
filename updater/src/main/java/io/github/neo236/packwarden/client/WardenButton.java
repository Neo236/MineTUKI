package io.github.neo236.packwarden.client;

import io.github.neo236.packwarden.PackWarden;
import io.github.neo236.packwarden.config.WardenConfig;
import io.github.neo236.packwarden.core.UpdateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** El boton del menu principal: estado de un vistazo, y consulta manual al hacer clic. */
public final class WardenButton extends Button {

    /** Lado de la textura del icono, en pixeles. Ver assets/packwarden/textures/gui. */
    private static final int ICON_SIZE = 64;

    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(PackWarden.MOD_ID, "textures/gui/fox.png");

    public WardenButton(int x, int y, int size) {
        super(x, y, size, size, Component.empty(), b -> handleClick(), Button.DEFAULT_NARRATION);

        // Un boton de 20x20 con un dibujo y sin texto no le dice nada a nadie: ni a
        // quien lo ve por primera vez, ni a un lector de pantalla. El tooltip y la
        // narracion le ponen nombre.
        Component label = Component.literal(WardenConfig.COMMON.brandName.get());
        this.setMessage(label);
        this.setTooltip(Tooltip.create(label));
    }

    @Override
    public void renderString(GuiGraphics graphics, net.minecraft.client.gui.Font font, int color) {
        // El nombre existe para el tooltip y la narracion, no para dibujarse encima
        // del icono.
    }

    private static void handleClick() {
        Minecraft client = Minecraft.getInstance();
        Screen previous = client.screen;

        UpdateChecker.Result known = ClientUpdateState.last();
        if (known != null && known.updateAvailable()) {
            client.setScreen(new UpdateScreen(previous, known));
            return;
        }

        // Al pedirlo a mano, se vuelve a consultar: puede haber salido algo nuevo
        // desde que arranco el juego. La consulta corre en otro hilo.
        ClientUpdateState.checkInBackground(false);
        client.setScreen(new AlertScreen(
                () -> client.setScreen(previous),
                Component.translatable("packwarden.checking.title", WardenConfig.COMMON.brandName.get()),
                describe(known)));
    }

    private static Component describe(UpdateChecker.Result result) {
        if (result == null) {
            return Component.translatable("packwarden.checking.message");
        }
        return switch (result.state()) {
            case UP_TO_DATE -> Component.translatable("packwarden.status.up_to_date");
            case OFFLINE -> Component.translatable("packwarden.status.offline");
            case DISABLED -> Component.translatable("packwarden.status.disabled");
            case NOT_MANAGED -> Component.translatable("packwarden.status.not_managed");
            case UPDATE_AVAILABLE -> Component.translatable("packwarden.status.available");
        };
    }

    /** La ubicacion se resuelve una vez, en el primer render. Ver {@link ButtonPlacer}. */
    private boolean placed;

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!placed) {
            placed = true;
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) {
                int[] spot = ButtonPlacer.findFreeSpot(screen, this, this.width);
                if (spot != null) {
                    this.setX(spot[0]);
                    this.setY(spot[1]);
                }
            }
        }

        super.renderWidget(graphics, mouseX, mouseY, partialTick);

        // La textura es de 64x64 y se dibuja escalada a 16x16. Guardarla mas grande
        // que su tamaño en pantalla la mantiene nitida en monitores con escalado,
        // donde una de 16x16 se ve pastosa.
        int drawn = 16;
        int inset = Math.max(1, (this.width - drawn) / 2);
        graphics.blit(
                ICON, this.getX() + inset, this.getY() + inset, drawn, drawn, 0, 0, ICON_SIZE, ICON_SIZE,
                ICON_SIZE, ICON_SIZE);

        if (ClientUpdateState.updateAvailable()) {
            graphics.drawString(
                    Minecraft.getInstance().font, "!", this.getX() + this.width - 5, this.getY() + 2, 0xFFFF5555, true);
        } else if (CompanionLauncher.isScheduledOnExit()) {
            // Actualizacion agendada: el jugador ya decidio, y conviene que lo vea.
            graphics.drawString(
                    Minecraft.getInstance().font, "✓", this.getX() + this.width - 6, this.getY() + 2, 0xFF55FF55, true);
        }
    }
}
