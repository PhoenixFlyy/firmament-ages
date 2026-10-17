# Offene Punkte

Stand 2026-10-17, Pack 1.0.1, firmages-core 1.0.1. Hier steht nur, was nach 1.0 wirklich offen ist: Entscheidungen, die
Felix trifft, Prüfungen im Client und Ideen für die Zeit nach 1.0. Erledigtes früherer Runden steht in der Git-Historie
dieser Datei (bis Commit `7689802`) und mit Beleg in `dev/poc-results.md`; unten nur, was die Runden F1 und G1
geschlossen haben.
Eine Zeile pro Punkt: was, wo, wer entscheidet oder erledigt. Erledigt: Zeile nach unten verschieben, mit Beleg.

## Entscheidungen (Felix)

Keine offenen Entscheidungen: Felix hat die neun Fragen von 1.0.0 und die Codes M1A, T1A und Q1A am 2026-10-17 beantwortet, jede steht mit Beleg unter „Erledigt in Runde G1“.

## Bestehende Welten (Server)

| Was | Wo | Wer |
|---|---|---|
| Vanilla-Herzen nur in neuen Welten: eine Welt von 1.0.0 behält TFCs Gesundheit (Default 0,85) in ihrer Welt-Config; bei gestopptem Server `nutritionMinimumHealthModifier`, `nutritionDefaultHealthModifier` und `nutritionMaximumHealthModifier` von Hand auf `1.0` setzen | `<Welt>/serverconfig/tfc-server.toml` `[mechanics.player]` | Felix |
| Welten, die älter als drei Tage sind und keine Tutorial-Wayshrine haben (Suche bis 1.0.0 mit nur 16 Peilungen, oder vor 0.12.0 angelegt): der Dawn-Keystone „A Shrine on the Road“ ist dort unerfüllbar, und „The First Spark“ wartet auf ihn. Felix' Testwelt ist neu; eine solche Welt bräuchte die Quest von Hand abgehakt | Questbuch, firmages-core `WayshrineTutorial` | Felix, falls eine solche Welt in Dawn steht |

## Im Client prüfen (Felix)

| Was | Wo | Wer |
|---|---|---|
| **Die „1.0.1 Checkliste“** oben in `dev/client-test-brief.md` (Runde G1: Betrachter und Knöpfe nach Age, nur Kreativ ausgenommen, Jade „Unknown“, keine Hinweistexte, Vanilla-Herzen, Quest-Panel, Schreinbild, neue Dawn-Quests, Backups), je Zeile Schritte und Erwartung | `dev/client-test-brief.md` | Felix |
| **Die „1.0 Checkliste“** darunter: alle offenen Client-Punkte von 0.10.0 bis 1.0.0 nach Age geordnet, mit Schritten und Erwartung | `dev/client-test-brief.md` | Felix |
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
| Generierte Breitstämme (`branch_direction none` in TFCs `sequoia_large` und `douglas_fir_large`) droppen vermutlich den TFC-Stamm statt des Vanilla-Klassenstamms (HW1A); bestand schon vor G1, nie geprüft | `dev/gen_wood_classes.py`, Loot der Stämme | Claude |
| Prism-Freunde mit einer alten `firmages-client.toml` haben den neuen Chat-Blocklist-Eintrag nicht: JourneyMaps Join-Zeile ist dort einmal zu sehen, bis die Datei neu geschrieben wird | `config/firmages-client.toml` `[chat]` | Felix, falls es stört |

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

## Erledigt in Runde G1 (mit Beleg)

| Was | Beleg |
|---|---|
| Eigene Schrift (ganzes Spiel oder nur Titel)? Antwort: keine eigene Schrift | Entscheidungszeile „G1: Keine eigene Schrift“ |
| Ernährungs-Untergrenze 0,85 oder 1,0? Antwort: keine TFC-Gesundheit, Vanilla-Herzen bei jeder Kost | `config/tfc-client.toml` `enableHealthBar = false`; `defaultconfigs/tfc-server.toml` `[mechanics.player]` Minimum, Default und Maximum je `1.0`; Zeile „G1: Vanilla-Herzen statt TFC-Gesundheit“ (bestehende Welten behalten ihre Werte in `<Welt>/serverconfig/tfc-server.toml`) |
| Chunk-Laden ohne Spieler? Antwort: `never` | `config/ftbchunks-world.snbt` `force_load_mode: "never"` unverändert, gegen FTB Chunks 2101.1.22 gelesen; `CHUNK_QUOTA` ab `age_4` bleibt sinnvoll; Zeile „G1: Chunk-Laden nur mit Team online“ |
| Mob-Leiter kumulativ? Antwort: bestätigt | `FA.MOB_LADDER_CUMULATIVE = true`, Kopf der zehn `mob_N.toml`; Zeile „G1: Mob-Leiter kumulativ bestätigt“ |
| AdvancedAE Strength Card sperren? Antwort: sperren | `advanced_ae:strength_card` in `[hidden]` (`dev/age_map.toml`), Tag `age_items/disabled` und `HIDDEN_ITEMS`; Zeile „G1: AdvancedAE Strength Card bleibt gesperrt“ |
| Backups ohne erzwungenes Speichern? Antwort: so lassen, Timer 30 bis 60 Minuten nach Vorbild bekannter Packs | `config/simplebackups-common.toml` `timer = 60`, `backupsToKeep = 12`, `saveAll = false`; Zeile „G1: Backups alle 60 Minuten, zwölf Stück“ (mit Vergleich und Klarstellung zu den „fünf Minuten“) |
| Ars-Elemental-Blitzteile? Antwort: versteckt lassen | `[derived_hidden]` in `dev/age_map.toml`, kein Pack-Rezept für die Schote; Zeile „G1: Ars-Elemental-Blitzteile bleiben versteckt“ |
| Jade-Namen gesperrter Blöcke? Antwort: geheimnisvolles Zeichen und „Unknown“ | firmages-core 1.0.1 (`FirmagesJadePlugin`, `UnknownBlocks`, Sigil aus `dev/gen_textures.py` `MOD_GUI`), mit Pack 1.0.1 gemergt; `UnknownBlocksTest`; Client-Prüfung offen (1.0.1 Checkliste 5); Zeile „G1: Jade zeigt „Unknown“ bei gesperrten Blöcken“ |
| Last des Arkan-Katalysators? Antwort: ohne Last | firmages-core §41.3 unverändert; Zeile „G1: Arkan-Katalysator ohne Last“ |
| Neue Regel: keine Erklärtexte (Entfernen, Ersetzen oder Freischalten eines Features bekommt keinen Hinweistext; Sperr-Rückmeldungen bleiben) | `dev/style-guide.md` Abschnitt „Keine Erklärtexte“, `dev/kontext.md` §2; Zeile „G1: Keine Erklärtexte“ |
| Felix sah in Dawn EMI (Operator im Einzelspieler mit Cheats) | Betrachter-Ausnahme nur noch Kreativ: `ViewerGate.open(ageAllows, creative)`, `InventoryButtons`, `InventoryButtonGate`; `gradlew build` 280 Unit-Tests, `runGameTestServer` 125 von 125; Client-Prüfung 1.0.1 Checkliste 1 bis 3; Zeile „G1: Betrachter-Ausnahme nur Kreativ“ |
| Mods auf die neueste passende Version (Felix) | acht Updates, Supplementaries und Create: Extra Gauges gepinnt; keine Mod zurückgesetzt; Belege in `dev/poc-results.md` „1.0.1 Runde G1“; Zeile „G1: Mod-Updates auf die neueste passende Version“ |
| Minimap (Felix M1A) | JourneyMap 6.0.9 und JourneyMap Integration 1.9.1 ab der Eisenzeit (`ui.mapFromAge`, firmages-core `MapGate`, U `MapGateTest`), Theme „Firmament“, kein Radar, keine Todespunkte; Zeilen „G1: JourneyMap ist die Karte“ und „G1: JourneyMap ohne Todespunkte“; Client-Prüfung 1.0.1 Checkliste |
| Baumfällen (Felix T1A) | Enhanced Falling Trees 0.7.0, TFC-Fällung aus (`#tfc:logs_that_log` leer), `mixin/eft/DefaultTreeMixin` (U `TreeFamilyTest`); Zeilen „G1: Enhanced Falling Trees statt TFC-Fällung“ und „G1: Steinaxt fällt ohne Abzug“; Client-Prüfung 1.0.1 Checkliste |
| Questbuch eingefroren (Felix Q1A) | `dev/gen_quests.py` nur mit `--unfreeze`, SNBT ist die Quelle, `dev/quests-editing.md`; Zeile „G1: Questbuch eingefroren“ |
