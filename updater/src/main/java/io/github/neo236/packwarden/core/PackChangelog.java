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

        for (String metaPath : installed.keySet()) {
            if (!published.containsKey(metaPath)) {
                removed.add(displayName(metaPath));
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

    /** "mods/create-aeronautics.pw.toml" -> "Create Aeronautics". */
    static String displayName(String metaPath) {
        String name = metaPath;

        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".pw.toml")) {
            name = name.substring(0, name.length() - ".pw.toml".length());
        }
        name = name.replace('-', ' ').replace('_', ' ').trim();

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
