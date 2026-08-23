package io.github.neo236.packwarden.client;

import io.github.neo236.packwarden.PackWarden;
import io.github.neo236.packwarden.config.WardenConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Lanza el proceso que actualiza el pack.
 *
 * <p>Tanto el companion como el bootstrap de packwiz viajan dentro del jar del
 * mod: no hay descargas en tiempo de uso, que era otro punto donde la
 * actualizacion podia fallar por red o por un redirect roto.
 */
public final class CompanionLauncher {

    private static final String WORK_DIR = "packwarden";
    private static final String COMPANION_JAR = "companion.jar";
    private static final String BOOTSTRAP_JAR = "packwiz-installer-bootstrap.jar";

    private static final AtomicBoolean scheduledOnExit = new AtomicBoolean(false);

    private CompanionLauncher() {}

    /** Si ya hay una actualizacion agendada para cuando se cierre el juego. */
    public static boolean isScheduledOnExit() {
        return scheduledOnExit.get();
    }

    /**
     * Agenda la actualizacion para cuando el jugador cierre el juego.
     *
     * <p>El proceso se lanza desde un shutdown hook y espera a que muera esta JVM
     * antes de tocar nada, asi que el jugador puede seguir jugando sin enterarse.
     */
    public static boolean scheduleOnExit() {
        if (!scheduledOnExit.compareAndSet(false, true)) {
            return true;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                launch();
            } catch (Exception e) {
                PackWarden.LOG.error("No se pudo lanzar la actualizacion al salir", e);
            }
        }, "packwarden-update-on-exit"));
        PackWarden.LOG.info("Actualizacion agendada para el cierre del juego.");
        return true;
    }

    /** Lanza el proceso ahora. El companion espera igual a que la JVM muera. */
    public static void launch() throws IOException {
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path workDir = gameDir.resolve(WORK_DIR);
        Files.createDirectories(workDir);

        Path companion = extract(COMPANION_JAR, workDir.resolve(COMPANION_JAR));
        Path bootstrap = extract(BOOTSTRAP_JAR, workDir.resolve(BOOTSTRAP_JAR));

        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-jar");
        command.add(companion.toAbsolutePath().toString());
        command.add("--wait-pid");
        command.add(String.valueOf(ProcessHandle.current().pid()));
        command.add("--pack-folder");
        command.add(gameDir.toAbsolutePath().toString());
        command.add("--pack-url");
        command.add(WardenConfig.COMMON.packUrl.get());
        command.add("--bootstrap");
        command.add(bootstrap.toAbsolutePath().toString());
        command.add("--side");
        command.add("client");
        command.add("--title");
        command.add(WardenConfig.COMMON.brandName.get());

        new ProcessBuilder(command)
                .directory(gameDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(
                        workDir.resolve("companion.log").toFile()))
                .start();

        PackWarden.LOG.info("Companion lanzado; esperando el cierre del juego.");
    }

    private static Path extract(String resourceName, Path target) throws IOException {
        try (InputStream in = CompanionLauncher.class.getResourceAsStream("/" + WORK_DIR + "/" + resourceName)) {
            if (in == null) {
                throw new IOException("El mod no incluye " + resourceName);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /** El mismo Java que corre el juego, para no depender de que haya uno instalado. */
    private static String javaExecutable() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path binary = javaHome.resolve("bin").resolve(windows ? "java.exe" : "java");
        return Files.isExecutable(binary) ? binary.toString() : "java";
    }
}
