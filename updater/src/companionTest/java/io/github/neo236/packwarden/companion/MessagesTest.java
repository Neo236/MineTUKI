package io.github.neo236.packwarden.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los textos de la ventana del instalador.
 *
 * <p>Messages.get() devuelve la clave cuando no encuentra la traduccion, en vez
 * de romper la ventana. Eso esta bien para no dejar a nadie a pie, pero
 * significa que una clave mal escrita o sin traducir no falla en ningun lado:
 * sale en pantalla como "step.neoforgeWorking" y listo. Estas pruebas son las
 * que lo convierten en un error.
 */
class MessagesTest {

    /** Llamadas del estilo Messages.get("step.algo", ...) en el codigo. */
    private static final Pattern LLAMADA = Pattern.compile("Messages\\.get\\(\"([^\"]+)\"");

    private static Set<String> claves(Locale locale) {
        return new TreeSet<>(ResourceBundle.getBundle("packwarden.installer", locale).keySet());
    }

    @Test
    @DisplayName("los dos idiomas tienen exactamente las mismas claves")
    void bothLanguagesMatch() {
        // Una clave que existe en ingles y no en español deja media ventana sin
        // traducir para quien la use en español, sin aviso ninguno.
        assertEquals(claves(Locale.ENGLISH), claves(Locale.forLanguageTag("es")),
                "las claves de installer.properties y installer_es.properties no coinciden");
    }

    @Test
    @DisplayName("cada texto que pide el codigo existe en los dos idiomas")
    void everyRequestedKeyExists() throws IOException {
        Path sources = Paths.get(System.getProperty("companion.sources", "src/companion/java"));
        assertTrue(Files.isDirectory(sources), "no se encontro " + sources.toAbsolutePath());

        Set<String> pedidas = new TreeSet<>();
        try (Stream<Path> archivos = Files.walk(sources)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher encontrado = LLAMADA.matcher(Files.readString(archivo, StandardCharsets.UTF_8));
                while (encontrado.find()) {
                    pedidas.add(encontrado.group(1));
                }
            }
        }

        assertTrue(pedidas.size() > 20, "el escaneo no encontro casi nada: " + pedidas);

        Set<String> ingles = claves(Locale.ENGLISH);
        Set<String> espanol = claves(Locale.forLanguageTag("es"));
        Set<String> faltan = new TreeSet<>();
        for (String clave : pedidas) {
            if (!ingles.contains(clave) || !espanol.contains(clave)) {
                faltan.add(clave);
            }
        }
        assertTrue(faltan.isEmpty(), "el codigo pide estos textos y no estan traducidos: " + faltan);
    }

    @Test
    @DisplayName("los saltos de linea van escritos y no parten la clave en dos")
    void multilineValuesStayOnOneLine() {
        // Un salto de linea de verdad adentro de un .properties corta el valor: la
        // segunda mitad queda como una entrada nueva, con la primera palabra de
        // clave y el resto de valor, y el texto se pierde sin que falle nada.
        //
        // Se pide que toda clave empiece en minuscula. Las de verdad se escriben
        // asi --intro, step.algo, error.algo-- mientras que la mitad suelta de una
        // linea partida es la primera palabra de una oracion, con mayuscula.
        for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.forLanguageTag("es")}) {
            for (String clave : claves(locale)) {
                assertTrue(clave.matches("[a-z][a-zA-Z0-9]*(\\.[a-zA-Z0-9]+)*"),
                        "clave sospechosa en " + locale + ", parece media linea suelta: " + clave);
            }
        }
    }
}
