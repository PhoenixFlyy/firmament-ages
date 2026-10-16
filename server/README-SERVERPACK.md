# Firmament Ages server pack

This zip is a ready-to-run dedicated server for Firmament Ages (Minecraft 1.21.1, NeoForge 21.1.252). It holds the
server-side mods, configs, KubeJS scripts and quests of the pack version in its file name. Client-only mods are left
out.

Every player needs the client pack of the same version as this server pack. A client of another version may fail to
join, or it shows recipes and locks that do not match the server.

## Start

1. Unzip into an empty folder.
2. Install Java 21 (for example Eclipse Temurin 21). The scripts take the first of `FA_JAVA_HOME`, `JAVA_HOME` and
   `java` on PATH that is Java 21 (another version is skipped), and stop if none is.
3. Read the [Minecraft EULA](https://aka.ms/MinecraftEULA) and create `eula.txt` with the line `eula=true`.
4. Windows: run `start.bat`. Linux: run `sh start.sh`. The first start installs NeoForge from
   `neoforge-21.1.252-installer.jar` once, then the server runs in a restart loop. To end the loop for good, create an
   empty file named `STOP` next to the scripts and type `stop` in the console.
   A hosting panel: run `java -jar neoforge-21.1.252-installer.jar --installServer` once and start `run.sh` or
   `run.bat`; heap settings go into `user_jvm_args.txt`.

## First start

The whitelist is on. In the server console:

```
whitelist add <name>      every player, yourself included
op <name>                 only for admin work
```

Operators and creative players see the recipe viewer in every Age: deop yourself to play the Ages as intended.

## Memory

`user_jvm_args.txt` holds the heap size and Aikar's G1 flags. It ships with 8 GB (`-Xms8G -Xmx8G`):

| Players online | Heap |
|---|---|
| up to about 4 | 8 GB (as shipped) |
| 5 to 10 | 10 GB: `-Xms10G -Xmx10G` |

Keep `-Xms` equal to `-Xmx` and leave the other flags as they are. On a hosting panel with a memory limit for the
container, set the heap to about 85 % of that limit (an 8 GB heap needs a 10 GB plan); the JVM needs the rest for
itself, and a heap at the limit gets the server killed.

## Pregenerate the world

TerraFirmaCraft world generation is heavy, and players exploring new land is what lags a server most. Chunky is in
the server pack: generate the land around spawn once before players join, in the server console:

```
chunky spawn
chunky radius 2000
chunky start
```

That is about 62,500 chunks; a Ryzen 7 3700X does 35 to 65 chunks per second, so plan 16 to 30 minutes. `chunky
progress` shows how far it is, and an interrupted run continues with `chunky continue` after a restart.

## Play together

All players share one FTB Teams party: the Ages are unlocked for that party. Every player joins it at the first login,
without an invite; on a new server the first player founds it. To manage parties yourself, set `autoJoin = false`
under `[team]` in `config/firmages-server.toml`; players then use `/ftbteams party create`, `invite` and `join`.

PvP is on (`pvp=true` in `server.properties`). For a public server set `pvp=false`. There are no chunk claims until
the Industrial Age; then the party gets 25 claims and 25 force-loaded chunks, which tick only while a player is online.

## Operation

- **Backups:** Simple Backups writes a full backup every 120 minutes to `simplebackups/world/`, keeps 10 and at most
  25 GB (`config/simplebackups-common.toml`). Each backup is one zip named after the world and the time, for example
  `world_2026-10-14_01-06-35.zip`, with the folder `world/` inside. It holds everything of the world: terrain,
  players, Ages, teams, quest progress and `world/serverconfig`. Copy `simplebackups/` to a second disk now and then.
- **Restore a backup:**
  1. Stop the server: create `STOP` next to the scripts, then type `stop` in the console.
  2. Rename the folder `world` to `world_broken` (delete it later, once the restored world runs).
  3. Unzip the chosen backup into the server folder, so that `world/level.dat` exists again (the zip also holds a
     `backup.log`, which can be deleted).
  4. Delete `STOP` and start the server. Everything after the time in the zip name is lost.
- **Repair Ages:** `/stage grant <player> age_N` grants an Age to the whole party (grant in order). The map, the
  mob ladder and the station windows follow on their own, at once or at the next login.
  If recipes or ores still behave like the old Age, run `/firmages ages sync` (works from the console): it takes
  every Age the stage mod knows into the world and reloads the recipes.
- **Windows Defender:** it scans every file the server writes, which slows the first start and every world save.
  Add the server folder as an exclusion: Windows Security, Virus & threat protection, Manage settings, Exclusions,
  Add an exclusion, Folder. Exclude only this folder, and only if you trust the files in it.
- **RCON:** it listens on every interface when `server-ip` is empty. Keep `enable-rcon=false` on a public server, or
  never forward port 25575.

## Operator commands

Permission level 2 (`op <name>`), or the server console.

| Command | What it does |
|---|---|
| `/stage grant <player> age_N` | Grants an Age to the party. Each `age_N` needs `age_(N-1)`: grant in order. The ceremony is short and leaves no relic. |
| `/firmages ages` | Unlocked and locked Ages and the last reload. `/firmages ages sync` is the repair (see Operation). |
| `/firmages shrine status` | The shrine: missing blocks per ring, offering, rites, prayer. `shrine locate` finds the heart; `shrine maintenance on\|off` opens the consecrated shrine for repair. |
| `/firmages origin tp <player> force` | Sends a player into The Origin without the Singularity Age, for example to fetch a lost item. |
| `/firmages origin status` / `reset` | State of the finale arena / lets the Gathering and the boss happen again (the won finale stays won). `origin rebuild` builds the arena again. |
| `/firmages origin fight` | Summons The Primordial above the altar at once (the Gathering skipped). Refused while it lives, while nobody is in The Origin to keep the altar loaded, and once the finale is won. |
| `/firmages origin kill boss` | Kills The Primordial; the finale is won the normal way. |

The fight's numbers are the `[boss]` section of `config/firmages-server.toml`: `effectiveHealth` (13000, one player),
`perPlayerFactor` (0.7), `veilFactor` (0.25), `hitCap` (40, the most one hit counts), `enrageMinutes` (10),
`starfallEnrageMinutes` (12), `wipeSeconds` (30). The fight model behind them (1, 2, 4 players: 8.4, 7.2, 6.5 minutes in
Chaotic gear) is in `mod/firmages-core/docs/primordial.md`; the server log names it each time the boss wakes.

## Update

Replace `mods`, `config`, `defaultconfigs` and `kubejs` with the folders of the new server pack, then re-apply your
edits to `config/firmages-server.toml` (for example `autoJoin`). Keep `world`, `simplebackups`, `server.properties`,
`whitelist.json` and `ops.json`.

New TerraFirmaCraft settings in `defaultconfigs/tfc-server.toml` only reach a server whose `config/tfc-server.toml`
does not exist yet. A server from before 0.8.1 adds one line under `[general]` by hand, so that wrought iron ingots
stack as wrought iron (not iron) in ingot piles:

```
inferredMetals = ["ingots/iron", "double_ingots/iron"]
```
