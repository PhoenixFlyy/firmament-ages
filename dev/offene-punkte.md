# Offene Punkte

Alle offenen Punkte an einer Stelle, Stand 2026-10-14 (0.9.0-Runde). Gesammelt aus `dev/config-todo.md` (archiviert),
den „Offen“-Listen in `dev/client-test-brief.md`, „Open points“ in `dev/quests-notes.md` und den „Open points“-Abschnitten
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
| Deko in TFC/AFC-Hölzern später als geplant (Nachtlauf N3): Macaw's „classic“-Türen ab Eisenzeit (Every Compat behält den Vanilla-Holzdruck `mcwdoors:print_<holz>` als Zutat), Handcrafted-Theken ab Arkanzeit (Calcit). Rezept anpassen oder so lassen? Und: Vanilla-Holz-Varianten der Basis-Mods (etwa 750 EMI-Einträge ab Eisenzeit) verstecken? | `dev/data/derived_ages.json`, `kubejs/server_scripts/recipes` | Felix |
| Bekannte Lücken mit Regel-Age: Apothic-Enchanting-Tisch braucht Diamant (Industriezeit), die Arkane-Nebenquest „Enchantment Industry“ hängt daran; das Create-Dragons-Plus-Blaze-Template hat keine sichtbare Quelle. Quest oder Rezept ändern? | `dev/decisions-while-away.md` (Zeile „Bekannte Lücken bleiben sichtbar“) | Felix |

## Im Client prüfen (Felix)

| Was | Wo | Wer |
|---|---|---|
| Alles aus den Abschnitten von `dev/client-test-brief.md` (pro Version, neueste oben) | `dev/client-test-brief.md` | Felix |
| Titelbild: nach dem ersten Blick auf das Shader-Sternbild `show_compile_errors = true` wieder auf `false` | `config/fancymenu/customization/title_screen_layout.txt` | Felix sieht, Claude stellt um |
| Blutmond erst ab `mob_2`: `/ec lunarForecast` vor und nach der Eisenzeit | `kubejs/server_scripts/firmages/blood_moon.js` | Felix |
| Industriezeit-Quest „The Thresher“ schließt beim Blick auf einen geformten Thresher ab; Loot-Crate-Opener und Task-Screen-Konfigurator fehlen in EMI | Questbuch, EMI | Felix |
| IE-Handbuch-Vorschau von Mahlwerk, Webstuhl, Lichtbogenofen, Schaufelrad, Bagger, Koker und Pumpjack zeigt den TFC-Stahlblock | IE-Handbuch | Felix |
| Endkampf: Länge des Kampfs gegen The Primordial (1024 Leben, Cataclysm-Schadensdeckel) mit Chaos-Ausrüstung | The Origin | Felix |
| Neue Sperrtexte (0.9.0): gesperrtes Item zeigt „Something Unknown“ und eine graue Zeile „Not of this Age“, Hotbar-Zeile „… Their Age has not come.“, keine Fehler im Log | `config/progressivestages/progressivestages.toml` `[messages]` | Felix |

## Inhalt und Config (Claude)

| Was | Wo | Wer |
|---|---|---|
| Apotheosis-Weltstufe pro Age (Haven bis Pinnacle): Befehlssyntax unbekannt, nur Platzhalter | `kubejs/server_scripts/stages/grants.js` (POC-Kommentar) | Claude, mit Server-Test |
| TFC + IE Crossover: Quarz-Geode abschalten (die TFCreate-Quarzader ist kanonisch) | Datapack-Override, Key nicht verifiziert | Claude |
| Create Crafts & Additions: Alternator-FE-Rate deckeln (Create darf nicht die billigste FE-Quelle sein) | Common-Config, Key nicht verifiziert | Claude |
| Create: Enchantable Machinery: Fortune und Looting auf die Blacklist (Erz-Leck) | Config, Key nicht verifiziert | Claude |
| Create: Bits 'n' Bobs: Schwungrad als Speicher aus | Config, Key nicht verifiziert | Claude |
| Create Mechanical Spawner: Zufalls-Spawn-Fluid aus | Config, Key nicht verifiziert | Claude |
| Mowzie's Mobs: Biome- und Block-Tags auf TFC (`tfc:grass`), Struktur-Tag erweitern | `config/mowziesmobs-common.toml`, `kubejs/data/mowziesmobs/…/has_mowzie_structure.json` | Claude |
| Mekanism Additions: Baby-Mobs aus | Config, Key nicht verifiziert | Claude |
| Stargate Journey: Strukturen aus | Config, Key nicht verifiziert | Claude |
| IE-Bagger: Mineral-Mix mit Osmium (Dok. 08 §3.2) | `kubejs/server_scripts` | Claude |
| Sieben IE/IP-Multiblöcke geben beim Abbau den gesperrten `immersiveengineering:storage_steel` zurück | `dev/gen_multiblock_templates.py` | Claude |
| Questbuch: FE-Nachweise sind Blick-Aufgaben; nach einem Client-Test auf `forge_energy`- oder Task-Screen-Aufgaben umstellen | `dev/gen_quests.py` | Claude |
| Questbuch: Automatisierungsnachweise per Task-Screen (`task_screen_only`) statt Mengen | `dev/gen_quests.py` | Claude |
| „Data Model: Tier 3“ nimmt jedes Datenmodell (die Stufe ist eine Komponente, die eine Item-Aufgabe nicht liest) | `dev/gen_quests.py` | Claude |
| Planungsdokumente: `08-Ages-Design.md` bekommt „§0 Stand 0.8“ (Abweichungen seit v3), `10-Modliste.md` aus `mods/*.pw.toml` erzeugen (`dev/cf/gen_credits.py --modlist`) | `../Modpack-Planung/docs/` | Claude |
| `mod/firmages-core/README.md` sagt noch „Current state: milestone M1“: eine Zeile mit Verweis auf `SPEC.md` | `mod/firmages-core/README.md` | Claude (mit der nächsten firmages-core-Version) |
| Java-Fallback-Texte der Mod stimmen nicht mehr mit der Lang-Datei überein („The world realigns...“, „No shrine heart is placed“) | `FirmagesClient.java`, `ReloadStatePayload.java`, `ShrineCommands.java` | Claude (mit der nächsten firmages-core-Version) |

## Prüfen, ob noch offen

| Was | Wo | Wer |
|---|---|---|
| Sequenzierte Montagen ohne Ausgabe (`simulated:…gyroscopic_mechanism`, `…engine_assembly`, `create_connected:…control_chip`, `create_factory_logistics:…fluid_mechanism`): wegen eines gesperrten Create-Blechs als Zufallsausgabe gestrichen; brauchen einen `byproducts.js`-Eintrag, falls kein anderes Rezept sie macht | `dev/poc-results.md` „Content fixes after the late Ages“ | Claude, im nächsten Dump |
| `createdeco`-Schiffscontainer brauchen das gesperrte Create-Eisenblech | Rezepte | Claude, im nächsten Dump |
| Sieben `create_dragons_plus:sandpaper_polishing/*`-Rezepte fehlen im Dump (Ursache unbekannt) | `dev/poc-results.md` „Osmium vein“ | Claude |
| Mekanisms Teleporter und Ars-Warp in The Origin sperren; Trophäen-Denkmal | firmages-core | Claude |
| Welt, in der ein Spieler allein ohne Party eine Age erreicht hat (Felix' Welt: HiighHopes, age_9, keine Party): ProgressiveStages 3.0.6 nimmt die Stages beim Gründen einer Party nicht mit (Pack-Server, Review 0.9.0: 22 Stages vorher, 0 danach). firmages-core gründet darum dort keine Party; ein zweiter Spieler liest „... outside a party ... Ask an operator.“. Offen: geprüfter Weg, die Stages in eine neue Party zu übertragen (z. B. Party gründen, dann `/stage grant` pro Age und `/fa_reconcile`, ohne Zeremonien und Firmament Nights erneut auszulösen) | `server/README.md`, firmages-core | Claude, mit Server-Test auf einer Kopie |
| Flüssigkeits-Tags `age_fluids/<stage>` (SPEC §2.1) werden nicht erzeugt | `dev/gen_stage_locks.py` | Claude |
| Reload-Zeiten auf einem leeren Rechner neu messen (`firmages reload` dreimal bei age_9) | Test-Server | Claude |
| Mekanism-Strahlung, Fusions- und Reaktor-Balance (Dok. 10 v3 §7.4 nur für DE gesetzt) | Configs | Claude |

## Erledigt (diese Runde geprüft)

| Was | Beleg |
|---|---|
| `drop_book_on_death = false` (config-todo 1) | `config/ftbquests/quests/data.snbt`, `validate_quests.py` prüft es |
| Draconic-Evolution-Werte (config-todo 6) | `config/brandon3055/DraconicEvolution.cfg`: Brennstoff 0,5, Ausgabe 1,0, Chaos-Drops 5, Drachenstaub 16 |
| AE2-Meteoriten (config-todo 7) | entstehen in der TFC-Welt nicht; die Pressen gibt es per Rezept (`recipes/age_6/information_age.js`) |
| Create: Diesel Generators ohne Öl-Chunks (config-todo 9) | `config/createdieselgenerators-server.toml` |
| `reveal_stage_names_only_to_operators` (config-todo 19) | Standard `true` bleibt; die Spielertexte der Sperre stehen jetzt in `[messages]` (0.9.0) |
| Taste K frei (config-todo 21) | `dev/keybinds.md`: Shader-Schalter von K gelöst |
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
