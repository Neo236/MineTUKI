package io.github.neo236.packwarden.server;

import io.github.neo236.packwarden.PackWarden;
import io.github.neo236.packwarden.config.WardenConfig;
import io.github.neo236.packwarden.core.UpdateChecker;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Actualizacion automatica del servidor.
 *
 * <p>El servidor no descarga nada: se apaga, y quien lo levanta de nuevo vuelve a
 * sincronizar los mods al arrancar. Con itzg eso ya pasa en cada arranque, asi que
 * duplicar el mecanismo aca solo agregaria formas nuevas de fallar.
 *
 * <p>Sin jugadores el reinicio es inmediato. Con jugadores hay cuenta regresiva y
 * votacion, porque apagarle el mundo a alguien que esta jugando es la clase de
 * cosa que hace que la gente desinstale el mod.
 */
public final class ServerUpdateManager {

    private static final int TICKS_PER_SECOND = 20;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60L;

    private enum Phase {
        IDLE,
        COUNTDOWN
    }

    private static final AtomicReference<ServerUpdateManager> INSTANCE = new AtomicReference<>();

    private final MinecraftServer server;
    private final AtomicReference<UpdateChecker.Result> checkResult = new AtomicReference<>();

    private long ticks;
    private long nextCheckTick;
    private long restartAtTick;
    private long nextAnnounceTick;
    private int postponesUsed;
    private Phase phase = Phase.IDLE;
    private VoteSession vote;
    private boolean checking;

    private ServerUpdateManager(MinecraftServer server) {
        this.server = server;
    }

    public static void start(MinecraftServer server) {
        ServerUpdateManager manager = new ServerUpdateManager(server);
        INSTANCE.set(manager);
        manager.nextCheckTick = TICKS_PER_MINUTE; // un minuto de gracia tras el arranque
        PackWarden.LOG.info(
                "Vigilancia del pack {}",
                WardenConfig.SERVER.enabled.get() ? "activada" : "desactivada por configuracion");
    }

    public static void stop() {
        INSTANCE.set(null);
    }

    public static ServerUpdateManager get() {
        return INSTANCE.get();
    }

    public void tick() {
        ticks++;

        if (phase == Phase.COUNTDOWN) {
            tickCountdown();
            return;
        }
        if (!WardenConfig.SERVER.enabled.get()) {
            return;
        }
        if (ticks >= nextCheckTick) {
            nextCheckTick = ticks + WardenConfig.SERVER.checkIntervalMinutes.get() * TICKS_PER_MINUTE;
            runCheck(this::actOnResult);
        }
    }

    /**
     * Consulta el estado y lo informa. **No actua sobre el resultado.**
     *
     * <p>Consultar y actuar estaban en el mismo camino, y el efecto era que un
     * simple {@code /packwarden check} apagaba el servidor. Un comando de solo
     * lectura no puede tener ese poder, ni siquiera con la vigilancia activada.
     */
    public void checkNow(Consumer<Component> feedback) {
        runCheck(result -> {
            if (feedback != null) {
                feedback.accept(statusMessage(result));
            }
        });
    }

    /** Consulta el pack fuera del hilo principal y entrega el resultado en el. */
    private void runCheck(Consumer<UpdateChecker.Result> then) {
        if (checking) {
            return;
        }
        checking = true;

        Thread thread = new Thread(
                () -> {
                    try {
                        UpdateChecker.Result result = UpdateChecker.check(FMLPaths.GAMEDIR.get());
                        checkResult.set(result);
                        server.execute(() -> then.accept(result));
                    } catch (Exception e) {
                        PackWarden.LOG.error("Fallo el chequeo del pack", e);
                    } finally {
                        checking = false;
                    }
                },
                "packwarden-server-check");
        thread.setDaemon(true);
        thread.start();
    }

    public UpdateChecker.Result lastResult() {
        return checkResult.get();
    }

    /** Unico camino que puede terminar apagando el servidor por su cuenta. */
    private void actOnResult(UpdateChecker.Result result) {
        if (!WardenConfig.SERVER.enabled.get()) {
            return;
        }
        if (!result.updateAvailable() || phase == Phase.COUNTDOWN) {
            return;
        }

        announce(Component.translatable(
                        "packwarden.server.update_found", WardenConfig.COMMON.brandName.get())
                .withStyle(ChatFormatting.YELLOW));

        if (server.getPlayerList().getPlayerCount() == 0) {
            announce(Component.translatable("packwarden.server.restarting_now"));
            restartForUpdate();
            return;
        }

        beginCountdown(WardenConfig.SERVER.countdownMinutes.get());
    }

    /** Fuerza el ciclo, sea por comando o por decision de una votacion. */
    public void forceUpdate() {
        if (server.getPlayerList().getPlayerCount() == 0) {
            restartForUpdate();
        } else {
            beginCountdown(1);
        }
    }

    private void beginCountdown(int minutes) {
        phase = Phase.COUNTDOWN;
        restartAtTick = ticks + minutes * TICKS_PER_MINUTE;
        nextAnnounceTick = ticks;
        openVote();
    }

    private void tickCountdown() {
        if (vote != null && vote.isClosed(ticks)) {
            resolveVote();
        }
        if (ticks >= nextAnnounceTick) {
            announceCountdown();
        }
        if (ticks >= restartAtTick) {
            restartForUpdate();
        }
    }

    private void announceCountdown() {
        long remaining = Math.max(0, (restartAtTick - ticks) / TICKS_PER_SECOND);
        announce(Component.translatable("packwarden.server.countdown", formatDuration(remaining))
                .withStyle(ChatFormatting.YELLOW));

        // Los avisos se espacian segun lo que falte: seguido cerca del final, sin
        // molestar cuando todavia hay tiempo de sobra.
        long spacingSeconds = remaining > 300 ? 120 : remaining > 60 ? 30 : 10;
        nextAnnounceTick = ticks + spacingSeconds * TICKS_PER_SECOND;
    }

    /** Abre una votacion y la ofrece como botones, no como comandos para tipear. */
    public void openVote() {
        if (!WardenConfig.SERVER.votingEnabled.get() || phase != Phase.COUNTDOWN) {
            return;
        }

        int maxPostpones = WardenConfig.SERVER.maxPostpones.get();
        boolean postponeAllowed = maxPostpones == 0 || postponesUsed < maxPostpones;

        vote = new VoteSession(
                ticks + WardenConfig.SERVER.voteWindowSeconds.get() * TICKS_PER_SECOND, postponeAllowed);

        announce(Component.translatable("packwarden.server.vote_open").withStyle(ChatFormatting.AQUA));

        MutableComponent buttons = Component.empty();
        if (postponeAllowed) {
            buttons.append(button(
                    Component.translatable(
                            "packwarden.server.vote_postpone",
                            formatDuration(WardenConfig.SERVER.postponeMinutes.get() * 60L)),
                    "/packwarden vote postpone",
                    ChatFormatting.GREEN));
            buttons.append(Component.literal("  "));
        } else {
            announce(Component.translatable("packwarden.server.vote_limit").withStyle(ChatFormatting.GRAY));
        }
        buttons.append(button(
                Component.translatable("packwarden.server.vote_now"), "/packwarden vote now", ChatFormatting.RED));

        announce(buttons);
    }

    private static MutableComponent button(Component label, String command, ChatFormatting color) {
        return label.copy()
                .withStyle(Style.EMPTY
                        .withColor(color)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    public void castVote(ServerPlayer player, VoteSession.Choice choice) {
        if (vote == null || vote.isClosed(ticks)) {
            player.sendSystemMessage(Component.translatable("packwarden.server.vote_rejected"));
            return;
        }
        if (choice == VoteSession.Choice.POSTPONE && !vote.isPostponeAllowed()) {
            player.sendSystemMessage(Component.translatable("packwarden.server.vote_limit"));
            return;
        }
        vote.cast(player, choice);
        player.sendSystemMessage(Component.translatable("packwarden.server.vote_counted"));
    }

    private void resolveVote() {
        VoteSession.Outcome outcome = vote.resolve();
        vote = null;

        switch (outcome) {
            case POSTPONE -> {
                postponesUsed++;
                int minutes = WardenConfig.SERVER.postponeMinutes.get();
                restartAtTick += minutes * TICKS_PER_MINUTE;
                nextAnnounceTick = ticks;
                announce(Component.translatable(
                                "packwarden.server.vote_postponed", formatDuration(minutes * 60L))
                        .withStyle(ChatFormatting.GREEN));
            }
            case NOW -> restartForUpdate();
            case NO_QUORUM -> announce(Component.translatable("packwarden.server.vote_rejected")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void restartForUpdate() {
        PackWarden.LOG.info("Apagando para que el pack se sincronice en el proximo arranque.");
        phase = Phase.IDLE;

        Component reason = Component.translatable("packwarden.server.kick");
        for (ServerPlayer player : server.getPlayerList().getPlayers().toArray(new ServerPlayer[0])) {
            player.connection.disconnect(reason);
        }

        server.saveEverything(true, true, true);
        server.halt(false);
    }

    private void announce(Component message) {
        if (WardenConfig.SERVER.announceToEveryone.get()) {
            server.getPlayerList().broadcastSystemMessage(message, false);
        } else {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (server.getPlayerList().isOp(player.getGameProfile())) {
                    player.sendSystemMessage(message);
                }
            }
        }
        PackWarden.LOG.info(message.getString());
    }

    public Component statusMessage(UpdateChecker.Result result) {
        if (result == null) {
            return Component.translatable("packwarden.command.checking");
        }
        return switch (result.state()) {
            case UP_TO_DATE -> Component.translatable("packwarden.status.up_to_date");
            case OFFLINE -> Component.translatable("packwarden.status.offline");
            case DISABLED -> Component.translatable("packwarden.status.disabled");
            case NOT_MANAGED -> Component.translatable("packwarden.status.not_managed");
            case UPDATE_AVAILABLE -> Component.translatable("packwarden.status.available");
        };
    }

    private static String formatDuration(long seconds) {
        if (seconds >= 60) {
            long minutes = seconds / 60;
            return minutes + " min";
        }
        return seconds + " s";
    }
}
