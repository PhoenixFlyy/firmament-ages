# Firmament Ages server pack

This zip is a ready-to-run dedicated server for Firmament Ages (Minecraft 1.21.1, NeoForge 21.1.252). It holds the
server-side mods, configs, KubeJS scripts and quests of the pack version in its file name. Client-only mods are left
out.

## Start

1. Unzip into an empty folder.
2. Install Java 21 (for example Eclipse Temurin 21). The scripts use `FA_JAVA_HOME`, then `JAVA_HOME`, then `java`
   on PATH, and stop if that is not Java 21.
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

That is about 62,500 chunks; a Ryzen 7 3700X does about 66 chunks per second, so roughly 16 minutes. `chunky
progress` shows how far it is, and an interrupted run continues with `chunky continue` after a restart.

## Play together

All players share one FTB Teams party: the Ages are unlocked for that party. Every player joins it at the first login,
without an invite; on a new server the first player founds it. To manage parties yourself, set `autoJoin = false`
under `[team]` in `config/firmages-server.toml`; players then use `/ftbteams party create`, `invite` and `join`.

PvP is on (`pvp=true` in `server.properties`). For a public server set `pvp=false`. There are no chunk claims until
the Industrial Age; then the party gets 25 claims and 25 force-loaded chunks, which tick only while a player is online.

## Operation

- **Backups:** Simple Backups writes a full backup every 120 minutes to `simplebackups/`, keeps 10 and at most 25 GB
  (`config/simplebackups-common.toml`). Copy that folder to a second disk now and then.
- **Repair Ages:** `/stage grant <player> age_N` grants an Age (grant in order). Then run `/fa_reconcile` as that
  player so the helper stages (map, mob ladder, station windows) follow.
- **Windows Defender:** it scans every file the server writes, which slows the first start and every world save.
  Add the server folder as an exclusion: Windows Security, Virus & threat protection, Manage settings, Exclusions,
  Add an exclusion, Folder. Exclude only this folder, and only if you trust the files in it.
- **RCON:** it listens on every interface when `server-ip` is empty. Keep `enable-rcon=false` on a public server, or
  never forward port 25575.

## Operator commands for testing

Permission level 2. `/firmages` commands come from firmages-core, `/stage` from the stage mod, `/fa_*` from pack
scripts.

| Command | What it does |
|---|---|
| `/stage grant @s age_N` | Grants an Age (the team gets it). Each `age_N` depends on `age_(N-1)`: grant in order. A quest or command grant plays the short ceremony and leaves no relic. A ring standing for an Age you already hold is consecrated at the next check; its plinth takes the late offering at once, without a grant. |
| `/firmages ages` | Unlocked and locked Ages, mirror status, last reload time. `ages sync` reconciles with the stage mod. |
| `/firmages shrine status` | Heart, missing blocks per ring, tier, offering, rites, phase, prayer, blessings; in age_9 whether the Gathering is done. Also `shrine locate`, `relics`, `extract <plinth>`, `maintenance on/off/status`, `consecrate <ring>`. |
| `/firmages ceremony preview <stage> [full\|short]` | Plays a ceremony to you, no grant. |
| `/firmages origin tp <player> force` | Into The Origin without age_9 and outside creative; both locks let the player stay until he leaves, respawns elsewhere or the server stops. Without `force` the age_9 lock applies. |
| `/firmages origin gathering` | Starts the altar Gathering now for everyone online, wherever they stand, without cooldown. The trial waves follow as in the real fight. |
| `/firmages origin fight` | Summons The Primordial at once, no waves. Refused while the finale trial or the boss lives, and after the win. |
| `/firmages origin status` / `reset` | Arena version, return gate, altar, Gathering count, boss state, island echoes / lets the Gathering and the boss happen again (`finale_won` stays). `origin rebuild` builds the arena again. |
| Simulate facility | Set `debug.allowSimulate = true` in `serverconfig/firmages-server.toml`. Then `/firmages ages simulate grant\|revoke <stage>` changes the Age state without the stage mod (self-tests only); `/firmages shrine simulate_pray` completes the current prayer with ring and offering in place, skipping rites and praying players (in age_9 the Gathering); the headless test players work (`/firmages debug player join\|leave <name>`, `debug use`, `debug pray`, `debug punch`, `debug run`). |
| `/fa_dump` | Writes `local/firmages/recipes.json` (every loaded recipe with its locks), `item_tags.json` and `registries.json`. Also `/fa_dump_full [regex]`, `/fa_recipe <regex>`, `/fa_dims`, `/fa_m3`. Runs from the console and RCON too. |

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
