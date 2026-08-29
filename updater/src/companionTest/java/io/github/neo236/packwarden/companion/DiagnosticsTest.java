package io.github.neo236.packwarden.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * El rastro que deja el instalador cuando se cae.
 *
 * <p>Todo esto existe por un caso real: el instalador se lanzaba con javaw.exe,
 * sin consola y desprendido con "start", y cualquier fallo se perdia entero.
 * El jugador veia una ventana negra abrirse y cerrarse, y no habia nada que
 * pedirle para diagnosticar.
 */
class DiagnosticsTest {

    @Test
    @DisplayName("el resumen no dice 'null' cuando la excepcion no trae mensaje")
    void summaryFallsBackToTheType() {
        // NullPointerException suele llegar sin mensaje, y un cartel que dice
        // "null" no le sirve de nada a quien lo esta leyendo.
        assertEquals("NullPointerException", Diagnostics.summary(new NullPointerException()));
        assertEquals("NullPointerException", Diagnostics.summary(new NullPointerException("   ")));
        assertEquals(
                "IOException: no se encontro el bootstrap",
                Diagnostics.summary(new IOException("no se encontro el bootstrap")));
    }

    @Test
    @DisplayName("el detalle lleva la version de Java y la traza completa")
    void describeCarriesTheEnvironment() {
        // La version de Java es el dato que mas veces explica el problema: el
        // fallo original era un UnsupportedClassVersionError por correr un jar
        // de Java 21 con el Java 8 que el launcher guarda como jre-legacy.
        String detail = Diagnostics.describe(new IllegalStateException("algo se rompio"));

        assertTrue(detail.contains(System.getProperty("java.version")), detail);
        assertTrue(detail.contains(System.getProperty("os.name")), detail);
        assertTrue(detail.contains("IllegalStateException: algo se rompio"), detail);
        assertTrue(detail.contains("DiagnosticsTest"), "falta la traza:\n" + detail);
    }

    @Test
    @DisplayName("guarda el registro en la primera carpeta que lo acepte")
    void writeUsesTheFirstUsableFolder(@TempDir Path temp) throws IOException {
        Path inexistente = temp.resolve("no").resolve("existe");
        Path buena = Files.createDirectory(temp.resolve("buena"));

        Path escrito = Diagnostics.write("detalle", inexistente, buena);

        assertEquals(buena.resolve(Diagnostics.LOG_NAME), escrito);
        assertEquals("detalle", Files.readString(escrito, StandardCharsets.UTF_8));
        assertFalse(Files.exists(inexistente), "no deberia haber creado la carpeta mala");
    }

    @Test
    @DisplayName("acumula los fallos en vez de pisar el anterior")
    void writeAppends(@TempDir Path temp) throws IOException {
        Diagnostics.write("primero\n", temp);
        Path escrito = Diagnostics.write("segundo\n", temp);

        // Si el jugador reintenta y falla distinto, las dos trazas tienen que
        // seguir ahi: la primera suele ser la que explica lo que paso.
        assertEquals("primero\nsegundo\n", Files.readString(escrito, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("sin ninguna carpeta utilizable devuelve null en vez de explotar")
    void writeGivesUpQuietly(@TempDir Path temp) {
        // Reportar un fallo no puede provocar otro fallo.
        assertNull(Diagnostics.write("detalle", temp.resolve("a").resolve("b")));
    }

    @Test
    @DisplayName("el manejador de excepciones no atrapadas queda armado")
    void handlerIsBuilt() {
        assertNotNull(Diagnostics.handler("MineTUKI"));
    }
}
