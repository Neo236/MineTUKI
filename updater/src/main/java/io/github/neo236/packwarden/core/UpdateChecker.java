package io.github.neo236.packwarden.core;

import io.github.neo236.packwarden.PackWarden;
import io.github.neo236.packwarden.config.WardenConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Decide si hay algo que actualizar.
 *
 * <p>Compara el hash de indice que registro packwiz al instalar contra el que
 * declara el pack publicado. No inventa nada: si no puede saberlo, lo dice.
 */
public final class UpdateChecker {

    public enum State {
        /** No hay URL configurada. */
        DISABLED,
        /** No se pudo consultar. Sin internet, por ejemplo. */
        OFFLINE,
        /** Hay archivos del pack, pero no los instalo packwiz: no hay con que comparar. */
        NOT_MANAGED,
        UP_TO_DATE,
        UPDATE_AVAILABLE
    }

    public record Result(
            State state, String installedVersion, String publishedVersion, String publishedIndexHash,
            PackChangelog changelog) {

        public boolean updateAvailable() {
            return state == State.UPDATE_AVAILABLE;
        }

        static Result of(State state) {
            return new Result(state, null, null, null, PackChangelog.EMPTY);
        }
    }

    private UpdateChecker() {}

    /**
     * @param packFolder la carpeta de juego: donde packwiz deja su manifiesto
     */
    public static Result check(Path packFolder) {
        String url = WardenConfig.COMMON.packUrl.get();
        if (url == null || url.isBlank()) {
            return Result.of(State.DISABLED);
        }

        RemotePack remote = new RemotePack(
                Duration.ofSeconds(WardenConfig.COMMON.httpTimeoutSeconds.get()));

        Optional<RemotePack.Snapshot> published =
                remote.fetch(url, WardenConfig.COMMON.fallbackPackUrl.get());
        if (published.isEmpty()) {
            return Result.of(State.OFFLINE);
        }
        RemotePack.Snapshot snapshot = published.get();

        Optional<PackManifest> manifest = PackManifest.read(packFolder);
        if (manifest.isEmpty()) {
            // Sin manifiesto no hay forma honesta de saber que hay instalado. La
            // version anterior de este mod asumia "hay actualizacion", que es como
            // no chequear nada.
            PackWarden.LOG.info(
                    "No hay {} en {}: la instalacion no la gestiona packwiz.",
                    PackManifest.FILE_NAME, packFolder);
            return new Result(
                    State.NOT_MANAGED, null, snapshot.version(), snapshot.indexHash(), PackChangelog.EMPTY);
        }

        PackManifest installed = manifest.get();
        String installedHash = installed.indexFileHash();

        if (installedHash != null && installedHash.equalsIgnoreCase(snapshot.indexHash())) {
            return new Result(
                    State.UP_TO_DATE, snapshot.version(), snapshot.version(), snapshot.indexHash(),
                    PackChangelog.EMPTY);
        }

        Map<String, String> publishedFiles = remote.fetchIndexFiles(snapshot);
        PackChangelog changelog = PackChangelog.between(installed.installedFiles(), publishedFiles);

        PackWarden.LOG.info(
                "Actualizacion disponible: {} cambios (indice {} -> {})",
                changelog.total(), shorten(installedHash), shorten(snapshot.indexHash()));

        return new Result(
                State.UPDATE_AVAILABLE, null, snapshot.version(), snapshot.indexHash(), changelog);
    }

    private static String shorten(String hash) {
        if (hash == null) {
            return "?";
        }
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }
}
