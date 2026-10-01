# Quest book (FTB Quests 2101.1.36)

Date: 2026-09-30, Ages 3 to 9 added 2026-10-01. Files: `config/ftbquests/quests/` (data.snbt, chapter_groups.snbt, chapters/, reward_tables/,
lang/en_us.snbt). Generator: `dev/gen_quests.py`. Offline check: `dev/validate_quests.py`. Design: Doc 08 v3
sections 2 and 9, Doc 11 section 3 (shrine).

## How to change the book

- The generator is the source for now. Edit `dev/gen_quests.py`, run it, then run
  `python dev/validate_quests.py --mods <path to test-server/mods>` (jar lookups for advancements, entities, tags and
  dimensions; `--no-jars` skips them). The generator is deterministic: ids come from sha1 of stable keys, so
  re-running it keeps every quest id and the teams' saved progress.
- Once someone edits the book in game (FTB Quests editor), the SNBT files become the source. From then on, do not
  re-run the generator over them; keep running the validator.
- All player text is in `lang/en_us.snbt` (keys `<type>.<16-hex id>.<field>`, the 2101 translation-table format).
  The chapter files hold no text.
- After changing served files, run `tools/packwiz.exe refresh` in the main checkout and commit index.toml and
  pack.toml (not done on this branch: worktree rule).

## Chapters

| File | Group | Title | Visible | Quests | Content |
|---|---|---|---|---|---|
| `welcome.snbt` | default | Welcome | always | 6 | checkmarks: pack overview and K key, the Ages and recipe/EMI/ore locks, how a chapter reads, one team, the shrine and Caelum, survival notes |
| `the_firmament.snbt` | default | The Firmament | always | 11 | one quest per Age goal: The First Spark, Hearthstone, Sky Disc, Steel Heart, Arcane Keystone, Pressure Core, Humming Core, Data Matrix, Star Chart, Quantum Core, Beyond the Firmament. Gamestage tasks (age_0 ... age_9, finale_won), `progression_mode: linear` + `hide_quest_details_until_startable`, so the whole road is visible from minute one and each quest opens only after the previous goal. Every signature item is the icon of its quest; the last, locked goal (the final boss in The Origin, stage `finale_won`) shows the classic Stargate |
| `dawn.snbt` | Ages | Dawn | stage `dawn` (start) | 15 | entry, strands Stone / Shelter / Hunt, goal The First Spark, 2 side missions, 1 explanation |
| `stone_age.snbt` | Ages | Age 0: Stone Age | stage `age_0` | 27 | entry, strands Fire & Clay / First Metal / Camp / Hearth & Shrine (ends in Raise the Shrine), goal Offer the Hearthstone at the Shrine, 3 side missions, 3 explanations |
| `bronze_age.snbt` | Ages | Age 1: Bronze Age | stage `age_1` | 31 | entry, strands Smithing / Prospecting / Alloys / Kinetics, goal Offer the Sky Disc at the Shrine, 4 side missions, 2 explanations |
| `iron_age.snbt` | Ages | Age 2: Iron Age | stage `age_2` | 29 | entry, strands Metallurgy / Power & Motion / Survival / Frontier, goal Offer the Steel Heart at the Shrine, 3 side missions, 2 explanations |
| `arcane_age.snbt` | Ages | Age 3: Arcane Age | stage `age_3` | 34 | entry, strands Spirits (Nether via Beneath, Djinni) / Source (Wilden Chimera) / Alchemy, goal Offer the Arcane Keystone at the Shrine, 4 side missions (Blaze Burner, Enchantment Industry, Ars Creo, Umvuthi), 2 explanations, ring Raise the Spirit Circle |
| `industrial_age.snbt` | Ages | Age 4: Industrial Age | stage `age_4` | 37 | entry, strands Coke & Steel (Netherite Monstrosity) / Power / Ore Line / Logistics & Backfill, goal Offer the Pressure Core at the Shrine, 4 side missions (Airship, Jetpack, Mechanical Spawner, Frostmaw), 3 explanations, ring Raise the Foundry Nave |
| `electric_age.snbt` | Ages | Age 5: Electric Age | stage `age_5` | 32 | entry, strands Power Grid / Oil / Aluminium & Excavator / Circuits (The Wither), goal Offer the Humming Core at the Shrine, 4 side missions (Afrit, Garden Cloche, Hypertubes, Sculptor), 2 explanations, ring Raise the Tesla Crown |
| `information_age.snbt` | Ages | Age 6: Information Age | stage `age_6` | 35 | entry, strands Digital / Mekanism / Mob Data / The End (Ender Dragon), goal Offer the Data Matrix at the Shrine, 4 side missions (Mystical Agriculture, Archmage, Mekanism Jetpack, Ender Guardian), 2 explanations, ring Raise the Data Nave |
| `space_age.snbt` | Ages | Age 7: Space Age | stage `age_7` | 33 | entry, strands Atomic / Rocketry & Moon / Mars (The Harbinger) / Draconium, goal Offer the Star Chart at the Shrine, 4 side missions (Digital Miner, Induction Matrix, MEGA Cells, Ancient Remnant), 2 explanations, ring Raise the Star Spire |
| `quantum_age.snbt` | Ages | Age 8: Quantum Age | stage `age_8` | 32 | entry, strands Fusion / Inner Planets / Awakened (The Leviathan) / Marid, goal Offer the Quantum Core at the Shrine, 3 side missions (Draconic Tools, Ore Times Five, Scylla), 2 explanations, ring Raise the Quantum Ring |
| `singularity_age.snbt` | Ages | Age 9: Singularity Age | stage `age_9` | 22 | entry, strands Chaos (Chaos Guardian, Reactor Online) / Singularity (keystone The Ultimate Singularity, checkmark) / Stargate (Gate Online), goal Beyond the Firmament (gamestage task `finale_won`), 3 side missions (Chaotic Armor, Chaotic Staff, Ignis), 2 explanations, no ring |

Total 344 quests (225 of them in the Ages 3 to 9), 21 reward tables, 1277 object ids. Counts per chapter come from the
validator output. Ring quests per chapter: 1 in Ages 3 to 8.

Chapter ids: welcome `2F7890D901468DC7`, the_firmament `7D0742BC787AAFAC`, dawn `544197C921D77FCB`, stone_age
`1ECA2119E26A0A06`, bronze_age `5546E96FD2574ABB`, iron_age `1D01936AE318F34B`, arcane_age `40D4D715E3F4DB36`,
industrial_age `66BF343917FA056F`, electric_age `12AE834BF6E07368`, information_age `079BA6A3BF85646D`, space_age
`172FAEFD3F2102E6`, quantum_age `795C8B99823402E1`, singularity_age `4472107CE8ED9B80`; group Ages `7E67E59034114849`.

### Structure of an Age chapter (Doc 08 section 9.1)

- Entry quest (hexagon, size 2): gamestage task of the chapter's own stage, no cross-chapter dependency, so an admin
  `/stage grant` also opens the chapter correctly.
- 3 or 4 required strands, each a dependency chain from the entry to a keystone (hexagon, size 1.5, tag
  `keystone`). Keystone reward: XP and one random item from the Age's supplies table (team reward).
- One goal quest (gear, size 3, tag `goal`) that depends on every keystone. From the Stone Age on: a gamestage task
  on the next Age ("Offer the X at the shrine"), icon = the signature item, no stage reward (section "Age grants").
  Dawn: item and advancement tasks plus the `age_0` stage reward.
- Side missions: `optional: true`, circle, tag `side`; reward XP and a choice of one comfort item (team reward).
- Ring quest (Ages 3 to 8): "Raise the <ring>", `optional: true`, diamond, size 1.25, tag `ring`, XP only, below the
  goal, depends on the entry with hidden lines. One task: observation `block_state`
  `firmages:shrine_heart[awakened=N,ready=true]` in the chapter of `age_N` (SPEC section 7.8: `ready` = the current
  tier's rings stand, `awakened` = highest Age index), so it ticks only in its own Age. The Singularity Age has no
  shrine tier and no ring quest. The Stone Age keeps its required keystone Raise the Shrine (`[ready=true]`).
- Explanations: checkmark, `optional: true`, gear, tag `explain`, dependency lines hidden.
- Chapter `progression_mode: flexible`: tasks count early, but a quest completes only when its dependencies are done
  (TeamData.setProgress), so a goal never fires before its strands.
- Hiding: chapter key `progressivestages_required_stage` (added by ProgressiveStages 3.0.5's ChapterMixin). The
  chapter is invisible until the team owns the stage.

### Task choices

- FTB Filter System is not in the pack, so item tasks match one exact item. Rock- and wood-variant steps use TFC
  advancements (`tfc:story/*`, `tfc:world/*`), observation of block tags (`tfc:support_beams`, `tfc:farmlands`) or an
  either/or quest: all tasks `optional_task: true`, and the quest completes with any one of them
  (QuestObject.isCompletedRaw).
- The thatch bed is built in the world (two thatch + large raw hide) and `enableThatchBedSleeping = false` in
  `tfc-server.toml`, so "first night" is an observation of the placed bed, not a sleep stat.
- Kill tasks count kills (Doc 08 section 2.2): `tfc:animals` tag, `twilightforest:naga`, `twilightforest:lich`,
  `mowziesmobs:grottol`, `mowziesmobs:ferrous_wroughtnaut`. Ages 3 to 9: required boss kills where the drop goes into
  the signature item (`ars_nouveau:wilden_boss`, `cataclysm:netherite_monstrosity`, `minecraft:wither`,
  `minecraft:ender_dragon`, `cataclysm:the_harbinger`, `cataclysm:the_leviathan`,
  `draconicevolution:draconic_guardian` = Chaos Guardian); optional ones as side missions (`mowziesmobs:umvuthi`,
  `mowziesmobs:frostmaw`, `mowziesmobs:sculptor`, `cataclysm:ender_guardian`, `cataclysm:ancient_remnant`,
  `cataclysm:scylla`, `cataclysm:ignis`). All entity ids checked in the pack jars; vanilla entities are accepted
  without a jar lookup (`VANILLA_ENTITIES`).
- IE and Immersive Petroleum multiblocks are observation tasks on the formed block (`immersiveengineering:coke_oven`,
  `blast_furnace`, `advanced_blast_furnace`, `crusher`, `metal_press`, `alloy_smelter`, `diesel_generator`,
  `arc_furnace`, `excavator`, `assembler`, `immersivepetroleum:pumpjack`, `distillation_tower`). No FE or task-screen
  task is used yet: "Grid Online (LV)", "Reactor Online" and "Gate Online" observe the placed block.
- Dimension tasks: `minecraft:the_nether` (Beneath ritual), `minecraft:the_end`, `ad_astra:moon`, `mars`, `venus`,
  `mercury`, `glacio`. The Origin (`firmages:origin`) is not defined yet, so no quest has a dimension task on it.

## Age grants: quest reward in Dawn, the shrine from the Stone Age on

| Chapter | Goal quest | Next Age comes from | Goal task |
|---|---|---|---|
| Dawn | The First Spark `724BED62EE9DF8FB` | quest reward `age_0` (no shrine yet, Doc 11 section 3) | firestarter item + `tfc:story/firepit` |
| Stone Age | Offer the Hearthstone at the Shrine `5298B856BFEE50A2` | the shrine (offering `firmages:hearthstone`, ring 0) | gamestage `age_1` |
| Bronze Age | Offer the Sky Disc at the Shrine `2070B77706F2CB8E` | the shrine (`firmages:sky_disc`, ring 1) | gamestage `age_2` |
| Iron Age | Offer the Steel Heart at the Shrine `28A963AA218A4EE4` | the shrine (`firmages:steel_heart`, ring 2) | gamestage `age_3` |
| Arcane Age | Offer the Arcane Keystone at the Shrine `02BAEBC0675BF246` | the shrine (`firmages:arcane_keystone`, ring 3) | gamestage `age_4` |
| Industrial Age | Offer the Pressure Core at the Shrine `68A4B5AC4EBE2085` | the shrine (`firmages:pressure_core`, ring 4) | gamestage `age_5` |
| Electric Age | Offer the Humming Core at the Shrine `23536BC1524A5044` | the shrine (`firmages:humming_core`, ring 5) | gamestage `age_6` |
| Information Age | Offer the Data Matrix at the Shrine `64CDFFD93C9CBDCE` | the shrine (`firmages:data_matrix`, ring 6) | gamestage `age_7` |
| Space Age | Offer the Star Chart at the Shrine `44FB988268E119B4` | the shrine (`firmages:star_chart`, ring 7) | gamestage `age_8` |
| Quantum Age | Offer the Quantum Core at the Shrine `67314514BDCA1DB3` | the shrine (`firmages:quantum_core`, ring 8) | gamestage `age_9` |
| Singularity Age | Beyond the Firmament `1D5C854562BD6F58` | the end boss script (not written yet) | gamestage `finale_won` |

`validate_quests.py` checks one grant path per Age over the whole book: `age_0` by exactly one quest reward,
`age_1` to `age_9` by exactly one shrine tier (`tier/ring_N.json` grants) and no quest reward, `finale_won` by no quest
reward (a warning while no KubeJS script grants it), `dawn` by nothing. The goal texts of Ages 3 to 8 name the ring,
its blocks and its rite as firmages-core 0.3.3 ships them (`tier/ring_3..8.json`, `multiblock.firmages.shrine_ring_N`
and the rite hints in the mod lang file).

- The shrine offerings are the file firmages-core reads: `data/firmages/firmages_shrine/offerings.json` in the mod
  (`mod/firmages-core/src/main/resources/...`, flat `{"age_N": item}`, SPEC section 2.6); a pack copy at
  `kubejs/data/firmages/firmages_shrine/offerings.json` would replace it. `validate_quests.py` reads the same file
  (item in the Age tag of its key, not yet registered = warning; every `ring_N` tier grants `age_(N+1)`) and checks
  every goal of a shrine Age: exactly one gamestage task on the next Age, the offering as icon, no stage reward. A
  stage reward there would be a second grant path. The `interim_shrine` rewards are gone; one left on a shrine Age
  is an error. A file at the old path `kubejs/data/*/shrine/offerings.json` is an error (the mod never read it).
- The goal completes when the shrine (or an admin `/stage grant`) gives the next Age and all keystones are done
  (flexible mode). Offering before the strands are done is possible; the goal then completes with the last keystone.
- Stone Age strand "Hearth & Shrine": Unfired Hearth Idol, Hearth Idol, The Shrine Heart (observation of the placed
  block `firmages:shrine_heart`), keystone Raise the Shrine (observation `block_state`
  `firmages:shrine_heart[ready=true]`: ring 0 valid and plinth 1 empty, SPEC section 2.5). The strand replaces the
  old "Hearth" strand (keystone Hearth Idol), so the chapter keeps 4 keystones (Doc 08 section 9.1). FTB Quests
  2101.1.36 matches `block_state` with `BlockInput.test` on the listed properties only (javap of ObservationTask),
  so `[ready=true]` ignores `awakened` and `lit`.
- `firmages:shrine_heart` and `firmages:offering_plinth` come with firmages-core 0.3.0 and are in
  `dev/data/registry.json` since the all-Ages dump of 2026-10-01 (both `age_0` in `dev/age_map.toml`); `PENDING_MOD_IDS`
  in the validator is empty.
- Bronze and Iron goal texts name the rings and rites that firmages-core 0.3.0 ships (`tier/ring_1.json`: Bronze
  Sanctum + bell; `tier/ring_2.json`: Iron Sanctum + four lamps, SPEC section 7.8). The goal has no item task: it
  unlocks only after Raise the Shrine, and a Hearthstone laid on the plinth at once would never tick it.

Stage reward settings (Dawn): type `gamestage`, `auto: "invisible"` (without it the reward falls back to the file
default `disabled` and would wait for a click), not a team reward. Non-team stage rewards call FTB Library's
StageHelper provider, which is ProgressiveStages (PoC A10); with `team_mode = "ftb_teams"` the stage lands on the
whole team. grants.js derives mob stages and helper stages from the Age stage.

## Age titles: KubeJS or the mod ceremony

`kubejs/server_scripts/stages/on_stage_added.js` shows title, subtitle, sound and firework on every Age grant only
while firmages-core is older than 0.3.0. From 0.3.0 the mod owns the transition (SPEC section 8: the full ceremony
at the shrine, the short one for admin and quest grants such as The First Spark), and the script stays silent for
the Age stages; it still announces `finale_won`, which is not an Age. The script reads the version with
`Platform.getInfo('firmages').getVersion()` (KubeJS `PlatformWrapper`, checked with javap), because the `FirmAges`
binding has no version or ceremony flag. The server log says which path is active:
`[firmages] on_stage_added: Age titles by firmages-core ceremony` (or `by KubeJS`).

## Rewards

Only XP and items of the chapter's Age (validator: item Age tag from `kubejs/data/firmages/tags/item/age_items/`
must not be later than the chapter's stage; disabled items fail). No fresh food, no creative items.

| Table | Age | Items |
|---|---|---|
| dawn_supplies | dawn | sticks, straw, thatch, rope (Dawn keystones and side missions) |
| stone_supplies / stone_comfort | age_0 | copper ingots and ore, charcoal, clay, fired vessel and ingot mold / large vessel, copper knife, copper axe, candles |
| bronze_supplies / bronze_comfort | age_1 | bronze, tin, zinc ingots, flux, andesite alloy, shafts, cogwheels, leather / backpack, wrench, goggles, bronze propick |
| iron_supplies / iron_comfort | age_2 | wrought iron, steel, brass, wrought iron sheets, electron tubes, fluid pipes / charm of life, wrought iron shield, storage terminal, magic map focus |
| arcane_supplies / arcane_comfort | age_3 | source gems, iesnium, mercury shards, amethyst, lapis, otherworld essence, blaze cakes / Starbuncle, Whirlisprig and Wixie charms, otherworld goggles |
| industrial_supplies / industrial_comfort | age_4 | coal coke, treated wood, lead, copper wire, iron components, constantan, black steel / Engineer's hammer, wire cutter, Create jetpack, electric lanterns |
| electric_supplies / electric_comfort | age_5 | aluminium ingots and plates, electrum and steel wire, red steel, lubricant / speedboat, oil can, hypertubes, MV capacitor |
| information_supplies / information_comfort | age_6 | osmium, basic circuits, certus, fluix, silicon, HDPE, blue steel / Mekanism jetpack, free runners, wireless terminal, configurator |
| space_supplies / space_comfort | age_7 | desh, ostrum, steel plates, draconium, uranium, rocket fuel / rover, zip gun, teleporter, wyvern capacitor |
| quantum_supplies / quantum_comfort | age_8 | calorite, awakened draconium, ice shards, hypercharged alloy, SPS casings / draconic capacitor, overclocked energy cube, overclocked circuits, draconic pickaxe |
| singularity_supplies / singularity_comfort | age_9 | small chaos fragments, raw naquadah, naquadah, a chaos shard / chaotic capacitor, GDO, naquadah iris |

Every table carries the tag `age:<stage>`; the validator fails a table that a chapter of another Age uses, and any
table item later than that Age.

## data.snbt

`drop_book_on_death: false`, `drop_loot_crates: false`, no emergency items, `default_consume_items: false`,
`default_autoclaim_rewards: "disabled"`, `progression_mode: "flexible"`, `fallback_locale: "en_us"`, file version 13.
FTB Quests 2101 never hands out a book by itself (no first-join give in the source); the `ftbquests:book` recipe is
removed and the item sits in the `disabled` stage. The K key: `config/defaultoptions/keybindings.txt` has
`key_key.ftbquests.quests:key.keyboard.k`; the mapping name `key.ftbquests.quests` is confirmed in
FTBQuestsKeyMappings (name "quests", mod "ftbquests") and FTB Library's `key.%s.%s` format.

## Integration test (in game, needs a client)

1. Server log after start: `Loaded 2 chapter groups, 13 chapters, 344 quests, 21 reward tables`, no SNBT or
   "failed to read data" errors; `loaded translation tables for 1 language(s)`.
2. Fresh team (only `dawn`): K opens the screen; Welcome, The Firmament and Dawn are visible; the ten chapters from
   Stone to Singularity are hidden. No quest book in the inventory, none after death.
3. The Firmament: all 11 quests show with their signature item icons (the last one the classic Stargate), only The
   First Spark can be opened; the others say they are locked.
4. Dawn walk: advancement tasks tick (find rock, stone tool, logging, straw, firepit), the javelin either/or quest
   completes with one javelin, the thatch-bed observation completes when looking at the bed, the animal kill counts.
5. The First Spark completes only after the three keystones, grants `age_0` without a click, the Stone Age chapter
   appears without relog, one title plays (the short ceremony of firmages-core 0.3.0; the KubeJS title only with an
   older mod), grants.js keeps `mob_0`.
6. Stone Age shrine: The Shrine Heart completes when looking at the placed heart, Raise the Shrine when looking at
   it with the Hearth Circle complete. Offering the Hearthstone and praying grants `age_1`; the goal completes once
   the keystones are done, the Bronze chapter appears, one title (the full ceremony), the Hearthstone stays on the
   plinth. Then Sky Disc (`age_2`: map opens, `tool_toms_storage`) and Steel Heart (`age_3`, needs ring 2).
7. Keystone rewards: the random supplies item goes to the claiming player once per team; side-mission choice screen
   offers only current-Age items; XP per player.
8. Observation tasks: `tfc:support_beams` and `tfc:farmlands` block tags, `create:millstone` and
   `create:water_wheel` blocks, `mowziesmobs:lantern` entity. Dimension task in the Twilight Forest: a 2x2 water
   pool ringed by TFC grass and flowers opens with polished quartz.
9. Admin repair: `/stage grant <player> age_1` on a team without Dawn done opens the Bronze chapter entry at once
   (entry quests have no cross-chapter dependency).
10. Edit mode round trip: open the editor, move one quest, save; FTB rewrites the files (comments vanish);
    `validate_quests.py` still passes.
11. Ages 3 to 8 ladder (`/firmages debug` players or the shrine ladder run of `dev/poc-results.md`): with `age_N`, the
    chapter of Age N appears without relog; each goal stays open until the shrine grants `age_(N+1)` and all keystones
    are done, and no quest reward grants an Age (exactly one ceremony per Age). The goal icon is the offering.
12. Ring quests: Raise the Spirit Circle ... Raise the Quantum Ring tick when looking at the heart with that ring
    complete in its own Age (`awakened=N,ready=true`), never in a later Age; the diamond shape renders. Check that FTB's
    block_state matching takes the two properties in this order (SPEC section 7.8 names them the other way round).
13. Multiblock observations: a formed coke oven, blast furnace, improved blast furnace, crusher, metal press, alloy
    kiln, diesel generator, arc furnace, excavator, assembler, pumpjack and distillation tower each tick their quest;
    an unformed structure does not. Placed alternator, LV capacitor, Draconic reactor core and assembled classic
    Stargate tick their keystones.
14. Kill tasks: Wilden Chimera (`ars_nouveau:wilden_boss`), Netherite Monstrosity, Wither, Ender Dragon, Harbinger,
    Leviathan and the Chaos Guardian (`draconicevolution:draconic_guardian`) count for the team.
15. Dimension tasks: Nether through the Beneath ritual (age_3), End (age_6), Moon and Mars (age_7), Venus, Mercury and
    Glacio (age_8); each ticks on arrival and is impossible before its Age (PS dimension locks).
16. Singularity: "Nine Relics" ticks on the heart once `age_9` is owned (`awakened=9`); the goal Beyond the Firmament
    completes only when `finale_won` is granted (for now by `/stage grant`, until the end boss script exists), and
    the Firmament's last quest completes with it.
17. Reward tables of Ages 3 to 9: supplies and comfort screens offer only items of the chapter's Age.

## Open points

- **Twilight portal (resolved 2026-10-01, `dev/decisions-while-away.md`).** `twilightforest:portal/activator` was
  `#c:gems/diamond` (age_4). `kubejs/server_scripts/tags/unification.js` now sets it to `tfcreate:polished_quartz`
  (the Iron Age quartz vein, age_2); the quest text names polished quartz. `validate_quests.py` checks every
  dimension task against its portal tag (`PORTAL_TAGS`): the tag must be overridden in the KubeJS tag scripts and
  hold only items of the chapter's Age or earlier. The pack test server confirms the tag
  (`item_tags.json`: `["tfcreate:polished_quartz"]`) and `poc_analyze.py` R-age_2 reaches it at the Iron Age.
- Signature items of Ages 3 to 8 are registered (all-Ages dump of 2026-10-01); their Firmament quests and goals use
  them as icons.
- **End boss not written.** No script grants `finale_won` yet (validator warning). The Singularity goal and the
  Firmament's last quest wait for it; the end boss script must grant it (one path, Doc 08 section 10.4).
- **The Origin not defined.** `firmages:origin` does not exist, so "Gate Online" observes the assembled Stargate and
  no quest checks arrival in The Origin. Add a dimension task (and the address text) when the dimension exists.
- **Ultimate Singularity not registered.** Keystone "The Ultimate Singularity" is a checkmark; switch it to an item
  task when `firmages:ultimate_singularity` exists (Felix: forged at the shrine, SPEC section 7.5 Gathering, M6/M8).
- **Awakened Keystone recipe is a stub** (items.js) and Keystone lending (SPEC section 7.4) is not built: the Marid
  keystone cannot be completed until both exist.
- **End portal:** no recipe for `minecraft:end_portal_frame` in kubejs (Doc 08 section 6: a built frame by KubeJS
  recipe). Until it exists the End strand of the Information Age cannot be done; the age_6 reachability check in
  `poc_analyze.py` treats the End as given.
- "Data Model: Tier 3" accepts any data model (the tier is a component an item task does not read).
- FE proofs (Doc 08: sustained power as FE task) are observations of placed blocks; switch to `forge_energy` or task
  screen tasks once those are tested in the client.
- Automation proofs (Doc 08 section 9.1: task screens with pipe or belt input) are not used yet; the "Automated
  Bloomery Line" keystone asks for 32 wrought iron ingots. Switch to `task_screen_only` once task screens are tested.
- Torque Link is not in the registry snapshot; the Kinetics strand ends with millstone + water wheel instead of the
  quern link from Doc 08.
- Gold panning (Doc 08 Stone Age side line) is left out: native gold is `age_1` in the age map.
- Survival keystone "Winter Stores" is a checkmark (cellar and sealed vessels cannot be detected).
- The Firmament stage tasks poll every second; a team that already owns several Ages sees them complete in order.
- Quest sizes follow Doc 08 in relation (entry 2, keystone 1.5, goal 3) but smaller than the doc's 3/2/4 so the
  chapters fit the default zoom; adjust in the editor if wanted.
