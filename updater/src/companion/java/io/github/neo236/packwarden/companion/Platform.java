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

    /**
     * Memoria a asignarle al juego, en gigabytes.
     *
     * <p>Sin esto el launcher usa su valor por defecto, que con 151 mods y Distant
     * Horizons es un crash asegurado. Se calcula sobre la RAM real de la maquina en
     * vez de fijar un numero que estaria mal en los dos extremos.
     */
    public static int recommendedHeapGb() {
        long totalBytes = totalMemoryBytes();
        if (totalBytes <= 0) {
            return 4;
        }
        long totalGb = totalBytes / (1024L * 1024L * 1024L);
        long half = Math.round(totalGb * 0.55);
        return (int) Math.max(4, Math.min(8, half));
    }

    private static long totalMemoryBytes() {
        try {
            var bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            var method = bean.getClass().getMethod("getTotalMemorySize");
            method.setAccessible(true);
            return (long) method.invoke(bean);
        } catch (Exception e) {
            return -1;
        }
    }
}
