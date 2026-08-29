package io.github.neo236.packwarden.companion;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.JOptionPane;

/**
 * Deja rastro de lo que pasa cuando el instalador se cae.
 *
 * <p>Existe por un caso concreto: el .bat lanzaba el instalador con javaw.exe,
 * que no tiene consola, y ademas lo desprendia con "start". Si la JVM no
 * arrancaba --un .jar que no estaba al lado, una version de Java demasiado
 * vieja-- el error no se escribia en ningun lado y lo unico que veia el
 * jugador era una ventana negra que se abria y se cerraba.
 *
 * <p>Ahora el .bat espera al proceso y guarda su salida, asi que escribir en
 * stderr alcanza para que quede registrado. Pero el jar tambien se puede
 * ejecutar con doble clic, y ahi no hay consola que valga: por eso, cuando
 * algo explota, ademas se escribe un archivo y se muestra un cartel.
 */
public final class Diagnostics {

    /** Nombre del archivo que se escribe solo cuando hay un fallo. */
    static final String LOG_NAME = "packwarden-error.log";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Diagnostics() {}

    /**
     * Escribe en stderr con que se esta ejecutando esto.
     *
     * <p>La version de Java es el dato que mas veces explica el problema, asi
     * que va siempre, aunque no falle nada.
     */
    public static void start(String[] args) {
        System.err.println("PackWarden companion");
        System.err.println("  java    " + System.getProperty("java.version")
                + "  (" + System.getProperty("java.home") + ")");
        System.err.println("  sistema " + System.getProperty("os.name")
                + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch"));
        System.err.println("  args    " + String.join(" ", args));
        System.err.flush();
    }

    /**
     * Deja la traza en stderr --que el .bat guarda-- y en un archivo, para
     * quien haya ejecutado el jar con doble clic. Devuelve donde quedo, o null
     * si no se pudo escribir en ningun lado.
     */
    public static Path record(Throwable error) {
        String detail = describe(error);
        System.err.println(detail);
        System.err.flush();
        return write(detail);
    }

    /**
     * Lo de arriba mas un cartel, para cuando no hay otro aviso que darle al
     * jugador. Los caminos que ya muestran su propio mensaje usan
     * {@link #record(Throwable)} y no este.
     */
    public static void crash(String title, Throwable error) {
        Path log = record(error);

        String message = summary(error)
                + (log == null ? "" : System.lineSeparator() + System.lineSeparator() + log);
        try {
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) {
            // Sin entorno grafico no hay cartel que mostrar; el texto ya salio
            // por stderr y por el archivo, que es lo que importa.
        }
    }

    /** El texto completo que se guarda: encabezado, entorno y traza. */
    static String describe(Throwable error) {
        StringWriter buffer = new StringWriter();
        PrintWriter out = new PrintWriter(buffer);
        out.println();
        out.println("--- fallo del instalador: " + ZonedDateTime.now().format(STAMP) + " ---");
        out.println("java    " + System.getProperty("java.version")
                + "  (" + System.getProperty("java.home") + ")");
        out.println("sistema " + System.getProperty("os.name")
                + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch"));
        error.printStackTrace(out);
        out.flush();
        return buffer.toString();
    }

    /**
     * Una linea con el tipo y el mensaje, para el cartel.
     *
     * <p>Algunas excepciones llegan sin mensaje --NullPointerException suele
     * ser una-- y un cartel que dice "null" no le sirve a nadie.
     */
    static String summary(Throwable error) {
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }

    private static Path write(String detail) {
        return write(detail, jarFolder(), tempFolder());
    }

    /**
     * Escribe el detalle en la primera carpeta que lo acepte.
     *
     * <p>La primera opcion es la del jar, que es donde el jugador va a mirar,
     * pero puede no ser escribible: desde una carpeta comprimida, un pendrive
     * de solo lectura o Archivos de programa. Por eso hay una segunda.
     */
    static Path write(String detail, Path... folders) {
        for (Path folder : folders) {
            if (folder == null) {
                continue;
            }
            try {
                Path target = folder.resolve(LOG_NAME);
                Files.writeString(target, detail, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return target;
            } catch (IOException | RuntimeException ignored) {
                // Carpeta de solo lectura o ruta invalida: se prueba la siguiente.
            }
        }
        return null;
    }

    /** La carpeta desde la que se ejecuta el jar, que es donde el jugador va a mirar. */
    static Path jarFolder() {
        try {
            Path jar = Paths.get(Diagnostics.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isDirectory(jar) ? jar : jar.getParent();
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private static Path tempFolder() {
        String temp = System.getProperty("java.io.tmpdir");
        return temp == null || temp.isBlank() ? null : Paths.get(temp);
    }

    /** Handler para los hilos que no atrapan nada por su cuenta, incluido el principal. */
    public static Thread.UncaughtExceptionHandler handler(String title) {
        return (thread, error) -> {
            crash(title, error);
            System.exit(1);
        };
    }
}
