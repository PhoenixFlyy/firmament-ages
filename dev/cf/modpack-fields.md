# Author Console fields: modpack "Firmament Ages"

Every value to type or pick in the CurseForge Author Console (authors.curseforge.com), in the order the console asks. Files are relative to the repo root. Companion: `dev/cf/firmages-core-fields.md` (the mod project, created first), `dev/release.md` (the German step list).

## Create Project

| Field | Value |
|---|---|
| Game | Minecraft |
| Project type (class) | Modpacks |
| Name | `Firmament Ages` |
| Summary (max. 150 characters, 133 used) | `A co-op TerraFirmaCraft progression pack: ten Ages from knapping flint to a Draconic reactor and a Stargate, one shrine, one final boss.` |
| Avatar / project image | `dev/cf/avatar-400.png` (400 x 400, the console's required size). `dev/cf/logo.png` (512 x 512) is the same picture for places that take a larger image. |
| Primary category | Tech |
| Additional categories | Quests, Magic, Multiplayer, Sci-Fi, Extra Large |
| License | MIT License (pick from the list; the pack's own parts, see "License" in the description) |
| Source | `https://github.com/PhoenixFlyy/firmament-ages` |
| Issues | `https://github.com/PhoenixFlyy/firmament-ages/issues` |
| Wiki | leave empty |
| Discord / Donations | leave empty |
| Allow project distribution (API / third-party launchers) | leave on (friends install through packwiz-installer from GitHub; the CurseForge copy is for everybody else) |

## Description

Open the editor's HTML (source) view and paste the content of `dev/cf/modpack-description.html`. Regenerate before pasting when anything changed:

```
python dev\cf\gen_credits.py      # credits: 216 mods, every one with author and link
python dev\cf\md2html.py          # modpack-description.html and firmages-core-description.html
```

If the editor only takes Markdown or plain text, `dev/cf/modpack-description.md` is the same text.

## Gallery (Images)

Upload in this order, each with its title as the image title:

| File | Title |
|---|---|
| `dev/cf/banner.png` (1920 x 1080) | Firmament Ages |
| `dev/cf/gallery/01-shrine-stone-age.png` | The Shrine after the Stone Age |
| `dev/cf/gallery/02-shrine-arcane-age.png` | The Shrine after the Arcane Age |
| `dev/cf/gallery/03-shrine-quantum-age.png` | The Shrine after the Quantum Age |
| `dev/cf/gallery/04-shrine-build-plan.png` | Build plan of the finished shrine |
| `dev/cf/gallery/05-signature-items.png` | The ten signature items |

All gallery images are 1920 x 1080 and generated from the pack's shrine data and textures (`dev/cf/make_gallery.py`); real in-game screenshots can be added later.

## Upload File (the modpack zip)

Build the zip first: `tools\packwiz.exe refresh` then `python dev\cf\export_cf.py` writes `dev\exports\FirmamentAges-0.9.1-curseforge-upload.zip`. The script lists every jar in `overrides/`; expected: only `firmages-core-0.9.1.jar` until the mod project is approved, then none.

| Field | Value |
|---|---|
| File | `dev/exports/FirmamentAges-0.9.1-curseforge-upload.zip` |
| Display name | `Firmament Ages 0.9.1` |
| Release type | Beta |
| Game version | 1.21.1 (read from the manifest; tick it if the console asks) |
| Mod loader | NeoForge |
| Java version | Java 21 (if the console offers the field) |
| Changelog | the newest section of `CHANGELOG.md` (Markdown; switch the changelog editor to Markdown, or paste as plain text) |
| Relations | Required dependency: Firmament Ages Core (once its project exists). The other 215 mods (and the two shader packs) are in the manifest and need no relation. |

## Additional File (server pack)

On the uploaded file's page: **Upload Additional File**.

| Field | Value |
|---|---|
| File | `dev/exports/FirmamentAges-0.9.1-server.zip` (from `powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1`, step 5; about 510 MB) |
| Display name | `Firmament Ages 0.9.1 Server Pack` |
| Release type | Beta (same as the main file) |
| Game version / loader | 1.21.1, NeoForge |
| Changelog | `Server pack for Firmament Ages 0.9.1: ready-to-run server folder (NeoForge 21.1.252 installer, all server-side mods, configs, scripts, start.bat/start.ps1). See README-SERVERPACK.md inside.` |

## After approval

1. Note the project id and the file id of the modpack file (file page).
2. The friends' install does not change (GitHub, packwiz); nothing to do in the repo.
3. Next release: `dev/release.md` section 4.
