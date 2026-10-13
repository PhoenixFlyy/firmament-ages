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

`user_jvm_args.txt` asks for 10 GB of heap; lower `-Xms`/`-Xmx` if the machine has less memory (8 GB works for a
small group). `server.properties` starts with the TFC world preset, a whitelist and `difficulty=hard`.

## Play together

All players share one FTB Teams party: the Ages are unlocked for that party, so invite everyone into the same party
before the first Age is reached.

## Update

Replace `mods`, `config`, `defaultconfigs` and `kubejs` with the folders of the new server pack. Keep `world`,
`server.properties`, `whitelist.json` and `ops.json`.
