package io.github.neo236.packwarden.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Toda la configuracion del mod. Nada de esto esta incrustado en el codigo, para
 * que el mismo jar sirva en cualquier servidor sin recompilar.
 */
public final class WardenConfig {

    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    static {
        Pair<Common, ModConfigSpec> common = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();

        Pair<Client, ModConfigSpec> client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();

        Pair<Server, ModConfigSpec> server = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
    }

    private WardenConfig() {}

    public static final class Common {

        public final ModConfigSpec.ConfigValue<String> packUrl;
        public final ModConfigSpec.ConfigValue<String> fallbackPackUrl;
        public final ModConfigSpec.ConfigValue<String> brandName;
        public final ModConfigSpec.ConfigValue<String> commandAlias;
        public final ModConfigSpec.IntValue httpTimeoutSeconds;

        Common(ModConfigSpec.Builder b) {
            b.comment("Ajustes compartidos por cliente y servidor.").push("general");

            packUrl = b
                    .comment(
                            "URL del pack.toml. Es la unica fuente de verdad de que mods van.",
                            "Vacio desactiva el mod por completo.")
                    .define("pack_url", "");

            fallbackPackUrl = b
                    .comment(
                            "URL alternativa, por si la principal no responde.",
                            "Sirve para tener un espejo: por ejemplo el repositorio crudo de GitHub",
                            "cuando la principal es un CDN. Vacio = sin alternativa.")
                    .define("fallback_pack_url", "");

            brandName = b
                    .comment("Nombre que ve el jugador en los mensajes y pantallas del mod.")
                    .define("brand_name", "PackWarden");

            commandAlias = b
                    .comment(
                            "Alias corto del comando /packwarden. Vacio = sin alias.",
                            "Solo letras minusculas, numeros y guion bajo.")
                    .define("command_alias", "");

            httpTimeoutSeconds = b
                    .comment("Timeout de las consultas HTTP, en segundos.")
                    .defineInRange("http_timeout_seconds", 10, 1, 120);

            b.pop();
        }
    }

    /** Punto de referencia para ubicar el boton del menu principal. */
    public enum ButtonAnchor {
        REALMS,
        MULTIPLAYER,
        BOTTOM_LEFT
    }

    public static final class Client {

        public final ModConfigSpec.BooleanValue checkOnStartup;
        public final ModConfigSpec.BooleanValue promptOnStartup;
        public final ModConfigSpec.EnumValue<ButtonAnchor> buttonAnchor;

        Client(ModConfigSpec.Builder b) {
            b.comment("Ajustes que solo afectan a tu juego.").push("client");

            buttonAnchor = b
                    .comment(
                            "Donde ubicar el boton en el menu principal.",
                            "El costado del menu es zona disputada: varios mods ponen ahi su",
                            "boton. Si el elegido esta ocupado se busca el lugar libre mas",
                            "cercano, y si no queda ninguno se cae a la esquina inferior.",
                            "REALMS = a la izquierda de Minecraft Realms",
                            "MULTIPLAYER = a la izquierda de Multijugador",
                            "BOTTOM_LEFT = esquina inferior izquierda, sobre el texto de version")
                    .defineEnum("button_anchor", ButtonAnchor.REALMS);

            checkOnStartup = b
                    .comment("Consultar si hay actualizaciones al abrir el juego.")
                    .define("check_on_startup", true);

            promptOnStartup = b
                    .comment(
                            "Mostrar la pantalla de actualizacion al llegar al menu principal.",
                            "Con false no aparece nada solo: queda el boton para consultar a mano.")
                    .define("prompt_on_startup", true);

            b.pop();
        }
    }

    public static final class Server {

        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.IntValue checkIntervalMinutes;
        public final ModConfigSpec.BooleanValue announceToEveryone;
        public final ModConfigSpec.IntValue countdownMinutes;
        public final ModConfigSpec.BooleanValue votingEnabled;
        public final ModConfigSpec.IntValue voteWindowSeconds;
        public final ModConfigSpec.IntValue postponeMinutes;
        public final ModConfigSpec.IntValue maxPostpones;

        Server(ModConfigSpec.Builder b) {
            b.comment(
                            "Actualizacion automatica del servidor.",
                            "",
                            "El servidor no descarga nada por su cuenta: se apaga, y quien lo",
                            "levanta de nuevo (Docker, systemd, un script) vuelve a sincronizar",
                            "los mods al arrancar. Requiere que el servidor se reinicie solo;",
                            "si no, dejar 'enabled' en false.")
                    .push("auto_update");

            enabled = b
                    .comment("Activa el chequeo periodico y el reinicio automatico.")
                    .define("enabled", false);

            checkIntervalMinutes = b
                    .comment("Cada cuanto se consulta si el pack cambio.")
                    .defineInRange("check_interval_minutes", 15, 1, 1440);

            announceToEveryone = b
                    .comment("false = solo se avisa a los operadores.")
                    .define("announce_to_everyone", true);

            countdownMinutes = b
                    .comment(
                            "Aviso previo cuando hay jugadores conectados.",
                            "Sin nadie conectado el reinicio es inmediato.")
                    .defineInRange("countdown_minutes", 10, 1, 180);

            votingEnabled = b
                    .comment("Permite a los jugadores postergar o adelantar el reinicio.")
                    .define("voting_enabled", true);

            voteWindowSeconds = b
                    .comment("Cuanto dura una votacion antes de contar los votos.")
                    .defineInRange("vote_window_seconds", 60, 15, 600);

            postponeMinutes = b
                    .comment("Cuanto suma cada postergacion aprobada.")
                    .defineInRange("postpone_minutes", 15, 1, 180);

            maxPostpones = b
                    .comment("Tope de postergaciones seguidas. 0 = sin tope.")
                    .defineInRange("max_postpones", 3, 0, 100);

            b.pop();
        }
    }
}
