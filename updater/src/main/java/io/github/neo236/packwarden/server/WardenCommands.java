package io.github.neo236.packwarden.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.neo236.packwarden.PackWarden;
import io.github.neo236.packwarden.config.WardenConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos del mod.
 *
 * <p>El alias sale de la configuracion: el nombre real es siempre
 * {@code /packwarden}, y cada servidor le pone el suyo.
 */
public final class WardenCommands {

    /** Nivel de operador necesario para forzar un reinicio. */
    private static final int OP_LEVEL = 2;

    private WardenCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build(PackWarden.MOD_ID));

        String alias = WardenConfig.COMMON.commandAlias.get();
        if (alias != null && alias.matches("[a-z0-9_]{1,32}") && !alias.equals(PackWarden.MOD_ID)) {
            dispatcher.register(build(alias));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("check").executes(context -> check(context.getSource())))
                .then(Commands.literal("update")
                        .requires(source -> source.hasPermission(OP_LEVEL))
                        .executes(context -> update(context.getSource())))
                .then(Commands.literal("vote")
                        .then(Commands.literal("postpone")
                                .executes(context -> vote(context.getSource(), VoteSession.Choice.POSTPONE)))
                        .then(Commands.literal("now")
                                .executes(context -> vote(context.getSource(), VoteSession.Choice.NOW))));
    }

    private static int status(CommandSourceStack source) {
        ServerUpdateManager manager = ServerUpdateManager.get();
        if (manager == null) {
            return 0;
        }
        source.sendSuccess(() -> manager.statusMessage(manager.lastResult()), false);
        return 1;
    }

    private static int check(CommandSourceStack source) {
        ServerUpdateManager manager = ServerUpdateManager.get();
        if (manager == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("packwarden.command.checking"), false);
        manager.checkNow(message -> source.sendSuccess(() -> message, false));
        return 1;
    }

    private static int update(CommandSourceStack source) {
        ServerUpdateManager manager = ServerUpdateManager.get();
        if (manager == null) {
            return 0;
        }
        manager.forceUpdate();
        return 1;
    }

    private static int vote(CommandSourceStack source, VoteSession.Choice choice) {
        ServerUpdateManager manager = ServerUpdateManager.get();
        if (manager == null || !(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        manager.castVote(player, choice);
        return 1;
    }
}
