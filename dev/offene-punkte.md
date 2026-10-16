# Offene Punkte

Stand 2026-10-17, Pack 1.0.0, firmages-core 1.0.0. Hier steht nur, was nach 1.0 wirklich offen ist: Entscheidungen, die
Felix trifft, Prüfungen im Client und Ideen für die Zeit nach 1.0. Erledigtes früherer Runden steht in der Git-Historie
dieser Datei (bis Commit `7689802`) und mit Beleg in `dev/poc-results.md`; unten nur, was Runde F1 geschlossen hat.
Eine Zeile pro Punkt: was, wo, wer entscheidet oder erledigt. Erledigt: Zeile nach unten verschieben, mit Beleg.

## Entscheidungen (Felix)

| Was | Wo | Wer |
|---|---|---|
| Eigene Schrift (ganzes Spiel oder nur Titel)? Logo und Knöpfe sind eigene Bilder, Texte in der Minecraft-Schrift. | FancyMenu, Resource-Pack | Felix |
| Ernährungs-Untergrenze: TFC-Startwert 0,85 (etwa 850 Leben) oder volle Gesundheit ab Start (1,0)? | `defaultconfigs/tfc-server.toml` `nutritionDefaultHealthModifier` | Felix |
| Chunk-Laden ohne Spieler: `"never"` (nur solange jemand aus dem Team online ist, so geschrieben) oder `"always"` (Fabriken laufen offline, der TFC-Kalender steht dann)? | `config/ftbchunks-world.snbt` `force_loading.force_load_mode` | Felix |
| Mob-Leiter kumulativ (`mob_0` bleibt, neue Mobs kommen dazu) statt Tausch wie in Dok. 08; so seit 2026-09-30, Begründung im Kopf von `mob_1.toml`. Bestätigen oder ändern? | `config/progressivestages/stages/mob_*.toml`, `kubejs/server_scripts/stages/grants.js` | Felix |
| AdvancedAE Strength Card wertet nur die gesperrte Quantum-Rüstung auf: sperren oder lassen? | `dev/age_map.toml` | Felix |
| Backups ohne erzwungenes Speichern (`saveAll = false` seit 0.9.1): ein Backup hält den Stand des letzten Autosaves, also bis fünf Minuten alt, und fällt ein Backup in einen Autosave, kann eine Region-Datei halb geschrieben im Zip landen (Simple Backups 4.0.30 schaltet das Speichern nicht ab). `saveAll = true` speichert vorher, bringt aber „Could not save data“ (AccessDeniedException beim Zippen, Live-Sitzung 2026-10-14) zurück. So lassen? | `config/simplebackups-common.toml`, `dev/decisions-while-away.md` („Backups ohne erzwungenes Speichern“) | Felix |
| Ars Elemental: Blitz-Teile (Discharge-Glyphe, Shock-Faden) und Flashjack/Sirene bleiben versteckt, weil Flashpine-Schoten und die Splitter nur aus den abgeschalteten Elementar-Biomen kommen; ein Pack-Rezept für die Schote würde sie zurückholen | `recipes/age_3/ars_addons.js` | Felix |
| Jade zeigt ab der Bronzezeit die Namen gesperrter Blöcke (nichts davon gibt ein Item vor seiner Age). Nach Age filtern wäre eine Java-Änderung im Core mit Client-Test | `FirmagesJadePlugin`, Zeile „F1 Gating: bleibt wie es ist“ | Felix |
| Arkan-Katalysator liest nur die Drehzahl der Welle und nimmt keine Create-Last; soll er Last ziehen (eigener kinetischer Block)? | firmages-core §41.3 | Felix (Wunsch?) |

## Im Client prüfen (Felix)

| Was | Wo | Wer |
|---|---|---|
| **Die „1.0 Checkliste“** oben in `dev/client-test-brief.md`: alle offenen Client-Punkte von 0.10.0 bis 1.0.0 nach Age geordnet, mit Schritten und Erwartung | `dev/client-test-brief.md` | Felix |
| Titelbild: nach dem ersten Blick auf das Shader-Sternbild `show_compile_errors = true` wieder auf `false` | `config/fancymenu/customization/title_screen_layout.txt` | Felix sieht, Claude stellt um |
| Industriezeit-Quest „The Thresher“ schließt beim Blick auf einen geformten Thresher ab; Loot-Crate-Opener und Task-Screen-Konfigurator fehlen in EMI | Questbuch, EMI | Felix |
| IE-Handbuch-Vorschau von Mahlwerk, Webstuhl, Lichtbogenofen, Schaufelrad, Bagger, Koker und Pumpjack zeigt den TFC-Stahlblock | IE-Handbuch | Felix |
| Sperrtexte: gesperrtes Item zeigt „Something Unknown“ und eine graue Zeile „Not of this Age“, Hotbar-Zeile „… Their Age has not come.“ | `config/progressivestages/progressivestages.toml` `[messages]` | Felix |
| Abenteuer-Wege in einer neuen Welt: Flugschreiber in der Mars-Tempelkiste, Reaktor gibt Chaos-Fragmente, gebaute Stahl-Kanonenkammer per Cannon Builder und Hitze, Draconium-Adern in Granit, Diorit, Gabbro ab der Raumfahrtzeit | neue Welt | Felix |
| n7-Deko-Sichtprüfung: Meißelzyklus, Verbundtexturen Panel und Column, Rechiseled-Gruppen; Vanilla-Holz-Deko nur in Stilen mit TFC-Kopie | `dev/client-test-brief.md`, Abschnitt „n7-Deko“ | Felix |
| Alternator an einer befeuerten Dampfmaschine und an einem Dieselmotor (168 FE/t bei 256 RPM; Wasserrad allein 0), `/firmages balance alternator <pos>` | `dev/client-test-brief.md` (0.11.0 Punkt 6) | Felix |
| Energized Smelter gibt aus Eisenstaub Schmiedeeisen (eigenes `mekanism:smelting`-Rezept) | `recipes/unify_iron.js` | Felix |
| N7-Welt: Cacophony (Vögel nach Klima und Jahreszeit, Wind), Particle Rain in Wüste und Regenwald, Cosy-Critters-Vögel, Subtle Effects ohne Herzschlag; Vulkan ohne Blockschaden, Mineralkrusten an heißen Quellen | `config/`, `config/tfcvolcanoes-startup.toml` | Felix |
| Startzeit-Test: `mixin.perf.dynamic_resources=true` in `config/modernfix-mixins.properties`, Create-Kontraption, Framed-Blöcke und ein Sable-Schiff ansehen; Startphasen vorher/nachher mit `python dev/check_log.py --client` | `config/modernfix-mixins.properties` | Felix testet, Claude wertet aus |
| Backup zurückspielen nach `server/README-SERVERPACK.md` (vier Schritte) einmal echt durchspielen; bisher nur die Zip-Struktur geprüft | Server | Felix oder Claude auf einer Kopie |
| Team-Übernahme auf einer bestehenden Welt: der erste Spieler gründet die Party „Firmament“ mit allen Ages der Welt, ein zweiter kommt ohne Einladung dazu; belegt nur durch `TeamGameTests` | firmages-core, `server/README-SERVERPACK.md` | Felix |
| Startkomplex: Dieseleimer per Hand in die Steuerung, mit freigeschalteter Age | firmages-core `LaunchControlBlock.useItemOn` | Felix |
| Ruinen-Truhen: in Dawn nur Dawn-Kleinkram (ProgressiveStages `stage_filter`), Trichter unter der Truhe ziehen nichts Gesperrtes | `kubejs/data/firmages/loot_table/chests/ruin_*.json` | Felix |
| Felix' Test-Server hält veraltete Dateien, die der packwiz-Installer nicht löscht (`mods/` mit alten Jars, `kubejs/data/twilightforest`, `kubejs/server_scripts/finale`): vor dem nächsten Start `kubejs/` löschen und alte Jars entfernen, dann synchronisieren | `test-server/` | Felix |

## Nach 1.0 (Ideen und Beobachtung)

| Was | Wo | Wer |
|---|---|---|
| Pacing-Modell ins Repo holen (`scratchpad/b2/pacing/pacing.py`, Treiber `scratchpad/b2int/drive.py`, nur im Sitzungs-Scratchpad) als `dev/pacing.py` mit `--players` und `--dump-dir`; mit einem gestoppten Durchgang von zwei Spielern eichen und danach entscheiden, ob die Machine Route der Arkanzeit (Modell 0,63) einen dritten Schritt bekommt | `dev/`, `dev/gen_quests.py` | Claude, nach Felix' Durchgang |
| Questbuch-Ideen: „Terminal Habits“ verspricht in der Eisenzeit mehr, als das Terminal ohne Connector kann (kürzen?); Firmament-Ader mit dem Create-Bohrer automatisieren; „launch 16 probes“; FE-Nachweise als `forge_energy`- oder Task-Screen-Aufgaben; Automatisierungsnachweise per Task-Screen; „Data Model: Tier 3“ nimmt jedes Datenmodell | `dev/gen_quests.py` | Claude |
| Wayshrines: Dichte je Seed streut (Vulkangürtel-Seed 18 statt 7 bis 11); Opfergaben und Riten von age_2 bis age_6 nur per GameTest geprüft | `dev/gen_wayshrines.py` `TYPE_SPACING`, SPEC §28 | Claude, falls es im Spiel stört |
| Mekanisms Teleporter und Ars-Warp in The Origin sperren; Trophäen-Denkmal nach dem Sieg | firmages-core | Claude |
| Unverifizierte Config-Keys: TFC + IE Quarz-Geode aus, Enchantable Machinery Fortune/Looting-Blacklist, Bits 'n' Bobs Schwungrad-Speicher aus, Mechanical Spawner Zufalls-Fluid aus, Mekanism Additions Baby-Mobs aus | Configs | Claude |
| Sieben IE/IP-Multiblöcke geben beim Abbau den gesperrten `immersiveengineering:storage_steel` zurück | `dev/gen_multiblock_templates.py` | Claude |
| Helfer-Items (Tom's Storage-Terminal, Alloy Kiln, Kondensatoren, Mechanical Spawner) verlieren ihr Bau-Rezept in einer späteren Age; Nicht-Operatoren lesen dann „Its making belongs to a later Age“ | `config/progressivestages/stages/tool_*.toml` | Claude |
| Werkzeuge: `derive_ages.py --scan` liest Block-Prädikate von Features als platzierte Blöcke (Vanilla-Eichensetzling in der Arkanzeit); die R-Prüfungen rechnen Dawn noch mit Raster statt Bodenhandwerk (D-1 sichert es ab); Ark-Vorlagen laden TFC-Blöcke in GameTests ohne TFC als Luft | `dev/derive_ages.py`, `dev/poc_analyze.py`, `ArkGameTests` | Claude |
| Messungen live: Verbraucher (Mek Purification, Injection, Dissolution, SPS, Digital Miner voll aufgerüstet, IE Crusher, Arc Furnace, Excavator), Reload-Zeiten (`firmages reload` dreimal bei age_9; ein voller `/reload` hielt den Server-Thread in Runde F1 15,3 s an), erster Login (6 s Ruckler), Ethen-Kette mit vollen Upgrades (Spaltung überspringen?), Mekanism-Strahlung und Reaktor-Balance | `dev/harmonie-messung.md`, `fuels.json` | Claude |
| Apotheosis-Weltstufe pro Age (Haven bis Pinnacle): Befehlssyntax unbekannt, nur Platzhalter; Apotheosis-Tiefendungeons in Dimensionen mit Boden ab y = 0 („Empty height range“, Rauschliste) | `kubejs/server_scripts/stages/grants.js`, `kubejs/data/apotheosis/neoforge/biome_modifier/` | Claude |
| Starfall: Ausschluss um FTB-Chunks-Claims ohne Test, Tarnung vor age_2 nur aus den Stage-Overrides | firmages-core `sky/`, `compat/ftbchunks` | Claude |
| `create_dragons_plus:sandpaper_polishing/*`: die Zahl dieser erzeugten Rezepte schwankt zwischen Boots um eins; G-2 kann daran rot werden, ohne dass das Gate etwas falsch macht | `poc_analyze.py` G-2 | Claude |
| Debug-Befehle `/fa_dump` und `/firmages selftest all` halten den Server-Thread 2 bis 4 s an („Can't keep up“); Log-Prüfungen laufen darum auf Boots ohne sie | `kubejs/server_scripts/debug/dump.js`, `command/SelfTest*` | Claude, falls es stört |
| Mod-Rauschen: JEI schreibt sechs „Found a broken recipe“ für Chemixer und APT (Client); Moonlight hält beim Stop 15 Threads offen. Beide in den Rauschlisten | Evolved Mekanism, JEI, Moonlight | Mod-Autor |
| GUI-Resourcepacks mit anderer Grundfarbe zeigen in Dawn ein graues Rechteck über dem Feld (#C6C6C6 fest); das Pack liefert keins mit | firmages-core `client/immersion/GridLockClient` | Claude, falls ein GUI-Pack dazukommt |
| Planungsdokumente: `08-Ages-Design.md` „§0 Stand“, `10-Modliste.md` aus `mods/*.pw.toml` erzeugen, `12-Walkthrough.md` auf einen Verweis auf `docs/WALKTHROUGH.md` kürzen | `../Modpack-Planung/docs/` | Claude |
| Der TFC-GameTest-Lauf (`-PgametestTfc`) stürzt in `IsleGameTests.shapeAndSkyLight` ab (NPE in `LightEngine.getLightValue`); der Standardlauf (125 von 125) ist grün | `src/gametest/.../IsleGameTests.java` | Claude |

## Erledigt in Runde F1 (mit Beleg)

| Was | Beleg |
|---|---|
| Barren-Verdopplung über TFC-Metallblöcke in `c:storage_blocks/*` (4 Bleche -> 72 Barren) | `recipes/metal_values.js` nimmt 42 Rezepte mit dem Vanilla-Preis; L-8 auf dem echten Gate-aus-Dump von 1.0.0: PASS (0.14.0: 19 Kreise in 14 Metallen) |
| Mekanism Neutron Activator nie erreichbar | Fusion Neutron Activator mit Pack-Rezept in age_8, EMI-Arbeitsstation; `derive_ages.py --check` auf dem echten Dump: age_8; Quest „Fusion Neutron Activator“ |
| `ars_nouveau:wixie_charm` nie erreichbar | Apparat-Rezept mit Beschwörungsessenz, age_3 auf dem echten Dump; Quest „Wixie Charm“ |
| Weapons Grade Naquadah ohne Quelle, 30 Stargate-Journey-Items verborgen | `firmages:arc_furnace/naquadah`; auf dem echten Dump alle 30 aus `[derived_hidden]` heraus (age_9) |
| Strukturen und Erze späterer Ages in früheren Welten, Tierprodukte gesperrt | X-1 bis X-4 auf dem echten Dump PASS |
| Absturz nach `/reload` (`StackOverflowError` in TFCs Wärmeabfrage) | `mixin/tfc/ItemStackHooksMixin` + `LookupGuard` (SPEC §42); JUnit; `/reload` bei age_9 auf der Kopie ohne Absturz (die Injektionskammer mit Erz bleibt Client-Punkt 55) |
| Reload verliert 148 Rezepte (147 `everycomp:mcf/*`, `adpoles:redstone_pole`) | Ursache DiagonalBlocks (tauscht beim TagsUpdated den Block der Zaun-Items, Moonlight findet die Eichen-Vorlage nicht mehr): Macaws Eichen-Zäune und -Hecke in `diagonalfences:non_diagonal_fences`; Redstone-Stange mit 32 (Override `recount`). Kopie: 43.709 Rezepte vor und nach `/reload`, gate aus und an, 0 Every-Compat-Fehler |
| Blöcke ohne Funktion (Alloyer, Alloying Factories, Walkie-Talkie) | `[hidden]` in `dev/age_map.toml`; der Chemixer nimmt den chemischen Infuser; Q-1 0 ohne Quelle |
| Welt-Abkürzer der Vanilla-Eimer-Items in `poc_analyze.py` | `world_bucket()` in beiden Startsets, mit `derive_ages.py` geteilt; poc 134 PASS |
| Echter Gate-aus-Dump statt Vorhersage | Boot 1b der Integration F1; `derive_ages.py --check` up to date, Q-1 523 Aufgaben mit Quelle; Basis in `dev/data/baseline-1.0.0/` |
| Alte Origin-Arenen behalten den Fusionskern im Arena-DHD | `OriginService.migrateDhdCore`; GameTest `oldReturnGateLosesItsDhdCore` |
| Java-Fallback-Texte, Tutorial-Wayshrine nach hartem Kill doppelt | firmages-core 1.0.0 |
| Kopie-Prüfungen der Runde: `sequenced_assembly` von Simulated und Create Factory Logistics, Createdeco-Schiffscontainer, Flüssigkeits-Tags `age_fluids` | alle mit Age auf dem echten Dump (age_2 bzw. age_4); `gen_stage_locks.py` schreibt `age_fluids` je Stage |
| Bekannte Lücken Apothic-Tisch und Blaze-Template (Machine Route der Arkanzeit) | Release-Review 1.0.0: Tisch mit Arkan-Edelsteinen, Template als Kistenbeute (`CODE_DROPS`), `KNOWN_GAPS` leer; Q-1 0 ohne Quelle; Zeile „Release-Review 1.0.0: Machine Route ohne Diamant“ |
| Splitter und Kristalle vor ihrer Maschine (Informationszeit-Straße „More Metals“) | `LATE_LOADED` im Walk, Q-1 mit Entweder-oder-Quests; Aufgaben in „Ore Times Four“ und „Ore Times Five“ |

## Erledigt in der Abschlussprüfung 1.0.0 (mit Beleg)

| Was | Beleg |
|---|---|
| Sauce (Bibliothek von Ars Elemental) warf beim ersten Start `Cannot get config value before config is loaded` aus `SauceConfig.onReload` | Ursache: der Config-Watcher meldet das erste Schreiben von `sauce-startup.toml` als Reload, `onReload` liest dabei den noch nicht geladenen Server-Wert; `config/sauce-startup.toml` jetzt im Pack. Frische Installation, Boot C: keine Sauce-Zeile, die Datei unverändert (`dev/poc-results.md`, „1.0.0 Abschlussprüfung“) |
| `everycomp-entries.toml` korrigiert | nur Felix' alte Datei aus `test-server/` (Einträge für Cataclysm und Twilight Forest); auf einer frischen Installation schreibt Every Compat die Datei ohne Korrektur |
| Every-Compat-Schmuckschilder mit einer Rezept-ID je Namensraum (G-2 schwankte um ein Rezept) | `deco_c1.js` entfernt `everycomp:bcl/tfc/fancy_sign` und `everycomp:bcl/afc/fancy_sign`; poc 134 PASS auf den echten Dumps |
| Tutorial-Wayshrine fehlte auf 2 von 5 neuen Welten (sechzehn Peilungen auf einem Abstand) | `WayshrineTutorial`: fünf Abstände auf 32 Peilungen, Chunks per Ticket, Wiederholung bis drei Tage (SPEC §28.4); GameTest `tutorialSearchTriesEveryDistance`, JUnit; drei neue Welten: platziert 675, 300 und 424 Blöcke vom Spawn, `check_log.py` remaining 0 (Boot mit Selftest: nur dessen „Can't keep up“) |
