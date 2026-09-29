# Firmament Ages server template

The files in this folder are the template for a dedicated Firmament Ages server on Windows. The pack itself (mods, configs and scripts) is not stored here. On every start, `packwiz-installer-bootstrap` pulls it from `pack.toml`.

| File | Purpose |
|---|---|
| `start.bat` | Start script for cmd. It runs the packwiz sync and ServerStarterJar in a restart loop. |
| `start.ps1` | The same loop in PowerShell. Pass `-NoUpdate` to skip the pack sync. |
| `user_jvm_args.txt` | JVM arguments: 10 GB heap and the G1 flags from earlier servers on this host. |
| `server.properties.template` | Vanilla settings: whitelist, online mode, PvP, the TFC world preset and view distances. |

## Set up a server folder

1. Create the server folder. For the local PoC this is `<repo>\test-server\`, which git and packwiz ignore. For the live server use any folder outside the repo.
2. Copy `start.bat`, `start.ps1` and `user_jvm_args.txt` into the folder. Copy `server.properties.template` as `server.properties`.
3. Put these binaries next to the scripts. They are not part of the repo.
   - `server.jar` is [ServerStarterJar](https://github.com/neoforged/ServerStarterJar/releases) (0.1.35 or newer). It installs NeoForge 21.1.252 on the first start.
   - `packwiz-installer-bootstrap.jar` comes from [packwiz-installer-bootstrap](https://github.com/packwiz/packwiz-installer-bootstrap/releases).
4. Java 21 (Temurin) goes into `<repo>\tools\jdk-21\`. For a server folder outside the repo, set `JAVA_HOME` in `start.bat` or `$JavaHome` in `start.ps1` to an absolute path. Do not rely on `java` from PATH: on this host PATH points to Corretto 25.
5. Set the pack URL in both scripts:
   - Local PoC: run `packwiz serve` in the repo. The URL is `http://localhost:8080/pack.toml`.
   - Live: use the raw GitHub URL of the `main` branch.
6. Read the [Minecraft EULA](https://aka.ms/MinecraftEULA) and create `eula.txt` with `eula=true` yourself. The scripts never accept it for you.
7. Run `start.bat`.

## Daily operation

- **Update:** type `stop` in the console. The loop syncs the pack from `pack.toml` and starts the server again. A NeoForge version change in the scripts triggers a reinstall (`--installer-force`) exactly once.
- **Shut down for good:** create an empty file named `STOP` in the server folder, then type `stop`.
- **Whitelist and ops:** use `whitelist add <name>` and `op <name>` in the console. `server.properties`, `whitelist.json`, `ops.json` and `world/` are not in the pack index, so packwiz never touches them.
- **One team:** every player joins the same FTB Teams party, because the Ages hang on that party. The planned admin command is `/ftbteams force-add <player> <party>`; its exact syntax is still to be checked in the PoC. See `dev/poc-checklist.md`.
- **Repair stages:** grant with `/stage grant <player> age_N`. Then run `/fa_reconcile` as that player so the helper stages (map, mob ladder, station windows) follow. `/progressivestages reload` reloads the stage files.
- **Backups:** Simple Backups runs inside the server. A nightly `robocopy` of `simplebackups\` to a second disk is still to be set up.
- **Pregeneration:** run Chunky before the first login. TFC worldgen is expensive.

## Settings worth knowing

- `difficulty=hard` follows the design rule "Normal+: hard, not needlessly complicated". Set it to `normal` if the nights are too punishing.
- `view-distance=10`, `simulation-distance=8`. ProgressiveStages checks mob spawn locks against the players inside the simulation distance.
- `allow-flight=true` stops contraptions and flight gear from getting players kicked.
- FTB Chunks gives no claims, no protection and PvP everywhere until the Industrial Age. At that point `kubejs/server_scripts/stages/grants.js` hands out 25 claims and 25 force-loads. Force-loaded chunks only tick while a player is online (`force_load_mode: "never"`, see `config/ftbchunks-world.snbt`).
