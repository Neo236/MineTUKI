package io.github.neo236.packwarden.client;

import io.github.neo236.packwarden.PackWarden;
import io.github.neo236.packwarden.core.LocalPack;
import io.github.neo236.packwarden.net.PackStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Respuesta del cliente al handshake de version.
 *
 * <p>Avisa, no expulsa. Que el pack no coincida puede ser algo tan menor como una
 * version distinta de un mod de cliente, y sacar a alguien del servidor por eso
 * seria peor que el problema.
 *
 * <p>Limitacion conocida: NeoForge compara las listas de mods antes que esto, asi
 * que al que le falte un mod entero lo rechaza primero, con su propio mensaje.
 * Este aviso cubre el caso en que las listas son compatibles pero el pack difiere.
 */
public final class ClientPackState {

    private ClientPackState() {}

    public static void handle(PackStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            String installed = LocalPack.installedIndexHash();
            if (installed == null) {
                // Sin manifiesto no hay comparacion posible. Avisar igual seria
                // marcar como desactualizado a todo el que instalo a mano.
                return;
            }
            if (installed.equalsIgnoreCase(payload.indexHash())) {
                return;
            }

            PackWarden.LOG.info(
                    "El pack del servidor no coincide con el instalado ({} vs {})",
                    shorten(payload.indexHash()),
                    shorten(installed));

            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.translatable("packwarden.client.outdated").withStyle(ChatFormatting.YELLOW),
                        false);
            }
        });
    }

    private static String shorten(String hash) {
        if (hash == null) {
            return "?";
        }
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }
}
