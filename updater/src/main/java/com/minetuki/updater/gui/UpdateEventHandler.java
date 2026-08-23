package com.minetuki.updater.gui;

import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.components.AbstractWidget;

public class UpdateEventHandler {

    @SubscribeEvent
    public void onInitScreen(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen) {
            
            int btnWidth = 20;
            int btnHeight = 20;
            
            int targetY = event.getScreen().height / 4 + 48;
            int targetX = event.getScreen().width / 2 - 100 - btnWidth - 4;
            
            for (var listener : event.getListenersList()) {
                if (listener instanceof AbstractWidget) {
                    AbstractWidget widget = (AbstractWidget) listener;
                    String msg = widget.getMessage().getString().toLowerCase();
                    String translationKey = widget.getMessage().getContents().toString().toLowerCase();
                    if (msg.contains("multijugador") || msg.contains("multiplayer") || translationKey.contains("menu.multiplayer")) {
                        targetY = widget.getY();
                        targetX = widget.getX() - btnWidth - 4;
                        break;
                    }
                }
            }
            
            event.addListener(new FoxUpdaterButton(targetX, targetY, btnWidth, btnHeight));
        }
    }
}
