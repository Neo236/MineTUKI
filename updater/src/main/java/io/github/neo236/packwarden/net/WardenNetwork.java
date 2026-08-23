package io.github.neo236.packwarden.net;

import io.github.neo236.packwarden.PackWarden;
import io.github.neo236.packwarden.config.WardenConfig;
import io.github.neo236.packwarden.core.LocalPack;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registro y envio del handshake de version del pack.
 *
 * <p>El canal se declara <b>opcional</b> a proposito. Si fuera obligatorio, un
 * cliente sin el mod no podria entrar al servidor, que es exactamente el problema
 * que este mod deberia ayudar a resolver, no a empeorar.
 */
public final class WardenNetwork {

    /**
     * Puente hacia el manejador del cliente.
     *
     * <p>El registro vive en codigo comun, pero la respuesta es puramente de
     * cliente. En vez de referenciar una clase de cliente desde aca —lo que la
     * cargaria en un servidor dedicado— el cliente instala su manejador al
     * inicializarse. En el servidor el puente queda vacio y no pasa nada.
     */
    private static volatile BiConsumer<PackStatePayload, IPayloadContext> clientHandler = (payload, context) -> {};

    private WardenNetwork() {}

    public static void setClientHandler(BiConsumer<PackStatePayload, IPayloadContext> handler) {
        clientHandler = handler;
    }

    public static void register(PayloadRegistrar registrar) {
        registrar
                .optional()
                .playToClient(PackStatePayload.TYPE, PackStatePayload.CODEC, (payload, context) -> {
                    if (payload.protocol() != PackWarden.PROTOCOL_VERSION) {
                        PackWarden.LOG.debug(
                                "Mensaje de protocolo v{} descartado; este mod habla v{}",
                                payload.protocol(),
                                PackWarden.PROTOCOL_VERSION);
                        return;
                    }
                    clientHandler.accept(payload, context);
                });
    }

    /**
     * Le manda al jugador el estado del pack del servidor, si su cliente entiende
     * el canal. Un cliente sin el mod simplemente no lo recibe.
     */
    public static void sendStateTo(ServerPlayer player) {
        String indexHash = LocalPack.installedIndexHash();
        if (indexHash == null) {
            // Sin manifiesto el servidor no sabe que version tiene, y afirmarlo
            // seria peor que callarse: el cliente marcaria desactualizados a todos.
            return;
        }

        PackStatePayload payload = new PackStatePayload(
                PackWarden.PROTOCOL_VERSION,
                indexHash,
                "",
                WardenConfig.COMMON.brandName.get());

        // Se pregunta antes de mandar: el canal es opcional, asi que del otro lado
        // puede no existir, y eso es un caso normal y no un error.
        if (!player.connection.hasChannel(PackStatePayload.TYPE)) {
            PackWarden.LOG.debug("{} no tiene el mod; no se le manda el estado del pack.", player.getGameProfile().getName());
            return;
        }

        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (Exception e) {
            PackWarden.LOG.debug("No se pudo mandar el estado del pack: {}", e.toString());
        }
    }
}
