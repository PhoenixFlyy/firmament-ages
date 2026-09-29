# Firmament Ages

A private, SevTech-style progression modpack for a small group of friends. It runs on Minecraft 1.21.1 with NeoForge 21.1.x and Java 21.

The whole server plays as **one team** through **Dawn and ten Ages**: Stone, Bronze, Iron, Arcane, Industrial, Electric, Information, Space, Quantum and Singularity. TerraFirmaCraft carries the world and the metallurgy up to steel. Create is the kinetics and logistics layer. The Arcane Age is the magic Age. Immersive Engineering, Mekanism and Draconic Evolution carry the late game. The finale runs from the Ultimate Singularity through a Stargate to a final boss in The Origin.

Each Age has one goal item, and its quest unlocks the next Age. ProgressiveStages hides everything from a later Age: locked items, recipes, ores (disguised as rock), mobs and dimensions. The quest book opens with **K**.

The design documents are in German and live outside this repo, in `../Modpack-Planung/docs/` (`08-Ages-Design.md`, `10-Kernmodliste-v3.md`, `03-Progression-Gating-Technik.md`). All player-facing text in the pack is English.

## Repository layout

| Path | Contents | git | packwiz |
|---|---|---|---|
| `pack.toml`, `index.toml` | pack metadata and file index (`packwiz refresh`) | yes | yes |
| `mods/*.pw.toml` | one metafile per mod (Modrinth first, CurseForge only for CF-only mods) | yes | yes |
| `config/` | changed configs only; `config/progressivestages/stages/*.toml` holds the stage definitions | yes | yes |
| `defaultconfigs/` | server configs that NeoForge copies into every new world (TFC, IE, Mekanism) | yes | yes |
| `kubejs/` | `startup_scripts/` (items, shared constants), `server_scripts/` (recipes, tags, stage logic), `client_scripts/` (recipe viewer), `data/` (datapack: biome modifiers, TFC item heat) | yes | yes |
| `dev/` | tooling: `gen_stage_locks.py`, `poc-checklist.md`, `config-todo.md`, `manifest.csv`, notes | yes | no |
| `server/` | dedicated server template (`start.bat`, `start.ps1`, `user_jvm_args.txt`, `server.properties.template`) | yes | no |
| `tools/` | local binaries: JDK 21, packwiz, packwiz-installer-bootstrap | no | no |
| `test-server/` | local PoC server | no | no |

## Conventions

- **Stages.** The Age stages are `dawn` and `age_0` … `age_9`. The mob ladder is `mob_0` … `mob_9`. Helper stages are `ftbchunks_mapping`, `tool_<station>` and `finale_won`, plus `disabled`, which is never granted. In ProgressiveStages an entry is locked while *any* stage that lists it is missing, so everything goes into the stage file that unlocks it.
- **Generated lock lists.** TFC has 1113 ore blocks, and ProgressiveStages 3.0.5 only accepts exact block ids in `[[ores.overrides]]`. `dev/gen_stage_locks.py` therefore writes the TFC metal, ore and override lists. It rewrites the regions between `# >>> generated:… >>>` and `# <<< generated:… <<<`. Edit the tables in the script, never the generated lines. Run `python dev/gen_stage_locks.py`; `--check` exits 1 if a file is out of date.
- **Quest rewards.** A goal quest grants only the Age stage. `kubejs/server_scripts/stages/grants.js` derives everything else from it: the mob ladder, the map, the station windows and the chunk quota.
- **Needs in-game proof.** Every line marked `// POC:` or `# POC:` still has to be checked in the game. `dev/poc-checklist.md` has the steps. `dev/config-todo.md` lists the config values whose keys are not verified yet.

## Build and serve (packwiz)

```
packwiz refresh                  # after any change under config/, defaultconfigs/, kubejs/ or mods/
packwiz serve                    # serves the working tree on http://localhost:8080/pack.toml
python dev/gen_stage_locks.py    # after changing the Age tables in the generator
```

The dev client is a Prism instance with `packwiz-installer-bootstrap.jar` in its folder. Set its pre-launch command to `"$INST_JAVA" -jar packwiz-installer-bootstrap.jar http://localhost:8080/pack.toml`. Junction `kubejs/`, `config/` and `defaultconfigs/` from the instance into this repo so that in-game edits land in git. Do not junction `mods/`.

Reload cycle: `/reload` for server scripts, F3+T for client scripts, a restart for startup scripts, `/progressivestages reload` for stage files and a new world for worldgen.

## How friends install

- **Recommended: a Prism instance zip with auto-update.** Send it once. It holds `instance.cfg` with the pre-launch command pointing at the raw `pack.toml` of the `main` branch, plus `mmc-pack.json` (MC 1.21.1 + NeoForge), `packwiz-installer-bootstrap.jar` and optionally `servers.dat`. Every launch then updates mods, configs and scripts.
- **Alternative: `.mrpack` per release** (`packwiz mr export`). It imports into Prism or the Modrinth App, but there are no automatic updates. CurseForge-only mods (the FTB suite, Twilight Forest and others) may get embedded as jars. Send such an `.mrpack` privately and never attach it to a public release.
- **Last resort: a CurseForge zip** (`packwiz cf export -s client`).

## How the server updates

The server folder is built from `server/` (see `server/README.md`). `start.bat` and `start.ps1` run a loop:

1. `packwiz-installer-bootstrap -g -s server <pack.toml>` syncs the server-side files.
2. ServerStarterJar starts NeoForge.
3. The loop restarts after a crash or `stop`.

To update, push to `main`, then type `stop` in the server console. To shut down for good, create a `STOP` file first.

## Release

1. `packwiz refresh`.
2. Bump `version` in `pack.toml` and add a changelog entry.
3. Merge `dev` into `main`, tag `vX.Y.Z` and push.
4. Take a server backup before any world-breaking change (removed blocks, new worldgen).
