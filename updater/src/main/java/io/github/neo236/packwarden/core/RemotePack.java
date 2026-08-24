package io.github.neo236.packwarden.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descarga y lectura del pack publicado.
 *
 * <p>Solo se leen unas pocas claves de TOML, con formato fijo generado siempre por
 * la misma herramienta, asi que se resuelve con expresiones regulares acotadas en
 * vez de arrastrar un parser completo.
 */
public final class RemotePack {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("PackWarden");

    /** {@code version = "1.4.0"} en la raiz de pack.toml. */
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*version\\s*=\\s*\"([^\"]*)\"");

    /**
     * {@code hash = "..."} dentro de la seccion {@code [index]}.
     *
     * <p>El espacio antes del {@code =} importa: sin el, tambien matchea
     * {@code hash-format}, que fue justamente el bug que rompio la validacion del
     * indice en el gate de CI.
     */
    private static final Pattern INDEX_HASH =
            Pattern.compile("(?s)\\[index\\].*?^\\s*hash\\s*=\\s*\"([0-9a-fA-F]+)\"", Pattern.MULTILINE);

    /**
     * Entradas {@code [[files]]} de index.toml, que siempre traen el hash en la
     * linea siguiente al nombre. Verificado sobre las 153 entradas del pack real.
     */
    private static final Pattern INDEX_ENTRY = Pattern.compile(
            "(?m)^\\s*file\\s*=\\s*\"([^\"]+)\"\\s*\\R\\s*hash\\s*=\\s*\"([0-9a-fA-F]+)\"");

    private final HttpClient http;
    private final Duration timeout;

    public RemotePack(Duration timeout) {
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Lo que hay publicado ahora mismo. */
    public record Snapshot(String url, String body, String fileHash, String indexHash, String version) {}

    /**
     * Descarga el pack.toml, probando la URL principal y despues la alternativa.
     *
     * <p>Devuelve vacio si ninguna responde: quedarse sin internet no es un error
     * que deba molestar al jugador.
     */
    public Optional<Snapshot> fetch(String primaryUrl, String fallbackUrl) {
        for (String url : candidates(primaryUrl, fallbackUrl)) {
            try {
                Optional<Snapshot> snapshot = fetchOne(url);
                if (snapshot.isPresent()) {
                    return snapshot;
                }
            } catch (Exception e) {
                LOG.warn("No se pudo consultar {}: {}", url, e.toString());
            }
        }
        return Optional.empty();
    }

    private static List<String> candidates(String primaryUrl, String fallbackUrl) {
        List<String> urls = new ArrayList<>(2);
        if (primaryUrl != null && !primaryUrl.isBlank()) {
            urls.add(primaryUrl.trim());
        }
        if (fallbackUrl != null && !fallbackUrl.isBlank() && !fallbackUrl.trim().equals(primaryUrl)) {
            urls.add(fallbackUrl.trim());
        }
        return urls;
    }

    private Optional<Snapshot> fetchOne(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "PackWarden")
                .GET()
                .build();

        // Se piden bytes crudos y no texto: el hash tiene que calcularse sobre lo
        // mismo que hashea packwiz, y decodificar y volver a codificar puede no ser
        // una identidad exacta.
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            LOG.warn("{} respondio {}", url, response.statusCode());
            return Optional.empty();
        }

        byte[] raw = response.body();
        String body = new String(raw, StandardCharsets.UTF_8);
        String indexHash = parseIndexHash(body);
        if (indexHash == null) {
            LOG.warn("{} no parece un pack.toml: no tiene hash de indice", url);
            return Optional.empty();
        }

        return Optional.of(new Snapshot(url, body, sha256(raw), indexHash, parseVersion(body)));
    }

    /**
     * Lista de archivos declarados en el index.toml que acompaña a ese pack.toml.
     * Se usa solo para armar el detalle de cambios, asi que si falla no se
     * interrumpe la actualizacion.
     */
    public Map<String, String> fetchIndexFiles(Snapshot snapshot) {
        String indexUrl = resolveSibling(snapshot.url(), "index.toml");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(indexUrl))
                    .timeout(timeout)
                    .header("User-Agent", "PackWarden")
                    .GET()
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Map.of();
            }

            return parseIndexEntries(response.body());
        } catch (Exception e) {
            LOG.warn("No se pudo leer el indice remoto: {}", e.toString());
            return Map.of();
        }
    }

    /** El index.toml vive al lado del pack.toml, sea cual sea el host. */
    private static String resolveSibling(String url, String name) {
        int slash = url.lastIndexOf('/');
        return slash < 0 ? name : url.substring(0, slash + 1) + name;
    }

    /** Hash del indice declarado en un pack.toml. Visible para poder probarlo. */
    static String parseIndexHash(String toml) {
        return firstGroup(INDEX_HASH, toml);
    }

    /** Version declarada en un pack.toml. */
    static String parseVersion(String toml) {
        return firstGroup(VERSION, toml);
    }

    /** Pares archivo-hash de un index.toml. */
    static Map<String, String> parseIndexEntries(String toml) {
        Map<String, String> files = new LinkedHashMap<>();
        Matcher matcher = INDEX_ENTRY.matcher(toml);
        while (matcher.find()) {
            files.put(matcher.group(1), matcher.group(2));
        }
        return files;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 deberia existir siempre", e);
        }
    }
}
