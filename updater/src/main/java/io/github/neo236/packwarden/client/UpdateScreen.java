package io.github.neo236.packwarden.client;

import io.github.neo236.packwarden.PackWarden;
import io.github.neo236.packwarden.config.WardenConfig;
import io.github.neo236.packwarden.core.PackChangelog;
import io.github.neo236.packwarden.core.UpdateChecker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * La pantalla que ve el jugador cuando hay una actualizacion.
 *
 * <p>Ofrece tres salidas y no dos. La version anterior preguntaba si o no, y el
 * "no" no significaba nada: volvia a aparecer igual, y no habia forma de decir
 * "si, pero cuando termine de jugar". Las tres opciones son intencionales.
 */
public final class UpdateScreen extends Screen {

    private static final int MAX_LISTED = 6;

    private final Screen parent;
    private final UpdateChecker.Result result;

    private Checkbox dontAsk;

    public UpdateScreen(Screen parent, UpdateChecker.Result result) {
        super(Component.translatable("packwarden.screen.title", brand()));
        this.parent = parent;
        this.result = result;
    }

    private static String brand() {
        return WardenConfig.COMMON.brandName.get();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonsY = this.height - 60;

        this.dontAsk = Checkbox.builder(Component.translatable("packwarden.check.dont_ask"), this.font)
                .pos(centerX - 100, buttonsY - 28)
                .selected(false)
                .build();
        this.addRenderableWidget(this.dontAsk);

        this.addRenderableWidget(Button.builder(
                        Component.translatable("packwarden.button.now"), b -> updateNow())
                .bounds(centerX - 154, buttonsY, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("packwarden.button.on_exit"), b -> updateOnExit())
                .bounds(centerX - 50, buttonsY, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("packwarden.button.later"), b -> later())
                .bounds(centerX + 54, buttonsY, 100, 20)
                .build());
    }

    private void updateNow() {
        rememberDismissalChoice();
        try {
            CompanionLauncher.launch();
            Minecraft.getInstance().stop();
        } catch (Exception e) {
            PackWarden.LOG.error("No se pudo iniciar la actualizacion", e);
            // No cerrar el juego si el lanzamiento fallo: dejarlo abierto es la unica
            // forma de que el jugador se entere de que algo salio mal.
            this.minecraft.setScreen(new AlertScreen(
                    () -> this.minecraft.setScreen(parent),
                    Component.translatable("packwarden.error.title"),
                    Component.translatable("packwarden.error.launch", String.valueOf(e.getMessage()))));
        }
    }

    private void updateOnExit() {
        rememberDismissalChoice();
        CompanionLauncher.scheduleOnExit();
        this.minecraft.setScreen(parent);
    }

    private void later() {
        rememberDismissalChoice();
        this.minecraft.setScreen(parent);
    }

    private void rememberDismissalChoice() {
        if (this.dontAsk != null && this.dontAsk.selected()) {
            ClientPrefs.get().dismiss(result.publishedIndexHash());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, 30, 0xFFFFFF);

        int y = 55;
        for (Component line : summaryLines()) {
            graphics.drawCenteredString(this.font, line, centerX, y, 0xC0C0C0);
            y += 12;
        }
    }

    private List<Component> summaryLines() {
        List<Component> lines = new ArrayList<>();
        PackChangelog changelog = result.changelog();

        if (result.publishedVersion() != null && !result.publishedVersion().isBlank()) {
            lines.add(Component.translatable("packwarden.screen.version", result.publishedVersion()));
        }

        if (changelog.isEmpty()) {
            lines.add(Component.translatable("packwarden.screen.changes_unknown"));
            return lines;
        }

        lines.add(Component.translatable(
                "packwarden.screen.summary",
                changelog.added().size(),
                changelog.removed().size(),
                changelog.updated().size()));

        addNames(lines, "packwarden.screen.added", changelog.added(), ChatFormatting.GREEN);
        addNames(lines, "packwarden.screen.removed", changelog.removed(), ChatFormatting.RED);
        addNames(lines, "packwarden.screen.updated", changelog.updated(), ChatFormatting.AQUA);
        return lines;
    }

    private void addNames(List<Component> lines, String key, List<String> names, ChatFormatting color) {
        if (names.isEmpty()) {
            return;
        }
        String joined = String.join(", ", names.subList(0, Math.min(MAX_LISTED, names.size())));
        if (names.size() > MAX_LISTED) {
            joined = joined + " (+" + (names.size() - MAX_LISTED) + ")";
        }
        lines.add(Component.translatable(key, joined).withStyle(color));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
