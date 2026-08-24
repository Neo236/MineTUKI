package io.github.neo236.packwarden.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;

/**
 * Registro del perfil en el launcher oficial.
 *
 * <p>Este archivo es de los pocos que puede arruinarle el dia a alguien: si se
 * escribe mal, el jugador pierde todos sus perfiles. Por eso se parsea con una
 * libreria de verdad —nunca con reemplazos de texto—, se conserva cualquier campo
 * desconocido, y se deja una copia de respaldo antes de tocar nada.
 */
public final class LauncherProfiles {

    private static final String FILE_NAME = "launcher_profiles.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LauncherProfiles() {}

    public static Path file(Path minecraftFolder) {
        return minecraftFolder.resolve(FILE_NAME);
    }

    public static boolean exists(Path minecraftFolder) {
        return Files.isRegularFile(file(minecraftFolder));
    }

    /**
     * Intenta detectar si el launcher esta abierto.
     *
     * <p>Importa porque el launcher reescribe este archivo al cerrarse, y pisaria
     * el perfil que acabamos de agregar. Es una heuristica: ante la duda conviene
     * avisar igual, no bloquear.
     */
    public static boolean looksLikeLauncherRunning() {
        try {
            return ProcessHandle.allProcesses()
                    .map(handle -> handle.info().command().orElse(""))
                    .anyMatch(command -> {
                        String lower = command.toLowerCase();
                        return lower.endsWith("minecraft.exe")
                                || lower.endsWith("minecraftlauncher.exe")
                                || lower.contains("minecraft launcher");
                    });
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Agrega o actualiza el perfil, conservando todo lo demas.
     *
     * @return la ruta del respaldo que se dejo
     */
    public static Path install(
            Path minecraftFolder,
            String profileKey,
            String displayName,
            Path gameDirectory,
            String versionId,
            String javaArgs)
            throws IOException {

        Path target = file(minecraftFolder);
        if (!Files.isRegularFile(target)) {
            throw new IOException(
                    "No se encontro " + FILE_NAME + ".\n"
                            + "Abri el launcher oficial una vez y volve a intentar.");
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        Path backup = target.resolveSibling(FILE_NAME + ".packwarden.bak");
        Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);

        JsonObject profiles;
        if (root.has("profiles") && root.get("profiles").isJsonObject()) {
            profiles = root.getAsJsonObject("profiles");
        } else {
            profiles = new JsonObject();
            root.add("profiles", profiles);
        }

        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        // Si el perfil ya existe se conserva su objeto y solo se actualizan los
        // campos que nos importan: el jugador pudo haberle cambiado el icono, la
        // resolucion o cualquier otra cosa.
        JsonObject profile = profiles.has(profileKey) && profiles.get(profileKey).isJsonObject()
                ? profiles.getAsJsonObject(profileKey)
                : new JsonObject();

        if (!profile.has("created")) {
            profile.addProperty("created", now);
        }
        profile.addProperty("lastUsed", now);
        profile.addProperty("name", displayName);
        profile.addProperty("type", "custom");
        profile.addProperty("icon", icon());
        profile.addProperty("lastVersionId", versionId);
        profile.addProperty("gameDir", gameDirectory.toAbsolutePath().toString());
        profile.addProperty("javaArgs", javaArgs);

        profiles.add(profileKey, profile);

        Path temporary = target.resolveSibling(FILE_NAME + ".packwarden.tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        // Se escribe aparte y se mueve al final, para no dejar el archivo a medias
        // si algo falla en el camino.
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);

        return backup;
    }

    /**
     * Icono del perfil en la lista del launcher.
     *
     * <p>El launcher acepta tanto uno de sus iconos predefinidos como una imagen
     * propia codificada en base64. Se usa la propia para que el perfil se reconozca
     * de un vistazo entre los demas, en vez de quedar con un horno generico.
     *
     * <p>Si la imagen no estuviera, se cae a un icono predefinido antes que dejar
     * el perfil sin ninguno.
     */
    private static String icon() {
        try (java.io.InputStream in =
                LauncherProfiles.class.getResourceAsStream("/packwarden/icon-64.png")) {
            if (in != null) {
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
            }
        } catch (Exception ignored) {
            // Un icono ilegible no puede impedir que se cree el perfil.
        }
        return "Furnace";
    }

    /** Version de NeoForge instalada que coincida con la pedida, si esta. */
    public static Optional<String> findInstalledVersion(Path minecraftFolder, String versionId) {
        Path versionFolder = minecraftFolder.resolve("versions").resolve(versionId);
        Path descriptor = versionFolder.resolve(versionId + ".json");
        return Files.isRegularFile(descriptor) ? Optional.of(versionId) : Optional.empty();
    }
}
