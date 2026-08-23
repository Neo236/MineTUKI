package io.github.neo236.packwarden;

import io.github.neo236.packwarden.config.WardenConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada comun a los dos lados.
 *
 * <p>Este mod no sabe nada de ningun modpack en particular: la URL del pack, el
 * nombre que se muestra y el alias del comando salen de la configuracion. Eso es
 * deliberado, para poder reusarlo en cualquier servidor.
 */
@Mod(PackWarden.MOD_ID)
public class PackWarden {

    public static final String MOD_ID = "packwarden";

    public static final Logger LOG = LoggerFactory.getLogger("PackWarden");

    /**
     * Version del protocolo de red, independiente de la version del mod.
     *
     * <p>Cliente y servidor se actualizan por separado y quedan desfasados un
     * ciclo: quien aplica una actualizacion es la copia vieja del mod, y la nueva
     * recien manda en el arranque siguiente. Versionar el protocolo aparte permite
     * degradar en vez de romper cuando los dos lados no coinciden.
     */
    public static final int PROTOCOL_VERSION = 1;

    public PackWarden(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, WardenConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.SERVER, WardenConfig.SERVER_SPEC);
        LOG.info("PackWarden iniciando (protocolo v{})", PROTOCOL_VERSION);
    }
}
