package io.github.neo236.packwarden.net;

import io.github.neo236.packwarden.PackWarden;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Lo que el servidor le cuenta al cliente sobre el pack al conectarse.
 *
 * <p>Lleva la version del protocolo aparte de la del mod: los dos lados se
 * actualizan por separado y van a estar desfasados seguido, asi que el receptor
 * tiene que poder reconocer un mensaje de una version distinta y descartarlo sin
 * romper nada.
 */
public record PackStatePayload(int protocol, String indexHash, String packVersion, String brand)
        implements CustomPacketPayload {

    public static final Type<PackStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PackWarden.MOD_ID, "pack_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PackStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            PackStatePayload::protocol,
            ByteBufCodecs.STRING_UTF8,
            PackStatePayload::indexHash,
            ByteBufCodecs.STRING_UTF8,
            PackStatePayload::packVersion,
            ByteBufCodecs.STRING_UTF8,
            PackStatePayload::brand,
            PackStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
