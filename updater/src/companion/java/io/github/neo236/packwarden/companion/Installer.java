package io.github.neo236.packwarden.companion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Instalacion de primera vez: deja el juego listo para jugar, no listo para copiar archivos. */
public final class Installer {

    /** Donde van a parar los mods. */
    public enum Destination {
        /** Carpeta propia dentro de .minecraft, mas un perfil en el launcher. */
        DEDICATED_PROFILE,
        /** Una carpeta que elige el jugador. Cubre Prism, MultiMC y demas. */
        CUSTOM_FOLDER
    }

    public record Options(
            Destination destination,
            Path minecraftFolder,
            Path gameDirectory,
            String packUrl,
            String fallbackPackUrl,
            String neoForgeVersion,
            String profileName,
            String profileKey,
            String brandName,
            String commandAlias,
            /** Codigo de idioma de Minecraft para la primera vez, por ejemplo "es_es". */
            String gameLanguage,
            Path bootstrapJar) {}

    public interface Progress {
        void step(String message);

        /**
         * Avance de una tarea larga: el texto para la etiqueta y la fraccion para
         * la barra.
         *
         * <p>Sin esto la barra queda indeterminada durante varios minutos, que es
         * indistinguible de un programa colgado. El texto viaja junto con los
         * numeros porque no todas las tareas largas son la descarga de mods: la de
         * NeoForge tiene que poder decir lo suyo.
         */
        void progress(String message, int hechos, int total);
    }

    /** Plazo de espera para hablar con maven.neoforged.net, en milisegundos. */
    private static final int TIMEOUT_MS = 30_000;

    /** Lineas de packwiz del estilo "(12/153) Downloaded Create". */
    private static final java.util.regex.Pattern AVANCE =
            java.util.regex.Pattern.compile("\\((\\d+)/(\\d+)\\)");

    private Installer() {}

    public static void run(Options options, Progress progress) throws Exception {
        Files.createDirectories(options.gameDirectory());

        // El registro arranca limpio en cada corrida y despues todos los pasos le
        // van agregando: NeoForge primero y packwiz despues.
        Files.writeString(installLog(options), "");

        if (options.destination() == Destination.DEDICATED_PROFILE) {
            ensureNeoForge(options, progress);
        }

        progress.step(Messages.get("step.downloading"));
        installPack(options, progress);

        progress.step(Messages.get("step.configuring"));
        seedModConfig(options);

        progress.step(Messages.get("step.resourcepacks"));
        seedOptions(options.gameDirectory(), options.gameLanguage());

        if (options.destination() == Destination.DEDICATED_PROFILE) {
            progress.step(Messages.get("step.profile"));
            int heapGb = Platform.recommendedHeapGb();
            LauncherProfiles.install(
                    options.minecraftFolder(),
                    options.profileKey(),
                    options.profileName(),
                    options.gameDirectory(),
                    options.neoForgeVersion(),
                    javaArgs(heapGb));
            progress.step(Messages.get("step.profileDone", heapGb));
        }
    }

    /**
     * Argumentos de JVM del perfil.
     *
     * <p>Sin esto el launcher usa su valor por defecto, que no alcanza para un pack
     * de este tamaño. G1 con pausas acotadas es lo que mejor se porta en Minecraft
     * modeado.
     */
    private static String javaArgs(int heapGb) {
        return "-Xmx" + heapGb + "G -Xms" + Math.max(2, heapGb / 2) + "G"
                + " -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+AlwaysPreTouch";
    }

    private static void ensureNeoForge(Options options, Progress progress) throws Exception {
        if (LauncherProfiles.findInstalledVersion(options.minecraftFolder(), options.neoForgeVersion())
                .isPresent()) {
            progress.step(Messages.get("step.neoforgePresent", options.neoForgeVersion()));
            return;
        }

        progress.step(Messages.get("step.neoforgeInstalling", options.neoForgeVersion()));
        String version = options.neoForgeVersion().replace("neoforge-", "");
        String url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                + version + "/neoforge-" + version + "-installer.jar";

        Path installer = Files.createTempFile("neoforge-installer", ".jar");
        try {
            download(url, installer, progress);
            runNeoForgeInstaller(options, installer, progress);
        } finally {
            Files.deleteIfExists(installer);
        }
    }

    /**
     * Baja un archivo mostrando cuanto va.
     *
     * <p>Se abre la conexion a mano en vez de usar openStream() por los plazos de
     * espera: openStream() no trae ninguno, asi que una conexion que se corta a
     * mitad de la descarga deja el instalador esperando para siempre, con la
     * ventana quieta en "Instalando NeoForge" y sin forma de saber que paso.
     */
    private static void download(String url, Path target, Progress progress) throws IOException {
        URLConnection connection = java.net.URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);

        long total = connection.getContentLengthLong();
        long done = 0;
        byte[] buffer = new byte[64 * 1024];

        try (InputStream in = connection.getInputStream();
                OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                done += read;
                if (total > 0) {
                    progress.progress(
                            Messages.get("step.neoforgeDownloading", megabytes(done), megabytes(total)),
                            (int) (done / 1024),
                            (int) (total / 1024));
                }
            }
        } catch (IOException e) {
            throw new IOException(Messages.get("error.neoforgeDownload", String.valueOf(e.getMessage())), e);
        }
    }

    private static String megabytes(long bytes) {
        return String.valueOf(Math.round(bytes / 1048576.0));
    }

    /** El unico registro de la instalacion, al que escriben todos los pasos. */
    private static Path installLog(Options options) {
        return options.gameDirectory().resolve("packwarden-install.log");
    }

    /**
     * Corre el instalador oficial de NeoForge.
     *
     * <p>Hay que vaciar su salida si o si. El instalador escribe una linea por
     * cada libreria que baja, y si nadie lee esa tuberia el sistema operativo la
     * llena y el proceso queda bloqueado escribiendo, sin terminar nunca. Antes
     * se llamaba a waitFor() sin leer nada, y ese era justamente el cuelgue.
     */
    private static void runNeoForgeInstaller(Options options, Path installer, Progress progress)
            throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-jar");
        command.add(installer.toAbsolutePath().toString());
        command.add("--install-client");
        command.add(options.minecraftFolder().toAbsolutePath().toString());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        try (var salida = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                var archivo = Files.newBufferedWriter(installLog(options), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String linea;
            while ((linea = salida.readLine()) != null) {
                archivo.write(linea);
                archivo.newLine();

                // La ultima linea del instalador va a la etiqueta: el paso dura
                // minutos y sin esto no se distingue de un cuelgue.
                String corta = linea.trim();
                if (!corta.isEmpty()) {
                    progress.step(Messages.get("step.neoforgeWorking", resumir(corta)));
                }
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException(Messages.get("error.neoforge", exit));
        }
    }

    /** Recorta una linea del instalador para que entre en la etiqueta. */
    private static String resumir(String linea) {
        return linea.length() <= 58 ? linea : linea.substring(0, 55) + "...";
    }

    private static void installPack(Options options, Progress progress) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-jar");
        command.add(options.bootstrapJar().toAbsolutePath().toString());
        command.add("--pack-folder");
        command.add(options.gameDirectory().toAbsolutePath().toString());
        command.add("--side");
        command.add("client");
        // Sin ventana propia de packwiz: el instalador ya muestra su progreso, y
        // la de packwiz ademas pregunta por "mods opcionales", algo que este pack
        // no usa y que solo confunde.
        command.add("--no-gui");
        command.add(options.packUrl());

        Process process = new ProcessBuilder(command)
                .directory(options.gameDirectory().toFile())
                .redirectErrorStream(true)
                .start();

        // Se lee la salida en vivo para saber por donde va, y se guarda igual en el
        // log por si despues hay que revisar que fallo.
        try (var salida = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                var archivo = Files.newBufferedWriter(installLog(options), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String linea;
            while ((linea = salida.readLine()) != null) {
                archivo.write(linea);
                archivo.newLine();

                var encontrado = AVANCE.matcher(linea);
                if (encontrado.find()) {
                    int hechos = Integer.parseInt(encontrado.group(1));
                    int total = Integer.parseInt(encontrado.group(2));
                    progress.progress(Messages.get("step.downloadingCount", hechos, total), hechos, total);
                }
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException(
                    "La descarga de mods fallo (codigo " + exit + ").\n"
                            + "Revisa packwarden-install.log dentro de la carpeta del juego.");
        }
    }

    /**
     * Le deja al mod su configuracion, con la URL del pack ya puesta.
     *
     * <p>Sin esto el mod arranca con la configuracion por defecto, que no tiene
     * ninguna URL, y lo unico que sabe decir es "no hay ningun modpack
     * configurado": queda instalado pero inutil, y el jugador no tiene forma de
     * adivinar que le falta.
     *
     * <p>Se escriben todas las claves. Si falta alguna, NeoForge reescribe el
     * archivo para completarlo y avisa que estaba "incorrecto".
     */
    static void seedModConfig(Options options) throws IOException {
        Path configFolder = options.gameDirectory().resolve("config");
        Files.createDirectories(configFolder);

        Path common = configFolder.resolve("packwarden-common.toml");
        if (!Files.exists(common)) {
            Files.writeString(
                    common,
                    "[general]\n"
                            + "\tpack_url = " + tomlString(options.packUrl()) + "\n"
                            + "\tfallback_pack_url = " + tomlString(options.fallbackPackUrl()) + "\n"
                            + "\tbrand_name = " + tomlString(options.brandName()) + "\n"
                            + "\tcommand_alias = " + tomlString(options.commandAlias()) + "\n"
                            + "\thttp_timeout_seconds = 10\n",
                    StandardCharsets.UTF_8);
        }

        Path client = configFolder.resolve("packwarden-client.toml");
        if (!Files.exists(client)) {
            Files.writeString(
                    client,
                    "[client]\n\tcheck_on_startup = true\n\tprompt_on_startup = true\n",
                    StandardCharsets.UTF_8);
        }
    }

    private static String tomlString(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Deja activados los paquetes de texturas que trae el pack.
     *
     * <p>Descargarlos no los activa: eso es cosa de options.txt. Solo se escribe si
     * el archivo no existe, porque en una carpeta ya usada seria pisarle la
     * configuracion al jugador.
     */
    static void seedOptions(Path gameDirectory, String gameLanguage) throws IOException {
        Path options = gameDirectory.resolve("options.txt");
        if (Files.exists(options)) {
            return;
        }

        Path packsFolder = gameDirectory.resolve("resourcepacks");
        List<String> packs = new ArrayList<>();
        if (Files.isDirectory(packsFolder)) {
            try (Stream<Path> files = Files.list(packsFolder)) {
                files.map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".zip"))
                        .sorted(Comparator.naturalOrder())
                        .forEach(packs::add);
            }
        }

        StringBuilder list = new StringBuilder("[\"vanilla\"");
        for (String pack : packs) {
            // Los nombres traen espacios, parentesis y hasta guiones iniciales, asi
            // que hay que escaparlos y no concatenarlos a mano.
            list.append(',').append(quote("file/" + pack));
        }
        list.append(']');

        // El idioma tambien se siembra: es la primera vez que se abre esta carpeta,
        // asi que el juego arranca directamente en el idioma que eligio el jugador
        // en vez de en ingles.
        String language = gameLanguage == null || gameLanguage.isBlank() ? "en_us" : gameLanguage;

        String content = "version:3955\n"
                + "lang:" + language + "\n"
                + "fullscreen:false\n"
                + "resourcePacks:" + list + "\n"
                + "incompatibleResourcePacks:[]\n";

        Files.writeString(options, content, StandardCharsets.UTF_8);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.append('"').toString();
    }

    private static String javaExecutable() {
        return Platform.findJava().map(Path::toString).orElse("java");
    }
}
