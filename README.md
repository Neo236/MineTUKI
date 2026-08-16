# MineTUKI

Modpack for a private Minecraft **1.21.1** server running **NeoForge 21.1.248**, managed with [packwiz](https://packwiz.infra.link/).

This repo is the single source of truth for the modlist. It is *not* the server itself — no world save, no server jar, no logs live here. It's just the list of mods + their configs, versioned so changes are reviewable and reproducible.

## How the client/server split works

Every mod tracked here has a `side` in its `.toml` file: `client`, `server`, or `both`. One pack, one repo — no separate branches or releases needed for this. The installer picks what to download based on which side you ask for:

- **Server** (this machine) runs the installer with `-s server`, pulling only `server` + `both` mods.
- **Clients** (you and friends) run the installer with `-s client` (the default), pulling only `client` + `both` mods.

Both point at the exact same `pack.toml` URL — the raw file on this GitHub repo's `main` branch.

## For friends: installing the modpack

No extra launcher, no typing commands, and it never touches anyone's existing `.minecraft` folder - everything lives self-contained in its own directory.

1. Download the **[client installer](https://github.com/Neo236/MineTUKI/releases/tag/client-installer-v1)** and unzip it into a new folder anywhere (Desktop, wherever) - e.g. `MineTUKI`.
2. Double-click `instalar-mods-windows.bat` (Windows) or `instalar-mods-mac-linux.command` (Mac/Linux). A window shows mod download progress; mods land inside that same `MineTUKI/mods/` folder.
3. Install [NeoForge 21.1.248](https://neoforged.net/) for Minecraft 1.21.1 (adds a profile to the normal Minecraft Launcher). Edit that profile → *More Options* → set **Game Directory** to the `MineTUKI` folder from step 1.
4. Play - the launcher reads mods/config/world from that folder, the real `.minecraft` is untouched.

Whenever the modlist changes, just double-click the installer again (step 2) - it diffs against the current `pack.toml`, adding and **removing** what's needed (verified: a mod dropped from the pack gets its local jar deleted on the next run, not just left behind). Full instructions are in the `LEEME.txt` inside the zip.

Under the hood this is still `packwiz-installer-bootstrap.jar` pointed at this repo's `pack.toml` (defaults to `client`+`both` side) - the .bat/.command files just wrap that one command, run from wherever they're unzipped, so nobody has to type it or touch a shared folder.

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

### Managing access

- **Online-mode** (`true`) and **whitelist** (`true`) are both on — only players with legit Microsoft/Mojang accounts *and* a whitelist entry can join.
- Add a player: `docker exec -u 1000 minetuki mc-send-to-console whitelist add <username>`
- Remove a player: `docker exec -u 1000 minetuki mc-send-to-console whitelist remove <username>`
- List current whitelist: `docker exec -u 1000 minetuki mc-send-to-console whitelist list`
- Or edit `whitelist.json` directly in the deployments data folder and run `whitelist reload` via the same command.

## Updating mods

```
packwiz modrinth add <slug>       # add a Modrinth mod
packwiz curseforge add <slug>     # add a CurseForge mod
packwiz update --all              # bump everything to latest compatible version
packwiz refresh                   # rehash after manual edits
```

Commit `pack.toml`, `index.toml`, and the `mods/*.toml` files, then push. Run `docker compose restart mc` on this machine to pick up the change server-side; friends re-run the installer client-side whenever they want to update.
