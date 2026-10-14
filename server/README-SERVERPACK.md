# Firmament Ages server pack

This zip is a ready-to-run dedicated server for Firmament Ages (Minecraft 1.21.1, NeoForge 21.1.252). It holds the
server-side mods, configs, KubeJS scripts and quests of the pack version in its file name. Client-only mods are left
out.

## Start

1. Unzip into an empty folder.
2. Install Java 21 (for example Eclipse Temurin 21) and point the environment variable `FA_JAVA_HOME` at it, for
   example `setx FA_JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21"`. The scripts do not use `java` from PATH.
3. Read the [Minecraft EULA](https://aka.ms/MinecraftEULA) and create `eula.txt` with the line `eula=true`.
4. Windows: run `start.bat` (or `start.ps1` in PowerShell). The first start installs NeoForge from
   `neoforge-21.1.252-installer.jar` once, then the server runs in a restart loop. To end the loop for good, create an
   empty file named `STOP` next to the scripts and type `stop` in the console.
   Linux or a hosting panel: run `java -jar neoforge-21.1.252-installer.jar --installServer` once, then start with
   `./run.sh` (the installer writes it; heap settings go into `user_jvm_args.txt`).

`server.properties` starts with the TFC world preset, a whitelist and `difficulty=hard`.

## Memory

`user_jvm_args.txt` holds the heap size and Aikar's G1 flags. It ships with 10 GB (`-Xms10G -Xmx10G`):

| Players online | Heap |
|---|---|
| up to about 4 | 8 GB: `-Xms8G -Xmx8G` |
| 5 to 10 | 10 GB (as shipped) |

Keep `-Xms` equal to `-Xmx` and leave the other flags as they are. On a hosting panel with a memory limit for the
container, set the heap to about 85 % of that limit (10 GB heap needs a 12 GB plan); the JVM needs the rest for
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
under `[team]` in `config/firmages-server.toml`.

PvP is on (`pvp=true` in `server.properties`). For a public server set `pvp=false`.

## Update

Replace `mods`, `config`, `defaultconfigs` and `kubejs` with the folders of the new server pack. Keep `world`,
`server.properties`, `whitelist.json` and `ops.json`.

New TerraFirmaCraft settings in `defaultconfigs/tfc-server.toml` only reach a server whose `config/tfc-server.toml`
does not exist yet. A server that keeps its own file adds new lines by hand. Since 0.8.1 that is one line under
`[general]`, so that wrought iron ingots stack as wrought iron (not iron) in ingot piles:

```
inferredMetals = ["ingots/iron", "double_ingots/iron"]
```
