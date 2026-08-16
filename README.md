# MineTUKI

Modpack for a private Minecraft **1.21.1** server running **NeoForge 21.1.248**, managed with [packwiz](https://packwiz.infra.link/).

This repo is the single source of truth for the modlist. It is *not* the server itself — no world save, no server jar, no logs live here. It's just the list of mods + their configs, versioned so changes are reviewable and reproducible.

## How the client/server split works

Every mod tracked here has a `side` in its `.toml` file: `client`, `server`, or `both`. One pack, one repo — no separate branches or releases needed for this. The installer picks what to download based on which side you ask for:

- **Server** (this machine) runs the installer with `-s server`, pulling only `server` + `both` mods.
- **Clients** (you and friends) run the installer with `-s client` (the default), pulling only `client` + `both` mods.

Both point at the exact same `pack.toml` URL — the raw file on this GitHub repo's `main` branch.

## For friends: installing the modpack

No extra launcher, no typing commands - just the regular Minecraft Launcher plus a double-click installer.

1. Install [NeoForge 21.1.248](https://neoforged.net/) for Minecraft 1.21.1 (this adds a profile to your normal Minecraft Launcher, same as any other version).
2. Download the **[client installer](https://github.com/Neo236/MineTUKI/releases/tag/client-installer-v1)** and drop its 4 files straight into your `.minecraft` folder (same place as `options.txt` - `%appdata%\.minecraft` on Windows, `~/Library/Application Support/minecraft` on Mac, `~/.minecraft` on Linux).
3. Double-click `instalar-mods-windows.bat` (Windows) or `instalar-mods-mac-linux.command` (Mac/Linux). A window shows mod download progress, then closes.
4. Launch Minecraft normally with the NeoForge 1.21.1 profile.

Whenever the modlist changes, just double-click the installer again - it diffs against the current `pack.toml` and adds/removes what's needed. Full instructions are in the `LEEME.txt` inside the zip.

Under the hood this is still `packwiz-installer-bootstrap.jar` pointed at this repo's `pack.toml` (defaults to `client`+`both` side) - the .bat/.command files just wrap that one command so nobody has to type it.

## For the server (this machine)

Runs in Docker via [`itzg/minecraft-server`](https://github.com/itzg/docker-minecraft-server), which has built-in packwiz support: set `PACKWIZ_URL` to this repo's `pack.toml` and it installs only `side = "server"` / `side = "both"` mods automatically — same source, same mechanism as the client install above, just filtered the other way. No separate server branch, fork, or release needed.

```
docker compose up -d        # first start (pulls image, installs NeoForge + mods, generates world)
docker compose logs -f mc   # watch it boot
docker compose restart mc   # re-syncs mods from the latest pushed pack.toml, then relaunches
docker compose down         # stop (world/config/mods persist on disk, untouched)
```

Runtime data (world save, downloaded mod jars, configs, logs) lives entirely **outside this repo**, bind-mounted from `~/deployments/minecraft/mainMinecraftServer/` — that folder is this server's `.minecraft` equivalent. Nothing under it is committed to git.

Copy `.env.example` to `.env` (gitignored) to override memory/MOTD/difficulty/RCON without touching the committed compose file.

## Updating mods

```
packwiz modrinth add <slug>       # add a Modrinth mod
packwiz curseforge add <slug>     # add a CurseForge mod
packwiz update --all              # bump everything to latest compatible version
packwiz refresh                   # rehash after manual edits
```

Commit `pack.toml`, `index.toml`, and the `mods/*.toml` files, then push. Run `docker compose restart mc` on this machine to pick up the change server-side; friends re-run the installer client-side whenever they want to update.
