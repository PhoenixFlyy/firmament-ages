# Author Console fields: mod "Firmament Ages Core"

Every value to type or pick in the CurseForge Author Console for the mod project. Create this project **before** the modpack project, because the modpack lists it as a dependency and the upload zip must not carry its jar once the project is approved. Companion: `dev/cf/modpack-fields.md`, `dev/release.md`.

## Create Project

| Field | Value |
|---|---|
| Game | Minecraft |
| Project type (class) | Mods |
| Name | `Firmament Ages Core` |
| Summary (max. 255 characters, 199 used) | `The core mod of the Firmament Ages modpack: shared Ages for one team, a global recipe gate, miner and prospecting filters, the Shrine of Caelum with its ceremony, a Draconic reactor controller and The Origin.` |
| Avatar / project image | `dev/cf/firmages-core-avatar-400.png` (400 x 400). `dev/cf/firmages-core-logo.png` (512 x 512) is the same picture, larger. |
| Primary category | Adventure and RPG |
| Additional categories | Technology, Server Utility, Magic |
| License | MIT License (`mod/firmages-core/LICENSE`; the jar's `neoforge.mods.toml` says `license="MIT"`, moderators compare them) |
| Source | `https://github.com/PhoenixFlyy/firmament-ages/tree/main/mod/firmages-core` |
| Issues | `https://github.com/PhoenixFlyy/firmament-ages/issues` |
| Wiki / Discord / Donations | leave empty |
| Allow project distribution | leave on (packwiz-installer downloads the jar from CurseForge for the friends' installs once the pack lists it) |

Not chosen: *API and Library* (the mod is not a library, nothing depends on it except the pack) and *Miscellaneous* (too vague for moderation).

## Description

Open the editor's HTML (source) view and paste the content of `dev/cf/firmages-core-description.html` (`python dev\cf\md2html.py` regenerates it from `firmages-core-description.md`). The modpack link in the first paragraph assumes the slug `firmament-ages`; adjust it after the modpack project exists, or leave it, CurseForge resolves the final slug when the pack is approved.

## Gallery (Images)

Optional. `dev/cf/gallery/03-shrine-quantum-age.png` (the shrine) and `dev/cf/gallery/05-signature-items.png` (the items the mod registers) fit; both are 1920 x 1080.

## Upload File

| Field | Value |
|---|---|
| File | `mods/firmages-core-0.10.0.jar` (byte-identical to `mod/firmages-core/build/libs/firmages-core-0.10.0.jar` from `gradlew build`) |
| Display name | `firmages-core 0.10.0` |
| Release type | Beta |
| Game version | 1.21.1 |
| Mod loader | NeoForge |
| Java version | Java 21 |
| Environment | Client and server (both) |
| Changelog | the entries **0.10.0**, **0.9.2**, **0.9.1**, **0.9.0**, **0.8.1**, **0.8.0**, **0.7.2**, **0.7.1**, **0.7.0** and **0.6.2** from the changelog in `dev/cf/firmages-core-description.md` (Markdown) |

## Relations (on the file)

| Type | Project |
|---|---|
| Required dependency | ProgressiveStages |
| Required dependency | Modonomicon |
| Optional dependency | KubeJS, FTB Teams, Create, Immersive Engineering, Mekanism, Evolved Mekanism, Occultism, Ars Nouveau, TerraFirmaCraft, Applied Energistics 2, Draconic Evolution, Stargate Journey, Jade, FTB Quests, TerraFirmaCraft Registry API |

## After approval

1. Note the **project id** and the **file id** on the file page.
2. In the repo root:
   ```
   tools\packwiz.exe curseforge add --addon-id <project id> --file-id <file id>
   del mods\firmages-core-0.10.0.jar
   tools\packwiz.exe refresh
   ```
   Answer the dependency prompts with `n` (every dependency is already in the pack). Commit `mods/firmages-core.pw.toml`, `index.toml`, `pack.toml`.
3. Every later version: upload the new jar to this project first, then `tools\packwiz.exe update firmages-core` (or `curseforge add` with the new file id).
