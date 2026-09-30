# firmages-core

Custom mod for the Firmament Ages pack (Minecraft 1.21.1, NeoForge 21.1.252, Java 21).

- Mod id: `firmages`
- Display name: Firmament Ages Core
- Maven group: `dev.firmages`, base package `dev.firmages.core`
- Build system: official NeoForged MDK for 1.21.1 (ModDevGradle 2.0.147, Gradle 9.2.1 wrapper, Parchment 2024.11.17)

Current state: skeleton only. The main class `dev.firmages.core.FirmagesCore` logs `firmages-core loaded` and registers nothing.

## Build

The build uses the pack's bundled JDK in `FirmamentAges/tools/jdk-21`. `gradle.properties` pins it through
`org.gradle.java.home` and `org.gradle.java.installations.paths`, with auto-detect and auto-download off.
The wrapper script itself still needs a Java to start, so set `JAVA_HOME` for the shell session only (no PATH change):

PowerShell:

```powershell
cd D:\Minecraft\MinecraftModServer\FirmamentAges\mod\firmages-core
$env:JAVA_HOME = "D:\Minecraft\MinecraftModServer\FirmamentAges\tools\jdk-21"
.\gradlew.bat build
```

Git Bash:

```bash
cd /d/Minecraft/MinecraftModServer/FirmamentAges/mod/firmages-core
JAVA_HOME=/d/Minecraft/MinecraftModServer/FirmamentAges/tools/jdk-21 ./gradlew build
```

Output: `build/libs/firmages-core-<mod_version>.jar` (version is `mod_version` in `gradle.properties`).

The first build downloads Gradle, NeoForge 21.1.252, Minecraft and the dependency jars (a few minutes);
later builds take seconds. `./gradlew runClient` / `runServer` start a dev instance in `run/` (with only
NeoForge and this mod, since all hooked mods are compileOnly).

## Shipping the jar in the pack

The mod is not on Modrinth or CurseForge, so it goes into the pack as a plain jar:

1. Copy `build/libs/firmages-core-<version>.jar` into `FirmamentAges/mods/` (delete the previous version's jar).
2. Run `packwiz refresh` in `FirmamentAges/`. packwiz tracks the jar directly in `index.toml`
   (hash of the file, no `.pw.toml` metafile), so clients get it via packwiz-installer.

`mod/` is listed in `FirmamentAges/.packwizignore`, so sources and build output never enter the pack index.
The repo root `.gitignore` excludes `mod/firmages-core/build/`, `.gradle/`, `run/`, `repo/` and `libs/*.jar`.

## Dependencies

All hook targets are `compileOnly` and non-transitive: the mod compiles against the exact versions the pack
ships but neither bundles nor requires them. Each integration must be guarded with
`ModList.get().isLoaded("<modid>")` and kept in classes that only load when the mod is present.
Versions live in `gradle.properties` and must be bumped together with the pack's `mods/*.pw.toml`.

| Mod | Artifact | Version | Source |
|---|---|---|---|
| Create | `com.simibubi.create:create-1.21.1:<v>:slim` | 6.0.10-281 | maven.createmod.net |
| Immersive Engineering | `blusunrize.immersiveengineering:ImmersiveEngineering` | 1.21.1-12.4.2-194 | maven.blamejared.com |
| Mekanism (+ `generators` classifier) | `mekanism:Mekanism` | 1.21.1-10.7.19.85 | modmaven.dev |
| Draconic Evolution | `com.brandon3055.draconicevolution:Draconic-Evolution` | 1.21.1-3.1.4.633 | maven.covers1624.net |
| Brandon's Core | `com.brandon3055.brandonscore:BrandonsCore` | 1.21.1-3.2.1.309 | maven.covers1624.net |
| CodeChicken Lib | `io.codechicken:CodeChickenLib` | 1.21.1-4.6.1.529 | maven.covers1624.net |
| Occultism | `com.klikli_dev:occultism-1.21.1-neoforge` | 1.224.4 | dl.cloudsmith.io/public/klikli-dev/mods |
| Applied Energistics 2 | `org.appliedenergistics:appliedenergistics2` | 19.2.18 | maven.neoforged.net/releases |
| FTB Library | `dev.ftb.mods:ftb-library-neoforge` | 2101.1.37 | maven.ftb.dev/releases |
| FTB Teams | `dev.ftb.mods:ftb-teams-neoforge` | 2101.1.11 | maven.ftb.dev/releases |
| FTB Quests | `dev.ftb.mods:ftb-quests-neoforge` | 2101.1.36 | maven.ftb.dev/releases |
| ProgressiveStages | `libs/progressivestages-3.0.5.jar` | 3.0.5 | no maven; Modrinth jar pinned in `mods/progressivestages.pw.toml` |
| TerraFirmaCraft | `libs/TerraFirmaCraft-NeoForge-1.21.1-4.2.11.jar` | 4.2.11 | no maven; Modrinth jar pinned in `mods/terrafirmacraft.pw.toml` |

Notes:

- `libs/`: the task `fetchLibs` (runs before `compileJava`) reads `url`, `filename` and the sha512 `hash`
  from the two `.pw.toml` files, downloads the jar into `libs/` if missing or different, and fails on a hash
  mismatch. The jars are git-ignored (TFC alone is 65 MB). Updating TFC or ProgressiveStages in the pack
  updates the build automatically; only `tfc_version` / `progressivestages_version` in `gradle.properties`
  must follow.
- Create: the Modrinth jar is not byte-identical to any maven build (it bundles Ponder/Flywheel via Jar-in-Jar).
  Build 281 is the last 6.0.10 build and was published a day after the release jar's build timestamp
  (2026-04-21), so it matches the release. When code touches Create types from Ponder, Flywheel or Registrate,
  add those artifacts as `compileOnly` too (versions are in Create's POM).
- Mekanism: the full jar is used, not only the `api` classifier, because the Digital Miner hook needs internal classes.
  Additions and Tools classifiers exist on the same coordinate if needed.
