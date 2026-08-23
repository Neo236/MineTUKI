package io.github.neo236.packwarden.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Que cambia entre lo instalado y lo publicado.
 *
 * <p>Existe para que la pantalla de actualizacion pueda decir "entra X, sale Y" en
 * vez de "hay una actualizacion". Es la diferencia entre un aviso que el jugador
 * acepta a ciegas y uno que puede leer.
 */
public record PackChangelog(List<String> added, List<String> removed, List<String> updated) {

    public static final PackChangelog EMPTY = new PackChangelog(List.of(), List.of(), List.of());

    /**
     * @param installed lo que registra el manifiesto local (ruta de metadato -> entrada)
     * @param published lo que declara el indice remoto (ruta de metadato -> hash)
     */
    public static PackChangelog between(
            Map<String, PackManifest.Entry> installed, Map<String, String> published) {

        if (published.isEmpty()) {
            return EMPTY;
        }

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> updated = new ArrayList<>();

        for (Map.Entry<String, String> entry : published.entrySet()) {
            PackManifest.Entry local = installed.get(entry.getKey());
            if (local == null) {
                added.add(displayName(entry.getKey()));
            } else if (local.metaHash() != null && !local.metaHash().equalsIgnoreCase(entry.getValue())) {
                updated.add(displayName(entry.getKey()));
            }
        }

        for (Map.Entry<String, PackManifest.Entry> local : installed.entrySet()) {
            // Solo cuenta como "se quita" lo que de verdad estaba instalado de este
            // lado. El manifiesto tambien anota mods del otro lado, y los conserva
            // aunque ya no esten en el pack: contarlos hacia aparecer bajas
            // fantasma de mods que el jugador nunca tuvo.
            if (local.getValue().installed() && !published.containsKey(local.getKey())) {
                removed.add(displayName(local.getKey()));
            }
        }

        added.sort(String.CASE_INSENSITIVE_ORDER);
        removed.sort(String.CASE_INSENSITIVE_ORDER);
        updated.sort(String.CASE_INSENSITIVE_ORDER);
        return new PackChangelog(added, removed, updated);
    }

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && updated.isEmpty();
    }

    public int total() {
        return added.size() + removed.size() + updated.size();
    }

    /**
     * "mods/create-aeronautics.pw.toml" -> "Create Aeronautics".
     *
     * <p>Tambien maneja archivos sueltos, que llegan con nombre de jar y version
     * pegada: "mods/minetuki_updater-1.0.0.jar" -> "Minetuki Updater".
     */
    static String displayName(String metaPath) {
        String name = metaPath;

        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        for (String extension : new String[] {".pw.toml", ".jar", ".zip"}) {
            if (name.endsWith(extension)) {
                name = name.substring(0, name.length() - extension.length());
                break;
            }
        }

        // Se descartan los tramos finales que son numeros de version. Nunca el
        // primero: hay mods que empiezan con digito, como "3dskinlayers".
        String[] parts = name.split("[-_]");
        int last = parts.length;
        while (last > 1 && parts[last - 1].matches("[vV]?\\d[\\w.+]*")) {
            last--;
        }

        name = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, last)).trim();

        if (name.isEmpty()) {
            return metaPath;
        }

        StringBuilder out = new StringBuilder(name.length());
        boolean startOfWord = true;
        for (char c : name.toCharArray()) {
            out.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = c == ' ';
        }
        return out.toString();
    }
}
