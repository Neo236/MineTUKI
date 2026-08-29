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
    private static final String ARG_INSTALL = "--install";
    private static final String ARG_NEOFORGE = "--neoforge-version";
    private static final String ARG_FOLDER_NAME = "--folder-name";
    private static final String ARG_PACK_NAME = "--pack-name";
    private static final String ARG_ALIAS = "--command-alias";
    private static final String ARG_FALLBACK_URL = "--fallback-url";
    private static final String ARG_LANGUAGE = "--language";

    /** Margen de seguridad: si el juego no cierra en este plazo, seguimos igual. */
    private static final Duration WAIT_LIMIT = Duration.ofMinutes(2);

    private CompanionMain() {}

    public static void main(String[] args) {
        Map<String, String> options = parse(args);
        Messages.setLanguage(options.getOrDefault(ARG_LANGUAGE, ""));
        String title = options.getOrDefault(ARG_TITLE, "PackWarden");

        // Antes de tocar nada: dejar constancia de con que Java se esta corriendo
        // y atrapar lo que se escape de cualquier hilo. Sin esto, un fallo antes
        // de que exista la ventana se perdia entero.
        Diagnostics.start(args);
        Thread.setDefaultUncaughtExceptionHandler(Diagnostics.handler(title));

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // El look and feel es cosmetico; si falla, se sigue igual.
        }

        // Un solo binario con dos modos: instalacion de primera vez, y actualizacion
        // con el juego ya cerrado. Comparten la deteccion de Java, las rutas y la
        // llamada a packwiz, asi que separarlos en dos programas seria duplicar todo.
        if (contains(args, ARG_INSTALL)) {
            String packName = options.getOrDefault(ARG_PACK_NAME, "Modpack");
            try {
                InstallerUi.launch(new InstallerUi.Config(
                        packName,
                        options.getOrDefault(ARG_TITLE, packName),
                        options.getOrDefault(ARG_ALIAS, ""),
                        options.getOrDefault(ARG_PACK_URL, ""),
                        options.getOrDefault(ARG_FALLBACK_URL, ""),
                        options.getOrDefault(ARG_NEOFORGE, ""),
                        options.getOrDefault(ARG_FOLDER_NAME, "modpack"),
                        Paths.get(options.getOrDefault(ARG_BOOTSTRAP, "packwiz-installer-bootstrap.jar"))));
            } catch (Throwable e) {
                // Salir con codigo distinto de cero es lo que hace que el .bat
                // muestre el registro en vez de cerrar la ventana.
                Diagnostics.crash(title, e);
                System.exit(1);
            }
            return;
        }

        try {
            waitForGameToExit(options.get(ARG_WAIT_PID));
            int exitCode = runInstaller(options);

            if (exitCode == 0) {
                info(title, Messages.get("update.done"));
            } else {
                error(title, Messages.get("update.failed", exitCode));
            }
            System.exit(exitCode);
        } catch (Throwable e) {
            // Este camino ya tiene su propio cartel, asi que solo se registra.
            Diagnostics.record(e);
            error(title, Messages.get("update.error", String.valueOf(e.getMessage())));
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

    private static boolean contains(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parseo de argumentos.
     *
     * <p>Una bandera sin valor, como {@code --install}, no puede tragarse el nombre
     * de la siguiente: se considera valor solo lo que no empieza con dos guiones.
     */
    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                continue;
            }
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                options.put(args[i], args[i + 1]);
                i++;
            } else {
                options.put(args[i], "");
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
