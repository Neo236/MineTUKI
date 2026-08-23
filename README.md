# MineTUKI

Modpack para un servidor privado de Minecraft **1.21.1** corriendo **NeoForge 21.1.248**, gestionado con [packwiz](https://packwiz.infra.link/).

Este repo es la fuente única de verdad del listado de mods. *No* es el servidor en sí — acá no vive el mundo, ni el jar del servidor, ni los logs. Es solo la lista de mods + sus configs, versionada para que los cambios sean revisables y reproducibles.

## Cómo funciona la separación cliente/servidor

Cada mod tiene un `side` en su archivo `.toml`: `client`, `server`, o `both`. Un solo pack, un solo repo — no hace falta rama ni release separado para esto. El instalador elige qué descargar según el lado que le pidas:

- **Servidor** (esta máquina) corre el instalador con `-s server`, bajando solo mods `server` + `both`.
- **Clientes** (vos y tus amigos) corren el instalador con `-s client` (el default), bajando solo mods `client` + `both`.

Ambos apuntan a la misma URL de `pack.toml` — el archivo crudo de este repo en la rama `main`.

## Para amigos: instalar el modpack

Descargá el **[instalador](https://github.com/Neo236/MineTUKI/releases/latest)**, extraelo y abrilo:

| Archivo | Para quién |
|---|---|
| `MineTUKI-instalador-windows.zip` | Windows. Trae `MineTUKI.exe` con su propio Java: doble clic y listo, no necesitás nada instalado. ~55 MB |
| `MineTUKI-instalador.zip` | Mac, Linux, o Windows si preferís algo liviano. Doble clic al script de tu sistema. ~0,4 MB |

Crea un perfil aparte en el launcher oficial, con su **propia carpeta de mods**. No toca los mods que ya tengas: tus otros perfiles y modpacks quedan intactos. El nombre del perfil y la carpeta se pueden cambiar antes de instalar, y también podés instalar solo a una carpeta suelta si usás Prism, MultiMC u otra instalación.

**No hace falta instalar Java**: usa el que ya viene con el launcher de Minecraft.

Cerrá el launcher antes de instalar — al cerrarse reescribe su configuración y podría borrar el perfil recién creado.

Esto se corre **una sola vez**. De ahí en más el mod [PackWarden](updater/) avisa solo dentro del juego cuando el modpack cambia, muestra qué mods entran y salen, y te deja actualizar en el momento, al salir del juego, o más tarde.

### Actualizar el modpack

No hace falta bajar nada de nuevo. Al abrir el juego, si hay cambios aparece una pantalla con el detalle. El botón del zorro en el menú principal sirve para consultar cuando quieras.

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

Commiteá `pack.toml`, `index.toml`, y los `mods/*.toml`, después pusheá. Corré `docker compose restart mc` en esta máquina para aplicar el cambio del lado servidor; los clientes se enteran solos la próxima vez que abran el juego.

**Activá el hook una vez por clon**, para que el índice no se publique nunca desincronizado:

```
git config core.hooksPath .githooks
```

Publicar un `pack.toml` cuyo hash no coincide con `index.toml` rompe la instalación de todos los clientes y, como itzg hace `exit 1` cuando packwiz falla, deja el servidor en bucle de reinicio. El hook lo resincroniza al commitear y el CI lo verifica antes de que llegue a `main`.

## De dónde sale el pack

| | URL | por qué |
|---|---|---|
| Clientes | `minetuki-neo236s-projects.vercel.app/pack.toml` | se actualiza en el momento del push |
| Servidor | `raw.githubusercontent.com/.../main/pack.toml` | una dependencia menos en el arranque |

GitHub raw tarda hasta ~5 minutos en propagar, y **por nodo de CDN**: durante esa ventana dos jugadores pueden recibir versiones distintas. Al servidor no le afecta porque solo sincroniza al reiniciar. El mod lleva la URL de GitHub como respaldo configurable.

## El código del mod

Vive en [`updater/`](updater/), excluido del pack vía `.packwizignore`. Ver [docs/PLAN.md](docs/PLAN.md) para las decisiones de diseño y los hechos verificados.
