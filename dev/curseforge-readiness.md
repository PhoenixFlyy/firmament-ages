# CurseForge-Bereitschaft

Stand 2026-10-13, Pack 0.6.2, 191 Mods (190 CurseForge-Dateien im Manifest plus firmages-core). Erste Fassung 2026-10-01 (Pack 0.5.0, 193 Mods). Regel von CurseForge ([Moderation Policies](https://support.curseforge.com/support/solutions/articles/9000197279-project-and-modpack-moderation-policies), Abschnitt „Non-CurseForge Mods“): Ein Modpack darf nur Mods enthalten, die auf CurseForge liegen oder auf der Liste [Non-CurseForge Approved](https://docs.google.com/spreadsheets/d/176Wv-PZUo9hFxy6oC6N8tWdquBLPRtSuLbNK-r0_byM/edit#gid=0) stehen. Neue Einträge beantragt man über das [Antragsformular](https://forms.monday.com/forms/a46faa60c3d9cf097763311811db5bbd?r=use1) (verlinkt im Kopf der Liste). Mods, die ein CurseForge-Projekt haben, gehören ausdrücklich **nicht** auf die Liste.

## Ergebnis

| | Anzahl |
|---|---|
| Mods mit CurseForge-Datei im Upload-Zip (Manifest) | 190 |
| davon schon vorher CurseForge (FTB, Twilight Forest, Torque Link …) | 8 |
| davon umgestellt von Modrinth (`dev/cf_convert.py`, Protokoll `dev/cf-convert.log`) | 182 |
| … byte-gleich (CurseForge-Fingerprint-Treffer, sha1 gleich) | 170 |
| … gleiche Version, andere Datei (nur `MANIFEST.MF` bzw. Zip-Packung, gleicher Code) | 11 |
| … gleiche Version, **anderer Code** | 1 (Gateways to Eternity) |
| Mods ohne CurseForge-Projekt | 0 (MekaTFC und TFC Ore Tooltips sind seit 2026-10-13 nicht mehr im Pack) |
| eigene Mod, noch nicht auf CurseForge | 1 (firmages-core) |
| Jars in `overrides/mods/` des Upload-Zips heute | 1 (`firmages-core-0.6.1.jar`; `python dev/cf/export_cf.py`, 2026-10-13) |
| Jars in `overrides/` nach dem Upload von firmages-core | 0 |

Keine Mod hatte auf CurseForge nur eine andere Versionsnummer: jede gefundene Datei ist dieselbe Version wie die Modrinth-Datei des Packs.

## Entscheidungen (getroffen 2026-10-13)

| Mod | Lizenz (Modrinth / Repo) | Auf der Approved-Liste? | Entscheidung |
|---|---|---|---|
| **MekaTFC** 0.1.0 (zhixuan45), [Modrinth](https://modrinth.com/mod/mekatfc), [GitHub](https://github.com/zhixuan45/MekaTFC) | All Rights Reserved (Modrinth); das Repo hat keine LICENSE-Datei | nein | **ersetzt** durch die eigene Osmium-Ader des Packs: KubeJS-Erzblöcke `firmages:ore/<güte>_native_osmium/<gestein>` (arm, normal, reich in den zehn magmatischen und metamorphen Gesteinen der MekaTFC-Ader), Erzstücke, kleines Oberflächenstück, TFC-Ader `firmages:vein/osmium`, Schmelzen und Gießen zum Mekanism-Osmiumbarren, Doppelbarren und Steel-Casing-Schweißen (`kubejs/startup_scripts/osmium.js`, `kubejs/server_scripts/recipes/age_6/osmium.js`, `kubejs/data/firmages/`). Die Common-Tags, die MekaTFC für TFC-Erze nebenbei setzte, stehen jetzt in `kubejs/server_scripts/tags/tfc_common_tags.js`. Nachweis: `dev/poc-results.md`, Abschnitt „Osmium vein and Ore Tooltips removal“. |
| **TFC Ore Tooltips** 1.2.0 (Grimm147), [Modrinth](https://modrinth.com/mod/tfc-ore-tooltips) | MIT (Modrinth); kein Repo angegeben | nein | **gestrichen**. Reine Client-Hilfe (Gesteine und Höhen im Erz-Tooltip); das TFC-Feldbuch zeigt dieselben Wirtsgesteine, nichts im Pack hing an ihr. |
| **firmages-core** 0.6.1 (eigene Mod) | MIT (`mod/firmages-core/LICENSE`) | – | eigenes Projekt: zuerst als Mod-Projekt hochladen (`dev/cf/UPLOAD-ANLEITUNG.md`), danach per `packwiz curseforge add` ins Pack. Kein Antrag nötig. |

Damit braucht das Upload-Zip keinen Antrag auf der Approved-Liste mehr.

## Sonderfälle der Umstellung

### Sechs Mods bleiben im Repo auf Modrinth

Die CurseForge-Dateien von **Alternating Flux, Create Aeronautics, Create: Design n' Decor, Create Factory Logistics, Entity Culling** und **TFC Ruins** sind für Drittprogramme gesperrt („excluded from the CurseForge API“). packwiz-installer (Prism-Instanzen der Freunde, der Server) bricht bei ihnen mit „must be downloaded manually“ ab; gefunden mit einem Testlauf gegen `packwiz serve` (Client- und Server-Seite). Darum stehen sie in `mods/` weiter als Modrinth-Metadateien (dieselben Jars, Fingerprint-Treffer). Ihre CurseForge-Metadateien liegen in `dev/cf/swap/`; `python dev/cf/export_cf.py` tauscht sie für das Upload-Zip ein. Nach dem Tausch laufen beide Installer-Testläufe durch: Client 410 von 410 Dateien, Server 401 von 401, alle Hashes gleich (`dev/verify_install.py dir`).

### Gleiche Version, andere Datei (CurseForge-Datei übernommen)

Elf Mods: Create: Connected, Create: Liquid Fuel, FastSuite, Kotlin for Forge, KubeJS, KubeJS Create, KubeJS Mekanism, MEGA Cells, Sophisticated Backpacks, Sophisticated Backpacks Create Integration, Sophisticated Core. Unterschied nur in `META-INF/MANIFEST.MF` (Zeitstempel der Builds) bzw. in der Zip-Packung (MEGA Cells: gleiche Einträge, gleiche CRCs; Kotlin for Forge: die eingebetteten Jars haben gleichen Inhalt). Gleicher Autor auf beiden Seiten (MEGA Cells: „90“ auf Modrinth = „ninety“ auf CurseForge).

**Gateways to Eternity 5.1.0** ist die Ausnahme: Die Modrinth-Datei ist ein Neubau vom 2026-06-19, die CurseForge-Datei der ursprüngliche Build vom 2025-08-25. 12 Klassen unterscheiden sich (u. a. ein Spawn-Debug-Log, ein `FinalizeSpawnEvent`-Handler für Gateway-Mobs, die Aufbauzeit der Endless-Gateways), dazu `gateways.mixins.json` und `zh_cn.json`, Modrinth hat zusätzlich `pt_br.json`. CurseForge hat keinen neueren 1.21.1-Build. Das Pack nutzt jetzt den CurseForge-Build. **Den Endkampf in The Origin (Gateway-Wellen) mit diesem Build einmal testen.**

### Seitenangabe

`side` jeder Metadatei ist von der Modrinth-Fassung übernommen (170 both, 14 client, 6 server; Stand 2026-10-13). Das Upload-Zip wird mit `-s both` gebaut: Eine CurseForge-Installation spielt auch Einzelspieler, und dort müssen die reinen Server-Mods (LootJS, TFC Ruins, Almost Unified IE, Create: Liquid Fuel, Chunky, Simple Backups) mitlaufen.

## Werkzeuge

| Datei | Zweck |
|---|---|
| `dev/cf_convert.py` | `scan` (Jars laden, Fingerprint-Abgleich per `packwiz curseforge detect`, Rest per cfwidget), `differs` (abweichende Dateien vergleichen), `apply` (umstellen per `packwiz curseforge add`), `keep-modrinth` (gesperrte Mods zurück) |
| `dev/cf-convert.log` | jede Entscheidung mit Projekt-ID, Datei-ID, sha1-Vergleich |
| `dev/cf/export_cf.py` | Upload-Zip `dev/exports/FirmamentAges-<v>-curseforge-upload.zip` |
| `dev/cf/gen_credits.py` | Credits-Abschnitt in `dev/cf/modpack-description.md` |
| `dev/cf/make_logo.py` | `logo.png` (512²), `avatar-400.png` (400², CurseForge-Avatargröße), `banner.png` (1920×1080) |
