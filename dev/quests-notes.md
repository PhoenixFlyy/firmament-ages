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
| `welcome.snbt` | default | Welcome | always | 6 | checkmarks: pack overview and K key, the Ages and recipe/EMI/ore locks, how a chapter reads, one team, the shrine to come, survival notes |
| `the_firmament.snbt` | default | The Firmament | always | 11 | one quest per Age goal: The First Spark, Hearthstone, Sky Disc, Steel Heart, Arcane Keystone, Pressure Core, Humming Core, Data Matrix, Star Chart, Quantum Core, Beyond the Firmament. Gamestage tasks (age_0 ... age_9, finale_won), `progression_mode: linear` + `hide_quest_details_until_startable`, so the whole road is visible from minute one and each quest opens only after the previous goal |
| `dawn.snbt` | Ages | Dawn | stage `dawn` (start) | 15 | entry, strands Stone / Shelter / Hunt, goal The First Spark, 2 side missions, 1 explanation |
| `stone_age.snbt` | Ages | Age 0: Stone Age | stage `age_0` | 25 | entry, strands Fire & Clay / First Metal / Camp / Hearth, goal Hearthstone, 3 side missions, 3 explanations |
| `bronze_age.snbt` | Ages | Age 1: Bronze Age | stage `age_1` | 31 | entry, strands Smithing / Prospecting / Alloys / Kinetics, goal Sky Disc, 4 side missions, 2 explanations |
| `iron_age.snbt` | Ages | Age 2: Iron Age | stage `age_2` | 29 | entry, strands Metallurgy / Power & Motion / Survival / Frontier, goal Steel Heart, 3 side missions, 2 explanations |

Total 117 quests, 7 reward tables, 445 object ids. Counts per chapter come from the validator output.

Chapter ids: welcome `2F7890D901468DC7`, the_firmament `7D0742BC787AAFAC`, dawn `544197C921D77FCB`, stone_age
`1ECA2119E26A0A06`, bronze_age `5546E96FD2574ABB`, iron_age `1D01936AE318F34B`; group Ages `7E67E59034114849`.

### Structure of an Age chapter (Doc 08 section 9.1)

- Entry quest (hexagon, size 2): gamestage task of the chapter's own stage, no cross-chapter dependency, so an admin
  `/stage grant` also opens the chapter correctly.
- 3 or 4 required strands, each a dependency chain from the entry to a keystone (hexagon, size 1.5, tag
  `keystone`). Keystone reward: XP and one random item from the Age's supplies table (team reward).
- One goal quest (gear, size 3, tag `goal`) that depends on every keystone. Item task with `consume_items: false`.
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

## Stage rewards

| Chapter | Goal quest | Next Age | How |
|---|---|---|---|
| Dawn | The First Spark `724BED62EE9DF8FB` | `age_0` | stage reward (the shrine is built in the Stone Age, Doc 11 section 3) |
| Stone Age | Hearthstone `5298B856BFEE50A2` | `age_1` | the shrine grants it; the goal has the item task plus a gamestage task `age_1`, no stage reward |
| Bronze Age | Sky Disc `2070B77706F2CB8E` | `age_2` | the shrine, as above (`age_2`) |
| Iron Age | Steel Heart `28A963AA218A4EE4` | `age_3` | the shrine, as above (`age_3`) |

Since firmages-core 0.3.0 the shrine grants the Ages from the Stone Age on (offer the signature item, pray). The goal
quest then completes through its gamestage task, so there is exactly one grant path; `validate_quests.py` checks that
Dawn's goal grants `age_0` and every later goal waits for the next Age without granting it. The goal descriptions
explain the ring to build and the rite.

Stage reward settings: type `gamestage`, `auto: "invisible"` (without it the reward falls back to the file default
`disabled` and would wait for a click), not a team reward. Non-team stage rewards call FTB Library's StageHelper
provider, which is ProgressiveStages (PoC A10); with `team_mode = "ftb_teams"` the stage lands on the whole team.
(A team reward would also work: ProgressiveStages' StageRewardMixin routes team rewards of PS stages to its provider
instead of FTB Teams' own stage storage.) grants.js derives mob stages and helper stages from the Age stage; the
title, Caelum's line and the beam come from firmages-core's Age ceremony (on_stage_added.js skips Age stages).

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
   appears without relog, the short Age ceremony plays (one title "The Stone Age dawns"), grants.js keeps `mob_0`.
6. Hearthstone: holding it ticks the item task only; the quest completes when the shrine prayer grants `age_1`
   (full ceremony, one title). Repeat for Sky Disc (`age_2`: map opens, `tool_toms_storage`) and Steel Heart
   (`age_3`) with their rings. The goal item stays in the inventory (`consume_items: false`) until it is offered.
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
