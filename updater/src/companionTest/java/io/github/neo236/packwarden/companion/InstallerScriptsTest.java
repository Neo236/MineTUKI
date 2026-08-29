package io.github.neo236.packwarden.companion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los dos lanzadores que abren el instalador.
 *
 * <p>Son archivos de texto y no codigo, asi que nada los compila ni los revisa.
 * Estas pruebas fijan las cuatro decisiones que hicieron que en una maquina el
 * instalador se abriera y se cerrara sin dejar rastro: se lanzaba con javaw.exe
 * --que no tiene consola--, desprendido con "start" --asi que el .bat terminaba
 * enseguida--, tomando el primer Java que apareciera --incluido el Java 8 que
 * el launcher guarda como jre-legacy-- y sin pausa al fallar.
 */
class InstallerScriptsTest {

    private static String bat;
    private static byte[] batBytes;
    private static String command;

    @BeforeAll
    static void leer() throws IOException {
        Path folder = Paths.get(System.getProperty("installer.scripts", "src/installer"));
        Path batFile = folder.resolve("instalar-windows.bat");
        Path commandFile = folder.resolve("instalar-mac-linux.command");

        assertTrue(Files.isRegularFile(batFile), "no se encontro " + batFile.toAbsolutePath());
        assertTrue(Files.isRegularFile(commandFile), "no se encontro " + commandFile.toAbsolutePath());

        batBytes = Files.readAllBytes(batFile);
        bat = new String(batBytes, StandardCharsets.UTF_8);
        command = Files.readString(commandFile, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("el .bat no usa javaw ni desprende el proceso")
    void windowsKeepsTheConsole() {
        // javaw no tiene consola: si la JVM no arranca, el error no va a parar
        // a ningun lado. Y "start" hace que el .bat termine antes de saber como
        // le fue al instalador, con lo que nunca llega a mostrar nada.
        //
        // Los comentarios del propio script explican esto y nombran las dos
        // cosas, asi que se miran solo las lineas que se ejecutan.
        String ejecutable = sinComentarios(bat);
        assertFalse(ejecutable.contains("javaw"), "el .bat volvio a usar javaw");
        assertFalse(ejecutable.contains("start \"\""),
                "el .bat volvio a desprender el proceso con start");
    }

    private static String sinComentarios(String script) {
        StringBuilder out = new StringBuilder();
        for (String line : script.split("\r?\n")) {
            if (!line.stripLeading().toLowerCase().startsWith("rem ")) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    @Test
    @DisplayName("los dos lanzadores exigen Java 17 o mas nuevo")
    void bothRequireJava17() {
        // El launcher guarda varios runtimes a la vez y jre-legacy es Java 8.
        // Quedarse con el primero que aparezca mata la JVM al instante.
        assertTrue(bat.contains("LSS 17"), "el .bat dejo de comparar la version de Java");
        assertTrue(command.contains("-ge 17"), "el .command dejo de comparar la version de Java");
    }

    @Test
    @DisplayName("los dos lanzadores esperan antes de cerrarse cuando algo falla")
    void bothPauseOnFailure() {
        assertTrue(bat.contains("pause"), "el .bat se cierra sin mostrar el error");
        assertTrue(command.contains("read -r -p"), "el .command se cierra sin mostrar el error");
    }

    @Test
    @DisplayName("el .bat se entrega con finales de linea de Windows")
    void windowsScriptIsCrlf() {
        // cmd.exe recorre el archivo por posicion de byte: con LF solos, un
        // "goto" o un "call :etiqueta" puede caer en medio de una linea.
        for (int i = 0; i < batBytes.length; i++) {
            if (batBytes[i] == '\n') {
                assertTrue(i > 0 && batBytes[i - 1] == '\r',
                        "hay un salto de linea sin retorno de carro en el byte " + i);
            }
        }
    }
}
