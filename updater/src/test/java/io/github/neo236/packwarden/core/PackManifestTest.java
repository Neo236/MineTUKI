package io.github.neo236.packwarden.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lectura del manifiesto de packwiz.
 *
 * <p>Los datos de ejemplo son recortes del manifiesto real de un servidor, no
 * inventados: incluyen las tres formas que puede tomar una entrada.
 */
class PackManifestTest {

    private static final String REAL = """
        {"packFileHash":{"type":"sha256","value":"5b1992b99256bd05"},
         "indexFileHash":{"type":"sha256","value":"6e0ceafc6fd5fabb"},
         "cachedFiles":{
           "mods/create.pw.toml":{"hash":{"type":"sha256","value":"dcaf5f2e"},
             "linkedFileHash":{"type":"sha512","value":"11cc8fc0"},
             "cachedLocation":"mods/create-1.21.1-6.0.10.jar"},
           "mods/better-third-person.pw.toml":{"optionValue":true,"onlyOtherSide":true}
         },
         "cachedSide":"server"}
        """;

    @Test
    @DisplayName("lee los hashes y el lado")
    void leeLoBasico(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve(PackManifest.FILE_NAME), REAL);

        PackManifest manifiesto = PackManifest.read(folder).orElseThrow();

        assertEquals("5b1992b99256bd05", manifiesto.packFileHash());
        assertEquals("6e0ceafc6fd5fabb", manifiesto.indexFileHash());
        assertEquals("server", manifiesto.side());
    }

    @Test
    @DisplayName("distingue lo instalado de lo que es del otro lado")
    void distingueLados(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve(PackManifest.FILE_NAME), REAL);

        var archivos = PackManifest.read(folder).orElseThrow().installedFiles();

        PackManifest.Entry create = archivos.get("mods/create.pw.toml");
        assertTrue(create.installed());
        assertEquals("mods/create-1.21.1-6.0.10.jar", create.installedPath());

        PackManifest.Entry otro = archivos.get("mods/better-third-person.pw.toml");
        assertFalse(otro.installed(), "sin hash y con onlyOtherSide no esta instalado");
    }

    @Test
    @DisplayName("sin manifiesto devuelve vacio en vez de fallar")
    void sinManifiesto(@TempDir Path folder) {
        assertEquals(Optional.empty(), PackManifest.read(folder));
    }

    @Test
    @DisplayName("un manifiesto corrupto devuelve vacio en vez de fallar")
    void manifiestoCorrupto(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve(PackManifest.FILE_NAME), "{ esto no es json");
        assertEquals(Optional.empty(), PackManifest.read(folder));
    }
}
