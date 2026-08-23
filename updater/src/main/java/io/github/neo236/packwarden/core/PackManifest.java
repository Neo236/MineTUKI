package io.github.neo236.packwarden.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.neo236.packwarden.PackWarden;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lectura del manifiesto que deja packwiz-installer en la carpeta del pack.
 *
 * <p>Es el estado real de la instalacion, y por eso es la fuente correcta para
 * saber que version esta puesta. La implementacion anterior buscaba un pack.toml
 * con una ruta relativa al directorio de trabajo del proceso, que en el cliente
 * casi nunca existe; el resultado era que siempre reportaba "hay actualizacion".
 *
 * <p>Formato real, verificado contra packwiz-installer v0.5.14:
 *
 * <pre>
 * {
 *   "packFileHash":  {"type":"sha256","value":"..."},
 *   "indexFileHash": {"type":"sha256","value":"..."},
 *   "cachedFiles": {
 *     "mods/ejemplo.pw.toml": {
 *       "hash":           {"type":"sha256","value":"..."},
 *       "linkedFileHash": {"type":"sha256","value":"..."},
 *       "cachedLocation": "mods/ejemplo-1.2.3.jar",
 *       "optionValue": true
 *     }
 *   },
 *   "cachedSide": "client"
 * }
 * </pre>
 */
public record PackManifest(
        String packFileHash, String indexFileHash, String side, Map<String, Entry> installedFiles) {

    /**
     * Un archivo del pack tal como quedo registrado.
     *
     * <p>No todas las entradas corresponden a algo instalado. packwiz tambien anota
     * las del otro lado, con la forma {@code {"optionValue":true,"onlyOtherSide":true}}:
     * sin hash y sin archivo. Y las conserva aunque el mod ya no este en el indice,
     * porque nunca hubo nada que borrar.
     *
     * <p>Confundirlas con archivos instalados hace que aparezcan como "se quitan"
     * mods que en realidad nunca estuvieron de este lado.
     */
    public record Entry(String metaHash, String installedPath, boolean installed) {}

    public static final String FILE_NAME = "packwiz.json";

    /**
     * @param installedFiles ruta del metadato ("mods/x.pw.toml") a lo que quedo
     *     instalado a partir de el. Para entradas sin archivo asociado, el camino
     *     instalado es el propio metadato.
     */
    public PackManifest {
        installedFiles = Collections.unmodifiableMap(new LinkedHashMap<>(installedFiles));
    }

    /** Devuelve vacio si no hay manifiesto, o si esta corrupto. */
    public static Optional<PackManifest> read(Path packFolder) {
        Path file = packFolder.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            Map<String, Entry> files = new LinkedHashMap<>();
            JsonElement cached = root.get("cachedFiles");
            if (cached != null && cached.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : cached.getAsJsonObject().entrySet()) {
                    String metaPath = entry.getKey();
                    String installedPath = metaPath;
                    String metaHash = null;
                    boolean otherSide = false;

                    if (entry.getValue().isJsonObject()) {
                        JsonObject value = entry.getValue().getAsJsonObject();
                        if (value.has("cachedLocation") && value.get("cachedLocation").isJsonPrimitive()) {
                            installedPath = value.get("cachedLocation").getAsString();
                        }
                        metaHash = hashValue(value, "hash");
                        otherSide = value.has("onlyOtherSide")
                                && value.get("onlyOtherSide").isJsonPrimitive()
                                && value.get("onlyOtherSide").getAsBoolean();
                    }

                    files.put(metaPath, new Entry(metaHash, installedPath, !otherSide && metaHash != null));
                }
            }

            return Optional.of(new PackManifest(
                    hashValue(root, "packFileHash"),
                    hashValue(root, "indexFileHash"),
                    root.has("cachedSide") ? root.get("cachedSide").getAsString() : null,
                    files));
        } catch (Exception e) {
            PackWarden.LOG.warn("No se pudo leer {}: {}", file, e.toString());
            return Optional.empty();
        }
    }

    /** Los hashes vienen envueltos en un objeto {@code {"type": ..., "value": ...}}. */
    private static String hashValue(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonElement value = element.getAsJsonObject().get("value");
        return value == null ? null : value.getAsString();
    }
}
