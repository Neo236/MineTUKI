package io.github.neo236.packwarden.client;

import io.github.neo236.packwarden.PackWarden;
import io.github.neo236.packwarden.config.WardenConfig;
import io.github.neo236.packwarden.core.UpdateChecker;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Lo que sabemos del estado del pack, del lado del cliente.
 *
 * <p>La consulta siempre corre fuera del hilo del juego. La version anterior la
 * hacia en el hilo de render al apretar el boton, con lo cual el juego se
 * congelaba hasta que respondiera la red o venciera el timeout.
 */
public final class ClientUpdateState {

    private static final AtomicReference<UpdateChecker.Result> LAST = new AtomicReference<>();
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static final AtomicBoolean PROMPTED = new AtomicBoolean(false);

    private ClientUpdateState() {}

    public static UpdateChecker.Result last() {
        return LAST.get();
    }

    public static boolean updateAvailable() {
        UpdateChecker.Result result = LAST.get();
        return result != null && result.updateAvailable();
    }

    /** Dispara una consulta si no hay ninguna en curso. */
    public static void checkInBackground(boolean promptWhenDone) {
        if (!CHECKING.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(
                () -> {
                    try {
                        UpdateChecker.Result result = UpdateChecker.check(FMLPaths.GAMEDIR.get());
                        LAST.set(result);
                        if (promptWhenDone) {
                            maybePrompt(result);
                        }
                    } catch (Exception e) {
                        PackWarden.LOG.error("Fallo el chequeo de actualizaciones", e);
                    } finally {
                        CHECKING.set(false);
                    }
                },
                "packwarden-check");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Muestra la pantalla, pero solo si corresponde: una vez por sesion, con el
     * jugador en el menu principal, y respetando que haya pedido no ser molestado
     * por esta version en particular.
     */
    private static void maybePrompt(UpdateChecker.Result result) {
        if (!result.updateAvailable()) {
            return;
        }
        if (!WardenConfig.CLIENT.promptOnStartup.get()) {
            return;
        }
        if (ClientPrefs.get().isDismissed(result.publishedIndexHash())) {
            PackWarden.LOG.info("Hay actualizacion, pero el jugador pidio no ser avisado por esta version.");
            return;
        }
        if (!PROMPTED.compareAndSet(false, true)) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.screen instanceof TitleScreen title) {
                client.setScreen(new UpdateScreen(title, result));
            }
        });
    }
}
