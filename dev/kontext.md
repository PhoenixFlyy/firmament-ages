# Firmament Ages: Kontext für Agenten

Diese Seite ersetzt für den Einstieg `mod/firmages-core/SPEC.md`, `dev/README.md` und die langen Notizen; die Pfade führen zu den Details.
Stand 2026-10-17: Pack 1.0.2 und firmages-core 1.0.2 (Runde G1b: Client-Absturz von 1.0.1 behoben, TerraFirmaCraft auf 4.2.11 gepinnt, weil WoodenCog 1.2.19 `TemperatureDisplayStyle` braucht; Backups alle 2 h; mit `g1/server`: `stop` fragt nach `stop confirm`, `restart`, Server-Pack mit EULA-Datei und Neustart-Schleife; Release-Commit mit Tag `v1.0.2`), Branch `dev`. Davor 1.0.1 (Runde G1, Felix' Rückmeldung zu 1.0.0, Tag `v1.0.1`, nie auf CurseForge).

## 1. Was das Pack ist

Öffentliches Koop-Progressionspack (ein Team pro Server) auf Minecraft 1.21.1, NeoForge 21.1.252, Java 21. TerraFirmaCraft trägt Welt und Metallurgie bis Stahl, Create ist die Kinetik- und Logistikschicht, Immersive Engineering, Mekanism und Draconic Evolution tragen das Spätspiel.
Stages: `dawn` (Prolog) und zehn Ages `age_0` bis `age_9` (Stone, Bronze, Iron, Arcane, Industrial, Electric, Information, Space, Quantum, Singularity). Die Quest „The First Spark“ gewährt `age_0`, der Schrein von Caelum gewährt `age_1` bis `age_9` (Ring, Opfergabe auf dem Plinth, Ritus, Gebet); Finale: Ultimate Singularity, Sternentor nach The Origin, Endboss The Primordial, Stage `finale_won`.
Eigene Mod `firmages-core` (Mod-ID `firmages`, Paket `dev.firmages.core`, `mod/firmages-core/`). 267 Mod-Metadateien plus das firmages-core-Jar, 268 Mods in den Credits. Inhaltssperre seit 2026-10-15: keine neuen Mods, auch keine Bibliotheken (`dev/content-lock.md`); Ausnahmen G1 (Felix M1A/T1A): JourneyMap, JourneyMap Integration, Enhanced Falling Trees. Questbuch heißt „the Chronicle“ (Taste K).

## 2. Die Regeln

- **Technik ist Pflicht, Magie Bonus, kein Boss Pflicht:** jede Age hat einen Technik-Weg; die Arkanzeit öffnet über die Spirit Route oder die Machine Route (Hollow Keystone). Belege: `dev/decisions-while-away.md` „Age-Token statt Boss-Token“, „Technik-Weg für die drei Magie-Teile“, Prüfung R-X.
- **Eine Erzleiter:** jede Route Erz zu Metall sitzt auf einer Stufe 1 / 1,5 / 2,5 / 3 / 4 / 5 / 6 (Arkan-Magie 2,0); Tabelle `kubejs/data/firmages/balance/metals.json`, Prüfungen L-1 bis L-7, `/firmages ore audit`. Ein Eisen: Schmiedeeisen ist `c:ingots/iron`, kein Vanilla-Eisenbarren (U-2). Ein Wert je Metallform, der TFC-Preis (Block 1 Barren, Blech 2): kein Rezeptkreis schafft Metall (L-8, `recipes/metal_values.js`).
- **Energie:** nur FE, ein FE-Generator pro Age, kein Passiv- oder Billigstrom (`../Modpack-Planung/docs/09-Entscheidungen.md`, Zeile „Energie“); der Alternator gibt FE nur aus befeuerter SU (H1A), Mekanism-Maschinen x10 (H2A). Bänder je Age: `dev/harmonie-messung.md`.
- **Chunk-Laden nur über FTB Chunks:** Team-Kontingent ab `age_4` (`CHUNK_QUOTA` in `kubejs/startup_scripts/00_constants.js`), `force_load_mode "never"`: nur solange ein Teammitglied online ist (G1, Zeile „G1: Chunk-Laden nur mit Team online“); andere Chunkloader sind aus.
- **Alles wartet auf seine Age, in Menüs und Welt:** ProgressiveStages versteckt Items, Rezepte, Erze (als Gestein), Mobs und Dimensionen; firmages-core sperrt Maschinenrezepte (SPEC §4) und filtert jedes erzeugte Loot nach Age (§19.5, `gate.lootFilter`; ProgressiveStages' `stage_filter` wirkt nur auf eine `[loot]`-Kategorie, die das Pack nicht hat) und zeigt EMI, Jade und Ponder nach Age (§18, §21). Betrachter-Regel (G1): EMI, die Rezeptfenster von EMI und JEI, Jade, die Inventar-Knöpfe von Curios und Apothic Attributes (ab `age_0`, `ui.inventoryButtonsFromAge`) und JourneyMap (ab `age_2`, `ui.mapFromAge`) sieht vor ihrer Age nur der Kreativmodus; alle Schwellen stehen in `firmages-server.toml [ui]`, der Server schickt sie in `firmages:age_status`; Operatoren im Survival sind gesperrt wie alle; Jade zeigt ab der Bronzezeit gesperrte Blöcke als „Unknown“ mit Zeichen (§18.2, §18.3, §21.14). Stage-Dateien ohne `unlock_message`: die Zeremonie ist die einzige Ansage.
- **Tasten, Fenster und F3 nach Age (G2):** vor der Age einer Mod tut jede ihrer Tasten nichts und kein Fenster aus ihren Paketen öffnet sich (SPEC §18.10, Tabelle `kubejs/assets/firmages/key_gates.json` aus `dev/gen_key_gates.py`, Ausnahmen mit Grund im Skript); vor der Karte (`ui.mapFromAge`) zeigt F3 außer Systemdaten nur `???` (§18.11); JourneyMap-Vollbild auf M, FTB-Chunks-Karte auf L (§18.12). Kreativ offen, Operatoren im Survival gesperrt.
- **Keine Erklärtexte (G1, Felix 2026-10-17):** wird ein Feature entfernt, ersetzt oder freigeschaltet, bekommt der Spieler keinen Hinweistext (kein Chat, Toast, Tooltip, keine Questzeile); er entdeckt es selbst. Sperr-Rückmeldungen („Something Unknown“, „Not of this Age“) bleiben (`dev/style-guide.md`).
- **TFC-Überleben, Vanilla-Herzen (G1):** kein Durst (`tfc.disableThirst`), kein Ernährungs-Malus (`tfc.noNutritionMalus`), keine TFC-Lebensleiste: `config/tfc-client.toml` `enableHealthBar = false`, `defaultconfigs/tfc-server.toml` `[mechanics.player]` Minimum, Default und Maximum 1,0, also immer zehn Herzen (20 Lebenspunkte); nur neue Welten lesen `defaultconfigs/` (SPEC §18.6, Zeile „G1: Vanilla-Herzen statt TFC-Gesundheit“).
- **Kein Kram:** ein Item pro Material, eine Station pro Funktion und Age; kein nutzloser Block vor seiner Age (Entscheidungszeilen „Abschaltliste ab der Electric Age“, „Tally Chute erst ab der Industriezeit“).
- **Keine nervigen Mechaniken:** z. B. Advanced Chimneys ohne Rauchvergiftung, Vulkane ohne Blockschaden (Entscheidungszeilen „Rauch nur durch Kamine“, „Vulkane zeigen und rütteln nur“).
- **Echte Belohnungen:** Quest-Belohnungen sind echte Items; jede Nicht-Deko-Mod bekommt einen Quest-Strang (`dev/content-lock.md`).
- **Sprache:** Spielertext Englisch nach `dev/style-guide.md` (Questtext seit 1.0.1 in den SNBT-Dateien des Buchs, Felix editiert im Spiel: `dev/quests-editing.md`); Dev-Notizen Deutsch, Belege in `dev/poc-results.md` Englisch.
- **Holzklassen (HW1A):** Vanilla-Holz ist so viel wert wie das Holz seiner Klasse; natürlich gewachsene TFC- und AFC-Stämme droppen den Vanilla-Stamm ihrer Klasse. Tabelle `dev/wood_classes.toml`, Prüfungen W-1 bis W-9.
- **Dawn ohne 2x2-Feld:** das Inventarfeld öffnet mit der Steinzeit, Dawn craftet am Boden (bis vier Items mit V legen, schleichen und Rechtsklick); Abbau mit der Hand nur mit passendem Werkzeug (SPEC §19.2, §19.3, Prüfung D-1).
- **Eine Quelle je Himmelsmaterial** (SPEC §24.2, Prüfungen S-1 bis S-4).
- **V4A:** ab der Arkanzeit eine Himmelslegierung der Lens Crown pro Age im Schlüsselrezept (SPEC §27.8); `kubejs/startup_scripts/sky.js` schaltet diese Rezepte, solange jedes Himmelsmaterial registriert ist.
- **Tally-Zehnt ab der Industriezeit (H5A):** die Opfergabe braucht erst den Zehnt des Age-Metalls in Tally Chutes (`firmages_shrine/tallies.json`, SPEC §35).
- **Jede Age baut die nächste:** die Maschinen einer Age nehmen ein Teil der Age davor (`recipes/age_N/links.js`, Prüfung K-1).

## 3. Die Age-Leiter

Opfergabe und Token gehören zur Age, in der sie gemacht werden; der Schrein öffnet damit die nächste Age. Daten: `mod/firmages-core/src/main/resources/data/firmages/firmages_shrine/` (`offerings.json`, `tallies.json`, `tier/ring_N.json`), Token-Tag `#firmages:age_token/<age>` in `kubejs/server_scripts/tags/`.

| Age | Schlüssel-Mods (`[mods]` in `dev/age_map.toml`) | Opfergabe (Ring) | Age-Token | Legierung → Schlüsselrezept | Tally |
|---|---|---|---|---|---|
| Dawn `dawn` | TFC, AFC, Goblin Traders | Quest „The First Spark“ | | | |
| Stone `age_0` | TFC, Totemic | Hearthstone (0 Hearth Circle) | | | |
| Bronze `age_1` | Create, Firmalife, Supplementaries | Sky Disc (1 Bronze Sanctum) | | | |
| Iron `age_2` | TFCreate, Create Steam 'n' Rails, Tom's Storage | Steel Heart (2 Iron Sanctum) | Frontier Relic | | |
| Arcane `age_3` | Occultism, Ars Nouveau, Theurgy, Apotheosis | Arcane Keystone (3 Spirit Circle) | Hollow Keystone | Starlit Iron → Arcane Keystone | |
| Industrial `age_4` | Immersive Engineering, Create Crafts & Additions, Create Big Cannons | Pressure Core (4 Foundry Nave) | gebaute Stahl-Kanonenkammer | Cloudsteel → Pressure Core | 384 Stahl |
| Electric `age_5` | Immersive Petroleum, Create Diesel Generators | Humming Core (5 Tesla Crown) | Nether Star | Aurora Steel → Humming Core | 512 Aluminium |
| Information `age_6` | Mekanism, AE2, Mystical Agriculture, Psi | Data Matrix (6 Data Nave) | AE2-Singularität | Halo Steel → Data Matrix | 1.024 Osmium |
| Space `age_7` | Ad Astra, Draconic Evolution, MEGA Cells | Star Chart (7 Star Spire) | Flugschreiber (Mars-Tempel) | Void Steel → Star Chart | 2.880 Desh |
| Quantum `age_8` | Evolved Mekanism, Advanced AE | Quantum Core (8 Quantum Ring) | subatomare Legierung | Quasar Alloy → Quantum Core | 3.584 Calorit |
| Singularity `age_9` | Stargate Journey, Draconic Additions | keine; Ultimate Singularity, Sternentor nach The Origin | großes Chaos-Fragment | Firmament Alloy → Ultimate Singularity | |

Das Herz zieht zusätzlich FE: 10 M an der Data Nave, 100 M am Quantum Ring, 1 G für The Gathering (SPEC §36). Bester Erzfaktor `age_0` bis `age_9` (1.0.0 wie 0.13.0, `/firmages ore audit`): 1 / 1 / 1,5 / 1,5 / 2,5 / 2,5 / 2,857 bis 3 / 4 / 5 / 6.

## 4. Werkzeuge (aus dem Repo-Ordner)

- `python dev/derive_ages.py --scan --mods <Kopie>/mods`, dann `python dev/derive_ages.py --dump-dir <Gate-aus-Dump>`, `--check`: schreibt `[derived]` und `[derived_hidden]` in `dev/age_map.toml` (Age = später von Regel und erster Erreichbarkeit, nie erreichbar = `[derived_hidden]`); Q-1 prüft die Quest-Aufgaben (auch Entweder-oder-Quests), Q-2 Items nur aus Belohnungen (`REWARD_ONLY`); der Walk hält spät geladene Pack-Rezepte (`LATE_LOADED` in `poc_analyze.py`, `FirmAges.requireAge`) bis zu ihrer Age zurück.
- `python dev/gen_stage_locks.py` (`--check`, `--registry <Dump>`, `--explain <id>`): Age-Tags und Stage-Dateien aus `dev/age_map.toml`; nur Tabellen editieren, nie die Zeilen zwischen `# >>> generated` und `# <<< generated`.
- Questbuch seit 1.0.1 eingefroren (Felix Q1A): die SNBT-Dateien sind die Quelle, Felix editiert im Spiel (`dev/quests-editing.md`); `python dev/gen_quests.py` läuft nur mit `--unfreeze` und überschreibt sonst seine Änderungen. `python dev/validate_quests.py --style` (`--mods <Kopie>/mods` oder `--no-jars`) liest nur die SNBT-Dateien: 0 Fehler, 0 Warnungen (prüft auch Linienkreuzungen und Linien über Quests, höchstens 2 je Kapitel, Side Roads und Quest-Links und die Belohnungen kapitelübergreifend: höchstens ein Drittel einer offenen Aufgabe, nie ein Side-Roads-Task-Item, nie Token, Opfergabe oder Chaos, keine Fackel in Dawn; eine Arbeitsquest ohne Item-Belohnung und eine Lese-Quest mit Items sind Fehler); danach `git diff config/ftbquests/quests/lang/en_us.snbt` lesen. `python dev/preview_quests.py` zeichnet jede Karte nach `dev/quest-preview/` (Buchstabe = Icon, das der flache Renderer nicht kennt; roter Punkt = Quest ohne Icon). Belege 1.0.1 beim Einfrieren: 26 Kapitel, 667 Quests (The Firmament 25, Ages 333, Side Roads 291, Great Works 18); Dawn 21 Quests in fünf Strängen Stone, Shelter, Hunt, Forage, Sky mit je einem Keystone (Stone Kit, Shelter, Hunter, The Larder, A Shrine on the Road), „The First Spark“ wartet auf alle fünf; die Markierung „The First Spark“ in The Firmament wartet auf die Notiz „By Hand“; 1.0.0: 666 Quests (Ages 329, Side Roads 291, Great Works 18, The Firmament 28).
- `python dev/poc_analyze.py --server-dir <Kopie> --dump-dir <Dump> --baseline <Gate-aus-Dump>`: eine PASS/FAIL-Zeile je Prüfung, Exit 1 bei FAIL. Familien: G Gate, A Stage-Rezeptsperren, C Inhalt, D Dawn-Bodenhandwerk, E EMI je Age, R Erreichbarkeit (R-0 Vanilla-Stationen, R-X ohne Magie, R-M Multiblöcke, R-K), K Querverbindungen, L Erzleiter (L-8 kein wertschöpfender Metallkreis), U Vereinheitlichung, W Holzklassen, S Himmelsmaterialien, X Welt (Strukturen, Weltgenerierung, Vorlagen, Tierprodukte nach Age).
- `python dev/predict_dump.py <Dump> <Ziel>` und `--sync-locks <Ziel>`: Dump einer KubeJS-Rezeptänderung ohne Boot vorhersagen; der nächste echte Dump muss `derive_ages.py --check` bestehen.
- `python dev/check_log.py <Kopie>/logs/latest.log` (`--client` für Client-Logs): Problemzeilen gegen `dev/data/log_noise.json`, Ziel „remaining 0“.
- `python dev/check_mixin_refs.py --mods <Kopie>/mods --mods <Client-only-Jars>` (Runde G1b): liest die Mixin-Klassen aller Jars samt Jar-in-Jar ohne javap und meldet jede Klasse, die ein Mixin nennt und kein Jar hat (1.0.1: WoodenCog 1.2.19 gegen TFC 4.2.12, nur im Client); Ziel „MISSING: 0“, Exit 1 bei Fehlern; belegte optionale Mixins (Plugin, `@Pseudo`, Soft-Dependency) in `dev/data/mixin_refs_allow.json` je exaktem Jar-Namen.
- `python dev/gen_key_gates.py --mods <Client-Mods>` (Runde G2; `--check`; ohne `--mods` nach einer Age-Änderung; `--options <options.txt>` gleicht mit einem echten Client ab): Tasten- und Fenster-Gate je Mod-Age aus den Client-Jars (Bytecode der `KeyMapping`-Konstruktoren, Lang-Einträge, Pakete), schreibt `dev/data/key_gates.json` und `kubejs/assets/firmages/key_gates.json`; nach jedem Mod-Update mit dem vollen Client-Satz (`C:/Users/Felix/curseforge/minecraft/Instances/Firmament Ages/mods`) neu laufen lassen.
- `python dev/check_menus.py`: FancyMenu-Titel-, Pause- und Ladebildschirm, Shader-Defaults.
- `python dev/gen_textures.py` (`--check`): alle firmages-Texturen (KubeJS-Items, Mod-Blöcke).
- `python dev/gen_wood_classes.py` (`--check`): Drops, Brennwerte, Tags, Vanilla-Haine und Rezeptkonstanten aus `dev/wood_classes.toml`.
- `python dev/gen_fuels.py` (`--check`): Generatordaten aus `kubejs/data/firmages/balance/fuels.json`.
- `python dev/gen_machine_usage.py` (`--check --server-dir <Kopie>`): Mekanism-, More-Machine-, Generators- und IE-Configs als volle Dateien.
- `python dev/miner_ladder.py --check`: Digital Miner und Excavator gegen die Erzleiter (jede Stufe senkt die FE je Barren).
- `python dev/gen_wayshrines.py`, `python dev/gen_ark.py`, `python dev/gen_cold_forge.py` (je `--check`): Wayshrines samt Ruinen, Origin-Archipel, Cold-Forge-Vorlage.
- `python dev/gen_recipe_overrides.py --check --mods <Kopie>/mods`: Overrides für Mod-Rezeptdateien, die KubeJS nicht lesen kann.
- `python dev/gen_loot_silence.py` (`--check`): leere Loot-Tabellen für Compat-Blöcke fehlender Mods.
- `python dev/check_loot_ages.py` (`--mods <Kopie>/mods`, `--dump-dir`, `--table <id>`, `--list`, `--strict`): jede Loot-Tabelle mit der Age ihrer Items und dem Ort, ab dem sie sich öffnet; meldet Tabellen mit Items späterer Ages (informativ) und mit `--mods` die generierenden Strukturvorlagen mit vorbefüllten Behältern späterer Ages (der Core filtert sie beim Platzieren); `--strict` Exit 1 nur für `firmages:*` (Liste `LATER_ON_PURPOSE`). Prüfung X-5 in `poc_analyze.py`.
- `powershell -NoProfile -ExecutionPolicy Bypass -File dev\release.ps1 -Version X.Y.Z`: LF-Prüfung, `packwiz refresh`, Version in `pack.toml` und Titelbild, Exporte nach `dev/exports/`.
- `python dev\cf\export_cf.py`: CurseForge-Upload-Zip (Modrinth-Metadateien gegen `dev/cf/swap/` getauscht); hochladen nie.
- `python dev/run_server.py --server-dir <Kopie> --xmx 5G --hold 240 --timeout 900` (`--wipe-world`, `--cmd "<Befehl>"`): Boot, Konsolenbefehle, Zusammenfassung, zum Schluss `stop` und `stop confirm` (firmages-core fragt seit 1.0.2 vor dem Stopp, `/restart` startet über die Startskripte neu, SPEC §10.1); nur auf einer Kopie (Abschnitt 6).

## 5. Wo was liegt

- firmages-core `mod/firmages-core/src/main/java/dev/firmages/core/`:
  - `age/` Age-Zustand, Reload, `AgeSwap` (SPEC §2, §3, §4.8); `gate/` Maschinenrezept-Gate (§4); `miner/`, `reactor/` (§5, §6).
  - `shrine/` Herz, Plinth, Zustandsmaschine, Blessings (`Blessings`, `BlessingIndex`, `BlessingSources`), Consecration (`ShrineConsecration`), The Gathering (§7, §17, §29.5); `ceremony/` (§8); `proof/` Tally Chute und Silo-Start (§22, §35).
  - `origin/` The Origin, Arena, Rücktor (§16); `boss/` The Primordial, Kampfmodell (§16.4, §39).
  - Himmel: `sky/` Starfall und Sky Harvest (§25, §26), `crown/` Lens Crown (§27), `isles/` (§30), `launch/` (§31.3 bis §31.7), `ark/` Origin Archipelago (§31.8), `wayshrine/` (§28), `anchor/` Sky Anchors und Blessing Shards (§29).
  - `balance/` Erzleiter, Audits, FE-Deckel, One Iron (§34, §37, §38); `machine/` Ore Processor (§40) und Arcane Catalyst (§41).
  - `hardship/` `ToolRule` (Abbau mit der Hand, §19.2), `GridLock` und `GroundCraft` (Dawn-Feld, Bodenhandwerk, §19.3), Cave Dweller (§19.1); `immersion/` EMI, Jade, HUD nach Age (§18, §21).
  - `loot/` Loot-Gate `firmages:age_filter` (§19.5): jedes erzeugte Loot behält nur Items der Ages des Spielers des Wurfs (sein Team), ohne Spieler der Server-Ages; `age_items/disabled` fällt immer; `mixin/LootModifierManagerMixin` stellt ihn hinter alle anderen Loot-Modifier. Vorbefüllte Behälter platzierter Strukturvorlagen filtert `mixin/StructureTemplateMixin` mit `TemplateContents` und `PlacedContents` nach der Server-Age, verschachtelte Inhalte eingeschlossen.
  - `net/` Netzprotokoll `"7"` (`FirmagesNetwork.VERSION`, §11; seit 1.0.1); `compat/<mod>` und `mixin/<mod>` laden nur mit der Mod. Mod-Updates von Enhanced Falling Trees und JourneyMap vorher per `javap` gegen die Mixin- und Reflection-Ziele prüfen (`dev/release.md`, „Mod-Updates von Enhanced Falling Trees und JourneyMap“).
  - Config (§9): `firmages-server.toml` (Pack-Kopie `config/firmages-server.toml`) mit `gate miner prospecting reactor shrine sanctuary wayshrine anchor crown launch origin ark boss sync team ui tfc hardship perf starfall skyHarvest isles balance machines debug`; `firmages-client.toml` mit `effects music ui chat tfc`.
  - Tests: `/firmages selftest [all|core|gate|server|sky|crown|wayshrines|isles|launch|ark|endgame]` (`command/*Suite.java`); GameTests in `src/gametest/java/dev/firmages/core/gametest/*GameTests.java` (26 Klassen), JUnit in `src/test/java/`.
- KubeJS:
  - `kubejs/startup_scripts/00_constants.js`: `global.FA` (Stages, Titel, Mob-Leiter, `CHUNK_QUOTA`, `HIDDEN_ITEMS`, `WOOD_CLASSES`); `metal_table.js` (`global.FA_METALS`), `sky.js`, `items.js`.
  - `kubejs/server_scripts/recipes/age_N/*.js` (Rezepte je Age, `links.js` Querverbindungen) und übergreifende `recipes/*.js`; `stages/grants.js` leitet Mob-Leiter, Karte, Stationsfenster und Chunk-Kontingent aus dem Age-Stage ab; `tags/` (Age-Token, `hidden_from_viewers.js`); `debug/dump.js` (`/fa_dump`, `/fa_dump_full`).
  - Tags (generiert): `kubejs/data/firmages/tags/item/age_items/<stage>.json` (`dawn`, `age_0` bis `age_9`, `disabled`), `tags/block/age_blocks/`, `tags/fluid/age_fluids/`; statisch versteckt: `kubejs/data/c/tags/item/hidden_from_recipe_viewers.json`.
  - Balance: `kubejs/data/firmages/balance/metals.json` (Metalle, Stationen, Faktoren) und `fuels.json` (FE je Eimer).
- `dev/age_map.toml`: die eine Quelle der Age je Item. Abschnitte `[mods]`, `[materials]`, `[mod_materials.*]`, `[items]`, `[ore_families]`, `[ore_blocks]`, `[tfc_ores]`, `[tfc_ores_visible]`, `[tfc_metals]`, `[tfc_powders]`, `[small_ore_display]`, `[recipes]`, `[hidden]`, `[hidden.parity]`, `[dawn_whitelist]`, erzeugt `[derived]`, `[derived_hidden]`.
- Stage-Dateien `config/progressivestages/stages/*.toml`; Questbuch `config/ftbquests/quests/`, bis 1.0.0 erzeugt von `dev/gen_quests.py`, seit 1.0.1 eingefroren (Layout `dev/quest_layout.py`, `LAYOUT_HINTS`; Vorschau `dev/preview_quests.py`). Gruppen: „Ages“ (11 Age-Kapitel), „Great Works“ (drei Bauten, Pläne hängen an „Flux Mill“, „Brass“, „Launch Pad“ mit `quest_links`), „Side Roads“ (B2: ein Kapitel je Age, `ROADS`, 59 Straßen für die Nicht-Deko-Mods, Reihen von 2 bis 7 Nebenquests, jede Quest mit prüfbarer Aufgabe). Belohnungen seit B2 fest je Quest (`T()` Team, `P()` je Spieler), keine Tabellen.
- Quest-Bilder: `kubejs/assets/firmages/textures/quests/<gruppe>/` (`dev/gen_quest_images.py`), Schrein- und Werke-Bilder `kubejs/assets/firmages/textures/gui/quests/` (`dev/gen_quest_pictures.py`); KubeJS lädt `kubejs/assets` als Client-Resourcepack (`dev/quest-images.md`).

## 6. Prüfregeln vor einem Commit

1. `cd mod/firmages-core`, `JAVA_HOME` = `tools/jdk-21`, `gradlew build`: BUILD SUCCESSFUL, 0 Testfehler.
2. `gradlew runGameTestServer` in einem frischen `run/gametest`: „All N required tests passed“ (1.0.2: 127; 1.0.1: 125).
3. Jedes betroffene `gen_*.py --check`, dazu `check_menus.py` und `miner_ladder.py --check`: alles aktuell.
4. `python dev/derive_ages.py --check` und `python dev/gen_stage_locks.py --check`: up to date.
5. `python dev/validate_quests.py --style`: 0 Fehler, 0 Warnungen.
6. `python dev/poc_analyze.py ...`: alle PASS, 0 FAIL.
7. Boot auf einer Kopie, `check_log.py`: remaining 0. Boots macht nur der Integrator.
8. Nach jedem Mod-Update (Pflicht seit 1.0.2): `python dev/check_mixin_refs.py --mods <Kopie>/mods --mods <Client-only-Jars>`: „MISSING: 0“. Ein Server-Boot zeigt Client-Mixins nicht (`dev/release.md`, „Nach jedem Mod-Update“).

- Textdateien LF (`.gitattributes`; CRLF bricht die Hashes in `index.toml`). Autor `PhoenixFlyy <97892522+PhoenixFlyy@users.noreply.github.com>`, Trailer `Co-Authored-By: Claude <Modell> <noreply@anthropic.com>` mit dem Modell, das committet.
- Nie pushen, nie zu CurseForge hochladen, `test-server/` nie anfassen (Felix' Server).
- Boot-Kopie: `robocopy test-server ..\.wt\test-server-<name> /E /XD world logs local` (Ziel `D:/Minecraft/MinecraftModServer/.wt/test-server-<name>`), `kubejs/` der Kopie vor dem ersten Sync löschen; in `server.properties` ein freier Port über 25566, `rcon.port` = Port + 10, Whitelist aus.
- Sync: `tools\packwiz.exe refresh`, dann `tools\packwiz.exe serve -p <p>` im Arbeitsbaum (mit `--refresh=false` liest `serve` den Index nur beim Start: nach neuen Dateien neu starten, sonst 404); `tools\packwiz-installer-bootstrap.jar` in die Kopie legen und dort (Arbeitsordner = Kopie) mit `tools\jdk-21\bin\java` `-jar packwiz-installer-bootstrap.jar -g -s server http://localhost:<p>/pack.toml` bis „Finished successfully“; danach in `<Kopie>/config/firmages-server.toml` `debug.allowSimulate = true` und `gate.enabled` (aus für Dumps) setzen.
- Boot: `python dev/run_server.py --server-dir <Kopie> --xmx 5G --hold 240 --timeout 900`; Befehle z. B. `firmages ages simulate grant age_9`, `fa_dump`, `fa_dump_full`, `firmages selftest all`. Kopie danach löschen.

## 7. Offene Punkte und Entscheidungen

- Keine offene Entscheidung: die neun Fragen von 1.0.0 und die Codes M1A (JourneyMap ab der Eisenzeit), T1A (Enhanced Falling Trees) und Q1A (Questbuch eingefroren) sind beantwortet und umgesetzt („Erledigt in Runde G1“ in `dev/offene-punkte.md`; Zeilen „G1: …“ in `dev/decisions-while-away.md`).
- Im Client (Felix): die „1.0.1 Checkliste“ oben in `dev/client-test-brief.md` (Runde G1, gilt für 1.0.2 samt den Server-Zeilen 19 bis 21), darunter die „1.0 Checkliste“ (alle offenen Client-Punkte von 0.10.0 bis 1.0.0 nach Age; die Abschnitte darunter sind Archiv), dazu die Client-Zeilen in `dev/offene-punkte.md`.
- Nach 1.0 (`dev/offene-punkte.md`, „Nach 1.0“): Pacing-Modell als `dev/pacing.py` ins Repo holen und an einem Durchgang eichen, Questbuch-Ideen, Wayshrine-Dichte, Teleporter- und Warp-Sperre in The Origin, unverifizierte Config-Keys, Live-Messungen.
- Basis für `check_registry_parity.py dump`: `dev/data/baseline-1.0.2/` (Gate-aus-Dump der Runde G1b mit TFC 4.2.11, `registries.json.gz`, `recipes.json.gz`).
- Entscheidungen: eine Zeile in `dev/decisions-while-away.md`, Deutsch, Format `| Datum | Thema | Entscheidung | Warum | So änderst Du es |`, neue Zeilen unten, Thema mit Runde als Präfix (z. B. „Integration F1: …“).
- Offene Punkte: `| Was | Wo | Wer |` in `dev/offene-punkte.md`; Erledigtes mit Beleg nach unten. Belege jeder Runde: `dev/poc-results.md` (`| Check | Observed | Result |`).
