# PoC results: server side (section A of poc-checklist.md)

Open points of all runs, checked on 2026-10-14, are collected in `dev/offene-punkte.md`; the sections below keep them as
they were found.

Date: 2026-09-30 (second pass the same day: Age map, open points, M0). Pack 0.1.0 on branch `dev`, Minecraft 1.21.1,
NeoForge 21.1.252, Temurin 21.0.12.1, `-Xms6G -Xmx8G`.
Server: `test-server/`, synced from `http://localhost:8080/pack.toml`, fresh world (`--wipe-world`, seed random,
spawn -2912 / -2236). No player joined. Everything that needs a player is marked **needs client**.

## Tools used (all in `dev/`, no downloads)

| Tool | Use |
|---|---|
| `run_server.py --hold N` | keeps the server running for RCON; lines appended to `test-server/logs/run_server-input.txt` go to the console (spark answers asynchronously, so RCON never sees its output) |
| `rcon.py "cmd" ...` | minimal RCON client; reads port and password from `test-server/server.properties` |
| `/fa_dump`, `/fa_dump_full [regex]`, `/fa_recipe <regex>`, `/fa_dims` | console commands in `kubejs/server_scripts/debug/dump.js`: recipe and tag dump with all ProgressiveStages stages that lock each recipe, its output and its result item, plus `registries.json` (every item, block and fluid id); `recipes_full.json` (every recipe as its serializer JSON, for the C- checks); recipe inspector; dimension locks and ore-override count |
| `/fa_selftest` | console command in `kubejs/server_scripts/stages/grants.js`: a FakePlayer walks dawn to age_6 and back, `reconcile()` runs after each step; each line shows the time of the PS call and of `reconcile()` |
| `gen_stage_locks.py` | generates the Age tags and the stage-file locks from `dev/age_map.toml` (see "Age registry tags"); `--registry <dump dir>` refreshes `dev/data/registry.json`, `--explain <item>` prints the rule behind one item's Age |
| `poc_analyze.py` | reads the `/fa_dump` files and the Age tags and prints PASS/FAIL per recipe or tag check, including the grid recipes that open at Dawn, Stone, Bronze and Iron |
| `scan_regions.py` | offline Anvil reader: block counts per id in the generated chunks (run `save-all flush` first) |

## Results

| # | Check | Command | Expected | Observed | Result |
|---|---|---|---|---|---|
| A1 | Generator up to date | `python dev/gen_stage_locks.py --check` | `checked 0 file(s)`, exit 0 | `checked 0 file(s)`, exit 0 (now covers the 23 tag files and 28 stage files) | pass |
| A2 | JS syntax | `node --check` on the 16 files under `kubejs/` | no error | no error | pass |
| A3 | Stage files load | `progressivestages validate`; console grep | `28/28 stage files valid`, no `Failed to parse stage file`, no `Invalid ore override entry` | `SUMMARY: 28/28 stage files valid, all passed!`; 0 matching log lines | pass |
| A4 | Stage tree | `stage tree` | `dawn → age_0 → … → age_9 → finale_won`, `ftbchunks_mapping` under `age_2` | exactly that; mob_0..9, tool_*, disabled are roots | pass |
| A5 | KubeJS without errors | `logs/kubejs/startup.log`, `server.log` | 0 errors | 2/2 startup, 13/13 server scripts, 0 errors, 0 warnings. **Two latent Rhino bugs fixed first** (see Fixes 1) | pass after fix |
| A6 | Recipe balance | `reload`, `logs/kubejs/server.log` | Added/Removed line, no `Unable to parse recipe filter` | `Added 272 recipes, removed 281 recipes, modified 17 recipes, with 0 failed recipes` (the Occultism miner remap adds 11 and removes 64); 0 filter warnings | pass |
| A7 | Pack recipes present | `/fa_dump` + `poc_analyze.py` | hearthstone, sky_disc, steel_heart, pressing/tfc_sheet_bronze, crushing/rich_hematite | all present; plus `firmages:knapping/unfired_hearth_idol` (tfc:knapping) and 9 `firmages:heating/*` (tfc:heating) | pass |
| A8 | Removed recipes absent | same | no create:pressing/iron_ingot, mixing/brass_ingot, crafting/materials/andesite_alloy, crushing/raw_iron, ftbquests:book recipe | none present | pass |
| A9 | Tags | `/fa_dump` item_tags.json | `c:plates/iron` has `tfc:metal/sheet/wrought_iron`, not `create:iron_sheet`; `c:hidden_from_recipe_viewers` = HIDDEN_ITEMS | `c:plates/iron` = IE plate, Ad Astra plate, TFC sheet (Almost Unified makes the TFC sheet the target and hides the other two); hidden tag holds all 33 | pass |
| A10 | FTB provider | `progressivestages ftb status` | Provider Registered YES | Registered YES, Compat Active YES, Previous Provider Stored YES (XMod's KubeJS Stages provider) | pass |
| A11 | World configs | fresh world; `config/tfc-server.toml`, `config/immersiveengineering-server.toml`, `config/Mekanism/world.toml` | surface monsters on, `vein_size = 0`, ore generation off | `enableVanillaMonstersOnSurface = true`; 6 × `vein_size = 0`; the 6 ore-level `shouldGenerate = false` (vein-level keys stay `true`, which is harmless because the ore level is off; worldgen scan shows 0 Mekanism blocks). The files land in `config/`, not `world/serverconfig/` (see boot-report) | pass |
| A12 | FTB Chunks defaults | `logs/debug.log`, `config/ftbchunks-world.snbt` | no SNBT errors; protection off, PvP on, map needs stage, 0 claims | `tracking config ftbchunks-world, loaded from ...\config\ftbchunks-world.snbt`; `disable_protection: true`, `pvp_mode: "always"`, `require_game_stage: true`, `max_claimed_chunks: 0`, `max_force_loaded_chunks: 0`, `force_load_mode: "never"`, `party_limit_mode: "largest"` | pass |
| A13 | KubeJS datapack active | `datapack list` | KubeJS data active | `datapack list` shows 8 packs and no separate KubeJS entry (KubeJS 2101 injects its data differently). Proven by effect: the `neoforge:none` biome-modifier overrides work (worldgen rows below) and the `firmages` recipes load | pass (by effect) |
| A14 | Idle baseline | `spark tps`, `spark health`, `spark profiler` 5 min idle | baseline for C3 | see Performance below | done |

### Stages and the reconcile logic

`stage grant/revoke/list` need an online player (`stage grant FA_Nobody age_0` → `No player was found`), and
ProgressiveStages fires `onGranted`/`onRevoked` only for players in the server list. The script logic was therefore driven
with a FakePlayer (`/fa_selftest`), calling `reconcile()` after each grant or revoke.

| Step | Stages after the step | Expected (grants.js + 00_constants.js) | Result |
|---|---|---|---|
| grant dawn | dawn, mob_0 | mob_0 | pass |
| grant age_0 | + age_0 | mob_0 only | pass |
| grant age_1 | + age_1, mob_1 | cumulative ladder | pass |
| grant age_2 | + age_2, mob_2, ftbchunks_mapping, tool_toms_storage | map stage and Tom's window open | pass |
| grant age_3 | + age_3, mob_3 | | pass |
| grant age_4 | + age_4, mob_4, tool_ie_alloy_kiln, tool_ie_capacitor, tool_mech_spawner | IE and spawner windows open | pass |
| grant age_5 | + age_5, mob_5; tool_ie_alloy_kiln gone | kiln window [age_4, age_5) | pass |
| grant age_6 | + age_6, mob_6; tool_toms_storage, tool_ie_capacitor, tool_mech_spawner gone | windows close with age_6 | pass |
| revoke age_6 … age_2 | each step restores the previous state exactly; after revoking age_2: age_0, age_1, dawn, mob_0, mob_1 | idempotent reconcile | pass |
| cleanup | [] | | pass |

| Check | Command | Observed | Result |
|---|---|---|---|
| Grant/revoke for a real team via console | `stage grant <player> age_1` | needs an online player | needs client (C1, C8, C20, C21) |
| Event wiring (quest reward → onGranted → reconcile, age title) | – | PS fires script hooks only for listed players | needs client (C20, C21) |
| Chunk quota at age_4 | `ftbchunks admin extra_claim_chunks FA_Nobody set 25` | the command parses and fails only on the player lookup (`No player was found`); same for `extra_force_load_chunks` | syntax pass; needs client (C17) |

### Recipe gating and unification (`poc_analyze.py`, final dump: 35,518 recipes, 3,438 item tags)

| Check | Expected | Observed | Result |
|---|---|---|---|
| Dawn: vanilla grid recipes | every `minecraft:` crafting recipe locked in Dawn | 0 open | pass |
| Dawn: TFC grid whitelist | only stone-tool shafting, firestarter, straw/thatch, sticks, rope, obsidian tools | 44 TFC recipes open, all in the whitelist | pass |
| Dawn: other mods' grid recipes | 0 open | first pass: 4,144 grid recipes of other mods carried no recipe, output or item lock (afc 796, dndecor 529, firmalife 378, createframed 323, createdeco 274, framedblocks 256, createcasing 241, mysticalagriculture 210, mekmm 183, beneath 118 …). Now 0: every item has an Age tag and every recipe namespace a grid lock (Fixes 9). Dawn opens exactly the 44 whitelisted TFC recipes | pass after fix |
| Stone Age (age_0) open set | results of age_0 or earlier; landmarks `tfc:crafting/wood/workbench/oak`, `afc:crafting/wood/lumber/ipe_from_planks`, `firmages:crafting/hearthstone` open; nothing of Create, Firmalife, Create Deco, IE | 4,174 open (+4,130: tfc 2,474, afc 684, minecraft 677, framedblocks 251, totemic 26, mowziesmobs 8, ftbquests 6 …); landmarks open, no later mod open | pass |
| Bronze Age (age_1) open set | landmarks Create cogwheel and water wheel, `firmages:crafting/sky_disc`; nothing of Create Deco, Design n Decor, Steam 'n' Rails, IE, Mekanism | 6,183 open (+2,009: firmalife 710, create 582, tfcastikorcarts 238, tfc 154, rnr 94, sophisticatedbackpacks 73, create_connected 66 …) | pass |
| Iron Age (age_2) open set | landmarks `firmages:crafting/steel_heart`, Create brass hand, any `railways:` recipe; nothing of Occultism, Ars, IE, Mekanism, Mystical Agriculture | 8,815 open (+2,632: dndecor 508, twilightforest 366, railways 362, createframed 326, createdeco 273, createcasing 241, minecraft 108, tfc 87, bits_n_bobs 86, tfcreate 71 …) | pass |
| Live locks equal the Age tags | for every recipe result: the stages PS reports (`getRequiredStages`) = the item's `age_items` tag | 31,170 results checked, 0 mismatches (MekaSuit items: age_9 plus finale_won, as intended) | pass |
| Create plates removed | no recipe makes create:{iron,copper,golden,brass}_sheet | none | pass |
| Create zinc/brass chain | no usable Create zinc/brass ingot source; TFC zinc → `9x create:zinc_nugget` → andesite alloy from `tfc:rock/cobble/andesite` | `firmages:crafting/zinc_nugget`, `firmages:heating/create_zinc_nugget`, `andesite_alloy_from_zinc` (grid and mixing) take `#c:nuggets/zinc` + TFC andesite cobble; iron variants gone. Before the fix TFC casting gave `create:zinc_ingot` (Almost Unified) and WoodenCog knapped andesite alloy from rocks alone (Fixes 2, 3). `occultism:miner/eldritch/raw_zinc` gave `create:raw_zinc_block` until Fixes 10 removed it | pass after fix |
| IE hammer plates / hammer crushing | no `crafting/plate_*_hammering`, `hammercrushing_*`, `raw_hammercrushing_*` | 25 recipes were present, now 0 (Fixes 4) | pass after fix |
| Mekanism metal ingots hidden | Mek tin/bronze/steel/lead/uranium ingots lose to TFC/IE | Almost Unified now hides `mekanism:ingot_tin`, `ingot_bronze`, `ingot_steel`, `ingot_lead`, `ingot_uranium` (and `nugget_lead`, `nugget_steel`, `nugget_uranium`); osmium stays Mekanism by design. Before the fix AU hid the TFC ingots instead | pass after fix (EMI view needs client, C14/C19) |
| Create crushing of ores | no `create:crushing/raw_*`, `*_ore`, compat ores, the four Create stones | none left; 44 `firmages:crushing/*` present | pass |
| TFC knapping / heating from KubeJS | exist | `firmages:knapping/unfired_hearth_idol` (tfc:knapping), `firmages:heating/hearth_idol` and 8 more (tfc:heating) | pass |
| Occultism miner outputs | Dimensional Mineshaft gives only rich TFC ore pieces of metals unlocked by age_3 (Doc 08 section 7) | first pass: 92 miner recipes with vanilla, Mekanism, IE, Create and MA ores, raw-ore blocks and gems. Now 39: 11 `firmages:miner/ores/rich_*` (native copper, malachite, tetrahedrite, cassiterite, sphalerite, bismuthinite, native silver, native gold, hematite, limonite, magnetite; results locked age_0 to age_2), plus Occultism's own materials (iesnium, otherstone, mining core), Theurgy sal ammoniac and plain world blocks (Fixes 10). The miner GUI itself needs a client | pass after fix |
| TFC results stay TFC | casting/anvil/welding/quern give TFC items | before the fix: `tfc:casting/copper_ingot` → `minecraft:copper_ingot`, `tfc:casting/tin_ingot` → `mekanism:ingot_tin`, `tfc:anvil/metal/ingot/black_steel` → `immersiveengineering:ingot_steel`, TFC quern powders → 100 mB Mekanism dusts (×5 per ore). After: all TFC; the only non-TFC results are the Mekanism osmium ingot, Mekanism sulfur dust and `mekanism:steel_casing`, which the design wants | pass after fix (Fixes 5) |

### Worldgen (fresh world, Chunky, `scan_regions.py`)

| Check | Command | Expected | Observed | Result |
|---|---|---|---|---|
| Pregen overworld | `chunky spawn`, `chunky radius 512`, `chunky start` | completes | 4,225 chunks in 64 s; 3,219 full chunks inside the circle scanned | pass |
| Create zinc and striated ores | `scan_regions.py --x -2912 --z -2236 --radius 512` | 0 | `create:zinc_ore`, `deepslate_zinc_ore`, `crimsite`, `asurine`, `veridium`, `ochrum`: 0 | pass |
| IE ores | same | 0 | `immersiveengineering:*`: 0 blocks | pass |
| Mekanism ores | same | 0 | `mekanism:*`: 0 blocks | pass |
| Occultism silver | same | 0 | `occultism:silver_ore*`: 0 | pass |
| TFC ores present | same | > 0 | 1,690,551 `tfc:ore/*` blocks in 98 ids | pass |
| Draconic, Mystical Agriculture (Doc 08 §13.2 rule 2) | Nether and overworld scans | 0 | first world: 239 `draconicevolution:nether_draconium_ore` and 127,476 `mysticalagriculture:soulstone`/`soulium_ore` in 112 Nether chunks; fresh world after Fixes 6: 0 of both in 204 Nether chunks and in the overworld | pass after fix |
| Other ore sources | `scan_regions.py --match mekatfc: --match tfc_ie_addon:ore --match firmalife:ore --match tfcreate:` | every generating ore has a disguise | first pass: `mekatfc:ore/*_native_osmium/*` 118,990 blocks and `tfc_ie_addon:ore/*` (galena, bauxite, uraninite) 275,530 blocks without disguise. The second scan also found `firmalife:ore/*_chromite/*` (22,138 blocks) and the TFCreate quartz vein `tfcreate:quartz_<rock>` (398,296 blocks). All four families now have override rows and `age_blocks` membership (Fixes 9): galena age_4, chromite age_4, bauxite age_5, osmium age_5 (visible from 5, Mekanism in 6), uraninite age_7, TFCreate quartz age_2 | pass after fix |
| Ore disguise config | `/fa_dims` (reads LockRegistry), `progressivestages validate` | loads without errors | 1,337 `[[ores.overrides]]` rows (age_1 488, age_2 276, age_3 168, age_4 213, age_5 128, age_7 64; was 996), all targets are registered blocks, spoof active; `28/28 stage files valid` | pass; the visual check needs client (C3) |

### Dimensions

| Check | Command | Expected | Observed | Result |
|---|---|---|---|---|
| The Origin | `/fa_dims` | `firmages:origin` exists if defined | not defined yet; `age_9.toml` already locks `id:firmages:origin` | noted (not built) |
| Nether via Beneath | `execute in minecraft:the_nether run locate biome #minecraft:is_nether`; Chunky radius 128 | Beneath terrain | `nether_wastes` found; 204 chunks with 37,202 `beneath:*` blocks (crackrack, nether gold ore, cursecoal), TFC magma rock | pass; the ritual portal needs client |
| Twilight Forest | Chunky radius 128 in `twilightforest:twilight_forest` | dimension generates | 204 chunks, 41 `twilightforest:*` block ids | pass |
| Dimension locks | `/fa_dims` | Twilight age_2, Nether age_3, End age_6, Ad Astra 7/8, all Stargate Journey worlds disabled | as expected after Fixes 7; before, `sgjourney:destiny` and `sgjourney:tollan` had no lock. `ae2:spatial_storage` has none (reachable only through AE2 spatial IO) | pass after fix |
| Teleport-back on a locked dimension | `/execute in twilightforest:twilight_forest run tp @s ...` | sent back | needs a player | needs client (C9) |

### Performance (fresh world, after pregen, no player)

| Measure | Command | Observed |
|---|---|---|
| TPS | `spark tps` | 20.0 (5 s, 10 s, 1 m); the 5 m value (15.3) still contains the pregen |
| MSPT | `spark tps` | min/median/95th/max 1.5 / 1.9 / 2.7 / 3.3 ms |
| All dimensions | `neoforge tps` | 20 TPS in all 25 dimensions; overall 1.6 ms/tick (first world, after pregen) |
| Memory | `spark health` | 4.8 GB of 8.0 GB heap in use (60 %) |
| GC | `spark gc` | G1 young 31.8 ms average every 6 s; no old-generation collection |
| CPU | `spark health` | process 0 to 1 %, system 13 % |
| Profiler | `spark profiler start`, 5 min idle, `spark profiler stop --save-to-file --comment idle-5min` | Saved locally, not uploaded: `test-server/config/spark/profile-2026-09-30_11.52.05.sparkprofile` (git-ignored; the spark web viewer can open it). Server thread 309.6 s sampled: 95.3 % waiting for the next tick (`waitUntilNextTick`), 4.2 % in `tickServer` (about 2.1 ms per 50 ms tick). Largest self times: Create Aeronautics' Sable physics `Rapier3D.step` 1.3 %, `ThreadedLevelLightEngine.tryScheduleUpdate` 1.0 %, NeoForge `EventBus.post` 0.9 %, `DimensionDataStorage.computeIfAbsent` 0.5 %. With no player online nothing from TFC, Create or Mekanism ticking stands out. Read with a small local reader for the .sparkprofile protobuf |

### Age registry tags (shared data model, `dev/age_map.toml`)

| Check | Command | Observed | Result |
|---|---|---|---|
| Registry ids from the running server | `/fa_dump` writes `local/firmages/registries.json`; `gen_stage_locks.py --registry test-server/local/firmages` | 23,503 items (without air), 17,302 blocks, 761 fluids; 104 item namespaces and 91 grid-recipe namespaces, all mapped in `[mods]` (an unmapped namespace stops the generator) | pass |
| One tag per item | `gen_stage_locks.py` | `age_items`: dawn 5,272, age_0 700, age_1 3,733, age_2 5,363, age_3 1,935, age_4 1,858, age_5 465, age_6 2,331, age_7 691, age_8 725, age_9 396, disabled 34 = 23,503, each item exactly once | pass |
| Ore blocks per Age | same | `age_blocks`: age_0 213 (copper, visible), age_1 488, age_2 276, age_3 168, age_4 213, age_5 128, age_7 64 | pass |
| PS uses the tags | stage files | each `age_N.toml` locks items only through `"tag:firmages:age_items/age_N"` (one tag lookup per stage; PS scans its selector list linearly, so thousands of `id:` entries would be slow) and grid recipes through generated `mod:<ns>` lines; `always_unlocked` is gone (the canonical Iron Age dusts are simply `age_2` items) | pass; EMI hiding by tag needs client (C19) |

### M0 reload measurement (decides reload vs hot swap, `mod/firmages-core/SPEC.md` section 3)

Setup: pregenerated world of the first pass, no player online, TPS 20 before each run. Stall = the one server tick that
contains `/reload` (spark tick monitor "Tick #… lasted"). It equals the RCON round trip of the `reload` command and the
vanilla "Can't keep up! … behind" value within 50 ms, because `reloadResources` blocks the server thread until it is done.

| Run | Condition | Stall per reload (s) | KubeJS recipe phase (s, "taking … in total") |
|---|---|---|---|
| A | 2 min after boot, explicit `spark profiler start` running | 10.98, 7.27, 7.67 | –, –, 2.56 |
| B | same session, profiler cancelled | 7.17, 6.27, 6.34 | 2.43, 2.05, 2.05 |
| C | fresh boot, spark background profiler on (pack default), 30 s after `Done` | 6.55, 5.95, 5.61 | 2.24, 2.10, 1.97 |

- Recipes after each reload: 35,314 (`RecipeManager` "Loaded 35314 recipes"; KubeJS finds 35,397 and skips 5,793 before its changes; Added 272, removed 281, modified 17).
- Where the stall goes (run A profile `profile-2026-09-30_11.42.51.sparkprofile`, 25.8 s inside `reloadResources` for 3 reloads): `RecipeManager.apply` 11.4 s (KubeJS recipe event 7.4 s, Almost Unified 1.5 s), light-engine polling while the server thread waits for the reload workers 6.4 s, ProgressiveStages stage-file reload 1.9 s, `PlayerList.reloadResources` 1.1 s, the rest (tags, TFC and other listeners, GC) about 5 s. Per reload: recipes about 3.8 s, of which KubeJS about 2.5 s.
- The 10.98 s outlier is the first reload after the explicit profiler started, with four young-gen GCs (37 to 127 ms) inside. With the profiler cancelled, or with only the default background profiler, the same world stays at 5.6 to 7.2 s.
- Stage grant via `/fa_selftest` (3 runs): `ProgressiveStages.grant` 1 to 2 ms and `reconcile()` 0 to 1 ms per Age step (server side, FakePlayer). The whole command takes 9 to 10 s, but that is `ProgressiveStages.revokeAll` at start and end (about 5 s each), which gameplay never calls. With a real player PS also syncs stages to the client: needs client.
- **Client EMI rebuild: not measured.** It needs a connected client (recipe and tag sync, EMI/JEI re-index), and none was available. The server-side cost of sending 35k recipes and all tags to connected players is not in these numbers either (0 players).
- **Verdict against the SPEC thresholds:** the server stall is 5.6 to 7.7 s in 8 of 9 reloads and 11.0 s once under an explicit profiler; all stay below the 15 s target and far below the 30 s client timeout. The 10 s limit holds under normal conditions, so **the reload path stays; no phase-2 hot swap on the server numbers.** The decision is provisional until the client half of M0 (EMI rebuild ≤ 30 s, stall with 1 to 4 players connected) is measured. An active spark profiler adds up to 3 s to a reload.

### firmages-core M1 (core: AgeState, mirror, AgeIndex, ReloadScheduler, binding, commands)

Setup: `mods/firmages-core-0.1.1.jar` (plain jar in the index), test server synced from `packwiz serve`, pregenerated
world of the first pass, no player online. Eight boots with `run_server.py`; mirror = `world/firmages/ages.json`.
Stage changes came from ProgressiveStages events (`/fa_selftest`, FakePlayer) and from
`/firmages ages simulate grant|revoke` (`debug.allowSimulate = true` for the test, set back to `false` afterwards).
The initial-load checks used a local probe script in `test-server/kubejs/server_scripts/` (not in the pack, removed
afterwards) that calls `FirmAges.unlockedAges()` at script load and `FirmAges.lockedOreBlocks()` in
`ServerEvents.tags('block')`, adds the answer to the tag `firmages:probe_locked` and reads that tag back with `/fa_probe`.

| Check | Command | Observed | Result |
|---|---|---|---|
| Mod loads | boot | `firmages-core loaded`; KubeJS lists plugin source `firmages`; 0 ERROR lines from `FirmagesCore` in all 8 boots except the deliberate corrupt-mirror boot; KubeJS startup and server logs 0 errors in every boot | pass |
| AgeIndex from the pack tags | boot log | `AgeIndex gen 1: 23469 items, 1550 blocks, 0 fluids in 72-101 ms`; per Age equal to the generated tags (dawn 5,272 … age_9 396; `disabled` is not an Age) | pass; 0 fluids because `age_fluids` is not generated yet |
| Coverage vs ProgressiveStages | boot log, `logs/firmages-coverage.txt` | `all 18197 ProgressiveStages Age item locks are in an age tag` | pass |
| `/firmages ages` | RCON | unlocked/locked lists, AgeState version, mirror status, boot snapshot source, reload status, index summary | pass |
| `/firmages dump registry` | RCON | `logs/firmages-registry.json`: 23,504 items, 17,302 blocks, 761 fluids of 107 mods (same as `/fa_dump`) | pass |
| `/firmages selftest all` | RCON, `logs/firmages-selftest.json` | 26 passed, 0 failed (core 17, server 9, including `age_index_not_empty`, `age_tags_present`, `age_coverage_vs_progressivestages`, `binding_consistent: 1274 locked ore blocks`) | pass |
| Real PS events, coalescing | `/fa_selftest` | 14 Age changes in one tick (grant dawn … age_6, revoke age_6 … age_2, revokeAll): 14 `AgeState now` lines (version 0 → 14), a revoke warning each, **one** reload 7 s later (`Age reload (granted age_0, …, revoked age_1) finished in 5739 ms`), mirror version 14 | pass |
| Grant → mirror → one reload | `simulate grant age_2` | `AgeState now [dawn, age_2] (version 15)`, reload after 60 ticks, 5859 ms, mirror `[dawn, age_2]` v15 | pass |
| Restart, mirror = SavedData | boot 3 | `Boot Age snapshot [dawn, age_2] from MIRROR`; probe sees `[dawn, age_2]` during the load; `matches the boot snapshot (MIRROR) used during the initial load; no reload` | pass |
| Restart, corrupt mirror | boot 4, truncated JSON | ERROR `Age mirror … is corrupt (not valid JSON …)`, snapshot = fallback; probe sees the fallback; `the initial datapack load used [dawn, age_0] from FALLBACK_CORRUPT_MIRROR, AgeState is [dawn, age_2]; reloading once`; 1 reload 5807 ms; mirror rewritten | pass |
| Restart, stale mirror | boot 5, valid mirror `[dawn]` v14 | `used [dawn] from MIRROR, AgeState is [dawn, age_2]; reloading once`; 1 reload 6161 ms; mirror v15 | pass |
| Restart, no mirror (SPEC P test) | boot 6, `bootFallbackStages = ["dawn"]` | `FALLBACK_NO_MIRROR [dawn]`, 1 reload 6048 ms, selftest `boot_snapshot_fail_strict` pass | pass |
| Boot fallback read from `config/` | boot 2, `bootFallbackStages = ["dawn", "age_0"]` in `config/firmages-server.toml` | 0.1.0 ignored it (see fix below); 0.1.1: `Boot Age snapshot [dawn, age_0] from FALLBACK_NO_MIRROR` | pass after fix |
| KubeJS pre-capture on the initial load | boot 7, mirror ok | initial load: `lockedOreBlocks() = 0 ids`, mod logs `answered 1 time(s) before the initial load's tags were readable`, one reload after start; both KubeJS runs of that reload answer 1,274; `/fa_probe`: tag `firmages:probe_locked` = 1,274 | pass |
| Binding follows a grant | boot 7, `simulate grant age_0` | both KubeJS runs of the reload answer 1,061 (1,274 − 213 age_0 blocks); `/fa_probe`: tag = 1,061 | pass |
| Revoke | boot 8, `simulate revoke age_2`, `simulate revoke age_0` | WARN `restart the server to clear in-progress items`, one reload each (5674 ms, 5319 ms); world back to `[dawn]` v18 | pass |

- Reload time as the mod logs it (`Age reload (…) finished in`): 5,251 to 6,161 ms over 9 reloads (no player, pack default
  spark background profiler), in line with the M0 stall of 5.6 to 7.7 s.
- Fix found here: NeoForge 21.1 keeps server configs in `config/` (the world's `serverconfig/` only holds overrides, see
  boot-report). firmages-core 0.1.0 read `gate.bootFallbackStages` only from `<world>/serverconfig/` and `defaultconfigs/`,
  so the pack's setting was ignored at boot. 0.1.1 reads the world override, then `config/`, then `defaultconfigs/`. It
  also no longer logs "matches the boot snapshot" when nothing read the snapshot during the initial load.
- In M1 nothing in the pack reads the boot snapshot during the initial load (no recipe filter yet, no pack script uses
  `FirmAges`), so a plain restart logs `nothing read the boot snapshot …; no reload`. The fail-strict reload path starts
  working by itself once m2 or the m1/m3 tag scripts read the snapshot; the probe runs above prove it.

## Content: Arcane and Industrial Age

Date: 2026-09-30, branch `dev` (commits 7e6c7f7 to 9179de0). Test server synced with `packwiz-installer-bootstrap`
from `packwiz serve`, pregenerated world of the first pass, no player online. Final boot: KubeJS 2/2 startup and
17/17 server scripts, **0 errors**; `Added 557 recipes, removed 970 recipes, modified 43 recipes, with 0 failed
recipes`; 35,114 recipes loaded. `python dev/poc_analyze.py`: **all checks PASS** (the earlier A- checks and the new
C- checks). `gen_stage_locks.py --check`: 0 files to rewrite.

### What the pack now contains

| Area | Content | Where |
|---|---|---|
| Arcane goal | `firmages:arcane_keystone` (age_3): Occultism ritual, pentacle `occultism:craft_djinni`, bound Djinni book; Spirit Attuned Crystal, iesnium ingot, `#firmages:boss_token/age_3` (Wilden Tribute), Mercury Catalyst, `tfc:metal/ingot/steel`, source gem block | `recipes/age_3/arcane_age.js` |
| Magic in a TFC world | 159 magic recipes rewritten by JSON (Ars types have no KubeJS schema, so `replaceInput` does not reach them): vanilla tools and armour to TFC heads, unfinished armour or sheets; crafting table, furnace, blast furnace, smoker, campfire, anvil, lantern, glass bottle, bucket, saplings, crops, meats, vanilla rocks, sand, gravel to TFC items or tags. 21 Arcane progression recipes take `#firmages:gems/arcane` (TFC emerald, ruby, sapphire, topaz, opal) instead of diamonds (kimberlite is age_4). Purple chalk takes TFC amethyst powder instead of End stone dust; `mekanism:dust_obsidian` (the unified obsidian dust) is age_3 | `recipes/age_3/arcane_tfc_inputs.js`, `dev/age_map.toml` |
| World gaps | Archwood saplings (four colours) from a TFC sapling and TFC gem powder; source berry bush from a TFC blueberry; Datura seeds at 2 % from 17 TFC grasses (loot modifier); **Arcane Soil**: 8 TFC dirt + 1 amethyst give 8 vanilla farmland; TFC animals in Occultism's entity tags (`c:cows`, `c:pigs`, ... sacrifices and butcher-knife tallow); Starbuncle, Whirlisprig and Drygmy spawns in TFC land biomes; TFC grass in `animals_spawnable_on`; Summoning Rituals altar recipe | `arcane_age.js`, `kubejs/data/firmages/loot_modifiers`, `kubejs/data/firmages/neoforge/biome_modifier`, `tags/arcane_industrial.js` |
| One magic carrier per function | Occultism crusher: no ore, raw ore, raw block, clump or metal-ingot recipes (iesnium stays). Theurgy: standard ore liquefaction removed; 15 `firmages:liquefaction/*` for RICH TFC pieces (copper, tin, zinc, gold, silver, iron, nickel, lead, aluminium, uranium, osmium); incubation gives the canonical dust. Foliot transporter ritual, Warp Index and the diamond Ritual of Scrying removed. Theurgy rods refuse `#firmages:age_blocks/age_4` to `age_9` | `arcane_age.js`, `tags/arcane_industrial.js` |
| Industrial goal | `firmages:pressure_core` (age_4): black steel double sheet x2, IE Heavy Engineering Block, `firmages:arcane_gearbox`, `#firmages:boss_token/age_4` (`cataclysm:monstrous_horn`, a guaranteed Monstrosity drop). Arcane Gearbox (grid, Wixie-automatable) from source gems, steel sheets and a Create gearbox; it sits in the reinforced blast brick (4 per gearbox, Improved Blast Furnace). Netherite Monstrosity summon at the Summoning Rituals altar (boss fallback 1) | `recipes/age_4/industrial_age.js` |
| IE unified | Coke oven: TFC coal only. Crusher: 60 `firmages:crusher/*` (15 ores x 4 grades): quern-amount TFC mineral powder as main output, canonical dust with chance 2 x piece mB / 100, IE's secondary metal at 5 % of that; IE tag recipes and TFC + IE powder recipes removed. Metal press: one-ingot plate mold removed for the 11 TFC metals (TFC + IE two-ingot sheet mold stays). Alloy kiln: 6 `firmages:alloy_kiln/*` in TFC ratios. Arc furnace ore doubling removed. Blast and alloy bricks rebuilt from TFC fire bricks (TFC + IE disables IE's recipes and adds none, so neither multiblock could be built) | `industrial_age.js` |
| Disable list | IE steel gear, windmill, watermill, kinetic dynamo, conveyors except the basic belt, thermoelectric generator, refinery, IE silver/nickel/steel ingots, copper nugget, TFC-metal plates, IE silver/nickel ores; Crafts & Additions except Electric Motor and Alternator (both rebuilt with IE LV coils); CBC alloying, steel and cast iron; vanilla furnace ore recipes (they run in Create bulk blasting) | `industrial_age.js`, `global_removals.js`, `stages/disabled.toml`, `HIDDEN_ITEMS` (122 items) |
| Create 6 packages | Packager, Frogport (IE iron component), Stock Link, Chain Conveyor (IE steel component); the rest is made from these; all age_4 | `industrial_age.js` |
| Gear rule (age_4) | Netherite tools and armour, Cataclysm cursium/ignitium/monstrous helm and the Incinerator, Create netherite backtank and diving gear: the base is a TFC black steel part of the same slot. Create Jetpack and netherite jetpack direct (fluid tank and propeller instead of backtank and elytra). Apotheosis golden-to-diamond upgrades removed. IE shield from a steel double sheet | `industrial_age.js` |
| MekaTFC | 10 broken originals switched off by `neoforge:false` overrides; 12 `firmages:{purifying,injecting,dissolution}/native_osmium_<grade>` in Mekanism 10.7 format (3x: small 7->2, poor 7->3, normal 4->3, rich 1->1 clumps; 4x: 5->2, 5->3, 1->1, 3->4 shards; 5x: 100/150/250/350 mB slurry); MekaTFC furnace, enriching and crusher ore recipes removed; pieces out of `c:raw_materials/osmium`, blocks out of `c:ores/osmium` | `recipes/age_6/mekatfc_osmium.js`, `kubejs/data/mekatfc/` |
| Mowzie drops | Frostmaw ice crystal and Petiole disc age_4; Sculptor staff, earthrend gauntlet, Geomancer set, bluff rod, sand rake age_5 | `dev/age_map.toml` |
| Stubs | `firmages:attuned_circuit` (age_5), `firmages:awakened_keystone` (age_8): items only, recipes come with their Ages | `startup_scripts/items.js` |

### Checks (`poc_analyze.py`, needs `fa_dump` and `fa_dump_full`)

The C- checks run a recipe-graph closure from what a TFC world yields without a recipe (every item of TFC, AFC,
Firmalife and Beneath; vanilla mob drops and Nether blocks of the mob ladder; Wilden and Starbuncle drops; Datura
seeds and tallow from the loot changes; saplings grow, Datura and Magebloom only with vanilla farmland).

| # | Check | Result |
|---|---|---|
| C-3a | Keystone ritual: type, pentacle, 6 ingredients + activation book; item age_3, PS item lock age_3; boss token = Wilden Tribute | pass |
| C-3b | Spirits, Source and Alchemy chain (40 items incl. stations, Mineshaft, Djinni miner, Starbuncle charm, Ritual of Flight, altar, Keystone) obtainable | pass (before the fixes: 685 magic recipes had an unobtainable input; Datura, archwood, chalks, spellbooks, imbuement chamber, source jar and the Wilden ritual were not makeable) |
| C-3c/d | every chain item is age_3 or earlier, and the chain closes with age_3 items only | pass (before: Spirit Attuned Gem and 20 more needed diamonds; purple chalk needed End stone and Mekanism obsidian dust) |
| C-3e/f | no Occultism ore crushing, only the 15 rich-piece spagyrics, no transporter/Warp Index/diamond scrying; no miner output of a later Age | pass |
| C-4a/b | Pressure Core recipe and Ages; coke, blast, reinforced blast and alloy bricks, treated wood, engineering blocks, LV power, IE workbench, Alternator, Motor, package tier, Jetpack and the Pressure Core close with age_4 items | pass (before: blast and alloy bricks had no recipe) |
| C-4c | IE-type recipes give no disabled item and no IE/vanilla ingot, plate or non-canonical dust of a TFC metal | pass |
| C-4d | TFC ore pieces are consumed only by TFC hand stations, WoodenCog heated melting, Create crushing/milling, Theurgy, the IE Crusher and grid parts (stations up to age_4) | pass (before: IE crusher and arc furnace tag recipes, vanilla furnace via bulk blasting, CBC) |
| C-4e | TFC sheets only from two ingots (stations up to age_4: TFC anvil, Create and WoodenCog press, IE Metal Press, sequenced assembly) | pass (before: IE plate mold, 1 ingot -> 1 sheet) |
| C-4f | Alloy kiln = TFC ratios, no IE ratio left | pass (before: rose gold 3 copper : 1 gold, bronze 3:1, brass 1:1) |
| C-4g | coke only from the coke oven on TFC coal; new TFC steel only from TFC, IE Blast Furnace and Arc Furnace | pass (before: CBC 2 iron + coal -> 2 steel in a heated mixer, reachable in the Iron Age) |
| C-4h/i/j | C&A down to Motor and Alternator; package tier carries IE components; no age_4 gear eats gear (Cataclysm boss-weapon fusion excepted) | pass |
| C-M | MekaTFC: 0 parse errors in `latest.log`, 12 grade recipes, no furnace/enriching/crushing of pieces, pieces out of the raw tag | pass |
| C-W | Mowzie drop Ages | pass |

Server-side plant test (`setblock` soil, plant, neighbour update, `execute if block`): Datura and Magebloom break on
`tfc:farmland/mollisol` and stay on `minecraft:farmland`; archwood saplings, the source berry bush and the Otherworld
sapling stay on TFC grass and dirt. Iesnium ore generates in the Beneath Nether (149 `occultism:iesnium_ore_natural`
in the scanned Nether chunks). 0 Ars, Theurgy or Occultism blocks in 3,219 overworld chunks (the reason for the
recipe route to archwood and source berries).

Reload with the new content (2 runs, no player): stall 8.1 and 9.0 s, KubeJS recipe phase 3.6 to 3.7 s (was 5.6 to
7.7 s and 2.0 to 2.5 s in M0). The JSON rewrite of the magic recipes and the forEachRecipe scans cost about 1.5 s per
reload; still below the 10 s limit of SPEC section 3.

## M2/M3, quests and content

Date: 2026-10-01, branch `dev`, firmages-core **0.2.1** (late pass, see below). Test server synced with
`packwiz-installer-bootstrap` from `packwiz serve` (no stray files: `kubejs/` equals the repo apart from KubeJS's
generated `README.txt` and `config/`, the quest folder was deleted and re-synced byte-identical), **fresh world**
(`--wipe-world`) for each of two runs, no player. Run 1 found the problems fixed below; run 2 is the final state and
gives the numbers. `debug.allowSimulate = true` only during the runs, `false` again afterwards.

### Boot

| Check | Observed (run 2) | Result |
|---|---|---|
| KubeJS | startup 2/2, server 20/20 scripts, 0 errors, 0 warnings; `Added 562 recipes, removed 975 recipes, modified 43 recipes, with 0 failed recipes` | pass |
| ProgressiveStages | 0 ERROR lines; `progressivestages validate`: 28/28 stage files valid | pass |
| FTB Quests | `Loaded 2 chapter groups, 6 chapters, 117 quests, 7 reward tables`, translation tables for 1 language, 0 ERROR lines | pass |
| firmages-core | 0 ERROR lines; `Boot Age snapshot [dawn] from FALLBACK_NO_MIRROR`, `AgeState [dawn] matches the boot snapshot ...; no reload`; `firmages selftest all` 44 passed, 0 failed | pass |
| Other errors | only the known ones: 35 recipe parse errors of TFC Regrowing Forests, WoodenCog and Create Deco, Sable `copycat_catwalk`, TF `dev_new_world` function, three Train Utilities advancements, client-class DISTXFORM lines | not ours |

### M2: recipe gate on the pack

Every Age was unlocked with `firmages ages simulate grant age_0` ... `age_9`, one reload each; after each reload
`fa_dump`, `fa_dump_full` and `fa_m3` wrote the state, and `poc_analyze.py --dump-dir <Age> --baseline <age_9>` ran
the G-checks.

| Unlocked up to | Recipes loaded | Dropped (latest locked output) | Late pass (175 added) | Resolved-result drops | Gate ms | Reload stall s |
|---|---|---|---|---|---|---|
| dawn (boot) | 12,339 | 22,775: age_0 1,209, age_1 3,899, age_2 7,461, age_3 2,401, age_4 1,750, age_5 368, age_6 3,577, age_7 763, age_8 725, age_9 616, disabled 6 | 175 dropped | 4 | 575 (534 + late) | boot |
| age_0 | 13,544 | 21,541 | 103 dropped | 4 | 350 | 8.82 |
| age_1 | 17,443 | 17,642 | 25 dropped | 4 | 412 | 8.84 |
| age_2 | 24,904 | 10,181 | 0 | 4 | 351 | 7.84 |
| age_3 | 27,304 | 7,781 | 0 | 5 | 278 | 7.28 |
| age_4 | 29,054 | 6,031 | 0 | 2 | 278 | 7.48 |
| age_5 | 29,422 | 5,663 | 0 | 2 | 346 | 7.30 |
| age_6 | 33,000 | 2,085 | 0 | 0 | 250 | 7.06 |
| age_7 | 33,743 | 1,342 | 0 | 0 | 255 | 7.29 |
| age_8 | 34,463 | 622 | 0 | 0 | 254 | 7.15 |
| age_9 | 35,079 | 6 (disabled) | 0 | 0 | 323 | 7.70 |

- **Gate time:** 35,114 recipes (34,939 in `RecipeManager#apply` plus 175 added later). 534 to 575 ms on the
  initial load (extractor class loading), 250 to 412 ms on every Age reload (late pass 11 to 41 ms of it). The
  500 ms target holds on reloads; the boot load is 34 to 75 ms over it once per start. The audit
  (`/firmages recipes audit`, `logs/firmages-recipe-audit.txt`) reports the same numbers.
- **Reload stall** (spark `tickmonitor --threshold-tick 1000`, the tick that contains the reload): run 1 6.32 to
  7.36 s, run 2 7.06 to 8.84 s over 10 reloads each; the KubeJS recipe phase is 2.65 to 3.56 s. Below the 10 s
  limit of SPEC section 3. The 1 to 2 s ticks after each reload are the dump commands, not the game.
- **Each Age's recipes appear after one reload:** one `Age reload (granted age_N) finished in` line per grant, and
  the loaded count grows as in the table.

| # | Check (`poc_analyze.py`) | Result |
|---|---|---|
| G-1 | no loaded recipe has an output of a locked Age or of `age_items/disabled` (independent mirror of GateRules on the full JSON, tag outputs locked only when every member is) | pass at all 11 states |
| G-2 | every recipe of the all-Ages dump whose outputs are all unlocked is loaded (the gate drops nothing more) | pass at dawn to age_4. Run 1 failed: the mirror missed IE `secondaryOutputs`/`strippingSecondaries`/`slag`, which the gate's IE extractor reads (sawmill sawdust, arc-furnace slag; mirror fixed). 1 to 2 Evolved Mekanism solidifying recipes per state have a tag output the mirror cannot resolve; the gate drops them by the stack Mekanism resolves (`moremekanismprocessing:dust_amethyst`, age_6 station anyway); listed, not failed |
| G-3 | no recipe waits for a chance byproduct of a later Age than its main output and station; every such recipe is in `recipes/byproducts.js`, and is loaded once its main output and station are open | pass (5 entries). Negative test with an empty table: lists exactly gravel splashing, tuff crushing (2) and the IE blaze-powder crusher recipe |
| G-4 | every recipe type with no detected output is accepted (`dev/data/accepted_undetected.json`) | pass: 37 types, 37 accepted |

- **Undetected outputs (audit review):** 813 recipes in 37 types and 99 serializers have no detected output. The 37
  types make no item (fuels, fertilizer, windmill biomes, reservoirs, world data, rituals with an effect), change
  only the input item (enchanting, upgrades, smoking, scroll writing) or have only inputs of the same late Age (AE2
  cell disassembly, Ad Astra space station). Accepted, no extractor follow-up (decision log). The other 99
  serializers belong to types where other recipes are detected (special crafting, TFC pot dynamic food).
- **Late pass (firmages-core 0.2.1):** Create Dragons Plus adds 175 sandpaper polishing recipes at the TAIL of
  `ReloadableServerResources#updateRegistryTags`, after `RecipeManager#apply`; 0.2.0 never saw them. The late pass
  gates them (175 dropped at Dawn, 103 at age_0, 25 at age_1, 0 from age_2). It also drops 0 to 5 IE/Occultism
  recipes per state whose tag result, resolved once the tags are bound, is a locked item (none from age_6 on, so
  all of them resolve to Information Age items such as Mekanism dusts). Mystical Agriculture's code-added recipes (Cucumber `cucumber$apply`) are inside
  `apply` and were always seen by the main pass (javap).
- **Byproduct policy:** recipes dropped only because a chance byproduct is locked are not allowlisted;
  `recipes/byproducts.js` strips the byproduct while its Age is locked (the precision mechanism's disabled scrap,
  formerly in `iron_age.js`, moved there). The 8 IE crusher recipes of galena and native silver were dropped until
  age_6 by their canonical lead dust; `mekanism:dust_lead` is now age_4 (decision log), so they open with the
  Industrial Age. Without the precision-mechanism strip the Steel Heart had no recipe at all (run 1 found it).

### M3: miners and prospecting (`/fa_m3`, 2,000 rolls per mineral mix)

| Unlocked up to | Mixes with a locked ore | Locked rolls | Spoil rolls | Locked ore blocks | not in `mekanism:miner_blacklist` | `tfc:prospectable` size (locked members) | `precisionprospecting:prospectable_mineral` |
|---|---|---|---|---|---|---|---|
| dawn | 20 of 26 | 0 of 52,000 | 31,930 | 1,550 | 0 | 117 (0) | 3 (0) |
| age_0 | 20 | 0 | 27,515 | 1,337 | 0 | 306 (0) | 3 (0) |
| age_1 | 19 | 0 | 22,149 | 849 | 0 | 726 (0) | 108 (0) |
| age_2 | 12 | 0 | 11,131 | 573 | 0 | 999 (0) | 171 (0) |
| age_3 | 10 | 0 | 8,465 | 405 | 0 | 1,167 (0) | 339 (0) |
| age_4 | 4 | 0 | 3,323 | 192 | 0 | 1,377 (0) | 360 (0) |
| age_5 / age_6 | 3 / 1 | 0 | 1,936 / 1,315 | 64 | 0 | 1,503 (0) | 360 (0) |
| age_7 to age_9 | 0 | 0 | 0 | 0 | 0 | 1,566 (0) | 360 (0) |

IE (and TFC-IE Crossover) mineral mixes never yield a locked ore, the Digital Miner blacklist holds every locked
ore block at every state, and both prospecting tags exclude locked ores at Dawn and regain them with each unlock.

### Quests

| Check | Observed | Result |
|---|---|---|
| Load | 0 FTB Quests errors; 6 chapters, 117 quests, 7 reward tables, 2 groups = `dev/quests-notes.md` | pass |
| Stage rewards resolve | FTB Quests rewrote the files from memory on shutdown: the four goal rewards are still `type: "gamestage"` with `stage` age_0 to age_3, `auto: "invisible"` and the `interim_shrine` tags; `validate_quests.py --dir test-server/config/ftbquests/quests` on the rewritten files: 0 errors | pass |
| Chapter visibility | the rewritten Age chapters keep `progressivestages_required_stage` (dawn, age_0, age_1, age_2), so ProgressiveStages' ChapterMixin read and wrote the key | pass |
| Twilight portal | `twilightforest:portal/activator` = `["tfcreate:polished_quartz"]` in the live tags; quest text updated; `validate_quests.py` checks the portal tag Age against the chapter (negative test with `minecraft:diamond`: 1 error) | pass; building the portal needs client |

### Content and reachability (all-Ages dump)

All A- and C- checks pass after the merges (Arcane and Industrial Age unchanged). New **R- checks** walk the
recipe graph once per goal Age from the Dawn start set (TFC-world items, vanilla world items and drops by the mob
ladder, the Age's boss drops and the quartz-vein drop); a recipe fires only when all ingredients are reached, all
outputs are of that Age or earlier and its station's mod belongs to that Age or earlier (Create heat levels need a
heater). For each goal they report every ingredient that is not reached and why (the first unreached input of up to
three makers, three levels deep).

| # | Goal | Reached at its Age | Result |
|---|---|---|---|
| R-age_2 | Steel Heart: steel sheet, precision mechanism, wrought iron double sheets, Lich trophy; chain: mechanical crafter, deployer, press, fuel heater, steel, polished quartz (portal) | 10,662 items from 6,362 | pass (run 1 without the quartz-vein drop: polished quartz not reached, which the start set fixed) |
| R-age_3 | Arcane Keystone and the 40-item Arcane chain | 12,581 items | pass |
| R-age_4 | Pressure Core and the 29-item Industrial chain | 13,761 items | pass |

Negative test: the Steel Heart at age_1 and the Pressure Core at age_2 fail with each ingredient's Age named.

## Fixes made in the repo

1. **Rhino loop-body `const` (A5).** `grants.js` `reconcileNow()` declared `const wanted` inside a `for` loop. Rhino
   reports "redeclaration of var" on the second pass, so **every** reconcile (every Age grant, every login) threw before
   it granted a mob or helper stage. `debug/scan.js` had the same pattern. Both rewritten without a loop-body declaration.
2. **Almost Unified default config (A8, unification).** The pack shipped no AU config, so AU 1.4.2 ran with its defaults
   (`minecraft, kubejs, create, immersiveengineering, mekanism`): every TFC casting, anvil and welding recipe gave vanilla,
   Mekanism, IE or Create metal, and AU hid the TFC ingots and sheets in EMI. New `config/almostunified/unification/materials.json`:
   `mod_priorities` from Doc 10 v3 §8.2; dust and nugget overrides from §8.1 (dusts Mekanism, else IE, zinc `firmages`;
   copper/zinc/brass nuggets Create, iron/gold nuggets vanilla); `c:ores`, `c:raw_materials` and `c:storage_blocks/raw_*` taken out
   of the unified tags, because TFC ore grades and rock variants share those tags and AU collapsed them (small, poor and rich
   ore pieces became interchangeable, TFC collapse recipes turned ores into Mekanism/Create ore blocks); five Ad Astra
   `*_plateblock` recipes that AU could not rebuild are ignored.
3. **Tag hygiene for AU (`tags/unification.js`).** AU turns every tag member into the tag's target. TFC puts rose gold in
   `c:ingots/gold`, sterling silver in `c:ingots/silver`, cast and pig iron in `c:ingots/iron`, the coloured and high-carbon
   steels in `c:ingots/steel`, bismuth and black bronze in `c:ingots/bronze` (same for blocks), and its 5 mB ore powders in
   `c:dusts/<metal>`. These entries are removed; `tfc:metal/rod/wrought_iron` joins `c:rods/iron`.
4. **Zinc gate (`recipes/age_1/bronze_age.js`).** `woodencog:rock_knapping/andesite_alloy` and `..._deploying` made andesite
   alloy from andesite rocks alone. Removed, like the iron-nugget variants (Doc 10 v3 §7.3). TFCreate's welding recipes
   (ore piece + andesite → 4 alloy) stay.
5. **IE hammer (`recipes/global_removals.js`).** The 25 `plate_*_hammering`, `hammercrushing_*`, `raw_hammercrushing_*` grid
   recipes are removed (Doc 10 v3 §7.3, IE row).
6. **Foreign worldgen (Doc 08 §13.2 rule 2; config-todo rows 3 to 5).** `config/mysticalagriculture-common.toml`
   `generate{Prosperity,Inferium}Ore`/`generateSoulstone = false`; `neoforge:none` for the four Mystical Agradditions ore
   modifiers and for Draconic's overworld and Nether draconium. File names and keys taken from the jars and the generated config.
7. **Stargate Journey locks (`stages/disabled.toml`).** Added `sgjourney:destiny` and `sgjourney:tollan`, the two worlds missing
   from the research list.
8. **Tooling.** `dev/rcon.py`, `dev/scan_regions.py`, `dev/poc_analyze.py`, `run_server.py --hold` plus console inbox,
   `debug/dump.js`, `/fa_selftest` in `grants.js`.
9. **Age map, one source (commit 69c6867).** `dev/age_map.toml` holds the Age of every mod namespace (Doc 10 v3 section 1),
   the material canon as path words (section 8.1; max rule: an item takes the latest Age of its mod and its materials, so
   netherite deco is age_4, aluminium items age_5, uranium age_7), mod-only tier words (Afrit 5, Marid 8, Archmage 6,
   Imperium 7, Supremium 8, fission 7, fusion 8, Draconic 8, Chaotic 9, Ad Astra Venus/Mercury/Glacio 8), ore families,
   the TFC tables and explicit rules (Create brass/blaze/package tiers, IE MV/HV, Mowzie drops by their spawn locks, the
   Iron Age dusts, the goal items). `gen_stage_locks.py` writes `kubejs/data/firmages/tags/item/age_items/<stage>.json`,
   `.../block/age_blocks/<stage>.json` and the stage-file blocks. Four hand-written `mod:` ids were wrong
   (`create_aeronautics`, `create_stock_bridge`, `create_applied_kinetics`, `mekanismmoremachine`); Mystical Agradditions moves
   from age_6 to its Doc 10 v3 slot age_8. Grid recipes of every namespace are locked until its Age, never before age_0
   (AFC follows the TFC wood recipes). New ore overrides for MekaTFC osmium, TFC-IE galena/bauxite/uraninite, Firmalife
   chromite and TFCreate quartz; the item form of every ore block follows the block.
10. **Occultism miner (commit cf0b3b2, `recipes/age_3/arcane_age.js`).** All ore, raw-ore, gem and foreign outputs of the
   Dimensional Mineshaft are removed. Every miner (`occultism:miners/ores`) gives rich TFC ore pieces of the 11 Arcane Age
   metal ores, weighted like Occultism's own ores (iron 750 split over 3 ores, copper 584 over 3, tin 602, silver 381,
   gold 311, zinc 186, bismuth 186). End stone (age_6) leaves the basic resources.
11. **Self-test timing.** `/fa_selftest` prints the time of each PS call and of each reconcile (M0).
12. **firmages-core 0.2.1, late pass (M2).** Recipes added after `RecipeManager#apply` (Create Dragons Plus, 175) were
   not gated; `ReloadableServerResourcesLateMixin` gates them at the TAIL of `updateRegistryTags` and drops IE/Occultism
   recipes whose resolved tag result is locked. GameTest `LateRecipeInjector` covers it (12/12 GameTests pass).
13. **Precision mechanism and locked byproducts.** TFCreate's only precision mechanism recipe has the disabled
   `create:crushed_raw_gold` as scrap, so the gate dropped it and the Steel Heart had no recipe.
   `recipes/byproducts.js` strips locked chance byproducts until their Age (5 recipes), checked by G-3.
14. **Lead dust Age.** `mekanism:dust_lead` age_6 -> age_4, so the Industrial Age IE crusher recipes of galena and
   native silver load with the Industrial Age.
15. **Twilight portal.** Activator `#c:gems/diamond` (age_4) -> `tfcreate:polished_quartz` (age_2); quest text and
   `validate_quests.py` (portal tag Age per dimension task) updated.
16. **Tooling.** `poc_analyze.py`: G-1 to G-4 and R- checks, the G-2 mirror reads IE secondary keys; `dump.js`:
   `/fa_m3`, the unlocked Ages in `recipes.json`, explicit `getResultItem(HolderLookup$Provider)` overload (Ars caster
   tomes made 50 results unreadable at age_3 and age_4).

## Open points

- **Client half of M0.** EMI/JEI rebuild after a reload and the stall with players connected (recipe and tag sync) need a
  client; the reload-vs-hot-swap verdict above is provisional until then.
- **MekaTFC 0.1.0 recipes:** resolved in "Content: Arcane and Industrial Age" (mod kept, grade recipes rebuilt).
- **Chromite Age derived, not in the canon.** Firmalife chromite is age_4 because its only use, stainless steel, needs
  nickel (age_4); Doc 10 v3 section 8.1 has no chromium row.
- **Mowzie's Mobs boss drops:** resolved (Frostmaw age_4, Sculptor age_5).
- **Fluid tags `age_fluids/<stage>`** (SPEC section 2.1) are not generated yet; the registry snapshot has no fluids yet
  (the dump has them).
- **Tag `age_items/disabled`** sits next to dawn..age_9 so that every item PS locks has exactly one Age tag (SPEC coverage
  check); firmages-core can ignore it or treat it as "never".
- **Occultism basic resources** still give vanilla stone, andesite, deepslate, netherrack and similar (not ores, left as is).
- **KubeJS datapack** does not appear in `datapack list`; checklist item A13 should test the effect instead.
- **The Origin** (`firmages:origin`) does not exist yet.
- **firmages-core on the pack, not yet covered:** real PS events from a real player (team UUID from `getTeamId()` equal to
  the FTB team id, one event per team member) and the one-team guard with two FTB parties need a client; the FakePlayer
  runs gave no one-team warning. Every boot where a pack script calls `FirmAges.lockedOreBlocks()` will cost one extra
  reload (about 6 s) after start.
- **`firmages-server.toml` is not shipped in `defaultconfigs/`:** each server creates it with the mod defaults
  (`bootFallbackStages = ["dawn"]`, `allowSimulate = false`), which fits the pack; ship it only if a pack value differs.
- **Arcane and Industrial Age, needs a client:** Ars creature spawns in TFC biomes (Starbuncle, Whirlisprig and Drygmy
  shards come only from wild creatures); the Wilden summon ritual with the age_3 spawn lock on the Wilden; Theurgy rods
  refusing disguised ores; butcher-knife tallow from TFC animals; how vanilla farmland from Arcane Soil behaves (it dries
  to vanilla dirt); the 159 rewritten magic recipes in EMI; the IE multiblocks with the rebuilt bricks.
- **Theurgy spagyrics take only rich pieces.** Liquefaction reads one item without a count, so 2.5x of a 10 to 35 mB
  piece cannot be a whole sulfur; rich pieces (the Mineshaft's only output) give one sulfur = one dust (about 2.9x),
  poor, normal and small pieces have no spagyric path. Bismuth has no Theurgy sulfur.
- **IE Crusher output shape.** The crusher takes one item and its main output has no chance, so the main output is the
  TFC mineral powder in quern amount and the canonical dust comes as a chance output (exactly 2x on average).
- **Alchemy depends on Spirits.** Sal ammoniac crystals (Theurgy solvent) come only from Occultism miners; the Theurgy
  ore does not generate in TFC. The strands are not fully parallel; a pack source of sal ammoniac would decouple them.
- **Mekanism raw-ore recipes on TFC pieces.** TFC puts its ore pieces into `c:raw_materials/<metal>`, so Mekanism,
  More Mekanism Processing and Mekanism: More Machine apply vanilla raw-ore ratios to every grade (for example 3 small
  pieces -> 2,000 mB slurry). Done for osmium; the other metals belong to the Information Age content (the
  `oreLadder` helper in `recipes/age_6/mekatfc_osmium.js` is written for them).
- **Magic recipes outside the Arcane chain.** 272 of 2,447 magic recipes still have an input the closure cannot reach:
  mostly later tiers (Afrit, Marid, stabilizer tier 5), vanilla-only items (totem of undying, echo shards, specific
  vanilla flowers) and Occultism entity sacrifice tags that the item-level model reads as items. The Arcane chain
  itself closes (C-3b, C-3d).
- **Cataclysm boss-weapon fusion** (for example Infernal Forge + Astrape -> Brontes) keeps its chain; the gear check
  excepts it.
- **Create Big Cannons casting** still runs on CBC molten metals (Doc 10 v3 section 7.3 wants TFC fluids); CBC's own
  alloying, steel and cast iron are removed.
- **IE basic conveyor stays**: it is a block of the Metal Press, Assembler and Auto Workbench multiblocks, although the
  disable list names IE conveyors.
- **TFC + IE sheets of IE metals** (aluminium, lead, constantan, electrum, uranium) stay next to the canonical IE
  plates; they only feed TFC-style metal blocks.
- **Magic tail for later Ages** is stubs only (Attuned Circuit, Awakened Keystone items); the Afrit book on an IE HV
  coil, Archmage and Reformation gates and the fallback tokens (Wild Sigil, Forge Sigil) are not built.
- **MekaTFC second routes left alone:** redstone mixture (enriching and barrel, 16 redstone) and pyrite -> sulfur in a
  barrel are canon losers (redstone only from the quern, sulfur from TFC sulfur) but outside the osmium task.
- **Reload time** rose to 8 to 9 s (KubeJS recipe phase 3.6 to 3.7 s); still below the 10 s limit, little headroom.
- **Pre-existing parse errors:** 35 `Parsing error` lines in `latest.log` (TFC Regrowing Forests, WoodenCog, Create
  Deco), none from pack scripts.
- **Initial-load-only recipes** (cause found in "Content: Electric to Singularity": TFC stack sizes; fixed). The boot load has 29 recipes more than every later reload (34,939 vs 34,910 at the
  gate's main pass, while KubeJS reports the same added/removed counts on both). 4 of them pass the gate at Dawn: the Twilight Forest giant block
  to vanilla block recipes (`twilightforest:giant_log_to_oak_log` and three more), which vanish after the first Age
  reload. Not a leak (dawn outputs), cause not found.
- **Gate time on the initial load** is 534 to 575 ms (target 500 ms; every reload stays at 250 to 412 ms).
- **Reload stall** 7.1 to 8.8 s in the last run (6.3 to 7.4 s in the first); little headroom below 10 s.
- **Mekanism sulfur dust stays age_6** (decision log); the pack's TFC sulfur powder -> sulfur dust milling recipe opens
  only in the Information Age.
- **G-2 cannot resolve Mekanism-family tag outputs** (Evolved Mekanism solidifying); it lists them instead.
- **M2/M3, needs client:** EMI shows each Age's recipes after the unlock reload (C19); the Twilight portal built from a
  TFC grass ring, TFC flowers and polished quartz; Occultism Mineshaft and Ars apparatus cache flushes with a running
  machine.
- Everything marked needs client: C1, C3, C8, C9, C11 (Dawn EMI view), C14, C17, C19, C20, C21 in `poc-checklist.md`.

## Shrine M4 and ceremony

Date: 2026-10-01, branch `dev`, firmages-core **0.3.0** (shrine and Age ceremony, merged from the shrine branch). Test
server synced with `packwiz-installer-bootstrap` from `packwiz serve` (quest folder deleted and re-synced
byte-identical), **fresh world** (`--wipe-world`), then one restart on the same world. `debug.allowSimulate = true`
and the whitelist off only during the run (the whitelist kicks every non-listed player when an op changes); both are
back to `false`/on.

**Headless players.** No client can run here, so firmages-core got `/firmages debug player join|leave <name>`,
`debug use <name> <pos> [sneak]`, `debug pray <name> <pos> <seconds>` and `debug run <name> <command>` (all behind
`debug.allowSimulate`, `command/DebugPlayerCommands`). A debug player is a real `ServerPlayer` on an in-memory
connection placed with `PlayerList.placeNewPlayer`, like vanilla's GameTest mock player, with a payload setup that
accepts every mod channel. So FTB Teams, FTB Quests and ProgressiveStages see a normal login, and the shrine runs its
real code: `use` and `pray` go through `ServerPlayerGameMode.useItemOn` and the `RightClickBlock` event. The player logs
every firmages payload, title and chat line it receives as `[debug-player <name>]`. Players Alpha, Beta and Gamma, one
FTB party "Firmament" (created with `debug run ... ftbteams party create/invite/join`).

### Boot (fresh world)

| Check | Observed | Result |
|---|---|---|
| KubeJS | startup 2/2, server 20/20 scripts, 0 errors, 0 warnings; `on_stage_added: Age titles by firmages-core ceremony`; `arcane TFC inputs: 162 magic recipes rewritten` | pass (after fixes 1 and 2 below; the first boot had 1 error and 1 warning) |
| ProgressiveStages | 0 ERROR lines | pass |
| FTB Quests | `Loaded 2 chapter groups, 6 chapters, 119 quests, 7 reward tables`, 0 ERROR lines | pass |
| firmages-core | 0 ERROR lines; `Shrine data: tiers [0, 1, 2], fallback true, rings up to 2, 9 offerings, blessings [firmages:hearthward]`; `Boot: AgeState [dawn] ...; no reload` | pass |
| Other errors | loot tables of dndecor, railways, create_connected, createcasing, more_immersive_wires, extendedae; Sable `copycat_catwalk`; WoodenCog recipe parse errors; Polymorph EMI module; TF `dev_new_world`; DISTXFORM lines | not ours |

### The Stone Age shrine, step by step

Heart at -1408 221 -800 on a stone platform. The ring was set with `/setblock` from pack materials:
`tfc:rock/cobble/granite`, `tfc:wood/log/oak`, `tfc:thatch`, `firmages:offering_plinth`.

| Step | Observed | Result |
|---|---|---|
| `stage grant Alpha age_0` | Alpha, Beta and Gamma all have `age_0` (one party). One SHORT `firmages:age_transition` payload per player, PS chat line, no title packet (the KubeJS title is off). One reload, 10,685 ms | pass |
| Ring 0 built | `shrine status`: `Ring 0 ... complete, rotation CLOCKWISE_90`; `execute if block ... shrine_heart[ready=true]` passes; phase READY, rite "The heart is cold" | pass |
| Broken ring (one post block removed) | offering refused and prayer refused: "Caelum does not dwell in ruins: the Hearth Circle is not complete (21 of 22 blocks)", with the `shrine_preview` payload for ring 0; `ready` false; after the repair `ready` is true again | pass |
| Wrong item (stick) on the plinth | "Caelum asks for (Hearthstone) on this plinth.", item kept | pass |
| Hearthstone from Beta | "(Hearthstone) rests on the plinth...", Beta's hand empty (one item taken) | pass |
| Second Hearthstone from Alpha | "This plinth already holds (Hearthstone).", Alpha keeps it | pass |
| Prayer on a cold heart | actionbar "The heart is cold. Kindle it with a firestarter." | pass |
| `tfc:firestarter` on the heart | "The heart is kindled. Caelum watches.", `lit=true`, phase RITE_DONE | pass |
| Gamma leaves; Alpha and Beta pray | actionbar "2 pray to Caelum", heard after about 6 s (200 ticks alone, halved for 2): `Shrine prayer heard (tier 0, by [Beta, Alpha]): granting age_1` | pass |
| Ceremony | one FULL payload per online player (stage age_1, tier 0, heart position, beam and sky colours); `Ceremony FULL for age_1`; PS chat line; actionbar "Caelum is answering." | pass |
| One reload inside the ceremony | `Age reload starting (granted age_1)` 3 s after the payload (t60), `finished in 8671 ms`, so it ends at about 12 s, the end of the 240-tick timeline. Exactly one reload; `firmages ages` shows `dawn, age_0, age_1` | pass |
| Team grant | `stage check` age_1: Alpha and Beta yes; Gamma (offline during the prayer) has it on rejoin, from the team storage (no shrine catch-up line) | pass |
| Relic | `shrine relics`: `tier 0: firmages:hearthstone on -1408, 221, -802`; plinth BE `item: hearthstone, relic: 1b`, blockstate `awakened=true`; `shrine grants [age_1]`; Hearthward radius 12 active | pass |
| Goal quest | at the grant, the goal's gamestage task `6494AD8893357940` completes. The goal `5298B856BFEE50A2` stays started while keystones are open. Keystones forced with `ftbquests change_progress ... complete` do not re-check their dependants. After all 4 keystones were done, the goal was reset and the player relogged: the stage task completed again from `age_1`, and so did the goal | pass (see note) |
| Broken after awakening | a post removed: "The shrine is broken. Caelum's blessings rest until it is repaired.", sanctuary inactive, `age_1` kept; repaired: "The shrine stands again. Caelum's blessings return." | pass |

Item names appear as `item.firmages.hearthstone` in the server log only: the dedicated server has no KubeJS client lang;
the client renders the translatable name.

Note on the goal quest: in normal play a keystone completes through its tasks. FTB Quests' `Quest.onCompleted` then
calls `checkForDependantCompletion` (javap, 2101.1.36), which completes a flexible-mode dependant whose tasks are
already done. So offering before the strands are done is fine. Only the admin command `change_progress complete` skips
that check.

### Restart (same world)

`Boot Age snapshot [dawn, age_0, age_1] from MIRROR`, `matches the boot snapshot ...; no reload`; no
`age_transition` payload at login; `shrine status`: heart, ring 0 complete, `awakened 1, relics 1, shrine grants
[age_1]`, sanctuary active; the plinth BE still holds the Hearthstone as relic; all three players have `age_1`; the quest
data kept the completed task. `ready=false` is correct now: tier 1 needs the Bronze Sanctum, which is not built.

### Fixes from this run

1. **`on_stage_added.js`:** Rhino threw "redeclaration of var m" on a `const` in the version check. The script fell
   back to KubeJS titles, so every Age would have had two titles. It now uses `let`.
2. **`arcane_tfc_inputs.js`:** the same Rhino error at `const j` in the GEAR_FIX branch aborted the whole recipes
   event, so none of the 162 magic rewrites (diamond substitutes, the gear rule) applied. It now uses `let`.
3. **`grants.js`:** `PlayerEvents.advancement` got a null advancement for join-time advancements and logged one error
   per join. It now has a guard.
4. **firmages-core `shrine status`:** the header showed the previous validation's `intact`. It now validates first.
5. **firmages-core prayer refusal:** a held prayer on a broken ring sent the ghost preview 4 times a second. The
   preview now goes out with the throttled message, once a second (checked: 4 previews in 4 s).
6. **Offerings:** `kubejs/data/firmages/shrine/offerings.json` was never read by the mod, and its format would have
   been rejected at the mod's path. It is removed; `validate_quests.py` reads the mod's
   `data/firmages/firmages_shrine/offerings.json` (merge commit).

### Open points of this run

- **Reload time is now over the 10 s limit of SPEC §3.** age_0 took 10.7 s with three players online, age_1 8.7 s.
  The all-Ages dump run (no players, `simulate grant age_0`..`age_9`) took 11.0 to 19.3 s per reload ("Age reload ...
  finished in"). The KubeJS recipe phase alone took 4.6 to 6.3 s (20.7 s on the first one), against 2.65 to 3.56 s in
  the 0.2.1 run. Likely cause: the 162 magic recipe rewrites of `arcane_tfc_inputs.js` (remove plus `event.custom` per
  recipe on every reload), which never ran before fix 2. Not measured with spark; still below the 30 s client timeout.
- `dev/data/registry.json` was refreshed from an all-Ages dump (+2 items: shrine heart and plinth, `age_0`).
  `gen_stage_locks.py --registry` now refuses a dump taken before age_9. A Dawn dump had silently dropped 53 grid-recipe
  namespace locks, because the gate removes their recipes.
- The client half (beam, sky tint, title, voice line, sounds, plinth renderer, ghost alignment, EMI update) is Felix's
  dev-client test (`dev/dev-client.md` section 7 and "Age-Übergang testen").
- Rings 1 and 2 (TFC bricks, bronze blocks, bell, smooth stone, lamps) were not built in the world.
- From the console, FTB Teams' team argument accepts neither the display name nor the UUID (`Team ... not found`,
  `unexpected error`); `party join @a[name=<owner>]` works for an op player.

## Reload performance

Date: 2026-10-01, branch `dev`, test server with the world of the shrine run (not pregenerated: 4 region files; the
pregenerated M0 world was wiped by the shrine run), `debug.allowSimulate` and the whitelist off only during the run.
Reloads with `firmages reload` (the same filtered path as an Age grant); the stall is the mod's `Age reload ...
finished in` value (it wraps `reloadResources`, which blocks the server thread). Profiles: `spark profiler start
--thread Server thread` around 3 reloads, `stop --save-to-file` (local files, not uploaded), read with a local
.sparkprofile reader; per-handler times from temporary `Date.now()` logs in the test-server copy of the scripts.
Two headless players (`firmages debug player join Alpha|Beta`).

### Stall per reload (s)

| Condition | Before (b55015e) | After |
|---|---|---|
| age_4, no player (profiler on) | 7.08, 6.91, 6.63 | not measured |
| age_4, 2 players (no profiler) | not measured | 6.49, 6.57, 6.55 |
| age_9, no player (profiler on) | 6.67, 6.80, 6.63 | not measured |
| age_9, 2 players (profiler on) | 8.50, 8.09, 8.16 | 6.93, 6.86, 6.73 |
| age_9, 2 players (no profiler) | not measured | 8.02 (first reload after boot), 7.22, 6.78 |
| `stage grant Alpha age_5` (SHORT ceremony), 2 players | not measured | 6.60 |

KubeJS recipe phase ("taking ... in total"): 2.6 to 2.9 s before, 1.77 to 1.96 s after; "Posted recipe events" 1.38 s
before, 0.40 s after. Recipe gate: 232 to 290 ms before, 237 to 273 ms after (the gate is unchanged). The boot load
had a KubeJS phase of 5.3 s before (cold JIT).

### Where a reload goes (age_9, 2 players, per reload, server thread)

| Part | Before | After | Note |
|---|---|---|---|
| KubeJS recipe event | 2.47 | 1.61 | script handlers 1.32 -> 0.53; parsing the 35,387 original recipes (`discoverRecipes`) about 1.0, fixed |
| ProgressiveStages reload listener | 1.52 | 0.92 | player sync 1.17 -> 0.59 (duplicate lock sync, below); stage files and editor catalog 0.33 |
| Waiting for the reload workers | 1.71 | 1.69 | vanilla preparation on 15 worker threads: tag loading, recipe and loot JSON reads |
| `PlayerList.reloadResources` | 0.57 | 0.57 | EMI Loot sends the loot tables to every player (0.34) |
| Almost Unified | 0.48 | 0.39 | |
| Recipe decoding (vanilla codecs) | 0.42 | about 0.4 | |
| `updateRegistryTags` | 0.37 | 0.38 | |
| firmages-core recipe gate | 0.24 | 0.24 | |
| Advancements | 0.22 | 0.19 | |

Not a cost: `debug/dump.js` only registers commands (loads in 1 ms, runs nothing on reload), `byproducts.js` takes
0 to 1 ms, FTB Quests stays under 0.15 s, the tag scripts run inside the worker phase. The light-engine polling of
the M0 profile does not appear on this world; the server thread waits in `waitForTasks` instead.

### KubeJS filter costs (per call, one reload, 35,387 recipes)

| Filter | ms |
|---|---|
| plain id `{ id: 'a:b' }` (KubeJS looks it up in a map; also an array of plain ids) | 0 |
| `{ type: ... }` | 1.5 |
| `{ mod: ... }` | 3.4 |
| regex id | 13.5 to 17 |
| output item or output regex | 10.6 to 20 |
| input tag | 101 |

### Fixes

1. `arcane_tfc_inputs.js`: one global regex pass per magic recipe instead of 80 `split`/`join` passes (431 -> 58 ms).
   Same 162 rewrites; checked offline with node against all 35,081 dumped recipe texts, 0 differences.
2. `industrial_age.js`, `global_removals.js`, `arcane_age.js`, `bronze_age.js`: regexes that list fixed ids became id
   lists; open-ended id and output patterns are collected and removed in one pass per handler; the Occultism miner
   patterns are tested on the miner ids of one type pass; the Create andesite tier is resolved to ids once for its
   three `replaceInput` calls (industrial 338 -> 81 ms, global removals 247 -> 100 ms, Arcane 132 -> 17 ms, Bronze
   210 -> 122 ms; its remaining cost is the first output filter of the event). The all-Ages `fa_dump_full` is
   identical before and after apart from two randomised components (Occultism spirit names, an Ars tome colour);
   Added/removed/modified stay 563/974/43.
3. firmages-core 0.3.2: ProgressiveStages `syncPlayer` sent the lock sync twice per player (once itself, once inside
   `sendStageSync`), 0.27 s each. Two fail-soft mixins skip the second one only inside the same `syncPlayer` call
   (`compat/progressivestages/LockSyncDedupe`); the order of the packets stays. The reload log line counts the
   skipped syncs (2 per reload with 2 players). JUnit and 13 GameTests pass.
4. `industrial_age.js` passed a regex as a recipe `type` filter (KubeJS boot warning `Could not create ID from
   '/^createaddition:/'`); it removes the three C&A types by id now. `dev/run_server.py` writes UTF-8.

**No hot swap.** After the fixes every reload stays below the 10 s limit of SPEC section 3 (6.5 to 7.2 s with 2
players, 8.0 s for the first reload after a boot), so the phase-2 hot swap was not built.

### Arcane gear rule on the real dump

`poc_analyze.py` on the all-Ages dump after the fixes: all checks PASS, among them C-3g and C-4j. The three Ars
apparatus recipes load (they are in `fa_dump_full`, encoded by their serializer) and are reachable at age_3:
`enchanters_fishing_rod` takes `tfc:metal/rod/steel` as reagent and 5 pedestals (2 gold blocks, 2 source blocks,
string); `spell_bow` takes `#c:logs/archwood` and 5 pedestals (source block, gold block, manipulation essence, 2
string); `spell_crossbow` takes `#c:logs/archwood` and 6 pedestals (gold block, manipulation essence, source block, 2
string, steel rod). All three have `keepNbtOfReagent: false`. 0 KubeJS errors, no recipe errors for these ids.

### Ceremony

The FULL ceremony runs 240 client ticks (12 s) and the reload starts at t60, so it covers a reload stall of up to 9 s;
the measured 6.5 to 7.2 s end at about t190 to t205. An Age grant has a second, earlier stall: the KubeJS stage hook
(`stages/grants.js`) grants and revokes the mob and helper stages, and every one of those makes ProgressiveStages
resync each team member with a full lock sync. With 2 players that freezes the grant tick for 2.6 s ("Can't keep up!
Running 2630ms"), between the ceremony payload and t1. Wall clock after the payload: 2.6 + 3 + 6.6 = 12.2 s, so the
reload ends just after the FULL timeline; "The world realigns..." covers the rest.

### Open points

- **Grant-time stall.** About 0.3 s per stage change and team member (2.6 s with 2 players at age_5). It grows with the
  team: with 4 players the grant tick and the reload together will run past the 12 s ceremony. A root fix is one
  lock sync per player per tick (coalesce the PS `sendLockSync` calls to the end of the tick). That changes the order
  of the PS lock and stage packets on the client, so it needs a client test first; not done.
- **Reload with 4 players** was not measured; PS sync and EMI Loot scale at about 0.47 s per player after the fix.
- **firmages-core version.** The shrine worktree (`worktree-wf_c2b18449-288-2`) also bumped firmages-core to 0.3.1
  (rings 3..8). This branch serves 0.3.2 with the PS mixins only; the merge needs one combined build. Resolved: 0.3.3
  ("Shrine ladder and reload v2").

## Shrine ladder and reload v2

Date: 2026-10-01, branch `dev` after the merge of the shrine ladder branch (rings 3..8, Humming Core to Quantum Core)
with the reload fixes. firmages-core **0.3.3** = 0.3.1 (ring and blessing data) + 0.3.2 (PS lock-sync mixins):
`gradlew build` 57 JUnit tests pass (`CoreSuiteTest` 18, `GateSuiteTest` 16, `JsonOutputWalkerFixtureTest` 15,
`ShrineRingFilesTest` 1, `ShrineRulesTest` 7), `runGameTestServer` "All 14 required tests passed", selftest core
17/0 and gate 16/0. Shipped in `mods/` (0.3.2 removed), `packwiz refresh`. Test server synced with
`packwiz-installer-bootstrap` from `packwiz serve` (quest folder deleted and re-synced; `kubejs/` and the quests are
byte-identical with the repo), **fresh world** (`--wipe-world`). Two headless players Alpha and Beta in one FTB party.
`debug.allowSimulate` and the whitelist were off only during the run; both are back to `false`/on.

### Boot (fresh world)

| Check | Observed | Result |
|---|---|---|
| KubeJS | startup 2/2, server 25/25 scripts, 0 errors, 0 warnings; recipes "Added 572, removed 978, modified 43, 0 failed"; `arcane TFC inputs: 162 magic recipes rewritten`; `on_stage_added: Age titles by firmages-core ceremony` | pass |
| ProgressiveStages | 0 ERROR lines (2 WARN: FTB Library stage provider override) | pass |
| FTB Quests | `Loaded 2 chapter groups, 6 chapters, 119 quests, 7 reward tables`, 0 ERROR lines | pass |
| firmages-core | 0 ERROR lines; `Shrine data: tiers [5, 6, 7, 8, 0, 1, 2, 3, 4], fallback true, rings up to 8, 9 offerings, blessings [iron_will, starwalker, clarity, tireless_hands, attunement, skyreading, long_arm, hearthward, resolve]`; `Boot: AgeState [dawn] ...; no reload` | pass |
| Other errors | DISTXFORM, loot tables, Sable, Polymorph, TF `dev_new_world`, trainutilities advancements; recipe parse errors of tfcrf, woodencog and createdeco only | not ours |

### Building the rings

`dev/build_shrine.py` (new) reads the nine `shrine_ring_N.json` patterns from the mod resources and places one real
pack block per pattern character with `/setblock` over RCON (one member of each `firmages:shrine/*` tag, for example
`ars_nouveau:gilded_sourcestone_large_bricks`, `immersiveengineering:cokebrick`, `ae2:quartz_block`,
`ad_astra:steel_plating`, `mekanism:sps_casing`, `draconicevolution:awakened_draconium_block`). With `--rites N` it
also sets tier N's rite: the candles get `lit=true`; the IE electric lanterns and floodlights get stored energy in
their block entity NBT, so IE's own tick switches them to `active=true` (wiring a generator by command is not
possible); each floodlight gets a redstone block on top. Heart at 0 240 0 on a stone platform; all nine rings at once,
550 commands, no unexpected reply. `shrine status`: `valid ring 8`, every ring `complete` (rotation CLOCKWISE_90).

Dawn and age_0 to age_3 were given with `stage grant` (four grants, one reload, 9,909 ms, the first reload after the
boot). From there every Age came from the shrine.

### The ladder age_3 to age_9

For each tier N one border block of ring N was removed first (`shrine status` showed ring N with one block missing
and `valid ring N-1`). Then ring N was rebuilt with its rite, Alpha laid the offering on plinth N+1 with `debug use`,
and the prayer was held with `debug pray`. "Reload end" counts from the FULL ceremony payload; the ceremony timeline
is 12 s.

| Tier | Ring (blocks) | Rite: before / after | Offering | Prayer | Reload (ms) | Grant stall | Reload end |
|---|---|---|---|---|---|---|---|
| 3 | Spirit Circle (61) | "Light the eight candles" / done | Arcane Keystone accepted (an Ars worn notebook was refused: "Caelum asks for item.firmages.arcane_keystone on this plinth", the server log shows the lang key) | Alpha alone, granting age_4 | 6,624 | 2.68 s | 12.27 s |
| 4 | Foundry Nave (65) | "Power the four electric lanterns": prayer refused while unpowered / done after charging | Pressure Core accepted | Alpha alone, granting age_5 | 6,238 | none | 9.24 s |
| 5 | Tesla Crown (73) | "Light the four floodlights ... power and a redstone signal" / done | Humming Core: at age_4 ProgressiveStages refuses the locked item ("This item is locked!"); at age_5 accepted | Alpha alone, granting age_6 | 6,277 | 2.65 s | 11.88 s |
| 6 | Data Nave (77) | Chorus: "At least 2 of you must pray together" with Beta online / Alpha and Beta pray | Data Matrix accepted | by [Beta, Alpha], granting age_7 | 6,204 | none | 9.20 s |
| 7 | Star Spire (89) | Stars: refused right after `weather clear` and `time set 18000` (the rain level fades over a few seconds) / done | Star Chart accepted | by [Beta, Alpha], granting age_8 | 6,290 | none | 9.29 s |
| 8 | Quantum Ring (89) | Chorus and stars / both done | Quantum Core accepted | by [Beta, Alpha], granting age_9 | 6,150 | none | 9.15 s |

Every grant had exactly one `Age reload starting (granted age_N+1)`, 3.0 s after the payload, and one `Ceremony FULL
for age_N+1`; Beta holds every Age (one party). End state: `awakened 9, relics 6, shrine grants [age_4 .. age_9]`;
`shrine relics` lists the six offerings on plinths 4 to 9; "No current tier (highest Age age_9)". The prayer needs an
empty main hand: a held item (here the locked Humming Core) turns the use into an item use, so no prayer starts.

**Reload performance holds across all Ages:** 6.15 to 6.62 s per grant from age_4 to age_9 with 2 players (limit
10 s), and 6.20 and 6.22 s for the two operator reloads at age_9 after the station fix below (KubeJS phase 1.60 to
1.71 s, "modified 80 recipes"). The grant stall of the earlier run is still there at the grants that change mob or
helper stages (age_4 and age_6: 2.65 to 2.68 s); those two reloads end 12.27 s (age_4) and 11.88 s (age_6) after
the payload, around the end of the 12 s ceremony.

### Signature items reachable at their Age (`poc_analyze.py`, all-Ages dump)

New R-checks R-age_5 to R-age_8 cover the Humming Core (`firmages:arc_furnace/humming_core`), Data Matrix
(`firmages:crafting/data_matrix`), Star Chart (`firmages:fusion/star_chart`) and Quantum Core
(`firmages:fusion/quantum_core`). Each has a chain of the Age's stations: arc furnace parts and HV wiring; Metallurgic
Infuser, AE2 controller, Inscriber, Molecular Assembler and Pattern Provider; DE crafting core and injectors,
polonium, the tier 1 to 4 rockets, desh, ostrum and calorite plates, ice shards and the SPS casing. The walk now also
knows the boss tokens (Nether Star, dragon's breath, witherite block, abyssal egg) and the End as start items, the ore
pieces of `[ore_families]` from their Age, the Ad Astra planets (stone, sand, raw desh, ostrum and calorite, ice
shard) and End draconium dust. It reads Mekanism chemicals as ingredients and outputs (`item_output`,
`chemical_output`; fluids count as available). Every late recipe type needs its station item (Mekanism machines, AE2
inscriber and charger, Ad Astra workbench, compressor and refinery, the DE crafting core plus the injector of the
recipe's tech level, the arc furnace parts). Chemicals that a multiblock makes without a recipe need its parts
(fission reactor: nuclear waste; SPS: antimatter).

First run: R-age_5 PASS; R-age_6, R-age_7 and R-age_8 FAIL. The Metallurgic Infuser (every Mekanism circuit and
alloy) needs `minecraft:furnace`; the AE2 Molecular Assembler and Pattern Provider and the Ad Astra NASA Workbench
(every rocket) need `minecraft:crafting_table`; a TFC world makes neither. A scan found 40 such recipes outside the
magic mods (crafting table, furnace, blast furnace, smoker, campfire, anvil), not counting three that only transform
the vanilla block itself.

**Fix:** `kubejs/server_scripts/recipes/tfc_station_inputs.js` swaps those inputs for the TFC counterparts the Arcane
Age already uses (`#tfc:workbenches`, `tfc:crucible`, `tfc:blast_furnace`, `tfc:firepit`, `#tfc:anvils`): 37
`replaceInput` calls by plain id, and a JSON rewrite for the 3 recipes of schema-less types (ExtendedAE crystal
assembler, DE fusion). The new check **R-0** fails on any recipe that still needs one of the six vanilla station
blocks. After a `firmages reload` and a new dump: **45 checks, 0 FAIL**, among them R-0, R-age_2 to R-age_8, G-1 to
G-4, C-3g and C-4j. `gen_stage_locks.py --registry` on the new dump rewrote 0 files (the hand-added signature item
ids match the registry).

### Open points of this run

- **Gear in tech recipes** (resolved in "Content: Electric to Singularity", `recipes/gear_rule.js`). About 30 recipes outside the magic mods still take finished vanilla tools or armour that
  a TFC world cannot make (Mekanism paxels and MekaSuit modules, Mystical Agriculture augments and gear, DE wyvern
  tools and modules, Apotheosis salvaging, SGJourney naquadah gear, `minecraft:netherite_*_smithing`). None is on a
  goal chain. Each needs a gear-rule decision (tool head, unfinished armour or sheet), so they are not changed.
- **Ad Astra stack sizes** (resolved in "Content: Electric to Singularity": TFC's late stack-size table). From the first reload on, 25 Ad Astra recipes (panels, plateblocks, factory and encased
  blocks of steel, desh, ostrum, calorite and iron) fail to parse: "Item stack with stack size of 64 was larger than
  maximum: 32". They are decorative blocks only; the cause (a stack-size limit in the pack) is not traced yet.
- **Rites with real power.** The lanterns and floodlights ran on stored energy written into their block entities; IE
  LV wiring to a generator was not built (it needs a player). The candle, chorus and star rites ran their real paths.
- **Grant stall** (2.65 to 2.68 s at age_4 and age_6 with 2 players), as in "Reload performance": still open; it needs
  the client test before the PS sync is coalesced.
- The client half (ceremony, ghost previews of rings 3..8, tooltips of the four new items) stays Felix's test.

## Content: Electric to Singularity

Date: 2026-10-01, branch `dev` (commits e437de7 to b5835dd), firmages-core 0.3.3 unchanged. Test server synced with
`packwiz-installer-bootstrap` from `packwiz serve` (`kubejs/` and the changed configs byte-identical with the repo), the
world of the shrine run at age_9 (no wipe). Iterations synced the scripts by copy; the final boot came from the packwiz
sync. `debug.allowSimulate` and the whitelist were off only during the partial-Age and player runs; both are back.

### Boot

| Check | Observed | Result |
|---|---|---|
| KubeJS | startup 2/2, server 29/29 scripts, 0 errors, 0 warnings; "Added 811, removed 2448, modified 290, 0 failed" | pass |
| Recipe parse errors of the pack | none; 0 "stack size larger than maximum" (was 29 per reload); left: tfcrf, woodencog, createdeco (pre-existing) | pass |
| Mods | Mystical Customization reads `configure-crops.json` without an unknown crop (after b5835dd); Cucumber loads `cucumber-tags.json` | pass |
| Other ERROR lines | DISTXFORM, loot tables, Sable, azimuth mixin: as before, none from the pack | not ours |
| One transient crash | one boot of four failed in Create's `RegisterEvent` (ExceptionInInitializerError, before any pack script); the retry booted with the same files | open point |

### What the pack now contains

| Age | Content | Where |
|---|---|---|
| all | **Station Ages:** every machine recipe waits for the Age of its machine (55 types, Doc 10 v3 matrix 6.1 per type; world data types never). Pack recipes in such machines check `FirmAges.isUnlocked` themselves, because a type filter does not see recipes added in the same event (0 found on the server) | `recipes/station_ages.js`, guards in `age_3`, `age_4`, `age_5`, `age_6`, `byproducts.js`, `arcane_tfc_inputs.js` |
| all | **Stack sizes:** 25 Ad Astra and 4 Twilight Forest recipes give one TFC stack (32, logs 16) by same-path datapack files. Root cause: TFC applies its size table after the datapack load, so only the first load of a boot accepted 64 | `kubejs/data/ad_astra/recipe/`, `kubejs/data/twilightforest/recipe/` |
| all | **Disable list** Doc 10 v3 §7.2 for the late mods: 516 items in `[hidden]` (tag `age_items/disabled`, gate drops their recipes, EMI hides them, no PS lock); `HIDDEN_ITEMS` 638 | `dev/age_map.toml`, `00_constants.js` |
| all | **Vanilla bucket** in 16 recipe ids and the Steam 'n' Rails boilers → TFC wooden bucket (no vanilla bucket recipe in a TFC world) | `recipes/tfc_station_inputs.js` |
| 5 Electric | Arc furnace: weak steel from steel (TFC + IE took black steel), black steel from weak steel + pig iron, weak red steel 5:2:1:1. Diesel Generators: engines and upgrades only (oil chunks off). IP gas generator off. Afrit book with an IE HV coil block. Excavator: TFC + IE mixes (IE's own mixes keep `minecraft:is_overworld`, which no TFC biome carries, so they do not generate) | `recipes/age_5/electric_age.js`, `config/createdieselgenerators-server.toml` |
| 6 Information | Ore ladder: 192 `firmages:{purifying,injecting,dissolution}/<ore>_<grade>` for 16 families (4x from age_7, 5x from age_8); every Mekanism-family recipe on TFC ore blocks, raw ores or raw blocks, ingot crushing, stone crushing, combiner ores, the More Machine silver chain, the Mek steel and bronze routes removed; fluorite from cryolite. Precision Sawmill, planting, recycling, Evolved molten metals, More Machine stamper/lathe/rolling mill/presser removed. AE2 Inscriber is the one processor station; presses by recipe. Red steel by the Metallurgic Infuser (blue from age_7). End portal frame recipe. Archmage book and Theurgy flux emitter with Mekanism parts. Mystical Agriculture crops, outputs and farmland canonical | `recipes/age_6/ore_ladder.js`, `information_age.js`, `config/mysticalcustomization/`, `config/cucumber-tags.json`, AU `materials.json` |
| 7 Space | Ad Astra compressing, alloying, refining and its planet-ore furnace recipes removed; desh, ostrum, calorite plates only from IE's Metal Press; IP kerosene in `#ad_astra:fuel` (diesel was already in); DE transfuser and More Machine large SNA without disabled inputs | `recipes/age_7/space_age.js`, `tags/late_ages.js` |
| 8 Quantum | Marid book with atomic alloy and Wyvern Core; Awakened Keystone by the Marid ritual (Arcane Keystone, 2 atomic alloy, Wyvern Core, Leviathan token); AdvancedAE reaction chamber without the Vibration Chamber | `recipes/age_8/quantum_age.js` |
| 9 Singularity | Ultimate Singularity (DE chaotic fusion, the nine relic ids, 2 chaos shards, singular alloy, 2 billion FE); naquadah vein in Mercury stone; naquadah through the Arc Furnace, the Infuser and the nucleosynthesizer; SGJ crystals on TFC gems; the Classic Stargate base block takes the Singularity; Coordinates of The Origin (address 9, 16, 31, 5, 21, 37, SPEC §14 item 5); Reactor Controller recipe when the block exists; DE config (fuel 0.5, explosion 0.25, dragon dust 16) | `recipes/age_9/singularity_age.js`, `kubejs/data/firmages/worldgen/`, `config/brandon3055/DraconicEvolution.cfg` |
| 5-9 | **Gear rule:** about 60 recipes direct or on TFC parts (list in the decision log); paxels, MA gear and augments, AdvancedAE Quantum Armor disabled; MekaSuit and Meka-Tool keep their chains (trophies) | `recipes/gear_rule.js` |

### Checks (`poc_analyze.py`)

All-Ages dump (33,223 recipes): **59 checks, 0 FAIL**. New: C-5a, C-6a to C-6d, C-7a, C-9a, C-G for age_5 to age_9,
R-age_9 twice. The R-walk now resolves tag outputs to the Almost Unified target, knows the dragon heart as a dragon drop,
raw naquadah on Mercury and the SGJ machines as stations.

| # | Goal | Reached at its Age | Result |
|---|---|---|---|
| R-0 | no recipe needs a vanilla station block or bucket without a source (7 blocks) | | pass (first run: 4 bucket users, fixed) |
| R-age_5 | Humming Core + 12 chain items | 14,401 items | pass |
| R-age_6 | Data Matrix + 9 | 15,843 | pass |
| R-age_7 | Star Chart + 20 (now with the fission fuel chain) | 16,292 | pass (first run: no vanilla bucket, so no Dynamic Tank, PRC, oxidizer, polonium) |
| R-age_8 | Quantum Core + 10 (Awakened Keystone, bound Marid book) | 16,687 | pass |
| R-age_9 | Ultimate Singularity + 8 (chaotic injector and core, singular alloy, reactor core, stabilizer, injector, flux gate) | 16,842 | pass |
| R-age_9 | Classic Stargate base block + 15 (naquadah ingot, alloy, refined and pure naquadah, liquidizer, crystallizer, crystals, ring, chevron, DHD, coordinates) | 16,842 | pass (first runs: SGJ crystals named `minecraft:diamond`, naquadah buckets needed a vanilla bucket) |
| C-G | gear rule age_5..age_9 | | pass (first run: MA soulium daggers) |
| C-6c / C-6d / C-7a / C-9a | | | pass (first run: More Machine presser type `mekmm:pressing`, the awakened draconium crop key, planet ore smelting, a too wide regex in the check) |

Partial Ages with the all-Ages dump as baseline (`--baseline`): at **age_5** and at **age_2** G-1 to G-4 pass. G-2 now
counts what the station Ages keep out (age_5: 1,554 of age_6, 16 / 20 / 2 of age_7 to age_9; age_2: 374 of age_3, 222
of age_4, 160 of age_5, 1,197 of age_6). The world went back to age_9 afterwards.

Naquadah on Mercury: `place feature firmages:ore_naquadah_mercury` places ore in Mercury stone; in four fresh Mercury
chunks (forceloaded at 4000/4000) two 16x16 columns held 45 and 49 naquadah ore from y -48 to 40, one of them 30 more
from y 41 to 96.

### Reload performance (age_9)

| Condition | Before (0.3.3 run) | After |
|---|---|---|
| no player | 6.20, 6.22 | 8.90 (first after boot), 7.69, 7.45 |
| 2 headless players | 6.15 to 6.62 | 8.24, 8.25, 8.33 (9.40 to 9.60 while the 516 late items were in the PS disabled stage) |

KubeJS recipe phase 2.6 to 2.9 s (was 1.6 to 1.7; "Posted recipe events" 1.0 s, was 0.4). The scripts of this run cost
about 0.5 s (information 166 ms, ore ladder 143, electric 75, space 38, gear 29) plus the larger add/remove sets.
Still below the 10 s limit of SPEC §3; with the 3 s grant stall the FULL ceremony (12 s) now ends about as the reload
does.

### Open points of this run

- **Reload headroom** is down to about 1.7 s with 2 players. Next cuts: the per-recipe JSON passes in `ore_ladder.js`,
  `information_age.js` (three `replaceOutput` mod scans), and `global_removals.js`/`bronze_age.js` (pre-existing).
- **Quest text** for the late Ages (address of The Origin, the Awakened Keystone, the naquadah route) is not written;
  `dev/gen_quests.py` belongs to the quest workflow. The address item and its recipe exist.
- **Keystone lending** (SPEC §7.4) and the **Gathering** (§7.5) are mod milestones; until then the Marid takes a second
  Arcane Keystone, and the relics leave their plinths through `/firmages shrine extract` (the age_9 sneak-use release
  of SPEC §7.5 was not tested).
- **Boss fallbacks** (Gateways "Chaos Trial", sigils) are not built for any Age; the Chaos Guardian's soft-lock bugs
  stay a risk. The dragon heart in the R-walk assumes DE's default dragon loot (needs a kill).
- **Needs a client:** the Ad Astra rocket burning IP diesel and kerosene, the crafted End portal frame lighting with
  vanilla eyes, SGJ crystallizers fed with liquid naquadah through pipes, the Afrit/Marid book pages in the Occultism
  guide, EMI per Age with the station gating.
- **Not covered by recipes:** Mekanism and Evolved Mekanism radiation, fusion and reactor balance (Doc 10 v3 §7.4 values
  set only for DE); `immersiveengineering:mineral_mix` vanilla mixes are dead data (no TFC biome), not removed.
- **Create pressing** of ad hoc compat plates for lead, constantan, aluminium and uranium is gone (one ingot → plate was a
  second plate station); TFC + IE sheets stay.
- **Transient Create crash** on one boot (see Boot); not reproduced.

## Late Ages, Origin and finale

Date: 2026-10-01, branch `dev` after the integration of the mod branch (reactor controller, The Origin, end-boss
scaffolding) and the quest branch (chapters Arcane to Singularity), commits 0883c9b to 5f318f3. firmages-core
**0.4.0**: `gradlew build` 63 JUnit tests pass, `runGameTestServer` "All 19 required tests passed" (also after the
fixes below). The 0.4.0 jar replaces 0.3.3 in `mods/`, `packwiz refresh`. Test server synced with
`packwiz-installer-bootstrap` from `packwiz serve` (quest folder deleted first; `kubejs/` and the quests are
byte-identical with the repo, jar md5 equal), **fresh world** (`--wipe-world`). Two headless players Alpha and Beta in
one FTB party, both survival. `debug.allowSimulate` and the whitelist were off only during the run; both are back.
A first fresh-world run found the problems fixed below; the numbers here come from the final run on the committed state.

### Merge decisions

- **One Origin address.** The two branches had picked different addresses in parallel: the mod 9-16-21-33-2-37 (its
  GameTest dials it), the content item tooltip and SPEC §14 9, 16, 31, 5, 21, 37. The mod's address stays; tooltip and
  SPEC follow it. The quest "Gate Online" and the side mission "Into The Origin" give it.
- **One reactor controller recipe** (`recipes/age_9/reactor_controller.js`); the second one with the same id in
  `singularity_age.js` is gone.
- Quests after the mod merge: "The Ultimate Singularity" is an item task (the KubeJS item exists), "Into The Origin" is a
  new side mission with a dimension task on `firmages:origin` (345 quests, 1281 object ids), Space supplies drop the
  disabled Ad Astra steel plate and Mekanism uranium ingot, `validate_quests.py` accepts firmages-core as the
  `finale_won` grant path (0 errors, 0 warnings).

### Boot (fresh world)

| Check | Observed | Result |
|---|---|---|
| KubeJS | startup 2/2, server 31/31 scripts, 0 errors, 0 warnings; recipes "Added 460, removed 7061, modified 290, 0 failed" in 3.72 s (4.88 s before the station-Age fix); 0 `[ERROR]` lines in `logs/kubejs/server.log` over the whole run | pass |
| ProgressiveStages | `progressivestages validate`: 28/28 stage files valid; 0 ERROR lines | pass |
| FTB Quests | `Loaded 2 chapter groups, 13 chapters, 345 quests, 21 reward tables` (= `dev/quests-notes.md`), 0 ERROR lines | pass |
| firmages-core | 0 ERROR lines; "Draconic reactor coupling enabled"; "The Origin: arena v1 built in 483 ms"; return stargate at 0 66 31, DHD 0 65 25, "in the network: true" | pass |
| Gateways | "Registered 13 gateways" (with `firmages:the_origin`) | pass |
| Other errors | tfcrf, woodencog, createdeco recipe parse errors, Sable `copycat_catwalk`, Polymorph EMI module, TF `dev_new_world`, DISTXFORM: as before | not ours |

### (1) The ladder age_0 to age_9

Rings 0 to 8 built at once by `build_shrine.py --heart 0 200 0 --rings 0-8 --platform` (550 commands); `dawn` and
`age_0` by `stage grant` (the First Spark quest path), then every Age from the shrine: offering by `debug use` on the
tier's plinth, the rite (tier 0 firestarter on the heart, tier 1 the bell, tiers 2 to 5 `build_shrine.py --rites N`,
tiers 6 and 8 two praying, tiers 7 and 8 a clear night sky), prayer by `debug pray`. Every grant: exactly one
`Age reload starting`, one FULL ceremony; `firmages ages` ends with all eleven stages.

| Grant | Path | Reload (ms), final run | First run (before the station-Age fix) |
|---|---|---|---|
| age_0 | quest reward (`stage grant`), first reload after the boot | 8,775 | 13,943 |
| age_1 | tier 0, Alpha | 7,054 | 9,322 |
| age_2 | tier 1, Alpha | 6,995 | 9,197 |
| age_3 | tier 2, Alpha | 6,837 | 8,393 |
| age_4 | tier 3, Alpha | 6,824 | 7,037 |
| age_5 | tier 4, Alpha | 6,551 | 6,818 |
| age_6 | tier 5, Alpha | 6,601 | 6,596 |
| age_7 | tier 6, Beta and Alpha (Chorus) | 6,775 | 6,831 |
| age_8 | tier 7, Alpha (stars) | 6,818 | 6,726 |
| age_9 | tier 8, Beta and Alpha (Chorus and stars) | 6,933 | 6,755 |

Two operator reloads at age_9 with both players online: 6,496 and 6,625 ms (the Electric-to-Singularity run had 8.2 to
8.3 s). The early Ages were the slow ones: `station_ages.js` ran a remove and a `forEachRecipe` pass per locked machine
type (117 types at Dawn, 234 scans of about 35k recipes); one OR-filter pass cut the KubeJS phase of the boot at Dawn
from 4.9 to 3.7 s. Note for testers: a held item (the Ars worn notebook every player gets at login) turns the prayer
into an item use, so the prayer never starts.

### (2) Reactor controller on a real DE reactor

Core at 30 202 0, four stabilizers at distance 5 placed by `/setblock` with their `bc_managed_data.facing` set towards
the core by `data merge`, one `debug use` on a stabilizer to form it (`structure_valid 1b`). Controller on top of the
west stabilizer, a chest with 16 awakened draconium blocks on top of the controller, redstone wire beside it.

| Step | Observed | Result |
|---|---|---|
| Cold empty reactor | READY within 3 s: fuel 10,368, 8 blocks taken from the chest, redstone 15 (wire `power=15`), comparator 15; DE state COLD | pass |
| Burnt-down reactor (fuel 2073.6, chaos 8294.4 by `data merge`) | SWAP: chaos out to the controller's slots (6 large, 3 medium, 5 small fragments; a rest of 6.4 stays), 6 blocks in, fuel 9,849.6, "waiting for fuel", redstone 0 | pass |
| 64 nuggets added to the chest | 32 nuggets pulled, fuel 10,361.6, READY, redstone 15; the reactor stays COLD (never started) | pass |

RUNNING to SHUTDOWN at 80 % conversion was not forced on the server: an uncharged reactor set to RUNNING by NBT would
fail its field and explode (scale 0.25). The GameTest `controllerCycleOnRealReactor` covers that path against the real
DE 3.1.4 core (shutdown accepted, DE's own STOPPING, COOLING, COLD, then the swap).

### (3) The Origin

| Check | Observed | Result |
|---|---|---|
| Below age_9 (Alpha at age_8, survival) | `/firmages origin tp Alpha`: "Alpha was not let into The Origin", Alpha stays in the overworld, chat "This dimension is locked!" (ProgressiveStages) | pass |
| At age_9 | `/firmages origin tp @a`: both players in `firmages:origin` at 0.5 65 14.5 | pass |
| Address | `sgjourney stargateNetwork address firmages:origin`: "-9-16-21-33-2-37-"; extragalactic "-1-30-9-16-21-33-2-" | pass |
| Arena and return gate | altar `firmages:origin_altar` at 0 65 0, `sgjourney:milky_way_stargate` at 0 66 31, `sgjourney:milky_way_dhd` at 0 65 25 | pass |
| Quest | the "Into The Origin" dimension task ticks on arrival | pass |

### (4) Gathering, end boss and finale

The boss script did not exist, so `kubejs/server_scripts/finale/the_primordial.js` and the gate
`kubejs/data/firmages/gateways/the_origin.json` were written (decision log). Both players moved next to the altar:

| Step | Observed | Result |
|---|---|---|
| Gathering | "The Origin: Gathering #1 of 2 player(s)", chat line to both, script: gate `firmages:the_origin` opened above the altar | pass |
| Waves | Maledictus (t+9 s), Ignis (t+20 s), Maledictus and Ignis (t+31 s), each killed by command; one gateway entity throughout | pass |
| The Primordial | gate completed, an Ender Guardian named "The Primordial" with the tag `firmages.final_boss`, health 1024 (the attribute cap), armour 20; title to both players | pass |
| Phases (on a tagged Ender Guardian, health set by NBT) | at 2/3: 2 Ender Golems and darkness on the players; at 1/3: 4 Endermapteras, lightning, resistance II on the boss; each phase once | pass |
| Kill | `kill @e[tag=firmages.final_boss]`: "final boss ... defeated, granting finale_won"; Alpha and Beta have `finale_won`; one FULL `age_transition` payload per player (stage finale_won, tier -1, at the altar); "Ceremony FINALE"; `origin status` "DEFEATED (finale_won)"; a later tagged death: "another firmages.final_boss died after the finale; nothing to do" | pass |
| Altar after the fight | present | pass |
| Quests | the Singularity goal's `finale_won` task (`55C92C85FCE9B94B`) is done and the Firmament chapter's last quest is completed; the goal quest itself completes once its three keystones are done (FTB Quests flexible mode, as in the Stone Age run) | pass |

Fixed on the way (first run):
- **Server crash** from the script: `gate.level()` is shadowed by KubeJS's `level` bean getter, and the uncaught
  TypeError inside `GateEvent$Completed` (fired from the entity tick) crashed the server. Every handler now catches and
  logs its errors; the script reads the level by `getCommandSenderWorld()`.
- **Boss tag not selectable**: `firmages:final_boss` cannot be written in a selector or in `/tag add` (unquoted word,
  no colon). The mod's tag is now `firmages.final_boss` (0.4.0 rebuilt before any push).
- **Altar destroyed** during the fight, so no later Gathering could start: the altar is in every Cataclysm `*_immune`
  tag and in `wither_immune`/`dragon_immune` now; it survived both later fights.
- **Phases never fired**: KubeJS `EntityEvents.afterHurt` did not fire on this server (not even for a zombie); the
  script listens to NeoForge's `LivingDamageEvent$Post`. The summons of a phase needed `execute in ... positioned`.

### (5) `poc_analyze.py` and (6) quests

All-Ages dump of the final world (33,224 recipes): **59 checks, 0 FAIL**, among them R-age_5 to R-age_8, R-age_9
Ultimate Singularity (8 chain items) and R-age_9 Classic Stargate base block (15), C-9a, C-G age_5 to age_9, G-1, G-3,
G-4. `gen_stage_locks.py --check` clean. Quest book: all 32 gamestage tasks of the 13 chapters (each entry quest and
goal, the Firmament goals, `finale_won`) are done in the team's progress file after the ladder and the finale;
`validate_quests.py` 0 errors, 0 warnings.

### Open points of this run

- **Fight length**: The Primordial lost about 8 health to a 500-damage `/damage` hit (Cataclysm's damage cap and
  armour). With 1024 health the real fight length with Chaotic gear needs a client test; the cap may need a lower
  health or a Cataclysm config value.
- **Needs a client**: the FINALE ceremony on screen, a real dial from a player-built Classic Stargate and the trip home
  from the Origin DHD, whether ProgressiveStages bounces a player without age_9 after SGJ wormhole transport, the ring
  quests (observation tasks), the controller with pipes on a reactor that really burns.
- **Not built** (mod branch): blocking the Mekanism teleporter and Ars warp into The Origin; `mob_9` spawns; the trophy
  monument. Boss fallback gateways of the earlier Ages are still open.
- A crashed server hung on shutdown and its watchdog wrote a second crash report two minutes later; seen once, after the
  script crash.

## Content fixes after the late Ages

Date: 2026-10-01, branch `dev`, commits b379cb4 to b0e2c49 on top of b2f2249; firmages-core 0.4.0 unchanged. Test
server: the world of the late-Ages run at age_9 (no wipe). Iterations synced `kubejs/` and the quests by copy; the
final boot came from `packwiz-installer-bootstrap` against `packwiz serve` (`kubejs/` and the quest folder identical
with the repo). `debug.allowSimulate` was on only during the run (back to false); the whitelist stayed on (the debug
players join through `placeNewPlayer`, which skips it). Both debug players were dead in this world (health 0, they
cannot respawn without a client); their player files were moved aside, so Alpha and Beta rejoined fresh in the same
FTB party. Recipe-set comparisons: `fa_dump_full` before and after each change, compared with creation dates, colours
and Occultism spirit names left out.

### Boot (final, packwiz sync)

| Check | Observed | Result |
|---|---|---|
| KubeJS | startup 2/2, server 32/32 scripts, 0 errors, 0 warnings; "Added 818, removed 2441, modified 333, 0 failed" in 3.28 s (3.54 s before); 0 error lines in `logs/kubejs/server.log` | pass |
| ProgressiveStages | `progressivestages validate`: 28/28 stage files valid | pass |
| FTB Quests | `Loaded 2 chapter groups, 13 chapters, 345 quests, 21 reward tables` | pass |
| Gateways | "Registered 13 gateways" | pass |
| Recipe parse errors | tfcrf 22, woodencog 12, createdeco 1 (as before); none of the pack | not ours |
| Other ERROR lines | DISTXFORM, azimuth and bits_n_bobs mixins, loot tables, Sable `copycat_catwalk`, Polymorph EMI module: as before | not ours |

### (1) Quests against the content

| Point | Finding | Change |
|---|---|---|
| Ultimate Singularity | `firmages:ultimate_singularity` is registered (`items.js`); the keystone was already an item task (not consumed, the Singularity goes into the Stargate base block) | none |
| End portal frame | recipe `firmages:crafting/end_portal_frame` exists (age_6, C-6d); the quest said nothing about it | "Your Own End Portal": 12 frames + 12 eyes of ender; "The End" explains the ring |
| Gate Online | Stargate Journey 0.6.49 gives `sgjourney:classic_stargate` only `facing`, `orientation`, `part`, `waterlogged` (javap), so no "dialling" or "powered" state exists | keeps the observation of the assembled gate; "Into The Origin" (dimension task) proves the dial; the text says how to assemble |
| Awakened Keystone | no stub any more: the Marid ritual `firmages:ritual/awakened_keystone` takes `firmages:arcane_keystone` | quest text: borrow the keystone from the Spirit Circle plinth (SPEC 7.4) or bind a second one with the Djinni, lay the Awakened Keystone back; SPEC 7.4 names the item ids |
| Generator and validator | `gen_quests.py`, `validate_quests.py` | 345 quests, 1282 object ids, 0 errors, 0 warnings |

### (2) End boss

| Check | Observed | Result |
|---|---|---|
| Gathering | Alpha and Beta at the altar: "Gathering #1 of 2", one gate `firmages:the_origin` (gate NBT read back), still one listener after three script reloads | pass |
| Waves to boss | Maledictus and Ignis killed by command; "gate firmages:the_origin completed"; The Primordial with health 1024, armour 20, toughness 20, knockback resistance 1, tag `firmages.final_boss` | pass |
| Foreign gate | `gateways:basic/enderman` opened and completed in The Origin after `origin reset` (probe: "gate completed gateways:basic/enderman"): no boss, no finale line | pass |
| Damage intake (phase 1) | `damage ... minecraft:mob_attack` without attacker, 20 s each: 17.5 every 0.31 s 4.57/s, 17.5 every 0.10 s 4.59/s, 22.5 every 1 s 4.50/s | independent of the hit pattern |
| Full fight, 17.5 every 0.31 s | 1024 -> 683 in 78 s (4.4/s), phase 2 to 341 in 93 s (3.6/s, Resistance I), phase 3 to 0 in 109 s (3.1/s, Resistance II); dead after 4 min 46 s | 5 to 10 min with real uptime |
| Phases | subtitles "The Primordial calls the stone of the End" and "The Primordial hardens; the sky breaks" plus one chat line each, to both players | pass |
| Death | script line "The Primordial died (tag firmages.final_boss)"; firmages-core "defeated, granting finale_won", FINALE payload to both, ceremony at the altar; chat "The Primordial falls..."; no helper left (`firmages.primordial_add`), altar present | pass |

Why the numbers: Cataclysm caps the Ender Guardian at 22 damage per hit and drains a 13-per-second damage bucket
(`LLibrary_Boss_Monster.hurt`/`tick`, javap); the vanilla hurt cooldown allows two full hits a second. A Chaotic sword
(DE 3.1.4: 17.5 damage, 3.2 swings a second) or staff (22.5, once a second) is far above that for one player, so team
size and gear barely change the rate; armour toughness 20 makes the share that armour keeps nearly equal for small
and large hits. Decision log: "Länge des Endkampfs", "Endkampf-Skript abgesichert".

### (3) Reload (age_9, 2 headless players, `firmages reload`, fresh boots)

| Condition | Before (b2f2249) | After |
|---|---|---|
| Reload 1 / 2 / 3 after the boot, players in the overworld | 8.09 / 7.11 / 7.06 s | 7.70 / 6.80 / 6.51 s |
| Final synced boot, players logged in at the Origin arena | not measured | 8.42 / 7.66 / 7.24 s |
| Same boot, players moved to the overworld (reloads 4 to 6) | not measured | 6.95 / 6.94 / 6.72 s |
| KubeJS recipe phase ("taking ... in total"), reloads 2 and 3 | 2.13 / 2.18 s | 1.69 / 1.61 s |
| "Posted recipe events" | 0.83 to 0.93 s | 0.33 to 0.39 s |
| Script handlers (temporary timers in the test-server copy) | about 850 ms | about 320 ms |

The cuts, each checked with a before/after `fa_dump_full` (identical apart from the 14 combiner recipes below):

| Script | Before (ms) | After (ms) | Change |
|---|---|---|---|
| `bronze_age.js` | 135 to 160 | 12 | andesite tier = 14 fixed ids (was a scan of 1,738 Create recipes and three `replaceInput` over them) |
| `information_age.js` | 97 to 125 | 45 | 18 type removals as one OR filter; MA essence swaps by id (was three `replaceOutput` mod scans) |
| `ore_ladder.js` | 94 to 120 | 4 | 330 fixed ids (was a JSON pass over 2,700 Mekanism-family recipes); MekaTFC 15 ids |
| `global_removals.js` | 122 to 128 | 80 | no output filter for disabled items (the gate drops their recipes); Occultism silver 15 ids |
| `industrial_age.js` | 110 to 129 | 70 | no output filter for disabled IE and C&A items |
| `arcane_tfc_inputs.js` | 56 to 81 | 8 | the MAP rewrite reads 167 fixed ids (was all 2,714 magic recipes as JSON) |
| `electric_age.js` | 50 to 80 | 8 | Diesel Generators type pass dropped (its namespace pass covers it), no output filter |
| `space_age.js` | 36 to 38 | 0 | 62 fixed ids |

The 14 Mekanism combiner recipes of vanilla ore blocks were meant to go (`ore_ladder.js`, matrix 6.1) but the old JSON
pattern expected `"id"` before `"count"`; the fixed list removes them. Every fixed list has a guard in
`poc_analyze.py` that names a recipe the list misses (C-1a, C-3m, C-3s, C-6a, C-6b, C-6e, C-7a, C-M, G-1). The steady
reload is under 7.5 s with the players in the overworld; the first reload after a boot (cold JIT) and reloads with
both players in The Origin are not (7.7 to 8.4 s). What is left is mostly outside the pack scripts: parsing 35,000
recipes (about 0.8 s), the ProgressiveStages player sync and EMI Loot per player, the reload workers. The stall also
drifted up by about 0.7 s over 20 reloads with fa_dump runs in one server session.

### (4) Stations and gear across all Ages

R-0 now covers 14 vanilla blocks. Seven more had no source in a TFC world: enchanting table (6 recipes, among them the
DE Energy Transfuser and the MA Enchanter), barrel (18: Create postboxes, Fragile Fluid Tank, Velocity Sensor), chest
and trapped chest of the 16 Twilight chests, lectern (Ars storage lectern), fletching table (projectile glyph), soul
campfire (Afrit smelter), milk bucket (abjuration essence, dispel glyph, two Ars Additions charms, Create cake). They
take the Apothic enchanting table, TFC barrels, chests and lecterns (`tags/tfc_stations.js`), feathers, the firepit
and TFC's `tfc:fluid_content` for 1000 mB of milk. New check C-Gv (no recipe needs a vanilla tool or armour piece that
no recipe makes) found the MA seed reprocessor (iron hoes), the backpack tool swapper (wooden tools) and the FTB loot
crate opener (stone pickaxe); the elytra now counts as gear, so C-G found the HDPE Elytra (now on a propeller). No
recipe of a late-Age mod still names `minecraft:crafting_table` or `minecraft:furnace` (the remaining ones are vanilla
`minecraft:` recipes: crafter, blast furnace, furnace minecart).

`poc_analyze.py` on the all-Ages dump of the final boot (33,203 recipes): **67 checks, 0 FAIL** (62 before; new C-1a,
C-3m, C-3s, C-6e, C-Gv). `gen_stage_locks.py --check` clean. `validate_quests.py` 0 errors, 0 warnings.

### Open points of this run

- **Reload after a boot** stays at 7.7 to 8.4 s with 2 players, and about 0.5 s more while the players stand in The
  Origin. Further cuts need the mod side (coalescing the ProgressiveStages lock syncs, SPEC 1.2 hot swap) or less
  recipe parsing; the scripts are down to about 0.3 s.
- **Needs a client:** Ars accepting a TFC bucket of milk on its pedestals and glyph press (`tfc:fluid_content` loads in
  those recipes, no parse error), EMI showing it; the real fight against The Primordial (uptime, the Ender Guardian's
  teleports and the helpers); the subtitles on screen.
- **Keystone lending** (SPEC 7.4) is still a mod milestone; the Marid quest already describes it. If a release ships
  without it, drop the borrow clause from `quantum/marid/key`.
- **Found, not changed:** the sequenced assemblies `simulated:...gyroscopic_mechanism`, `simulated:...engine_assembly`,
  `create_connected:...control_chip` and `create_factory_logistics:...fluid_mechanism` are dropped because a chance
  output is a disabled Create sheet (before this run by the output filter, now by the gate); unless another recipe
  makes these items they need a `byproducts.js` entry (G-3 cannot see removed recipes). The `createdeco` shipping
  containers need a barrel and the disabled Create iron sheet. `advanced_ae:strength_card` keeps a netherite sword
  (craftable, but the gear_rule swap targets a diamond sword the recipe no longer has).
- The headless debug players died in an earlier run and cannot respawn; a `debug player respawn` command in
  firmages-core would save moving their files.

## M6, textures and release tooling

Date: 2026-10-01, branch `dev`. Integrated: the mod branch (M6 Keystone lending, blessing effects, energy rite, Jade,
The Origin's own travel lock, mob_9 spawn list, boss trigger hardening; 9f181e1 to 124b8f3) as merge aaf5ed4 and the
texture branch (generated 16x16 textures, e43621f) as merge feb8ec5, both `--no-ff` on top of the content fixes and the
release tooling. firmages-core **0.5.0**: `gradlew build` 67 JUnit tests, 0 failures; `runGameTestServer` "All 24
required tests passed" (selftest core 17/17, gate 16/16). The 0.5.0 jar replaces 0.4.0 in `mods/`, `packwiz refresh`.
Test server synced with `packwiz-installer-bootstrap -g -s server` from `packwiz serve` (quest folder deleted first),
**fresh world** (`--wipe-world`). Alpha and Beta, survival, one FTB party. `debug.allowSimulate` and the whitelist were
off only during the run; both are back on their old values.

### Merge decisions

- **Boss script**: the content-fix version stays (phases, `faInOrigin`, `faAltar`, announcements, tagged-death
  listener); the gate id now comes from `FirmAges.finaleGateway()` (mod config `origin.finaleGateway`) instead of a
  script constant, and the duplicate `faGateId` of the mod branch is gone. SPEC 16.3 keeps both texts (decision log).
- **Line endings**: `config/cucumber-tags.json` and `kubejs/server_scripts/tags/late_ages.js` had CRLF in the working
  tree while git stored LF, so `index.toml` carried hashes that an install from GitHub could never match ("Hash
  invalid!", release run). Both are LF again and the index is refreshed; no served text file has CRLF now (416 index
  entries checked). The other two files of the release report were already LF after the content commits.

### Assets (the dedicated server cannot render)

| Check | Observed | Result |
|---|---|---|
| `python dev/gen_textures.py --check` | 33 referenced textures, 0 errors, 0 warnings | pass |
| Every JSON and `.mcmeta` under `mod/.../assets` and `kubejs/assets`, strict parse (duplicate keys refused) | 11 models, 4 blockstates, 1 lang, `sounds.json`, 1 `.mcmeta`: all valid; every model has `parent` or `elements`, every face texture variable is defined, every blockstate variant names a model, the `.mcmeta` has `animation`; no CRLF | pass |
| Jar content | `firmages-core-0.5.0.jar` carries the block textures (16 PNG under `textures/block/`) and `version="0.5.0"` | pass |

### Install and boot (fresh world)

| Check | Observed | Result |
|---|---|---|
| Sync | bootstrap 416/416 entries, "Finished successfully"; `verify_install.py dir test-server --side server`: 401 expected, 0 missing; 5 config hash differences (`DraconicEvolution.cfg`, `createdieselgenerators-server.toml`, `ftbchunks-world.snbt`, `mysticalagriculture-common.toml`, `progressivestages.toml`) are the mods rewriting their own files on start (comments dropped) | pass |
| Boot | `Done (10.972s)` | pass |
| KubeJS | startup 2/2, server 32/32 scripts, 0 errors, 0 warnings; "Added 461, removed 7038, modified 333, 0 failed" in 3.25 s; 0 error lines in `logs/kubejs/*.log` | pass |
| ProgressiveStages | `progressivestages validate`: 28/28 stage files valid; no ERROR lines | pass |
| FTB Quests | `Loaded 2 chapter groups, 13 chapters, 345 quests, 21 reward tables` (= `dev/quests-notes.md`); `validate_quests.py`: 1282 object ids, 0 errors, 0 warnings | pass |
| firmages-core | 0 ERROR or WARN lines at boot; Jade plugin `FirmagesJadePlugin` loading; "The Origin spawn list: monster minecraft:enderman w10, cataclysm:endermaptera w6, cataclysm:ignited_revenant w2, cataclysm:ender_golem w1"; arena built in 1352 ms, return gate at 0 66 31; `origin status` names the finale gateway `firmages:the_origin` and the spawn list | pass |
| Gateways | "Registered 13 gateways" | pass |
| Other ERROR lines | DISTXFORM, azimuth mixin, loot tables, tfcrf/woodencog/createdeco recipe parse errors, Sable `copycat_catwalk`, Polymorph EMI module, TF `dev_new_world`: as before | not ours |

### The ladder and the M6 checks

Rings 0 to 8 by `build_shrine.py --heart 0 200 0 --rings 0-8 --platform` (550 commands); `dawn` to `age_3` by `stage
grant`, then tiers 3 to 8 from the shrine (offering by `debug use`, prayer by `debug pray`). `build_shrine.py --rites 5`
now also puts 1,000,000 FE into each HV capacitor of the crown (IE's own block entity key `energy`, javap of
`EnergyHelper.serializeTo`), because ring 5 has the energy rite since M6.

| Grant | Path | Reload (ms) |
|---|---|---|
| age_0 to age_3 | four `stage grant`, one reload, the first after the boot | 11,664 |
| age_4 | tier 3, Alpha (candles) | 7,954 |
| age_5 | tier 4, Alpha (lanterns) | 7,110 |
| age_6 | tier 5, Alpha (floodlights and capacitors) | 6,920 |
| age_7 | tier 6, Beta and Alpha (Chorus) | 6,991 |
| age_8 | tier 7, Beta and Alpha (stars) | 6,976 |
| age_9 | tier 8, Beta and Alpha (Chorus, stars, Keystone back) | 7,310 |

| Check | Observed | Result |
|---|---|---|
| Energy rite (tier 5) | capacitors set to 0 FE: `shrine status` "Charge the four capacitors of the crown with 1,000,000 FE together (now 0 FE)", the prayer stays refused with that actionbar line; 4 x 250,000 FE: "rites not done []", prayer heard, age_6 | pass |
| Lend (age_8) | Alpha, empty hand, sneak-use on plinth 4 (0 200 -5): Alpha holds `firmages:arcane_keystone` (no components); chat "Caelum lends you ... for the rite of the Marid"; plinth BE `lent: 1b, relic: 0b, tier: 3`, no item; `shrine status` "relics 5, lent [firmages:arcane_keystone (tier 3)]", intact true, valid ring 8; the rites add "The lent ... must rest on its plinth again" | pass |
| Offering while lent | Quantum Core on plinth 9: refused ("Bring it back to its plinth before this offering"), item kept, plinth empty | pass |
| Marid ritual input | `fa_recipe`: `firmages:ritual/awakened_keystone [occultism:ritual]` takes `firmages:arcane_keystone`, 2 `mekanism:alloy_atomic`, `draconicevolution:wyvern_core`, `#firmages:boss_token/age_8`; the lent item is exactly that item id without components, so the plain item ingredient matches. The ritual itself (pentacle, bound Marid) was not run headless | pass (ingredient) |
| Return | the lent Keystone taken away (the ritual consumes it), `firmages:awakened_keystone` in hand, use on plinth 4: "... rests on its plinth again. Caelum's circle is whole."; BE `item: awakened_keystone, relic: 1b, lent: 0b`; `shrine relics` "tier 3: firmages:awakened_keystone" | pass |
| age_8 offering after the return | Quantum Core on plinth 9 accepted (BE `tier: 8`, item quantum_core); only the Chorus left; prayer by both, age_9 | pass |
| Blessings inside the ring (age_8, radius 40) | Alpha at 1 200 -2: speed 0.105 (+5 %), max health 22 (2 x 5 %), block break speed 1.05, block interaction range 5.0, safe fall distance 4, fall damage multiplier 0.8; Beta max health 22 | pass |
| Outside the ring | Alpha at 60 200 0, 2.5 s later: 0.100, 20, 1.0, 4.5, 3, 1.0, luck 0 | pass |
| Broken shrine | one ring-0 post removed: "The shrine is broken...", `intact false`, sanctuary inactive, Alpha back to 0.100 / 20 / 4.5 within 2.5 s; repaired: "The shrine stands again...", attributes back within about 5 s (a read 2.5 s after the repair still showed the base values) | pass |
| age_9 | Resolve added: max health 23, luck 1.0 | pass |
| Origin, Alpha at age_8 (survival) | `execute in firmages:origin run tp Alpha` (console), `firmages origin tp Alpha` ("was not let into The Origin") and Alpha as operator running `execute in firmages:origin run tp @s`: Alpha stays in the overworld each time. ProgressiveStages answers first ("This dimension is locked! Required: Age 9"): its listener has the same HIGH priority and is registered earlier, so firmages-core's listener does not see these cancelled events | pass |
| The mod's own lock alone | the `firmages:origin` line of `age_9.toml` commented out in the test-server copy, `progressivestages reload`, console tp: Alpha stays in the overworld, log "The Origin: Alpha was turned back (no age_9) from minecraft:overworld"; file restored (byte-identical with the repo) and reloaded | pass |
| Origin at age_9 | console tp and `firmages origin tp Beta`: both in `firmages:origin`; back to the overworld by tp | pass |
| Finale gate in the overworld | `open_gateway ... firmages:the_origin` in the overworld (players creative for the waves): Maledictus, Ignis, then both killed by command, gate gone after 28 s; no Ender Guardian, no entity with `firmages.final_boss`, no `[finale]` line | pass |
| Tagged death outside The Origin | zombie with `firmages.final_boss` killed in the overworld: WARN "a firmages.final_boss died in minecraft:overworld ... not in The Origin; it does not count"; Alpha has no `finale_won`; `origin status` unchanged | pass |
| mob_9 | spawn list loaded at boot and after every reload (log line above) and listed in `origin status` | pass (loaded) |

### `poc_analyze.py`, quests and reload at age_9

All-Ages dump of this world at age_9 (`fa_dump`, `fa_dump_full`: 33,204 recipes): **67 checks, all PASS**.
`validate_quests.py` 0 errors, 0 warnings. Three operator reloads (`firmages reload`) at age_9 with Alpha and Beta
online in the overworld, after about 25 minutes of server time and two dumps: **7,100 / 7,505 / 6,587 ms**.

### Open points of this run

- **Needs a client**: the textures in game (KubeJS auto-models for `firmages:dust/zinc` and `dust/bismuth`, the
  animated lit heart top, the plinth and reactor models), the Jade tooltips, the real Marid ritual with a lent
  Keystone, how many mob_9 mobs spawn in the lit arena.
- **Blessing return delay**: after a repair the attributes came back a few seconds after the "stands again" line;
  harmless, but a client tester may notice it.
- **Debug players and `deop`**: with `enforce-whitelist=true`, a `deop` kicks every player not on the whitelist, the
  headless debug players included, even after `whitelist off`. Avoid `op`/`deop` during a debug-player run or put the
  players on the whitelist.
- The pack version in `pack.toml` is still 0.1.0; `dev/release.ps1 -Version` bumps it for a release.
- `origin/main` is still the skeleton; friends and the live server read main (release run).

## M7 consecration and textures v2

Date: 2026-10-01, branch `dev`. Integrated: the M7 branch (consecration and maintenance mode, SPEC §17; 1f6dcb5,
3f7cb79) as merge a8806bc and the textures v2 branch (18 redrawn items, 9 animated textures, 19 consecrated block
textures; 9a256f6, bb2d000) as merge 011a1d7, both `--no-ff`, on top of the CurseForge conversion. Both worktrees
and branches are removed. firmages-core **0.6.0** (MIT): `gradlew build` 70 JUnit tests, 0 failures;
`runGameTestServer` "All 27 required tests passed" (selftest core 17/17, gate 16/16), last run on the final code
5318cb7. The jar's `META-INF/neoforge.mods.toml` says `license="MIT"`, `version="0.6.0"`. 0.6.0 replaces 0.5.0 in
`mods/`, `packwiz refresh`. Two debug players Alpha and Beta in one FTB party, survival; `debug.allowSimulate` and
the whitelist were off only during the run and are back on their old values.

### Merge decisions

- **Textures over placeholders:** the 17 PNGs both branches added (`textures/block/consecrated_*`) are the textures
  v2 files; the M7 placeholder `consecrated_pillar.png` is gone, the pillar side is `consecrated_pillar_side.png`.
- **Models follow `dev/textures-notes.md`:** `blockstates/consecrated_lamp.json` picks `consecrated_lamp` for
  `lit=false` and the new `consecrated_lamp_lit` for `lit=true` (M7 had one unlit model for both); the lamp's item
  model uses the lit model; the nine accent overlays carry `neoforge_data` (block and sky light 15, no AO) on the
  element plus `ambientocclusion: false`; the pillar is a `cube_column` with `consecrated_pillar_side` and `_top`.
  The other base models and blockstates were already identical.
- **`gen_textures.py --check`:** the exemption for `consecrated_*` is gone; instead every consecrated texture that no
  mod model references is an error (tested: without `consecrated_lamp_lit.json` the check reports 3 errors).
- **Decision log:** both branches' rows kept (CurseForge rows first, then M7, then textures v2).

### Fixes from this run

1. **`fetchLibs` after the CurseForge conversion** (`build.gradle`): the seven lib metafiles now have no `url` and a
   sha1 hash, so the build failed at once ("index is out of range"). It reads `hash-format`, builds the forgecdn URL
   from `file-id` and verifies with that algorithm (tested by deleting the Rhino jar: downloaded again, byte-equal).
2. **Consecration in real time** (`ShrineConsecration.pacedStep`): in the first ladder run ring 3 turned in 0.34 s
   instead of 6 s. The age_4 grant stalls 2.7 s, vanilla's "Can't keep up" warning is rate-limited to 15 s and only
   that warning skips missed ticks, so after the reload 6 s later the server ran 120 ticks back to back. A catch-up
   tick now counts by its real duration; the GameTest server (unthrottled) and `/tick sprint` count every tick. The
   batch also starts 20 ticks after the reload is idle, as SPEC §17.3 says (it started on the idle tick).
3. **`build_shrine.py`** set the heart unconditionally; over an awakened heart that resets `lit`, `awakened` and
   `maintenance`. It now only places a missing heart, so later rings can be added to a standing shrine.
4. **Tools for this proof:** debug players got `debug punch <name> <pos> [sneak]` (left click, the maintenance
   toggle), `debug mine <name> <pos>` (survival mining through `handleBlockBreakAction`) and `debug break` (the server
   side of a finished mining); they log particle packets. The consecration logs each block at DEBUG (`debug.log`) and
   one INFO line per finished batch.

### Assets (the dedicated server cannot render)

| Check | Observed | Result |
|---|---|---|
| `python dev/gen_textures.py --check` | 52 referenced textures, 0 errors, 0 warnings | pass |
| Every JSON and `.mcmeta` under `mod/.../assets` and `kubejs/assets`, strict parse (duplicate keys refused) | 38 models, 12 blockstates, 9 `.mcmeta`, lang and `sounds.json`: all valid; every model has `parent` or `elements`, every face texture variable is defined, every blockstate has `variants` or `multipart`, every `.mcmeta` has `animation`; no CRLF | pass |
| Blockstate properties | multipart `when` uses `accent` 0..8 (`ConsecratedBlock.ACCENT`) and `lit` (lamp only) | pass |
| Jar content | `firmages-core-0.6.0.jar` holds the 19 consecrated block textures and `consecrated_lamp_lit.json`, no `consecrated_pillar.png` | pass |

### Install and boot (fresh world)

| Check | Observed | Result |
|---|---|---|
| CurseForge metafiles, clean install | empty folder, `packwiz-installer-bootstrap -g -s client` from `packwiz serve`: 423/423, "Finished successfully", no "must be downloaded manually"; `verify_install.py dir --side client`: 417 expected, 0 missing, 0 hash mismatch. The five client-side Modrinth-kept mods (Alternating Flux, Create Aeronautics, Design n' Decor, Create Factory Logistics, Entity Culling) are installed | pass |
| Test server sync | `-g -s server` (quest folder deleted first): 423/423, "Finished successfully", 0 error lines; `verify_install.py dir test-server --side server`: 408 expected, 0 missing; 6 config hash differences are mods rewriting their own files on start (`DraconicEvolution.cfg`, `createdieselgenerators-server.toml`, `cucumber-tags.json`, `ftbchunks-world.snbt`, `mysticalagriculture-common.toml`, `progressivestages.toml`; the installer keeps them as cached). TFC Ruins (Modrinth, server only) is installed | pass |
| Boot | `Done (9.984s)`; the restart on the same world `Done (3.049s)` | pass |
| KubeJS | startup 2/2, server 32/32 scripts, 0 errors, 0 warnings | pass |
| FTB Quests | `Loaded 2 chapter groups, 13 chapters, 345 quests, 21 reward tables` | pass |
| firmages-core | 0 WARN or ERROR lines over the boot and the whole run; "Shrine consecration: roles for 9 rings"; arena built in 476 ms | pass |
| Other ERROR lines | DISTXFORM, loot tables (dndecor, railways, create_connected, createcasing, more_immersive_wires, extendedae), tfcrf and woodencog recipe parse errors, Sable `copycat_catwalk`, Polymorph EMI module, TF `dev_new_world`, trainutilities advancements: as before | not ours |

### Ring 0: ceremony and consecration

Heart at 0 200 0 on a stone platform, ring 0 by `build_shrine.py --rings 0 --platform` from Age materials
(`tfc:rock/cobble/granite`, `tfc:wood/log/oak`, `tfc:thatch`); `stage grant` dawn and age_0.

| Check | Observed | Result |
|---|---|---|
| Before | `shrine status`: ring 0 complete, phase READY; `execute if block` cobble, log and thatch pass; `shrine originals` 0 | pass |
| Offering and prayer | Hearthstone on plinth 0 200 -2, `tfc:firestarter` on the heart ("The heart is kindled"), Alpha and Beta pray: `Shrine prayer heard (tier 0, by [Beta, Alpha]): granting age_1`, `Shrine: consecrating ring 0 (20 blocks, after the reload)`, one reload (6,108 ms) | pass |
| Spread, server log | reload finished 22:28:12.701; the four stones next to the heart at 13.744 (+0), the four diagonal stones at 15.141 (+28), the posts bottom up at 19.843, 19.890 and 19.941 (+121 to +123): `consecration done, 20 block(s) of rings [0] from +0 to +123 ticks`, 6.2 s from the heart outward | pass |
| Spread, players | Alpha receives `end_rod x6` and `enchant x10` particle packets at each block position at the same times | pass |
| Result | all 20 positions are `consecrated_<role>[accent=0]`: 8 stone (cobble), 8 pillar (logs), 4 trim (thatch); heart and plinth unchanged; `shrine originals` 20 (the 22 blocks of ring 0 minus heart and plinth; the brief's "21" counted the plinth); ring 0 complete, intact true, valid ring 0 | pass |

### Unbreakable, maintenance, repair

| Check | Observed | Result |
|---|---|---|
| Survival mining outside maintenance | copper pickaxe, `debug mine`: "destroy progress 0, mining aborted"; `debug break` on a stone and a post: "refused", blocks unchanged, actionbar "Consecrated by Caelum. Sneak and punch the Shrine Heart with an empty hand to start maintenance." | pass |
| Toggle needs sneak and an empty hand | sneak-punch holding the pickaxe, and a plain punch with an empty hand: maintenance stays off | pass |
| Sneak-punch | empty hand, `debug punch ... sneak`: heart `maintenance=true`, chat to both "Alpha opened the shrine for maintenance: for 60 s ...", actionbar countdown from 60 s; a prayer during maintenance: "Caelum does not listen while the shrine is under maintenance." | pass |
| Break in maintenance | `debug mine` (progress 0.023 per tick): broken after 31 ticks, "dropped [1 tfc:rock/cobble/granite]", the item entity lies at the spot; ring 0 21/22, "The shrine is broken. Caelum's blessings rest until it is repaired."; Ages unchanged (dawn, age_0, age_1) | pass |
| Repair | granite cobble placed by Alpha (`debug use`): "The shrine stands again"; the second sneak-punch ends maintenance ("Alpha ended the shrine maintenance. Caelum consecrates what stands again."), `consecrating 1 standing block(s)`, the stone is `consecrated_stone[accent=0]` again with the new original | pass |
| Command and timer | `shrine maintenance on` at 22:29:13.456, actionbar 60 s, 30 s, 1 s, "Shrine maintenance off (timer or console)" at 22:30:13.439 (60.0 s), heart `maintenance=false`; `on` then `off` by command | pass |
| TNT | two primed TNT on top of ring 0 (players moved away): the three dirt blocks next to them are gone, heart and plinth stand, all 20 positions still consecrated with accent 0, ring complete | pass |
| Pistons | a piston pushing down onto a stone, a piston pushing sideways into a stone, a sticky piston on a stone: none extends; ring unchanged | pass |

### Migration and the ladder

| Check | Observed | Result |
|---|---|---|
| Migration (pre-M7 world) | `stage grant Alpha age_2` (an Age from a quest or an old world), then ring 1 built from Age materials: `consecrating 24 standing block(s) of awakened rings (rings [1])`, `consecration done, 24 block(s) of rings [1] from +0 to +120 ticks`; 19 brick, 5 metal (bronze blocks, bell), accent 1; ring 1 complete. The load path itself (the heart's first validation after loading) is the GameTest `awakenedRingMigrates` | pass |
| Ladder | tiers 2 to 8 from the shrine with `dev/build_shrine.py --rings N [--rites N]`, offering by `debug use`, Alpha and Beta pray; tiers 7 and 8 at `time set midnight` (TFC disables `/weather`, and its `/time set` refuses 18000) | pass |
| End state | `shrine originals` **537** (20, 24, 51, 59, 63, 71, 75, 87, 87 = SPEC §17.2); every one is `consecrated_<role>[accent=ring]`; `shrine status` valid ring 8, intact true, awakened 9, all nine rings complete; `firmages ages` 11 unlocked | pass |
| Restart (same world) | after the chunks load: 537 originals, all consecrated, no consecration batch, 0 firmages WARN/ERROR | pass |

| Grant | Path | Reload (ms) | Consecration, wall clock |
|---|---|---|---|
| age_0 | `stage grant` | 6,842 | none |
| age_1 | tier 0 (ring 0, 20 blocks) | 6,108 | 13.744 to 19.941 (6.2 s) |
| age_2 | `stage grant` (then ring 1 migrates, 24 blocks) | 5,935 | +0 to +120 ticks |
| age_3 | tier 2 (ring 2, 51) | 6,049 | 34.891 to 41.092 (6.2 s) |
| age_4 | tier 3 (ring 3, 59) | 5,934 | 04.141 to 10.540 (6.4 s; 0.34 s before fix 2) |
| age_5 | tier 4 (ring 4, 63) | 5,862 | 35.239 to 41.491 (6.3 s) |
| age_6 | tier 5 (ring 5, 71) | 5,933 | 13.889 to 20.289 (6.4 s) |
| age_7 | tier 6 (ring 6, 75) | 5,895 | 40.989 to 47.191 (6.2 s) |
| age_8 | tier 7 (ring 7, 87) | 6,113 | 05.389 to 11.641 (6.3 s) |
| age_9 | tier 8 (ring 8, 87) | 6,014 | 35.040 to 41.190 (6.2 s) |
| operator | `firmages reload` three times at age_9, 2 players, after the restart | 9,441 / 8,345 / 7,937 | none |

### Open points of this run

- **Needs a client:** the consecrated look (marble base, accent overlay full-bright through `neoforge_data`, no
  z-fighting of the coplanar overlay, translucent glass and cutout scaffold under the overlay, the lamp's lit and
  unlit models), the heart's scaffold cage in maintenance, the 9 animated textures (items and altar top), the
  destroy prediction during maintenance (crack animation; outside maintenance no ghost break, from
  `firmages:shrine_sync`), the consecrate sound and the particles as seen, the Jade lines of consecrated blocks and
  the heart, the item models in EMI and the creative tab. The client checklist is `dev/client-test-brief.md`.
- **Store images:** `dev/shrine-viewer/render_shrine.py` and `dev/cf/make_logo.py` still draw the shrine in Age
  materials; the CurseForge images should be rendered again with the consecrated look.
- **`shrine originals` on unloaded chunks** marks every entry "(not consecrated now)" because it reads unloaded
  positions as air; the counts are right. Cosmetic.
- The first operator reload after the restart took 9.4 s (the next two 8.3 and 7.9 s); the Age grants stayed at
  5.9 to 6.1 s.

## Leftovers

Date: 2026-10-02, branch `dev` on top of a3379fb (pack 0.6.0, firmages-core 0.6.0 unchanged). Fresh test world
(`run_server.py --wipe-world`), test server synced from `packwiz serve` (`-g -s server`, "Finished successfully").
Debug players Alpha and Beta (`firmages debug player join`), survival; Ages granted to Alpha with `stage grant` one
at a time, one reload each (7.3 to 8.9 s). Design calls are in `dev/decisions-while-away.md` (rows of 2026-10-02).

### What changed

1. **Boss fallback gateways (Doc 08 §5.1).** Eight sigils (`firmages:frontier_sigil`, `wild_`, `forge_`, `wither_`,
   `end_`, `space_`, `abyss_`, `chaos_sigil`): KubeJS items with textures (`gen_textures.py`), one grid recipe each
   from the materials of its Age (`recipes/boss_sigils.js`, never the boss drop), each in its Age tag. Used on a block,
   a sigil opens `firmages:<name>_trial` (`kubejs/data/firmages/gateways/`) the way a gate pearl does
   (`server_scripts/firmages/boss_fallback.js`; the clicked block's GUI never opens, creative keeps the sigil). The
   gate pays out the real boss drop, so `firmages:boss_token/<age>`, the quests and the recipes that name the drop by
   id accept it unchanged. From `age_7` on the End Trial also gives a Dragon Heart (hook on `GateEvent$Completed`).
   Tooltips and gate names (boss bar) in `client_scripts/item_tooltips.js`. `the_origin_fallback.json` is the finale
   gate with vanilla echoes of Maledictus and Ignis, switched on only by `origin.finaleGateway`.
   Quests (`gen_quests.py`): each boss quest is "kill OR bring the drop" (both tasks optional), and every keystone or
   boss quest of a fallback Age names its sigil, ingredients and payout. The Twilight Hydra is no checkpoint in this
   pack (Doc 08 §2.2: Naga and Lich), so it has no gate; Ignis has none of its own (side mission; finale fallback
   above).
2. **Sequenced assemblies with disabled chance outputs** (`byproducts.js`): Simulated engine assembly and gyroscopic
   mechanism, Create Connected control chip, Create Factory Logistics fluid mechanism lose their disabled outputs
   (Create sheets, crushed ores) and take `c:plates/<metal>` instead of the Create sheet as input; the control chip's
   electron tube byproduct waits for age_2.
3. **Dead inputs** (`recipes/dead_inputs.js`, new): 97 recipes named by item id something no recipe makes (the four
   Create sheets, vanilla copper and gold ingots, iron block, lapis) and now take the common tag; among them the 16
   createdeco shipping containers, whose vanilla barrel became `#tfc:barrels` (with the Create Dragons Plus fragile
   fluid tank, moved here from `tfc_station_inputs.js`). Also: `create:brass_nugget` from TFC brass (no recipe made it,
   about 30 recipes ask for it) with TFC heat data, Ad Astra plateblocks on `c:rods/<metal>`, the MA soul extractor
   on soulium ingots (the soulium dagger is disabled).
4. **Gear rule, AdvancedAE strength card** (`gear_rule.js`): the netherite sword becomes a black steel sword blade
   (diamond sword -> steel blade as before; the AE2 fluix and certus swords stay).
5. **`poc_analyze.py`**: new checks C-Di (no live recipe names a dead item), R-F (each sigil reachable at its Age
   without that Age's or later boss drops, its gate file exists and pays out every member of the boss token tag), R-L
   (the 15 items whose only recipe was broken are reachable at their Age); R-0 judges a mod recipe under the
   `minecraft:` namespace (createdeco containers) by its output.

### Results

| Check | Observed | Result |
|---|---|---|
| Boot | `Done (11.514s)`; KubeJS startup 2/2, server 35/35 scripts, 0 errors, 0 warnings (also after the `reload` with the hook fix); "Registered 22 gateways"; FTB Quests 13 chapters, 345 quests | pass |
| KubeJS lines at age_9 | `byproducts: 5 recipe(s)`, `dead inputs: 97 of 97 recipes now take the common tag`, `TFC station inputs: 92 replacements` | pass |
| `poc_analyze.py`, all-Ages dump (`fa_dump`, `fa_dump_full`: 33,218 recipes, audit at age_9) | **82 checks, all PASS**, among them C-Di, C-Gv, R-0, R-F age_2 to age_9, R-L age_1/2/4/5/7/8 | pass |
| The four assemblies in the dump | e.g. `simulated:sequenced_assembly/engine_assembly` takes `c:plates/iron`, no disabled result; `control_chip` starts on `c:plates/gold`; all four reachable at their Age (R-L) | pass |
| createdeco container, strength card in the dump | `minecraft:red_shipping_container`: `#tfc:barrels`, `#c:plates/iron`, red dye; `advanced_ae:strength_card`: black steel and steel sword blades, fluix and certus quartz swords, quantum upgrade base | pass |
| Each gate with its sigil (Alpha, all Ages, `debug use` on a bedrock block, waves killed by command) | all eight opened ("boss fallback: Alpha opened ..."), one sigil used each, gate gone after 18 to 26 s; payout at the gate: Frontier naga + lich trophy, Wild wilden tribute, Forge monstrous horn, Wither nether star, End 4 dragon's breath + 1 dragon heart, Space witherite block, Abyss abyssal egg, Chaos 4 chaos shards | pass |
| End Trial at age_6 (`stage revoke` age_9, 8, 7) | 4 dragon's breath, no dragon heart | pass |
| Sigil of a locked Age | Beta (dawn only) with a Frontier Sigil: "This item is locked!", no gate, sigil kept; at age_6 `recipes why firmages:crafting/space_sigil`: DROPPED (returns with age_7), `end_sigil` KEPT | pass |
| Finale fallback gate in the overworld | `open_gateway ... firmages:the_origin_fallback`: Echo of Maledictus, Echo of Ignis, both; gone after 33 s; no `[finale]` line | pass |
| `validate_quests.py` | 1291 object ids, 0 errors, 0 warnings | pass |
| `gen_textures.py --check` | 60 referenced textures, 0 errors, 0 warnings | pass |

Bug found and fixed in this run: the first End Trial hook declared a `const` inside `try`, which Rhino refuses
("redeclaration of var gate"); it logged the error and the gate still paid its file rewards.

### Open points of this run

- **Needs a client:** the waves fought for real (here every wave was killed by command), the sigil textures and
  tooltips, the gate names on the boss bar.
- **AdvancedAE strength card** only upgrades the Quantum Armor, which is disabled; the card is craftable at age_8 but
  has no use. Disable it or leave it (Felix).
- **G-4 needs the audit of the dump's world state:** `firmages recipes audit` at dawn lists
  `immersiveengineering:bottling_machine` and `mixer` as undetected types and G-4 fails; the audit at age_9 passes.
  Run the audit after the grants, as in the earlier runs.

## First reload after a boot (firmages-core, branch core-boot-reload)

Where the extra second goes, from the test-server log of the 22:41 boot (age_9, 2 headless players, three
`firmages reload`):

| Part | Reload 1 | Reload 2 | Reload 3 |
|---|---|---|---|
| Whole reload | 9,441 ms | 8,345 ms | 7,937 ms |
| Start to "Posted recipe events" (scripts, reload workers, tags) | 5.15 s | 4.71 s | 4.27 s |
| KubeJS recipe phase ("taking ... in total") | 2.45 s | 2.07 s | 1.91 s |
| firmages-core recipe gate | 331 ms | 305 ms | 324 ms |
| firmages-core AgeIndex build | 95 ms | 77 ms | 67 ms |
| Late pass to "finished" (PlayerList sync, PS) | 0.94 s | 0.68 s | 0.80 s |
| Duplicate PS lock syncs skipped (running total) | 2 | 4 | 6 |

The mod's own parts warm up by about 50 ms; the rest is KubeJS, recipe parsing and the vanilla reload path. The
lock-sync dedupe already covers the first reload. Changes:

- `gate.warmupReload` (default true) and `gate.warmupDelayTicks` (20): on a dedicated server one reload with the same
  Ages after start while nobody is online; skipped on a boot reconcile, after an earlier reload or when a player joins
  first.
- `mixin/ps/PlayerJoinMixin`: PS `onPlayerJoin` (login, dimension change, respawn) sent the lock sync twice like
  `syncPlayer`; the second one is skipped now (GameTest `joinSendsOneLockSync`: 1 skipped for one join).
- The AgeIndex is not cached across reloads (40 to 95 ms; a cache cheaper than reading the tag files could go stale).

Dev dedicated server (firmages-core with KubeJS, PS, IE, Mekanism, DE, about 4,700 recipes; `runServer` with the
gametest classpath, flat world, no player), `firmages reload` after the boot, ms:

| Run | Warm-up | Reload 1 | 2 | 3 | 4 |
|---|---|---|---|---|---|
| warmupReload = false | none | 1,844 | 1,619 | 1,948 | 1,733 |
| warmupReload = false | none | 2,179 | 1,861 | 1,886 | 1,719 |
| warmupReload = true | 2,122 | 1,921 | 1,826 | 1,667 | |
| warmupReload = true | 2,224 | 1,902 | 1,788 | 1,706 | 1,767 |
| warmupReload = true | 2,227 | 1,953 | 1,808 | 1,770 | 1,780 |

In the dev world the first reload a player would wait for drops from 2.01 s to 1.93 s on average, inside the noise
of these runs; the pack's penalty is mostly KubeJS and 35,000 recipes, which the dev world does not have. **Not yet
measured on the pack:** boot the test server, wait for "Age reload (warm-up after boot) finished", then join the debug
players and time three reloads against the table above. With the warm-up on, "reload 1 after the boot" in later
measurements is the second reload of the session.

## Leftovers integration

Date: 2026-10-02, branch `dev`. The worktree branch with the warm-up reload and the join lock-sync dedupe (9a86ca1,
238c7a1, 26094f0) is merged into `dev` with `--no-ff` (2d5cbd8; the only conflicts were the appended sections of this
file and of `dev/decisions-while-away.md`, both kept). firmages-core is **0.6.1** (5c088b9): `mods/firmages-core-0.6.1.jar`
replaces 0.6.0, `packwiz refresh`; README and the CurseForge texts name the 0.6.1 jar. The pack version stays 0.6.0.

### Checks

| Check | Observed | Result |
|---|---|---|
| `gradlew build` | exit 0 | pass |
| `gradlew runGameTestServer` | "All 28 required tests passed", `joinSendsOneLockSync`: 1 duplicate lock sync skipped on join; selftest core 17/0, gate 16/0 | pass |
| Sync | quest folder deleted, `packwiz-installer-bootstrap -g -s server` from `packwiz serve`: "Finished successfully", `firmages-core-0.6.1.jar` in `test-server/mods` | pass |
| Boot, fresh world (`run_server.py --wipe-world`) | `Done (11.197s)`; KubeJS startup 2/2, server 35/35, 0 errors, 0 warnings; 0 ERROR lines from FirmagesCore, ProgressiveStages and FTB Quests; "Registered 22 gateways"; FTB Quests 13 chapters, 345 quests. The other ERROR lines are the known parse noise (TFC Regrowing Forests, WoodenCog, loot tables of absent mods), counted twice because of the warm-up reload | pass |
| Warm-up after boot | "Warm-up reload due in 20 ticks", "Age reload (warm-up after boot) finished in 7884 ms; reload 1 since start" (fresh world, dawn) | pass |
| Grants dawn to age_9 (Alpha and Beta online, `stage grant Alpha age_N`, one reload each) | 9.12 s for age_0, then 7.30 to 8.54 s; 2 duplicate lock syncs skipped per reload and 2 on join | pass |
| `poc_analyze.py` (`firmages recipes audit`, `fa_dump`, `fa_dump_full` at age_9: 33,218 recipes) | **82 checks, all PASS** | pass |
| `validate_quests.py` | 1291 object ids, 0 errors, 0 warnings | pass |
| `gen_textures.py --check` | 60 referenced textures, 0 errors, 0 warnings | pass |
| `firmages selftest all` (age_9) | 44 passed, 0 failed | pass |
| Fallback gateway spot check: End Trial | Alpha (age_9) with 2 End Sigils, `debug use` on a bedrock block: "boss fallback: Alpha opened firmages:end_trial", one sigil left; waves killed by command, gate gone after about 22 s; "completed in age_7: one draconicevolution:dragon_heart added"; at the gate 4 dragon's breath and 1 dragon heart (plus the mob drops of the killed waves) | pass |

### First reload after a boot, on the pack

Same world, stopped at age_9 and booted again (`Done (4.346s)`, "AgeState ... matches the boot snapshot (MIRROR) ...;
no reload"). The warm-up ran 20 ticks after the start, then Alpha and Beta joined (`firmages debug player join`) and
three `firmages reload`:

| | Warm-up (nobody online) | Reload 1 with players | Reload 2 | Reload 3 |
|---|---|---|---|---|
| Before (0.6.0, no warm-up; section above) | none | 9,441 ms | 8,345 ms | 7,937 ms |
| After (0.6.1) | 8,127 ms | 8,986 ms | 8,023 ms | 7,790 ms |
| KubeJS recipe phase, before | | 2.45 s | 2.07 s | 1.91 s |
| KubeJS recipe phase, after | 2.37 s (first load at boot: 3.46 s) | 2.22 s | 1.95 s | 1.85 s |
| firmages-core recipe gate / AgeIndex, after | 320 / 96 ms | 314 / 90 ms | 304 / 70 ms | 360 / 70 ms |

The warm-up takes 0.46 s off the first reload players wait for (9.44 to 8.99 s) and a little off the next two, but the
first reload with players online is still about 1 s slower than the third, and slower than the warm-up itself. So part
of the penalty is tied to players being online for the first time (the per-player sync after the reload), which a
reload with nobody online cannot warm; the gate and AgeIndex are again a small share. Not dug into further in this run.

### Review of the commits since a3379fb

- Diffs read: the mod changes (warm-up in `AgeService`, `LockSyncDedupe` sites, `PlayerJoinMixin`, configs) and the
  boss-fallback hook. `PlayerJoinMixin` matches the PS 3.0.5 bytecode of `ServerEventHandler.onPlayerJoin` (javap:
  `sendLockSync`, `getStages`, `sendStageSync` in a straight line, no branch between them).
- No `tools/`, `test-server/`, build outputs, exports or secrets in the diff of `a3379fb..HEAD`; the jar in `mods/`
  is the shipped mod. `packwiz refresh` after the last commit: no change.
- Claims checked against this run's logs: KubeJS 35/35 with 0 errors, 82 PASS, quests 0 errors, End Trial heart from
  age_7 and one sigil used, GameTest join dedupe 1 skip, `dead inputs: 97 of 97`, `byproducts: 5 recipe(s)`. All hold.
- Trailers: 9a86ca1, 238c7a1 and 26094f0 end with "Claude Fable 5.1", the other commits with "Claude Opus 5.5" (the
  session model of those runs). Left as is; rewriting merged history was not worth it.

### Open points

- First reload with players online is still about 1 s slower; the warm-up covers only half of it (see above).
- Needs a client: waves fought for real, sigil textures and tooltips, gate names on the boss bar; packet order of the
  join dedupe.
- AdvancedAE strength card has no use (Quantum Armor disabled): disable or keep (Felix).

## Maintenance 2026-10-13

Date: 2026-10-13, branch `dev` on top of 281130f (pack 0.6.1, firmages-core 0.6.1).

### Mod updates

`tools/packwiz.exe update --all -y`: 30 updates (28 CurseForge metafiles, 2 Modrinth). Jade 15.10.6, Gateways 5.1.0 and
the rest stay. The six Modrinth-kept mods (`dev/cf/swap/`): Create Factory Logistics and Entity Culling moved on
Modrinth; their CurseForge swap metafiles are updated to the same files (`packwiz update --all` in a scratch pack of
the six swap files; CurseForge sha1 equals the Modrinth sha1 of both jars, so `export_cf.py` still matches them by
filename). Alternating Flux 1.0.6, Create Aeronautics 1.3.2, Design n' Decor 2.2b and TFC Ruins 1.0.0 have no newer
file on either site.

| Mod | Before | After | Source |
|---|---|---|---|
| Apothic Enchanting | `ApothicEnchanting-1.21.1-1.6.2.jar` | `ApothicEnchanting-1.21.1-1.6.4.jar` | CurseForge |
| Ars Nouveau | `ars_nouveau-1.21.1-5.13.2.jar` | `ars_nouveau-1.21.1-5.13.3.jar` | CurseForge |
| Azimuth API | `azimuth-1.4.9.jar` | `azimuth-1.4.10.jar` | CurseForge |
| CorgiLib | `Corgilib-NeoForge-1.21.1-5.0.0.9.jar` | `Corgilib-NeoForge-1.21.1-5.0.0.10.jar` | CurseForge |
| Create Crafts & Additions | `createaddition-1.7.1.jar` | `createaddition-1.7.2.jar` | CurseForge |
| Create Factory Logistics | `create_factory_logistics-1.21.1-1.6.0-all.jar` | `create_factory_logistics-1.21.1-1.6.1-all.jar` | Modrinth |
| Create: Enchantment Industry | `create-enchantment-industry-2.5.4.jar` | `create-enchantment-industry-2.5.4b.jar` | CurseForge |
| Create: Mobile Packages | `create_mobile_packages-1.21.1-0.7.7.jar` | `create_mobile_packages-1.21.1-0.7.8.jar` | CurseForge |
| DragonLib | `dragonlib-neoforge-1.21.1-beta-3.0.28.jar` | `dragonlib-3.0.29+mc1.21.1-neoforge.jar` | CurseForge |
| Entity Culling | `entityculling-neoforge-1.11.2-mc1.21.1.jar` | `entityculling-neoforge-1.11.3-mc1.21.1.jar` | Modrinth |
| ExtendedAE | `ExtendedAE-1.21-2.2.35-neoforge.jar` | `ExtendedAE-1.21-2.2.41-neoforge.jar` | CurseForge |
| FramedBlocks | `FramedBlocks-10.6.1.jar` | `FramedBlocks-10.6.3.jar` | CurseForge |
| Just Enough Items (JEI) | `jei-1.21.1-neoforge-19.57.0.450.jar` | `jei-1.21.1-neoforge-19.64.0.453.jar` | CurseForge |
| Just Enough Mekanism Multiblocks | `JustEnoughMekanismMultiblocks-1.21.1-7.21.jar` | `JustEnoughMekanismMultiblocks-1.21.1-7.23.jar` | CurseForge |
| Mekanism: Ponders | `mekanism_ponders-1.0.3-1.21.1.jar` | `mekanism_ponders-1.0.4-1.21.1.jar` | CurseForge |
| MezzConfig | `mezz_config-1.21.1-neoforge-0.6.5.jar` | `mezz_config-1.21.1-neoforge-0.6.8-standalone.jar` | CurseForge |
| ModernFix | `modernfix-neoforge-5.27.24+mc1.21.1.jar` | `modernfix-neoforge-5.27.26+mc1.21.1.jar` | CurseForge |
| More Mekanism Processing | `MoreMekanismProcessing-1.21.1-6.4.jar` | `MoreMekanismProcessing-1.21.1-6.8.jar` | CurseForge |
| Mystical Agradditions | `MysticalAgradditions-1.21.1-8.0.14.jar` | `MysticalAgradditions-1.21.1-8.0.15.jar` | CurseForge |
| Occultism | `occultism-1.21.1-neoforge-1.224.4.jar` | `occultism-1.21.1-neoforge-1.224.5.jar` | CurseForge |
| Placebo | `Placebo-1.21.1-9.9.2.jar` | `Placebo-1.21.1-9.9.4.jar` | CurseForge |
| ProgressiveStages | `progressivestages-3.0.5.jar` | `progressivestages-3.0.6.jar` | CurseForge |
| Puzzles Lib | `puzzleslib-v21.1.62-mc1.21.1+neoforge.jar` | `puzzleslib-v21.1.64-mc1.21.1+neoforge.jar` | CurseForge |
| Sable | `sable-neoforge-1.21.1-2.0.5.jar` | `sable-neoforge-1.21.1-2.0.6.jar` | CurseForge |
| Sophisticated Backpacks | `sophisticatedbackpacks-1.21.1-3.26.6.2174.jar` | `sophisticatedbackpacks-1.21.1-3.26.12.2208.jar` | CurseForge |
| Sophisticated Backpacks Create Integration | `sophisticatedbackpackscreateintegration-1.21.1-0.2.1.171.jar` | `sophisticatedbackpackscreateintegration-1.21.1-0.2.3.194.jar` | CurseForge |
| Sophisticated Core | `sophisticatedcore-1.21.1-1.5.2.2343.jar` | `sophisticatedcore-1.21.1-1.5.11.2394.jar` | CurseForge |
| Stargate Journey | `Stargate.Journey-1.21.1-0.6.49.jar` | `Stargate.Journey-1.21.1-0.6.51.jar` | CurseForge |
| SuperMartijn642's Core Lib | `supermartijn642corelib-1.1.24a-neoforge-mc1.21.jar` | `supermartijn642corelib-1.1.25-neoforge-mc1.21.jar` | CurseForge |
| Theurgy | `theurgy-1.21.1-neoforge-1.76.1.jar` | `theurgy-1.21.1-neoforge-1.78.0.jar` | CurseForge |

### firmages-core against the new jars

`gradle.properties` pins ProgressiveStages 3.0.6, Stargate Journey 0.6.51 and Occultism 1.224.5 (the other pinned
mods did not change); `fetchLibs` downloaded the new PS and SGJ jars (hash from the metafiles). javap, old against new:

| Target | Observed | Result |
|---|---|---|
| PS `ProgressiveStagesAPI.syncPlayer`, `NetworkHandler.sendStageSync`, `ServerEventHandler.onPlayerJoin` (the three mixins) | bytecode identical, offsets included (constant-pool indices ignored) | pass |
| PS API (`ProgressiveStagesAPI`, `StageChangeEvent`, `StagesBulkChangedEvent`, `LockRegistry`, `StageManager`, `StageCause`, `StageId`, `NetworkHandler`) | no member removed or changed; new: `grantStageFromSourceWithDependencyBypass` and private helpers; one new class (a PS GameTest) | pass |
| SGJ `StructureGenEntity` (and `Step`), `AbstractDHDEntity`, `AbstractStargateEntity`, `StargateNetwork` | only added members (`isWormholeEstablished`, `isDialing`, `findStargatesInLevel`); every `data/` file of 0.6.49 is byte-identical in 0.6.51, new are the Andromeda gate files; the `stargate_pedestal_1` template is present | pass |
| Occultism `DimensionalMineshaftBlockEntity` (`tick()`, `possibleResults`), `MinerRecipe` and the four recipe results | identical | pass |
| Ars Nouveau 5.13.3 `EnchantingApparatusTile` (`memo`, `Memoizer.memoize` in `<init>`, `getRecipe`) | bytecode identical to 5.13.2 | pass |
| IE MineralMix, Jade, Modonomicon, Gateways `GateEvent`, Create, Mekanism | not updated | n/a |
| `gradlew build` (also with `compileJava --rerun jar --rerun`) | exit 0; the jar is **byte-identical** to `mods/firmages-core-0.6.1.jar` (460 entries, same sha1) | pass |
| `gradlew runGameTestServer` | "All 28 required tests passed"; "GameTest dial: Origin address -> CONNECTION_ESTABLISHED_INTERSTELLAR, unknown address -> INVALID_ADDRESS" against SGJ 0.6.51; selftest gate 16/0 | pass |

The jar does not change, so firmages-core stays **0.6.1** (nothing to ship); SPEC §1.4 and the README name the new
versions. The GameTest log carries one KubeJS error that is not new: `boss_fallback.js` loads Gateways'
`GatewayRegistry`, and Gateways is not in the GameTest run.

### AdvancedAE strength card

Disabled (decision log): `[hidden]` of the Age map and `HIDDEN_ITEMS`; the two sword swaps in `gear_rule.js` are gone.
In the age_9 dump no recipe names `advanced_ae:strength_card`; the item is in `c:hidden_from_recipe_viewers` and in
`firmages:age_items/disabled`.

### Server pack for CurseForge

`packwiz curseforge export -s server` exists, but it writes a manifest zip, and CurseForge server packs (the
"Additional File" of a modpack file) are unpacked server folders with the jars. `dev/release.ps1` step 5 now builds
`dev/exports/FirmamentAges-<version>-server.zip`: `packwiz serve` on port 8091, packwiz-installer-bootstrap `-g -s
server` into `dev/exports/server-work/` (the sync of a live server), plus `server/start.bat`, `start.ps1`,
`user_jvm_args.txt`, `server.properties` from the template, the new `server/README-SERVERPACK.md` (English) and the
NeoForge installer; `-SkipServerPack` skips it. The zip entries use `/` (the first try with .NET Framework's
`ZipFile.CreateFromDirectory` wrote `\`). 0.6.1: 435 entries, 178 jars in `mods/`, 508.6 MB, `release.ps1` exit 0.
`dev/cf/UPLOAD-ANLEITUNG.md` §4 step 4 describes the upload.

### Proof on the test server

Fresh world (`run_server.py --wipe-world`), synced with packwiz-installer-bootstrap `-g -s server` from `packwiz
serve`: "Finished successfully", 444 index entries, 429 server-side files resolved (the updated CurseForge files
included), 15 client-only skipped; `mods/` holds exactly the 178 server jars of the metafiles, no stale old version.
Debug players Alpha and Beta, survival, one FTB party; `debug.allowSimulate` was on for the run only.

| Check | Observed | Result |
|---|---|---|
| Boot | `Done (12.430s)`; KubeJS startup 2/2, server 35/35, 0 errors, 0 warnings; recipes "Added 573, removed 7189, modified 332, 0 failed"; `byproducts: 8 recipe(s)`, `dead inputs: 97 of 97`, `TFC station inputs: 92 replacements`; "Registered 22 gateways"; FTB Quests 13 chapters, 345 quests | pass |
| ERROR lines | 0 from FirmagesCore, ProgressiveStages and FTB Quests; the rest is the known noise (DISTXFORM, client mixins of azimuth, bits_n_bobs, woodencog and simulated, loot tables of railways, dndecor and create_connected, tfcrf and woodencog recipe parse errors, createdeco placard, Sable `copycat_catwalk`, Polymorph EMI module, TF `dev_new_world`, trainutilities advancements) | pass |
| Warm-up after boot | "Age reload (warm-up after boot) finished in 7577 ms" | pass |
| Ladder (shrine at 0 200 0, rings 0 to 8 by `build_shrine.py`) | dawn and age_0 by `stage grant`, then tiers 0 to 8: offering by `debug use`, rites (firestarter on the heart, the bell, `build_shrine.py --rites 2..5`, Chorus, `time set midnight`), prayer by `debug pray`; exactly one "Age reload starting" and one "Ceremony FULL" per Age; end state awakened 9, relics 9, valid ring 8 | pass |
| `firmages selftest all` (age_9) | 44 passed, 0 failed | pass |
| Origin at age_8 | `firmages origin tp Alpha`: "Alpha was not let into The Origin", Alpha stays in the overworld | pass |
| Origin at age_9 | `firmages origin tp @a`: both in `firmages:origin` at 0.5 65 14.5; `sgjourney stargateNetwork address firmages:origin`: "-9-16-21-33-2-37-" (as with 0.6.49); `origin status` "return gate built, altar at 0 65 0 present"; return DHD at 0 65 25 | pass |
| Stargate dial with SGJ 0.6.51 | GameTest `overworldGateCanDialTheOrigin` (simulated dial from an overworld Milky Way gate): CONNECTION_ESTABLISHED_INTERSTELLAR; an unknown address: INVALID_ADDRESS | pass |
| Fallback gateway: End Trial (Alpha at age_9, 2 End Sigils, `debug use` on bedrock) | "boss fallback: Alpha opened firmages:end_trial", one sigil left; waves killed by command, gate gone after 34 s; "completed in age_7: one draconicevolution:dragon_heart added"; at the gate 4 dragon's breath and 1 dragon heart | pass |
| Reactor controller on a real DE reactor (core 30 202 0, four stabilizers by `/setblock`, facing by `data merge`, formed by `debug use`) | cold empty reactor: READY, fuel 10,368, 8 blocks taken, redstone wire 15; burnt down (fuel 2073.6, chaos 8294.4 by NBT): SWAP, 6 large, 3 medium and 5 small fragments out, fuel 9,850, redstone 0; 64 nuggets in the chest: 32 taken, READY, fuel 10,362, redstone 15; the reactor stays COLD | pass |
| `poc_analyze.py` (`firmages recipes audit`, `fa_dump`, `fa_dump_full` at age_9) | first run 81 PASS, **1 FAIL: C-6b**: More Mekanism Processing 6.8 added ingot crushing for antimony, boron, magnesium, palladium and thorium. Fixed in `ore_ladder.js` (five ids in `ORE_LADDER_REMOVALS`), synced, `firmages reload` (6,884 ms), new dump (33,277 recipes): **82 checks, all PASS** | pass after fix |
| `gen_stage_locks.py --registry` on the age_9 dump | 23,572 items; new items assigned: the 35 MMP items of the five metals and the ExtendedAE vacuum interface (age_6), the SGJ Andromeda gate and DHD (age_9), the 8 consecrated shrine blocks (dawn); `--check` clean | pass |
| `validate_quests.py` | 1291 object ids, 0 errors, 0 warnings | pass |
| `gen_textures.py --check` | 60 referenced textures, 0 errors, 0 warnings | pass |

Reload times with 2 players online, ms (each grant one reload):

| Grant | Path | Reload |
|---|---|---|
| age_0 | `stage grant` (first reload with players, after the warm-up) | 6,670 |
| age_1 | tier 0, Alpha (firestarter) | 6,951 |
| age_2 | tier 1, Alpha (bell) | 7,959 |
| age_3 | tier 2, Alpha (lamps) | 7,163 |
| age_4 | tier 3, Alpha (candles) | 6,933 |
| age_5 | tier 4, Alpha (lanterns) | 7,242 |
| age_6 | tier 5, Alpha (floodlights, capacitors) | 6,643 |
| age_7 | tier 6, Beta and Alpha (Chorus) | 6,602 |
| age_8 | tier 7, Alpha (stars) | 6,526 |
| age_9 | tier 8, Beta and Alpha (Chorus, stars) | 6,661 |
| operator `firmages reload` at age_9 | after the ore-ladder fix | 6,884 |

All below the 10 s limit and in the range of the earlier ladders; 2 duplicate PS lock syncs skipped per reload and 2
on join, as with PS 3.0.5.

### Open points

- The server zip is 508.6 MB; CurseForge names no size limit for additional files. If the Author Console refuses
  it, ask CurseForge support.
- MezzConfig now uses the file named `mezz_config-1.21.1-neoforge-0.6.8-standalone.jar` (the newest CurseForge file
  of the project); the server boots with it, the client (JEI 19.64) is untested.
- Needs a client: the updated client-side mods (JEI, Entity Culling, FramedBlocks, Sophisticated Backpacks), a real
  dial and trip through a player-built gate.
- The pack version stays 0.6.1; the next release (`dev/release.ps1 -Version`) raises it.

## Osmium vein and Ore Tooltips removal

Date: 2026-10-13, branch `dev` on top of 37f9f69 (pack 0.6.2, firmages-core 0.6.1). Felix's decisions (1) and (2) of
2026-10-13: the pack's own osmium vein replaces MekaTFC 0.1.0, TFC Ore Tooltips 1.2.0 is dropped. Decision rows in
`dev/decisions-while-away.md`.

### What changed

| Part | Files |
|---|---|
| 30 ore blocks `firmages:ore/{poor,normal,rich}_native_osmium/<rock>` in the ten rocks of MekaTFC's vein (granite, diorite, gabbro, rhyolite, basalt, andesite, dacite, quartzite, schist, gneiss), model `tfc:block/ore` with a grade overlay; TFC ore hardness (6.5 plus the rock category), resistance 10, pickaxe, `c:ores` and `c:ores/osmium` (block tags only) | `kubejs/startup_scripts/osmium.js` |
| Small piece `firmages:ore/small_native_osmium` as a TFC groundcover block (KubeJS TFC `tfc:groundcover`, `.ore()`), its own item | same |
| Ore pieces `firmages:ore/{poor,normal,rich}_native_osmium`, double ingot `firmages:metal/double_ingot/osmium` | `kubejs/startup_scripts/items.js` |
| Vein `firmages:vein/osmium` (`tfc:cluster_vein`, MekaTFC's numbers: rarity 50, size 30, density 0.45, y -40 to 60, grades 35/40/25) in `#tfc:in_biome/veins`; indicator on the surface (rarity 15, depth 35) and underground (`underground_count` 5, new) | `kubejs/data/firmages/worldgen/`, `kubejs/data/tfc/tags/worldgen/placed_feature/in_biome/veins.json` |
| Loot (one piece per block, survives explosion, like TFC ores), item heat (pieces, nugget, ingot, double ingot; TFC rule: forging 0.6, welding 0.8 of the 1230 °C melt), fluid heat for `evolvedmekanism:molten_osmium`, `#tfc:molten_metals`, `tfc:ore_pieces`, `tfc:small_ore_pieces`, `c:double_ingots/osmium` | `kubejs/data/firmages/loot_table/`, `kubejs/data/firmages/tfc/`, `kubejs/data/tfc/tags/`, `kubejs/data/c/tags/` |
| TFC metalworking: 7 heating recipes to molten osmium (4 pieces, nugget, ingot, double ingot), casting in both ingot molds to `mekanism:ingot_osmium`, welding of the double ingot and of the Steel Casing (wrought iron sheet + double ingot) | `kubejs/server_scripts/recipes/age_6/osmium.js` |
| Ore ladder and spagyric on the new pieces (ids unchanged); MekaTFC tag removals, recipe removals and the 10 `neoforge:false` overrides gone | `recipes/age_6/ore_ladder.js`, `recipes/age_3/arcane_age.js`, `kubejs/data/mekatfc/` (deleted) |
| Common tags MekaTFC set for TFC ores (see below) | `kubejs/server_scripts/tags/tfc_common_tags.js`; `tags/broken_mod_tags.js` keeps only the WoodenCog fix |
| Ages: `[ore_families] "firmages:native_osmium" = "age_5"`; `mekatfc` left `[mods]`; registry refreshed | `dev/age_map.toml`, `dev/data/registry.json`, `age_items/age_5.json`, `age_items/age_6.json`, `age_blocks/age_5.json`, `stages/age_5.toml` |
| Textures: osmium palette, 4 piece items, double ingot, 3 grade overlays (KubeJS block textures); `--check` now also reads texture ids from every startup script | `dev/gen_textures.py`, `kubejs/assets/firmages/textures/` |
| Metafiles `mods/mekatfc.pw.toml`, `mods/tfc-ore-tooltips.pw.toml` removed | `mods/`, `index.toml` |
| Checks: C-M rewritten for the vein; piece regex of C-4d, by-design results, Mekanism namespaces of C-6 without MekaTFC; scan default matches `firmages:ore/` | `dev/poc_analyze.py`, `dev/scan_regions.py` |

### Tags MekaTFC carried

A baseline run of the pack before this change (HEAD 37f9f69 exported to a scratch folder, `packwiz serve`, fresh
world, every Age by `firmages ages simulate grant`, `fa_dump` and `fa_dump_full`) against the first run without
MekaTFC showed that MekaTFC also tagged every TFC ore block as `c:ores/<metal>`, every TFC ore piece as
`c:raw_materials/<metal>`, the hay block as `c:storage_blocks/wheat` and `#c:ingots/wrought_iron` as `c:ingots/iron`.
Without them the two Occultism animal rituals failed to load (KubeJS: `arcane_tfc_inputs.js#257` "Component
'ingredient' is not allowed to be empty", `c:storage_blocks/wheat`), and Theurgy's mercury distillation, mineral salt
calcination and divination rods lost the TFC ores. `tags/tfc_common_tags.js` restores them. After it, the item-tag
diff against the baseline is only the entries the pack's canon rejects (`unification.js`): TFC alloy sheets in
`c:sheets/{bronze,gold,silver,steel}`, the wrought iron sheet in `c:sheets/iron` (no recipe uses that tag), TFC
charcoal and lapis powder in `c:dusts`, Mekanism sulfur dust in `c:powders/sulfur`.

### Proof on the test server

Test server synced with packwiz-installer-bootstrap `-g -s server` from `packwiz serve` (MekaTFC jar removed by the
installer), fresh world (`run_server.py --wipe-world`), `debug.allowSimulate` on for the run only.

| Check | Observed | Result |
|---|---|---|
| Boot | `Done (14.229s)`; KubeJS startup 3/3, server 37/37, 0 errors in every load; 0 "Couldn't load tag"; no "AgeIndex: tagged ids are not registered" | pass |
| Block loot (`loot insert ... mine ... diamond_pickaxe`) | normal granite block: 1 `firmages:ore/normal_native_osmium` from `firmages:blocks/ore/normal_native_osmium/granite`; rich gneiss: 1 rich piece; small piece: itself; every stack carries `tfc:heat` | pass |
| Pregen and scan (`chunky spawn` = 3456 1408, radius 256, 1,089 chunks in about 30 s; `scan_regions.py --radius 256`, 812 full chunks) | 18,081 osmium ore blocks in 12 ids (gabbro 11,006, basalt 3,206, schist 2,196, diorite 1,673; normal 7,228, poor 6,295, rich 4,558); 135 `firmages:ore/small_native_osmium` (TFC's small pieces: 2,209 in 10 ids, bismuthinite 196). A first world (spawn 576 1600, same radius, indicator without underground pieces) had 40,143 ore blocks and 0 indicators, hence `underground_count` 5 | pass |
| Disguise (`fa_dims`) | `[[ores.overrides]]` age_5 95 rows (64 TFC + IE bauxite, 30 osmium blocks, 1 small piece), total 1,304, 0 targets that are not registered blocks, spoof active | pass |
| Prospecting (`execute if block ... #tfc:prospectable` on a placed osmium block) | Dawn: no; age_0 to age_4: no (garnierite of age_4: yes); after `simulate grant age_5`: yes; uraninite (age_7) at age_5: no. `precisionprospecting:prospectable_mineral` holds no metal ores in any Age | pass |
| Reloads (2 grants of several Ages, 2 single) | 8,356 ms (age_0 to age_4), 7,285 ms (age_5), 7,251 ms (age_6 to age_9), 6,957 ms (operator) | pass |
| `poc_analyze.py` (age_9 dump) | 82 PASS, 0 FAIL; C-M: no `mekatfc:` or Ore Tooltips id, 30 ore blocks, 12 ladder recipes on the four pack pieces, pieces eaten only by the ladder, TFC heating and the spagyric, 7 heating, 2 casting, 2 welding recipes, pieces and blocks out of `c:raw_materials/osmium` and `c:ores/osmium` (items), pieces and blocks age_5, double ingot age_6; R- checks (Data Matrix and later goals) reach their Ages | pass |
| `gen_stage_locks.py --registry` / `--check` | 23,537 items; age_5 388 items, 95 blocks; `--check` clean | pass |
| `validate_quests.py` | 1291 object ids, 0 errors, 0 warnings | pass |
| `gen_textures.py --check` | 68 referenced textures, 0 errors, 0 warnings | pass |
| `python dev/cf/export_cf.py` | `FirmamentAges-0.6.2-curseforge-upload.zip` 0.9 MB, 190 manifest files, overrides: only `firmages-core-0.6.1.jar` | pass |

Recipes against the baseline (both at age_9): 33,277 before, 33,202 after. Gone: the 79 MekaTFC recipes (67 heating
recipes of pieces and of every ore block, 4 metal heating, 2 casting, 2 welding, the redstone mixture and its barrel
and enriching recipes, pyrite to sulfur in a barrel), new: the 11 of `recipes/age_6/osmium.js`. Changed: the Mekanism osmium ingot as a result now
carries `tfc:heat` (MekaTFC's heat files sat in `item_heats/`, which TFC 4.2 does not read, so the ingot could not be
heated or welded before), the ladder recipes take the new pieces, and Ars tomes, Draconic modules and two Occultism
books differ only in a random spirit name or the order of their data components.

### Open points

- Needs a client: the ore overlays on the ten rocks, the piece and double ingot textures, the groundcover indicator
  model, block and item names (KubeJS lang), the disguise as raw rock before age_5, the crucible and welding in play.
- 7 auto-generated `create_dragons_plus:sandpaper_polishing/*` recipes (Mars and Mercury stone, Moon stone stairs,
  three sourcestone parts, blackstone wall) were in the baseline dump and are missing in this one; Create Dragons
  Plus builds them at runtime, they have nothing to do with osmium, and the cause is not found.
- A world that generated chunks with MekaTFC loses those osmium blocks and MekaTFC items on the update.
- The IE Excavator mix with osmium (Doc 08 §3.2, Electric Age) is still not built.

## CF final prep

Date: 2026-10-13, branch `dev` on top of 4961701 (pack 0.6.2, firmages-core 0.6.2 with `sync.coalesce`). This run
measures the Age-grant fix on the pack, walks the full shrine ladder, spot-checks the osmium vein, adds the root
`LICENSE` and builds the 0.6.3 release and CurseForge upload zip.

Test server synced with packwiz-installer-bootstrap `-g -s server` from `packwiz serve`: "Modpack is already up to
date" (the server had been synced to the same index, sha256 `5727…a9a3`, after 4961701); `mods/` holds 177 jars,
among them `firmages-core-0.6.2.jar`, no MekaTFC and no TFC Ore Tooltips. Three fresh worlds (`run_server.py
--wipe-world`): A with `sync.coalesce = false` (the old behaviour), B with the shipped `true`, C for the ladder.
Debug players Alpha and Beta, survival, one FTB party; spark `tickmonitor --threshold-tick 100`;
`debug.allowSimulate` on for the run only.

**Host load.** A game (Battlefield 6) started on the host at 15:30:48, between run B and run C, and ran through the
rest of the run (about 6.5 of 16 cores). Runs A and B were before it. Every time from run C (boot, ladder, warm
reloads) is inflated two- to threefold and is not a performance result.

### Boot (run B, shipped configs)

| Check | Observed | Result |
|---|---|---|
| Boot | `Done (11.845s)`; 207 mod files with 201 mods | pass |
| KubeJS | startup 3/3, server 37/37, 0 errors, 0 warnings; `byproducts: 8 recipe(s)`, `dead inputs: 97 of 97`, `TFC station inputs: 92 replacements, 6 recipes rewritten as JSON` | pass |
| FTB Quests, Gateways | 13 chapters, 345 quests, 21 reward tables; "Registered 22 gateways" | pass |
| firmages-core | `Boot: AgeState [dawn] matches the boot snapshot (FALLBACK_NO_MIRROR) ...; no reload`; warm-up reload 7,439 ms | pass |
| ERROR lines | 0 from FirmagesCore, ProgressiveStages, FTB Quests and KubeJS. The rest is the known noise: DISTXFORM, client mixins of azimuth, bits_n_bobs, simulated and woodencog, loot tables, recipe parse errors of tfcrf (44), woodencog (24) and createdeco (2), Sable, Polymorph, TF `dev_new_world`, trainutilities advancements | pass |

### Age-grant stall, before and after (runs A and B)

Same sequence in both worlds: `stage grant Alpha dawn` to `age_3` (one reload), then `age_4`, `age_5` and `age_6`
one at a time, each followed by its reload. "Grant ticks" are the ticks over 100 ms between the grant and the reload
start (60 ticks later); syncs are the `progressivestages:lock_sync` and `stage_sync` payloads each debug player
received in that window.

| Grant | Before: grant ticks (ms) | Before: syncs per player | After: grant ticks (ms) | After: coalescer line | After: syncs per player |
|---|---|---|---|---|---|
| age_4 | 3,016 ("Can't keep up! ... Running 2977ms") | 5 lock + 5 stage | 400 + 570 | "10 request(s) sent as 2 sync(s) in 482 ms" | 1 lock + 1 stage |
| age_5 | 1,735 | 3 + 3 | 299 + 646 | "6 request(s) sent as 2 sync(s) in 490 ms" | 1 + 1 |
| age_6 | 2,926 ("Running 2883ms") | 5 + 5 | 386 + 568 | "10 request(s) sent as 2 sync(s) in 483 ms" | 1 + 1 |

An RCON command runs between two ticks, so with the fix the grant itself (PS, `stages/grants.js`, FTB Quests, the
ceremony payload) shows up in one tick and the deferred syncs in the next one; the server thread is blocked about
0.95 to 0.97 s in total instead of 2.9 to 3.0 s (age_4 and age_6), and no "Can't keep up" warning comes from a grant
any more. Each player gets exactly one lock sync followed by one stage sync per grant; the reload then sends its own
full sync (1 + 1 per player, the duplicate lock sync skipped: the counter in the `Age reload ... finished` line grows
by 2 per reload). Reloads: before 7,264 / 6,810 / 7,017 ms, after 6,932 / 6,717 / 6,788 ms. On the shrine path
(run C) the prayer completes inside a tick, so the grant and the flush land in the same tick: 1.25 to 2.17 s there
under the host load above, again 1 + 1 syncs per player at every one of the nine grants.

### Shrine ladder (run C)

Heart at 0 200 0, rings 0 to 8 by `build_shrine.py --rings 0-8 --platform` (550 commands; `shrine status` valid
ring 8, all rings complete); dawn and age_0 by `stage grant`; tiers 0 to 8 from the shrine: offering by `debug use`,
rites (firestarter on the heart, the bell, `build_shrine.py --rings N --rites N` for 2 to 5, Chorus for 6 and 8,
`time set midnight` for 7 and 8), prayer by `debug pray`.

| Grant | Path | Coalescer | Syncs per player (grant / reload) | Reload (ms, under host load) |
|---|---|---|---|---|
| dawn, age_0 | `stage grant` (two commands, two ticks) | 4 requests -> 2 syncs | 2+2 / 1+1 | 11,827 |
| age_1 | tier 0, Alpha | 4 -> 2 | 1+1 / 1+1 | 17,721 |
| age_2 | tier 1, Alpha | 8 -> 2 | 1+1 / 1+1 | 13,027 |
| age_3 | tier 2, Alpha | 4 -> 2 | 1+1 / 1+1 | 13,090 |
| age_4 | tier 3, Alpha | 10 -> 2 | 1+1 / 1+1 | 17,918 |
| age_5 | tier 4, Alpha | 6 -> 2 | 1+1 / 1+1 | 19,447 |
| age_6 | tier 5, Alpha | 10 -> 2 | 1+1 / 1+1 | 21,402 |
| age_7 | tier 6, Beta and Alpha | 4 -> 2 | 1+1 / 1+1 | 21,495 |
| age_8 | tier 7, Alpha | 4 -> 2 | 1+1 / 1+1 | 23,478 |
| age_9 | tier 8, Beta and Alpha | 4 -> 2 | 1+1 / 1+1 | 20,440 |
| warm reloads at age_9 | `firmages reload` x3 | none | 0 / 1+1 | 21,936 / 20,282 / 22,205 |

Every Age had exactly one "Shrine prayer heard", one `Ceremony FULL` and one "Age reload starting". End state:
`shrine status` valid ring 8, intact, awakened 9, relics 9, shrine grants age_1 to age_9; `firmages ages` 11 unlocked.
The reload times are two to three times those of runs A and B (6.7 to 7.7 s) and of the earlier ladders (6.5 to
8.0 s); they follow the host load, not the pack, and are no basis for the 10 s limit. The warm reloads need a re-run
on an idle host.

### Osmium spot check (run C)

| Check | Observed | Result |
|---|---|---|
| age_4 | `firmages:ore/normal_native_osmium/granite` and `rich_native_osmium/gabbro` placed: not `#tfc:prospectable`; Alpha (steel pickaxe, `debug break`) gets `tfc:rock/loose/granite` (the override's `drop_as`) | pass |
| age_5 (after tier 4) | both blocks `#tfc:prospectable`; mined: one `firmages:ore/normal_native_osmium` and one `rich_native_osmium`, both with `tfc:heat` | pass |
| Small piece | `firmages:ore/small_native_osmium` not prospectable at age_5, the same as TFC's own `tfc:ore/small_native_copper` | pass |
| Mekanism ladder (age_9 world, 3x path) | Purification Chamber with 3 rich pieces and oxygen: 3 `mekanism:clump_osmium` (`firmages:purifying/native_osmium_rich`, 1:1); Crusher, 3 clumps: 3 dirty dust; Enrichment Chamber, 3 dirty dust: 3 dust; Energized Smelter, 3 dust: 3 `mekanism:ingot_osmium` (with `tfc:heat`) | pass |

The machines were placed by `/setblock`; energy, oxygen and the inputs were written by `data merge` (Mekanism 10.7
keys `items`, `energy_containers`, `chemical_tanks`), each step with fresh items of the previous step's output id
and count, the outputs read from the block entity. No pipes and no player GUI.

### Checks (run C, age_9)

| Check | Observed | Result |
|---|---|---|
| `firmages selftest all` | 44 passed, 0 failed | pass |
| `firmages recipes audit` | 33,677 recipes, 468 dropped (disabled), 0 extraction errors | pass |
| `poc_analyze.py` (`fa_dump`, `fa_dump_full`: 33,209 recipes) | 82 checks, all PASS (C-M included) | pass |
| `validate_quests.py` | 1291 object ids, 0 errors, 0 warnings | pass |
| `gen_textures.py --check` | 68 referenced textures, 0 errors, 0 warnings | pass |
| `gen_stage_locks.py --check` / `--registry` | exit 0; the registry refresh from the age_9 dump rewrote 0 files; ore overrides 1,304 (age_5 95) | pass |

The dump has 7 recipes more than the one of "Osmium vein and Ore Tooltips removal" (33,202): the
`create_dragons_plus:sandpaper_polishing` recipes listed missing there (`ad_astra/mars_stone`, `mercury_stone`,
`moon_stone_stairs` and `minecraft/blackstone_wall` among them) are back. Create Dragons Plus builds them at runtime,
so that gap was run-to-run, not caused by the osmium change.

### License, credits, release

- `LICENSE` (MIT, 2026, PhoenixFlyy; the same text as `mod/firmages-core/LICENSE`) at the repo root, named in
  `README.md` and in the description's license section; `.packwizignore` already lists it, so it is not served.
- `gen_credits.py`: still 191 mods (190 CurseForge, 1 own); the credits line now names 0.6.3; `md2html.py` rerun.
- `dev/cf/changelog-0.6.2.md` is now `changelog-0.6.3.md` with a 0.6.3 entry (grant freeze fix, license file).
- `release.ps1 -Version 0.6.3`: exit 0; `FirmamentAges-0.6.3.mrpack` 468.6 MB, `-curseforge.zip` 39.1 MB,
  `-server.zip` 506.4 MB (478 entries, 177 jars in `mods/` like the test server, no backslash in entry names), sha256
  list; `index.toml` unchanged, only the version line of `pack.toml`.
- `export_cf.py`: `FirmamentAges-0.6.3-curseforge-upload.zip` 0.9 MB, 6 swaps, manifest "Firmament Ages" 0.6.3,
  Minecraft 1.21.1, `neoforge-21.1.252`, **190 files**, all required, no duplicate project id; overrides hold one
  jar, `overrides/mods/firmages-core-0.6.2.jar`, sha1 equal to `mods/firmages-core-0.6.2.jar` and to the
  `gradlew build` output.
- `UPLOAD-ANLEITUNG.md`, `LOKAL-TESTEN.md`, `modpack-fields.md` and `curseforge-readiness.md` name the 0.6.3 pack
  files; firmages-core stays 0.6.2.

### Review of the commits since 6dd74ee

- Files: 125 changed. Binaries are only the shipped `mods/firmages-core-0.6.2.jar` (sha1 `1aa35a8a…` = the
  `gradlew build` output; 0.6.1 removed) and PNG assets (7 CurseForge images in `dev/cf/`, 8 osmium textures in
  `kubejs/assets/`). Nothing from `tools/`, `test-server/`, `dev/exports/` or the mod's build folders; no
  password, token or key in the added lines.
- `packwiz refresh` after the last commit: no change to `index.toml` or `pack.toml`.
- Trailers: all 11 commits end with a Co-Authored-By line; 65ad3d9, 37f9f69, 04ab72f and e0cf849 name "Claude
  Fable 5.1", the others "Claude Opus 5.5" (the model of the session that wrote them). Left as is.
- Claims of the earlier sections against this run's logs: KubeJS startup 3/3 and server 37/37 with 0 errors; the
  age_5 override rows (95, total 1,304); prospecting off at age_4 and on at age_5; ore blocks dropping their
  piece with `tfc:heat`; 82 PASS with C-M; the upload zip with 190 manifest files and only the firmages-core jar in
  the overrides. All hold. The GameTest claim of 4961701 ("All 29 required tests passed",
  `stageBatchSyncsOncePerPlayer` 10 requests for 2 players -> 1 lock + 1 stage sync each) is in the GameTest log of
  that build and matches the pack (10 requests -> 2 syncs at age_4 and age_6). The decision log's "2,6 s mit 2
  Spielern" for the old stall is 2.9 to 3.0 s on today's pack (age_4 and age_6); the row now carries
  the measured numbers.

### Open points

- Reload times on an idle host: the ladder and the three warm reloads at age_9 ran under a concurrent game and say
  nothing about the 10 s limit (6.7 to 7.7 s in runs A and B before the game started). Re-run
  `firmages reload` x3 at age_9 when the host is idle.
- Needs a client: the packet order after the coalescing (stage updates, toasts and the ceremony payload of the
  grant tick before the one lock and stage sync), the osmium textures and names, the disguise as raw rock.
- Upload (Felix): firmages-core 0.6.2 first, then `FirmamentAges-0.6.3-curseforge-upload.zip` and the server pack
  (`dev/cf/UPLOAD-ANLEITUNG.md`). Merge `dev` into `main`, tag and push are his.
- `debug.allowSimulate` in `test-server/config/firmages-server.toml` is back to `false` after the run.

## Item Ages by reachability

Date: 2026-10-13, branch `dev` on top of 6896165. Felix's decision 7: an item takes the later of its `age_map` Age
and the first Age a team can reach it; an item no source yields is disabled. Fresh test world (`run_server.py
--wipe-world`), test server synced from `packwiz serve` (`-g -s server`, "Finished successfully"), debug players Alpha
and Beta in one FTB party, `debug.allowSimulate` on for the run only (back to `false`).

### What changed

- **`dev/derive_ages.py`** (new). Walks the recipe graph once per Age, dawn to age_9, with the walk of the R- checks
  (`poc_analyze.make_walk`, now module level like `recipe_records`; the poc_analyze output on the old dump stayed
  byte-identical after that move). Per Age a recipe fires when its station exists, its ingredients are reached, every
  output has an `age_map` Age of that Age or earlier (the gate) and, for crafting-menu types only (crafting, smithing,
  stonecutting), its ProgressiveStages recipe lock is owned. Vanilla cooking needs a furnace, blast furnace, smoker,
  campfire, Create encased fan or Mekanism energized smelter. Start set: the R- start set plus what `--scan` reads from
  the jars and `kubejs/data` into `dev/data/world_sources.json` (loot tables, loot modifiers, world generation per
  dimension, structure templates with their chests and spawners, mob spawns): entity loot at the mob's Age (spawn
  biomes, structures, mod Age, then the spawn lock and mob ladder), chest loot at its structure's Age, block loot of
  generated blocks; quest and reward-table rewards at their chapter Age; the boss gates' payouts. Hand-kept mechanics
  in the script: vanilla interactions and special recipes (bottles, buckets of every registered fluid, brewing,
  fireworks, maps), copper weathering and waxing, AE2 condenser, blaze burner, Create schematics, packages and toolbox
  dyeing, HNN data models, IE core samples, Roads and Roofs concrete drying, Ad Astra planet trees, Apotheosis gems.
- **Structures**: a structure template yields only the blocks of its own namespace (a Twilight castle yields castle
  bricks; the crafting table a mod's tower is furnished with is no source of crafting tables). Vanilla overworld
  structures never generate in the TFC overworld (their biome tags hold no TFC biome).
- **Output**: `dev/age_map.toml` section `# >>> generated:derived >>>` with `[derived]` (1,784 items moved later) and
  `[derived_hidden]` (3,898 never reachable items). `gen_stage_locks.py` applies them (`rule_age`, then the later
  derived Age; hidden items go to `age_items/disabled`) and writes `kubejs/data/c/tags/item/hidden_from_recipe_viewers.json`,
  so EMI and JEI hide the never reachable items; they are not ProgressiveStages-locked, like `[hidden]`.
  `dev/data/derived_ages.json` lists every changed item with the step that first reaches it; `--explain ITEM` for any.
- **Known gaps** (`KNOWN_GAPS` in the script): the item stays at its rule Age and counts as a source; each is an open
  point below.
- **Checks**: `validate_quests.py` accepts a never reachable item as an icon (display only, not stage-locked); G-3
  counts a byproduct as late only when it is also later than the recipe's ingredients; C-3f leaves out the eldritch
  miner (never reachable); the A- checks no longer crash on a disabled result.

### Numbers

| | Before | After |
|---|---|---|
| Items (registry) | 23,537 | 23,537 |
| `age_items/dawn` | 5,279 (1,180 vanilla) | 4,263 (197 vanilla: TFC-made glass, wool, dyes, terracotta, world drops) |
| `age_items/disabled` | 640 | 4,538 (3,898 never reachable) |
| Vanilla items moved later | – | 891 (age_0 103, age_1 145, age_2 255, age_3 332, age_4 4, age_5 28, age_7 1, age_8 9, age_9 14) |
| Never reachable, top namespaces | – | createcasing 527 (vanilla-wood variants, encased forms), woodencog 473 (unused unfinished parts), tfcastikorcarts 329 (AFC woods without recipes), createframed 259, MA 222 (compat seeds), occultism 213 (ritual dummies, spawn eggs), MMP 176 (absent metals), theurgy 170, vanilla 166 (spawn eggs, creative blocks, vanilla tools) |

`derive_ages.py --check` on the dump taken after the reload (the gate had already dropped the recipes of hidden
items) reports the section up to date, so the derivation is a fixed point; `gen_stage_locks.py --check` exit 0.

### Proof on the test server

| Check | Observed | Result |
|---|---|---|
| Boot (fresh world) | `Done (12.705s)`; KubeJS server 38/38 scripts, 0 errors, 0 warnings; no tag load error; ERROR lines only the known noise (recipe parse errors of createdeco and woodencog, Sable catwalk, TF `dev_new_world`, trainutilities advancements) | pass |
| Vanilla items in Dawn | live `item_tags.json`: `minecraft:crafting_table` and `furnace` in `age_items/age_9` (only source: desert villages of the Stargate planets), `iron_ingot`, `torch` and `bucket` age_2, `chest` age_3, `stick` dawn, `barrier` disabled; `fa_dump` row `twilightforest:berry_torch` now carries `item_lock progressivestages:age_2` (was empty) | pass |
| EMI hiding | `progressivestages.toml` `[emi] enabled = true`, `show_locked_recipes = false`: the PS EMI plugin removes every stack the stage files lock (`tag:firmages:age_items/<Age>`). Never reachable items: live tag `c:hidden_from_recipe_viewers` 4,552 entries, all 3,898 derived ones in it | pass (client look open) |
| `poc_analyze.py` (`fa_dump`, `fa_dump_full` at age_9, 32,263 recipes) | **82 checks, all PASS** (R- signature chains, R-F, R-L included) | pass |
| `validate_quests.py` | 1291 object ids, 0 errors, 0 warnings | pass |
| Reloads at age_9, 2 players (`firmages reload`) | 9,082 / 8,760 / 8,612 ms on a busy host (other sessions running). A/B in the same server, tags swapped between runs: old tags 8,531 / 8,625 / 8,404 / 8,416 / 8,543 / 8,416 ms (mean 8,489), new tags 8,408 / 8,578 / 8,574 / 8,590 / 8,341 / 8,499 ms (mean 8,498) | no change from this work; the 7.7 s mark needs an idle host |

### Open points

- Known gaps kept at their rule Age: Mystical Agriculture has no source at all (ores off in
  `mysticalagriculture-common.toml`, nothing yields prosperity shards or inferium essence; the Information Age quest
  asks for the essence); the Industrial Age quest "Any Advanced TFC Tech machine" asks for formed multiblocks (no item
  form) and the fleshing machine (needs `component_electronic`, first reached at age_5); the Create Dragons Plus blaze
  upgrade template has no data-visible first source; Apothic Enchanting's table needs diamonds (age_4) while the
  Arcane Age quest's Blaze Enchanter is made from it. Each needs a design call (quest or recipe change).
- `mekanism:module_base` needs `c:nuggets/bronze`, whose only member is the Create Big Cannons bronze scrap (no
  source), so all MekaSuit modules are hidden. Pre-existing; needs a recipe change.
- Sources only code knows stay unmodelled unless listed in the script; cosmetic variants obtainable only that way (IE
  shaders, colored plastics, createframed colored glass) are hidden. If something hidden turns up in play, add its
  source to `INTERACTIONS`, `CODE_DROPS` or `CODE_WORLD` in `dev/derive_ages.py` and rerun.
- Needs a client: EMI in Dawn shows no crafting table, furnace or torch; hidden items are absent from the index; quest
  icons of hidden items render.
- Rerun after any recipe, loot or mod change: `fa_dump` + `fa_dump_full` at age_9, `derive_ages.py` (with `--scan`
  after jar changes), `gen_stage_locks.py`, sync, reload.

## Immersion

Date: 2026-10-13, branch `dev` after merging the firmages-core immersion branch (c27a7c7, 65e94bd) and the quest
theme branch (fa572ed, 7196ab5, bce26e7) on top of the screens (a4bc0c8..6896165) and item-Ages (6e543c1..3857d07)
commits. firmages-core 0.7.0, pack 0.7.0. Integration fixes: the HUD hold key moved from Left Alt to H (Left Alt is
Create's tool menu and toolbelt, Railways and Copycats in the live keymap), `gen_textures.py --check` skips
`textures/gui/` (quest theme art). Felix's decisions (1) to (7) of 2026-10-13 are the scope; client-only behaviour
is listed in `dev/client-test-brief.md` (section 0.7.0) for Felix.

### Build and install

| Check | Observed | Result |
|---|---|---|
| `gradlew build` (0.7.0) | 85 JUnit tests, 0 failed (10 new in `ImmersionTest`) | pass |
| `gradlew runGameTestServer` | "All 32 required tests passed" (welcome once per player, status follows Ages and config; the TFC test skips itself without `-PgametestTfc`, its opt-in run passed on the branch) | pass |
| Shipped jar | `mods/firmages-core-0.7.0.jar` byte-identical to the build output (sha1 `55a7f7b2…`), 0.6.2 removed, index refreshed | pass |
| Clean client install | empty folder, packwiz-installer-bootstrap `-g -s client` from `packwiz serve`: 610/610, "Finished successfully", no "must be downloaded manually"; `verify_install.py dir --side client`: 604 expected, 0 missing, 0 extra, 0 hash mismatch. FancyMenu 3.9.14, Konkrete 1.9.9, Melody 1.0.10, Drippy Loading Screen 3.1.5, Drippy Early Loading Module 3.1.5 and Certain Questing Additions 1.2.0.4 all came from CurseForge, so none of them joins the swap list | pass |
| Test server sync | quest folder deleted, `-g -s server`: 610/610 "Finished successfully", `firmages-core-0.7.0.jar` in `mods/`; config hash differences only in files the mods rewrite on start (values kept: `show_lock_message = false`, `spawnBook = false`, KubeJS `web_server` `enabled = false`) | pass |

### Server proof (fresh world, debug players Alpha and Beta, one FTB party)

A temporary KubeJS probe in `test-server/kubejs/server_scripts/zz_probe/` (removed after the run, not part of the
pack) drove `ServerPlayer.doTick()` for the debug players (they have no ticking connection; verified by a Luck effect
counting down 204 ticks in 10 s), read TFC `IPlayerInfo` and evaluated chapter visibility.

| Check | Observed | Result |
|---|---|---|
| Boot | `Done (12.927s)`; KubeJS startup 3/3, server 39/39 (38 plus the probe), 0 errors; FTB Quests "2 chapter groups, 13 chapters, 346 quests, 21 reward tables"; 22 gateways; ERROR lines only the known noise (DISTXFORM, client mixins of azimuth, bits_n_bobs, simulated, woodencog, loot tables of dndecor and railways, recipe parse errors of tfcrf and woodencog, Sable catwalk, Polymorph EMI module, TF `dev_new_world`) | pass |
| `announceAdvancements` | `gamerule announceAdvancements` → false (from `kubejs/server_scripts/firmages/gamerules.js`) | pass |
| Join packets (each player) | `lock_sync`, `stage_sync`, `firmages:age_status` (age=dawn, goalItem empty, recipeViewer=false, blockInfo=false), `firmages:welcome`, `firmages:shrine_sync` | pass |
| Join-time system chat | none from mods. Remaining lines: vanilla "Beta joined the game" and the op echo of the RCON/debug command ("[Rcon: Debug player Beta joined]", only because the debug players are ops) | pass |
| Welcome once per player | first join: one `firmages:welcome` each; leave and rejoin: none; server restart and rejoin: none (flag in the player data) | pass |
| ProgressiveStages lock message | Alpha in Dawn uses a crafting table (age_9) on the ground: not placed, no chat or action bar line | pass |
| TFC thirst, 10 min | 12,000 driven ticks (17:51:47 to 18:01:51): thirst 100 for both players. Control: `tfc.disableThirst = false` and 40 exhaustion → thirst 60 and food 13 within 3 s; back to `true` → thirst 100 on the next tick | pass |
| TFC nutrition floor | Alpha's modifier fell to 0.793 (below TFC's start 0.85) after the control; `damage Alpha 2` cost 2.353 HP (= 2/0.85), not 2.522 (= 2/0.793) | pass |
| Age ladder dawn → age_9 (`stage grant`) | one `age_status` per player and grant with the Age's offering (`hearthstone`, `sky_disc`, `steel_heart`, `arcane_keystone`, `pressure_core`, `humming_core`, `data_matrix`, `star_chart`, `quantum_core`, empty at age_9) and recipeViewer/blockInfo true from age_0; one SHORT ceremony per grant; reloads 11,369 (age_0) / 8,314 / 8,243 / 8,659 / 8,126 / 8,629 / 8,551 / 8,134 / 8,544 / 8,166 ms | pass |
| Hidden chapters (client rule) | visible chapters: Dawn 3 (Welcome, The Firmament, Dawn), age_0 4, age_1 5, age_2 6, age_3 7, age_4 8, … age_9 13; later Ages absent, not greyed. Evaluated as the client does (`hasStageClient` of the chapter's `progressivestages_required_stage`) with the server's stages. The server path of ProgressiveStages 3.0.6 (`hasStageForServerLogic`) answers "visible" for every chapter: it calls `getCurrentPlayer` on the `Optional` that `ServerQuestFile.getInstance()` returns (javap) and falls back to true; only the client view matters for the book | pass (client look open) |
| Theme and Certain Questing Additions | client-only resources and a client-side mod: nothing on the server to load; `validate_quests.py` 1294 ids, 0 errors, 0 warnings; `gen_quest_art.py` reproduces every committed theme file byte for byte | client open |
| `firmages selftest all` (age_9) | 44 passed, 0 failed | pass |
| `poc_analyze.py` (`fa_dump`, `fa_dump_full` at age_9, 32,263 recipes) | **82 checks, all PASS** | pass |
| `gen_textures.py --check` | 68 referenced textures, 0 errors, 0 warnings | pass |
| `gen_stage_locks.py --check`, `derive_ages.py --check` | exit 0; `generated:derived` up to date | pass |

### Remaining chat lines (not on join)

- Every Age grant posts the ProgressiveStages stage line to the team ("Age 0: Stone Age - fire tamed. Clay and
  copper await."). It is in-world text, but carries the "Age N:" stage name; the FULL shrine ceremony adds its own
  title. It is the stage's `unlock_message` in `config/progressivestages/stages/age_N.toml`; left as is (design text),
  emptying those lines leaves only the ceremony.
- Vanilla join/leave lines ("Beta joined the game") stay.

### Open points

- Everything a client draws (loading screens, title, pause menu, HUD, welcome screen, EMI/Jade hiding, quest theme,
  CQA shaders, TFC displays, Default Options) is unverified until Felix's client run (`dev/client-test-brief.md`).
- Pause menu: FancyMenu's `pause_screen_layout.txt` restyles the vanilla buttons while firmages-core hides Mods and
  Advancements and moves the rows below up by 24 px in `ScreenEvent.Init.Post`; the layout sets no positions, but
  whether FancyMenu re-applies cached widget positions after that move is only visible in the client.
- EMI and Jade open at age_0 (Stone Age) as Felix's brief says ("Stone Age"); the brief's id `age_1` is the Bronze
  Age in this pack (decision row of the core branch).
- The test world was switched to peaceful during the run (zombies killed the ticking debug players); the next run
  wipes it.

## Immersion follow-up

Date: 2026-10-13, branch `dev` at 0a1df0e (pack 0.7.0, firmages-core 0.7.0). Two review findings of the Immersion
run, both listed for Felix in `dev/client-test-brief.md`; decisions in `dev/decisions-while-away.md`.

### Changes

- Industrial Age quest "Advanced TFC Tech" (Logistics strand): its task asked for any of five Advanced TFC Tech
  machine items. Thresher, grist mill, beamhouse and power loom are formed multiblocks without an item form, the
  fleshing machine needs `immersiveengineering:component_electronic` (first reached at age_5), so the quest could not
  be done in its Age and blocked the "Backfill Line" keystone and the chapter goal. No Age fix applies (there is no
  item to move), so the task is now an observation of the formed thresher (`advancedtfctech:thresher`), as the IE
  multiblock quests do. The thresher template (`structure/multiblocks/thresher.nbt` in the ATFC 3.3 jar) is steel
  sheet metal, light engineering blocks, one redstone engineering block and a hopper; grist mill and power loom also
  need `immersiveengineering:storage_steel`, which is disabled. The five ATFC rows left `KNOWN_GAPS` in
  `dev/derive_ages.py`: the four multiblock items are now never reachable and hidden like IE's, the fleshing machine
  is age_5. `poc_analyze.py` has the thresher parts and `advancedtfctech:pirn` (the new quest icon) in the R-age_4
  chain.
- `ftbquests:loot_crate_opener` and `ftbquests:task_screen_configurator` are in `[hidden]` of `dev/age_map.toml`.
  The task screens stay (Doc 08 section 9.1); every other FTB Library and FTB Quests utility item was already hidden
  or disabled.

### Checks

| Check | Observed | Result |
|---|---|---|
| `derive_ages.py`, `gen_stage_locks.py` | final per Age: age_4 1,370 (was 1,375), age_5 400 (399), age_1 2,732 (2,734), disabled 4,544 (4,538); `--check` of both exit 0 against the new dump | pass |
| Boot (fresh world, `run_server.py --wipe-world`) | `Done (13.310s)`, 201 mods; KubeJS startup and server 0 error lines; FTB Quests "13 chapters, 346 quests, 21 reward tables"; ERROR lines only the known noise (DISTXFORM, azimuth and bits_n_bobs client mixins, loot table parse errors, recipe parse errors of woodencog and tfcrf, Sable catwalk, TF `dev_new_world`) | pass |
| Age ladder for the dump | debug player Alpha, `stage grant` age_0 … age_9 one by one (second fresh world; `debug.allowSimulate` on for the run, off again) | pass |
| `poc_analyze.py` (`fa_dump`, `fa_dump_full` at age_9, 32,261 recipes; the two hidden items' recipes are gone) | **82 checks, all PASS**; `R-age_4 firmages:pressure_core`: goal, ingredients and the 33-item chain (thresher parts included) reachable at age_4, 14,150 items reached | pass |
| `validate_quests.py` | 1290 object ids, 0 errors, 0 warnings | pass |
| Release `release.ps1 -Version 0.7.0` | `FirmamentAges-0.7.0.mrpack` 524,199,590 B, `-curseforge.zip` 47,104,836 B, `-server.zip` 537,039,781 B (server sync 611/611 "Finished successfully"), sha256 list written | pass |
| `dev/cf/export_cf.py` | `FirmamentAges-0.7.0-curseforge-upload.zip` 7,013,808 B, 196 manifest files, 6 swaps, overrides jar only `mods/firmages-core-0.7.0.jar` | pass |
| Export content | upload zip and server zip carry the thresher observation and `loot_crate_opener` in `age_items/disabled` | pass |

### Open points

- Needs a client: the quest completes when a player looks at a formed thresher; the two hidden items are absent from
  EMI.
- Grist mill and power loom: whether IE's multiblock matcher accepts a TFC steel block (via `c:storage_blocks/steel`)
  in place of the disabled `immersiveengineering:storage_steel` is unchecked. If not, both are unbuildable; the quest
  no longer depends on them, but the machines are design content (needs a world test or a template change).

## ATFC multiblocks

Date: 2026-10-13, branch `dev` at 88ab82a (pack 0.7.0). Follow-up to the open point of "Immersion follow-up": can
the four Advanced TFC Tech multiblocks be built in the Industrial Age with pack materials? Decision in
`dev/decisions-while-away.md`.

### Templates (Advanced TFC Tech 3.3, `data/advancedtfctech/structure/multiblocks/*.nbt`)

| Multiblock | Size | Blocks (count) | Disabled in the pack |
|---|---|---|---|
| Thresher | 3x3x3 | `sheetmetal_steel` 14, `light_engineering` 5, `rs_engineering` 1, `minecraft:hopper` 1 | none |
| Grist mill | 4x3x3 | `steel_scaffolding_standard` 9, `sheetmetal_steel` 3, `light_engineering` 4, `rs_engineering` 1, `slab_sheetmetal_steel` 4, `storage_steel` 1 (the trigger block), `minecraft:hopper` 1 | `immersiveengineering:storage_steel` |
| Power loom | 3x3x5 | `steel_scaffolding_standard` 13, `light_engineering` 3, `heavy_engineering` 3, `sheetmetal_steel` 5, `rs_engineering` 1, `steel_fence` 1, `storage_steel` 1 | `immersiveengineering:storage_steel` |
| Beamhouse | 4x3x4 | `steel_scaffolding_standard` 10, `light_engineering` 7, `sheetmetal_iron` 10, `slab_sheetmetal_iron` 6, `heavy_engineering` 1, `rs_engineering` 1, `fluid_pipe` 3 | none |

All other blocks are in `age_items/age_4` (the hopper in age_2). ATFC registers no matcher of its own.

### How IE 12.4.2 matches

`BlockMatcher.matches` runs the global predicates of `IEMultiblocks.init`: equal block, plus a tag predicate that
allows a found block when expected and found share one of IE's tags (`c:storage_blocks/<metal>` and
`c:sheetmetals/<metal>` of every IE metal, steel and aluminium scaffolding, treated wood and slabs, steel and aluminium
fences). `c:storage_blocks/steel` (block tag) holds both `immersiveengineering:storage_steel` and
`tfc:metal/block/steel` (TFC data), so the TFC block is accepted. But `TemplateMultiblock.disassemble` puts the
**template** state back, and a broken part drops the template block.

### World test

Fresh world (`run_server.py --wipe-world`), debug player Alpha, `firmages ages simulate grant` and `stage grant`
age_0 ... age_4 (`debug.allowSimulate` on for the run, off again). Each template was set unrotated with
`dev/build_multiblock.py` (new: template from the jar, `--sub immersiveengineering:storage_steel=tfc:metal/block/steel`),
Alpha held the Engineer's Hammer, looked north (yaw 180) and used the trigger block (`firmages debug use`).

| Step | Observed | Result |
|---|---|---|
| Form, jar templates, TFC steel block in place of `storage_steel` | thresher, grist mill, power loom, beamhouse: `use -> SUCCESS`, the trigger position is the ATFC multiblock block (4/4) | pass |
| Teardown, jar templates | grist mill: breaking the hopper part turned the TFC block position into `immersiveengineering:storage_steel`; power loom: breaking the steel part with a steel pickaxe dropped `immersiveengineering:storage_steel`; mining that block drops the same disabled item | **fail** (disabled block handed out) |
| Fix | `dev/gen_atfc_templates.py` writes pack copies of `grist_mill.nbt` and `power_loom.nbt` with `tfc:metal/block/steel` in the palette (byte-level swap of the palette string, re-parsed) to `kubejs/data/advancedtfctech/structure/multiblocks/`; `--check` exit 0 | done |
| Form after the fix (restart, same world) | all four formed again (4/4) | pass |
| Teardown after the fix | grist mill: hopper part broken, the steel position is `tfc:metal/block/steel`; power loom: steel part broken with a pickaxe, drop `tfc:metal/block/steel` | pass |
| Boot after the fix | KubeJS startup and server 0 error lines (`gear_rule.js` without the `loot_crate_opener` swap) | pass |
| `poc_analyze.py` (age_0 ... age_9 granted, `fa_dump` 32,260 recipes, `fa_dump_full`) | **83 checks, all PASS**; new R-M: four templates, every block obtainable at age_4 (grist mill and power loom from the pack copy). Without the pack copies R-M fails on `storage_steel` ("belongs to disabled") | pass |

### Open points

- Needs a client: the IE manual / ATFC multiblock preview of grist mill and power loom shows the TFC steel block
  (IE syncs the server template to the client).
- The same teardown drop affects IE's arc furnace (TFC-IE Crossover's template), bucket wheel and excavator and
  Immersive Petroleum's coker unit and pumpjack, which also contain `immersiveengineering:storage_steel`; they form
  with the TFC block through the tag but give the disabled block back when taken apart. Not changed here.

## IE templates

Date: 2026-10-13, branch `dev` at dd8a8d4 (pack 0.7.0). Follow-up to the open point of "ATFC multiblocks": every
IE-style multiblock template of the pack, not only the ATFC ones. Decision in `dev/decisions-while-away.md`.

### Scan (`dev/gen_multiblock_templates.py`, replaces `dev/gen_atfc_templates.py`)

36 template files in four jars (`data/<ns>/structure/multiblocks/*.nbt`: Immersive Engineering 25, Immersive Petroleum
6, Advanced TFC Tech 4, TFC + IE Crossover 1), 35 paths: the Crossover's `immersiveengineering:arcfurnace` (TFC fire
bricks) overrides IE's (the jar whose mod is not the namespace owner wins). The only palette block in
`age_items/disabled` or `c:hidden_from_recipe_viewers` is `immersiveengineering:storage_steel`, in 7 templates:

| Template | Source | `storage_steel` | Pack copy |
|---|---|---|---|
| `advancedtfctech:grist_mill`, `power_loom` | ATFC | 1, 1 | existing, byte-identical |
| `immersiveengineering:arcfurnace` | TFC + IE Crossover | 6 | new |
| `immersiveengineering:bucket_wheel` | IE | 9 | new |
| `immersiveengineering:excavator_full` | IE (manual preview of excavator + wheel) | 9 | new |
| `immersivepetroleum:cokerunit` | IP | 1 | new |
| `immersivepetroleum:pumpjack` | IP | 2 | new |

All copies swap `storage_steel` for `tfc:metal/block/steel`, nothing else. `immersiveengineering:excavator` itself has
no steel block. `--check` exits 0; it fails on a missing, stale or unneeded copy and on a disabled or hidden block
without a canonical replacement.

### World test

Fresh world (`run_server.py --wipe-world`), debug player Alpha, `firmages ages simulate grant age_5`, `stage grant`
dawn ... age_5 (`debug.allowSimulate` on for the run, off again). Each template built unrotated at y 180 with
`dev/build_multiblock.py --sub immersiveengineering:storage_steel=tfc:metal/block/steel` (arc furnace from the
Crossover jar), Engineer's Hammer, yaw 180, `firmages debug use` on the trigger (offsets from the multiblock classes:
arc furnace 2 0 4, bucket wheel 3 3 0, excavator 1 1 5, coker unit 4 1 4, pumpjack 1 1 4). Round A breaks a non-steel
part with a steel pickaxe (`debug break`), round B re-forms and breaks a steel part. After each break every template
position was tested, item entities within 60 blocks and Alpha's inventory counted.

| Multiblock | Form (A, B) | Round A: steel positions after teardown | Round B: steel part broken | `storage_steel` anywhere |
|---|---|---|---|---|
| Arc furnace (Crossover) | SUCCESS, SUCCESS | 6/6 `tfc:metal/block/steel` | drop `tfc:metal/block/steel` x1, other 5/5 TFC | none |
| Bucket wheel | SUCCESS, SUCCESS | 9/9 TFC | drop TFC x1, other 8/8 TFC | none |
| Excavator | SUCCESS, SUCCESS | no steel position; disassembled (0 formed parts left) | non-steel part (none in template) | none |
| Coker unit | SUCCESS, SUCCESS | 1/1 TFC | drop TFC x1 | none |
| Pumpjack | SUCCESS, SUCCESS | 2/2 TFC | drop TFC x1, other 1/1 TFC | none |

The drop proves the server loads the pack copy over the Crossover override (KubeJS data sits above the mod packs).
Boot: KubeJS startup and server 0 error lines.

### R-M (`poc_analyze.py`)

R-M now covers all 35 templates the server loads (pack copy before jar), grouped by the multiblock's Age: [mods] Age
of the namespace, `MULTIBLOCK_AGE` lifts the IE MV/HV machines to age_5 (arc furnace, sawmill, mixer, bottling machine,
excavator, excavator_full, bucket wheel, diesel generator, lightning rod, radio tower). Each block must be obtainable
at that Age and none disabled or hidden. IE's chunk loader is in `MULTIBLOCK_UNBUILT` (needs
`minecraft:calibrated_sculk_sensor`, which no Age reaches; chunk loading is FTB Chunks) and R-M checks it stays
unbuildable at age_9.

| Check | Result |
|---|---|
| R-M age_4 (18 templates) | PASS |
| R-M age_5 (16 templates) | PASS |
| R-M unbuildable (chunk loader) | PASS |
| All checks (existing `fa_dump` with dawn ... age_9, recipes unchanged by this work) | **85 checks, all PASS** |
| Pack copies moved away (negative test) | R-M age_4 and age_5 FAIL on `storage_steel` |

### Open points

- Needs a client: the IE manual previews (arc furnace, bucket wheel, excavator, coker unit, pumpjack) show the TFC
  steel block.
- `immersiveengineering:slab_storage_steel` (16 in the coker unit) is a second steel slab next to
  `tfc:metal/block/steel_slab`; it is obtainable at age_4 from `#c:storage_blocks/steel` and not disabled, so the
  template keeps it.
- IE's chunk loader multiblock cannot be built (no sculk sensor); whether it should be disabled outright is a call for
  Felix.

## Decisions 2026-10-13 late

Date: 2026-10-13, branch `dev` from 36d47a0 (pack 0.7.1, firmages-core 0.7.1). Felix's answers 3, 4, 5, 6, 7 and 9.
The test server was not used (Felix is testing on it): proof is JUnit, GameTests, javap and the static checks on the
`fa_dump` of 2026-10-13 19:29 (copied to a scratch folder). Design calls in `dev/decisions-while-away.md`.

### What changed

| Decision | Change |
|---|---|
| 3: Tab stays the player list | `simulated.keyinfo.rotate_mode` (Create Simulated Physics Staff, default Tab) on Comma |
| 4: Y stays the Cataclysm ability | `key.cataclysm.chestplate_ability` (default Y) on Apostrophe |
| 5: Jade from the Stone Age, EMI from the Bronze Age | firmages-core 0.7.1: `ui.jadeFromAge` default `age_0`, `ui.emiFromAge` default `age_1`; the pack ships no `firmages-server.toml` |
| 6: one steel slab | `tfc:metal/block/steel_slab` and its exposed, weathered and oxidized stages in `[hidden]` and `HIDDEN_ITEMS`; `immersiveengineering:slab_storage_steel` stays (`age_4`) |
| 7: no IE chunk loader | `immersiveengineering:chunk_loader` in `[hidden]` and `HIDDEN_ITEMS`; the template stays unbuildable |
| 9: MekaSuit modules | `mekanism:module_base` from four `#c:ingots/bronze` (was `#c:nuggets/bronze`, no source), `recipes/age_6/information_age.js` |

### Keys

Mapping names from the jars: `SimKeys` (Create Simulated, jar-in-jar of Create Aeronautics 1.3.2) registers
`simulated.keyinfo.rotate_mode` with key 258 (Tab); Cataclysm 3.33 `ModKeybind` registers `key.cataclysm.chestplate_ability`
with 89 (Y). A javap scan of the `KeyMapping` classes of all 197 client jars (CurseForge instance) found no default on
Comma (44) or Apostrophe (39) outside screen-internal keys (Create stock keeper, IE redstone connector, Ars spell book);
`keybindings.txt` uses neither, with or without a modifier. Plain keys after the change: only G is shared (Curios and
GuideME, by design).

### Checks

| Check | Result |
|---|---|
| `gradlew build` (JUnit) | 85 tests, 0 failures |
| `gradlew runGameTestServer` (generated config removed first, so the run took the new defaults: `emiFromAge = "age_1"`, `jadeFromAge = "age_0"`) | "All 32 required tests passed"; `statusFollowsAgesAndConfig` now also asserts the two defaults. KubeJS `boss_fallback.js` errors (no Gateways in the GameTest run) as in the 18:37 run |
| javap `mods/firmages-core-0.7.1.jar` | `DEFAULT_EMI_FROM_AGE` = `AgeId.AGE_1`, `DEFAULT_JADE_FROM_AGE` = `AgeId.AGE_0`; `neoforge.mods.toml` version 0.7.1; jar byte-identical to `build/libs` |
| `derive_ages.py` on the simulated dump (below) | modules out of `[derived_hidden]`: base and 10 modules `age_6`, 18 `age_7`, 3 plus the 4 Evolved Mekanism modules `age_8`; Mekanism Generators' solar and geothermal modules stay disabled. `[derived_hidden]` 3,902 -> 3,865, `[derived]` 1,783 -> 1,804, `[hidden]` 519 -> 524, disabled tag 4,544 -> 4,512 |
| `gen_stage_locks.py --check`, `derive_ages.py --check` (simulated dump), `validate_quests.py` (1290 ids), `gen_textures.py --check` (68 textures), `gen_multiblock_templates.py --check` (35 templates) | all exit 0 |
| `poc_analyze.py` on the simulated dump | **86 checks, all PASS** (new: R-L age_6 `mekanism:module_base`; C-Gv: no module needs vanilla gear without a recipe) |
| `poc_analyze.py` on the unchanged dump (negative test) | 83 PASS, 3 FAIL as expected: G-1 (the TFC slab recipes are still loaded there), A9 (the hidden tag lacks the four slabs), R-L age_6 (no module base recipe) |

Simulated dump: the gate drops every recipe whose output is disabled, so the module recipes were missing from the dump
and `derive_ages.py` alone would have kept the modules hidden. The copy gets the 36 jar recipes of the Mekanism and
Evolved Mekanism modules with the pack's edits (module base above; `gear_rule.js` swaps: wrought iron sword blade,
pickaxe and hoe heads, steel greaves and pickaxe, axe and shovel heads, `#c:tools/shear`, black steel sword blade), loses
the four recipes that make the TFC steel slabs (TFC grid, three Create Dragons Plus sandpaper polishings) and gets the
new `HIDDEN_ITEMS` in `c:hidden_from_recipe_viewers`: what the server does on its next reload.

### Needs the server or a client

- Next server run: `fa_dump` + `fa_dump_full` at `age_9`, then `poc_analyze.py` (86 PASS expected) and
  `derive_ages.py --check` ("up to date" expected). In the dump: `mekanism:module_base` with `#c:ingots/bronze`,
  every module recipe present, no recipe makes a TFC steel slab, `c:hidden_from_recipe_viewers` holds the slabs.
- Client: EMI absent in Dawn and the Stone Age, present after `stage grant @s age_1` without relog; Jade from `age_0`;
  Controlling "Conflicts" without Tab and Y on a fresh profile; the slabs and the Resonanz Observer absent in EMI.

## Mystical Agriculture veins (decision 8A)

Date: 2026-10-13, branch `fb2/ma-veins` from `dev` 21d6ae3 (pack 0.7.1). Felix's feedback decision 8A: Mystical
Agriculture had no ore source (its own generation is off for the single ore model), so its three ores get TFC-style
veins, hidden until the Information Age like the osmium vein. No server run (Felix is testing on the test server):
proof is the static tools on a simulated copy of the `fa_dump` the last workflow used. Design calls in
`dev/decisions-while-away.md`.

### What changed

| Part | Change | Where |
|---|---|---|
| Ore blocks | 34 KubeJS blocks like TFC's gem ores (one grade per rock): `firmages:ore/inferium/<rock>` in all 21 TFC rocks, `firmages:ore/prosperity/<rock>` in the 13 igneous and metamorphic rocks; model `tfc:block/ore` with MA's own overlay (`mysticalagriculture:block/<ore>_ore`), render type `cutout_mipped`, TFC ore hardness, block tags `c:ores` (TFC prospecting and collapse) and `c:ores/<ore>` | `kubejs/startup_scripts/mystical_veins.js` |
| Loot | MA's loot copied per block: silk touch gives the block, otherwise 2-4 inferium essence or 1-4 prosperity shards, fortune bonus, explosion decay | `kubejs/data/firmages/loot_table/blocks/ore/{inferium,prosperity}/` |
| Veins | `firmages:vein/inferium` (cluster, rarity 24, size 20, density 0.3, y -32..100), `firmages:vein/prosperity` (rarity 32, size 20, density 0.25, y -64..30), `firmages:vein/soulstone` (rarity 16, size 24, density 0.7, y -64..70, basalt only: 95 `mysticalagriculture:soulstone`, 5 `mysticalagriculture:soulium_ore`, MA's default soulium chance); all three in `tfc:in_biome/veins` | `kubejs/data/firmages/worldgen/`, `kubejs/data/tfc/tags/worldgen/` |
| Prospecting | the firmages blocks via `#c:ores`, soulium ore via MA's `c:ores`, soulstone added to `tfc:prospectable`; `prospecting.js` removes all of them while `age_6` is locked (they are in `age_blocks/age_6`) | `kubejs/data/tfc/tags/block/prospectable.json` |
| Age map | `[ore_families]` `firmages:ore/inferium/{rock}` and `firmages:ore/prosperity/{rock}` = `age_6`; new table `[ore_blocks]` for a mod's own block in one host rock: soulstone and soulium ore, `age_6`, basalt | `dev/age_map.toml`, `dev/gen_stage_locks.py` (reads `[ore_blocks]`, keeps its ids in a `--registry` refresh) |
| Disguise, miners | 36 `[[ores.overrides]]` rows in `age_6.toml` (each block shown and dropped as its host rock), 36 blocks in `age_blocks/age_6`; the Digital Miner blacklist and OreGuard follow that tag | generated by `gen_stage_locks.py` |
| Derivation | `derive_ages.py`: the ore and deposit blocks of the Age map give their block loot from their Age (TFC places veins through a placed-feature tag the world scan does not follow); the two MA entries left `KNOWN_GAPS` | `dev/derive_ages.py`, `dev/data/world_sources.json` (37 entries: 34 loot tables, 3 features) |
| Quest | Information Age side quest "Mystical Agriculture" (same id) now asks for 32 inferium essence, 8 prosperity shards and 8 soulstone and says where the veins lie; the entry text names the new veins (osmium dropped there: it is visible from `age_5`) | `dev/gen_quests.py`, `config/ftbquests/quests/` |
| Check | `poc_analyze.py` C-MA (below) | `dev/poc_analyze.py` |

### Checks

Simulated dump: a copy of the module dump of the last workflow (`fa_dump` of 2026-10-13 19:29 plus the Mekanism module
recipes), with the 34 new blocks in `registries.json` and the 38 Mystical Agriculture jar recipes the gate had
dropped for never-reachable outputs (soulstone family, witherproof blocks, awakening altar and pedestal, essence
vessel, soul glass, ...; conditions evaluated from dump evidence, KubeJS removals applied, 20 more whose outputs stay
disabled removed again). That is what the server loads after the change.

| Check | Result |
|---|---|
| `derive_ages.py --check` on the unchanged module dump, before the change (archive of HEAD) | up to date (baseline) |
| `derive_ages.py` on the simulated dump | `[derived]` 1,804 unchanged; `[derived_hidden]` 3,865 -> 3,839: 26 Mystical Agriculture items reachable (soulstone and its 14 building blocks, soulium ore, soul dust, witherproof block, bricks and glass, awakening altar and pedestal, essence vessel, soul glass, blank skull, experience capsule); nothing else moved. Inferium essence and prosperity shards now reached through the veins (`--explain`: "ore vein block firmages:ore/inferium/andesite (age_6)"), soulium dust through soulium ore |
| `gen_stage_locks.py` | age_items: `age_6` 1,445 -> 1,505 (+34 ore block items, +26 above), `disabled` 4,512 -> 4,486, every other Age unchanged; `age_blocks/age_6` 0 -> 36; ore overrides 1,304 -> 1,340 (`age_6`: 36); `c:hidden_from_recipe_viewers` 3,865 -> 3,839 |
| `gen_stage_locks.py --check`, `derive_ages.py --check` (simulated dump), `validate_quests.py` (1292 ids, 0 errors, 0 warnings), `gen_textures.py --check` (68 textures, 0 errors) | all exit 0 |
| `poc_analyze.py` on the simulated dump (`--server-dir test-server`, read only) | **87 checks, all PASS** (new: C-MA) |
| `poc_analyze.py` C-MA on the unchanged pack and dump (negative test) | FAIL as expected: three veins missing, soulstone and its cobble disabled |
| `node --check` on the startup script; every JSON under `kubejs/data` parses; `git diff --check` | pass |

C-MA: the three vein JSONs exist and are in `tfc:in_biome/veins`; each replaces one TFC raw rock; every placed block is
registered (dump), sits in its host rock, is in `age_blocks/age_6` and in no other Age, and has an `age_6` override
that shows and drops exactly that rock; the block items, inferium essence, prosperity shards, soulium dust, soulstone
and cobbled soulstone are `age_6`; MA's own ore generation stays off; 21 inferium and 13 prosperity blocks placed.

### Needs the server or a client

- Integrator boot (after `packwiz refresh`): `fa_dump` + `fa_dump_full` at `age_9`, then `poc_analyze.py` (87 PASS
  expected) and `derive_ages.py --check` ("up to date" expected; if the real dump differs from the simulation, run
  `derive_ages.py` and `gen_stage_locks.py` and commit). `gen_stage_locks.py --registry test-server/local/firmages`
  must keep the 34 new ids and soulstone and soulium ore in `blocks` (no diff in `dev/data/registry.json`).
- Worldgen, fresh world, after a Chunky pregen around spawn and `save-all flush`:
  `python dev/scan_regions.py --x <spawn x> --z <spawn z> --radius 256 --match firmages:ore/inferium/ --match firmages:ore/prosperity/ --match mysticalagriculture:`
  Expected: `firmages:ore/inferium/*` in many rock ids (thousands of blocks, more than prosperity),
  `firmages:ore/prosperity/*` only in igneous and metamorphic rock ids, `mysticalagriculture:soulstone` and
  `mysticalagriculture:soulium_ore` at about 19 to 1 (zero only if no basalt lies in the radius; then scan a basalt
  region), and no `mysticalagriculture:inferium_ore`, `prosperity_ore` or their deepslate forms.
- Prospecting on a placed block (`setblock` of `firmages:ore/inferium/granite`, then `execute if block ... #tfc:prospectable`):
  no at `age_5`, yes after `firmages ages simulate grant age_6`; same for `mysticalagriculture:soulstone`.
- Client: below `age_6` a vein looks and drops like its rock; from `age_6` the inferium and prosperity blocks show
  the rock with MA's ore overlay (no black pixels) and drop essence or shards; soulstone shows in basalt.

## Feedback 2 (2026-10-13)

Date: 2026-10-13, `dev` from 21d6ae3 (pack 0.7.1). Felix's second client feedback, worked on in five branches and
merged here with `--no-ff` in this order: `fb2/menus` (title and pause menu), `fb2/tfc-tabs` (TFC inventory tabs by
Age), `fb2/voicechat` (Simple Voice Chat removed), `fb2/ma-veins` (Mystical Agriculture veins, section above),
`fb2/quest-screen` (quest paragraph spacing). Conflicts only in the docs (`dev/client-test-brief.md`,
`dev/decisions-while-away.md`, SPEC §18.7): both sides kept, SPEC §18.7 lists the tab keys, the tab bits and
`questParagraphGap`. Then pack 0.7.2 and firmages-core 0.7.2. Design calls of the branches in
`dev/decisions-while-away.md`; root causes and the quest-view variants A and B in
`Modpack-Planung/research-raw/fb2-quest-screen.md`.

### Build and static checks

| Check | Observed | Result |
|---|---|---|
| `gradlew build` (firmages-core 0.7.2, merged tree) | BUILD SUCCESSFUL; JUnit 89 tests, 0 failures, 0 errors (85 before plus 4 tab tests); the jar carries `SwitchInventoryTabMixin`, `InventoryTabAccessor`, `ViewQuestPanelMixin`, `TextFieldAccessor`, `MenuButtons` | pass |
| `gradlew runGameTestServer` (generated `firmages-server.toml` moved away first) | "All 33 required tests passed" (`tfcTabsFollowAges` included); the new config holds `calendarTabFromAge = "age_0"`, `climateTabFromAge = "age_2"`, `nutritionTabFromAge = "never"`, `fieldGuideTabFromAge = "never"` | pass |
| `python -I dev/check_menus.py` | all checks pass (version line `Firmament Ages 0.7.2` = `pack.toml`; 1920x1080 and 2560x1440 at GUI 2, 3 and auto; every id in the FancyMenu 3.9.14 jar) | pass |
| `tools/packwiz.exe refresh` | 659 index entries, only under `config/`, `defaultconfigs/`, `kubejs/`, `mods/` (no `.wt`, no `.claude`); `simple-voice-chat.pw.toml`, the voicechat default config and `firmages-core-0.7.1.jar` gone; `firmages-core-0.7.2.jar`, `config/create-client.toml`, the 34 MA loot tables, 3 vein features and `mystical_veins.js` in | pass |
| Static tools on the simulated MA dump (`scratchpad/maveins/dump`) | `poc_analyze.py` 87 PASS; `derive_ages.py --check` up to date; `gen_stage_locks.py --check` 0 files; `validate_quests.py` 1292 ids, 0 errors, 0 warnings; `gen_textures.py --check` 68 textures, 0 errors; `gen_multiblock_templates.py --check` 35 templates, up to date | pass |

### Server proof (fresh world)

Test server synced with packwiz-installer-bootstrap `-g -s server` from `packwiz serve` (659/659, "Finished
successfully"; `voicechat-neoforge` and the old `firmages-core-0.7.0.jar` removed by the installer), then one boot with
`run_server.py --wipe-world --hold`. Debug player Alpha, `difficulty peaceful`, `debug.allowSimulate` on for the run
only. World spawn -752, -3200.

| Check | Observed | Result |
|---|---|---|
| Boot | `Done (12.296s)`, 206 mod files with 199 mods; KubeJS startup 4/4 and server 38/38, 0 errors, 0 warnings; warm-up reload 7786 ms | pass |
| ERROR lines | none from FirmagesCore, KubeJS, ProgressiveStages, FTB Quests or Mystical Agriculture; the rest is the known noise (DISTXFORM, azimuth, bits_n_bobs, woodencog and simulated client mixins, loot tables of dndecor, railways, create_connected, createcasing, more_immersive_wires and extendedae, tfcrf and woodencog recipe parse errors, createdeco placard, Sable tag physics, TF `dev_new_world`, trainutilities advancements) | pass |
| TFC tabs on the wire | `firmages:age_status` to Alpha: `tabs=0` in Dawn, `1` (calendar) at age_0 and age_1, `3` (calendar and climate) from age_2 to age_6 | pass |
| Prospecting gate | `setblock` of `firmages:ore/inferium/granite`, `mysticalagriculture:soulstone`, `firmages:ore/prosperity/basalt`; `execute if block ... #tfc:prospectable` at age_5: "Test failed" for inferium and soulstone; after `stage grant Alpha age_6`: "Test passed" for all three | pass |
| `fa_dump`, `fa_dump_full` at age_9 | 32,331 recipes (0 without a readable result), 3,496 item tags; 32,331 encoded, 0 not encodable | pass |
| `derive_ages.py --check` on the fresh dump | "generated:derived is up to date"; final per Age as in the simulation (age_6 1,505, disabled 4,486) | pass |
| `poc_analyze.py` on the fresh dump | **87 checks, all PASS** (C-MA included) | pass |
| `gen_stage_locks.py --registry test-server/local/firmages` | rewrote 0 files; `dev/data/registry.json` unchanged (the 34 vein blocks, soulstone and soulium ore were already in it) | pass |
| Worldgen at spawn (Chunky radius 256, 1,089 chunks, `save-all flush`, `scan_regions.py`, 812 full chunks) | inferium 15,938 (gabbro 14,217, granite 1,721), prosperity 10,518 (granite 6,418, gabbro 4,100); no basalt within the radius, so no soulstone; no `mysticalagriculture:inferium_ore`, `prosperity_ore` or deepslate forms | pass |
| Worldgen, eight patches (Chunky radius 64 at 2,000 and 4,000 blocks east, west, north and south of spawn) | basalt in three patches; soulstone 8,477 with 434 soulium ore at 1248, -3200 and 747 with 38 at -752, 800 (19.5 : 1 and 19.7 : 1); prosperity in basalt at 3248, -3200 (1,371) | pass |
| All nine areas together (the three `setblock` blocks subtracted) | inferium 24,184 in 11 rocks (andesite, basalt, chalk, chert, diorite, dolomite, gabbro, granite, marble, quartzite, schist); prosperity 18,261 in 8 rocks, all igneous or metamorphic (basalt, diorite, gabbro, gneiss, granite, marble, quartzite, schist); soulstone 9,224 to soulium ore 472; no MA ore of MA's own generation | pass |
| Cleanup | `stop`, exit code 0; `test-server/world` deleted (Felix starts a fresh Dawn world); `whitelist.json`, `ops.json`, `server.properties` byte-identical to before (the server had rewritten the last two, restored from the copy); `config/firmages-server.toml`: `allowSimulate = false` again, only the four new tab keys with their defaults added | pass |

`locate biome` finds nothing under TFC's biome source, so basalt had to be found by pregenerating patches. Basalt is
rare near this spawn: soulstone needs a basalt region, which may be thousands of blocks away.

### Needs a client (Felix, `dev/client-test-brief.md`, 0.7.2 Nachtrag)

- Title and pause menus at 1920x1080 and 2560x1440, GUI 2, 3 and auto; the Mojang copyright line stays (decision open).
- TFC tabs per Age without gaps, also in the Calendar and Climate screens and the creative inventory.
- MA vein overlay without black pixels, disguise below age_6, veins findable with the prospector in one session.
- Quest paragraph gap 14 px, `questParagraphGap` applied after reopening a quest; choice A or B for the cut-off
  fullscreen quest view (not fixed yet).

## Round B (2026-10-13)

Date: 2026-10-13, `dev` from 4346658 (pack 0.7.2). Felix's answers on "Firmament Ideen 2", worked on in six branches and
merged here with `--no-ff` in this order: `rb/removals` (Ping Wheel, Crash Assistant, TFCGenViewer removed; check U-1),
`rb/shaders` (Iris, Complementary Reimagined r5.9.3 on by default, MakeUp Ultra Fast, Enhanced Celestials 2, Dynamic
FPS, AllTheLeaks, BadOptimizations), `rb/hardship` (beds from age_2, torches, More Darkness and LambDynamicLights,
hidden coordinates, blood moon gate, Cave Dweller ReEvolved), `rb/core` (Jade from the Bronze Age, Cave Dweller gate,
EC2 join silence), `rb/quest-window` (quest book as a 75 % window) and `rb/title` (Constellation title screen).
Conflicts: `dev/decisions-while-away.md` (all rows kept), `index.toml` and `pack.toml` (refreshed afterwards), SPEC
§18.7 to §19 (18.8, 18.9, then 19; §18.7 lists `jadeFromAge = "age_1"`, `caveDweller` and `questWindowPercent`) and
`dev/check_menus.py` (shader and slideshow checks both kept). `FirmagesMixinPlugin` merged without conflict (9 pairs in
`Map.of`). Then pack 0.8.0 and firmages-core 0.8.0.

Integration fixes:

- **One Cave Dweller gate.** `rb/hardship` shipped `kubejs/server_scripts/firmages/cave_dweller.js` (cancel the join from
  a fixed `age_2`), `rb/core` the configurable `CaveDwellerService`. The script would have overruled
  `caveDwellerUntilAge = "never"`; it is gone, the mod gate stays.
- **Network protocol "2"** (`FirmagesNetwork`, SPEC §11): `firmages:age_status` grew in 0.7.2 and 0.8.0. NeoForge
  21.1.252 refuses a version mismatch at negotiation even for optional payloads (`NetworkComponentNegotiator`, javap),
  so an old client gets a clear message.
- **`grants.js` real-player check** (found by the boot below): `player.getUUID()` is hidden from KubeJS scripts and
  threw a TypeError on every login and Age grant, so `reducedDebugInfo` never dropped to false and the code after
  `reconcile()` in the grant callback (FTB Chunks quota at age_4) never ran. Now `!player.isFake()` (KubeJS
  `PlayerKJS`, instanceof NeoForge `FakePlayer`). **Not yet proven in a boot** (one boot only in this round).

### Build and static checks

| Check | Observed | Result |
|---|---|---|
| `gradlew build` (firmages-core 0.8.0, merged tree) | BUILD SUCCESSFUL; JUnit 100 tests, 0 failures, 0 errors (93 of `rb/core` plus 7 `QuestWindowTest`); the jar carries `mixin/ftbq/*` (5 mixins) and `hardship/CaveDweller*`; rebuilt after a javadoc edit: byte-identical (sha1 9db5788f) | pass |
| `gradlew runGameTestServer` (generated `firmages-server.toml` moved to `firmages-server-6.toml.bak` first) | "All 34 required tests passed" (`caveDwellerGoneFromItsAge` new); selftest core 17/0, gate 16/0; new config: `jadeFromAge = "age_1"`, `[hardship] caveDwellerUntilAge = "age_2"` | pass |
| `gradlew runGameTestServer -PgametestTfc` | 34 tests, 21 passed, 13 failed: the known shrine and gate failures of this mode (`consecrationCycle`, `shrineRitualEndToEnd`, `unlockTwoAgesThroughReload`, `keystoneLending`, `recipeGateAtBoot`, `excavatorFilterStubMix`, `recipeCommandsRun`, `modExtractorsAndExcavatorAtBoot`, `oreGuardDrops`, `oreGuardBreak`, `kubejsOreTagsAtBoot`, `gatheringAndFinalBoss`, `awakenedRingMigrates`); the TFC tests ran against TFC and passed: `tfcTabsFollowAges` (with the server guard against TFC's tab packet) and `tfcThirstAndNutrition` | pass (expected failures) |
| `python dev/check_menus.py` | all checks pass: version line `Firmament Ages 0.8.0`; default shader pack `ComplementaryReimagined_r5.9.3.zip` has a metafile; shaders off in `firmages:origin` and the 11 Ad Astra dimensions; slideshow `firmament_constellation` 9 slides; every local file a layout names exists | pass |
| `tools/packwiz.exe refresh` | 682 index entries: `kubejs` 349, `mods` 207, `config` 120, `defaultconfigs` 4, `shaderpacks` 2; nothing outside the pack paths, every listed file exists; no Ping Wheel, Crash Assistant, TFCGenViewer, EC1, `cave_dweller.js`, old shrine slideshow or `firmages-core-0.7.2.jar` | pass |
| Mod count | 206 metafiles in `mods/` plus `firmages-core-0.8.0.jar` = **207 mods**; 2 shader packs (not mods); `gen_credits.py`: 207 credits (206 CurseForge after the swaps, 1 own) | pass |

### Server proof (fresh world)

Test server synced with packwiz-installer-bootstrap `-g -s server` from `packwiz serve` (682/682, "Finished
successfully"; EC1 jar and `firmages-core-0.7.2.jar` removed, the client-only mods skipped), deploy values set
(`config/tfc-server.toml` `torchTicks = 43200`, `config/firmages-server.toml` `jadeFromAge = "age_1"`), instance leftovers
`config/pingwheel.server.json` and `config/crash_assistant/` deleted. One boot: `run_server.py --wipe-world --hold 0`
with 49 console commands (15 s apart), debug player Alpha (creative), `debug.allowSimulate` on for the run only.

| Check | Observed | Result |
|---|---|---|
| Boot | `Done (12.885s)` (112.8 s wall clock, world creation included), 208 mod files with 200 mods; KubeJS startup 4/4 and server 39/39, 0 errors, 0 warnings; warm-up reload 8678 ms | pass |
| ERROR lines | none from FirmagesCore, Enhanced Celestials 2, Cave Dweller, AllTheLeaks or any Iris-related mod (the Iris family, Colorwheel, More Darkness, LambDynamicLights, YACL, Dynamic FPS and BadOptimizations are client-only and not on the server; no client-class load error names them); **5 KubeJS errors from `grants.js`** (TypeError `getUUID`, fixed above); the rest is the known noise (DISTXFORM, azimuth, bits_n_bobs, woodencog and simulated client mixins, loot tables of dndecor, railways, create_connected, createcasing, more_immersive_wires and extendedae, tfcrf, woodencog and createdeco recipe parse errors, Sable, Polymorph, TF `dev_new_world`) | pass after fix (fix unproven in a boot) |
| H5 `/gamerule reducedDebugInfo` | fresh world `true`; after Alpha's login `true`; after `stage grant Alpha age_1` **`true`** (expected `false`), also at age_2. Cause: the `grants.js` TypeError; fixed, not re-booted | **fail, fixed** |
| H8 blood moon gate (`/ec lunarForecast`) | fresh world, nobody online: 4 blood moons in the forecast (1d, 76d, 82d, super 39d); `/ec scheduleLunarEvent insert ...:blood_moon 3` adds one (gate waits without players); 9 s after Alpha joins: "removed 6 blood moon(s) from the forecast (no mob_2 yet)", forecast without blood moons; at age_1 a scheduled blood moon is removed within 1 s; `stage grant Alpha age_2`: `mob_2` granted, "lunar forecast rolled again with blood moons", forecast with 4 blood moons, a scheduled one stays 30 s later | pass |
| H12 Cave Dweller | before age_2: `execute at Alpha run summon cave_dweller:cave_dweller ~4 ~ ~4` "Summoned", `execute if entity` count 1; at age_2 (`caveDweller=false` in Alpha's `firmages:age_status`): count 0, a new summon is refused (`Cave Dweller refused at 340 67 1124 (hardship.caveDwellerUntilAge)` in debug.log; vanilla still prints "Summoned" because `tryAddFreshEntityWithPassengers` ignores a cancelled join). The first dweller was gone before age_2 (no "Removed" line), so the discard of loaded dwellers on the Age change is not shown here; it is covered by GameTest `caveDwellerGoneFromItsAge` | pass |
| H9 Jade | `firmages:age_status` to Alpha: `blockInfo=false` in Dawn and age_0, `blockInfo=true` from age_1 (`jadeFromAge = "age_1"`), `caveDweller=true` until age_1 and `false` at age_2, `tabs` 0/1/1/3 | pass |
| H3 torches | TFC has no config command (`net/dries007/tfc/common/commands`: no config); `config/tfc-server.toml` holds `torchTicks = 43200` and the server kept it | trust the file |
| `fa_dump`, `fa_dump_full` at age_9 (`firmages ages simulate grant age_3` .. `age_9` after the proofs) | 32,330 recipes (0 without a readable result), 3,496 item tags; 32,330 encoded, 0 not encodable; recipe audit written | pass |
| Reloads | age_0 8237, age_1 8026, age_2 8235 ms (Alpha online); age_3 to age_9 simulated without players 7760, 7361, 7402, 7477, 7661, 7381 and 7364 ms | pass (all below 10 s) |
| `derive_ages.py --check` | "generated:derived is up to date"; final per Age dawn 4,263, age_0 649, age_1 2,732, age_2 4,657 (the 16 beds), age_3 1,915, age_4 1,370, age_5 400, age_6 1,505, age_7 557, age_8 673, age_9 364, disabled 4,486 | pass |
| `gen_stage_locks.py --check` and `--registry test-server/local/firmages` | 0 files to rewrite; `dev/data/registry.json` unchanged (EC2 and Cave Dweller register no items) | pass |
| `validate_quests.py`, `gen_textures.py --check`, `gen_multiblock_templates.py --check` | 1292 ids, 0 errors, 0 warnings; 68 textures, 0 errors; 35 templates, up to date | pass |
| `poc_analyze.py` | 88 checks: 87 PASS, **1 FAIL: U-1** with 15 tags (expected; list below) | expected |
| Cleanup | `stop`, exit 0; `test-server/world` deleted (Felix starts a fresh Dawn world); `whitelist.json`, `ops.json`, `server.properties` byte-identical to before (the server had rewritten the last two, restored from the copy); `debug.allowSimulate = false` again; `firmages-server.toml` otherwise only gained `[hardship]`; after the `grants.js` fix one more sync (682/682) so the test server has it | pass |

U-1 (one visible item per unify tag; all 15 "not unified: material not in AU placeholders"), visible members:

| Tag | Visible items |
|---|---|
| `c:dusts/amethyst` | `moremekanismprocessing:dust_amethyst`, `occultism:amethyst_dust` |
| `c:dusts/saltpeter` | `immersiveengineering:dust_saltpeter`, `tfc:powder/saltpeter` |
| `c:dusts/wood` | `mekanism:sawdust`, `immersiveengineering:dust_wood` |
| `c:gems/amethyst` | `minecraft:amethyst_shard`, `tfc:gem/amethyst` |
| `c:gems/certus_quartz` | `ae2:certus_quartz_crystal`, `ae2:charged_certus_quartz_crystal` |
| `c:gems/quartz` | `minecraft:quartz`, `tfcreate:polished_quartz`, `tfc_ie_addon:mineral/quartz_shard` |
| `c:ingots/cast_iron` | `createbigcannons:cast_iron_ingot`, `tfc:metal/ingot/cast_iron` |
| `c:ingots/sterling_silver` | `tfc:metal/ingot/sterling_silver`, `occultengineering:sterling_silver_ingot` |
| `c:plates/andesite_alloy` | `createdeco:andesite_sheet`, `createcasing:andesite_sheet` |
| `c:rods/all_metal` | `immersiveengineering:stick_aluminum`, `tfc:metal/rod/wrought_iron`, `tfc:metal/rod/steel`, `immersiveengineering:stick_netherite`, `immersiveposts:stick_lead`, `immersiveposts:stick_constantan`, `immersiveposts:stick_electrum`, `immersiveposts:stick_uranium` |
| `c:rods/wooden` | `minecraft:stick`, `immersiveengineering:stick_treated`, 20 `tfc:wood/twig/*`, 21 `afc:wood/twig/*`, `beneath:wood/twig/crimson`, `beneath:wood/twig/warped` |
| `c:storage_blocks/alloys` | `evolvedmekanism:block_alloy_{infused,reinforced,atomic,hypercharged,subatomic,singular,exoversal}` |
| `c:storage_blocks/cast_iron` | `createbigcannons:cast_iron_block`, `tfc:metal/block/cast_iron` |
| `c:storage_blocks/slime` | `minecraft:slime_block`, `twilightforest:maze_slime_block` |
| `c:storage_blocks/sterling_silver` | `tfc:metal/block/sterling_silver`, `occultengineering:sterling_silver_block` |

### Open points

- Prove the `grants.js` fix in the next boot: `/gamerule reducedDebugInfo` false after `stage grant <player> age_1`, no
  KubeJS error on login or grant, FTB Chunks quota at age_4.
- U-1: the 15 tags above (polish stage: unify, hide or allowlist).
- `neoforge config showfile tfc SERVER` failed (the type is lower case); the TFC config path was not shown.
- Needs a client (Felix, `dev/client-test-brief.md`, 0.8.0 Nachtrag): shaders and FPS, title screen, quest window,
  darkness with and without shaders, F3, Jade, Cave Dweller sounds, removed mods.

## Polish after round B (2026-10-13)

Static only, no server boot. The round-B dump (`test-server/local/firmages`) predates these changes; for U-1 a copy
of it with the two tag removals and the new portal tag applied was checked as well.

| Check | Result | Verdict |
|---|---|---|
| U-1 root cause | The pack shipped no `config/almostunified/placeholders.json`; AU 1.4.2 only knew its 44 default materials. Now shipped: the defaults (read from the AU jar) plus amethyst, andesite_alloy, bismuth, cast_iron, certus_quartz, draconium, quartz, saltpeter, sterling_silver, wood; four new priority overrides (`dev/decisions-while-away.md`) | fixed |
| `poc_analyze.py` (round-B dump, tags as dumped) | 87 PASS, U-1 FAIL with 4 tags: `c:rods/all_metal`, `c:rods/wooden`, `c:storage_blocks/alloys`, `c:storage_blocks/slime` (the maze slime removal is not in this dump) | expected |
| `poc_analyze.py` (same dump with the tag edits of `unification.js` applied) | 87 PASS, U-1 FAIL with 3 tags: `c:rods/all_metal`, `c:rods/wooden`, `c:storage_blocks/alloys` (aggregate tags, open for Felix); R-age_2 reaches `minecraft:quartz` | open |
| Quest descriptions | `paragraphs()` joins each paragraph to one entry: 82 of 880 lang keys change, all `quest_desc`, each new entry equals its old paragraph joined; chapters, data and reward tables byte-identical | pass |
| `validate_quests.py` | 1292 ids, 0 errors, 0 warnings (portal tag `minecraft:quartz`, age_1, chapter age_2) | pass |
| `derive_ages.py --check` | up to date, per-Age counts as in round B; MA awakening altar, pedestal and essence vessel at age_6 (`--explain`) | pass |
| `gen_stage_locks.py --check`, `check_menus.py`, `gen_textures.py --check`, `gen_multiblock_templates.py --check` | 0 files; all checks pass; 68 textures, 0 errors; 35 templates, up to date | pass |
| packwiz | `refresh`: 683 entries; the seven changed pack files copied to `test-server` | pass |

Next boot: `fa_dump`, then `poc_analyze.py` (U-1 should list only the 3 aggregate tags), `tfcreate:quartz` should give
`minecraft:quartz` in `recipes.json`, and `derive_ages.py` may move the AU losers that lost every producer (for
example `tfcreate:polished_quartz`) to disabled; run `gen_stage_locks.py` after it.

## Clean client install after round B (2026-10-13)

Round B added 13 client-only CurseForge files (12 mod metafiles and the MakeUp shader pack); the test server sync
(`-s server`) skips all of them, so this run checks that packwiz-installer can fetch each one. No server boot.

| Check | Observed | Result |
|---|---|---|
| Clean client install, first run | empty scratch folder, packwiz-installer-bootstrap `-g -s client` from `packwiz serve`: stops at "Resolving CurseForge metadata" with "More Darkness: This mod is excluded from the CurseForge API and must be downloaded manually" (CF 1511006/8574545); nothing else named | **fail** |
| Swap | Modrinth has the same jar (`more_darkness-neoforge-1.21.1-2.0.1+1.21.1.jar`, sha1 `7f783520…` in the CF metafile and on Modrinth version `6jywYHaX`); `mods/more-darkness.pw.toml` is now that Modrinth metafile (`side = "client"` kept), the CF metafile moved to `dev/cf/swap/more-darkness.pw.toml`; `packwiz refresh` (683 entries) | done |
| Clean client install, second run | new empty folder, same command: 683/683, "Finished successfully", no "must be downloaded manually"; `verify_install.py dir --side client`: 677 expected, 677 found, 0 missing, 0 extra, 0 hash mismatch. From CurseForge: Iris Shaders, Colorwheel, Dynamic FPS, BadOptimizations, YetAnotherConfigLib, LambDynamicLights, Iris Block Compat, Ars Skyweave Shader Fix, Enhanced Celestials 2: Shader Support, MakeUp Ultra Fast; from Modrinth (swap): More Darkness, Iris Veil Compat, Complementary Reimagined | pass |
| `export_cf.py` | "swapped 9" (the eight mods plus Complementary), manifest 208 files, only `overrides/mods/firmages-core-0.8.0.jar` as jar | pass |
| `gen_credits.py` | 207 credits (206 CurseForge, 1 own), `modpack-description.md` unchanged (More Darkness credited with its CurseForge project) | pass |
| `check_menus.py` | all checks pass | pass |

Still open: no client has loaded the round-B client jars in NeoForge yet (the server skips them). That is Felix's
client test (`dev/client-test-brief.md`, 0.8.0 Nachtrag).

## 0.8.0 final checks (2026-10-13)

`dev` at eed4bd1 (pack 0.8.0, firmages-core 0.8.0), boot at 01:05 on 2026-10-14. Before it the two pack files changed
since the last sync were copied to `test-server` (quest lang with the Frontier Sigil text, the comment in
`defaultconfigs/tfc-server.toml`); `verify_install.py dir test-server --side server`: 0 missing, the remaining hash
mismatches are files the mods rewrite at start (FTB Quests, Cucumber, Cave Dweller and other configs).

One boot: `run_server.py --wipe-world --hold 300 --timeout 900 --wait 15` with 24 console commands,
`debug.allowSimulate = true` for the run only. Debug player Alpha (`firmages debug player join Alpha`, creative)
took every Age with `stage grant Alpha age_0` .. `age_9` and stayed online for all grants (round B simulated age_3 and
later without players).

| Check | Observed | Result |
|---|---|---|
| Boot | `Done (10.455s)` (94.0 s wall clock, world creation included), 208 mod files with 200 mods; KubeJS startup 4/4 and server 39/39 at the boot and each of the 11 reloads, 0 errors, 0 warnings; warm-up reload 7103 ms; `stop`, exit 0 (762 s in all) | pass |
| H5 `/gamerule reducedDebugInfo` (proves 67d3382) | fresh world `true`; after Alpha's join (Dawn) `true`; after `stage grant Alpha age_0` `true`; after `stage grant Alpha age_1` **`false`**; at age_9 `false` | pass |
| `grants.js` after `reconcile()` | at age_4 `ftbchunks admin extra_claim_chunks Alpha get`: "Alpha has 25 extra claimed chunks", `extra_force_load_chunks`: 25 (`FA.CHUNK_QUOTA`), so the grant callback runs to its end | pass |
| Blood moon gate (EC2) | on Alpha's join "removed 6 blood moon(s) from the forecast (no mob_2 yet)"; at age_2 "mob_2 reached, lunar forecast rolled again with blood moons" | pass |
| Reloads (Alpha online) | age_0 7012, age_1 6378, age_2 6339, age_3 6386, age_4 6236, age_5 6265, age_6 6282, age_7 6343, age_8 6217, age_9 6237 ms | pass (all below 10 s) |
| `firmages ages`, `firmages recipes audit` at age_9 | 11 Ages unlocked, 0 locked; Age index 19,085 items, 1,553 blocks, per Age as in round B; gate 33,671 recipes, 1,343 dropped (all `disabled`), 0 extraction errors; audit written | pass |
| `fa_dump`, `fa_dump_full` at age_9 | 32,328 recipes (0 without a readable result), 3,496 item tags; 32,328 encoded, 0 not encodable | pass |
| `logs/latest.log` | 0 `TypeError`; no ERROR or WARN line from firmages-core, Enhanced Celestials 2 or Cave Dweller and no stack frame of theirs (Cave Dweller only: WARN "Reference map 'cave_dweller.refmap.json' ... could not be read" at mixin setup). The rest is the known noise: DISTXFORM of client classes, client mixins of azimuth (1,603 FATAL lines), bits_n_bobs, simulated and woodencog, loot tables of dndecor, railways, create_connected, createcasing, more_immersive_wires and extendedae at every reload, recipe parse errors of tfcrf, woodencog and createdeco, Sable (`copycats:copycat_catwalk`), TF `dev_new_world`, the trainutilities advancements (also in the round-B log), Polymorph's EMI module | pass |
| Cleanup | `test-server/world` deleted; `debug.allowSimulate = false` again (`firmages-server.toml` byte-identical to before); `whitelist.json` byte-identical; the server rewrote `ops.json` (trailing newline) and `server.properties` (date comment, CRLF), both put back byte-identical from the copy taken before the boot, as in round B | pass |

Checks with the fresh dump:

| Check | Observed | Result |
|---|---|---|
| `poc_analyze.py` | 88 checks: 87 PASS, **1 FAIL: C-1a** (below). **U-1 PASS** (495 tags, 3 umbrella, 0 allowlisted), notes only: `c:rods/all_metal` (8 visible, one per metal), `c:rods/wooden` (45: stick, treated stick, 43 twigs, one per wood species), `c:storage_blocks/alloys` (7 Evolved Mekanism alloy blocks); R-age_2 reaches `minecraft:quartz` | U-1 pass, C-1a open |
| TFCreate quartz | `tfcreate:quartz` makes `minecraft:quartz`; no recipe makes `tfcreate:polished_quartz` | pass |
| `derive_ages.py --check` (first run) | out of date: 9 AU losers have no producer left and go to disabled: `tfcreate:polished_quartz`, `createbigcannons:cast_iron_ingot` and `cast_iron_block`, `occultengineering:sterling_silver_ingot` and `sterling_silver_block`, `immersiveengineering:dust_saltpeter`, `mekanism:sawdust`, `moremekanismprocessing:dust_amethyst`, `createcasing:andesite_sheet`; `occultism:amethyst_dust` (AU target of `c:dusts/amethyst`) back to its age_map Age age_3 (was age_6) | regenerated |
| `derive_ages.py`, `gen_stage_locks.py` | `generated:derived` and 6 tag files rewritten; final per Age dawn 4,263, age_0 649, age_1 2,732, age_2 4,655, age_3 1,914, age_4 1,367, age_5 400, age_6 1,502, age_7 557, age_8 673, age_9 364, disabled 4,495; then `derive_ages.py --check` up to date, `gen_stage_locks.py --check` 0 files; `poc_analyze.py` again: same result | pass |
| `validate_quests.py`, `check_menus.py`, `gen_textures.py --check`, `gen_multiblock_templates.py --check` | 1292 ids, 0 errors, 0 warnings; all checks pass; 68 textures, 0 errors; 35 templates, up to date | pass |
| `tools/packwiz.exe refresh` | 683 entries: `kubejs` 349, `mods` 207, `config` 121, `defaultconfigs` 4, `shaderpacks` 2; nothing outside the pack paths, every listed file exists, nothing from `dev/` (`.packwizignore` lists `dev/`) | pass |
| test-server | the 6 regenerated tag files copied; `verify_install.py`: 0 missing | pass |

C-1a (new): `create:crafting/curiosities/minecart_coupling`, `create:crafting/kinetics/mechanical_harvester` and
`create:crafting/kinetics/mechanical_plough` ask for `#c:plates/iron`. The requirement is not new: in the round-B dump
the same three recipes were `tfcreate:minecart_coupling`, `tfcreate:mechanical_harvester` and
`tfcreate:mechanical_plough` with the same iron plate, and C-1a only looks at `create:` ids. With `andesite_alloy` in the
AU placeholders, `create:andesite_alloy` inputs became `#c:ingots/andesite_alloy`, and the TFCreate twins are gone while
the Create ids are present (also for `mechanical_arm`, `steam_engine` and `pulse_timer`); that AU's duplicate handling
does this is inferred from the dumps, not read in its code. The three items are age_2 in the Age tags (first reachable
at age_2 through the iron plate), so the pack is consistent; whether they belong to the bronze andesite tier is open
(decision row).

### Open points

- C-1a: Felix decides (row in `dev/decisions-while-away.md`).
- The boot ran with the tag set before the regeneration; the 9 newly disabled items have no producing recipe, so the
  gate result does not change, and AU already hid them in EMI. Not re-booted.
- `dev/start_test_server.ps1` parses (PowerShell parser, 0 errors) and its paths resolve; it has not started a server
  (one boot only).
- `test-server/server.properties` still says `white-list=true`; the docs say the test server runs without whitelist and
  tell Felix to type `whitelist off` if it is still on.
- Needs a client (Felix, `dev/client-test-brief.md`, 0.8.0 Nachtrag).

## TFCRegistryAPI scan scope (2026-10-14, branch n1/core-registryapi)

firmages-core limits TFCRegistryAPI 1.2's enum scan to mod files that are or depend on `tfc` or `tfcregistryapi`
(SPEC §20, `[perf] limitTfcRegistryApiScan`, default true). No pack server was booted for this change.

| Check | Observed | Result |
|---|---|---|
| `gradlew build` | 107 unit tests, 0 failed (`RegistryScanScopeTest` 7); `fetchLibs` downloaded `TFCRegistryAPI-1.21.x-1.2.jar` from the CurseForge CDN, sha1 `9cf37256…` as in `mods/tfc-registry-api.pw.toml` | pass |
| `gradlew runGameTestServer` | "All 35 required tests passed"; `tfcRegistryApiScanScope` skips itself (no TFCRegistryAPI in the default run) | pass |
| `gradlew runGameTestServer -PgametestTfc` (now with TFCRegistryAPI) | log "TFCRegistryAPI enum scan limited to 2 of 17 mod files (mods that are or depend on [tfcregistryapi, tfc]): tfcregistryapi, tfc"; `tfcRegistryApiScanScope` passed (every TFC value of `Metal`, `Ore`, `Rock`, `SandBlockType`, `SoilBlockType`, `SoilBlockType.Variant`, `Wood` registered, woods under mod `tfc`); 35 tests, 13 failed: the same 13 shrine and gate tests as in round B (TFC replaces their vanilla stand-ins) | pass (expected failures) |
| First `-PgametestTfc` run, before directory mod files were dropped | TFCRegistryAPI crashed on `FileSystems.newFileSystem` of firmages-core's class folder (`ProviderNotFoundException`, after scanning `tfcregistryapi` and `tfc`); fixed in `TfcRegistryScan` (directories only exist in dev runs) | fixed |
| `/fa_dump` with the new `dump.js` (one-off in the `-PgametestTfc` run, temporary trigger script, not committed) | `registries.json`: 108 keys (every built-in registry, `entity_type` 292 ids) and `tfcregistryapi_enums` metal 28, ore 32, rock 21, sand 7, soil 14, soil_variant 8, wood 20; `check_registry_parity.py dump` of it against itself: PASS | pass |
| `check_registry_parity.py jars "<CurseForge instance>/mods" --dump test-server/local/firmages` (0.8.0 dump) | 290 jars (246 mod jars, 44 libraries), 83,680 classes; scope 20 mod jars (advancedtfctech, afc, beneath, c2tfc, firmages, firmalife, kubejs_tfc, precisionprospecting, rnr, tfc, tfc_ie_addon, tfc_support_indicator, tfcah, tfcastikorcarts, tfcbetterbf, tfcregistryapi, tfcrf, tfcvesseltooltip, tfcweldbutton, woodencog); every enum of the seven interfaces lies in the scope: TFC `Metal`, `Rock`, `SoilBlockType$Variant`, `Wood`, AFC `AFCWood`, `TreeSpecies`, Firmalife `FLMetal`, Beneath `Stem`, plus TFC `Ore`, `SandBlockType`, `SoilBlockType` (interfaces added by the API's own mixins); the 81 wood constants equal the woods of all 7 TFCAstikorCarts parts in the 0.8.0 dump (567 items) | pass |
| Negative test of the static check (roots reduced to `tfcregistryapi`) | 11 enums reported OUTSIDE, exit 1 | pass |
| `check_registry_parity.py dump` 0.8.0 vs a copy without the 7 `acacia_koa` cart items, one `tfcrf` recipe and with an extra key | DIFF item (7 removed, all `tfcastikorcarts`), each cart part "lost acacia_koa", tfcrf recipes 99 -> 98, `entity_type` "only after (not compared)", exit 1 | pass |

### Open points

- Pack server and client **[PoC]**: boot time and log size with the change, and the A/B dump: two boots of the same
  build at age_9 with `limitTfcRegistryApiScan = false` and `true`, `/fa_dump` each, then
  `python dev/check_registry_parity.py dump <false dump> <true dump>`. The 0.8.0 dump has only `item`, `block`,
  `fluid`; against it the script compares those three plus the cart and regrowth checks.

The A/B proof on the pack server is in "0.8.1 night run" below: same build, both dumps identical in all 166 keys.

## 0.8.1 night run (2026-10-14)

`dev` 70a14f5 (0.8.0) plus six branches merged with `--no-ff` (n1/logfix, n1/tiers, n1/configs-quests,
n1/core-registryapi, n1/core-ux, n1/title-glsl; conflicts only in `dev/decisions-while-away.md`, `SPEC.md` and
`ServerConfig.java`, all kept on both sides), then firmages-core 0.8.1. Felix's test server (world at age_9) was
stopped with `rcon.py stop`, synced with `packwiz-installer-bootstrap -g -s server` from `packwiz serve` (quest folder
deleted first; 1167 and then 1170 of 1170 entries, "Finished successfully"), and booted three times with
`run_server.py` (no `--wipe-world`, the world kept). `server.properties`, `ops.json` and `whitelist.json` are
byte-identical before and after; `config/tfc-server.toml` got `inferredMetals = ["ingots/iron", "double_ingots/iron"]`
by hand (defaultconfigs only fill a new file).

### Build

| Check | Observed | Result |
|---|---|---|
| `gradlew build` | 116 unit tests, 0 failed (`RegistryScanScopeTest` 7, `ToolRuleTest` 5, `TooltipTrimTest` 4 new) | pass |
| `gradlew runGameTestServer` (stale `run/gametest/config` moved away first) | "All 36 required tests passed"; `toolRuleStopsHandBreaking` ran (debug.log: "Break of Block{minecraft:oak_log} by test-mock-player refused"), `tfcRegistryApiScanScope` skipped itself; `age_status` carries `toolRule=true` in Dawn and `false` from age_0 on | pass |
| `gradlew runGameTestServer -PgametestTfc` | 36 tests, 23 passed, 13 failed: the known 13 of this mode (`consecrationCycle`, `shrineRitualEndToEnd`, `unlockTwoAgesThroughReload`, `keystoneLending`, `recipeGateAtBoot`, `excavatorFilterStubMix`, `recipeCommandsRun`, `modExtractorsAndExcavatorAtBoot`, `oreGuardDrops`, `oreGuardBreak`, `kubejsOreTagsAtBoot`, `gatheringAndFinalBoss`, `awakenedRingMigrates`). Passed with TFC loaded: both hardship tests (tool rule, Cave Dweller) and `tfcRegistryApiScanScope` ("scan keeps 2 of 18 mods: [tfc, tfcregistryapi]") | pass (expected failures) |
| `mods/firmages-core-0.8.1.jar` | copied from `build/libs`, 0.8.0 jar removed; `packwiz refresh`: 1170 entries (`kubejs` 845, `mods` 207, `config` 112, `defaultconfigs` 4, `shaderpacks` 2); mod count unchanged | pass |

### Boot loop

| Round | Setup | Server load (ModernFix) | Log lines | ERROR / FATAL / STDERR | `check_log.py` |
|---|---|---|---|---|---|
| before | Felix's 0.8.0 boot at 02:00 with the age_9 grant (`latest.log` copied before the stop) | 117.6 s | 160,009 | 15,975 / 1,645 / 2 | 6,230 remaining in 9 signatures, 11,384 in the open scan entry |
| 1 | 0.8.1, `limitTfcRegistryApiScan = true`, `fa_dump`, `fa_dump_full`, `firmages recipes audit` | 56.5 s (Done after 54.5 s wall clock) | 3,533 | 78 / 14 / 18 | 103 remaining in 26 signatures, then documented (below); `--warn`: 2 one-time lines after the upgrade (`firmages 0.8.0 -> 0.8.1`, `firmages-server.toml` corrected with the new keys) |
| 2 (A) | `limitTfcRegistryApiScan = false`, dumps | 75.2 s (Done 73.4 s) | 145,959 | 9,759 / 1,645 / 20 | 2 remaining (`LocalPlayer` DISTXFORM on `main`, only with the full scan) |
| 3 (B, final) | switch back to `true` (file restored byte-identical), dumps | 53.9 s (Done 52.2 s) | 3,523 | 76 / 14 / 18 | **0 remaining, exit 0; with `--warn` also 0** |

- Scan window (first to last `modloading-worker` error line): A 33.4 s, B 8.0 s. On the server the scan keeps 17 of
  178 mod files ("TFCRegistryAPI enum scan limited to 17 of 178 mod files ..."). Same build A/B: 21.4 s less load
  time. Against Felix's 0.8.0 boot (117.6 s) the difference is 63.7 s; that boot also parsed the 480 missing loot
  tables and the broken recipes at each reload, and its disk cache state is unknown.
- `check_registry_parity.py dump A B`: PASS, all 166 keys the same (item 23,572, block 17,319, fluid 759,
  entity_type 1,261, `tfcregistryapi_enums` 193: metal 30, ore 32, rock 21, sand 7, soil 14, soil_variant 8, wood 81);
  TFCAstikorCarts 567 items, 81 woods per part. Against the 0.8.0 dump (only item, block, fluid): PASS, no id removed.
  The script compared tfcrf ids also in keys only one dump has and failed on that; it now compares shared keys only.
- `age_status` is not logged on the dedicated server (only for debug players, see the GameTests).

Fixes found by the loop (commit 7c176ad):

- C-1a on the real dump: Create Connected's freewheel clutch, centrifugal clutch and item silo still asked for
  `#c:plates/iron`. The jar recipes use `create:iron_sheet` with a `create_connected:feature_enabled` condition, and
  `replaceInput` left them unchanged (the tiers branch predicted the moves from a computed dump). They are now
  datapack overrides under the same ids in `kubejs/data/create_connected/recipe/crafting/kinetics/` (condition kept,
  bronze plates, `#tfc:barrels`); the round 2 dump shows them.
- C-4j: the three repaired Wooden Cog welding recipes (red steel armour + copper diving piece -> netherite diving
  gear) are gear from gear and contradict the pack rule in `industrial_age.js` ("never from the copper piece"). They
  are removed by id there; the logfix overrides stay so that the jar files parse without an error.
- `log_noise.json`: the open entry `tfcregistryapi-class-scan` is gone. New noise entries, each with its reason:
  `tfcregistryapi-scan-residual` (about 90 lines: the API still loads every class of the 17 kept files, client
  classes of the TFC addons fail, azimuth's mixin into Create's client renderer fails; the API catches each one),
  `rhino-dump-declared-methods` and `rhino-dump-distxform` (33 lines, only when `/fa_dump` runs: Rhino cannot reflect
  item classes with client types; the dump is complete, the same lines are in every fa_dump boot since 0.7.x), and
  `tfc-wrought-iron-first-load` (WARN: the first datapack load runs before the server config; in 0.8.0 logs the
  warning came at every Age reload, in 0.8.1 only at the first load and never at the warm-up reload).

Documented noise in the final log (round 3), ERROR/FATAL/STDERR: `tfcregistryapi-scan-residual`,
`rhino-dump-declared-methods`, `rhino-dump-distxform`, `distxform-boot-client-mixins` (108 lines). WARN: mixin
optional targets, refmaps and unique duplicates, ModernFix overwrite and timing lines, recipe conditions, KubeJS
schema fallbacks (including the 8 Wooden Cog old-soil cutting recipes removed afterwards), KubeJS optional plugins,
missing tag entries, jar-in-jar duplicates, vanilla pack schema, Draconic and mod notices, EvolvedMekanism config
rewrite, Age reload lag, FTB Library stage provider, TFC first-load metal inference.

### Checks on the final dump (round 3)

| Check | Observed | Result |
|---|---|---|
| `poc_analyze.py --server-dir test-server` | round 1: 86 PASS, 2 FAIL (C-1a, C-4j, both fixed above); round 3: 88 PASS, "all checks PASS", with C-1a and U-1 (495 tags, 3 umbrella, 0 allowlisted) | pass |
| `derive_ages.py` on the real dump | `dev/age_map.toml` unchanged: final per Age dawn 4,119, age_0 652, age_1 2,781, age_2 4,737, age_3 1,917, age_4 1,372, age_5 400, age_6 1,502, age_7 557, age_8 673, age_9 364, disabled 4,497, exactly the prediction of `predict_dump.py`; `derived_ages.json` now names `woodencog:milling/quartz` as the quartz source; `gen_stage_locks.py` rewrote 0 files; both `--check` clean | pass |
| `validate_quests.py`, `check_menus.py`, `gen_textures.py --check`, `gen_multiblock_templates.py --check`, `gen_loot_silence.py --check`, `gen_quests.py` | 1294 ids, 0 errors, 0 warnings; all checks pass; 68 textures, 0 errors; 35 templates up to date; 480 overrides, 0 problems; regenerated output identical | pass |
| `fa_dump`, `fa_dump_full` | 32,326 recipes, 0 without a readable result; 3,498 item tags; 32,326 encoded | pass |

### Open points

- Client [M] (Felix, `dev/client-test-brief.md`, 0.8.1 Nachtrag): shader title, tool rule and hint, mana bar,
  tooltips, knapping grids, client start time, wrought iron ingot pile texture.
- The residual scan noise could shrink further if firmages-core skipped classes whose bytecode is not an enum before
  TFCRegistryAPI's `Class.forName` (ASM read of the access flags). Not done; the lines are harmless.
- `show_compile_errors = true` stays in the title layout until Felix has seen the shader in game.

## 0.9.0 Age swap (branch n3/ageswap, 2026-10-14)

`Modpack-Planung/research-raw/n2-perf.md` item 1 and `n2-emi-leaks.md` item 4: an Age change re-filters the recipes kept
from the last datapack load instead of reloading every datapack (`age/AgeSwap`, SPEC §3.3), the three Age ore tags are
bound in Java (`age/AgeOreTags`; the KubeJS scripts `firmages/miner_blacklist.js` and `prospecting.js` are deleted),
the recipe scripts only declare Ages (`FirmAges.stationAge`, `requireAge`, `untilAge`), and the Create heat levels are a
station lock in the gate. No pack server was started on this branch (worktree rule); all numbers are from the dev
GameTest server (vanilla, IE 12.4.2, Mekanism 10.7.19, DE, KubeJS, ProgressiveStages; 4,746 recipes).

| Check | Observed | Result |
|---|---|---|
| `gradlew build` | 129 unit tests, 0 failed; new: `GateSuite` swap_* (8 cases, `keep` equals `verdict` for all 1,024 Age sets, heat levels, byproduct copies), `RecipeMarkersTest` (4), `CoreSuite.scheduler_swap_unless_full_requested` | pass |
| `gradlew runGameTestServer` | "All 39 required tests passed"; new `AgeSwapGameTests` (3) and the reworked `unlockTwoAgesThroughSwap`, `unlockAndRevokeSwap`, `ageOreTagsAtBoot` | pass |
| Swap vs full reload (`swapMatchesFullReload`, log line `GameTest Age swap vs full reload`) | swap 11 to 15 ms, full datapack reload with the same Ages 1,817 to 2,559 ms over four runs; 4,743 live recipe ids and 44 ore tag entries identical | pass |
| All swaps of one run | 26 swaps, 5 to 21 ms, median 9 ms; the `gate.ageSwap = false` test reloads in full (1.6 to 1.7 s) | pass |
| Machines after a swap (`unlockTwoAgesThroughSwap`) | vanilla furnace lookup, IE crusher cache and Mekanism enrichment cache find the iron recipe right after the age_2 swap and lose it after the revoke, without a datapack load | pass |
| `/reload` after a swap (`vanillaReloadKeepsGateAndSwap`) | full load with the live Ages, new master, ore tags filtered, KubeJS probe ran; the next grant swaps on the new master | pass |
| `gradlew runGameTestServer -PgametestTfc` | the swap flushes `[mekanism, tfc, immersiveengineering]` with no error (TFC `Helpers.updateReloadableData`); 15 failures: the 13 known ones of this mode plus `swapMatchesFullReload` and `ageSwapOffReloadsInFull`, which find the Ages left unlocked by the failed shrine tests | pass (expected failures) |
| `grep -rn isUnlocked kubejs/server_scripts/recipes` | no hits | pass |
| Pack server: swap time per Age with players, `poc_analyze.py` G-1 to G-4 on per-Age dumps, client EMI reload count | not run (no server start allowed on this branch) | open |

## 0.9.0 night run N3 (2026-10-14)

`dev` 41fd6fc (0.8.1) plus six branches merged with `--no-ff` in this order: n3/text, n3/night-anim-ram, n3/deco,
n3/emi, n3/ageswap, n3/shrine. Conflicts: docs and the decision log kept on both sides; `LOKAL-TESTEN.md` and
`UPLOAD-ANLEITUNG.md` stay one-line pointers (n3/text), the RAM text of n3/night-anim-ram went into `dev/release.md`
and `README.md`, the Every Compat budget routine of n3/deco back into `dev/dev-client.md`. n3/emi and n3/ageswap both
built `reloadPending` and an EMI hold mixin: one field, one protocol bump to "4", only the swap-aware
`psemi.EmiReloadHoldMixin` with `EmiReloadGate` kept; `firmages:age_status` then also got `gathered` (n3/shrine).
Then firmages-core 0.9.0. Felix's test server (world at age_9) was stopped with `rcon.py stop`, synced with
`packwiz-installer-bootstrap -g -s server` from `packwiz serve` (quest folder deleted first; 1307 of 1307 entries,
"Finished successfully") and booted seven times with `run_server.py --hold ... --timeout 900` (no `--wipe-world`).
Rounds that changed world state (debug player, Age revoke and grant, Origin gathering and boss, a second team) ran on a
copy: the world was restored from the snapshot taken before them, so the kept world is Felix's age_9 world after the
clean 0.9.0 boots. `debug.allowSimulate` was on for the test rounds and is `false` again; `permanentBeam = true` was set
for the living shrine. `ops.json`, `whitelist.json` and `server.properties` are byte-identical to before (the server
rewrites the timestamp comment of `server.properties` at every start; the original line was put back and the sha256
matches).

### Build

| Check | Observed | Result |
|---|---|---|
| `gradlew build` | 140 unit tests, 0 failed | pass |
| `gradlew runGameTestServer` (stale `run/gametest/config` and `run/gametest/kubejs` moved away) | first run 2 failed (`swapMatchesFullReload`, `unlockTwoAgesThroughSwap`): `miner_blacklist.js` and `prospecting.js`, deleted in n3/ageswap, were still in the run folder (the copy task adds, never removes) and filtered the ore tags before the swap captured them. With a fresh copy "All 43 required tests passed"; "GameTest Age swap vs full reload: swap 11 ms, full datapack reload 1876 ms, 4743 live recipes identical, 44 ore tag entries identical" | pass |
| `gradlew runGameTestServer -PgametestTfc` | 43 tests, 26 passed, 17 failed: the 13 known ones of this mode, `swapMatchesFullReload` and `vanillaReloadKeepsGateAndSwap` (Ages left by the failed shrine tests, as in n3/ageswap), and the new `gatheringReleasesRelics` and `livingShrineSync` (same shrine-ring cause as `consecrationCycle`). Swaps flush `[mekanism, tfc, immersiveengineering]` without error | pass (expected failures) |
| `mods/firmages-core-0.9.0.jar` | from `build/libs`, 0.8.1 jar removed; `packwiz refresh`: 1308 entries (`kubejs` 972, `mods` 216, `config` 114, `defaultconfigs` 4, `shaderpacks` 2); `CHANGELOG.md` added to `.packwizignore` | pass |

### Boot loop

| Round | Setup | Server load (ModernFix) | Log lines | ERROR / FATAL / STDERR | `check_log.py` |
|---|---|---|---|---|---|
| before | Felix's 0.8.1 boot at 10:57 (`latest.log` copied before the stop), followed by an 8.2 s warm-up Age reload | 77.6 s | 3,523 | 63 / 14 / 0 | 0 remaining |
| 1 | 0.9.0, `fa_dump`, `fa_dump_full`, status commands | 61.1 s | 3,379 | 158 / 14 / 30 | 72 in 50 signatures: `byproducts.js` "redeclaration of var copyId" (whole handler aborted, no byproduct copies) and two FirmaSupplementaries recipe parse errors |
| 2 | fixes of round 1, dumps | 71.3 s | 3,250 | 86 / 14 / 30 | 0 remaining; `--warn`: 4 new signatures (Every Compat banner, Supplementaries white dye, two FirmaSupplementaries soap recipes, sack loot table), documented |
| 3 (copy) | swap, prayer and Origin tests (below) | 71.6 s | 3,583 | 92 / 14 / 0 | 29 in 2: `firmament_night.js` "Cannot find function getGameTime" |
| 4 | fixes of round 3, Age tags regenerated from the round 2 dump, dumps | 69.5 s | 3,260 | 88 / 14 / 30 | 0; `poc_analyze.py` 87 PASS, R-0 FAIL (`handcrafted:oven` needs `minecraft:smoker`) |
| 5 (copy) | Origin fight, Firmament Night | not measured | – | – | `firmament_night.js` "getHolder ... is ambiguous" |
| 6 (copy) | fixes of rounds 4 and 5, dumps, Firmament Night | not measured | – | 88 / 14 / 30 | 0 |
| 7 (final, kept world) | Age tags regenerated from the round 6 dump, `allowSimulate = false`, dumps | 60.1 s (Done 58.0 s wall clock) | 3,260 | 88 / 14 / 30 | **0 remaining, exit 0; with `--warn` also 0** (after documenting TFC's start-up probe of `client/gui/screens/Screen`, which in this boot replaced its usual `ClientLevel` probe) |

| Measurement | Observed | Result |
|---|---|---|
| Age swap on the pack server (round 3, one debug player online) | revoke age_9 677 ms (first swap of the session), grant age_9 299 ms, revoke age_5 322 ms, grant age_5 407 ms; filter 58 to 102 ms, ore tags 0 to 58 ms (age_5: 95 ore blocks rebound), caches 37 to 65 ms flushed for `[mekanism, create, tfc, immersiveengineering, mysticalagriculture]`; recipes 35,540 to 35,070 or 34,591 and back. Before: 9 to 15 s freeze per Age (0.8.x); the 0.8.1 warm-up reload alone took 8.2 s, 0.9.0 runs none | pass (< 2 s) |
| `age_status` to a debug player | `reloadPending=true` from the stage change to the end of the swap, then `false`; `gathered=false` | pass |
| Gathering prayer path (`firmages debug pray Gamma <heart> 5`) | answers "Caelum does not dwell in ruins: the Hearth Circle is broken (1 of 22 blocks)" with sparks at the gaps; `firmages shrine status`: "the Gathering waits: ...". Felix's shrine has one block per ring left and no relic on a plinth, so the release itself is only proven by the GameTest `gatheringReleasesRelics` | pass (path) |
| `/firmages origin tp Gamma force`, `origin status` | forced visit accepted; the nine island echoes placed and loaded (9 of 9), arena v2, altar present | pass |
| `/firmages origin gathering` | Gathering #1 started, `[finale] Gathering 1: gate firmages:the_origin opened at the altar` | pass |
| `/firmages origin fight` (KubeJS path) | with the gate open: refused by `the_primordial.js` ("the finale gate or the boss is already alive"); on a fresh copy: `[finale] The Primordial (cataclysm:ender_guardian) summoned by an operator (waves skipped)`, title shown to the debug player | pass |
| Firmament Night | `stage grant Gamma age_0` (new team): "age_0 reached, night pending", 20 s later "firmages:firmament_age_0 scheduled tonight"; `/ec lunarForecast` lists `lunar.event.firmages.firmament_age_0(in 0d)` | pass |
| `check_registry_parity.py dump` 0.8.1 to 0.9.0 | 0 ids removed in any registry. Added: item 3,122 (everycomp 1,671, supplementaries 338, mcwwindows 315, handcrafted 285, mcwdoors 257, mcwbridges 146, decorative_blocks 52, firmages 31 = 10 Age Lanterns and 21 engraved bricks, firmasupps 25, moonlight 2), block 2,995, fluid 3, entity_type 18, plus registry types of the new mods. TFCAstikorCarts 81 woods per part and TFC Regrowing Forests unchanged | pass (additions only) |
| Deco checks | no `everycomp:*twilightforest*` id, 1,670 Every Compat blocks, Every Compat's conditional-registration banner (hazardous config read); Supplementaries' disabled bomb, cannon, safe, flax, netherite doors and plunderer are still registered (3.9.9 always registers) and now sit in `[derived_hidden]`; `signature_blocks.js` loads without error; no FirmaSupplementaries mixin error | pass |
| `fa_dump`, `fa_dump_full` (round 7) | 35,522 recipes, 0 without a readable result; 3,713 item tags; 35,522 encoded | pass |

Fixes found by the loop: `byproducts.js` `let copyId`; FirmaSupplementaries overrides for `sconce_haunting` (Create 6
result format) and `dragon_banner_pattern` (TFC cloth squares); `firmament_night.js` day time instead of `getGameTime`
and `getHolderOrThrow` instead of the ambiguous `getHolder`; `handcrafted:oven` takes the TFC firepit;
`poc_analyze.py` (used by `derive_ages.py`) resolves Every Compat's `moonlight:block_type_swap` ingredients (without it
all 1,635 Every Compat items moved to the Iron Age); `log_noise.json` (narrowed `tfcregistryapi-scan-residual`, four
WARN entries, the TFC `Screen` probe).

### Checks on the final dump (round 7)

| Check | Observed | Result |
|---|---|---|
| `poc_analyze.py` | 88 PASS, 0 FAIL, exit 0; G-1 "35522 recipes, 761 fluids with an Age", U-1 500 tags, C-1a and R-0 pass | pass |
| `derive_ages.py --check` | generated section up to date (from the round 6 dump: 26,693 items, 4,667 disabled; Every Compat palisades and bridges age_0, furniture, windows and doors age_1; classic doors age_2 and counters age_3, see `dev/offene-punkte.md`) | pass |
| `gen_stage_locks.py --registry` on the round 7 dump | rewrote 0 files | pass |
| `validate_quests.py --style` (jar lookups on the synced mods) | 1296 object ids, 0 errors, 0 warnings | pass |
| `check_menus.py`, `gen_textures.py --check`, `gen_multiblock_templates.py --check`, `gen_firmament_night.py --check` | all checks pass; 99 textures, 0 errors; 35 templates up to date; 44 files up to date | pass |

### Open points

- Client [M] (Felix, `dev/client-test-brief.md`, 0.9.0 Nachtrag): EMI audit lines and one EMI reload per grant,
  Ponder and `/guidemec`, deco styles and the boot and heap budget, swap without freeze and machines, idle beam, hum
  and dust, ceremony echo, the Gathering (needs the shrine rebuilt in Felix's world), Firmament Night colours, quest
  texts and lock tooltips, the CurseForge RAM suggestion.
- `show_compile_errors = true` stays in the title layout until Felix has seen the shader in game.
- EMI's recipe screen block also applies to ops and creative players in Dawn and the Stone Age (n3/emi).
- JEI still restarts on the recipe packet of a swap (about 1.5 to 2 s on the client).

## 0.9.0 review fixes (2026-10-14)

Code and player review of 0.9.0: recipe caches the Age swap missed, team join, Gathering, quests. One boot of Felix's
test server (age_9): stopped state checked, synced with `packwiz-installer-bootstrap -g -s server` from `packwiz serve`
(quest folder deleted first, 1308 entries, "Finished successfully"), `debug.allowSimulate` on for the run,
`run_server.py --hold 240 --timeout 900` with 64 `--cmd` steps. The world was copied before the boot and restored
after it (`diff -rq` clean); `debug.allowSimulate` is `false` again; `server.properties`, `whitelist.json` and
`ops.json` match their sha256 from before. After the boot the probe fix below was built and the server re-synced
(firmages-core jar identical to `mods/`).

| Check | Observed | Result |
|---|---|---|
| Jar scan for recipe state (`RecipeManager` with a reload listener, `AddReloadListenerEvent`, `TagsUpdatedEvent`, `ServerStartedEvent`, `OnDatapackSyncEvent`, or a `RecipeManager` subclass; 216 mods, jar-in-jar) | new caches: FastSuite `AuxRecipeManager`, Ars Nouveau registries (Ars Additions through `reloadAll`), Immersive Petroleum `RecipeReloadListener.lists`, AE2 `TransformLogic`, FramedBlocks `FramingSawRecipeCache`; megacells, IE arc recycling and Sophisticated Core listen to the `OnDatapackSyncEvent` the swap posts | flushed in `RecipeCaches` |
| `gradlew build`, `gradlew runGameTestServer` | build and unit tests pass; "All 43 required tests passed", swap 13 ms vs full reload 2366 ms, recipes identical | pass |
| Swap flush list on the pack server | every swap: `caches ... [fastsuite, mekanism, create, tfc, immersiveengineering, mysticalagriculture, ars_nouveau, immersivepetroleum, ae2, framedblocks]`, 39 to 90 ms, no `cannot flush` line; swaps 201 to 349 ms | pass |
| Crafting table after a swap (`firmages recipes craft sgjourney:building/naquadah/naquadah_stairs`, the 4-argument lookup FastSuite caches) | before: crafts the stairs; after `revoke age_9`: "locked by its Age: no recipe matches"; after `grant age_9`: crafts the stairs again | pass |
| `selftest server`, `recipe_caches_fresh` with age_0, age_3, age_5 and age_6 revoked | "caches of [fastsuite, ars_nouveau, immersivepetroleum, ae2, framedblocks] match the live recipes" | pass |
| `recipe_caches_fresh` with all Ages | failed on two probe rules, not on stale caches (same numbers at boot, before any swap): Ars Additions keeps entity-less source spawner recipes in `DEFAULTS`, and AE2 only takes the first ingredient of each transform recipe (a `break`, javap). Probe corrected; the corrected probe has not run on the pack server | probe fixed, unverified |
| Team: debug player Gamma joins Felix's world (Felix solo, age_9, no party) | before the fix: chat line naming HiighHopes, no team change | pass |
| Team: Gamma granted age_0..age_9 (22 stages), relog, Gamma founds "Firmament" | ProgressiveStages: 22/28 stages before, 0/28 right after founding (2/28 with the starting stages): stages do not move into a founded party. Fixed: no automatic party for a team that holds an Age | finding, fixed (fix not re-run) |
| Team: Delta joins with the party present | "You join Firmament. Its Ages are yours.", FTB "Delta has joined your party" | pass |
| Gathering line: Gamma at the altar (forced visit), Delta in the Overworld | action bar "The altar waits for 1 more: Delta"; no Gathering start | pass |
| Hearth idol in a pit kiln: placed item with `firmages:unfired_hearth_idol`, 2 thatch and 8 oak logs by `debug use`, fire above, `tick sprint 7600` | block back to `tfc:placed_item` holding `firmages:hearth_idol` | pass |
| `check_log.py test-server/logs/latest.log` | 63 ERROR, 14 FATAL, all documented noise; 0 remaining | pass |
| `gen_quests.py`, `validate_quests.py --style` | 1308 object ids, 0 errors, 0 warnings (four new teaching quests listed in `STYLE_TEACHING`) | pass |

### Open points

- The corrected `recipe_caches_fresh` probe and the no-founding rule for teams with Ages have not run on the pack server.
- A world where one player reached an Age alone, outside a party (Felix's world), keeps that state; see `dev/offene-punkte.md`.
- In game (client brief 0.9.0, points 3 and 11): crafting table, framing saw, Imbuement Chamber, Immersive Petroleum
  machines and AE2 water transforms right after a swap; the new quests and notes.

## 0.9.0 log warnings (branch n4/warnings, 2026-10-14, static)

Input: `test-server/logs/latest.log` and `debug.log` of the 0.9.0 boot (13:19) and Felix's live session up to 13:50.
`check_log.py` now counts every WARN and the INFO lines that report a problem. Before the fixes, with an empty
noise list: 868 problem lines in 220 signatures at boot; with the cleaned list: 551 lines in 13 signatures outside it,
112 in the open TFCRegistryAPI entry.

| Group | Lines | Source (checked with) | Fix |
|---|---|---|---|
| "Skipping recipe X, not a json object" | 462 | KubeJS `RecipesKubeEvent.discoverRecipes` warns on a file without `type` (javap); TFC 195, FirmaSupplementaries 184, TFCAstikorCarts 67, Firmalife 10, Beneath 4, TFCreate 2, all `{"neoforge:conditions": [{"type": "neoforge:false"}]}` (jar scan) | typed removal overrides, `dev/gen_recipe_overrides.py` |
| "Failed to parse recipe ... Falling back to vanilla" | 76 | Create Framed 47 (`{"item": {...}}` results), Diesel Generators 18 and Mechanical Spawner 1 (Create 0.5 fluid ingredients), Wooden Cog 8 (`tfc:mud_bricks/<old soil>`), FirmaSupplementaries 2 (`#tfc:candles`, `#tfc:colored_bricks_alabaster` do not exist) | Create 6.0.10 format, removal, or the right tag; same generator |
| "Not all defined tags ... present" | 5 (+1 per reload) | tags made in mod code without data | empty tags in `kubejs/data` |
| "Unreachable entry!" | 1 | `firmasupps:blocks/sack` alternatives | loot table override |
| ModernFix timing | 2 | `feature.measure_time` (javap `ModernFix`) | `config/modernfix-mixins.properties` |
| Modonomicon "Recipe ... not found" | 8 at the first login | eight permanently disabled Occultism rituals in six book entries | entries hidden, recipe pages removed |
| "Could not save data ... brandonscore_world_entity.dat" | 4 ERROR | SimpleBackups `saveEverything(true, false, true)` then the zip at once (javap `BackupThread`); AccessDeniedException on the atomic move | `saveAll = false` |
| Fzzy Config "Deserialization Error ... missing" | 15 (13:05 boot) | partial `config/cave_dweller/server_config.toml` | full file |
| "firmages-server.toml is not correct" | 1 (13:05 boot) | new 0.9.0 keys in an older file | `config/firmages-server.toml` shipped |
| "Can't keep up! ... 6095ms" | 1 | first login after the boot (13:41:03 to :09), before the backup | open point (spark) |

| Check | Result | Status |
|---|---|---|
| `python dev/gen_recipe_overrides.py --check` (jars of the CurseForge instance and the 1.21.1 client jar) | 538 overrides, 0 problems | pass |
| `python -m json.tool` on every added JSON | 559 files, 0 errors | pass |
| `node --check` on `global_removals.js`, `electric_age.js` | ok | pass |
| `python dev/validate_quests.py` | 1308 object ids, 0 errors, 0 warnings | pass |
| `python dev/check_log.py test-server/logs/latest.log` (log from before the fixes) | 551 remaining in 13 signatures, all listed above; 112 open | expected until a reboot |

### Open points

- Nothing above has run on a server yet: the next boot proves it (`dev/offene-punkte.md`).
- TFCRegistryAPI rest (112 lines per boot): firmages-core change, see `dev/offene-punkte.md`.

## 0.9.0 data round N4 (2026-10-14, static)

No server boot. Input: the round 7 dump of the N3 night run (`test-server/local/firmages`, 35,522 recipes), copied
read-only; `dev/predict_dump.py` applied the new deco recipes to it (edit set "Deco in TFC woods").

| Check | Observed | Result |
|---|---|---|
| `predict_dump.py`, `derive_ages.py --dump-dir <predicted>` | 2 prints rewritten, 46 counters on smooth rock; Every Compat items moved later 196 to 94; classic doors, cottage doors and counters in TFC/AFC woods: 34 each now age_1; the 11 vanilla-wood Handcrafted counters age_3 to age_2 (vanilla planks) | pass |
| `gen_stage_locks.py`, `predict_dump.py --sync-locks`, `derive_ages.py --check`, `gen_stage_locks.py --check` | 3 tag files rewritten (107 items with a new Age); afterwards both checks up to date | pass |
| `poc_analyze.py` (current and predicted dump, `--server-dir` the test server for the R-M jar reads) | 88 PASS, 1 FAIL: the new E-1. 0 recipe types shown before their station's Age; 67 shown recipes list a locked item in an item-list input (EMI keeps disabled stacks in a `ListEmiIngredient`), see `dev/offene-punkte.md` | E-1 fail (client fix open) |
| E-1 detection | `create:pressing` pointed at the mechanical crafter (age_2) in a test run: "create:pressing (station age_2) shown at age_1: 14" | pass |
| Dawn rock-pit escape | `firmages:needs_tool_early` (firmages-core) = `#minecraft:logs`, `#c:stones/raw`, `#c:stones/hardened`, `#c:ores`, `#c:sandstone/blocks`; block tag sources in the TFC, Create, TFCreate, Firmalife, TFC-IE and other jars and `tags/tfc_common_tags.js` name rock, sandstone and ore blocks only (claystone is a rock). TFC dirt, grass, rooted dirt, mud, clay, clay grass, sand and gravel are all Dawn items; no stage file has a `[blocks]` lock; gravel-coloured deposits of later Ages are `[[ores.overrides]]` that drop gravel | pass |
| Stone tool heads in Dawn | 4 rock categories x knife, axe, hammer and shovel head: `tfc:knapping` recipes without any lock, heads and the `tfc:crafting/stone/*` tools are Dawn items | pass |
| `validate_quests.py --style` (jars of the CurseForge instance), `check_menus.py` | 1308 object ids, 0 errors, 0 warnings; all checks pass | pass |

## 0.9.1 polish round N4 (2026-10-14)

Merged with `--no-ff` into `dev`: n4/warnings, n4/text, n4/data, n4/core (conflicts only in the append-only docs, both
sides kept; `gen_quests.py` re-run after the merge, no change). Then firmages-core 0.9.1: the EMI item-list filter
(SPEC §21.11) and enum-only TFCRegistryAPI loading (SPEC §20), the two open firmages-core items of n4/data and
n4/warnings. Felix's test server stayed untouched and running (he is testing 0.9.0 live on it). The boot loop ran on a
copy (`robocopy` without world, logs, local and backups; ports 25566 and RCON 25576; synced with
`packwiz-installer-bootstrap -g -s server` from `packwiz serve`, quest folder deleted first, 1867 entries, "Finished
successfully"; `debug.allowSimulate = true` only in the copy) with a new world, all Ages through
`firmages ages simulate grant`. The copy is deleted.

| Check | Observed | Result |
|---|---|---|
| `gradlew build` | 144 unit tests, 0 failed (new `ClassFileAccessTest`) | pass |
| `gradlew runGameTestServer` (fresh `run/gametest`) | "All 45 required tests passed"; team carry-over: `gt_founder entered Firmament ... granted [age_9]`; `crafting_table_follows_ages: 150 live recipes craft, 10 Age-locked recipes do not` | pass |
| `gradlew runGameTestServer -PgametestTfc` (fresh) | first try: mod construction failed, `ClassNotFoundException: org.objectweb.asm.ClassReader` (ASM is not visible from the game layer), so the class header is read by hand (`perf/ClassFileAccess`). Then 45 tests, 17 failed: exactly the 17 known failures of this mode (0.9.0 review row); `tfcRegistryApiScanScope` passes (every TFC enum value of all seven interfaces registered) | pass (expected failures) |
| Round 1 (grants, `fa_dump`, `fa_dump_full`, craft probe, `selftest all`) | swaps 237 to 378 ms; `recipes craft sgjourney:building/naquadah/naquadah_stairs`: crafts, after `simulate revoke age_9` "locked by its Age: no recipe matches", after `grant age_9` crafts; `selftest all` 55 of 55 (`recipe_caches_fresh`: caches of fastsuite, ars_nouveau, immersivepetroleum, ae2, framedblocks match; `crafting_table_follows_ages`: 150 live craft, 150 locked do not); `check_log` 8 lines in 7 signatures left | fixes below |
| Round 1 leftovers | `simplebackups-common.toml is not correct. Correcting`: NeoForge compares comments too, the shipped file had a custom comment (removed, the reason stays in the decision log). ModernFix `Option 'mixin.feature.measure_time' overriden`: ModernFix warns for every override, also by JVM property (javap), noise entry with that reason. `Boot snapshot ... FALLBACK_NO_MIRROR (file missing)` on a new world: firmages-core now says "new world, no Ages saved yet" while `level.dat` does not exist, so a lost mirror on an old world still reads "file missing". A passed self-test case named "missing": `check_log.py` skips `selftest [PASS]` lines. `Age age_9 revoked` and one `Can't keep up` (4.0 s): the revoke test and `/fa_dump` (debug tools, see below) | fixed |
| Round 2 (new world, grants, `selftest all`, no dump, no revoke) | `check_log.py`: FATAL 0 (0.9.0: 14), ERROR 7, WARN 208, documented noise 217, open 0, **remaining 0, exit 0**; boot 79 s to Done; 7 DISTXFORM lines (all documented), no "Skipping recipe", no "Falling back"; scan "limited to 18 of 187 mod files"; "deco: 46 counters take smooth rock"; `selftest all` 55 of 55 | pass |
| Round 3 (same world, boot from the mirror, `fa_dump`, `fa_dump_full`) | 35,522 recipes, 0 not encodable; the only problem line is `/fa_dump`'s own `Can't keep up` (3.4 s), open point | pass |
| `poc_analyze.py` on the round 3 dump | 88 PASS, 0 FAIL; E-1: 0 types before their station, 0 item lists with a locked item (filtered by `ListEmiIngredientMixin`) | pass |
| `derive_ages.py --check`, `gen_stage_locks.py --check` on the real dump | up to date, 0 files changed (the predicted n4/data dump matched) | pass |
| `check_registry_parity.py dump` (N3 dump of Felix's server, read only, vs round 3) | every compared key the same, TFCAstikorCarts 567 items and 81 woods per part, TFCRegrowingForests ids and recipes the same: "PASS no difference" | pass |
| `validate_quests.py --style`, `check_menus.py`, `gen_textures.py --check`, `gen_multiblock_templates.py --check`, `gen_recipe_overrides.py --check`, `gen_loot_silence.py --check` | 1295 object ids 0/0; menus pass; 99 textures 0 errors; templates up to date; 538 overrides 0 problems; 480 overrides 0 problems | pass |
| `release.ps1 -Version 0.9.1 -SkipServerPack` | `pack.toml` and title line 0.9.1, mrpack and CurseForge zip in `dev/exports/`; `gen_credits.py` 216 mods, `md2html.py` both pages | pass |

### Open points

- In game (client brief 0.9.1): the team carry-over on Felix's age_9 world, EMI for operators, the item-list filter,
  the new quest texts and banners, the deco recipes.
- Superseded or closed from the N3 open points: the corrected `recipe_caches_fresh` ran on the pack (rounds 1 and 2,
  pass); the no-founding rule for teams with Ages is replaced by the team carry-over (GameTests only so far).
- `/fa_dump` stalls the server thread 3.4 to 4 s; the first login after a boot stalled 6 s in 0.9.0 (no player login in
  the loop). Both in `dev/offene-punkte.md`.

## 0.9.2 round N5 (2026-10-14)

First the open N4 review fixes on `dev` (commit 65f541f), then `--no-ff` merges of n5/clientlog, n5/mods-loading and
n5/core (conflicts only in the append-only docs and `pack.toml`; the FancyMenu placeholders of n5/core left out), two
firmages-core fixes (Evolved Mekanism in EMI, the EMI audit index), version 0.9.2. Felix's test server stayed untouched
and running; every boot ran on a copy (`robocopy` without world, logs and local; ports 25566 and RCON 25576; synced
with `packwiz-installer-bootstrap -g -s server` from `packwiz serve -p 8093`, quest folder deleted first, 1912 entries,
"Finished successfully"; `debug.allowSimulate = true` only in the copy; all Ages through `firmages ages simulate
grant`). The copy is deleted.

| Check | Observed | Result |
|---|---|---|
| Review fixes: `gen_quests.py` | reproduces the fix agent's `stone_age.snbt` and `en_us.snbt` byte for byte, then the unified Dawn grid and torch texts | pass |
| Review fixes: `derive_ages.py`, `gen_stage_locks.py` on the N4 dump (predicted, matched the real one in N4) | Framed Blocks, Decorative Blocks and Macaw's Bridges age_1 in `[mods]`: 202 items age_0 to age_1, 0 tag files changed by the regeneration, both `--check` up to date | pass |
| Review fixes: `gradlew build`, `runGameTestServer` (fresh) | "All 47 required tests passed"; new `TeamGameTests.loginRepairsTheParty` (first try failed: the real login hook of the debug spawn ran the carry-over one tick before the test's own call, so the test waits three ticks before the grant) and `carryOverTakesOnlyTheMirror` | pass |
| Review fixes: server pack `start.bat` (scratch folder) | with `FA_JAVA_HOME` = tools\jdk-21: Java 21 found, EULA warning, installer step; with only PATH (Corretto 25): "Java 21 not found" | pass |
| firmages-core 0.9.2: `gradlew build`, `runGameTestServer` (fresh `run/gametest`) | 147 unit tests, 0 failed; "All 49 required tests passed" (the two Origin tests of n5/core included) | pass |
| `release.ps1 -Version 0.9.2` | `pack.toml` and title line 0.9.2; mrpack 519 MB, CurseForge zip 43.5 MB, server zip 527.8 MB: `start.bat` CRLF and `start.sh` LF with `NEOFORGE=21.1.252`, `user_jvm_args.txt` `-Xms8G -Xmx8G`, `mods/firmages-core-0.9.2.jar`; `gen_credits.py` 216 mods | pass |
| Round 1 (new world, grants, `fa_dump`, `fa_dump_full`, `selftest all`, `origin status`) | swaps 210 to 322 ms; `fa_dump_full` 35,521 recipes, 0 not encodable; `check_log`: 6 left: `/fa_dump`'s `Can't keep up` and five "is not correct. Correcting" lines (supplementaries-common, mysticalagriculture-common, everycomp-hazardous, ars_nouveau-common, progressivestages): NeoForge compares comments, the full files of n5/clientlog kept the pack's comments | fixed: files as NeoForge writes them |
| `derive_ages.py --scan` on the copy's 186 jars | only additions and the pack's own overrides (see decision row); `supplementaries:ash` reachable, 3 tag files rewritten; `derive_ages --check` and `gen_stage_locks --check` up to date on the round 1 dump | pass |
| `poc_analyze.py` on the round 1 dump | 88 PASS, 0 FAIL; E-1: 0 types before their station, 0 lists with a shown locked item, 137 recipes with an item list trimmed by `ShownMembers` (the rule is now modelled, not switched off) | pass |
| Round 2 (new world, grants, `selftest all`, `origin status`, no dump) | no "Correcting" line; `selftest all` 55 of 55; `origin status`: arena v2, "fight running false"; `check_log` 1 left: `Can't keep up` (2.1 s) right after `selftest all` (debug command, open point) | pass (debug stall open) |
| Round 3 (same world, boot from the mirror, `firmages ages`, `origin status`) | Done 65 s; 2 left: AllTheLeaks "server is stuck while trying to close" at the stop, JVM exit 0 | traced |
| Round 4 (same, `debugThreadsStuck` on in the copy) | the 15 stuck threads are Moonlight 3.7.2's `DynamicResourcesProvider.runGenerationPipeline` pool (non-daemon, never shut down); noise entry with that reason; `check_log` **0 left, exit 0** | pass |
| `validate_quests.py --style`, `check_menus.py` (with `check_loading`), `gen_textures.py --check`, `gen_way_sign_textures.py --check`, `gen_multiblock_templates.py --check`, `gen_recipe_overrides.py --check`, `gen_loot_silence.py --check` | 1298 object ids 0/0; menus pass, loading overlay memory line fits 168x16; 99 textures; 34 way sign icons; templates up to date; 538 and 480 overrides 0 problems | pass |
| `index.toml` | only `config/`, `defaultconfigs/`, `kubejs/`, `mods/`, `shaderpacks/` paths | pass |

### Open points

- In the client (brief 0.9.2): loading screen memory line and bar, no gear button, way sign icons, the Evolved Mekanism
  categories, the INFO audit line, no "Correcting" line in the client log, the Stone Age workbench path.
- `/firmages selftest all` stalls the server thread about 2.1 s (round 2), like `/fa_dump`; both debug commands.
- JEI still logs its six broken Evolved Mekanism recipes (EMI shows them through firmages-core); Moonlight's pool at
  stop. Both upstream, both documented as noise.

## 0.9.3 round N6 (2026-10-14)

`--no-ff` merges of n6/text (Chronicle corrections from the third player's-eye review) and n6/data (stage names,
drop messages, server README, one Gathering line). Conflicts only in `dev/decisions-while-away.md` (both row sets
kept) and `server/README-SERVERPACK.md` (the trimmed host table "Operator commands" of n6/data kept; the walkthrough
points there). No Java or lang change, so firmages-core stays 0.9.2 (no gradle build). Felix's test server stayed
untouched and running; both boots ran on a copy (`robocopy` without world, logs and local; ports 25566 and RCON
25576, whitelist off; synced with `packwiz-installer-bootstrap -g -s server` from `packwiz serve -p 8092`, quest
folder deleted first, "Finished successfully"). The default 8 GB heap could not be committed next to the live server
and client (Windows page file, errno 1455), so both boots ran with `-Xms3G -Xmx6G`. The copy is deleted.

| Check | Observed | Result |
|---|---|---|
| `gen_quests.py` after the merges | reproduces the merged chapters and `en_us.snbt` byte for byte (13 chapters, 21 reward tables, 896 lang keys) | pass |
| `validate_quests.py --style` | 1301 object ids, 0 errors, 0 warnings (jar lookups on) | pass |
| `release.ps1 -Version 0.9.3 -SkipServerPack` | `pack.toml` and title line 0.9.3, index refreshed; mrpack 519.0 MB, CurseForge zip 43.5 MB; `gen_credits.py` 216 mods, `md2html.py` | pass |
| Boot 1 (new world, `selftest all`, `firmages ages`, `origin status`, hold 180 s) | Done after 91.4 s from the first log line; FTB Quests "13 chapters, 351 quests, 21 reward tables"; `selftest all` 55 passed, 0 failed; no `Can't keep up` this time; `check_log.py` FATAL 0, 217 documented noise, **0 remaining, exit 0** | pass |
| Boot 2 (new world, `firmages ages simulate grant` age_0 to age_9, `fa_dump`, `fa_dump_full`) | the round N5 dump was gone with its copy and Felix's server held only a pre-N5 dump (`poc_analyze` on it: 1 FAIL, `derive_ages --check` out of date), so a fresh dump; 35,521 recipes, 0 not encodable; `check_log` 1 left: `/fa_dump`'s `Can't keep up` (3.3 s, known debug stall) | pass (debug stall open) |
| `poc_analyze.py` on the boot 2 dump | 88 PASS, 0 FAIL; E-1 no recipe type shown before its station's Age, no shown input with a locked item | pass |
| `derive_ages.py --check`, `gen_stage_locks.py --registry <dump> --check` on the boot 2 dump | both up to date; `registry.json` content unchanged (only its source path, reverted) | pass |
| `check_menus.py`, `gen_textures.py --check`, `gen_way_sign_textures.py --check`, `gen_multiblock_templates.py --check`, `gen_recipe_overrides.py --check`, `gen_loot_silence.py --check` | menus pass (memory line fits 168x16); 99 textures; 34 way sign icons; templates up to date; 538 and 480 overrides 0 problems | pass |

### Open points

- In the client (brief 0.9.3): the Firmament chapter revealing each offering with its Age, the lines around "Into The
  Origin", the Bronze and Iron ring quests, the new stage names in "Opens with" (operators), the drop message.
- A backup restore after the server README's four steps has not been played through.
- Retired helper items read "Its making belongs to a later Age" for non-operators; the walkthrough's Space Age
  pitfall still names naquadah.

## 1.0 round A (2026-10-15)

`--no-ff` merges of a1/adventure, a1/world, a1/deco and a1/core (in that order) into dev, then pack 0.10.0 and
firmages-core 0.10.0. a1/boss stays out (round C). Felix's test server stayed untouched and running; every boot ran on
a copy (`robocopy` without world, logs and local; ports 25566 and RCON 25576, whitelist off; synced with
`packwiz-installer-bootstrap -g -s server` from `packwiz serve --port 8095`, quest folder deleted first, "Finished
successfully"), each with a new world and `-Xms3G -Xmx6G` next to the live server. The copy is deleted.

Boot loop (nine copy boots). Boot 1 crashed during mod loading: TFC Tumbleweed 1.3 loads `ClientLevel` on a dedicated
server, so it left the pack again. Boots 2 to 5 fixed what the merged branches broke in the log and the dump (commit
"0.10.0 boot loop": Exposure's server config, Exposure's Create 6 developing recipes, the Storage Drawers CoFH key,
24 Twilight track loot tables, Every Compat's Macaw's coffee tables, TFC Volcanoes minerals and Exposure prints in
`derive_ages.py`, and a TFC Ruins chest item id without its namespace). Boots 6 and 7 dumped with `gate.enabled =
false` for the derivation (see `derive_ages.py`: on a gated dump a hidden item stays hidden), boot 8 dumped with the
gate on for the checks, boot 9 ran without dump and self test for the log check (both debug commands stall the server
thread, see the open points of round N5).

| Check | Observed | Result |
|---|---|---|
| `gradlew build` (JDK 21, offline) | BUILD SUCCESSFUL; `build/libs/firmages-core-0.10.0.jar` byte-identical to `mods/firmages-core-0.10.0.jar` (SHA-256 `eecb3810…6c90`, also in `index.toml`) | pass |
| `runGameTestServer` in a fresh `run/gametest` | firmages 0.10.0 loaded; "All 53 required tests passed" | pass |
| Copy boot 7 (gate off, `simulate grant` age_0 to age_9, `fa_dump`, `fa_dump_full`) | 42,136 recipes; `gen_stage_locks.py --registry` (34,969 items), `derive_ages.py`, `gen_stage_locks.py`; second pass: `derive_ages.py --check` and `gen_stage_locks.py --check` up to date | pass |
| `derive_ages.py` on that dump | 34,969 items; 3,286 moved later, 4,593 never reachable (hidden); final dawn 4,158, age_0 959, age_1 6,793, age_2 4,539, age_3 2,211, age_4 1,212, age_5 1,118, age_6 1,498, age_7 572, age_8 680, age_9 783, disabled 10,446 | done |
| Copy boot 8 (gate on, grants, `fa_dump`, `fa_dump_full`, `firmages recipes audit`, `selftest all`) | gate at age_9: 38,335 of 42,136 recipes, 3,801 dropped (disabled 3,794, superseded 7); `selftest all` 55 passed, 0 failed; Age coverage: all 20,365 ProgressiveStages Age item locks in an Age tag | pass |
| `poc_analyze.py --baseline <boot 7 dump>` on boot 8 | first 83 PASS, 8 FAIL (G-2 and the late R- chains); after two tool fixes 91 PASS, 0 FAIL, including R-T age_2..age_9 (every Age token reachable without a boss), G-4 (36 types accepted) and E-1 | pass after fix |
| `check_log.py` on boot 9 (gate on, grants, hold 120 s) | Done after 84.6 s from the first log line (86.6 s wall clock, server reported 16.7 s); FATAL 0, ERROR 8, STDERR 0, WARN 214, all documented; remaining 0 after one new noise entry (spark at boot) | pass after fix |
| Mods | 239 mod files with 230 mods loaded (jar-in-jar included); pack 233 metafiles plus `firmages-core` (234 in the CurseForge credits, 0.9.3: 216): 26 added, 8 removed | done |
| FTB Quests | "13 chapters, 339 quests, 21 reward tables"; `gen_quests.py` reproduces the book byte for byte; `validate_quests.py --style` (jars of the copy) 1251 object ids, 0 errors, 0 warnings | pass |
| `check_registry_parity.py dump` against Felix's server dump (pre-N5, 2026-10-14; no plain 0.9.3 dump exists) | items 26,694 to 34,970: removed 1,098 (twilightforest 660, cataclysm 284, mowziesmobs 56, railways 48 Twilight-wood tracks, bosses_of_mass_destruction 21, supplementaries 17 Twilight/Cataclysm boats and way signs, firmages 8 sigils, summoningrituals 3, gateways 1), added 9,374 (rechiseled 3,628, everycomp 2,434, mcwfurnitures 654, mcwroofs 607, refurbished_furniture 470, dustydecorations 387, …). Blocks 20,314 to 29,226 (703 removed, 9,615 added), entity types 1,279 to 1,062 (237 removed incl. `apotheosis:tiered_gateway`, 20 added). Every removed id belongs to the removed mods or to their compat content | expected |
| `check_menus.py`, `gen_textures.py --check`, `gen_way_sign_textures.py --check`, `gen_multiblock_templates.py --check`, `gen_recipe_overrides.py --check --mods <copy>`, `gen_loot_silence.py --check`, `gen_cold_forge.py --check`, `gen_blood_moon.py --check`, `gen_firmament_night.py --check`, `gen_ponder_schematics.py --check` | menus pass; 234 textures 0 errors; 34 way sign icons; 35 templates; 543 overrides 0 problems; 504 loot overrides 0 problems; Cold Forge 594 blocks; 56 and 45 files up to date | pass |
| `gen_credits.py`, `md2html.py`, `packwiz refresh` | 234 mods, no change after regeneration; index refreshed | pass |

Fixes of this pass:
- `poc_analyze.py` counted the Theurgy reformation's target sulfur as an ingredient (it stays on the target pedestal).
  That closed a false loop that made vanilla oak reachable from the Arcane Age; by the rule, vanilla oak, birch,
  spruce and jungle wood and their decoration are Singularity Age now (forest worlds behind the Stargate), the other
  vanilla woods hidden.
- With the vanilla bucket in the Electric Age, the R- walk had no lava bucket: the Mekanism crusher and everything
  behind it (bio fuel, HDPE, polonium, antimatter, chaos fragments, the Stargate) failed. `poc_analyze.py` now fills a
  bucket at water and lava (GROWN), as `derive_ages.py` already did. G-2 counts the byproduct copies
  (`FirmAges.untilAge`) whose Age is open as superseded, since a gate-off baseline still holds them.
- The Ars volcanic sourcelink took a vanilla lava bucket and moved to the Electric Age, away from its Arcane quest; it
  takes a magma block now (`recipes/age_3/arcane_tfc_inputs.js`).
- `log_noise.json`: spark's "Timed out waiting for world statistics" while the server creates its dimensions.

### Open points

- In the client (brief 0.10.0): the Age token routes, Cold Forge and compass, fortress and Mars temple loot, the
  built-up chamber, the reactor's chaos fragments, the draconium vein, Ponder scenes, Age music, the deco set with its
  client measurement, the chisel patterns.
- Vanilla wood in the Singularity Age is the rule's result, not a design: Felix decides (`dev/offene-punkte.md`).
- `derive_ages.py --scan` reads feature predicates as placed blocks (vanilla oak sapling "from" crimson fungus).

## 1.0 round A review fixes (2026-10-15)

Two review findings on 0.10.0. (1) Without magic, four shrine offerings were out of reach: the Pressure Core needed
the Arcane Gearbox (source gems), the Humming Core the Attuned Circuit (spirit attuned crystal), the Ultimate
Singularity the Awakened Keystone (Marid ritual only) and both cores. Against the decision "tech is required, magic a
bonus", each part has a tech recipe now next to the cheaper magic one; `poc_analyze.py` R-X walks without the magic
recipes. (2) The Singularity goal quest and the walkthrough finale still described the removed Cataclysm trial and The
Primordial's Cataclysm helpers; both say now that the guardian of The Origin is rebuilt for 1.0.

| Check | Observed | Result |
|---|---|---|
| `poc_analyze.py` R-X on the boot 8 dump (before the fix, `--baseline` boot 7) | 2,448 magic recipes left out; R-X age_4, age_5, age_8 and age_9 FAIL (Arcane Gearbox, Attuned Circuit, Awakened Keystone), age_2, age_3, age_6, age_7 PASS | finding reproduced |
| Copy boot 10 (gate on, new world, fresh sync from the repo) | Done after 101 s; KubeJS 42/42 server scripts, 0 errors; FTB Quests "13 chapters, 339 quests"; gate master 42,138 recipes; `firmages recipes audit` lists `firmages:mechanical_crafting/arcane_gearbox` [create:mechanical_crafting], `firmages:crafting/attuned_circuit_tuned` [minecraft:crafting] and `firmages:fusion/awakened_keystone` [draconicevolution:fusion_crafting] with their outputs; `selftest all` 55 passed, 0 failed | pass |
| Grants and dumps of boot 10 | `firmages ages simulate grant` was refused (`debug.allowSimulate` off in the copied config), so `fa_dump` ran at Dawn (7,262 recipes) and is no age_9 dump | not usable |
| `poc_analyze.py` on a predicted dump (boot 8 dump plus the three new recipes, encoded like their siblings; audit of boot 10) | 99 PASS, 0 FAIL, including R-X age_2 to age_9 and G-4 (9 types, 36 accepted) | pass (predicted) |
| `check_log.py` on boot 10 | FATAL 0, ERROR 8, all documented; 1 remaining: Every Compat corrects `everycomp-entries.toml` of the copied 0.9.2 config | pass |
| `gen_quests.py`, `validate_quests.py --style` (jars of the copy) | 1252 object ids, 0 errors, 0 warnings; Marid book and binding are side quests, the strand is "Keystone" | pass |
| `derive_ages.py --check` (boot 7 dump), `gen_stage_locks.py --check` | up to date | pass |

### Open points

- R-X on a real age_9 dump: the next copy boot needs `debug.allowSimulate = true` in the copy's
  `config/firmages-server.toml` before the grants.
- Arcane Age quest book: the goal depends on the three magic keystones (`dev/offene-punkte.md`).


## 1.0 round C, mods-tech (2026-10-15, branch c1/mods-tech)

Fourteen mods of the locked list (Goblin Traders, Artifacts, MmmMmmMmmMmm, Ars Elemental, Ars Ocultas, Create: Extra
Gauges, Ars Elemancy, AeroEngine, Building Gadgets 2, Ars Énergistique, Psi, Draconic Additions, Alex's Mobs Continued
with Codxlib) on dev 673e118. Felix's test server stayed off and untouched; every boot ran on a copy
(`.wt/test-server-c1-mods-tech`, ports 25576 and RCON 25586, whitelist off, `debug.allowSimulate = true`), synced with
`packwiz-installer-bootstrap -g -s server` from `packwiz serve --port 8097` of the worktree, `-Xms3G -Xmx6G`. Six copy
boots: d0 and boot 1 gate off with dumps (derivation), boot 2 gate on with a new world (checks), boot 3 gate off
(derivation after the fixes), boot 4 gate on with a new world (`fa_dump`, `fa_dump_full`, `firmages recipes audit`,
`selftest all`), boot 5 gate on with a new world, grants and a 120 s hold, no debug command (log check).

| Check | Observed | Result |
|---|---|---|
| Ars Énergistique 2.1.1-beta against the pack's Ars Nouveau | loads, its 25 recipes and 16 items in the dump; the two source acceptors hidden | pass |
| Copy boot 3 (gate off, `simulate grant` age_0 to age_9, dumps) | 42,777 recipes (round A: 42,136); `derive_ages.py`, `gen_stage_locks.py --registry` and `gen_stage_locks.py`; second pass `derive_ages.py --check` and `gen_stage_locks.py --check` up to date | pass |
| `derive_ages.py` on that dump | 35,767 items; final dawn 4,169, age_0 962, age_1 6,812, age_2 4,552, age_3 2,284, age_4 1,235, age_5 1,340, age_6 1,596, age_7 584, age_8 680, age_9 786, disabled 10,767. Ars Elemental moves 87 items to age_4 to age_7 (fire and water essence, mark of mastery) | done |
| Registry delta (dump against `dev/data/registry.json` of 0.10.0) | items 34,969 to 35,768 (+799: alexsmobs 280, ars_elemental 167, ars_elemancy 105, psi 82, artifacts 49, aeroengineering 46, draconicadditions 26, arseng 16, buildinggadgets2 8, extra_gauges 8, sauce 6, goblintraders 2, ars_ocultas 1, dummmmmmy 1, plus one Every Compat hedge and one vanilla id); of the 797 new-mod items 466 visible, 331 hidden (alexsmobs 265, draconicadditions 23, ars_elemental 18, artifacts 9, buildinggadgets2 5); fluids +5; new namespaces bring 161 entity types, 60 block entity types, 20 menus, 14 recipe types | done |
| Copy boot 4 (gate on, new world, grants, dumps, audit, `selftest all`) | gate at age_9: 38,867 of 42,777 recipes, 3,910 dropped (disabled 3,903, superseded 7); `selftest all` 55 passed, 0 failed | pass |
| `poc_analyze.py --baseline <boot 3 dump>` on boot 4 (`--server-dir` of the copy) | first 98 PASS, 1 FAIL (G-4: `ars_elemental:netherite_upgrade` not accepted); after the acceptance 99 PASS, 0 FAIL | pass after fix |
| `check_log.py` on boot 5 | Done after 97.5 s from the first log line (server reported 20.9 s; round A 84.6 s on an idle machine, here with sibling worker servers on the same machine); FATAL 0, ERROR 8, STDERR 0, WARN 217, all documented; remaining 0 | pass |
| `validate_quests.py --style`, `gen_quests.py` | 1252 object ids, 0 errors, 0 warnings; the book is reproduced byte for byte | pass |
| `check_menus.py`, `gen_recipe_overrides.py --check --mods <copy>`, `gen_loot_silence.py --check --registries <dump>`, `gen_textures.py --check`, `gen_blood_moon.py --check`, `gen_firmament_night.py --check`, `gen_cold_forge.py --check`, `gen_way_sign_textures.py --check` | all pass (543 overrides, 504 loot silences, 0 problems) | pass |
| `gen_credits.py` | 248 mods (234 + 14) | done |

Fixes of the boot loop:
- Boot 1: `alexsmobs:gameplay/seal_reward` warned at every data load (vanilla fishing junk without origin). Seals do
  not spawn; the pack's copy of the table is empty.
- Boot 1: `derive_ages.py --scan` from a worktree read no vanilla jar (all vanilla loot, structures and biomes
  missing) and counted every Alex's Mobs species as spawning from Dawn (Rocky Roller tuff, Soul Vulture coal). Both
  fixed in the tool (vanilla jar next to `--mods`, `OWN_SPAWNS_OFF`).
- Boot 2: `poc_analyze.py` C-G found 264 armour upgrades of Ars Elemental and Elemancy that eat armour, and the
  roadrunner boots that eat leather boots: `recipes/age_5/ars_gear.js`, `recipes/age_1/alexs_mobs.js`. One Elemancy
  recipe still names the old type `ars_elemancy:armor_upgrade` and slipped past the first version.
- Boot 1: Psi's own CAD Assembler recipe came back through the osmium rewrite; the rewrite skips it now.
- d0: the Ars Ocultas altar needed a Drygmy shard (conjuration essence now); boot 2: two Ars Elemental bee recipes
  needed honeycomb (TFC beeswax now). Both outputs were hidden by the derivation before.
- Boot 1 and 2: the only remaining log line was "Can't keep up" right after `fa_dump` (a debug command stalls the
  server thread, as in round A); boot 5 without debug commands has none.

### Open points

- Client (brief "Technik- und Welt-Mods"): textures, Psi programmer and book, Alex's Mobs climate spawns in a real
  world, goblin offers, Artifacts in ruin chests, gadget deny list.
- Ars Elemental lightning parts and the Flashjack/Siren familiars stay hidden (flashing biome only).
- Quest strands for every new non-deco mod: next round.

## 1.0 round C1 (2026-10-15)

`--no-ff` merges of c1/harmonie, c1/mods-deco, c1/mods-tech, c1/boss, c1/sky-m10, c1/sky-m12 and c1/sky-m16a (in that
order) into dev, then pack 0.11.0 and firmages-core 0.11.0 (run on 2026-10-16). Generated files were taken from one
side in the conflicts and regenerated on copy dumps; code conflicts were merged by hand (`FirmagesCommands` selftest
suites `sky`, `crown` and `wayshrines` side by side, the three Jade providers, `ServerConfig` sections, `Numbers` from
c1/sky-m16a, one `"meteoric"` palette and one `MOD_ITEMS` table in `gen_textures.py`). Felix's test server stayed off
and untouched; every boot ran on the copy `.wt/test-server-c1-int` (`robocopy` without world, logs, local,
simplebackups and crash-reports; ports 25566 and RCON 25576; whitelist off; the copy's stale `kubejs/` deleted before
the first sync; synced with `packwiz-installer-bootstrap -g -s server` from `packwiz serve --port 8095
--refresh=false`, "Finished successfully"; `debug.allowSimulate = true` after each sync), each with a new world and
`-Xms3G -Xmx6G`. The copy is deleted.

| Check | Observed | Result |
|---|---|---|
| `gradlew build` (JDK 21) | BUILD SUCCESSFUL, 188 unit tests, 0 failures (first run: `EmiAgeViewTest` failed on the merged `age_namespaces.json` until `gen_stage_locks.py` ran on the new dump); `build/libs/firmages-core-0.11.0.jar` byte-identical to `mods/firmages-core-0.11.0.jar` (SHA-256 `3696e018…acbd`, also in `index.toml`) | pass |
| `runGameTestServer` in a fresh `run/gametest` | firmages 0.11.0; "All 81 required tests passed" (0.10.0: 53) | pass |
| Copy boot 1 (gate off, `simulate grant` age_0 to age_9, `fa_dump`, `fa_dump_full`) | 43,609 recipes (0.10.0: 42,136); `gen_stage_locks.py --registry` 36,628 items, 1,700 ore and rock blocks, 767 fluids, 124 grid-recipe namespaces; `derive_ages.py --scan --mods <copy>/mods` 25,522 loot tables, 107 loot modifiers, 225 biomes, 1,623 features, 122 structures, 1,608 templates | done |
| `derive_ages.py` on that dump | 36,628 items; 3,383 moved later, 4,609 never reachable (hidden); final dawn 4,171, age_0 963, age_1 6,952, age_2 4,716, age_3 2,286, age_4 1,235, age_5 736, age_6 1,579, age_7 595, age_8 671, age_9 779, disabled 11,945. First pass hid the boss's consecrated shard and Firmament Glass: CODE_DROPS entry added | done after fix |
| Copy boot 2 (gate off, same commands) | Done after 93.0 s wall clock (server 17.7 s); 266 mod files with 257 mods; FTB Quests 13 chapters, 339 quests; tutorial Wayshrine placed 436 blocks from spawn; `derive_ages.py --check` and `gen_stage_locks.py --check` up to date | pass |
| Copy boot 3 (gate on, grants, dumps, `firmages recipes audit`, `selftest all`) | gate at Dawn: 7,296 of 43,609 live; at age_9: 38,562 of 43,609, 5,047 dropped (disabled 5,040, superseded 7), 849 undetected (37 types, 117 serializers); Age coverage: all 20,512 ProgressiveStages Age item locks in an Age tag; `selftest all` 71 passed, 3 failed (`crown/library_loaded` 1 of 22, `crown/live_after_age_9`, `crown/v4a_chain`: the M15 end state, red by design until M11, M13, M14 and M15); sky 7 of 7 and wayshrines 6 of 6 pass | pass (crown by design) |
| `poc_analyze.py --baseline <boot 2 dump>` on boot 3 | 104 PASS, 0 FAIL at the first run, including G-1 to G-4 (37 types accepted), E-1, L-1 to L-5 (13 metals, 68 ore pieces), R-T age_2 to age_9 and R-X age_2 to age_9 (2,750 magic recipes left out) | pass |
| Copy boot 4 (gate on, grants, hold 120 s; no dump and no selftest, both stall the server thread) | `check_log.py`: 89.9 s from the first log line to Done (91.9 s wall clock, server 17.6 s); FATAL 0, ERROR 8, STDERR 0, WARN 241, documented noise 251, remaining 0 | pass |
| Copy boot 5 (release commit, gate on, grants, `starfall now`, `selftest all`) | FTB Quests "13 chapters, 341 quests, 21 reward tables"; Starfall data loaded (Ages age_2 to age_7, pity 8 nights, old craters 0.35); `firmages starfall now` without a position finds no anchor (no player online, no shrine) and places nothing; `selftest all` 71 passed, 3 failed (crown, as boot 3) | pass |
| `gen_quests.py`, `validate_quests.py --style --mods <copy>/mods` | 1,258 object ids, 0 errors, 0 warnings (two new quests: "The Primordial", "A Piece of the Sky") | pass |
| `check_menus.py`, `gen_textures.py --check`, `gen_way_sign_textures.py --check`, `gen_multiblock_templates.py --check --mods <copy>/mods`, `gen_recipe_overrides.py --check --mods <copy>/mods`, `gen_loot_silence.py --check`, `gen_cold_forge.py --check`, `gen_blood_moon.py --check`, `gen_firmament_night.py --check`, `gen_ponder_schematics.py --check`, `gen_stage_locks.py --check` | menus pass; 291 textures 0 errors 0 warnings (after limiting the tile check to `block/` and `item/`; c1/boss alone gave 8 errors on its 128 px GeckoLib skins); 34 way sign icons; 35 templates, 7 pack copies; 543 overrides 0 problems; 504 loot overrides 0 problems; Cold Forge 594 blocks; 56 and 45 files up to date | pass |
| `check_registry_parity.py dump` against the 0.10.0 gate-off dump (`scratchpad/baseline2`) | items 34,970 to 36,629 (+2,549: bibliocraft 1,462, alexsmobs 280, ars_elemental 167, connectedglass 119, everycomp 106, ars_elemancy 105, psi 82, artifacts 49, aeroengineering 46, firmages 35, …; −890: refurbished_furniture 470, everycomp 420 `rfm/`); blocks 29,226 to 29,688; entity types 1,062 to 1,228; fluids 762 to 768. Namespaces added: adchimneys, adpoles, aeroengineering, ahandfulofacorns, akashictome, alexsmobs, ars_elemancy, ars_elemental, ars_ocultas, arseng, artifacts, bibliocraft, buildinggadgets2, connectedglass, draconicadditions, dummmmmmy, extra_gauges, goblintraders, psi, sauce; removed: refurbished_furniture (Carry On registers no items) | expected |
| `release.ps1 -Version 0.11.0`, `export_cf.py` | first run stopped: CurseForge blocks API downloads of Skin Layers 3D (a manual download in the `.mrpack` export); after the Modrinth swap: `.mrpack` 474.9 MB, CurseForge zip 74.6 MB, server pack 501.8 MB, SHA-256 list; upload zip 4.8 MB, 12 swaps, 265 manifest files, only `firmages-core-0.11.0.jar` in overrides | pass after fix |
| Mods | 264 metafiles plus `firmages-core` (265 in the CurseForge credits, 0.10.0: 234): 33 added (with ForgeEndertech, Bookshelf, Prickle, Codxlib), 2 removed | done |

### Open points

- In the client (brief 0.11.0): the new mods by Age, The Primordial (model, animations, sounds, fight length), Starfall
  (streak, boom, crater, emissive core), Wayshrines (pointer, ghost, lighting), the balance numbers, the client
  measurement.
- Wayshrine offerings from the Arcane Age on are Lens Crown alloys without a recipe until M11 to M15: Felix decides on
  an interim offering (`dev/offene-punkte.md`).
- `selftest all` keeps three red crown checks until M15; H2A and H5A, quest strands and real quest rewards are later
  rounds.

## 1.0 round C1, review fixes (2026-10-16, branch c1/review-fix)

Nine review findings on dev 7083e59 (pack 0.11.0, firmages-core 0.11.0, version kept: 0.11.0 is not pushed or
uploaded). Felix's test server stayed off and untouched; every boot ran on the copy `.wt/test-server-review-fix`
(`robocopy` without world, logs, local, simplebackups and crash-reports; ports 25566 and RCON 25576; whitelist off;
the copy's `kubejs/` deleted before the first sync; synced with `packwiz-installer-bootstrap -g -s server` from
`packwiz serve --port 8096 --refresh=false` of the worktree, "Finished successfully", stale jars removed by the
installer; `debug.allowSimulate = true` after each sync), each with a new world and `-Xms3G -Xmx6G`. The copy is
deleted.

| Check | Observed | Result |
|---|---|---|
| `gradlew build` (JDK 21) | BUILD SUCCESSFUL, 193 unit tests, 0 failures (new: `FuelShareTest` 4, `WayshrineRulesTest.tutorialOnlyOnNewWorlds`; `WayshrineDataTest` on the interim offerings); `mods/firmages-core-0.11.0.jar` = `build/libs` jar = the copy's jar (SHA-256 `ee8a041d…`) | pass |
| `runGameTestServer` | "All 82 required tests passed" in four of five runs (new: `tutorialSiteKeepsClearOfPlayers`); one run failed `SkyGameTests.excavatorVeinAtAutomationAge` ("IE's save data is marked dirty"), untouched code, open point | pass (one flaky test) |
| Copy boot 1 (gate off, `simulate grant` age_0 to age_9, `fa_dump`, `fa_dump_full`, `fa_m3`) | 43,607 recipes; `gen_stage_locks.py --registry`, `derive_ages.py`, `gen_stage_locks.py`: age_map unchanged, the meteoric rod in `age_items/disabled`; `fa_m3` with the old predicates (first fix): banded iron and lazulitic intrusion 109 overworld biomes, rich auricupride 8, alluvial sift and super vein 1 (`tfc:river` only), then widened to rivers and lakes | done |
| Copy boot 2 (gate on, new world, debug player, RCON) | tutorial Wayshrine 311 blocks from spawn; unlinked terminal with the class check opened ae2wtlib's `WCTMenu` (ae2wtlib 19.5.1 registers `ItemWCT` under the AE2 id) and closed it a few ticks later: the check now goes by id; alternator on a creative motor at 256 RPM: `balance alternator` share 0.0, LV capacitor below stays at 0 FE; `alternatorFuelOnly = false` (live config reload): 12,600 FE in about 75 ticks (168 FE/t); back to true: no further gain; a diesel engine could not be filled by command (NBT and bucket), so no in-world fuelled case | pass (fuelled case: client) |
| Copy boot 3 (gate on, new world, grants, debug player, dumps, audit, `selftest all`) | `balance cells`: 1k 126, 256k 126, portable 16k 36, MEGA 1M 126, fluid 64k 18; `debug useitem` with the unlinked terminal: `PortableCrafting$Grid`, still open after further commands; `fa_m3`: banded iron 109, lazulitic intrusion 109, rich auricupride 8, alluvial sift 11, super vein 11, mephitic quarzite 0 (Nether); gate at age_9 38,560 of 43,607, 5,047 dropped (disabled 5,040, superseded 7); `selftest all` 71 passed, 3 failed (the three crown checks, red by design until M15), wayshrines `offering_of_current_age`: "age_9 wants firmages:starsteel_ingot" | pass |
| Copy boot 4 (gate off, grants, `fa_dump`, `fa_dump_full`) | baseline 43,607 recipes; `derive_ages.py --check` up to date | pass |
| `poc_analyze.py --dump-dir <boot 3> --baseline <boot 4>` | 104 PASS, 0 FAIL (G-2: the gate drops nothing more) | pass |
| Copy boot 5 (gate on, new world, grants, hold 120 s, no debug commands) | tutorial Wayshrine 578 blocks from spawn; `check_log.py`: 75.8 s to Done, FATAL 0, ERROR 8, STDERR 0, WARN 241, documented noise 251, remaining 0 | pass |
| `gen_quests.py`, `validate_quests.py --style --mods <copy>/mods`, `check_menus.py` | 1,258 object ids, 0 errors, 0 warnings; menus pass | pass |

Notes:
- The tutorial Wayshrine found no fit site on the seed of boot 1 (16 bearings, slope or water; WARN, retried at the
  next start while the world is young); boots 2, 3 and 5 placed it 311, 538 and 578 blocks out.
- The portable cell mixin was dropped after boot 3: AE2 and MEGA portable cells never use 63 types.

## 1.0 round C2, harmony (2026-10-16, branch c2/harmonie)

Harmony packages A4 (ore ladder from the metal table), A5 (one iron), A6 (fuel table), H2A (Mekanism usage x10) and
the miners against the ladder, on dev ff67812 (pack 0.11.0). Felix's test server stayed off and untouched; every
boot ran on the copy `.wt/test-server-c2-harmonie` (`robocopy` without world, logs, local, simplebackups and
crash-reports; ports 25740 and RCON 25750, whitelist off; the copy's `kubejs/` and its Mekanism, More Machine and IE
server configs deleted before the first sync, so the shipped files are what loads; synced with
`packwiz-installer-bootstrap -g -s server` from `packwiz serve --port 8170 --refresh=false` of the worktree,
"Finished successfully"; `debug.allowSimulate = true` after each sync), each with a new world and `-Xms3G -Xmx5G`.
German write-up with all tables: `dev/harmonie-r2.md`. The copy is deleted.

| Check | Observed | Result |
|---|---|---|
| Copy boot 1 (gate off, `simulate grant` age_0 to age_9, `fa_dump`, `fa_dump_full`) | first try: KubeJS "'global' cannot be assigned to in client or server scripts" (`recipes/metal_table.js`), six recipe scripts failed; the table moved to `startup_scripts/`; then 43,641 recipes, KubeJS 0 errors; `derive_ages.py` hid `sgjourney:tollan_stargate` (its upgrade crystal still took `minecraft:iron_ingot` inside an `ingredient` wrapper) | fixed |
| Copy boot 2 (gate off, same commands, after the three JSON rewrites in `unify_iron.js`) | 43,641 recipes, 0 not encodable; `gen_stage_locks.py --registry`, `derive_ages.py`, `gen_stage_locks.py`: only `minecraft:iron_ingot` moves to `age_items/disabled`; second pass `--check` up to date | pass |
| Copy boot 3 (gate on, new world, grants, dumps, `firmages recipes audit`, `selftest all`) | gate at age_9: 38,582 of 43,641 recipes, 5,059 dropped (disabled 5,052, superseded 7); `selftest all` 71 passed, 3 failed (the three crown checks, red by design until M15) | pass (crown by design) |
| `poc_analyze.py --dump-dir <boot 3> --baseline <boot 2>` | 107 PASS, 0 FAIL at the first run: L-1 (13 metals, 68 ore pieces), L-2 best tech factor per Age 1.0 / 1.0 / 1.5 / 1.5 / 2.5 / 2.5 / 2.86-3.0 / 4.0 / 5.0 / 5.0, L-3, L-4, L-5, L-6 Mineshaft mean 20.75 mB, L-7 16 arc furnace dust recipes at 6,400 FE, U-2 no live recipe with `minecraft:iron_ingot` (394 take `#c:ingots/iron`), R-X age_2 to age_9 (2,783 magic recipes left out), G-1 to G-4 | pass |
| `gen_fuels.py --check --dump-dir <boot 3>` | up to date; every route within +-0.5 % of its table value; 6 IE generator fuels in the dump, all known | pass |
| `gen_machine_usage.py --check --server-dir <copy>` | up to date, 87 values off their default; 282 values read back from the booted copy equal the shipped files | pass |
| `miner_ladder.py --dump-dir <boot 3> --check` | 13 excavator mixes (2.46 to 22.5 mB per dig, mean 13.64); FE per ingot per step: Digital Miner -15.6 / -20.5 / -11.1 %, average excavator vein with energy upgrades -15.9 / -20.0 / -10.2 % | pass |
| Copy boot 4 (gate on, new world, grants, hold 120 s, no debug command) | `check_log.py`: 93.2 s to Done, FATAL 0, ERROR 8, STDERR 0, WARN 241, documented noise 251, remaining 0 | pass |

- Not run: `gradlew runGameTestServer` (no firmages-core code changed this round).
- Not checked in a world: the Energized Smelter's choice of the wrought iron recipe over the furnace recipe, the
  diesel engine and gas-burning generator output (data loads without an error), the Digital Miner and excavator rates.
## 1.0 round C2, core-balance (2026-10-16, branch c2/core-balance)
firmages-core on dev ff67812 (pack and firmages-core 0.11.0, version kept): H5A tally proof (SPEC §35), the rite
`energy_consume` (§36), `balance.feCap` (§37), `/firmages energy audit` and `/firmages ore audit` (§38), The
Primordial by numbers (§39), the Wayshrine density measured (§28). Felix's test server stayed off and untouched; every
boot ran on the copy `.wt/test-server-core-balance` (`robocopy` without world, logs, local; port 25900, RCON 25910;
whitelist off; synced with `packwiz-installer-bootstrap -g -s server` from `packwiz serve --port 8093` of the
worktree, "Finished successfully"; `debug.allowSimulate = true`; the worktree's jar copied over the synced one; a
copy-only `kubejs/data/firmages/firmages_shrine/tallies.json` asked for 16 steel ingots instead of 512, and the copy's
`feCap` named an IE creative capacitor at 300 FE/t), each boot on a new world with `-Xms4G -Xmx5G`. The copy is
| `gradlew build` (JDK 21) | BUILD SUCCESSFUL, 211 unit tests, 0 failures (new: `OreLadderTest` 7, `FeCapsTest` 3, `FightModelTest` 6; `ShrineRulesTest` tallies and `energy_consume`) | pass |
| `runGameTestServer` (fresh `run/gametest`) | "All 87 required tests passed" in three runs in a row after the race fix of `excavatorVeinAtAutomationAge` (before it failed 2 of 2: the age_6 grant's own reconcile had added the vein). New: `BalanceGameTests` (an LV capacitor capped at 50 FE/t pushes 50 a tick; the audit shows the LV at out 256.0 and the MV at in 256.0 FE/t; the ore audit reads the table), `offeringWaitsForProof`, `heartDrawsPower`; boss phases at 69 % and 38 %, pools 13,000 / 22,100 / 40,300, a 1000 hit counted as the cap | pass |
| Copy boot 1 (seed 1001; grants age_0 to age_4, `shrine build 8 229 8 4`, debug player) | the Pressure Core on the open plinth: refused, chat "Caelum asks for proof of work: your Tally Chutes must count 16 Steel Ingot in the Industrial Age. They have counted 0."; a Tally Chute placed by the player between a hopper and a chest: `passed: 16L`, `shrine status` "counted 16 by the leading team (met)"; the Core again: "Offering for tier 4 laid"; `ore audit` 43,607 recipes, 608 measurements, per Age best tech 1.0 / 1.0 / 1.7 / 1.7 / 2.0 / 3.0 / 2.857 to 3.0 / 3.81 to 4.0 / 5.0, magic 2.0 from age_3, L-1 to L-4 clean, 817 ms; `selftest all` 72 passed, 2 failed (the crown checks red by design until M15) | pass |
| Copy boot 2 (seed 1001, census boot with extras) | IE creative capacitor under an MV capacitor, `feCap` 300: `energy audit 6 5` capacitor_creative out 300.0, capacitor_mv in 300.0 FE/t, MV holds 103,200 FE after the run; `origin fight` with The Origin's altar chunks forceloaded: "The Primordial wakes ... (effective health 13000); fight model (Chaotic sword +2 damage modules, 49.5 per hit, 40.0 to the pool, 2.0 hits/s, uptime 60%): 1 player 8.4 min, 2 players 7.2 min, 4 players 6.5 min"; 30 s later the wipe | pass |
| Copy boots 2 to 21 (seeds 1001 to 1020, `wayshrine census 4000` shipped and at 64/24, 72/27, 96/36, 112/42) | seeds in 7 to 11: 80/30 7 of 20 (mean 7.0, 2 to 15), 64/24 8 (10.9, 3 to 20), 72/27 11 (10.0, 5 to 16), 96/36 6 (4.7), 112/42 2 (3.6); each census under 0.2 s | 72/27 chosen |
| Copy boot 22 (final jar, spacing 72/27, seed 1001, all of boot 1 and 2) | census shipped 9 (= the 72/27 variant of boot 2), refusal and acceptance as in boot 1, audit 300 / 300 FE/t, ore audit as in boot 1, boss and model line, `selftest all` 72 / 2 (crown); `check_log.py`: 70.7 s to Done, FATAL 0, ERROR 8, STDERR 0, remaining 14: the debug commands' own lines (rings 5 to 8 missing after `shrine status`, the two crown checks, one "Can't keep up" during `selftest all`) and 2 ERROR "DISTXFORM ... LocalPlayer" from TFC's "This is fine" probe (open point: noise list) | pass |
| `gen_quests.py`, `validate_quests.py --style --mods <copy>/mods`, `check_menus.py`, `gen_wayshrines.py --check` | 1,258 object ids, 0 errors, 0 warnings; menus pass; 89 files up to date | pass |
- `dev/scan_regions.py` was not used for acceptance 2: it reads generated region files and would need a pregen of
  4,000 blocks around spawn (about 250,000 chunks) per seed. `/firmages wayshrine census` lists the same candidates
  as `locate` and asks the structure manager, which decides a start for a chunk without data from the structure's
  own check (biome, height) and generates nothing; boot 22 confirms its spacing variant against the shipped data.
- The first census loop was started twice by mistake and ran two servers on one copy for about four minutes; both
  were stopped and the world deleted before the counted loop. Its numbers are not used.
- `balance.feCap` applies to new capability lookups only; Mekanism's strict-energy path is compiled and wired, the
  live measurement used IE capacitors (FE).
- The fight model's assumptions (uptime, veil share) wait for a recorded fight with 1, 2 and 4 players.
- Wayshrine density at 72/27 still spreads 5 to 16 per seed.
## 1.0 round C2, wood classes (2026-10-16, branch c2/holz)
Wood scheme HW1A (`dev/wood_classes.toml`, `dev/gen_wood_classes.py`, `recipes/age_0/wood_classes.js`) with Felix's
addition that vanilla trees generate and grow. Felix's test server stayed off and untouched; every boot ran on the
copy `.wt/test-server-c2-holz` (ports 25640 and RCON 25650, whitelist off, `debug.allowSimulate = true`), synced with
`packwiz-installer-bootstrap -g -s server` from `packwiz serve --port 8140 --refresh=false` of the worktree, each with
a new world and `-Xms5G -Xmx5G`. The copy is deleted.
| Forest-entry PoC | TFC's `tfc:forest` takes any configured feature as `normal_tree` of a `tfc:forest_entry` and checks the entry's climate itself; a second forest with only vanilla entries, added by a NeoForge biome modifier (`fluid_springs`) to the 100 TFC biomes whose large features hold `tfc:forest`, places vanilla trees by TFC climate. No code wrapper needed | pass |
| Loot (`loot spawn ... mine` with an iron axe) | natural TFC ash -> Oak Log, AFC kauri -> Spruce Log, TFC spruce -> Spruce Log, TFC kapok -> Jungle Log; copper hammer -> 3 Stick; placed ash log (`branch_direction=none`) -> Ash Log, placed ash wood -> Ash Wood, placed Beneath crimson -> its own log; AFC `ancient_ash` 2 of 3 rolls Oak Log (70 %) | pass |
| Felling (debug player, stone axe, `firmages debug break`) | whole TFC kapok: 23 item entities of Jungle Log, 0 Kapok Log; whole TFC aspen (final build): 3 entities of Birch Log, 0 Aspen Log | pass |
| Vanilla sapling growth | birch sapling on TFC mollisol grass over granite, `randomTickSpeed 3000` for 8 s: a birch tree (log at +0 and +3), the grass under it TFC mollisol dirt | pass |
| Copy boot 2 (gate off, grants, `fa_dump`, `fa_dump_full`) | 43,805 recipes; `wood classes: 208 recipes, 90 lead-species recipes take the class tags` | pass |
| Copy boot 3 (gate off, new world, grants, dumps) | 43,806 recipes; `gen_stage_locks.py --registry`, `derive_ages.py --scan` (25,522 loot tables, 1,651 features, 1,608 templates), `gen_stage_locks.py`; `derive_ages.py --check` on this dump: up to date. A StackOverflowError in a worldgen worker (the mangrove class selector had the path of the mangrove tree and placed itself) stalled chunk generation: fixed in 6f44e6d, see below | pass after fix |
| Age data | visible items 24,682 -> 25,245; vanilla-wood items newly visible 986 (age_0 110, age_1 559, age_2 180, age_3 125, Dawn 12), 286 moved earlier from age_9, 114 newly hidden by style parity (Macaw's Doors 64, Macaw's Windows 44, Create Encased 6); Every Compat TFC oak/mangrove twins hidden 283. Per Age: Dawn 4,205, age_0 1,148, age_1 7,490, disabled 11,383. Vanilla logs and saplings Dawn; planks, crafting table, bamboo planks, doors and trapdoors age_0; crimson and warped stems age_3 | pass |
| Copy boot 5 (gate on, new world, `simulate grant age_0`, `fa_dump`) | gate at Stone Age: 7,488 -> 8,940 live recipes; 195 of 209 `firmages:wood/*` live (the 14 missing make Nether wood, age_3); `firmages:wood/planks/oak` (saw + `#minecraft:oak_logs` -> 2 oak planks), `crafting_table`, `planks/bamboo` with item lock `age_0`, recipe lock `age_0` | pass |
| Copy boot 5, all Ages (`fa_dump`, `fa_dump_full`, `firmages recipes audit`, `selftest all`) | 39,409 live of 43,806, 4,397 dropped (disabled 4,390, superseded 7); `selftest all`: 71 passed, 3 failed (the three crown checks red until M15, as before) | pass |
| `poc_analyze.py --dump-dir <boot 5, age_9>` | 111 PASS, 0 FAIL ("all checks PASS"), among them W-1 (86 loot tables), W-2, W-3, W-4 (209 grids against 18,813 grid recipes, each one recipe), W-5, W-6 (8 allowlist patterns), W-7, W-8; visible vanilla-wood deco: mcwfurnitures 170, handcrafted 165, mcwwindows 117, mcwstairs 80, another_furniture 77, mcwroofs 77, storagedrawers 77, mcwdoors 66, mcwfences 66, mcwtrpdoors 62, mcwbridges 61, bibliocraft 49, decorative_blocks 43, create 22 | pass |
| `check_log.py`, boot 5 (pregen and debug commands) and boot 6 (final data) | boot 6: 70.1 s to Done, FATAL 0, ERROR 11, STDERR 0, WARN 241, documented noise 254, remaining 0; boot 5: remaining 8 = 5 "Can't keep up" during Chunky and the three crown selftest lines | pass |
| `gen_wood_classes.py --check`, `gen_stage_locks.py --check`, `gen_quests.py`, `validate_quests.py --style --mods <copy>/mods`, `check_menus.py` | 267 data files up to date; stage files up to date; 1,260 object ids, 0 errors, 0 warnings; menus pass | pass |
Vanilla trees per sample area (Chunky square, `scan_regions.py`, full chunks; log blocks, vanilla against TFC/AFC).
Four worlds with different seeds; the windows of dark oak and cherry were widened before world D (decision rows).
| World | Area (x z, radius) | Vanilla logs | TFC/AFC logs (main species) |
| A | 0 -15000, 192 | spruce 723 | pine, spruce |
| A | 0 0, 192 | oak 594, birch 544 | 13,364 (white cedar, birch, hickory) |
| B | 0 7500, 192 | jungle 196 | 22,811 (araucaria, kapok, teak) |
| B | 0 15000, 192 | oak 221, acacia 125 | 8,501 (araucaria, black oak, sequoia) |
| B | 12000 10000, 192 | jungle 470 | 3,430 (kapok, mahogany, tualang) |
| C | 0 -15000, 160 | spruce 615, birch 61 | 6,743 (douglas fir, pine, birch) |
| C | 8144 10064 (salt marsh), 160 | mangrove 74 (roots 101, propagules 7) | 370 (mangrove) |
| C | 8000 20000, 160 | oak 56, spruce 31 | 2,528 (beech, willow, aspen) |
| D | 4000 2500, 128 | dark oak 1,010, oak 159 | 7,713 (araucaria, willow, hickory) |
| D | -2000 5000, 128 | cherry 125, acacia 85 | 1,405 (pine, kapok, mahogany) |
| D | 4000 5000, 128 | dark oak 1,035, cherry 160, oak 55 | 5,680 (araucaria, mahogany, kauri) |
| D | 10000 22500, 128 | spruce 586 | 5,221 (douglas fir, aspen) |
All eight classes generate; areas with forest carry 1 to 18 % vanilla logs (dark oak trees are log-heavy), oceans and
deserts none.
- First boot of the round (world A): a server watchdog crash, 120 s in one tick: TFC Regrowing Forests' leaf random
  tick (`RegrowthRecipe.tryToSpread`) loaded a neighbouring chunk synchronously from a forceloaded test platform while
  Chunky filled the worker queue. Not caused by the wood data (TFC leaves); later boots built the test trees in the
  spawn chunks without `forceload` and ran no Chunky task at the same time. Open point below.
- Vanilla dirt and grass in the world: none; vanilla tree features keep TFC soil (`force_dirt` false, TFC soil is in
  `#minecraft:dirt`), the custom mangrove uses TFC fluvisol.
- TFC Regrowing Forests can stall a tick by loading chunks from a leaf random tick at the edge of loaded land (seen
  with forceload plus Chunky); check whether players with a Chunky pregen can hit it.
- 18 Macaw's barn and glass trapdoors in vanilla wood are age_1, their Every Compat TFC copies age_2 (the vanilla
  glass trapdoor recipe is earlier); harmless, not aligned.
## 1.0 round C2, Dawn grid lock (2026-10-16, branch c2/gridlock)
Felix's HW2A (SPEC §19.3): the 2x2 inventory grid (and the Curios one) is closed in Dawn and opens with age_0; Dawn
crafts on the ground on a TFC placed-item pile. firmages-core stays 0.11.0 (network protocol 5 -> 6). Felix's test
server stayed off and untouched; the one boot ran on the copy `.wt/test-server-gridlock` (`robocopy` without world,
logs, local, simplebackups and crash-reports; ports 25611 and RCON 25621; whitelist off; the copy's `kubejs/` deleted
first; synced with `packwiz-installer-bootstrap -g -s server` from `packwiz serve --port 8111 --refresh=false` of the
worktree, "Finished successfully", the copy's firmages-core jar byte-equal to the worktree's; `debug.allowSimulate =
true`), new world, `-Xms5G -Xmx5G`. The copy is deleted.
| `gradlew build` (JDK 21) | BUILD SUCCESSFUL, 198 unit tests, 0 failures (new: `GridLockTest` 5) | pass |
| `runGameTestServer` | "All 85 required tests passed" (new: `gridLockedInDawn`, `groundCraft`, `curiosGridLocked`; Curios 9.5.1 now in the GameTest run) | pass |
| `runGameTestServer -PgametestTfc` | the three new tests pass against TFC (`groundCraft` through `TfcProbe.groundCraft`: a real `tfc:placed_item` with head and stick gives a stone axe through `useItemOn`, the pile is gone; a lone head comes back; without sneaking TFC takes a part back, no axe); 21 failures, all shrine, gate and ore tests of the known TFC-mode set (TFC replaces their vanilla stand-ins) | pass (expected failures) |
| First GameTest run | Curios mixin crash at load: `@WrapOperation` on `NEW ResultSlot` in `CuriosContainer.<init>` "(0/1) succeeded"; replaced by an `@Inject` at `TAIL` that swaps slots 0 to 4 for the lock slots (`GridSlots.replaceGrid`) | fixed |
| Copy boot (Dawn, debug player `gridtest`, pile set with `setblock tfc:placed_item` + `data merge block ... {inventory:{Items:[...],Size:4}}`) | head on the pile, stick in hand, `debug use` (no sneak): TFC laid the stick (slot 0) and the action bar read "Sneak and right-click with an empty hand: Stone Axe"; `debug use ... sneak` with an empty hand: SUCCESS, one `tfc:stone/axe/sedimentary`, the block is air; four straw: one `tfc:thatch`; a lone axe head: CONSUME, TFC handed the head back; one limonite powder (`tfc:crafting/yellow_dye`, locked in Dawn): no dye, the powder back | pass |
| Same boot, `selftest all` | 73 passed, 2 failed (the crown checks `library_loaded` and `v4a_chain`, red by design until M15); `server/grid_lock_hooked`: "locked true (until age_0), Curios grid hooked, TFC piles hooked" | pass |
| Same boot, `simulate grant age_0` | the debug player's chat: "Your hands have learned: the crafting grid is open (key.inventory)" (the client shows the key, E); log "The crafting grid is open"; `age_status` `gridLock=false` | pass |
| `check_log.py` on the boot's `latest.log` | FATAL 0, STDERR 0, remaining 5: the two crown selftest lines (by design), one "Can't keep up" during the test commands (sibling servers and builds running on the machine), one `everycomp-entries.toml ... Correcting` (a server-side file the pack does not ship) | pass |
| `poc_analyze.py` D-1 on a full dump of round C2 (copy of `.wt/test-server-sky-m11`, read only) | "42 fit, 2 allowlisted" (`tfc:crafting/stick_bunch`, `stick_bundle`: 3x3, Stone Age workbench) | pass |
| `gen_quests.py`, `validate_quests.py --no-jars --style` | 1,260 object ids, 0 errors, 0 warnings ("By Hand" in `STYLE_TEACHING`, 87 words) | pass |
| `gen_stage_locks.py --check` | 0 files out of date | pass |
- Not checked on a server: anything a screen shows (paint-over, title, recipe-book button, EMI "+", Curios screen,
  Mouse Tweaks, client prediction without flicker). Listed for Felix in `dev/client-test-brief.md` ("Dawn ohne
  2x2-Feld").
- The sync step ran `taskkill /IM packwiz.exe` to stop this worker's `packwiz serve`; that also ends any sibling's
  `packwiz serve` running at that moment (03:20). A sibling sync at that moment would have to be repeated.
## 1.0 round C2, sky M11 Sky Harvest (2026-10-16, branch c2/sky-m11)
firmages-core M11 (SPEC §26): Storm Rod, storm glass, Aurora Net, aurora light. Felix's test server stayed off and
untouched; both boots ran on the copy `.wt/test-server-sky-m11` (`robocopy` without world, logs, local, simplebackups
and crash-reports; the copy's `kubejs/` and quest folder deleted before the first sync; ports 25817 and RCON 25827
after 25640 was taken by a sibling copy; whitelist off; synced with `packwiz-installer-bootstrap -g -s server` from
`packwiz serve --port 8141 --refresh=false` of the worktree, "Finished successfully"; `debug.allowSimulate = true`),
`-Xms3G -Xmx5G`, each with a new world. The copy is deleted.
| `gradlew build` (JDK 21) | BUILD SUCCESSFUL; `AuroraRulesTest` 8 of 8 (night window, band at the reference height, cold and warm chance 0.5 and 0.25 over 4,000 cell nights, cell stability, rain, day, override, intensity, rod rates) | pass |
| `runGameTestServer` | "All 84 required tests passed" (new: `auroraNetFills`, `stormRodFusesSand` with the test storm override) | pass |
| Boot 1 (gate off, `simulate grant` age_0 to age_9, `fa_dump`, `fa_dump_full`) | 43,614 recipes (+7: rod anvil and press, Storm Rod, Aurora Net and the rest of the M11 data); `/fa_dump` wrote 0 fluid buckets: Rhino "redeclaration of var" in `dump.js`, fixed and checked after `/reload` (763 buckets); `gen_stage_locks.py --registry`, `derive_ages.py --scan`, `derive_ages.py`, `gen_stage_locks.py`, both `--check` up to date | done |
| Boot 1 world test (debug player `skytest`, spawn 400 / 208) | net placed at Y 151 by the player, override on: 35 mB after about 18 steps, `catching=true`; Mekanism pipe in pull mode drained it into a Mekanism tank (110 mB); rod placed by the player (two halves), a side hopper fed 16 sand into the upper half, two `sky storm strike`: 8 sand fused, the hopper below the basin took 8 storm glass | pass |
| `sky aurora band`, seed 1 | spawn -5.8 °C at Y 140; north: -7.9 (500 blocks), -10.3 (1,000), -11.1 (1,500), -11.4 (2,000), -13.5 (2,500), -15.7 °C (3,000); band threshold set to -10.0 | measured |
| `sky aurora band`, seed `firmament-m11` (boot 2) | spawn -800 / -1712 at -9.9 °C, band from 256 blocks north (-10.9 °C), coldest -20.1 °C at 4,096 | measured |
| Boot 2 (gate on, grants, two nets, four rods) | net at spawn (out of band) with override on: `reason` No aurora, empty; net 512 blocks north: 1,033 mB after about 14 minutes; a bucket filled from it (1,000 mB), a full bucket is not poured back | pass |
| Storm Rod rate (boot 2, four rods, test storm override) | thunder 37 strikes in 2,132 rod-seconds (expected 35.5 at one per 1,200 ticks); rain 2 strikes in 1,060 rod-seconds (expected 4.4); no lightning entity left, no fire on the tip | pass |
| `selftest all` (boot 2) | 74 passed, 3 failed: the three crown checks, red by design until M15 (`library_loaded` now 4 of 22) | pass |
| `fa_dump`, `fa_dump_full`, `firmages recipes audit` (boot 2, gate on) | gate at age_9: 38,563 of 43,613, 5,050 dropped (disabled 5,043, superseded 7) | done |
| `poc_analyze.py --server-dir <copy> --baseline <boot 1 dump>` | 103 PASS, 1 FAIL: G-2 `create_dragons_plus:sandpaper_polishing/ars_additions/cracked_sourcestone_wall` missing in the gated dump (age_3 output, no sky link; open point) | open |
| `check_log.py` (boot 2) | 138 s to Done; FATAL 0; remaining 11 in 7 signatures: `Can't keep up` after the dumps and selftest, two DUMMY block entity lines on TFC water at -755 62 -1857, one debug-player command typo, the three crown selftest lines | pass |
- TerraFirmaCraft refuses `/weather` (its `WeatherCommand` points at `/time set rain`), so the storm commands are a
  timed override that only rods read (`StormWeather`).
- The aurora light bucket derives to age_5 (the empty bucket is the Electric Age steel bucket); the fluid is age_4.
## 1.0 round C2, M13 Drifting Isles (2026-10-16, branch c2/sky-m13)
SPEC §30 (static isles, S5A) with the Wind Shrine on the isles (S6B). Felix's test server stayed off and untouched;
the boot ran on the copy `.wt/test-server-sky-m13` (`robocopy` without world, logs, local, simplebackups and
crash-reports; ports 25731 and RCON 25741; whitelist off; the copy's `kubejs/` deleted before the sync; synced with
`packwiz-installer-bootstrap -g -s server` from `packwiz serve --port 8131 --refresh=false` of the worktree, "Finished
successfully"; the copy's `firmages-core-0.11.0.jar` replaced by the branch build; `debug.allowSimulate = true`), new
world, `-Xms3G -Xmx5G`, driven over RCON. The copy is deleted.
| `gradlew build` (JDK 21) | BUILD SUCCESSFUL, 198 unit tests, 0 failures (new: `IsleLayoutTest` 5: determinism, band and clearance, high terrain skips, shrine roll 0.23..0.27 of 20,000 chunks, shape rules) | pass |
| `runGameTestServer` | "All 87 required tests passed" (new `IsleGameTests`: `shapeAndSkyLight`, `lodeReforms`, `lodeLockedBeforeAge4`, `fallbackPlacesWhenLoaded`, `pieceShrineByChunks`); the fallback test placed a cluster of four isles at 6008, -6008 only after the forceload, at most 64 blocks per tick | pass |
| `gen_textures.py --check`, `gen_stage_locks.py` | 296 referenced textures, 0 errors; age_4 stage file 215 ore overrides (+2), Age tags with the crystal (age_4) and skystone (Dawn) | pass |
| Copy boot, seed -3107454076715593504, spawn 160, -3440 | Done in 23 s; `isles locate`: cluster in chunk 7, -218, 56 blocks from spawn, large r20 top 249 tip 222 with lode, two small satellites (tops 235 and 257); `selftest isles` 8 of 8 (125 biomes in the isle tag; three clusters at 56, 264 and 273 blocks, the second with the Wind Shrine) | pass |
| Spread model | A Python model of `random_spread` 20/6 with the salt (`m13_spread.py` in the session scratchpad) gives the same three candidates (56, 264, 273 blocks); over 20,000 random seeds and spawn points within ±4,000 the nearest candidate lies at median 131, p99 249, max 310 blocks | pass (§30.11 criterion 2 by model plus one seed) |
| Generation under TFC (forceload of the cluster) | `execute if block`: skystone at the three isle centres, the lode at 120 222 -3480 (the tip), air below it; census of the main isle box by `fill … replace` on the copy: skystone 7,568, crystal clusters 31 (as planned), lode 1, TFC logs 22 and leaves 336 (two trees), podzol dirt 36 and grass 18 (two pockets), `#c:ores` 0, one other block (the lode re-formed during the census) | pass |
| Wind Shrine (S6B) | `isles shrine` and `wayshrine locate wind_shrine` at age_3 both name the core at 8 238 -3656 (264 blocks); after a forceload the worldgen piece had placed it: `Wayshrine wind_shrine … BlockPos{x=8, y=238, z=-3656} registered (ruin)`, `wayshrine list` shows it beside the tutorial Wayshrine | pass |
| Fallback check | after `simulate grant` up to age_4: `Drifting Isles: fallback check: isle start at 120 -3480, 56 blocks from 160, 88, -3440`; no fallback cluster recorded | pass |
| Lode in the pack at age_4 | debug player (survival) `debug break` on the lode: two firmament crystals (one stack of 2), the lode stands again, nothing left in the re-form queue | pass |
| `selftest all` at age_4 | 80 passed, 2 failed: `crown/library_loaded` (2 of 22 library recipes; the rest wait for M11, M14, M15 materials) and `crown/v4a_chain` (M15); `crown/live_after_age_9` skips below age_9 and stays red until M11, M14 and M15 bring their materials | pass (crown red by design) |
| Registry | `firmages dump registry`: 36,630 items, identical to `dev/data/registry.json` with the four isle ids added by hand before the boot | pass |
| `check_log.py` | Done 131.8 s after the first log line (sibling builds on the machine), FATAL 0, ERROR 8, WARN 324, documented noise 324; remaining 10: four "Can't keep up" (forceloads and Age reloads under load), two TFC `LocalPlayer` DISTXFORM lines with TFC's own "This is fine" follow-up, the two crown selftest WARNs, and the config corrections of `firmages-server.toml` (the new `[isles]` section, now in the pack's `config/firmages-server.toml`) and `everycomp-entries.toml` | pass (no isle line) |
Flight PoC (§30.7), from the jars of the copy (no client):
- **Create Jetpack 5.2.1** (flightlib 4.0.36): `heightAboveGroundLimit` defaults to -1 (no limit; the pack has no
  `create_jetpack` server config override), `air.seconds_per_tank` 450 (900 hovering), `speed.vertical` 0.4. The
  only altitude rule in flightlib (`FlightApiImpl.uprightAction`) is that optional ground distance, so a backtank
  jetpack climbs from Y 120 to Y 240 with air to spare.
- **Create Aeronautics 1.3.2 on Sable 2.0.6**: no altitude cap in Aeronautics, Simulated or Sable. Sable's default
  `DimensionPhysics` pressure is a Bezier through exp(-0.004 × (y − sea level)) points every 200 blocks from sea
  level up to the build top (Y 320), then down to 0 above it: pressure about 0.47 at Y 250 and 0.43 at Y 275 (sea
  level 63), so hot-air lift is a bit under half of sea level, not a ceiling. The envelope size needed at Y 275 is a
  client test.
- Open (client): one age_4 hot-air airship from Y 90 to Y 275 and back, one jetpack climb; the look of the isles and
  their glow from the ground at night; skystone lighting and AO with face culling (§30.4 PoC).
## 1.0 round C2, sky M14: the Launch Complex (2026-10-16, branch c2/sky-m14)
firmages-core with the Launch Complex (SPEC §31.3 to §31.7, notes §31.12) on dev ff67812. Felix's test server stayed
off and untouched; one boot ran on a copy (`.wt/test-server-sky-m14`, port 25670, RCON 25680, whitelist off,
`debug.allowSimulate = true`, fresh world), synced with `packwiz-installer-bootstrap -g -s server` from
`packwiz serve --port 8160` of the worktree ("Finished successfully", 2570 entries), the branch jar copied over
`mods/firmages-core-0.11.0.jar`, `-Xms5G -Xmx5G`. The complex was built by commands at y 200 (IE concrete pad, IE steel
scaffolding gantry, TFC fire brick trench open west, one port), a Dawn debug player placed the control and loaded probe
and two sampler modules by hand. The copy is deleted.
| `gradlew build` | exit 0; 208 unit tests, 0 failures (new: `ThrottleMathTest` 8, `LaunchDataLoaderTest` 7) | pass |
| `runGameTestServer` (fresh `run/gametest`) | "All 91 required tests passed" with the nine `LaunchGameTests` of batch `firmages_12_launch`; with `handFillsFuel` and the fuel-in-item check (ten tests) see the last row | pass |
| `gen_textures.py --check`, `gen_ponder_schematics.py --check` | 311 referenced textures, 0 errors, 0 warnings; templates up to date | pass |
| Complex from pack blocks | `launch status`: "pattern complete, rotation COUNTERCLOCKWISE_90", "Ports 1 [8, 200, 6]; trench opens west; sky open"; owner = the debug player who placed the control | pass |
| Fuel tag in the pack (`selftest launch` `fuel_tag`) | `#ad_astra:fuel` = 10 fluids: `ad_astra:fuel`, `ad_astra:cryo_fuel`, IP diesel, diesel_sulfur and kerosene, IE biodiesel, Create Diesel Generators diesel and biodiesel (with their flowing forms) | pass |
| Launch | countdown with 7000 mB diesel in the tank; "probe launched with firmages:sampler_module (flight work 12000, owner …)"; at ignition the tank fell to 3000 mB and the probe and one module left their slots; `launch_fx` payloads for phases 0, 1, 2, 3, 0 reached the player | pass |
| Flight on fuel | FLIGHT 185 ticks in: progress 185/12000, tank 2815 mB (1 mB per tick at step one) | pass |
| Return | `launch finish`: "probe returned with [4xfirmages:voidglass] (module firmages:sampler_module, team flights of it 1)"; outputs 4 voidglass, probe back in its slot, second module waiting with "wait FUEL" (2715 < 4000 mB) | pass |
| `selftest launch` | 7 passed, 1 failed: `module_recipes` (no recipe for control, cradle, port, probe and the modules: pack data of M15, expected) | expected |
| `selftest all` | 79 passed, 3 failed: `crown/library_loaded`, `crown/v4a_chain` (M11, M13, M15 as before) and `launch/module_recipes` (M15) | expected |
| `check_log.py` on the copy's latest.log | Done 125.7 s from the first line (server reported 21.6 s, sibling servers on the machine); FATAL 0; remaining only the three expected selftest lines, a "Can't keep up", RCON port busy, and the config correction of `firmages-server.toml` (new `[launch]` keys) and `everycomp-entries.toml` | pass |
| Hand fill with an IP diesel bucket by the Dawn debug player | `use` returned PASS six times and the tank stayed empty; the tank was then filled with `data merge`. IP registers `FluidBucketWrapper` for its buckets (javap), so the likely cause is the Age lock of the age_5 bucket for a Dawn player; the hand path itself is covered by the GameTest `handFillsFuel` (water bucket in the test fuel tag) | plausible |
| `runGameTestServer` after the fuel-in-item change and `handFillsFuel` | first run: 91 of 92, the known flaky `SkyGameTests.excavatorVeinAtAutomationAge` ("IE's save data is marked dirty", in `offene-punkte.md` since the 0.11.0 review); second run on a fresh `run/gametest`: "All 92 required tests passed", batch `firmages_12_launch` with ten tests | pass |
