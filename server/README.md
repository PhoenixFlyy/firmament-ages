# Firmament Ages server template

The files in this folder are the template for a dedicated Firmament Ages server on Windows. The pack itself (mods, configs and scripts) is not stored here. On every start, `packwiz-installer-bootstrap` pulls it from `pack.toml`.

| File | Purpose |
|---|---|
| `start.bat` | Start script for cmd. It runs the packwiz sync, installs NeoForge when needed and starts the server in a restart loop. |
| `start.ps1` | The same loop in PowerShell. `-NoUpdate` skips the pack sync; `-PackUrl` and `-JavaHome` override the settings at the top. |
| `user_jvm_args.txt` | JVM arguments: 10 GB heap and the G1 flags from earlier servers on this host. The NeoForge installer leaves an existing file alone. |
| `server.properties.template` | Vanilla settings: whitelist, online mode, PvP, the TFC world preset and view distances. |

## Set up a server folder

1. Create the server folder. For the local PoC this is `<repo>\test-server\`, which git and packwiz ignore. For the live server use any folder outside the repo.
2. Copy `start.bat`, `start.ps1` and `user_jvm_args.txt` into the folder. Copy `server.properties.template` as `server.properties`.
3. Put these binaries next to the scripts. They are not part of the repo.
   - `neoforge-21.1.252-installer.jar` from [maven.neoforged.net](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.252/neoforge-21.1.252-installer.jar) (the repo keeps a copy in `tools\`). The script runs it with `--installServer` whenever `libraries\net\neoforged\neoforge\<version>\` is missing, so the first start and every NeoForge version change install it once.
   - `packwiz-installer-bootstrap.jar` from [packwiz-installer-bootstrap](https://github.com/packwiz/packwiz-installer-bootstrap/releases) (copy in `tools\`). On its first run it downloads `packwiz-installer.jar` next to itself.
4. Java 21 (Temurin). Order: `-JavaHome` (only `start.ps1`), then the environment variable `FA_JAVA_HOME`, then the default `<server folder>\..\tools\jdk-21`. The default only fits `<repo>\test-server`; for the live server set `FA_JAVA_HOME` (for example `setx FA_JAVA_HOME C:\Java\temurin-21`). Do not rely on `java` from PATH: on this host PATH points to Corretto 25.
5. Set the pack URL in both scripts:
   - Local PoC: run `packwiz serve` in the repo. The URL is `http://localhost:8080/pack.toml`.
   - Live: the raw GitHub URL of the `main` branch, `https://raw.githubusercontent.com/PhoenixFlyy/firmament-ages/main/pack.toml` (the commented line in both scripts).
6. Read the [Minecraft EULA](https://aka.ms/MinecraftEULA) and set `eula=true` in `eula.txt` yourself. The scripts never accept it for you; without it the server writes `eula=false` and exits, and the loop starts it again.
7. Run `start.bat`.

### Tested 2026-10-01

A copy of this template in `dev\exports\test-server-template\` (port 25566, 6 GB heap, pack from `packwiz serve -p 8089`) ran `start.ps1` once: sync in 10 s ("Finished successfully"), NeoForge install in 27 s, launch with `@libraries/.../win_args.txt`, exit at the EULA check, restart after 10 s, second sync, and a clean exit of the loop on the `STOP` file. `user_jvm_args.txt` was not overwritten. `Done` was not reached there because the EULA was not accepted for that folder; the identical launch line (`run.bat` of the installer) reaches `Done` in `test-server\` (`dev/boot-report.md`).

## Daily operation

- **Update:** type `stop` in the console. The loop syncs the pack from `pack.toml` and starts the server again. For a NeoForge version change, set `$NeoForge` in `start.ps1` and `NEOFORGE` in `start.bat` and put the new installer jar next to them; the next pass installs it.
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
