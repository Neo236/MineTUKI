package io.github.neo236.packwarden.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** Diferencias entre sistemas operativos, en un solo lugar. */
public final class Platform {

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    private Platform() {}

    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isMac() {
        return OS.contains("mac");
    }

    /** Carpeta de Minecraft del launcher oficial. */
    public static Path minecraftFolder() {
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, ".minecraft");
            }
            return Paths.get(System.getProperty("user.home"), "AppData", "Roaming", ".minecraft");
        }
        if (isMac()) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        }
        return Paths.get(System.getProperty("user.home"), ".minecraft");
    }

    public static boolean hasOfficialLauncher() {
        return Files.isRegularFile(minecraftFolder().resolve("launcher_profiles.json"));
    }

    /**
     * Busca un Java utilizable, en orden de preferencia.
     *
     * <p>El launcher oficial trae su propio Java 21, asi que en la mayoria de las
     * maquinas no hace falta que el jugador instale nada. Pedirle que instale Java
     * era el punto donde se caia la mitad de la gente.
     */
    public static Optional<Path> findJava() {
        List<Path> candidates = new ArrayList<>();

        // 1. El Java con el que corre este mismo proceso.
        candidates.add(javaBinaryIn(Paths.get(System.getProperty("java.home"))));

        // 2. JAVA_HOME.
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            candidates.add(javaBinaryIn(Paths.get(javaHome)));
        }

        // 3. Los runtimes que descarga el propio launcher.
        candidates.addAll(launcherRuntimes());

        for (Path candidate : candidates) {
            if (candidate != null && Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }

        // 4. Lo que haya en el PATH, si es que hay algo.
        return Optional.empty();
    }

    /**
     * Runtimes del launcher oficial.
     *
     * <p>La ruta cambia entre la version de Microsoft Store y la clasica, asi que se
     * prueban las dos y se recorre hacia abajo buscando el ejecutable.
     */
    private static List<Path> launcherRuntimes() {
        List<Path> roots = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");

        if (localAppData != null && !localAppData.isBlank()) {
            roots.add(Paths.get(
                    localAppData,
                    "Packages",
                    "Microsoft.4297127D64EC6_8wekyb3d8bbwe",
                    "LocalCache",
                    "Local",
                    "runtime"));
        }
        roots.add(minecraftFolder().resolve("runtime"));
        roots.add(Paths.get("C:", "Program Files (x86)", "Minecraft Launcher", "runtime"));

        List<Path> found = new ArrayList<>();
        String binaryName = isWindows() ? "java.exe" : "java";
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root, 6)) {
                walk.filter(path -> path.getFileName().toString().equals(binaryName))
                        .filter(Files::isExecutable)
                        .forEach(found::add);
            } catch (Exception ignored) {
                // Un runtime ilegible no es motivo para abortar la busqueda.
            }
        }
        return found;
    }

    private static Path javaBinaryIn(Path javaHome) {
        return javaHome.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
    }

    /** Se reserva siempre esto para el sistema y la memoria fuera del heap. */
    private static final int RESERVED_GB = 4;

    private static final int MIN_HEAP_GB = 4;
    private static final int MAX_HEAP_GB = 10;

    /** Por debajo de esto, un pack pesado no va a andar bien y conviene avisarlo. */
    private static final int COMFORTABLE_TOTAL_GB = 10;

    /** RAM total de la maquina en gigabytes, o -1 si no se pudo averiguar. */
    public static int totalMemoryGb() {
        long bytes = totalMemoryBytes();
        return bytes <= 0 ? -1 : (int) (bytes / (1024L * 1024L * 1024L));
    }

    /**
     * Memoria a asignarle al juego, en gigabytes.
     *
     * <p>Sin esto el launcher usa su valor por defecto, que con 151 mods y Distant
     * Horizons no alcanza. Pero tampoco sirve dar "todo lo que haya": el -Xmx es
     * solo el heap de Java, y aparte hacen falta la memoria fuera del heap, la JVM,
     * los drivers de video y el sistema. Pasarse hace que la maquina empiece a usar
     * disco, que rinde peor que un heap chico. Ademas las pausas del recolector
     * crecen con el tamaño del heap.
     *
     * <p>Por eso se reserva una franja fija y se acota arriba y abajo.
     */
    public static int recommendedHeapGb() {
        int totalGb = totalMemoryGb();
        if (totalGb <= 0) {
            return MIN_HEAP_GB;
        }
        int heap = totalGb - RESERVED_GB;
        return Math.max(MIN_HEAP_GB, Math.min(MAX_HEAP_GB, heap));
    }

    /**
     * Si la maquina esta justa de memoria para un pack pesado.
     *
     * <p>Se avisa en vez de inflar el heap: darle mas memoria a una maquina que no
     * la tiene no la hace andar mejor, la hace andar peor.
     */
    public static boolean isMemoryTight() {
        int totalGb = totalMemoryGb();
        return totalGb > 0 && totalGb < COMFORTABLE_TOTAL_GB;
    }

    /**
     * RAM fisica total.
     *
     * <p>Se usa la interfaz publica {@code com.sun.management.OperatingSystemMXBean}
     * y no reflexion sobre la clase concreta: la implementacion vive en un modulo
     * que no exporta sus tipos, asi que invocar el metodo por reflexion falla por
     * acceso y la deteccion terminaba devolviendo "desconocida".
     */
    private static long totalMemoryBytes() {
        try {
            java.lang.management.OperatingSystemMXBean bean =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getTotalMemorySize();
            }
        } catch (Throwable ignored) {
            // Si el runtime no trae jdk.management se cae al valor por defecto.
        }
        return -1;
    }
}
