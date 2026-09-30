# PoC results: server side (section A of poc-checklist.md)

Date: 2026-09-30 (second pass the same day: Age map, open points, M0). Pack 0.1.0 on branch `dev`, Minecraft 1.21.1,
NeoForge 21.1.252, Temurin 21.0.12.1, `-Xms6G -Xmx8G`.
Server: `test-server/`, synced from `http://localhost:8080/pack.toml`, fresh world (`--wipe-world`, seed random,
spawn -2912 / -2236). No player joined. Everything that needs a player is marked **needs client**.

## Tools used (all in `dev/`, no downloads)

| Tool | Use |
|---|---|
| `run_server.py --hold N` | keeps the server running for RCON; lines appended to `test-server/logs/run_server-input.txt` go to the console (spark answers asynchronously, so RCON never sees its output) |
| `rcon.py "cmd" ...` | minimal RCON client; reads port and password from `test-server/server.properties` |
| `/fa_dump`, `/fa_recipe <regex>`, `/fa_dims` | console commands in `kubejs/server_scripts/debug/dump.js`: recipe and tag dump with all ProgressiveStages stages that lock each recipe, its output and its result item, plus `registries.json` (every item, block and fluid id); recipe inspector; dimension locks and ore-override count |
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

## Open points

- **Client half of M0.** EMI/JEI rebuild after a reload and the stall with players connected (recipe and tag sync) need a
  client; the reload-vs-hot-swap verdict above is provisional until then.
- **MekaTFC 0.1.0 recipes are broken or leak.** 10 of its osmium recipes fail to parse on every load (dissolution,
  injecting, purifying: old Mekanism JSON without `per_tick_usage`), so osmium ore pieces have no 3x/4x/5x chain. Among
  those that load are `minecraft:smelting`/`blasting` of osmium ore (the vanilla furnace for ores is a canon loser) and
  `mekanism:enriching` of ore pieces (2x enrichment is a loser). Doc 10 v3 leaves keeping MekaTFC to the PoC: either
  replace these with KubeJS recipes per grade or drop the mod for a pack osmium vein.
- **Chromite Age derived, not in the canon.** Firmalife chromite is age_4 because its only use, stainless steel, needs
  nickel (age_4); Doc 10 v3 section 8.1 has no chromium row.
- **Mowzie's Mobs boss drops** outside the spawn locks (Frostmaw ice crystal, Sculptor staff, Geomancer set, earthrend
  gauntlet) take the mod's age_0; the design docs give them no Age.
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
- Everything marked needs client: C1, C3, C8, C9, C11 (Dawn EMI view), C14, C17, C19, C20, C21 in `poc-checklist.md`.
