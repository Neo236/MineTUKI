package io.github.neo236.packwarden.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * El proceso que actualiza el pack con Minecraft ya cerrado.
 *
 * <p>Existe porque en Windows los jar de mods quedan bloqueados mientras la JVM
 * del juego vive: reemplazarlos desde adentro no funciona. La version anterior
 * resolvia esto generando un .bat o .sh en tiempo de ejecucion, que ademas de
 * fragil dependia de encontrar un emulador de terminal en Linux.
 *
 * <p>No tiene dependencias a proposito: se compila aparte del mod y tiene que
 * poder ejecutarse solo, sin Minecraft en el classpath.
 */
public final class CompanionMain {

    private static final String ARG_WAIT_PID = "--wait-pid";
    private static final String ARG_PACK_FOLDER = "--pack-folder";
    private static final String ARG_PACK_URL = "--pack-url";
    private static final String ARG_BOOTSTRAP = "--bootstrap";
    private static final String ARG_SIDE = "--side";
    private static final String ARG_TITLE = "--title";

    /** Margen de seguridad: si el juego no cierra en este plazo, seguimos igual. */
    private static final Duration WAIT_LIMIT = Duration.ofMinutes(2);

    private CompanionMain() {}

    public static void main(String[] args) {
        Map<String, String> options = parse(args);
        String title = options.getOrDefault(ARG_TITLE, "PackWarden");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // El look and feel es cosmetico; si falla, se sigue igual.
        }

        try {
            waitForGameToExit(options.get(ARG_WAIT_PID));
            int exitCode = runInstaller(options);

            if (exitCode == 0) {
                info(title, "Listo. Ya podes abrir el juego.");
            } else {
                error(title, "La actualizacion no se completo (codigo " + exitCode + ").\n"
                        + "El modpack quedo como estaba.");
            }
            System.exit(exitCode);
        } catch (Exception e) {
            error(title, "No se pudo actualizar:\n" + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Espera a que muera el proceso del juego.
     *
     * <p>Esperar por PID y no dormir una cantidad fija de segundos: con muchos mods
     * el cierre puede tardar bastante mas de lo que dure cualquier espera a ciegas,
     * y si el jar sigue bloqueado la actualizacion falla.
     */
    private static void waitForGameToExit(String pidArgument) throws Exception {
        if (pidArgument == null || pidArgument.isBlank()) {
            return;
        }

        long pid;
        try {
            pid = Long.parseLong(pidArgument.trim());
        } catch (NumberFormatException e) {
            return;
        }

        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty() || !handle.get().isAlive()) {
            return;
        }

        try {
            handle.get().onExit().get(WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            // Si se agota la espera igual se intenta: en el peor caso el instalador
            // falla por archivos bloqueados y se reporta como error normal.
        }

        // El sistema operativo puede tardar un instante mas en soltar los archivos.
        Thread.sleep(1000);
    }

    private static int runInstaller(Map<String, String> options) throws IOException, InterruptedException {
        String packUrl = require(options, ARG_PACK_URL);
        Path packFolder = Paths.get(require(options, ARG_PACK_FOLDER));
        Path bootstrap = Paths.get(require(options, ARG_BOOTSTRAP));

        if (!Files.isRegularFile(bootstrap)) {
            throw new IOException("No se encontro el instalador de packwiz en " + bootstrap);
        }
        Files.createDirectories(packFolder);

        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-jar");
        command.add(bootstrap.toAbsolutePath().toString());
        command.add("--pack-folder");
        command.add(packFolder.toAbsolutePath().toString());
        command.add("--side");
        command.add(options.getOrDefault(ARG_SIDE, "client"));
        command.add(packUrl);

        // Sin --no-gui: el instalador de packwiz trae su propia ventana de progreso,
        // que es exactamente lo que queremos mostrar y funciona igual en los tres
        // sistemas operativos.
        Process process = new ProcessBuilder(command)
                .directory(packFolder.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(
                        packFolder.resolve("packwarden-update.log").toFile()))
                .start();

        return process.waitFor();
    }

    /** El mismo Java con el que corre este proceso, que es el que trajo el juego. */
    private static String javaExecutable() {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path binary = javaHome.resolve("bin").resolve(windows ? "java.exe" : "java");
        return Files.isExecutable(binary) ? binary.toString() : "java";
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                options.put(args[i], args[i + 1]);
                i++;
            }
        }
        return options;
    }

    private static String require(Map<String, String> options, String key) throws IOException {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Falta el argumento " + key);
        }
        return value;
    }

    private static void info(String title, String message) {
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private static void error(String title, String message) {
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
