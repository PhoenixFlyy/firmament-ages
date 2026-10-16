# Offene Punkte

Alle offenen Punkte an einer Stelle, Stand 2026-10-16 (0.11.0, 1.0 Runde C1 mit Review-Fixes). Gesammelt aus `dev/config-todo.md` (archiviert),
den „Offen“-Listen in `dev/client-test-brief.md`, „Open points“ in `dev/archive/quests-notes.md` und den „Open points“-Abschnitten
in `dev/poc-results.md`; jeder Punkt wurde gegen den aktuellen Stand geprüft, Erledigtes steht unten mit Beleg.
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
| Bekannte Lücken mit Regel-Age: Apothic-Enchanting-Tisch braucht Diamant (Industriezeit), die Arkane-Nebenquest „Enchantment Industry“ hängt daran; das Create-Dragons-Plus-Blaze-Template hat keine sichtbare Quelle. Quest oder Rezept ändern? | `dev/decisions-while-away.md` (Zeile „Bekannte Lücken bleiben sichtbar“) | Felix |
| Firmament-Kapitel (Entscheidung A): die neun Opfergaben erscheinen erst mit ihrer Age, am Anfang ist nur „The First Spark“ sichtbar. „Beyond the Firmament“ (neutrales Icon) erscheint zusammen mit „Quantum Core“, also ab der Quantenzeit (FTB Quests zeigt eine Quest ohne Schalter nur, wenn eine Abhängigkeit sichtbar ist). Ziel ab Start sichtbar, als Ende des Wegs, oder erst ab der Quantenzeit (so jetzt)? Ab Start hieße: „Beyond the Firmament“ hängt zusätzlich an „The First Spark“, Linie ausgeblendet (`hide_dependency_lines`). | `dev/gen_quests.py` `build_firmament` | Felix |
| Himmelsblöcke ohne eigenen Drop (Meteorit, Starsteel-Meteorit, Starfall-Kern) und alle Lens-Crown-Items stehen in `age_items/disabled` und sind in EMI versteckt (Ableitungsregel: nie erreichbar). Die Lens-Crown-Teile kommen mit M15 zurück; sollen die Meteorite als Blöcke sichtbar sein? | `dev/age_map.toml`, `dev/derive_ages.py` | Felix |

## Im Client prüfen (Felix)

| Was | Wo | Wer |
|---|---|---|
| Alles aus den Abschnitten von `dev/client-test-brief.md` (pro Version, neueste oben) | `dev/client-test-brief.md` | Felix |
| Titelbild: nach dem ersten Blick auf das Shader-Sternbild `show_compile_errors = true` wieder auf `false` | `config/fancymenu/customization/title_screen_layout.txt` | Felix sieht, Claude stellt um |
| Blutmond erst ab `mob_2`: `/ec lunarForecast` vor und nach der Eisenzeit | `kubejs/server_scripts/firmages/blood_moon.js` | Felix |
| Industriezeit-Quest „The Thresher“ schließt beim Blick auf einen geformten Thresher ab; Loot-Crate-Opener und Task-Screen-Konfigurator fehlen in EMI | Questbuch, EMI | Felix |
| IE-Handbuch-Vorschau von Mahlwerk, Webstuhl, Lichtbogenofen, Schaufelrad, Bagger, Koker und Pumpjack zeigt den TFC-Stahlblock | IE-Handbuch | Felix |
| Chronicle 0.9.3: Firmament-Kapitel deckt pro Age auf, Linien um „Into The Origin“ in der Singularitätszeit, Ring-Quests Bronze/Eisen, Stage-Namen in „Opens with …“ (als OP). Eine Welt, die „A Weird Rock“ schon abgeschlossen hat, behält nur einen verwaisten Fortschrittseintrag | `dev/client-test-brief.md` (0.9.3) | Felix |
| Backup zurückspielen nach `server/README-SERVERPACK.md` (vier Schritte) einmal echt durchspielen; bisher nur die Zip-Struktur geprüft | Test-Server | Felix oder Claude auf einer Kopie |
| Endkampf gegen The Primordial (eigener GeckoLib-Boss, Runde c1/boss) im Client: Modell, Animationen, Texturen, Klänge, Bossleiste und Titel je Phase; Kampfdauer mit 1, 2 und 4 Spielern gegen das Kampfmodell der Runde C2 (8,4 / 7,2 / 6,5 min; Annahmen 60 % Trefferzeit, Schleier ein Drittel offen; Stellschrauben `[boss]` inkl. `hitCap`, `docs/primordial.md`, SPEC §39); Server-Seite per GameTest und Boot geprüft | The Origin | Felix |
| Abenteuer-Runde (a1/adventure) in einer neuen Welt: die Cold Forge erscheint in Hügeln und Hochland, der Frontier Compass findet sie (Richtung, Entfernung), ihre Kiste hält die Frontier Shard; Relikt auf Deployer und Presse; Hollow Keystone in Netherfestungs-Kisten; Flugschreiber in der Mars-Tempelkiste; Draconium-Adern in Granit, Diorit, Gabbro ab age_7; Reaktor gibt Chaos-Fragmente; gebauter Stahl-Kanonenkessel per Cannon Builder und Hitze | neue Welt, Test-Server-Kopie | Felix oder Claude auf einer Kopie |
| Neue Sperrtexte (0.9.0): gesperrtes Item zeigt „Something Unknown“ und eine graue Zeile „Not of this Age“, Hotbar-Zeile „… Their Age has not come.“, keine Fehler im Log | `config/progressivestages/progressivestages.toml` `[messages]` | Felix |
| Startzeit-Test: `mixin.perf.dynamic_resources=true` in `config/modernfix-mixins.properties`, Client starten, Create-Kontraption, Framed-Blöcke und ein Sable-Schiff ansehen; Startphasen vorher/nachher mit `python dev/check_log.py --client` (0.9.0: 64 s erstes Resource-Reload, 128 s bis zum Titelbild) | `config/modernfix-mixins.properties` | Felix testet, Claude wertet aus |
| N7 Welt im Client: Cacophony (Vögel nach Klima und Jahreszeit, Wind), Particle Rain in einer TFC-Wüste und einem Regenwald (folgt der Regen TFCs lokalem Niederschlag?), Cosy-Critters-Vögel über TFC-Wald, Subtle Effects ohne Herzschlag; Startzeit bis Titel und bis EMI bereit, RAM nach 10 Minuten im Wald, FPS in der Basis | `config/`, `n7-world.md` §6 Punkt 9 | Felix |
| Exposure: Kamera bauen, S/W-Film belichten, mit Soda und Salz entwickeln, in der Lichtkammer drucken (Licht 13 nötig); Farbfilm erst in der Industriezeit | `kubejs/server_scripts/recipes/exposure.js` | Felix |
| Blutmond pro Age: `/ec lunarForecast` nach der Eisenzeit zeigt `firmages:blood_moon_age_<N>`; Zombies mit Rüstung und Waffe der Vor-Age, ab der Elektrischen Age schneller | `kubejs/server_scripts/firmages/blood_moon.js`, `dev/gen_blood_moon.py` | Felix |
| Vulkan: Ausbruch und Beben ohne Schaden an Blöcken, Mineralkrusten an heißen Quellen | `config/tfcvolcanoes-startup.toml` | Felix |
| n7-Deko: Startzeit und Heap mit dem vollen Set messen (A gegen B, je drei Läufe) und die Sichtprüfung (Meißelzyklus, Verbundtexturen Panel/Column, Rechiseled-Gruppen; Vanilla-Holz-Deko nur in den Stilen, die es auch in TFC-Holz gibt, keine Every-Compat-Kopien in TFC-Eiche/-Mangrove) | `dev/client-test-brief.md`, Abschnitt „n7-Deko“ | Integrator, dann Felix |
| Holzklassen (HW1A): ein TFC-Baum fällt als Vanilla-Stämme seiner Klasse (Jade zeigt die Art, gedroppt wird die Klasse); Säge + Stamm = 2 Bretter, 4 Bretter = Werkbank, Quest „Workbench“ zählt sie; Info-Quest „Wood Classes“ lesbar; Vanilla-Bäume in Wäldern (Birke, Eiche, Fichte, Tropen, Akazie) sehen neben TFC-Bäumen nicht fremd aus, Vanilla-Laub ohne Jahreszeiten stört nicht | `dev/wood_classes.toml`, `recipes/age_0/wood_classes.js` | Felix |
| Review-Fixes 0.11.0 im Client: Wireless Crafting Terminal unverlinkt (Raster, Ergebnis, Zutaten beim Schließen zurück), Alternator an einer befeuerten Dampfmaschine und an einem Dieselmotor (Server-Kopie zeigte nur Anteil 0 am Kreativmotor und die Gegenprobe ohne Regel, 168 FE/t; ein Dieselmotor ließ sich per Befehl nicht füllen), Tooltip des Consecrated Shard, Zell-Tooltip 126 Typen | `dev/client-test-brief.md` (0.11.0, Punkte 2, 4 und 6) | Felix |
| 0.11.0-Nachtrag in `dev/client-test-brief.md`: neue Mods pro Age, The Primordial, Starfall, Wayshrines, Balance-Zahlen, Messroutine | `dev/client-test-brief.md` | Felix |
| Felix' Test-Server hält veraltete Dateien, die der packwiz-Installer nicht löscht: `mods/` mit Cataclysm, Gateways to Eternity und `firmages-core-0.9.2.jar`, `kubejs/data/twilightforest`, `kubejs/server_scripts/finale`. Vor dem nächsten Start dort `kubejs/` löschen und die drei Jars entfernen, dann synchronisieren (die Kopie dieser Runde lief so sauber) | `test-server/` | Felix |
| Harmonie C2: Energized Smelter gibt aus Eisenstaub Schmiedeeisen (eigenes `mekanism:smelting`-Rezept vor dem Ofenrezept, laut Mekanism-Quelle); im Spiel nicht geprüft | `recipes/unify_iron.js` | Felix im Client |
| Harmonie C2: Digital Miner (36.000 FE/t, 10 s je Block) und Excavator (halbes Tempo) im Spiel fühlen: zu teuer oder zu langsam für die Weltraumzeit? Zahlen in `dev/harmonie-r2.md` §5 | `config/Mekanism/`, `config/immersiveengineering-server.toml` | Felix im Client |

## Inhalt und Config (Claude)

| Was | Wo | Wer |
|---|---|---|
| `derive_ages.py --scan` liest Block-Prädikate von Features als platzierte Blöcke: `minecraft:crimson_fungus` „platziert“ `minecraft:oak_sapling` (Überlebens-Prüfung), so steht der Vanilla-Eichensetzling in der Arkanzeit, ohne dass er dort zu finden ist. Prädikate beim Scan auslassen | `dev/derive_ages.py` (Scan der Features) | Claude |
| Apotheosis-Weltstufe pro Age (Haven bis Pinnacle): Befehlssyntax unbekannt, nur Platzhalter | `kubejs/server_scripts/stages/grants.js` (POC-Kommentar) | Claude, mit Server-Test |
| TFC + IE Crossover: Quarz-Geode abschalten (die TFCreate-Quarzader ist kanonisch) | Datapack-Override, Key nicht verifiziert | Claude |
| CDG-Modularmotor gibt mit Alternator 1,68 M FE je Bucket Diesel (IE-Generator 1,32 M, Band ±25 % bis 1,65 M); mit der Brennstoff-Tabelle (Paket A6) auf `burn_rate` 0,052 ziehen | `kubejs/data/createdieselgenerators/createdieselgenerators/fuel_type/diesel.json` | Claude, Paket A6 |
| Create: Enchantable Machinery: Fortune und Looting auf die Blacklist (Erz-Leck) | Config, Key nicht verifiziert | Claude |
| Create: Bits 'n' Bobs: Schwungrad als Speicher aus | Config, Key nicht verifiziert | Claude |
| Create Mechanical Spawner: Zufalls-Spawn-Fluid aus | Config, Key nicht verifiziert | Claude |
| Mekanism Additions: Baby-Mobs aus | Config, Key nicht verifiziert | Claude |
| Stargate Journey: Strukturen aus | Config, Key nicht verifiziert | Claude |
| IE-Bagger: Mineral-Mix mit Osmium (Dok. 08 §3.2) | `kubejs/server_scripts` | Claude |
| Sieben IE/IP-Multiblöcke geben beim Abbau den gesperrten `immersiveengineering:storage_steel` zurück | `dev/gen_multiblock_templates.py` | Claude |
| Questbuch: FE-Nachweise sind Blick-Aufgaben; nach einem Client-Test auf `forge_energy`- oder Task-Screen-Aufgaben umstellen | `dev/gen_quests.py` | Claude |
| Questbuch: Automatisierungsnachweise per Task-Screen (`task_screen_only`) statt Mengen | `dev/gen_quests.py` | Claude |
| „Data Model: Tier 3“ nimmt jedes Datenmodell (die Stufe ist eine Komponente, die eine Item-Aufgabe nicht liest) | `dev/gen_quests.py` | Claude |
| Planungsdokumente: `08-Ages-Design.md` bekommt „§0 Stand 0.8“ (Abweichungen seit v3), `10-Modliste.md` aus `mods/*.pw.toml` erzeugen (`dev/cf/gen_credits.py --modlist`) | `../Modpack-Planung/docs/` | Claude |
| Helfer-Items (Tom's Storage-Terminal, Alloy Kiln, Kondensatoren, Mechanical Spawner) verlieren ihr Bau-Rezept ab einer späteren Age wieder; Nicht-Operatoren lesen dann „Its making belongs to a later Age“, obwohl das Item abgelöst ist, nicht noch kommt | `config/progressivestages/stages/tool_*.toml`, `progressivestages.toml` `[messages]` | Claude |
| Walkthrough, Fallstrick der Weltraumzeit, nennt Naquadah der Singularitätszeit (Vorwärtsname; die Quests sind bereinigt, der Walkthrough deckt alle Ages ab) | `docs/WALKTHROUGH.md` | Claude |
| `Modpack-Planung/docs/12-Walkthrough.md` doppelt `docs/WALKTHROUGH.md`: auf einen Verweis kürzen | `../Modpack-Planung/docs/` | Claude |
| Java-Fallback-Texte der Mod stimmen nicht mehr mit der Lang-Datei überein („The world realigns...“, „No shrine heart is placed“) | `FirmagesClient.java`, `ReloadStatePayload.java`, `ShrineCommands.java` | Claude (mit der nächsten firmages-core-Version) |
| Ruinen-Truhen: prüfen, dass ProgressiveStages' `stage_filter` beim Öffnen gesperrte Pools entfernt (Truhe in Dawn öffnen: nur Dawn-Kleinkram) und dass Trichter unter der Truhe nichts Gesperrtes ziehen | `kubejs/data/firmages/loot_table/chests/ruin_*.json` | Claude, mit Server-Test |
| n7-Deko POC: KubeJS-Blockmodell mit `loader: fusion:model` (Panel, Column), leere Rechiseled-Gruppen (`"overwrite": true`, `entries: []`), Umschreiben der Every-Compat-Rezepte (Moonlight-Laufzeitpaket) durch `recipes/deco_inputs.js` | `kubejs/startup_scripts/signature_blocks.js`, `kubejs/data/rechiseled/chiseling_recipes/`, `kubejs/server_scripts/recipes/deco_inputs.js` | Integrator (Boot-Log) |
| Storage Drawers: kompaktierende Schubladen nutzen umkehrbare Grid-Rezepte; nach dem Dump prüfen, ob ein TFC-Metall oder -Erz so am Schweißen oder Schmelzen vorbeikommt | `dev/poc_analyze.py`, Storage-Drawers-Config | Integrator |
| Questbuch Arkanzeit: das Ziel „Offer the Arcane Keystone“ hängt an den drei Magie-Schlüsseln (Spirit Proof, Wilden Tribute, Mercury Catalyst); ein Team, das den Arcane Keystone auf der Create-Linie baut, sieht die Ziel-Quest gesperrt (die Age selbst kommt über den Schrein). Technik ist Pflicht: einen Technik-Strang oder ein Entweder-oder als Abhängigkeit ergänzen | `dev/gen_quests.py` `ARCANE` | Claude |
| Questbuch (nächste Runde): Deko-Quests aus n7 §2 (Gatehouse, Living House, Silo) und je eine Einführung für Meißel (TFC-Zyklus, Rechiseled), Drawers, Joy of Painting, Exposure | `dev/gen_quests.py` | nächste Runde |
| Runde C (mods-tech): Quest-Stränge für Goblin Traders, Artifacts, Psi, Building Gadgets 2, AeroEngine, Ars Elemental/Elemancy/Ocultas/Énergistique, Extra Gauges, Draconic Additions und die 22 Alex's-Mobs-Arten (Felix: jede Nicht-Deko-Mod braucht einen Strang) | `dev/gen_quests.py` | Claude, nächste Runde |
| Ars Elemental: Blitz-Teile (Discharge-Glyphe, Shock-Faden) und Flashjack/Sirene bleiben versteckt, weil Flashpine-Schoten und die Splitter nur aus den abgeschalteten Elementar-Biomen kommen; ein Pack-Rezept für die Schote würde sie zurückholen (wie bei den Ars-Nouveau-Schoten offen) | `recipes/age_3/ars_addons.js` | Felix entscheidet |
| Alex's Mobs Continued: Klima-Fenster und Spawn-Gewichte der 22 Arten sind in keiner Welt geprüft (der Server-Test hat keinen Spieler, also keine natürlichen Spawns); Arten, Klima und Dichte in echter Welt | `kubejs/server_scripts/firmages/wildlife_climate.js`, Biome-Modifier | Felix im Client |
| Harmonie C2: Mekanism-Energie-Upgrades im Questbuch lehren (ab der Informationszeit); ohne sie lohnt sich hinter dem IE-Excavator nur 3x (4x +19,5 %, 5x +38 % FE je Barren, `dev/harmonie-r2.md` §5) | `dev/gen_quests.py` (Paket A11) | Claude |
| Harmonie C2: Vanilla-Eisenbarren aus Beute (Truhen anderer Mods, Mob-Drops) ist ein totes verstecktes Item; prüfen, welche Loot-Tabellen ihn noch geben, und auf Schmiedeeisen umleiten (Plan §5 Loot-Umleitung) | `kubejs/data/*/loot_table`, firmages-core Global Loot Modifier | Claude (A7) |
| Harmonie C2: Fission, Turbine und Fusion messen (Plan-Fix 11, Paket A8) und gegen den Digital Miner (bis 360.000 FE/t mit 8 Tempo-Upgrades) stellen | `config/Mekanism/generators.toml` | Claude (A8) |
| Questbuch: echte Item-Belohnungen statt XP und ein Quest-Strang für jede neue Nicht-Deko-Mod von Runde C1 (spätere Runde) | `dev/gen_quests.py` | Claude |
| `firmages selftest all` hat drei rote Crown-Prüfungen (`library_loaded` 1 von 22, `live_after_age_9`, `v4a_chain`), gewollt bis M11, M13, M14 (Himmelsmaterialien) und M15 (Rezepte, V4A-Kette); Rest 71 von 71 grün | firmages-core `CrownSuite` | Claude |
| Wayshrines: Opfergaben und Riten von age_2 bis age_6, das Verstummen bei kaputtem Muster und das gemeinsame Gebet sind nur per GameTest geprüft; die Tutorial-Wayshrine wird nach einem harten Kill vor dem ersten Speichern neu gesetzt (SavedData nicht sofort gesichert) | firmages-core `wayshrine/` | Claude |
| Drifting Isles (M13) müssen `WayshrinePlacement.placeIsleShrine` für den Wind-Schrein aufrufen (S6B) | firmages-core | Claude |
| `balance.feCap` wirkt erst bei neuen Capability-Abfragen (gesetzter oder neu geladener Block, Neustart), nicht beim Config-Reload; Mekanisms eigener Energieweg ist nur kompiliert und per Code geprüft, live gemessen ist der FE-Weg (IE-Kondensatoren) | firmages-core `balance/EnergyMeter`, `compat/mekanism/MekEnergyMeter` | Claude |
| Wayshrine-Dichte: auch bei 72/27 liegen 9 von 20 Seeds außerhalb von 7 bis 11 (5 bis 16); weniger Streuung ginge nur über eine zweite Platzierung nahe dem Spawn (wie die Tutorial-Wayshrine), nicht über den Abstand | `dev/gen_wayshrines.py`, SPEC-himmel §28.4 | Claude (spätere Runde, falls die Streuung im Spiel stört) |
| Kampfmodell The Primordial: Annahmen (60 % Trefferzeit, Schleieranteil je Phase) an einem aufgezeichneten Kampf prüfen; Fernkampf (Draconic-Bogen) zählt mit demselben Treffer-Deckel, ist aber nicht eigens modelliert | `boss/FightModel`, `docs/primordial.md` | Claude mit Felix' Client-Test |
| `check_log`: TFCs „This is fine“-Probe lädt je nach Ladereihenfolge `LocalPlayer` statt `ClientLevel` (2 ERROR „DISTXFORM … LocalPlayer“ in einem von drei Boots der Runde C2); Rauschliste `distxform-boot-client-mixins` um `client/player/LocalPlayer` erweitern | `dev/data/log_noise.json` | Integrator |
| Tutorial-Wayshrine: auf einem von drei Seeds der Review-Kopie fand der erste Start auf 16 Peilungen keinen Platz (Hang oder Wasser), WARN „no fit site … trying again at the next start“; der nächste Start versucht es wieder, solange die Welt jünger als drei Tage ist. Mehr Peilungen oder eine zweite Entfernung, falls das öfter vorkommt | `WayshrineTutorial`, `WayshrineRules` | Claude |
| Starfall: der Ausschluss um FTB-Chunks-Claims hat keinen Test (FTB Chunks fehlt im GameTest-Lauf); die Tarnung als Himmelsgestein vor age_2 nur aus den Stage-Overrides, nicht im Spiel geprüft. Auf der Integrationskopie fand `firmages starfall now` ohne Spieler und Schrein keinen Anker | firmages-core `sky/`, `compat/ftbchunks` | Claude |
| TFC Regrowing Forests: ein Laub-Zufallstick (`RegrowthRecipe.tryToSpread`) lädt Nachbar-Chunks synchron; mit Forceload am Rand und laufendem Chunky hing ein Tick 120 s (Watchdog, Runde C2 Holz). Prüfen, ob Spieler mit Chunky-Vorgenerierung das treffen, sonst Config/Mixin | `dev/poc-results.md` („1.0 round C2, wood classes“) | Claude |
| 18 Macaw's-Scheunen- und Glas-Falltüren in Vanilla-Holz stehen auf age_1, ihre Every-Compat-TFC-Kopien auf age_2 (das Vanilla-Glas-Falltür-Rezept ist früher); harmlos, nicht angeglichen | `dev/data/derived_ages.json` | Claude |

## Prüfen, ob noch offen

| Was | Wo | Wer |
|---|---|---|
| Sequenzierte Montagen ohne Ausgabe (`simulated:…gyroscopic_mechanism`, `…engine_assembly`, `create_connected:…control_chip`, `create_factory_logistics:…fluid_mechanism`): wegen eines gesperrten Create-Blechs als Zufallsausgabe gestrichen; brauchen einen `byproducts.js`-Eintrag, falls kein anderes Rezept sie macht | `dev/poc-results.md` „Content fixes after the late Ages“ | Claude, im nächsten Dump |
| `createdeco`-Schiffscontainer brauchen das gesperrte Create-Eisenblech | Rezepte | Claude, im nächsten Dump |
| Sieben `create_dragons_plus:sandpaper_polishing/*`-Rezepte fehlen im Dump (Ursache unbekannt) | `dev/poc-results.md` „Osmium vein“ | Claude |
| Mekanisms Teleporter und Ars-Warp in The Origin sperren; Trophäen-Denkmal | firmages-core | Claude |
| Team-Übernahme auf Deiner Welt im Spiel prüfen: HiighHopes loggt ein, die Party „Firmament“ entsteht und behält age_0 bis age_9 (`/stage list`), ein zweiter Spieler kommt ohne Einladung dazu und hat alle Ages, keine Zeremonie und keine Firmament Night. Belegt nur durch die GameTests `TeamGameTests` (FTB Teams im Testlauf), auf dem Pack-Server nicht gelaufen | firmages-core, `server/README-SERVERPACK.md` | Felix (Client), Claude (Server-Log) |
| Flüssigkeits-Tags `age_fluids/<stage>` (SPEC §2.1) werden nicht erzeugt | `dev/gen_stage_locks.py` | Claude |
| Reload-Zeiten auf einem leeren Rechner neu messen (`firmages reload` dreimal bei age_9) | Test-Server | Claude |
| Mekanism-Strahlung, Fusions- und Reaktor-Balance (Dok. 10 v3 §7.4 nur für DE gesetzt) | Configs | Claude |
| `/fa_dump` hält den Server-Thread 3,4 bis 4 s an („Can't keep up“ im Log, Boot N4; N6-Dump-Boot 3,3 s); nur das Debug-Werkzeug, kein Spielweg. Darum lief die Log-Prüfung N4 auf einem Boot ohne Dump. Falls störend: Dump in Teilen pro Tick schreiben | `kubejs/server_scripts/debug/dump.js` | Claude |
| `/firmages selftest all` hält den Server-Thread etwa 2,1 s an („Can't keep up! … 2149ms“, N5-Kopie, Runde 2); wie `/fa_dump` nur ein Debug-Befehl. Die Log-Prüfung N5 lief darum auf Boots ohne Selbsttest und Dump (Runden 3 und 4: 0 übrig). N6-Boot 1 mit `selftest all`: kein „Can't keep up“, 0 übrig; der Ruckler kommt also nicht bei jedem Lauf | `command/SelfTest*`, `dev/poc-results.md` („0.9.2 round N5“) | Claude, falls es stört |
| JEI schreibt pro Start sechs ERROR-Zeilen „Found a broken recipe“ für die Chemixer- und APT-Rezepte (JEI baut seine Kategorien trotz EMI); EMI zeigt sie seit firmages-core 0.9.2 über eigene Kategorien. Im Client-Log Rauschen (`client_log_noise.json`) | Evolved Mekanism, JEI | Mod-Autor; Claude nur, wenn mehr als sechs Zeilen kommen |
| Moonlight 3.7.2 hält beim Stop 15 Nicht-Daemon-Threads seines Dynamic-Pack-Pools offen; AllTheLeaks meldet „server is stuck while trying to close“, die JVM endet trotzdem mit Exit 0 (Runden 3 und 4). Rauschen in `log_noise.json` | Moonlight | Mod-Autor |
| Erster Login nach dem Start: 6 s Ruckler („Can't keep up! … 6095ms“, Live-Sitzung 0.9.0, 13:41); mit spark messen, was beim Login läuft (Modonomicon-Bücher, IE-Trankrezepte, ProgressiveStages-Sync, firmages-core) | Test-Server | Claude |
| Apotheosis-Tiefendungeons in Dimensionen mit Boden ab y = 0 („Empty height range“, Rauschliste): Biome-Modifier auf die Oberwelt begrenzen, sobald ein Log die Dimension nennt | `kubejs/data/apotheosis/neoforge/biome_modifier/` | Claude |

## Erledigt (diese Runde geprüft)

| Was | Beleg |
|---|---|
| Harmonie H2A: Mekanism-Verbrauch ×10 (Runde C2) | `config/Mekanism/machine-usage.toml`, `machine-storage.toml`, `config/MekanismMoreMachine/` aus `dev/gen_machine_usage.py`; 282 Werte auf der Server-Kopie zurückgelesen; Bänder in `dev/harmonie-r2.md` §4 |
| Harmonie A4 bis A6 (Runde C2): Erzleiter aus der Metalltabelle, ein Eisen, Brennstoff-Tabelle | `poc_analyze.py` 107 PASS (L-1 bis L-7, U-2), `gen_fuels.py --check`, `dev/harmonie-r2.md` |
| Harmonie H5A: Tally-Nachweis in den Schrein-Opfergaben ab age_4 | Runde C2 (c2/core-balance): `firmages_shrine/tallies.json`, SPEC §35; Kopie: Opfergabe ohne Zählung abgelehnt („Caelum asks for proof of work … counted 0“), 16 Stahlbarren durch eine Tally Chute, dann angenommen; GameTest `offeringWaitsForProof` |
| Wayshrines: Dichte-PoC über 20 Seeds, SPEC §28 auf Lieferstand | Runde C2: Zensus 20 Seeds × 5 Abstände, Abstand 72/27 (11 von 20 im Fenster, alle 5 bis 16), SPEC §28.4, §28.8, §28.9, §28.11 nachgezogen |
| GameTest `excavatorVeinAtAutomationAge` wackelig | Runde C2: der Test entfernt die Ader und `veinAdded` vor der Dirty-Prüfung; danach 3 von 3 Läufen grün (87 Tests) |
| Wayshrine-Opfergaben ab der Arkanzeit (Lens-Crown-Legierungen ohne Rezept bis M15) | Übergang bis M15: Arkan Meteoreisen-Doppelbarren, Industrie Meteoreisen-Blech, ab Elektrik Sternenstahl-Barren (`firmages_wayshrine/offerings.json`, `dev/decisions-while-away.md`); `selftest wayshrines` auf der Review-Kopie: „age_9 wants firmages:starsteel_ingot; every offering item is registered“ |
| Brennstoff-Pflicht des Alternators (Felix H1A, Paket A7) | firmages-core `mixin/cca/AlternatorMixin`, `compat/create/AlternatorFuel`, Config `balance.alternatorFuelOnly`; `FuelShareTest` (Wind 0, Kohle-Kessel 168, halb und halb 84 FE/t); Review-Kopie: Kreativmotor 256 RPM, Anteil 0, LV-Kondensator bleibt leer, mit `alternatorFuelOnly = false` 168 FE/t (12.600 FE in 75 Ticks), wieder an: kein Zuwachs |
| AE2: Typen je Zelle | `balance.ae2CellTypes` 126 (Mixins auf `BasicCellInventory` und `BasicStorageCell`); `/firmages balance cells` auf der Review-Kopie: 1k 126, 256k 126, MEGA 1M 126, tragbare 16k 36 (eigene Grenze), Flüssigkeit 64k 18. 252 ist eine Config-Zeile |
| Wireless Crafting Terminal vor der Informationszeit nutzlos | firmages-core `compat/ae2/PortableCrafting`: unverlinkt öffnet Rechtsklick ein 3×3-Raster; Review-Kopie mit Debug-Spieler: vorher öffnete ae2wtlib `WCTMenu` und schloss es nach wenigen Ticks, jetzt `PortableCrafting$Grid`, offen nach weiteren Befehlen |
| IE-Mineralmixe mit Vanilla-Biomprädikaten | Prädikate auf `tfc_ie_addon:generate_mineral_mix` (Rich Auricupride plus `c:is_badlands`, Alluvial Sift und Super Vein plus `tfc:is_river`/`c:is_lake`); `/fa_m3` zählt die Oberwelt-Biome je Mix: Banded Iron und Lazulitic Intrusion 109, Rich Auricupride 8, Alluvial Sift und Super Vein 11 (vorher kein Biom); Mephitic Quarzite bleibt im Nether |
| Erster Boot nach a1/adventure: `fa_dump` + `fa_dump_full`, dann `derive_ages.py --check` und `gen_stage_locks.py --registry <dump> --check` (die Vorhersage aus `dev/predict_dump.py` muss stimmen), `check_log.py` ohne Twilight-/Cataclysm-Zeilen | 0.10.0 Kopie-Boots 7 bis 9: `gen_stage_locks.py --registry`, `derive_ages.py` (Dump ohne Gate) und `gen_stage_locks.py`, danach `--check` beider up to date; `poc_analyze.py` 91 PASS, 0 FAIL; `check_log.py` 0 übrig (`dev/poc-results.md`, „1.0 round A“) |
| Neue Namespaces `exposure` und `tfcvolcanoes` stehen in keinem Age-Tag, bis ein Dump mit den neuen Jars da ist: Boot mit `fa_dump`, dann `gen_stage_locks.py --registry <dump>`, `derive_ages.py --dump-dir <dump>`, `gen_stage_locks.py` | 0.10.0 Dump: Exposure `age_1` (Filme `age_2`/`age_4`), TFC-Volcanoes-Minerale ab Dawn (Code-Weltgenerierung in `derive_ages.py`), Pechblende `age_7` |
| EC2-Daten der Age-Blutmonde (Ausrüstungssets per Registry-Verweis, `corgilib:is_type_tag`) beim Boot ohne Fehler laden | 0.10.0 Kopie-Boots 7 bis 9: keine EC2-Zeile zu `blood_moon_age_*` im Log, `check_log.py` 0 übrig |
| n7-Deko: Server-Boot mit `fa_dump` bei `age_9`, dann `gen_stage_locks.py --registry`, `derive_ages.py` (Dump plus `predict_dump.py`), `gen_stage_locks.py`, `poc_analyze.py`: ersetzt die vorläufigen IDs (Item-Modelle der Jars) durch die echten und leitet die Ages der neuen Rezepte ab. Erwartet: Vanilla-Stein-Varianten von Macaw's und Dusty Decorations werden versteckt oder später | 0.10.0: `registry.json` aus dem echten Dump (34.969 Items), Ages abgeleitet, `poc_analyze.py` 91 PASS |
| Every-Compat-Hecken (Macaw's Fences, Blätter-Typen): erkennt Moonlight TFC/AFC-Blätter? Erst der Dump zeigt es; falls ja, Age-Regel `name:everycomp:mcf/` deckt sie schon | 0.10.0 Dump: nur sieben EC-Hecken (Ad Astra, Ars Nouveau), keine aus TFC- oder AFC-Blättern; Moonlight erkennt sie nicht, nichts zu tun |
| Kupferweg im Chronicle führte in eine Sackgasse (Erz im Feuerplatz) | 0.9.3 (n6/text): Erz im Gefäß im Grubenofen, Gießen erklärt; Werte im TFC-4.2.11-Jar geprüft (Kupfer 1080 °C, Feuerplatz höchstens 1000 °C, Grubenofen 1400 °C) |
| Stage-Anzeigenamen mit „Age N:“ und Modnamen | 0.9.3 (n6/data): „Stone Age“ bis „Singularity Age“, „Nights of the X Age“, Helfer ohne Modnamen; Boot N6: Stage-Dateien laden, `check_log` 0 übrig |
| „1 things fall“ (ProgressiveStages ohne Pluralform) | 0.9.3: `items_dropped` und `items_moved_hotbar` ohne `{count}` |
| Doppelte Gathering-Zeile in The Origin | 0.9.3: `the_primordial.js` sendet keine eigene Zeile mehr, nur firmages-core |
| Evolved Mekanism: Chemixer- und APT-Rezepte fehlten in EMI | firmages-core 0.9.2 `compat/emi/EvolvedMekanismEmi` (SPEC §21.12), `gradlew build` grün; im Client noch anzusehen (Testbrief 0.9.2, Punkt 8) |
| EMI-Audit schrieb bei jedem Reload WARN | firmages-core 0.9.2 zählt den angezeigten Index (`EmiStackList.filteredStacks`); INFO ohne Treffer (SPEC §21.4) |
| Neun Configs wurden beim ersten Start „korrigiert“ | Dateien so, wie NeoForge sie schreibt, ohne Pack-Kommentare; N5-Kopie Runde 2: keine „is not correct“-Zeile mehr |
| `dev/data/world_sources.json` aus 177 Jars | `derive_ages.py --scan` auf 186 Jars (N5), nur Zugänge; `supplementaries:ash` ist erreichbar (Bronzezeit) |
| EMI zeigt gesperrte Items in Item-Listen-Zutaten (67 Rezepte) | firmages-core 0.9.1 `mixin/emi/ListEmiIngredientMixin` (SPEC §21.11); `poc_analyze.py` E-1 auf dem 0.9.1-Dump: 0 Typen, 0 Item-Listen, 88 PASS, 0 FAIL. Im Client noch nicht gesehen (Client-Test 0.9.1) |
| TFCRegistryAPI-Rest (etwa 112 DISTXFORM/Mixin/FATAL-Zeilen pro Start) | firmages-core 0.9.1 lädt nur Enums (`perf/ClassFileAccess`); Boot N4: FATAL 0 (0.9.0: 14), 7 DISTXFORM (alle dokumentiert), Eintrag `tfcregistryapi-scan-residual` gelöscht; `check_registry_parity.py dump` gegen den N3-Dump: PASS (Enums, Astikor-Holz 81/81) |
| Log nach dem nächsten Neustart ohne offene Zeilen | Boot N4 auf einer Kopie des Test-Servers (neue Welt, alle Ages, `selftest all`): `check_log.py` 0 übrig, 0 offen, Exit 0; 462 „Skipping recipe“ und 76 „Falling back“ weg |
| Every Compat-Rezepte über KubeJS umgeschrieben (Theken auf glattem Gestein) | Boot N4: „[firmages] deco: 46 counters take smooth rock“, `derive_ages.py --check` auf dem echten Dump: up to date |
| Deko in TFC/AFC-Hölzern später als geplant (classic- und cottage-Türen Eisenzeit, Theken Arkanzeit) | N4: Drucke aus Tür bzw. Falltür, Theken aus glattem Gestein; 102 Items jetzt age_1 (`decisions-while-away.md`, „Deko in TFC- und AFC-Hölzern ab der Bronzezeit“) |
| `drop_book_on_death = false` (config-todo 1) | `config/ftbquests/quests/data.snbt`, `validate_quests.py` prüft es |
| Draconic-Evolution-Werte (config-todo 6) | `config/brandon3055/DraconicEvolution.cfg`: Brennstoff 0,5, Ausgabe 1,0, Chaos-Drops 5, Drachenstaub 16 |
| AE2-Meteoriten (config-todo 7) | entstehen in der TFC-Welt nicht; die Pressen gibt es per Rezept (`recipes/age_6/information_age.js`) |
| Create: Diesel Generators ohne Öl-Chunks (config-todo 9) | `config/createdieselgenerators-server.toml` |
| `reveal_stage_names_only_to_operators` (config-todo 19) | Standard `true` bleibt; die Spielertexte der Sperre stehen jetzt in `[messages]` (0.9.0) |
| Taste K frei (config-todo 21) | `dev/archive/keybinds.md`: Shader-Schalter von K gelöst |
| TFC-IE-Adern in den Stage-Locks (config-todo 22) | `dev/age_map.toml` (Bleiglanz age_4, Bauxit age_5, Uraninit age_7) |
| Stage-Chatzeile „Age N: …“ beim Age-Wechsel | gelöscht (0.9.0, `unlock_message` in allen Stage-Dateien entfernt) |
| Versionszeile im Titelbild bei jedem Release von Hand | `dev/release.ps1 -Version` schreibt sie, ohne `-Version` bricht es bei Abweichung ab |
| `grants.js`-Fix (reducedDebugInfo nach Age-Wechsel) | `dev/poc-results.md` „0.8.0 final checks“, H5 |
| Drei Sammel-Tags (U-1) | per Regel als Sammel-Tag erkannt (`decisions-while-away.md`, „U-1: Sammel-Tags per Regel“) |
| Create-Erntemaschine, Pflug, Lorenkupplung brauchen Eisen | 0.8.1: aus Bronze (`client-test-brief.md`, 0.8.1 Punkt 7) |
| MekaSuit-Module ohne Bronze-Nugget-Quelle | 0.7.1: herstellbar |
| Mystical Agriculture ohne Quelle | 0.7.2: eigene TFC-Adern ab der Informationszeit |
| IE-Chunk-Loader | versteckt (`decisions-while-away.md`, „IE-Chunk-Loader aus“) |
| Keystone-Leihe für die Marid-Quest | firmages-core 0.5.0 (SPEC §7.4) |
| `mod/firmages-core/README.md` „milestone M1“ | 2026-10-14: ein Satz mit Verweis auf `SPEC.md` (Branch n4/text) |
| Vanilla-Holz nur hinter dem Stargate (Entscheidung Felix) | HW1A umgesetzt (Branch `c2/holz`): TFC/AFC-Bäume droppen Vanilla-Klassenstämme, Vanilla-Bäume wachsen in TFC-Wäldern nach Klima, Vanilla-Holz ab Dawn/Steinzeit, Deko nach Stil-Parität; `poc-results.md` „1.0 round C2, wood classes“, W-1 bis W-8 PASS |
