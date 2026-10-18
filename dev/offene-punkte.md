# Offene Punkte

Stand 2026-10-18, Pack 1.2.0, firmages-core 1.2.0 (Runde G3). Hier steht nur, was nach 1.0 wirklich offen ist: Entscheidungen, die
Felix trifft, Prüfungen im Client und Ideen für die Zeit nach 1.0. Erledigtes früherer Runden steht in der Git-Historie
dieser Datei (bis Commit `7689802`) und mit Beleg in `dev/poc-results.md`; unten nur, was die Runden F1, G1 und G2
geschlossen haben.
Eine Zeile pro Punkt: was, wo, wer entscheidet oder erledigt. Erledigt: Zeile nach unten verschieben, mit Beleg.

## Entscheidungen (Felix)

Die neun Fragen von 1.0.0 und die Codes M1A, T1A und Q1A hat Felix am 2026-10-17 beantwortet, jede steht mit Beleg unter „Erledigt in Runde G1“.

| Was | Wo | Wer |
|---|---|---|
| G3 Quest-Edits (Questbuch eingefroren, Felix editiert im Spiel): (a) Industriezeit-Side-Road eine Quest zur Insel-Ruine (z. B. `firmages:wind_seal` beobachten oder Firmament Crystal aus der Kammer); (b) Aquaculture 2 braucht nach der Regel „jede Nicht-Deko-Mod im Quest-Strang“ eine Side Road (Haken, Neptunium); `validate_quests --style --mods` danach 0/0 | `config/ftbquests/quests/`, `dev/quests-editing.md` | Felix |
| G3 Endgame-Studie: ob eine Nach-Finale-Schicht gewollt ist (Vorschläge A „Ages-Stern“, B Wellen-Verteidigung, C New Game Plus) und ob dafür eine Mod-Ausnahme (Gateways to Eternity) in die Inhaltssperre darf | `dev/endgame-studie.md` | Felix |
| G3 Apotheosis: die Weltstufe folgt der Age (`nights.json` `apoth_tier`, bei jedem Age-Grant und Login gesetzt), aber die manuelle Wahl (Config „Enable Manual World Tier Changes“) ist an: Spieler können die Stufe im Auswahlfenster ändern. Abschalten oder so lassen; dazu die Apotheosis-Tiefendungeons in Dimensionen mit Boden ab y = 0 („Empty height range“, Rauschliste) | `kubejs/server_scripts/stages/grants.js`, Apotheosis-Config | Felix |
| G3 Radar bewusst gewählt (Felix R1A): `worldPlayerRadar` ALL, Teammitglieder und alle Spieler weltweit auf der Karte (ab `ui.mapFromAge`); auf öffentlichen Servern sieht so jeder jeden. Nur ändern, wenn das stört | `journeymap/server/6.0/journeymap.server.global.config` | Felix (Info) |
| G3: Aquaculture-Arten der Kälte, des Dschungels und der Pilzinseln sind beim Angeln unerreichbar (TFC hat keine `c:is_cold`/`c:is_hot`/`c:is_jungle`-Tags); eigene Biome-Tags oder hinnehmen | `kubejs/data/c/tags/worldgen/biome/` | Felix |
| Dawn-Falle: wer in Dawn in ein Steinloch oder eine Höhle fällt, kommt ohne Erde, Sand, Kies oder Stämme im Inventar nicht heraus (Stein bricht in Dawn nicht, Klettern an Stein gibt es weder in Vanilla noch in TFC). Das galt wegen der Spitzhackenregel (Stein nur mit Spitzhacke, SPEC §19.2; in Dawn gibt es keine) schon vor 1.1.0, jetzt ausdrücklich durch die Dawn-Liste `#firmages:dawn_breakable` (SPEC §19.6). Optionen nach Ergebnis: **A** so lassen (Dawn bleibt streng: Erde oder Kies in der Hotbar tragen, sonst bleibt nur der Tod und der Respawn); **B** Stein bricht in Dawn mit der Hand, droppt aber nichts (Stufen schlagen geht, Steinmaterial gibt es weiter erst ab der Steinzeit). Prüfung: Zeile 23 der „1.1.0 Checkliste“ | `kubejs/data/firmages/tags/block/dawn_breakable.json` (`dev/gen_dawn_breakable.py`), firmages-core `ToolRule` | Felix |

## Bestehende Welten (Server)

| Was | Wo | Wer |
|---|---|---|
| G3: Welten, deren Origin vor Runde G3 erzeugt wurde, behalten die kleine Arena (Radius 20, ohne Altäre, Schild, Treppe, Vitrinen); nur eine neu erzeugte Origin bekommt die große (SPEC §16.5). Umstellen hieße `origin rebuild` nach Löschen der Origin-Region, nicht automatisch | `OriginLayout`, `OriginSavedData.layout` | Felix (Info) |
| Vanilla-Herzen nur in neuen Welten: eine Welt von 1.0.0 behält TFCs Gesundheit (Default 0,85) in ihrer Welt-Config; bei gestopptem Server `nutritionMinimumHealthModifier`, `nutritionDefaultHealthModifier` und `nutritionMaximumHealthModifier` von Hand auf `1.0` setzen | `<Welt>/serverconfig/tfc-server.toml` `[mechanics.player]` | Felix |
| Welten, die älter als drei Tage sind und keine Tutorial-Wayshrine haben (Suche vor dem Release 1.0.0 mit nur 16 Peilungen, oder vor 0.12.0 angelegt): der Dawn-Keystone „A Shrine on the Road“ (Blick auf irgendeinen `firmages:wayshrine_core`) ist dort nur über eine natürliche Wayshrine weiter draußen oder nach `/firmages wayshrine tutorial` (Operator) erfüllbar, und „The First Spark“ wartet auf ihn. Felix' Testwelt ist neu | Questbuch, firmages-core `WayshrineTutorial` | Felix, falls eine solche Welt in Dawn steht |
| Welten von 1.0.x mit 1.1.0: Items in Inventar-Reihen, die die Age der Welt noch nicht öffnet, wandern beim ersten Login in freie Plätze oder fallen vor die Füße und bleiben dort ohne Ablaufzeit liegen, bis jemand sie aufhebt (Sweep, SPEC §19.4; bis zu 27 Stapel je Spieler); vorbefüllte Behälter in schon erzeugten Chunks (TFC-Ruinen-Gefäße) behalten ihre Kleinerze, ungeöffnete Truhen filtert das Loot-Gate beim Öffnen (SPEC §19.5) | `[hardship.inventoryRows]` und `gate.lootFilter` in `config/firmages-server.toml` | Felix (Hinweis an Mitspieler) |

## Im Client prüfen (Felix)

| Was | Wo | Wer |
|---|---|---|
| **Die „1.2.0 Checkliste (Runde G3)“** in `dev/client-test-brief.md` (die einzige offene Liste: Arena, Altäre und Schild, Himmel je Phase, Origin ohne Monster, Insel-Ruine, Meerestiere, Wracks, Aquaculture, Nächte je Age, Blutmond, Cave Dweller, Apotheosis-Stufe, Karte auf M, Radar, Something Unknown, TabTPS, Menüs, Resourcepacks, Dusty; ältere Listen im Archiv `dev/archive/client-test-brief-bis-1.1.0.md`) | `dev/client-test-brief.md` | Felix |
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
| Starfall: Ausschluss um FTB-Chunks-Claims ohne Test, Tarnung vor age_2 nur aus den Stage-Overrides | firmages-core `sky/`, `compat/ftbchunks` | Claude |
| `create_dragons_plus:sandpaper_polishing/*`: die Zahl dieser erzeugten Rezepte schwankt zwischen Boots um eins; G-2 kann daran rot werden, ohne dass das Gate etwas falsch macht | `poc_analyze.py` G-2 | Claude |
| Debug-Befehle `/fa_dump` und `/firmages selftest all` halten den Server-Thread 2 bis 4 s an („Can't keep up“); Log-Prüfungen laufen darum auf Boots ohne sie | `kubejs/server_scripts/debug/dump.js`, `command/SelfTest*` | Claude, falls es stört |
| Mod-Rauschen: JEI schreibt sechs „Found a broken recipe“ für Chemixer und APT (Client); Moonlight hält beim Stop 15 Threads offen. Beide in den Rauschlisten | Evolved Mekanism, JEI, Moonlight | Mod-Autor |
| GUI-Resourcepacks mit anderer Grundfarbe zeigen in Dawn ein graues Rechteck über dem Feld (#C6C6C6 fest); das Pack liefert keins mit | firmages-core `client/immersion/GridLockClient` | Claude, falls ein GUI-Pack dazukommt |
| Planungsdokumente: `08-Ages-Design.md` „§0 Stand“, `10-Modliste.md` aus `mods/*.pw.toml` erzeugen, `12-Walkthrough.md` auf einen Verweis auf `docs/WALKTHROUGH.md` kürzen | `../Modpack-Planung/docs/` | Claude |
| Der TFC-GameTest-Lauf (`-PgametestTfc`) stürzt in `IsleGameTests.shapeAndSkyLight` ab (NPE in `LightEngine.getLightValue`); der Standardlauf (125 von 125) ist grün | `src/gametest/.../IsleGameTests.java` | Claude |
| Generierte Breitstämme (`branch_direction none` in TFCs `sequoia_large` und `douglas_fir_large`) droppen vermutlich den TFC-Stamm statt des Vanilla-Klassenstamms (HW1A); bestand schon vor G1, nie geprüft | `dev/gen_wood_classes.py`, Loot der Stämme | Claude |
| Neue Welt, deren Tutorial-Wayshrine nie gefunden wurde (die Suche wiederholt sich jeden Vierteltag und endet, wenn die Welt drei Tage alt ist; dann ein WARN im Log): der Dawn-Keystone „A Shrine on the Road“ ist nur über eine andere Wayshrine (natürliche, weiter draußen) oder `/firmages wayshrine tutorial` (Operator) erfüllbar | firmages-core `WayshrineTutorial`, Server-Log | Felix, falls der WARN kommt |

| Loot-Lücken (SPEC §19.5): Entities, die eine Strukturvorlage setzt (Rahmen, Rüstungsständer), behalten ihre Items; Sophisticated-Rucksäcke halten ihren Inhalt in SavedData unter der Rucksack-ID, nicht im Item; was beim Erzeugen eines Chunks aus einem vorbefüllten Behälter fiel, kommt mit der Age nicht zurück; Mods mit eigenem Platzierungscode ohne `StructureTemplate.placeInWorld` laufen am Filter vorbei | firmages-core `loot/`, `mixin/StructureTemplateMixin` | Claude, falls sich ein Fall zeigt |
| KeyGate-Grenzen (SPEC §18.10): Mekanisms `MekKeyBinding` (eigenes `isDown`, handelt in `setDown`) und Leser des rohen Tastencodes (Ars Nouveau, Create-Werkzeuggürtel, Ponder per GLFW, AE2, Railways, Apotheosis über `isActiveAndMatches`) erreicht das Flag nicht; was sie öffnen, fängt die Screen-Sperre, der Rest braucht das Item der Mod (Ars-Zauberbuch, Create-, AE2-Items über das Item-Gate) | firmages-core `immersion/KeyGate`, `mixin/client/KeyMappingMixin` | Claude, falls eine Taste durchrutscht |
| Inventar-Reihen: Slot-Klassen, die `isActive` ohne `super` überschreiben (Ad Astras `InventorySlot`, Exposures Album-Slot), zeichnen ihre Hover-Markierung über dem grauen Deckel; Klicks und Verschieben bleiben gesperrt (SPEC §19.4) | firmages-core `client/immersion/InventoryRowsClient` | Claude, kosmetisch |
| Kriechen: Vanilla kriecht nur in einer 1 Block hohen Lücke (Not Enough Animations zeigt die Pose dann in F5); eine eigene Kriech-Taste bräuchte eine weitere Mod (Inhaltssperre) | Zeile „G2-Animationen“ in `dev/decisions-while-away.md` | Felix, falls gewünscht |

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
| JourneyMaps Join-Zeile bei einer alten `firmages-client.toml` (Review G1): das neue Muster stand nur im Default der Liste | Die eingebauten Muster gelten immer neben `chat.blocklist` (`ChatBlocklist.withBuiltIns`, Default der Liste leer); U `ImmersionTest.chatBlocklistBuiltInsApplyBesideAnOldConfig` mit der Liste von 1.0.0 |
| Karte und Inventar-Knöpfe nach der Client-Config (Review G1) | `ui.mapFromAge` und `ui.inventoryButtonsFromAge` in `firmages-server.toml`, im Status `firmages:age_status`, Netzprotokoll "7"; G `ImmersionGameTests.statusFollowsAgesAndConfig`, U `MapGateTest`, `InventoryButtonsTest`; Zeile „G1-Review: Karte und Knöpfe entscheidet der Server“ |
| Jade im Kreativmodus vor `jadeFromAge` gesperrt (Review G1) | `ViewerGate.overlayOpen` wie die anderen Gates; U `ImmersionTest.jadeOpensInCreativeBeforeItsAge` |

## Erledigt in Runde G3 (mit Beleg)

| Was | Beleg |
|---|---|
| `derive_ages.py` mit neuem Dump (Aquaculture, Wind-Siegel, Alex's-Meerestiere) | Boot A der Integration, `baseline-1.2.0`, `dev/poc-results.md` „1.2.0 round G3“ |
| Apotheosis-Weltstufe pro Age: Befehlssyntax | `/apoth set_world_tier <Spieler> <Stufe>` in `grants.js`, im Boot C2 „Set Bot2's world tier to haven“ |
| Client-Prüfliste der Arena (Größe, Ankunft, Altäre, Himmel) | in die 1.2.0 Checkliste übernommen (Zeilen 1 bis 4) |

## Erledigt in Runde G2 (mit Beleg)

| Was | Beleg |
|---|---|
| Truhen in Strukturen enthalten Loot späterer Ages (Felix) | firmages-core-Loot-Gate `firmages:age_filter`, SPEC §19.5; G `LootGameTests` (4 Loot-Fälle); `poc-results.md` „G2 loot gate“ |
| Vorbefüllte Behälter in Strukturvorlagen (27 generierende Vorlagen, davon 18 TFC Ruins mit Kleinerzen, Salz, Flussmittel, Ton in den Gefäßen) | `mixin/StructureTemplateMixin`, `loot/TemplateContents`, `loot/PlacedContents` (verschachtelt: Shulker-Kiste, Bündel, TFC-Gefäß); G `LootGameTests.templateContentsFollowTheServersAge`, U `PlacedContentsTest`; Liste `python dev/check_loot_ages.py --mods <mods>` |
| Inventar-Reihen nach Age und Dawn bricht nur Weiches (Felix) | SPEC §19.4 und §19.6, `[hardship.inventoryRows]`, `#firmages:dawn_breakable` (`dev/gen_dawn_breakable.py`, 1.116 Einträge); G `InventoryRowsGameTests` (3), `HardshipGameTests.dawnBreakableOnly`; Prüfung D-2; `poc-results.md` „1.1.0 round G2“ |
| Mod-Tasten und -Fenster nach Age, F3 `???`, Karte auf M und FTB-Claims auf L (Felix) | SPEC §18.10 bis §18.12, `kubejs/assets/firmages/key_gates.json` (`dev/gen_key_gates.py`, 135 Tasten gesperrt), F3+C und F3+I kopieren nichts, Stage-Baum nie offen; U `KeyGateTest` (7), `F3VeilTest` (6) |
| `/firmages stage`, `/firmages locate`, `/firmages` listet, Pick-Block-Items, H-Schild 8 s mit angehefteten Quests (Felix) | SPEC §10.2, §10.3, §18.13, §24.8; G `StageCommandGameTests`, `PickBlockGameTests`; Boot: `locate` listet eine echte TFC-Ruine und die Tutorial-Wayshrine |
| Questpanel Nachthimmel, Not Enough Animations und Emotecraft, Server-Pack mit `jdk`-Ordner und `release.ps1 -LocalServer` (Felix) | `panel.png`, `dev/quest-images.md`; Zeile „G2-Animationen“; `server/pack/start.bat`, `dev/release.md`; Ordner `FirmamentAges-Server-1.1.0` |
| G2-Review: Sweep-Drops verschwanden nach fünf Minuten (F1), Steuerzeichen in Checklistenzeile 21 (F2), Vorlagen-Entities (H1) und Inhalte gewürfelter Stapel (H2) ungefiltert, Gegenprobe der Tastensperre (H5), Dawn-Falle (H6) | `InventoryRowsService.sweep` (`setUnlimitedLifetime`), `StructureTemplateMixin` auf `createEntityIgnoreException`, `PlacedContents.filterEntity`, `NbtContents.filterNested`; 346 Unit-Tests, 142 GameTests; Kopie-Boot: Ad-Astra-Pygros in Dawn ohne Ausrüstung, kein Eisenbarren-Entity, `check_log` remaining 0, `selftest all` 112; `poc-results.md` „1.1.0 round G2, addendum“; H6 offen bei Felix (oben) |
| TFC-Kleingefäß ging beim Vorlagenfilter verloren (Integrationsboot) | `PlacedContents.keepsSize`, U `PlacedContentsTest.tfcVesselKeepsItsFourSlots`; Boot C: leeres Gefäß in Dawn, volles in `age_9` |
