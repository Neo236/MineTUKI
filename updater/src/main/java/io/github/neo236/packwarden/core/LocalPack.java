package io.github.neo236.packwarden.core;

import java.nio.file.Path;
import java.util.Optional;
import net.neoforged.fml.loading.FMLPaths;

/**
 * El estado instalado, cacheado.
 *
 * <p>El manifiesto solo cambia cuando se actualiza el pack, y eso pasa con el juego
 * cerrado. Leerlo una vez alcanza, y evita tocar el disco desde el hilo de red.
 */
public final class LocalPack {

    private static volatile PackManifest cached;
    private static volatile boolean loaded;

    private LocalPack() {}

    public static synchronized Optional<PackManifest> manifest() {
        if (!loaded) {
            Path gameDir = FMLPaths.GAMEDIR.get();
            cached = PackManifest.read(gameDir).orElse(null);
            loaded = true;
        }
        return Optional.ofNullable(cached);
    }

    /** Hash del indice instalado, o {@code null} si no hay manifiesto. */
    public static String installedIndexHash() {
        return manifest().map(PackManifest::indexFileHash).orElse(null);
    }

    public static synchronized void invalidate() {
        loaded = false;
        cached = null;
    }
}
