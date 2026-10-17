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
3. Read the [Minecraft EULA](https://aka.ms/MinecraftEULA). To accept it, open `eula.txt` and change `eula=false` to
   `eula=true`. Until then the scripts warn and the server stops right after the start.
4. Windows: run `start.bat`. Linux: run `sh start.sh`. The first start installs NeoForge from
   `neoforge-21.1.252-installer.jar` once.
   A hosting panel: run `java -jar neoforge-21.1.252-installer.jar --installServer` once and start `run.sh` or
   `run.bat`; heap settings go into `user_jvm_args.txt`. If the panel's stop button sends `stop`, set
   `confirmStop = false` under `[commands]` in `config/firmages-server.toml`, so that `stop` stops at once.

## Stop and restart

In the server console, over RCON, or in game with operator level 4:

| Command | What happens |
|---|---|
| `restart` | Everyone sees "Server restarts in 10 seconds" and a countdown, the server saves and stops, and the start script starts it again. Use it after copying in new mods or configs. |
| `stop`, then `stop confirm` within 30 seconds | The server saves and stops, and the start script ends. `stop` alone only asks. |

`restart` leaves a file named `RESTART` next to the scripts; the script deletes it before the next start. After a
crash the script starts the server again after 10 seconds; the third crash within five minutes ends it (the reason is
in `crash-reports/` and `logs/latest.log`). Do not close the console window to stop the server: Windows gives it only a
few seconds to save.

## First start

The whitelist is on. In the server console:

```
whitelist add <name>      every player, yourself included
op <name>                 only for admin work
```

Only creative mode sees the recipe viewer in every Age; operators in survival play the Ages like everyone else.

## Memory

`user_jvm_args.txt` holds the heap size, Aikar's G1 flags (https://mcflags.emc.gs) and `-Dfile.encoding=UTF-8`. It
ships with 8 GB (`-Xms8G -Xmx8G`):

| Players online | Heap |
|---|---|
| up to about 4 | 8 GB (as shipped) |
| 5 to 10 | 10 GB: `-Xms10G -Xmx10G` |

Keep `-Xms` equal to `-Xmx`: with `-XX:+AlwaysPreTouch` the JVM takes the whole heap at the start and never resizes
it during play. Leave the other flags as they are, except for a heap of 12 GB or more, where Aikar's flags change
five values:

```
-XX:G1NewSizePercent=40 -XX:G1MaxNewSizePercent=50 -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=15
-XX:InitiatingHeapOccupancyPercent=20
```

On a hosting panel with a memory limit for the container, set the heap to about 85 % of that limit (an 8 GB heap
needs a 10 GB plan); the JVM needs the rest for itself, and a heap at the limit gets the server killed.

## server.properties

The shipped file differs from the vanilla defaults where this table says so; every other key is vanilla. The server
rewrites the file at every start and drops its comments.

| Key | Value (vanilla) | Why |
|---|---|---|
| `white-list`, `enforce-whitelist` | `true` (`false`) | Friends only: `whitelist add <name>`. |
| `max-players` | `100` (`20`) | The whitelist decides who joins, not the slot count. |
| `online-mode`, `enforce-secure-profile` | `true` (`true`) | Mojang accounts and signed chat. |
| `log-ips` | `false` (`true`) | Player addresses stay out of the log; the whitelist replaces IP bans. |
| `spawn-protection` | `0` (`16`) | With 16 only operators could build near the spawn, where the first base often stands. |
| `difficulty` | `hard` (`easy`) | The pack's choice; `/difficulty normal` lowers it. |
| `pvp` | `true` (`true`) | See Play together. |
| `allow-flight` | `true` (`false`) | Create contraptions, jetpacks and flight gear would trip the fly kick. |
| `player-idle-timeout` | `0` (`0`) | Idle players stay: their farms and force-loaded chunks run only while someone is online. |
| `level-type` | `tfc:overworld` (`minecraft:normal`) | The TerraFirmaCraft world. |
| `view-distance` | `10` (`10`) | TerraFirmaCraft terrain is slow to generate; pregenerate instead of lowering it (below). |
| `simulation-distance` | `8` (`10`) | Fewer ticking chunks per player. The Age gates for mobs check the players within this distance. |
| `entity-broadcast-range-percentage` | `100` (`100`) | Lower values hide distant mobs and save little at ten players. |
| `sync-chunk-writes` | `false` (`true`) | Region files are written without a disk flush per chunk: saves are much faster, above all on Windows. A power cut can lose the last minutes; the hourly backup covers that. |
| `max-tick-time` | `120000` (`60000`) | The watchdog ends a server whose tick hangs for 2 minutes, writes a crash report with every thread, and the start script starts it again. `-1` would leave a hung server hanging; the longest normal stall, an Age reload, takes about 10 seconds. |
| `network-compression-threshold` | `256` (`256`) | A home upload is the bottleneck; packets above 256 bytes are compressed. |
| `rate-limit` | `0` (`0`) | No packet limit: a limit kicks a player whose client sends more packets per second, and mod menus send more than vanilla ones. |
| `enable-status`, `hide-online-players` | `true`, `false` | The server list shows the pack name, the version (tooltip) and who is online. |
| `motd` | `§6Firmament Ages` | The pack name; the pack version is in the server list tooltip. |
| `enable-rcon`, `server-ip` | `false`, empty | See Operation (RCON). |

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

- **Backups:** Simple Backups writes a full backup every 60 minutes to `simplebackups/world/`, keeps 12 and at most
  25 GB (`config/simplebackups-common.toml`). Each backup is one zip named after the world and the time, for example
  `world_2026-10-14_01-06-35.zip`, with the folder `world/` inside. It holds everything of the world: terrain,
  players, Ages, teams, quest progress and `world/serverconfig`. Copy `simplebackups/` to a second disk now and then.
- **Restore a backup:**
  1. Stop the server: `stop`, then `stop confirm` in the console.
  2. Rename the folder `world` to `world_broken` (delete it later, once the restored world runs).
  3. Unzip the chosen backup into the server folder, so that `world/level.dat` exists again (the zip also holds a
     `backup.log`, which can be deleted).
  4. Start the server. Everything after the time in the zip name is lost.
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

Stop the server (`stop`, then `stop confirm`). Replace `mods`, `config`, `defaultconfigs`, `kubejs`, `start.bat` and
`start.sh` with those of the new server pack, then re-apply your edits to `config/firmages-server.toml` (for example
`autoJoin`). Keep `world`, `simplebackups`, `server.properties`, `eula.txt`, `user_jvm_args.txt`, `whitelist.json` and
`ops.json`.

New TerraFirmaCraft settings in `defaultconfigs/tfc-server.toml` only reach a server whose `config/tfc-server.toml`
does not exist yet. A server from before 0.8.1 adds one line under `[general]` by hand, so that wrought iron ingots
stack as wrought iron (not iron) in ingot piles:

```
inferredMetals = ["ingots/iron", "double_ingots/iron"]
```
