# MineTUKI

Modpack para un servidor privado de Minecraft **1.21.1** corriendo **NeoForge 21.1.248**, gestionado con [packwiz](https://packwiz.infra.link/).

Este repo es la fuente única de verdad del listado de mods. *No* es el servidor en sí — acá no vive el mundo, ni el jar del servidor, ni los logs. Es solo la lista de mods + sus configs, versionada para que los cambios sean revisables y reproducibles.

## Cómo funciona la separación cliente/servidor

Cada mod tiene un `side` en su archivo `.toml`: `client`, `server`, o `both`. Un solo pack, un solo repo — no hace falta rama ni release separado para esto. El instalador elige qué descargar según el lado que le pidas:

- **Servidor** (esta máquina) corre el instalador con `-s server`, bajando solo mods `server` + `both`.
- **Clientes** (vos y tus amigos) corren el instalador con `-s client` (el default), bajando solo mods `client` + `both`.

Ambos apuntan a la misma URL de `pack.toml` — el archivo crudo de este repo en la rama `main`.

## Para amigos: instalar el modpack

Descargá el **[instalador de cliente](https://github.com/Neo236/MineTUKI/releases/tag/client-installer-v1)**, extraelo en cualquier lado, y doble clic al script de tu sistema (`instalar-mods-windows.bat` / `instalar-mods-mac-linux.command`). Descarga los mods de cliente a una carpeta `modsTUKI` al lado del instalador — si volvés a correrlo más adelante, actualiza esa misma carpeta (agrega lo nuevo, saca lo que ya no está). Qué hacés con esos mods después (copiarlos a tu perfil de NeoForge, etc.) es cosa tuya.

Por dentro es simplemente `packwiz-installer-bootstrap.jar --pack-folder modsTUKI` apuntando al `pack.toml` de este repo (default `client`+`both`, verificado que nunca baja mods `server`-only).

## Para el servidor (esta máquina)

Corre en Docker vía [`itzg/minecraft-server`](https://github.com/itzg/docker-minecraft-server), que tiene soporte nativo para packwiz: seteás `PACKWIZ_URL` al `pack.toml` de este repo y instala automáticamente solo los mods `side = "server"` / `side = "both"` — misma fuente, mismo mecanismo que el instalador de cliente, solo que filtrado al revés. No hace falta rama, fork ni release separado para el servidor.

```
docker compose up -d        # primer arranque (baja la imagen, instala NeoForge + mods, genera el mundo)
docker compose logs -f mc   # ver el arranque en vivo
docker compose restart mc   # resincroniza mods desde el pack.toml pusheado más reciente, y relanza
docker compose down         # detener (mundo/config/mods quedan en disco, intactos)
```

Los datos en tiempo de ejecución (mundo, jars de mods descargados, configs, logs) viven completamente **fuera de este repo**, montados desde `~/deployments/minecraft/mainMinecraftServer/` — esa carpeta es el equivalente al `.minecraft` de este servidor. Nada de ahí se commitea a git.

Copiá `.env.example` a `.env` (ignorado por git) para sobreescribir memoria/MOTD/dificultad/RCON sin tocar el compose commiteado.

### Gestionar el acceso

- **Online-mode** (`true`) y **whitelist** (`true`) están activos — solo entran jugadores con cuenta legítima de Microsoft/Mojang *y* que estén en la whitelist.
- Agregar jugador: `docker exec -u 1000 minetuki mc-send-to-console whitelist add <username>`
- Sacar jugador: `docker exec -u 1000 minetuki mc-send-to-console whitelist remove <username>`
- Ver whitelist actual: `docker exec -u 1000 minetuki mc-send-to-console whitelist list`
- O editar `whitelist.json` directamente en la carpeta de datos y correr `whitelist reload` con el mismo comando.

## Actualizar mods

```
packwiz modrinth add <slug>       # agregar un mod de Modrinth
packwiz curseforge add <slug>     # agregar un mod de CurseForge
packwiz update --all              # actualizar todo a la última versión compatible
packwiz refresh                   # rehashear después de editar a mano
```

Commiteá `pack.toml`, `index.toml`, y los `mods/*.toml`, después pusheá. Corré `docker compose restart mc` en esta máquina para aplicar el cambio del lado servidor; los amigos vuelven a correr el instalador del lado cliente cuando quieran actualizar.
