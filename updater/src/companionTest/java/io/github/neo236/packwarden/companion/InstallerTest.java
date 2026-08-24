package io.github.neo236.packwarden.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Los dos archivos que el instalador escribe y que, mal escritos, rompen cosas ajenas. */
class InstallerTest {

    /** Los nombres reales del pack: uno empieza con guion y tiene espacios y parentesis. */
    private static final String FRESH_MOVES = "-1.21.2 Fresh Moves v3.1 (With Animated Eyes).zip";

    private static Installer.Options options(Path folder) {
        return new Installer.Options(
                Installer.Destination.DEDICATED_PROFILE,
                folder,
                folder,
                "https://ejemplo/pack.toml",
                "https://respaldo/pack.toml",
                "neoforge-21.1.248",
                "MineTUKI",
                "packwarden-minetuki",
                "NeoTUKI Mod Updater",
                "tuki",
                "es_es",
                folder.resolve("bootstrap.jar"));
    }

    @Test
    @DisplayName("options.txt deja los paquetes activados y en orden")
    void seedOptions(@TempDir Path folder) throws IOException {
        Path packs = Files.createDirectories(folder.resolve("resourcepacks"));
        Files.writeString(packs.resolve("FreshAnimations_v1.10.4.zip"), "x");
        Files.writeString(packs.resolve(FRESH_MOVES), "x");

        Installer.seedOptions(folder, "es_es");
        String linea = lineaDe(folder.resolve("options.txt"), "resourcePacks:");

        assertTrue(linea.contains("[\"vanilla\""), "vanilla va primero");
        assertTrue(linea.contains("file/" + FRESH_MOVES), "nombre con espacios y parentesis intacto");
        assertTrue(
                linea.indexOf("Fresh Moves") < linea.indexOf("FreshAnimations"),
                "Fresh Moves tiene que ir antes que Fresh Animations");
    }

    @Test
    @DisplayName("options.txt siembra el idioma elegido")
    void seedOptionsIdioma(@TempDir Path folder) throws IOException {
        Installer.seedOptions(folder, "pt_br");
        assertEquals("lang:pt_br", lineaDe(folder.resolve("options.txt"), "lang:"));
    }

    @Test
    @DisplayName("no pisa un options.txt que ya existe")
    void seedOptionsRespetaExistente(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve("options.txt"), "MIO");
        Installer.seedOptions(folder, "es_es");
        assertEquals("MIO", Files.readString(folder.resolve("options.txt")));
    }

    @Test
    @DisplayName("la configuracion del mod queda con la URL puesta")
    void seedModConfig(@TempDir Path folder) throws IOException {
        Installer.seedModConfig(options(folder));

        String comun = Files.readString(folder.resolve("config/packwarden-common.toml"));
        assertTrue(comun.contains("pack_url = \"https://ejemplo/pack.toml\""));
        assertTrue(comun.contains("fallback_pack_url = \"https://respaldo/pack.toml\""));
        assertTrue(comun.contains("command_alias = \"tuki\""));
        // Sin todas las claves, NeoForge reescribe el archivo y avisa que estaba mal.
        assertTrue(comun.contains("http_timeout_seconds"));

        assertTrue(Files.exists(folder.resolve("config/packwarden-client.toml")));
    }

    @Test
    @DisplayName("registrar el perfil no pierde los que ya estaban")
    void perfilConservaLosDemas(@TempDir Path folder) throws IOException {
        Files.writeString(
                folder.resolve("launcher_profiles.json"),
                "{\"profiles\":{\"ajeno\":{\"name\":\"Otro\",\"lastVersionId\":\"1.21.1\",\"raro\":42}},"
                        + "\"settings\":{\"enableSnapshots\":false},\"version\":3}");

        Path respaldo = LauncherProfiles.install(
                folder, "packwarden-minetuki", "MineTUKI", folder.resolve("juego"),
                "neoforge-21.1.248", "-Xmx8G");

        JsonObject raiz = JsonParser.parseString(
                        Files.readString(folder.resolve("launcher_profiles.json")))
                .getAsJsonObject();
        JsonObject perfiles = raiz.getAsJsonObject("profiles");

        assertTrue(Files.exists(respaldo), "tiene que dejar respaldo antes de escribir");
        assertTrue(perfiles.has("ajeno"), "no puede perder perfiles ajenos");
        assertEquals(42, perfiles.getAsJsonObject("ajeno").get("raro").getAsInt(),
                "los campos que no conocemos se conservan");
        assertTrue(raiz.has("settings"), "las claves de raiz se conservan");

        JsonObject nuestro = perfiles.getAsJsonObject("packwarden-minetuki");
        assertEquals("MineTUKI", nuestro.get("name").getAsString());
        assertEquals("neoforge-21.1.248", nuestro.get("lastVersionId").getAsString());
        assertEquals("-Xmx8G", nuestro.get("javaArgs").getAsString());

        // El perfil lleva icono propio, no el generico del launcher.
        String icono = nuestro.get("icon").getAsString();
        assertTrue(icono.startsWith("data:image/png;base64,"), "tiene que llevar el icono propio");
        assertTrue(icono.length() > 200, "el icono no puede venir vacio");
    }

    @Test
    @DisplayName("registrar el perfil dos veces no duplica nada")
    void perfilIdempotente(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve("launcher_profiles.json"), "{\"profiles\":{}}");

        for (int i = 0; i < 2; i++) {
            LauncherProfiles.install(
                    folder, "packwarden-minetuki", "MineTUKI", folder.resolve("juego"),
                    "neoforge-21.1.248", "-Xmx8G");
        }

        Set<String> claves = new TreeSet<>(JsonParser.parseString(
                        Files.readString(folder.resolve("launcher_profiles.json")))
                .getAsJsonObject()
                .getAsJsonObject("profiles")
                .keySet());
        assertEquals(1, claves.size());
    }

    @Test
    @DisplayName("la memoria asignada deja margen para el sistema")
    void memoria() {
        int total = Platform.totalMemoryGb();
        int heap = Platform.recommendedHeapGb();

        assertTrue(heap >= 4, "nunca menos de 4 GB");
        assertTrue(heap <= 10, "nunca mas de 10 GB");
        if (total > 0) {
            assertTrue(heap <= Math.max(4, total - 4), "tiene que dejar margen para el sistema");
        }
        assertFalse(Platform.isMemoryTight() && total >= 10, "el aviso es solo por debajo de 10 GB");
    }

    private static String lineaDe(Path archivo, String prefijo) throws IOException {
        return Files.readAllLines(archivo).stream()
                .filter(l -> l.startsWith(prefijo))
                .findFirst()
                .orElseThrow(() -> new AssertionError("falta la linea " + prefijo));
    }
}
