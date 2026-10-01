# CurseForge-Bereitschaft

Stand 2026-10-01, Pack 0.5.0, 193 Mods. Regel von CurseForge ([Moderation Policies](https://support.curseforge.com/support/solutions/articles/9000197279-project-and-modpack-moderation-policies), Abschnitt „Non-CurseForge Mods“): Ein Modpack darf nur Mods enthalten, die auf CurseForge liegen oder auf der Liste [Non-CurseForge Approved](https://docs.google.com/spreadsheets/d/176Wv-PZUo9hFxy6oC6N8tWdquBLPRtSuLbNK-r0_byM/edit#gid=0) stehen. Neue Einträge beantragt man über das [Antragsformular](https://forms.monday.com/forms/a46faa60c3d9cf097763311811db5bbd?r=use1) (verlinkt im Kopf der Liste). Mods, die ein CurseForge-Projekt haben, gehören ausdrücklich **nicht** auf die Liste.

## Ergebnis

| | Anzahl |
|---|---|
| Mods mit CurseForge-Datei im Upload-Zip (Manifest) | 190 |
| davon schon vorher CurseForge (FTB, Twilight Forest, Torque Link …) | 8 |
| davon umgestellt von Modrinth (`dev/cf_convert.py`, Protokoll `dev/cf-convert.log`) | 182 |
| … byte-gleich (CurseForge-Fingerprint-Treffer, sha1 gleich) | 170 |
| … gleiche Version, andere Datei (nur `MANIFEST.MF` bzw. Zip-Packung, gleicher Code) | 11 |
| … gleiche Version, **anderer Code** | 1 (Gateways to Eternity) |
| Mods ohne CurseForge-Projekt | 2 (MekaTFC, TFC Ore Tooltips) |
| eigene Mod, noch nicht auf CurseForge | 1 (firmages-core) |
| Jars in `overrides/mods/` des Upload-Zips heute | 3 |
| Jars in `overrides/` nach Schritt 1 und 2 unten | 0 |

Keine Mod hatte auf CurseForge nur eine andere Versionsnummer: jede gefundene Datei ist dieselbe Version wie die Modrinth-Datei des Packs.

## Was Du entscheiden musst

| Mod | Lizenz (Modrinth / Repo) | Auf der Approved-Liste? | Status | Vorschlag |
|---|---|---|---|---|
| **MekaTFC** 0.1.0 (zhixuan45), [Modrinth](https://modrinth.com/mod/mekatfc), [GitHub](https://github.com/zhixuan45/MekaTFC) | All Rights Reserved (Modrinth); das Repo hat keine LICENSE-Datei | nein | **request-needed**, aber bei ARR nur mit schriftlicher Erlaubnis des Autors | Am einfachsten: zhixuan45 bitten, die Mod auf CurseForge hochzuladen (dann normaler Manifest-Eintrag), sonst um Erlaubnis bitten und sie mit dem Antrag einreichen. **replace-candidate**, falls er nicht antwortet: eine CurseForge-Mod mit Osmium in TFC-Gesteinen gibt es nicht (Suche „tfc mekanism“, „tfc osmium“ ohne Treffer). Ersatz wäre eigene Datenarbeit: eine TFC-Erzader mit Mekanisms eigenem `mekanism:osmium_ore` (ohne Gesteinsvarianten und ohne die drei Güten) und die Osmium-Rezepte von `ore_ladder.js` darauf umstellen. MekaTFC liefert heute die Osmium-Erze (arm, normal, reich, in jedem TFC-Gestein), die die Information Age braucht. **Nicht streichen ohne Ersatz.** |
| **TFC Ore Tooltips** 1.2.0 (Grimm147), [Modrinth](https://modrinth.com/mod/tfc-ore-tooltips) | MIT (Modrinth); kein Repo angegeben | nein | **request-needed** (MIT erlaubt das Weitergeben, der Antrag sollte durchgehen) | Antrag stellen. Fällt er durch: **drop**. Die Mod ist reine Client-Hilfe (Gesteine und Höhen im Erz-Tooltip); das TFC-Feldbuch zeigt dieselben Wirtsgesteine, und die Stationsmatrix hängt an ihr nicht. Eine CurseForge-Alternative mit derselben Funktion gibt es nicht. |
| **firmages-core** 0.5.0 (eigene Mod) | MIT (neu, `mod/firmages-core/LICENSE`) | – | eigenes Projekt | Zuerst als eigenes Mod-Projekt hochladen (`dev/cf/firmages-core-description.md`), danach per `packwiz curseforge add` ins Pack. Kein Antrag nötig. |

Bis die beiden Anträge durch sind, lehnt die Moderation ein Upload-Zip mit ihren Jars ab. Zwei Wege: warten, oder die erste Version ohne sie hochladen (MekaTFC fehlt dann für die Information Age, also nur mit Ersatz).

## Sonderfälle der Umstellung

### Sechs Mods bleiben im Repo auf Modrinth

Die CurseForge-Dateien von **Alternating Flux, Create Aeronautics, Create: Design n' Decor, Create Factory Logistics, Entity Culling** und **TFC Ruins** sind für Drittprogramme gesperrt („excluded from the CurseForge API“). packwiz-installer (Prism-Instanzen der Freunde, der Server) bricht bei ihnen mit „must be downloaded manually“ ab; gefunden mit einem Testlauf gegen `packwiz serve` (Client- und Server-Seite). Darum stehen sie in `mods/` weiter als Modrinth-Metadateien (dieselben Jars, Fingerprint-Treffer). Ihre CurseForge-Metadateien liegen in `dev/cf/swap/`; `python dev/cf/export_cf.py` tauscht sie für das Upload-Zip ein. Nach dem Tausch laufen beide Installer-Testläufe durch: Client 410 von 410 Dateien, Server 401 von 401, alle Hashes gleich (`dev/verify_install.py dir`).

### Gleiche Version, andere Datei (CurseForge-Datei übernommen)

Elf Mods: Create: Connected, Create: Liquid Fuel, FastSuite, Kotlin for Forge, KubeJS, KubeJS Create, KubeJS Mekanism, MEGA Cells, Sophisticated Backpacks, Sophisticated Backpacks Create Integration, Sophisticated Core. Unterschied nur in `META-INF/MANIFEST.MF` (Zeitstempel der Builds) bzw. in der Zip-Packung (MEGA Cells: gleiche Einträge, gleiche CRCs; Kotlin for Forge: die eingebetteten Jars haben gleichen Inhalt). Gleicher Autor auf beiden Seiten (MEGA Cells: „90“ auf Modrinth = „ninety“ auf CurseForge).

**Gateways to Eternity 5.1.0** ist die Ausnahme: Die Modrinth-Datei ist ein Neubau vom 2026-06-19, die CurseForge-Datei der ursprüngliche Build vom 2025-08-25. 12 Klassen unterscheiden sich (u. a. ein Spawn-Debug-Log, ein `FinalizeSpawnEvent`-Handler für Gateway-Mobs, die Aufbauzeit der Endless-Gateways), dazu `gateways.mixins.json` und `zh_cn.json`, Modrinth hat zusätzlich `pt_br.json`. CurseForge hat keinen neueren 1.21.1-Build. Das Pack nutzt jetzt den CurseForge-Build. **Den Endkampf in The Origin (Gateway-Wellen) mit diesem Build einmal testen.**

### Seitenangabe

`side` jeder Metadatei ist von der Modrinth-Fassung übernommen (171 both, 15 client, 6 server). Das Upload-Zip wird mit `-s both` gebaut: Eine CurseForge-Installation spielt auch Einzelspieler, und dort müssen die reinen Server-Mods (LootJS, TFC Ruins, Almost Unified IE, Create: Liquid Fuel, Chunky, Simple Backups) mitlaufen.

## Werkzeuge

| Datei | Zweck |
|---|---|
| `dev/cf_convert.py` | `scan` (Jars laden, Fingerprint-Abgleich per `packwiz curseforge detect`, Rest per cfwidget), `differs` (abweichende Dateien vergleichen), `apply` (umstellen per `packwiz curseforge add`), `keep-modrinth` (gesperrte Mods zurück) |
| `dev/cf-convert.log` | jede Entscheidung mit Projekt-ID, Datei-ID, sha1-Vergleich |
| `dev/cf/export_cf.py` | Upload-Zip `dev/exports/FirmamentAges-<v>-curseforge-upload.zip` |
| `dev/cf/gen_credits.py` | Credits-Abschnitt in `dev/cf/modpack-description.md` |
| `dev/cf/make_logo.py` | `logo.png` (512²), `avatar-400.png` (400², CurseForge-Avatargröße), `banner.png` (1920×1080) |
