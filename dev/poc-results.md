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
- **Initial-load-only recipes.** The boot load has 29 recipes more than every later reload (34,939 vs 34,910 at the
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

- **Gear in tech recipes.** About 30 recipes outside the magic mods still take finished vanilla tools or armour that
  a TFC world cannot make (Mekanism paxels and MekaSuit modules, Mystical Agriculture augments and gear, DE wyvern
  tools and modules, Apotheosis salvaging, SGJourney naquadah gear, `minecraft:netherite_*_smithing`). None is on a
  goal chain. Each needs a gear-rule decision (tool head, unfinished armour or sheet), so they are not changed.
- **Ad Astra stack sizes.** From the first reload on, 25 Ad Astra recipes (panels, plateblocks, factory and encased
  blocks of steel, desh, ostrum, calorite and iron) fail to parse: "Item stack with stack size of 64 was larger than
  maximum: 32". They are decorative blocks only; the cause (a stack-size limit in the pack) is not traced yet.
- **Rites with real power.** The lanterns and floodlights ran on stored energy written into their block entities; IE
  LV wiring to a generator was not built (it needs a player). The candle, chorus and star rites ran their real paths.
- **Grant stall** (2.65 to 2.68 s at age_4 and age_6 with 2 players), as in "Reload performance": still open; it needs
  the client test before the PS sync is coalesced.
- The client half (ceremony, ghost previews of rings 3..8, tooltips of the four new items) stays Felix's test.
