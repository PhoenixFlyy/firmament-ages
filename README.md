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
| `dev/` | tooling (generators, `run_server.py`, `poc_analyze.py`, `release.ps1`, `make_friends_zip.ps1`, `verify_install.py`) and notes; `dev/exports/` holds the release builds (git-ignored) | yes | no |
| `server/` | dedicated server template (`start.bat`, `start.ps1`, `user_jvm_args.txt`, `server.properties.template`) | yes | no |
| `mod/firmages-core/` | the pack's own NeoForge mod (source, `SPEC.md`) | yes | no |
| `tools/` | local binaries: JDK 21, packwiz, packwiz-installer-bootstrap, the NeoForge installer, portable Prism | no | no |
| `test-server/` | local PoC server | no | no |

## Conventions

- **Stages.** The Age stages are `dawn` and `age_0` … `age_9`. The mob ladder is `mob_0` … `mob_9`. Helper stages are `ftbchunks_mapping`, `tool_<station>` and `finale_won`, plus `disabled`, which is never granted. In ProgressiveStages an entry is locked while *any* stage that lists it is missing, so everything goes into the stage file that unlocks it.
- **Generated lock lists.** TFC has 1113 ore blocks, and ProgressiveStages 3.0.5 only accepts exact block ids in `[[ores.overrides]]`. `dev/gen_stage_locks.py` therefore writes the TFC metal, ore and override lists. It rewrites the regions between `# >>> generated:… >>>` and `# <<< generated:… <<<`. Edit the tables in the script, never the generated lines. Run `python dev/gen_stage_locks.py`; `--check` exits 1 if a file is out of date.
- **Quest rewards.** A goal quest grants only the Age stage. `kubejs/server_scripts/stages/grants.js` derives everything else from it: the mob ladder, the map, the station windows and the chunk quota.
- **Needs in-game proof.** Every line marked `// POC:` or `# POC:` still has to be checked in the game. `dev/poc-checklist.md` has the steps. `dev/config-todo.md` lists the config values whose keys are not verified yet.

## Current state (2026-10-01)

- **Pack:** 192 mods (184 from Modrinth, 8 CurseForge-only: the FTB suite, The Twilight Forest, Torque Link Create To TFC, AE2 Draconic Fusion Autocrafter) plus the pack's own `firmages-core` jar. Content for all Ages from Dawn to Singularity, the quest book (`dev/gen_quests.py`), the shrine of Caelum and the finale in The Origin are in. `pack.toml` is still at version 0.1.0; the next release sets it.
- **firmages-core 0.6.0:** milestones M1 to M7 of `mod/firmages-core/SPEC.md` (Age state and reloads, machine-recipe gate, miner filter and prospecting, reactor refuelling, the shrine multiblock, then Keystone lending, blessing effects, the energy rite and Jade tooltips, then the consecration of the rings with maintenance mode), The Origin with its own age_9 travel lock and spawn list, and the boss scaffolding.
- **Verified without a client:** the test server reaches `Done` (`dev/boot-report.md`), `dev/poc_analyze.py` runs its checks on a recipe dump, and the debug players of the mod walk the shrine, team and quest paths (`dev/poc-results.md`). The client checklist is `dev/client-test-brief.md`.
- **Design calls** made while Felix was away are logged in `dev/decisions-while-away.md`.

## Build and serve (packwiz)

```
tools\packwiz.exe refresh            # after any change under config/, defaultconfigs/, kubejs/ or mods/
tools\packwiz.exe serve              # serves the working tree on http://localhost:8080/pack.toml (dev\serve.bat)
python dev/gen_stage_locks.py        # after changing dev/age_map.toml or the Age tables in the generator
python dev/gen_quests.py; python dev/validate_quests.py   # after changing quests
```

Text files are LF (`.gitattributes`). packwiz hashes the working copy, GitHub serves the git copy: a file with CRLF in the working tree gets a wrong hash in `index.toml`, and every install from GitHub then fails on it. `dev/release.ps1` checks this.

## Build the mod

```
cd mod/firmages-core
set JAVA_HOME=D:\Minecraft\MinecraftModServer\FirmamentAges\tools\jdk-21
gradlew build                        # JUnit tests and build/libs/firmages-core-<mod_version>.jar
gradlew runGameTestServer            # GameTests on a fresh world
```

The version is `mod_version` in `mod/firmages-core/gradle.properties`. The build needs the local jars in `mod/firmages-core/libs/` (ProgressiveStages, TerraFirmaCraft, Modonomicon, KubeJS, Rhino, Stargate Journey, Jade; git-ignored; `gradlew fetchLibs` downloads them from the pack's `.pw.toml` files). To ship it, copy the jar to `mods/`, delete the old one and run `packwiz refresh`; packwiz tracks it as a plain file. Details: `mod/firmages-core/README.md`.

## Dev client

A portable Prism instance *Firmament Ages Dev* lives in `tools/PrismLauncher/instances/FirmamentAgesDev/` with Temurin 21 and the pre-launch command `"$INST_JAVA" -jar packwiz-installer-bootstrap.jar http://localhost:8080/pack.toml`. Start `dev\serve.bat`, then `tools\PrismLauncher\prismlauncher.exe`, log in with Microsoft yourself and launch the instance. `dev/dev-client.md` (German) has the full walk-through and the shrine test.

Reload cycle: `/reload` for server scripts, F3+T for client scripts, a restart for startup scripts, `/progressivestages reload` for stage files and a new world for worldgen. The test server is driven by `dev/run_server.py` and `dev/rcon.py`.

## Release

```
powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -Version 0.5.0
```

It checks line endings, runs `packwiz refresh`, sets the version in `pack.toml`, and writes `FirmamentAges-<v>.mrpack`, `FirmamentAges-<v>-curseforge.zip` and a SHA-256 list to `dev/exports/` (git-ignored). It never commits or pushes. Then commit `pack.toml` and `index.toml`, merge `dev` into `main`, tag `vX.Y.Z` and push; the server and the friends read `main`. Take a server backup before any world-breaking change. Both exports embed jars that may not be redistributed (the CurseForge-only mods in the `.mrpack`, the Modrinth mods in the CurseForge zip), so share them privately and never attach them to a public release. `python dev/verify_install.py` compares an export or an installed folder with `index.toml`. German notes: `dev/release.md`.

## How friends install

`powershell -NoProfile -ExecutionPolicy Bypass -File dev\make_friends_zip.ps1` builds `dev/exports/FirmamentAges-Prism.zip`: `instance.cfg` (pre-launch command on the raw `pack.toml` of `main`, Java chosen by Prism, 4 to 8 GB), `mmc-pack.json` (MC 1.21.1, NeoForge 21.1.252), `minecraft/packwiz-installer-bootstrap.jar` and `README-FRIENDS.md` (German, six steps). No mods, configs or account data. A friend imports the zip in Prism once; every launch then syncs mods, configs and scripts. Fallback for someone without Prism: the `.mrpack` (Prism or Modrinth App import, no automatic updates).

## How the server is set up

The server folder is built from `server/` (see `server/README.md`): copy the scripts, `server.properties.template` as `server.properties`, the NeoForge installer and `packwiz-installer-bootstrap.jar`, set `FA_JAVA_HOME` to a Java 21 and accept the EULA yourself. `start.bat` and `start.ps1` run a loop:

1. `packwiz-installer-bootstrap -g -s server <pack.toml>` syncs the server-side files.
2. The NeoForge installer runs once per NeoForge version (`--installServer`).
3. NeoForge starts with `@user_jvm_args.txt @libraries/.../win_args.txt nogui`.
4. The loop restarts after a crash or `stop`.

To update, push to `main`, then type `stop` in the server console. To shut down for good, create a `STOP` file first.
