# firmages-core

Custom mod for the Firmament Ages pack (Minecraft 1.21.1, NeoForge 21.1.252, Java 21).

- Mod id: `firmages`
- Display name: Firmament Ages Core
- Maven group: `dev.firmages`, base package `dev.firmages.core`
- Build system: official NeoForged MDK for 1.21.1 (ModDevGradle 2.0.147, Gradle 9.2.1 wrapper, Parchment 2024.11.17)

Current state: milestone M1 of `SPEC.md` (the core): `AgeState` SavedData, the `<world>/firmages/ages.json` mirror,
ProgressiveStages event subscription, `AgeIndex` from the `firmages:age_items|age_blocks|age_fluids/<age>` tags,
the coalescing `ReloadScheduler`, the commands `/firmages ages [sync|simulate]`, `dump registry`, `reload`,
`selftest <all|core|gate|server>`, the KubeJS binding `FirmAges`, and `firmages-server.toml` / `firmages-client.toml`.

Milestones M2 and M3 (0.2.0): the machine-recipe Age gate (`RecipeManager#apply` TAIL filter with typed extractors for
Create, IE, Mekanism and Occultism plus a JSON output walk, `/firmages recipes audit|why|locked`, the cache flush for the
Occultism mineshaft and the Ars apparatus), the IE excavator filter (MineralMix mixin), the fake-player OreGuard, and
the pack's KubeJS hand-off scripts `kubejs/server_scripts/firmages/` (Digital Miner blacklist, m1 prospecting tags).
Implementation notes: `SPEC.md` §4.7 and §5.1.

## Tests

- `gradlew build` runs the JUnit tests (`src/test`, level U of SPEC §12). They run the pure `CoreSuite`: mirror
  round trip, corrupt/missing mirror → fallback, atomic write, scheduler delay/coalescing/follow-up/failure,
  stage-change dedup, revoke, bulk-only-adds, tag JSON resolution, earliest-Age rule.
  `GateSuite` (the m2 rules, report, excavator pick) and `JsonOutputWalkerFixtureTest` (recipe JSON copied from the
  pinned Create, IE, Mekanism, Occultism, Ars, TFC and DE jars, in `src/test/resources/fixtures`).
- `gradlew runGameTestServer` runs the GameTests (`src/gametest`, level G) on a fresh world with ProgressiveStages,
  Modonomicon, KubeJS and Rhino from `libs/`, and IE and Mekanism from maven (so the typed extractors and the MineralMix
  mixin run against the real classes). The pack scripts `kubejs/server_scripts/firmages/` are copied into the run.
  M2/M3: synthetic recipes `firmages:test/*` and the m1/m3 test tags; M1: the self-test suites, the commands, the AgeIndex built from the test
  tags, and unlock → reload → revoke → reload with the KubeJS probe script `src/gametest/kubejs`. No EULA file is needed.
- In the pack: `/firmages selftest all` writes `logs/firmages-selftest.json` (suites `core` and `server`).

## Notes for the other milestones

- KubeJS 2101 runs `ServerEvents.tags` handlers as a pre-capture while the server scripts load: on a reload at the
  start of `ReloadableServerResources#loadResources` (after this mod's capture, so `FirmAges.lockedOreBlocks()` reads
  that load's tag JSON), but on the initial load before the resource manager exists. The initial-load answer is
  therefore empty; `AgeService` detects the wrong answer and reloads once right after server start.
- `/firmages dump registry` writes `logs/firmages-registry.json`: `{ "items": { "<mod>": [ids] }, "blocks": {...}, "fluids": {...} }`.
- The age-coverage check against ProgressiveStages writes `logs/firmages-coverage.txt` at every server start.

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
later builds take seconds. `./gradlew runClient` / `runServer` start a dev instance in `run/` with NeoForge, this
mod and the `localRuntime` jars from `libs/` (ProgressiveStages and Modonomicon are required dependencies; KubeJS
and Rhino for the binding). All other hooked mods are compileOnly.

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
| KubeJS + Rhino | `libs/kubejs-neoforge-2101.7.2-build.377.jar`, `libs/rhino-2101.2.8-build.91.jar` | as named | Modrinth jars pinned in `mods/kubejs.pw.toml`, `mods/rhino.pw.toml` (compileOnly and dev runtime) |
| Modonomicon | `libs/modonomicon-1.21.1-neoforge-1.120.7.jar` | 1.120.7 | Modrinth jar pinned in `mods/modonomicon.pw.toml` (dev runtime only, until M4) |

Notes:

- `libs/`: the task `fetchLibs` (runs before `compileJava`) reads `url`, `filename` and the sha512 `hash`
  from the `.pw.toml` files listed in `build.gradle` (`libMetafiles`), downloads each jar into `libs/` if missing or
  different, and fails on a hash mismatch. The jars are git-ignored (TFC alone is 65 MB). Updating one of these mods
  in the pack updates the build automatically; only the matching `*_version` in `gradle.properties` must follow.
- Create: the Modrinth jar is not byte-identical to any maven build (it bundles Ponder/Flywheel via Jar-in-Jar).
  Build 281 is the last 6.0.10 build and was published a day after the release jar's build timestamp
  (2026-04-21), so it matches the release. When code touches Create types from Ponder, Flywheel or Registrate,
  add those artifacts as `compileOnly` too (versions are in Create's POM).
- Mekanism: the full jar is used, not only the `api` classifier, because the Digital Miner hook needs internal classes.
  Additions and Tools classifiers exist on the same coordinate if needed.
