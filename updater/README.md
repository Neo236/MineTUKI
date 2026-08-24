# PackWarden

Mantiene un modpack de [packwiz](https://packwiz.infra.link/) al día sin salir del juego.

Detecta cuando la lista de mods cambia, muestra qué entra y qué sale, y actualiza cuando el jugador lo decide. Del lado del servidor, se reinicia solo para actualizarse.

No sabe nada de ningún modpack en particular: **la URL, el nombre visible y el alias del comando salen de la configuración**, así que el mismo jar sirve en cualquier servidor.

---

## Las dos piezas

El proyecto entrega dos cosas que se construyen juntas y se distribuyen por separado.

### El instalador — se corre una vez

Un programa de escritorio que deja el juego listo: crea un perfil en el launcher oficial con su propia carpeta de mods, descarga el pack, y **configura el mod** para que sepa de dónde actualizarse.

Se distribuye como `MineTUKI-instalador.zip`, con un lanzador por sistema operativo. No requiere tener Java: los lanzadores buscan el que ya trae el launcher de Minecraft.

### El mod — vive dentro del juego

De ahí en más, el instalador no se usa nunca más. El mod consulta el pack publicado y ofrece actualizar.

```
   instalador  ──(una vez)──►  perfil + mods + configuración
                                        │
                                        ▼
                                   PackWarden
                                        │
                   ┌────────────────────┴────────────────────┐
                   ▼                                         ▼
              cliente                                    servidor
     avisa y actualiza a pedido               se reinicia solo para actualizarse
```

---

## Cómo detecta que hay una actualización

packwiz deja en la carpeta de juego un `packwiz.json` con el hash del índice que instaló. El mod lo compara contra el hash que declara el `pack.toml` publicado.

Cuando no hay manifiesto, **lo dice** en vez de asumir. Una versión anterior buscaba un `pack.toml` con ruta relativa al directorio de trabajo del proceso — que en el cliente casi nunca existe — y por eso reportaba "hay actualización" siempre.

Para el detalle de cambios se comparan los hashes por archivo del manifiesto contra el índice remoto. Con un cuidado: packwiz también anota las entradas **del otro lado**, sin hash ni archivo, y las conserva aunque el mod desaparezca del pack. Contarlas hacía aparecer bajas de mods que el jugador nunca tuvo.

## Cómo aplica la actualización

En Windows los jar de mods quedan bloqueados mientras la JVM del juego vive, así que no se pueden reemplazar desde adentro. El mod lanza un proceso aparte —el *companion*— que **espera a que el juego cierre** y recién ahí actualiza.

El companion viaja dentro del jar del mod junto con el bootstrap de packwiz, así que no hay descargas en tiempo de uso. Espera por el PID del juego, no por una cantidad fija de segundos: con muchos mods el cierre puede tardar más que cualquier espera a ciegas.

## El lado servidor

El servidor **no descarga nada**: se apaga, y quien lo levanta vuelve a sincronizar al arrancar. Con `itzg/minecraft-server` eso ya ocurre en cada arranque, así que duplicar el mecanismo solo agregaría formas de fallar.

- Sin jugadores conectados, el reinicio es inmediato.
- Con jugadores, hay cuenta regresiva y una votación con botones en el chat para posponer o adelantar.

Viene **desactivado por defecto**: un mod que puede apagar un servidor no debería hacerlo sin que se lo pidan.

---

## Configuración

`config/packwarden-common.toml` — los dos lados

| | |
|---|---|
| `pack_url` | URL del `pack.toml`. Vacío desactiva el mod |
| `fallback_pack_url` | Espejo, por si la principal no responde |
| `brand_name` | Nombre que ve el jugador |
| `command_alias` | Alias corto de `/packwarden` |

`config/packwarden-client.toml`

| | |
|---|---|
| `check_on_startup` | Consultar al abrir el juego |
| `prompt_on_startup` | Mostrar la pantalla, o dejar solo el botón |
| `button_anchor` | `REALMS`, `MULTIPLAYER` o `BOTTOM_LEFT` |

`config/packwarden-server.toml`

| | |
|---|---|
| `enabled` | Activa el reinicio automático |
| `check_interval_minutes` | Cada cuánto consulta |
| `countdown_minutes` | Aviso previo con jugadores conectados |
| `voting_enabled`, `vote_window_seconds`, `postpone_minutes`, `max_postpones` | Política de votación |

## Comandos

```
/packwarden status          estado actual
/packwarden check           vuelve a consultar (no aplica nada)
/packwarden update          fuerza el ciclo (operadores)
/packwarden vote postpone   posponer
/packwarden vote now        actualizar ya
```

El alias configurado funciona igual: en MineTUKI es `/tuki`.

---

## Compilar

```
./gradlew build          el jar del mod, con el companion adentro
./gradlew installerZip   el paquete del instalador
./gradlew test           lógica del mod
./gradlew companionTest  lógica del instalador
```

## Publicar

Empujar una etiqueta `packwarden-vX.Y.Z` que coincida con `mod_version` en `gradle.properties`. El resto es automático: compila, publica el release, reapunta el metadato del pack con el hash del artefacto recién compilado, y commitea el índice refrescado.

## Estructura

```
src/main/java/…/packwarden/
  core/        detección y comparación. Sin dependencias de Minecraft más
               allá de las mínimas, para poder probarse sin levantar el juego
  client/      pantalla, botón y lanzamiento del companion
  server/      ciclo de vigilancia, votación y comandos
  net/         handshake de versión, en un canal opcional
  config/      toda la configuración

src/companion/     el instalador y el actualizador, sin Minecraft en el classpath
src/installer/     lanzadores por sistema operativo, LEEME e icono
src/test/          tests del mod
src/companionTest/ tests del instalador
```

---

Licencia MIT. Incluye packwiz-installer-bootstrap (MIT) y Gson (Apache 2.0); ver [AVISOS-DE-TERCEROS.txt](src/main/resources/AVISOS-DE-TERCEROS.txt).
