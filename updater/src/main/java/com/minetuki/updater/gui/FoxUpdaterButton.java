package com.minetuki.updater.gui;

import com.minetuki.updater.MinetukiUpdater;
import com.minetuki.updater.util.ScriptGenerator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class FoxUpdaterButton extends Button {

    private static final ResourceLocation FOX_TEXTURE = ResourceLocation.fromNamespaceAndPath("minetuki_updater", "textures/gui/fox.png");

    public FoxUpdaterButton(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(), FoxUpdaterButton::onPress, Button.DEFAULT_NARRATION);
    }

    private static void onPress(Button button) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen previousScreen = mc.screen;
        
        if (!MinetukiUpdater.updateRequired) {
            // Re-check
            boolean updateFound = com.minetuki.updater.network.UpdateChecker.checkForUpdates();
            if (updateFound) {
                MinetukiUpdater.updateRequired = true;
            } else {
                mc.setScreen(new ConfirmScreen(
                    (confirm) -> mc.setScreen(previousScreen),
                    Component.literal("Estás al día"),
                    Component.literal("Tu modpack ya se encuentra en la versión más reciente. ¡A jugar!"),
                    Component.literal("Aceptar"),
                    Component.literal("Volver")
                ));
                return;
            }
        }
        
        if (MinetukiUpdater.updateRequired) {
            mc.setScreen(new ConfirmScreen(
                (confirm) -> {
                    if (confirm) {
                        ScriptGenerator.executeUpdateAndShutdown(previousScreen);
                    } else {
                        mc.setScreen(previousScreen);
                    }
                },
                Component.literal("Actualización Disponible"),
                Component.literal("¿Deseas actualizar el modpack ahora? El juego se cerrará de inmediato.")
            ));
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw normal button background
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        
        // Draw Fox Texture
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(FOX_TEXTURE, this.getX() + 2, this.getY() + 2, 0, 0, 16, 16, 16, 16);
        
        // Draw Exclamation Mark at Top-Right
        if (MinetukiUpdater.updateRequired) {
            graphics.drawString(Minecraft.getInstance().font, "!", this.getX() + this.width - 5, this.getY() + 2, 0xFF0000, true);
        }
    }
}
