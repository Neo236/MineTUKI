# MineTUKI

Modpack for a private Minecraft **1.21.1** server running **NeoForge 21.1.248**, managed with [packwiz](https://packwiz.infra.link/).

This repo is the single source of truth for the modlist. It is *not* the server itself — no world save, no server jar, no logs live here. It's just the list of mods + their configs, versioned so changes are reviewable and reproducible.

## How the client/server split works

Every mod tracked here has a `side` in its `.toml` file: `client`, `server`, or `both`. One pack, one repo — no separate branches or releases needed for this. The installer picks what to download based on which side you ask for:

- **Server** (this machine) runs the installer with `-s server`, pulling only `server` + `both` mods.
- **Clients** (you and friends) run the installer with `-s client` (the default), pulling only `client` + `both` mods.

Both point at the exact same `pack.toml` URL — the raw file on this GitHub repo's `main` branch.

## For friends: installing the modpack

1. Install [NeoForge 21.1.248](https://neoforged.net/) for Minecraft 1.21.1 first (normal client install via the NeoForge installer).
2. Download [`packwiz-installer-bootstrap.jar`](https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest).
3. Run it pointed at this pack:
   ```
   java -jar packwiz-installer-bootstrap.jar https://raw.githubusercontent.com/Neo236/MineTUKI/main/pack.toml
   ```
4. It downloads mods straight into your instance's `mods` folder. Re-run it any time the modlist updates.

(Alternatively: Prism Launcher can import a packwiz pack directly from that same URL.)

## For the server (this machine)

See `server/` (gitignored, lives locally) — it runs the same installer with `-s server` against this repo, then launches NeoForge headless. Setup details below once the pack is populated.

## Updating mods

```
packwiz modrinth add <slug>       # add a Modrinth mod
packwiz curseforge add <slug>     # add a CurseForge mod
packwiz update --all              # bump everything to latest compatible version
packwiz refresh                   # rehash after manual edits
```

Commit `pack.toml`, `index.toml`, and the `mods/*.toml` files. Then push.
