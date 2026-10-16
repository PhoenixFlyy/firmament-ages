# Firmament Ages: developer notes

Firmament Ages is a public co-op progression modpack on Minecraft 1.21.1 with NeoForge 21.1.x and Java 21; the player-facing summary is the root [`README.md`](../README.md). This file is for whoever builds the pack.

The whole server plays as **one team** through **Dawn and ten Ages**: Stone, Bronze, Iron, Arcane, Industrial, Electric, Information, Space, Quantum and Singularity. TerraFirmaCraft carries the world and the metallurgy up to steel. Create is the kinetics and logistics layer. The Arcane Age is the magic Age. Immersive Engineering, Mekanism and Draconic Evolution carry the late game. The finale runs from the Ultimate Singularity through a Stargate to a final boss in The Origin.

Dawn ends with a quest (The First Spark grants the Stone Age). From the Stone Age on, every Age is granted by the shrine of firmages-core: the Age's ring, its offering on the plinth, its rite and a prayer; the goal quest only waits for the next Age. ProgressiveStages hides everything from a later Age: items, recipes, ores (disguised as rock), mobs and dimensions. The quest book (the Chronicle) opens with **K**.

## Where things are written down

| Document | What |
|---|---|
| `../Modpack-Planung/docs/README.md` | index of the German design documents outside this repo (`08-Ages-Design.md`, `09-Entscheidungen.md`); older planning is in `../Modpack-Planung/archive/` |
| `dev/decisions-while-away.md` | every design call made while Felix was away (German, one row each) |
| `dev/content-lock.md` | the locked content of 1.0 with Felix's answers (German): no new mods from here on |
| `dev/offene-punkte.md` | every open point in one place (German) |
| `dev/release.md` | build, local test, CurseForge upload, friends, server (German) |
| `dev/client-test-brief.md` | what Felix checks in the client, per version (German) |
| `dev/style-guide.md` | rules for player text, checked by `dev/validate_quests.py --style` (German) |
| `dev/quest-images.md` | quest pictures: path, sizes, the `{image:...}` line and who renders what (German) |
| `dev/poc-results.md` | evidence log of every server and static run |
| `dev/harmonie-messung.md` | the measured reference plants per Age against the harmony bands, the fission/fusion tuning (German) |
| `mod/firmages-core/SPEC.md` | the pack's own mod |
| `CHANGELOG.md` | public changelog, newest version on top |
| `docs/WALKTHROUGH.md` | the player walkthrough (English, linked from `README.md` and the CurseForge page); update it with every quest or shrine change |
| `server/README-SERVERPACK.md` | the host's guide, shipped in the server pack |
| `dev/archive/` | notes no longer kept up to date: dev client walk-through, keybind map, quest book notes, texture notes |

All player-facing text in the pack is English.

## Repository layout

| Path | Contents | git | packwiz |
|---|---|---|---|
| `pack.toml`, `index.toml` | pack metadata and file index (`packwiz refresh`) | yes | yes |
| `LICENSE` | MIT License (2026, PhoenixFlyy) for the pack's own parts; not served | yes | no |
| `mods/*.pw.toml` | one metafile per mod (all on CurseForge except Banner Flags, which is Modrinth-only; eleven keep a Modrinth metafile with a CurseForge twin in `dev/cf/swap/`) | yes | yes |
| `config/` | changed configs only; `config/progressivestages/stages/*.toml` holds the stage definitions | yes | yes |
| `defaultconfigs/` | server configs that NeoForge copies into every new world (TFC, IE, Mekanism) | yes | yes |
| `kubejs/` | `startup_scripts/` (items, shared constants), `server_scripts/` (recipes, tags, stage logic), `client_scripts/` (tooltips, recipe viewer), `data/` (datapack: biome modifiers, TFC item heat), `assets/` (textures, quest art) | yes | yes |
| `dev/` | tooling (generators, `run_server.py`, `poc_analyze.py`, `release.ps1`, `make_friends_zip.ps1`, `verify_install.py`) and notes; `dev/exports/` holds the release builds (git-ignored) | yes | no |
| `docs/` | `WALKTHROUGH.md`, the player walkthrough | yes | no |
| `server/` | dedicated server template (`start.bat`, `start.ps1` with the pack sync, `user_jvm_args.txt`, `server.properties.template`), the server pack's own start scripts (`pack/start.bat`, `pack/start.sh`) and `README-SERVERPACK.md` | yes | no |
| `mod/firmages-core/` | the pack's own NeoForge mod (source, `SPEC.md`) | yes | no |
| `tools/` | local binaries: JDK 21, packwiz, packwiz-installer-bootstrap, the NeoForge installer, portable Prism | no | no |
| `test-server/` | local PoC server | no | no |

## Conventions

- **Stages.** The Age stages are `dawn` and `age_0` … `age_9`. The mob ladder is `mob_0` … `mob_9`. Helper stages are `ftbchunks_mapping`, `tool_<station>` and `finale_won`, plus `disabled`, which is never granted. In ProgressiveStages an entry is locked while *any* stage that lists it is missing, so everything goes into the stage file that unlocks it. Stage files carry no `unlock_message`: the shrine ceremony is the only Age announcement.
- **Generated lock lists.** TFC has 1113 ore blocks, and ProgressiveStages only accepts exact block ids in `[[ores.overrides]]`. `dev/gen_stage_locks.py` therefore writes the TFC metal, ore and override lists from `dev/age_map.toml`. It rewrites the regions between `# >>> generated:… >>>` and `# <<< generated:… <<<`. Edit the tables, never the generated lines. Run `python dev/gen_stage_locks.py`; `--check` exits 1 if a file is out of date.
- **Age grants.** The First Spark grants `age_0`; the shrine grants `age_1` to `age_9`; firmages-core grants `finale_won` when the final boss dies. `kubejs/server_scripts/stages/grants.js` derives everything else from the Age stage: the mob ladder, the map, the station windows and the chunk quota.
- **Player text.** Quest text lives in `dev/gen_quests.py`, never in the SNBT; the rules are in `dev/style-guide.md`. The quest book is "the Chronicle" everywhere.
- **Chronicle maps and pictures.** `dev/quest_layout.py` places every quest (the goal in the middle, strands on orbits round it, curved lines); the hints per chapter are `LAYOUT_HINTS` in `dev/gen_quests.py`. The Firmament is the first chapter (read-me-first notes, the road of offerings, a build guide per shrine ring); the group Great Works holds The Gatehouse, The Living House and The Silo. Pictures (shrine rings per layer, an animated `*.apng.png` per ring, the works) come from `dev/gen_quest_pictures.py`, which reads the textures from the mod jars of the CurseForge instance (`FIRMAGES_MODS_DIR`) and writes `dev/data/quest_pictures.json` for the generator. `dev/preview_quests.py` draws every chapter map into `dev/quest-preview/` (open `index.html`).
- **Needs in-game proof.** Every line marked `// POC:` or `# POC:` still has to be checked in the game. Open checks and open config values are in `dev/offene-punkte.md`.

## Current state

- **Pack 0.12.0:** 264 mod metafiles plus the pack's own `firmages-core` jar (265 in the CurseForge credits; the content is locked, `dev/content-lock.md`), and two shader packs in `shaderpacks/` (Complementary Reimagined on by default, MakeUp Ultra Fast). Content for all Ages from Dawn to Singularity, the quest book (`dev/gen_quests.py`), the shrine of Caelum and The Origin with its final boss, The Primordial, are in. Round C2 added the wood classes (`dev/wood_classes.toml`, `dev/gen_wood_classes.py`), the ore ladder, fuel and machine tables of the harmony round (`kubejs/data/firmages/balance/`, `dev/gen_fuels.py`, `dev/gen_machine_usage.py`, `dev/miner_ladder.py`) and the sky road (`kubejs/startup_scripts/sky.js`).
- **firmages-core 0.12.0:** milestones M1 to M7 of `mod/firmages-core/SPEC.md` (Age state and reloads, machine-recipe gate, miner filter and prospecting, reactor refuelling, the shrine multiblock, Keystone lending, blessings, rites, consecration and maintenance mode, the immersion layer of SPEC §18, the Dawn grid lock with ground crafting of §19.3), The Origin with its own travel lock and spawn list, The Primordial (`mod/firmages-core/docs/primordial.md`, fight model §39), all sky features of SPEC §24 to §33 (M10 Starfall, M11 Sky Harvest, M12 the Lens Crown, M13 the Drifting Isles, M14 the Launch Complex, M16 Wayshrines and Sky Anchors, M17 the Origin Archipelago), proof of work and the heart's power draw (§35, §36), the FE cap and the energy and ore audits (§37, §38); round H3 adds the Ore Processor of the Singularity Age (§40), the Arcane Catalyst (§41), the Stargate costs (§16.2a), One Iron in loot and trades (§34.4) and the TFC Regrowing Forests guard (§20.1).
- **Verified without a client:** the test server boots on Felix's age_9 world, `dev/poc_analyze.py` runs its checks on a recipe dump, and the debug players of the mod walk the shrine, team and quest paths (`dev/poc-results.md`). The client checklist is `dev/client-test-brief.md`.

## Build and serve (packwiz)

```
tools\packwiz.exe refresh            # after any change under config/, defaultconfigs/, kubejs/ or mods/
tools\packwiz.exe serve              # serves the working tree on http://localhost:8080/pack.toml (dev\serve.bat)
python dev/gen_stage_locks.py        # after changing dev/age_map.toml or the Age tables in the generator
python dev/gen_quests.py; python dev/validate_quests.py --style   # after changing quests
python dev/gen_quest_images.py       # quest pictures in kubejs/assets/firmages/textures/quests (--check compares pixels)
python dev/preview_quests.py         # the chapter maps as pictures, dev/quest-preview/index.html
python dev/gen_quest_pictures.py     # after changing a shrine ring or a Great Works plan (then gen_quests)
python dev/gen_fuels.py              # after changing kubejs/data/firmages/balance/fuels.json (--check compares)
python dev/gen_machine_usage.py      # Mekanism, More Machine and IE excavator config values (--check, --server-dir)
python dev/miner_ladder.py --check   # miners against the ore ladder (metals.json, the configs, --dump-dir for all veins)
python dev/gen_reference_builds.py   # reference builds of the energy measurement (dev/reference/; --install <copy> as a world datapack,
                                     # then /firmages energy reference run <name>|all on that copy with debug.allowSimulate)
```

Text files are LF (`.gitattributes`). packwiz hashes the working copy, GitHub serves the git copy: a file with CRLF in the working tree gets a wrong hash in `index.toml`, and every install from GitHub then fails on it. `dev/release.ps1` checks this.

## Build the mod

```
cd mod/firmages-core
set JAVA_HOME=D:\Minecraft\MinecraftModServer\FirmamentAges\tools\jdk-21
gradlew build                        # JUnit tests and build/libs/firmages-core-<mod_version>.jar
gradlew runGameTestServer            # GameTests on a fresh world
```

The version is `mod_version` in `mod/firmages-core/gradle.properties`. The build needs the local jars in `mod/firmages-core/libs/` (git-ignored; `gradlew fetchLibs` downloads them from the pack's `.pw.toml` files and checks their hashes). To ship it, copy the jar to `mods/`, delete the old one and run `packwiz refresh`; packwiz tracks it as a plain file. Details: `mod/firmages-core/README.md`.

## Dev client

A portable Prism instance *Firmament Ages Dev* lives in `tools/PrismLauncher/instances/FirmamentAgesDev/` with Temurin 21 and the pre-launch command `"$INST_JAVA" -jar packwiz-installer-bootstrap.jar http://localhost:8080/pack.toml`. Start `dev\serve.bat`, then `tools\PrismLauncher\prismlauncher.exe`, log in with Microsoft yourself and launch the instance. `dev/archive/dev-client.md` (German) has the walk-through.

Reload cycle: `/reload` for server scripts, F3+T for client scripts, a restart for startup scripts, `/progressivestages reload` for stage files and a new world for worldgen. The test server is driven by `dev/run_server.py` and `dev/rcon.py`.

## Release

```
powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -Version X.Y.Z
python dev\cf\export_cf.py
```

`release.ps1` checks line endings, runs `packwiz refresh`, sets the version in `pack.toml` and on the title screen (`config/fancymenu/customization/title_screen_layout.txt`), and writes the `.mrpack`, a private CurseForge zip, the server pack and a SHA-256 list to `dev/exports/` (git-ignored). `export_cf.py` writes the CurseForge upload zip. Neither commits or pushes. The full steps (local test with the CurseForge App, upload, friends, server) are in `dev/release.md`.

## How the server is set up

The live server folder is built from `server/`: copy the scripts, `server.properties.template` as `server.properties`, the NeoForge installer and `packwiz-installer-bootstrap.jar`, set `FA_JAVA_HOME` to a Java 21 and accept the EULA yourself. `start.bat` and `start.ps1` run a loop:

1. `packwiz-installer-bootstrap -g -s server <pack.toml>` syncs the server-side files.
2. The NeoForge installer runs once per NeoForge version (`--installServer`).
3. NeoForge starts with `@user_jvm_args.txt @libraries/.../win_args.txt nogui`.
4. The loop restarts after a crash or `stop`.

Set the pack URL at the top of both scripts to `https://raw.githubusercontent.com/PhoenixFlyy/firmament-ages/main/pack.toml` (the default is a local `packwiz serve`). To update, push to `main`, then type `stop` in the server console. To shut down for good, create a `STOP` file first. For a NeoForge version change, set `$NeoForge` in `start.ps1` and `NEOFORGE` in `start.bat` and put the new installer next to them; the next pass installs it. Whitelist, backups and repair commands are in `server/README-SERVERPACK.md`.
