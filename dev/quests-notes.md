# Quest book: first chapters (FTB Quests 2101.1.36)

Date: 2026-09-30. Files: `config/ftbquests/quests/` (data.snbt, chapter_groups.snbt, chapters/, reward_tables/,
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
| `the_firmament.snbt` | default | The Firmament | always | 11 | one quest per Age goal: The First Spark, Hearthstone, Sky Disc, Steel Heart, Arcane Keystone, Pressure Core, Humming Core, Data Matrix, Star Chart, Quantum Core, Beyond the Firmament. Gamestage tasks (age_0 ... age_9, finale_won), `progression_mode: linear` + `hide_quest_details_until_startable`, so the whole road is visible from minute one and each quest opens only after the previous goal |
| `dawn.snbt` | Ages | Dawn | stage `dawn` (start) | 15 | entry, strands Stone / Shelter / Hunt, goal The First Spark, 2 side missions, 1 explanation |
| `stone_age.snbt` | Ages | Age 0: Stone Age | stage `age_0` | 27 | entry, strands Fire & Clay / First Metal / Camp / Hearth & Shrine (ends in Raise the Shrine), goal Offer the Hearthstone at the Shrine, 3 side missions, 3 explanations |
| `bronze_age.snbt` | Ages | Age 1: Bronze Age | stage `age_1` | 31 | entry, strands Smithing / Prospecting / Alloys / Kinetics, goal Offer the Sky Disc at the Shrine, 4 side missions, 2 explanations |
| `iron_age.snbt` | Ages | Age 2: Iron Age | stage `age_2` | 29 | entry, strands Metallurgy / Power & Motion / Survival / Frontier, goal Offer the Steel Heart at the Shrine, 3 side missions, 2 explanations |

Total 119 quests, 7 reward tables, 448 object ids. Counts per chapter come from the validator output.

Chapter ids: welcome `2F7890D901468DC7`, the_firmament `7D0742BC787AAFAC`, dawn `544197C921D77FCB`, stone_age
`1ECA2119E26A0A06`, bronze_age `5546E96FD2574ABB`, iron_age `1D01936AE318F34B`; group Ages `7E67E59034114849`.

### Structure of an Age chapter (Doc 08 section 9.1)

- Entry quest (hexagon, size 2): gamestage task of the chapter's own stage, no cross-chapter dependency, so an admin
  `/stage grant` also opens the chapter correctly.
- 3 or 4 required strands, each a dependency chain from the entry to a keystone (hexagon, size 1.5, tag
  `keystone`). Keystone reward: XP and one random item from the Age's supplies table (team reward).
- One goal quest (gear, size 3, tag `goal`) that depends on every keystone. From the Stone Age on: a gamestage task
  on the next Age ("Offer the X at the shrine"), icon = the signature item, no stage reward (section "Age grants").
  Dawn: item and advancement tasks plus the `age_0` stage reward.
- Side missions: `optional: true`, circle, tag `side`; reward XP and a choice of one comfort item (team reward).
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
  `mowziesmobs:grottol`, `mowziesmobs:ferrous_wroughtnaut`.

## Age grants: quest reward in Dawn, the shrine from the Stone Age on

| Chapter | Goal quest | Next Age comes from | Goal task |
|---|---|---|---|
| Dawn | The First Spark `724BED62EE9DF8FB` | quest reward `age_0` (no shrine yet, Doc 11 section 3) | firestarter item + `tfc:story/firepit` |
| Stone Age | Offer the Hearthstone at the Shrine `5298B856BFEE50A2` | the shrine (offering `firmages:hearthstone`, ring 0) | gamestage `age_1` |
| Bronze Age | Offer the Sky Disc at the Shrine `2070B77706F2CB8E` | the shrine (`firmages:sky_disc`, ring 1) | gamestage `age_2` |
| Iron Age | Offer the Steel Heart at the Shrine `28A963AA218A4EE4` | the shrine (`firmages:steel_heart`, ring 2) | gamestage `age_3` |

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

## data.snbt

`drop_book_on_death: false`, `drop_loot_crates: false`, no emergency items, `default_consume_items: false`,
`default_autoclaim_rewards: "disabled"`, `progression_mode: "flexible"`, `fallback_locale: "en_us"`, file version 13.
FTB Quests 2101 never hands out a book by itself (no first-join give in the source); the `ftbquests:book` recipe is
removed and the item sits in the `disabled` stage. The K key: `config/defaultoptions/keybindings.txt` has
`key_key.ftbquests.quests:key.keyboard.k`; the mapping name `key.ftbquests.quests` is confirmed in
FTBQuestsKeyMappings (name "quests", mod "ftbquests") and FTB Library's `key.%s.%s` format.

## Integration test (in game, needs a client)

1. Server log after start: `Loaded 2 chapter groups, 6 chapters, 117 quests, 7 reward tables`, no SNBT or
   "failed to read data" errors; `loaded translation tables for 1 language(s)`.
2. Fresh team (only `dawn`): K opens the screen; Welcome, The Firmament and Dawn are visible; Stone, Bronze and Iron
   chapters are hidden. No quest book in the inventory, none after death.
3. The Firmament: all 11 quests show (titles and icons; the Ages 3 to 9 quests have no item icon yet), only The
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

## Open points

- **Twilight portal (resolved 2026-10-01, `dev/decisions-while-away.md`).** `twilightforest:portal/activator` was
  `#c:gems/diamond` (age_4). `kubejs/server_scripts/tags/unification.js` now sets it to `tfcreate:polished_quartz`
  (the Iron Age quartz vein, age_2); the quest text names polished quartz. `validate_quests.py` checks every
  dimension task against its portal tag (`PORTAL_TAGS`): the tag must be overridden in the KubeJS tag scripts and
  hold only items of the chapter's Age or earlier. The pack test server confirms the tag
  (`item_tags.json`: `["tfcreate:polished_quartz"]`) and `poc_analyze.py` R-age_2 reaches it at the Iron Age.
- Signature items of Ages 3 to 8 (Arcane Keystone ... Quantum Core) are not registered yet, so their Firmament quests
  have no item icon. Set `icon` when the KubeJS items exist.
- Automation proofs (Doc 08 section 9.1: task screens with pipe or belt input) are not used yet; the "Automated
  Bloomery Line" keystone asks for 32 wrought iron ingots. Switch to `task_screen_only` once task screens are tested.
- Torque Link is not in the registry snapshot; the Kinetics strand ends with millstone + water wheel instead of the
  quern link from Doc 08.
- Gold panning (Doc 08 Stone Age side line) is left out: native gold is `age_1` in the age map.
- Survival keystone "Winter Stores" is a checkmark (cellar and sealed vessels cannot be detected).
- The Firmament stage tasks poll every second; a team that already owns several Ages sees them complete in order.
- Quest sizes follow Doc 08 in relation (entry 2, keystone 1.5, goal 3) but smaller than the doc's 3/2/4 so the
  chapters fit the default zoom; adjust in the editor if wanted.
