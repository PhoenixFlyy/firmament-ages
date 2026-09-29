# Config-Todo: Sollwerte ohne verifizierten Key

Stand 2026-09-30. In `config/` und `defaultconfigs/` steht nur, was aus dem Quellcode der gepinnten Version belegt ist. Alles andere steht hier, mit dem gewünschten Wert. Wer einen Key im PoC findet, schreibt die Datei und streicht die Zeile.

## Geschrieben und verifiziert (Referenz)

| Datei | Keys | Beleg |
|---|---|---|
| `config/progressivestages/progressivestages.toml` | `general.starting_stages`, `team_mode`, `linear_progression`, `team.persist_stages_on_leave`, `enforcement.mask_locked_item_names`, `jei.enabled`, `emi.enabled`, `emi.show_locked_recipes`, `integration.ftbteams.enabled`, `integration.ftbquests.enabled` | `StageConfig.java`, Tag v3.0.5 |
| `config/progressivestages/stages/*.toml` | `[stage]`, `[items]`, `[recipes].locked_ids/locked_items`, `[screens]`, `[dimensions]`, `[mobs]`, `[[mobs.replacements]]`, `[[ores.overrides]]`, `[advancements]`, `always_unlocked` | `StageFileParser.java` und `DOCUMENTATION.md`, Tag v3.0.5 |
| `config/ftbchunks-world.snbt` | `disable_protection`, `pvp_mode`, `require_game_stage`, `force_disable_minimap`, `claiming.max_claimed_chunks`, `claiming.party_limit_mode`, `force_loading.max_force_loaded_chunks`, `force_loading.force_load_mode` | `FTBChunksWorldConfig.java`, Tag v2101.1.22; Enum-Namen klein (FTB Library `NameMap`) |
| `config/defaultoptions/keybindings.txt` | `key_key.ftbquests.quests:key.keyboard.k` | Default Options `KeyMappingDefaultsHandler` (1.21.1); Name `key.ftbquests.quests` aus `FTBQuestsKeyMappings` + FTB Library `KeyMappingConfig.translationKey()` |
| `defaultconfigs/tfc-server.toml` | `mechanics.vanillaChanges.enableVanillaMonstersOnSurface = true` | `ServerConfig.java`, Tag v4.2.11 |
| `defaultconfigs/immersiveengineering-server.toml` | `ores.<vein>.vein_size = 0` (bauxite, lead, silver, nickel, deep_nickel, uranium), `ores.retrogen_excavator_veins = false` | `IEServerConfig.java`, Tag 12.4.2-194 |
| `defaultconfigs/Mekanism/world.toml` | `<ore>.shouldGenerate = false` (tin, osmium, uranium, fluorite, lead), `salt.shouldGenerate = false` | `WorldConfig.java`, `PrimaryResource.java`, `MiscResource.java`, Tag v1.21.1-10.7.19.85 |
| `kubejs/data/create/neoforge/biome_modifier/{zinc_ore,striated_ores_overworld,striated_ores_nether}.json` | `neoforge:none` | Create-Repo, Tag mc1.21.1-6.0.10 |
| `kubejs/data/occultism/neoforge/biome_modifier/{add_ore_silver,add_ore_silver_deepslate}.json` | `neoforge:none` | Occultism-Repo, Tag release/v1.21.1-1.224.4 |

## Offen: Key nicht verifiziert

| # | Mod | Datei | Key | Sollwert | Warum / Quelle der Absicht |
|---|---|---|---|---|---|
| 1 | FTB Quests | `config/ftbquests/quests/data.snbt` | `drop_book_on_death` | `false` | Key in `BaseQuestFile.java` belegt. Die Datei gehört aber zum Questbuch; eine Teil-Datei würde es überschreiben. Beim Anlegen des Questbuchs setzen. `disable_gui` bleibt `false`. |
| 2 | Apotheosis 1.21.1-8.9.0 | Befehl, keine Datei | Weltstufe setzen | Haven bis Age 2, Frontier Age 3–4, Ascent 5–6, Summit 7–8, Pinnacle 9 | Befehlssyntax unbekannt; Platzhalter in `kubejs/server_scripts/stages/grants.js` |
| 3 | Mystical Agriculture 8.0.28 | `config/mysticalagriculture-common.toml` | `[World] generateProsperityOre`, `generateInferiumOre`, `generateSoulstone` | alle `false` | Dok. 08 §3.1; Keys nur aus der Research, nicht aus dem Quellcode |
| 4 | Mystical Agradditions 8.0.14 | `kubejs/data/mysticalagradditions/neoforge/biome_modifier/*.json` | Dateinamen | `neoforge:none` | Dok. 10 v3 §7.1 „Dateinamen PoC“ |
| 5 | Draconic Evolution 3.1.4.633 | `kubejs/data/draconicevolution/neoforge/biome_modifier/{overworld,nether}_draconium_ore.json` | Dateinamen | `neoforge:none` | Dok. 08 §3.1; Namen nicht im Repo geprüft (nicht im PoC-Umfang Dawn–Iron) |
| 6 | Draconic Evolution | Brandon's-Core-Config (Pfad unbekannt) | `reactorFuelUsageMultiplier`, `reactorOutputMultiplier`, `guardianHealth`, `chaosDropCount`, `dragonDustLootModifier` | etwa 0,5 / 1–2 / PoC / 5 / etwa 16 | Dok. 10 v3 §7.4 |
| 7 | AE2 19.2.18 | Biome-Tag | `ae2:has_meteorites` leeren (Name unverifiziert) | leer | Meteoriten aus |
| 8 | TFC + IE Crossover 2.1.3 | Datapack-Override | Quarz-Geode | aus | TFCreate-Quarz-Ader ist kanonisch |
| 9 | Create: Diesel Generators | Common-Config | Öl-Vorkommen | aus | Dok. 10 v3 §7.1 |
| 10 | Create Crafts & Additions 1.6.0 | Common-Config | Alternator-FE-Rate | gedeckelt | Create darf nicht die billigste FE-Quelle sein |
| 11 | Create: Enchantable Machinery | Config | Verzauberungs-Blacklist | Fortune, Looting | Erz-Leck |
| 12 | Create: Bits 'n' Bobs | Config | Schwungrad als Speicher | aus | kein Passiv-Speicher |
| 13 | Create Mechanical Spawner | Config | Zufalls-Spawn-Fluid | aus | nur Pack-Fluids pro Mob-Stage |
| 14 | Mowzie's Mobs 1.8.2 | `config/mowziesmobs-common.toml` | `biome_tags`, `allowed_block_tags` pro Mob | TFC-Konventions-Tags, `tfc:grass` | `research-raw/r3-misc.md` §1 |
| 15 | Mowzie's Mobs | `kubejs/data/mowziesmobs/tags/worldgen/biome/has_structure/has_mowzie_structure.json` | Tag erweitern | `#c:is_snowy`, `#c:is_mountain`, `#c:is_hill`, `#c:is_dry`, `#c:is_hot/overworld`, `#c:is_wet/overworld` | Dok. 08 §5.2; Tag-Pfad aus der Research |
| 16 | Mekanism Additions | Config | Baby-Mobs | aus | Dok. 10 v3 §7.2 |
| 17 | Enhanced Celestials 6.0.2.6 | Config | Blutmond erst ab `mob_3` | an, aber gestaged | Wie das Mod-Event an eine Stage hängt, ist offen |
| 18 | Stargate Journey 0.6.49 | Config | Strukturen | aus | Dok. 08 §6 |
| 19 | ProgressiveStages | `progressivestages.toml` | `enforcement.reveal_stage_names_only_to_operators` | Entscheidung: Default `true` (Spieler sehen „Progress further“) oder `false` (sie sehen „Age 1: Bronze Age“) | Key belegt, Wert ist Deine Entscheidung |
| 20 | FTB Chunks | `config/ftbchunks-world.snbt` | `force_loading.force_load_mode` | geschrieben `"never"` | Entscheidung offen: `"always"` lässt Fabriken ohne Spieler laufen, obwohl der TFC-Kalender dann steht (Dok. 08 §13 Punkt 11) |
| 21 | Default Options | `config/defaultoptions/keybindings.txt` | Taste K | frei? | Kollision mit einer anderen Mod im PoC prüfen (Controlling-Suche) |
| 22 | TFC + IE Crossover | Stage-Dateien | Block-IDs der TFC-IE-Adern (Bleiglanz Age 4, Bauxit Age 5, Uraninit Age 7) | in `dev/gen_stage_locks.py` eintragen | IDs pro Gestein unbekannt; der Generator kennt bisher nur TFC-Erze |
