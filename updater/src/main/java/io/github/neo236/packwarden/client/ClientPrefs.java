package io.github.neo236.packwarden.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.neo236.packwarden.PackWarden;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Lo que el jugador ya decidio, y no queremos volver a preguntarle.
 *
 * <p>Se guarda contra el hash del indice, no contra un booleano suelto: si el
 * jugador dice "no me avises mas por esta version", el aviso tiene que volver a
 * aparecer cuando salga la siguiente. Un "no" permanente seria peor que no
 * preguntar.
 */
public final class ClientPrefs {

    private static final String FILE_NAME = "packwarden-client.json";

    private static ClientPrefs instance;

    private String dismissedIndexHash;

    private ClientPrefs() {}

    public static synchronized ClientPrefs get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public boolean isDismissed(String indexHash) {
        return indexHash != null && indexHash.equalsIgnoreCase(dismissedIndexHash);
    }

    public void dismiss(String indexHash) {
        this.dismissedIndexHash = indexHash;
        save();
    }

    public void clearDismissal() {
        this.dismissedIndexHash = null;
        save();
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static ClientPrefs load() {
        ClientPrefs prefs = new ClientPrefs();
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return prefs;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("dismissedIndexHash") && root.get("dismissedIndexHash").isJsonPrimitive()) {
                prefs.dismissedIndexHash = root.get("dismissedIndexHash").getAsString();
            }
        } catch (Exception e) {
            PackWarden.LOG.warn("No se pudo leer {}: {}", path, e.toString());
        }
        return prefs;
    }

    private void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            if (dismissedIndexHash != null) {
                root.addProperty("dismissedIndexHash", dismissedIndexHash);
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(root.toString());
            }
        } catch (Exception e) {
            PackWarden.LOG.warn("No se pudo guardar {}: {}", path, e.toString());
        }
    }
}
