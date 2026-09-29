# PoC-Checkliste: Gating Dawn bis Iron Age

Stand 2026-09-30. Grundlage ist Dok. 03 §6.7 (ersetzt §5.3), aktualisiert für die Runden 2 und 3:

- MI ist gestrichen (s1 A). Deshalb entfallen der alte MI-Punkt 6 und die MI-Erze.
- Create kommt spät in der Bronze Age (s3 A). Dafür gibt es neue Punkte C6 und C18.
- SDM RMS ist nicht im Pack. Punkt 7 ist nur noch optional.
- IE Excavator (Age 5), Draconic und MekaSuit liegen außerhalb dieses PoC.

Jede Zeile hat einen Befehl oder eine Prüfung. `(Syntax prüfen)` markiert Befehle, deren Syntax aus Doku oder Quellcode abgeleitet und noch nicht im Spiel gelaufen ist. Im Pack markiert `// POC:` bzw. `# POC:` jede Stelle, die im Spiel bestätigt werden muss (`grep -rn "POC:" config kubejs`).

## 0. Aufbau

- [ ] Temurin 21 nach `tools/jdk-21/`. packwiz und `packwiz-installer-bootstrap.jar` nach `tools/`.
- [ ] `packwiz refresh` im Repo. Der Index muss die neuen Dateien unter `config/`, `defaultconfigs/` und `kubejs/` enthalten.
- [ ] `packwiz serve` starten. Dann `test-server/` aus `server/` anlegen (siehe `server/README.md`). `server.jar` ist ServerStarterJar, `eula.txt` legst Du selbst an.
- [ ] Dev-Client in Prism mit derselben `pack.toml` (localhost). Für C1 kommen zwei bis drei Accounts dazu.

## A. Nur Server (automatisierbar: Konsole/RCON, Logs, Dateien)

| # | Prüfung | Befehl / Check | Erwartung |
|---|---|---|---|
| A1 | Generator aktuell | `python dev/gen_stage_locks.py --check` | `checked 0 file(s)`, Exit 0 |
| A2 | JS-Syntax | `node --check` auf jede Datei unter `kubejs/**/*.js` | kein Fehler |
| A3 | Stage-Dateien laden | Konsole: `progressivestages validate` | `SUMMARY: 28/28 stage files valid`; im Log kein `Failed to parse stage file`, keine `Invalid ore override entry` |
| A4 | Stage-Baum | `stage tree` | `dawn → age_0 → … → age_9 → finale_won`; `ftbchunks_mapping` unter `age_2` |
| A5 | KubeJS ohne Fehler | `kubejs errors startup`, `kubejs errors server` | leer; `logs/kubejs/server.log` ohne `Error` in `firmages`-Skripten |
| A6 | Rezept-Bilanz | `reload`, dann `logs/kubejs/server.log` lesen | Zeilen „Added … / Removed …“; keine Warnung „Unable to parse recipe filter“ |
| A7 | Eigene Rezepte vorhanden | `kubejs export` (Syntax prüfen), dann im Export nach `firmages:crafting/hearthstone`, `firmages:crafting/sky_disc`, `firmages:crafting/steel_heart`, `firmages:pressing/tfc_sheet_bronze`, `firmages:crushing/rich_hematite` suchen | alle da |
| A8 | Entfernte Rezepte weg | im selben Export | kein `create:pressing/iron_ingot`, kein `create:mixing/brass_ingot`, kein `create:crafting/materials/andesite_alloy`, kein `create:crushing/raw_iron`, kein `ftbquests:book`-Rezept |
| A9 | Tags | `kubejs list-tag item c:plates/iron` und `… c:hidden_from_recipe_viewers` (Syntax prüfen) | `tfc:metal/sheet/wrought_iron` drin, `create:iron_sheet` nicht; Hidden-Tag enthält die Liste aus `00_constants.js` |
| A10 | FTB-Provider | `progressivestages ftb status` | `Provider Registered: YES`, `Previous Provider Stored` notieren (XMod) |
| A11 | Welt-Configs kopiert | neue Welt, dann `world/serverconfig/tfc-server.toml`, `immersiveengineering-server.toml`, `Mekanism/world.toml` öffnen | `enableVanillaMonstersOnSurface = true`, alle `vein_size = 0`, alle `shouldGenerate = false` |
| A12 | FTB-Chunks-Defaults | `world/serverconfig/ftbchunks-world.snbt` fehlt, also gilt `config/ftbchunks-world.snbt`; im Log keine SNBT-Fehler | Schutz aus, PvP an, Karte mit Stage, 0 Claims |
| A13 | Datapack aktiv | `datapack list` | KubeJS-Datapack aktiv (Biome-Modifier-Overrides, Item-Heat) |
| A14 | Ladezeit/Lag-Basis | `spark profiler start`, 5 min Leerlauf, `spark profiler stop` | Baseline für C3 (Issue #24) |

## C. Mit Client (1 bis 3 Accounts)

| # | Punkt (Dok. 03 §6.7 Nr.) | Schritte / Befehle | Erwartung |
|---|---|---|---|
| C1 | Team-Scope (1) | Account A und B in eine Party: `/ftbteams party create firmament` (Syntax prüfen), B einladen und annehmen, alternativ `/ftbteams force-add B firmament` (Syntax prüfen). Dann `/stage grant A age_0`, `/stage list B`. C tritt später bei. | B hat `age_0` ohne Relog, EMI schaltet um; C erbt alles |
| C2 | Karten-Stage (2) | vor `age_2` die Kartentaste (M) drücken; dann `/stage grant A age_1` und `age_2` | vorher keine Karte oder Minimap; danach vergibt `grants.js` `ftbchunks_mapping`, die Karte öffnet sich |
| C3 | Erz-Tarnung (3) | in Dawn über eine Kassiterit-Ader in Granit und Basalt fliegen (Ader per TFCGenViewer oder Creative finden); Block abbauen; Jade ansehen; Kies-Deposit prüfen. Dann `/stage grant A age_1` | vorher: Rohgestein bzw. Kies, Drop loser Stein; danach echtes Erz ohne Relog. 3.0.5 kann nur exakte IDs, deshalb ca. 1000 generierte Zeilen: `spark profiler` mit 2 Spielern über 3 Adern, Vergleich mit A14 |
| C4 | Prospektier-Leck (4) | Kupfer-Prospektierhacke über einer getarnten Zinn-Ader (vor `age_1`) | Meldet sie Zinn: Mod-Kandidat B oder Propick-Sperre (Dok. 08 §3.4) |
| C5 | TFC-Rezepttypen (5) | in Dawn Ton aufheben/knappen; in `age_1` Schwarzstahl per Amboss versuchen (Schwarzstahl ist bis `age_4` gesperrt) | Ton: kein Aufheben, keine Knapping-GUI; Schwarzstahl entsteht, fällt aber aus der Hand und ist in EMI versteckt |
| C6 | Create-Gate (neu, s3 A) | `age_1`: Andesit-Legierung aus Zink-Nugget + TFC-Andesit-Cobble; Presse, Mixer, Lüfter, Säge mit Bronzeblech; Presse macht aus Doppelbarren ein Blech; Waschen eines Deposits; Mühlstein-Kopien der Quern. `create:crushing_wheel` und `create:deployer` versuchen | Andesit-Stufe baubar, Eisen nicht nötig; Crushing Wheel und Deployer gesperrt bis `age_2`; EMI zeigt kein `create:iron_sheet` |
| C7 | SDM RMS (7) | optional; RMS ist nicht im Pack | nur testen, wenn ein Maschinen-Rezept pro Age hart gesperrt werden muss |
| C8 | Mob-Leiter (8) | Dawn-Nacht an der Oberfläche beobachten; `/summon minecraft:skeleton ~ ~ ~`; `/stage grant A age_1`; `/stage list A` | vorher: Zombies und Spinnen, Creeper werden Zombies, Skelett verschwindet; danach `mob_1` da (kumulativ, `mob_0` bleibt), Skelette spawnen; TFC-Tiere unverändert |
| C9 | Dimensionen (9) | vor `age_2`: `/execute in twilightforest:twilight_forest run tp @s 0 100 0`; vor `age_3`: dasselbe mit `minecraft:the_nether` | zurückteleportiert, Sperrmeldung |
| C10 | Quest-Screen (10) | frischer Client: Taste K; EMI nach `ftbquests:book` durchsuchen | K öffnet das Questfenster; kein Buch, kein Rezept; `drop_book_on_death` siehe `config-todo.md` Nr. 1 |
| C11 | Dawn-Stage (11) | neuer Spieler: `/stage list`; Werkbank/`tfc:wood/planks/*_workbench` rechtsklicken; im 2x2-Grid Stöcke, Feuerstarter, Stroh/Thatch, Steinwerkzeug schäften, Bretter versuchen; Advancements öffnen (L) | `dawn` + `mob_0`; keine Werkbank-GUI; nur die Whitelist crafted; EMI zeigt Stein-Knapping; keine TFC- oder Vanilla-Advancements sichtbar |
| C12 | Fallback AStages (12) | nur wenn C1, C3 (Leistung) oder C5 scheitert | gleiche Liste mit AStages 2.5.3 + Server-Scope |
| C13 | Guide-Bücher / Ponder (§5.3 Nr. 11) | TFC Field Guide und Create-Ponder-Index in `age_1` öffnen | notieren, ob Messing-Teile sichtbar sind; wenn ja, PonderJS-Ausblendung |
| C14 | EMI + JEI (§5.3 Nr. 13) | gesperrtes Item (z. B. `create:deployer` vor `age_2`) in EMI suchen, auch über JEI-Plugin-Kategorien; `create:zinc_ingot` suchen | beide unsichtbar; nach dem Grant ohne Relog sichtbar (nur der Deployer) |
| C15 | Worldgen aus (Dok. 08 §13.2) | neue Welt, `chunky radius 500`, `chunky start`; dann an 3 Orten `/fa_scan 48 create:zinc_ore`, `/fa_scan 48 create:crimsite` (auch asurine, veridium, ochrum), `/fa_scan 48 occultism:silver_ore`, `/fa_scan 48 immersiveengineering:`, `/fa_scan 48 mekanism:` | überall 0 Treffer; `/fa_scan 48 tfc:ore/` zum Vergleich > 0 |
| C16 | Stilllegen (Dok. 08 §13.10) | `age_2`: Tom's Terminal craften und setzen; `/stage grant A age_6` (zweimal, Dependency-Bypass) | `tool_toms_storage` wird entzogen, das Rezept ist gesperrt, das gesetzte Terminal läuft weiter |
| C17 | Chunkloading (Dok. 08 §13.11) | vor `age_4`: Chunk claimen versuchen; `/stage grant A age_4` (Bypass); `/ftbchunks admin extra_claim_chunks A get` | vorher 0, danach 25 Claims und 25 Force-Loads; Zusammenspiel mit dem TFC-Kalender beobachten |
| C18 | Signatur-Kette | Hearth Idol knappen (Ton), im Grubenofen brennen, Hearthstone craften; Sky Disc und Steel Heart in EMI ansehen | alle drei craftbar in ihrer Age; Idol lässt sich platzieren und brennen |
| C19 | Vereinheitlichung | in EMI `c:plates/iron`, Kupfer-Barren und Zink suchen; Create-Rezepte ansehen | ein TFC-Blech pro Metall, keine Create-Bleche, kein Create-Zinkbarren, Dusts schmelzen im TFC-Gefäß |
| C20 | Age-Ansage | `/stage grant A age_1` | Titel „Age 1: Bronze Age“, Untertitel, Sound, Feuerwerk bei allen Online-Spielern, Chatzeile aus `unlock_message` |
| C21 | Reparatur | Stage per Befehl setzen, dann `/fa_reconcile` und `/fa_stages` | Hilfs-Stages und Mob-Leiter passen zur höchsten Age |

## Entscheidungen, die der PoC liefert

- Engine bleibt ProgressiveStages 3.0.5, oder Wechsel auf AStages, wenn C1, C3 (Leistung) oder C5 scheitern.
- Erz-Tarnung: exakte IDs reichen von der Leistung her, oder auf PS 3.1.0 warten (`[[blocks.overrides]]` mit Block-Tags, bisher nur auf `master`).
- Mob-Leiter kumulativ (Standard in `grants.js`) statt Tausch; Begründung im Kopf von `mob_1.toml`.
- Prospektier-Leck: Mod-Kandidat B oder Item-Sperre.
- `force_load_mode` `"never"` oder `"always"` (`config-todo.md` Nr. 20).
