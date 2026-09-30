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
- Everything marked needs client: C1, C3, C8, C9, C11 (Dawn EMI view), C14, C17, C19, C20, C21 in `poc-checklist.md`.
