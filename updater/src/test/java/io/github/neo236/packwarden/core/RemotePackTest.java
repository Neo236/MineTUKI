package io.github.neo236.packwarden.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lectura del pack publicado. Aca vivio el error que dejo el modpack sin instalar. */
class RemotePackTest {

    private static final String PACK_TOML = """
        name = "MineTUKI"
        author = "Neo236"
        version = "1.4.0"
        pack-format = "packwiz:1.1.0"

        [index]
        file = "index.toml"
        hash-format = "sha256"
        hash = "0d25b6cd3a19e703bc91d7d5d28d0c20b1278bff775da85a1f5f9621cbd2ce5b"

        [versions]
        minecraft = "1.21.1"
        neoforge = "21.1.248"
        """;

    @Test
    @DisplayName("el hash del indice no se confunde con hash-format")
    void noConfundeHashFormat() {
        // Este fue un error real en el gate de CI: la expresion tomaba
        // hash-format y comparaba contra el texto "sha256".
        assertEquals(
                "0d25b6cd3a19e703bc91d7d5d28d0c20b1278bff775da85a1f5f9621cbd2ce5b",
                RemotePack.parseIndexHash(PACK_TOML));
    }

    @Test
    @DisplayName("lee la version del pack")
    void leeVersion() {
        assertEquals("1.4.0", RemotePack.parseVersion(PACK_TOML));
    }

    @Test
    @DisplayName("un archivo que no es un pack.toml no devuelve hash")
    void archivoAjeno() {
        assertNull(RemotePack.parseIndexHash("<html><body>404</body></html>"));
    }

    @Test
    @DisplayName("lee los pares archivo-hash del indice")
    void leeElIndice() {
        String index = """
            hash-format = "sha256"

            [[files]]
            file = "mods/3dskinlayers.pw.toml"
            hash = "c1f98a1e9d7201f0b6ddc3f705f43a53f7539978f48425de5c1177a310fe7b86"
            metafile = true

            [[files]]
            file = "mods/create.pw.toml"
            hash = "dcaf5f2e28db0b9a127d0167456832341045ddc9aec058c3d9fcf53f618d2404"
            metafile = true
            """;

        Map<String, String> archivos = RemotePack.parseIndexEntries(index);

        assertEquals(2, archivos.size());
        assertEquals(
                "c1f98a1e9d7201f0b6ddc3f705f43a53f7539978f48425de5c1177a310fe7b86",
                archivos.get("mods/3dskinlayers.pw.toml"));
        // El hash-format de la primera linea no puede colarse como si fuera un archivo.
        assertEquals(2, archivos.size());
    }
}
