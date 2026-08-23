package io.github.neo236.packwarden.companion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    }

    private Installer() {}

    public static void run(Options options, Progress progress) throws Exception {
        Files.createDirectories(options.gameDirectory());

        if (options.destination() == Destination.DEDICATED_PROFILE) {
            ensureNeoForge(options, progress);
        }

        progress.step(Messages.get("step.downloading"));
        installPack(options);

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
        try (InputStream in = java.net.URI.create(url).toURL().openStream()) {
            Files.copy(in, installer, StandardCopyOption.REPLACE_EXISTING);
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-jar");
        command.add(installer.toAbsolutePath().toString());
        command.add("--install-client");
        command.add(options.minecraftFolder().toAbsolutePath().toString());

        int exit = new ProcessBuilder(command).redirectErrorStream(true).start().waitFor();
        Files.deleteIfExists(installer);

        if (exit != 0) {
            throw new IOException(Messages.get("error.neoforge", exit));
        }
    }

    private static void installPack(Options options) throws Exception {
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
                .redirectOutput(ProcessBuilder.Redirect.appendTo(
                        options.gameDirectory().resolve("packwarden-install.log").toFile()))
                .start();

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
