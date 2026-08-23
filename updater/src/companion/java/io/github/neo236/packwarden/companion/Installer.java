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
            String neoForgeVersion,
            String profileName,
            String profileKey,
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

        progress.step("Descargando los mods...");
        installPack(options);

        progress.step("Dejando los paquetes de texturas activados...");
        seedOptions(options.gameDirectory());

        if (options.destination() == Destination.DEDICATED_PROFILE) {
            progress.step("Registrando el perfil en el launcher...");
            int heapGb = Platform.recommendedHeapGb();
            LauncherProfiles.install(
                    options.minecraftFolder(),
                    options.profileKey(),
                    options.profileName(),
                    options.gameDirectory(),
                    options.neoForgeVersion(),
                    javaArgs(heapGb));
            progress.step("Perfil listo, con " + heapGb + " GB de memoria asignados.");
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
            progress.step("NeoForge " + options.neoForgeVersion() + " ya estaba instalado.");
            return;
        }

        progress.step("Instalando NeoForge " + options.neoForgeVersion() + "...");
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
            throw new IOException("El instalador de NeoForge fallo (codigo " + exit + ").");
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
                            + "Mira packwarden-install.log dentro de la carpeta del juego.");
        }
    }

    /**
     * Deja activados los paquetes de texturas que trae el pack.
     *
     * <p>Descargarlos no los activa: eso es cosa de options.txt. Solo se escribe si
     * el archivo no existe, porque en una carpeta ya usada seria pisarle la
     * configuracion al jugador.
     */
    static void seedOptions(Path gameDirectory) throws IOException {
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

        String content = "version:3955\n"
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
