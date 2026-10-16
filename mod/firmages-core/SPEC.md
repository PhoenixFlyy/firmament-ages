# firmages-core: Implementation Specification

Version 0.1 of the spec, 2026-09-30. Target: Minecraft 1.21.1, NeoForge 21.1.252, Java 21, mod id `firmages`, package `dev.firmages.core`.
Player-facing summary (German): `Modpack-Planung/docs/11-firmages-core.md`. Research this spec is built on: `Modpack-Planung/research-raw/core-integration.md` (hook points read in source), `core-shrine.md` (shrine design), `core-setup.md` (build), `core-spec.md` (decisions taken while writing this spec).

Marking: **[verified]** means read in source or javap during research. **[PoC]** means it must be checked in the running pack before the feature is considered done. Everything else is design.

## 0. Scope

| Id | Feature | Status |
|---|---|---|
| core | Global Age state, Age registry tags, reload orchestration | build (base for everything) |
| m2 | Machine-recipe Age gate: no recipe whose output belongs to a locked Age works in any machine | build |
| m3 | Miner filter: automated miners only yield ores of unlocked Ages | build |
| m4 | Draconic reactor automatic refuelling (DE issue #2004) | build |
| m6+m9 | The Shrine: one multiblock that is Tribal Hearth and Singularity Altar, worship of a deity | build |
| m7 | Age transition ceremony | build (staged at the shrine) |
| m1 | Stage-aware prospecting | build (Felix, 2026-09-30); config default on |
| M7 | Consecration of the shrine (one unified structure: accepted rings turn into one consecrated block family with one accent per Age) and maintenance mode (consecrated blocks are unbreakable except in maintenance, they drop their stored original) | build (Felix, 2026-10-01); §17 |
| m10 | The Origin (datapack dimension `firmages:origin`, arena, Stargate Journey address, return gate) and the end-boss scaffolding (Origin Gathering trigger, `finale_won` on the tagged boss's death, FINALE ceremony) | build (0.4.0, §16); 1.0: the boss is the mod's own entity (package `boss`), Cataclysm and Gateways to Eternity left the pack |
| immersion | Silent join, no advancement UI or mod list, EMI and Jade by Age, HUD Age indicator, first-join welcome, TFC without thirst and without nutrition malus | build (Felix, 2026-10-13); §18 |
| M8 | Proof of work: the Tally Chute and the criteria `firmages:tally` and `firmages:silo_launch`; the Ponder scenes "Silo Hatch" and "Portcullis" and the quest picture link to Ponder | build (N7, 2026-10-15); §22 |
| music | Per-Age background music, silence during the Firmament nights' own track | build (N7, 2026-10-15); §23 |
| later | Own end-boss entity, world events per Age | not in this spec |
| no | Age Codex screen, team chronicle, lore fragments, Dawn flair | never. Progress UI is the FTB Quests screen (key K) |
| later, if needed | Own interactive quest overview | only if FTB Quests on K turns out not clear or interactive enough (Felix, note on idea 5). It would replace, not duplicate, the K screen. Not in this spec |

Ages in order: `dawn`, `age_0` (Stone) … `age_9` (Singularity). Signature items (KubeJS items, namespace `firmages`) for `age_0`..`age_8`: Hearthstone, Sky Disc, Steel Heart, Arcane Keystone, Pressure Core, Humming Core, Data Matrix, Star Chart, Quantum Core. Offering the Age-N item grants `age_(N+1)`.

## 1. Architecture

### 1.1 Principle: global first, per-mod only where needed

The pack has exactly one FTB team, so the team's stages are the server's stages. firmages-core keeps a **server-global Age set** (`AgeState`), mirrored from ProgressiveStages. The set drives:

1. **m2, one global recipe filter.** A mixin on `RecipeManager#apply` at TAIL classifies every recipe of the load (what it needs: output Ages, station Ages) into a master list and keeps only the recipes the current Ages allow. Every mod's machines and the client sync work on that live list.
2. **One Age swap per Age change (0.9.0).** Inside the m7 ceremony `age/AgeSwap` re-filters the master list of the last load, rebinds the three Age-dependent block tags, flushes the machine recipe caches and sends the recipes to the players, without a datapack reload (§3.3). The full `server.reloadResources(selectedIds)` runs only for `/reload`, `/firmages reload`, `/firmages ages sync`, the boot reconcile and with `gate.ageSwap = false`.
3. **Per-mod hooks only where output does not come from recipes, or where removing a recipe would damage world data:** the IE excavator (mineral mixes are rolled at worldgen and must never be removed), fake-player block breaking, prospecting tags, and the DE reactor.

ProgressiveStages stays responsible for per-player item use, pickup, block interaction, EMI hiding and ore disguise. firmages-core never re-implements these.

### 1.2 Evaluation of the global simplification (honest)

| Aspect | Finding | Consequence |
|---|---|---|
| Server stall | Measured on the test server (35,213 recipes): 4.2 s recipes + KubeJS apply, about 15 s total datapack load at boot. All `apply` phases run on the server thread [verified, javap]. Estimate 6 to 10 s tick freeze per unlock, about 10 times per playthrough. | `reloadResources` blocks the server thread in `managedBlock` [verified, 21.1.252 source], so no ticks and no outgoing keep-alives happen during the stall; the client's Netty `ReadTimeoutHandler(30)` drops players after 30 s without packets. Measured on the 0.8.0 pack server (`Modpack-Planung/research-raw/n2-perf.md` §3.1): 8.4 to 14.7 s per Age grant (median 9.0 s), above the 10 s trigger, so 0.9.0 replaced the reload on Age changes with the Age swap (§3.3). The full reload remains for `/reload` and the boot reconcile. |
| Client resync | A full reload sends tags and recipes to every client: JEI restarts and the creative tabs rebuild on the render thread (1.9 to 4.3 s measured in 0.8.0), and EMI reloaded twice per grant. The Age swap sends the recipe packet only (no tags): no creative-tab rebuild, and EMI reloads once (§3.3). JEI still restarts on the recipe packet alone (19.64 `StartEventObserver.onRecipesUpdatedEvent` restarts when the client has synced recipes [verified, javap]), about 1.5 to 2.1 s on the render thread. | The ceremony runs client-timed, so visuals continue during the rebuild. |
| Monotonic progress | Ages only get unlocked; a reload only adds recipes. In-progress work (AE2 patterns, Create sequenced items, TFC anvil plans, Mekanism cached recipes) never points at a removed recipe. | No invalidation in normal play. |
| AE2 | A crafting pattern for a locked recipe cannot be encoded (terminal needs the recipe). A pattern turns "Invalid Pattern" only after a revoke; the item is kept and re-decodes after the recipe returns [verified]. | Revoke = admin repair, documented as "restart afterwards". |
| EMI/JEI | Locked recipes never reach the client, which matches `show_locked_recipes = false`. There is no next-Age recipe preview in EMI. Book pages that reference locked recipes stay empty until unlock. | The quests on K must explain what comes next. Books are gated anyway. |
| Stale per-block-entity caches | Occultism `DimensionalMineshaftBlockEntity.possibleResults` and Ars `EnchantingApparatusTile` memo keep old results [verified]. | These only delay an unlock. Flush them with a generation counter (§4.6). |
| Code-added recipes after TAIL | IE arc-furnace recycling recipes are built later from crafting recipes and escape the filter. Create Dragons Plus writes 175 sandpaper recipes into `byType`/`byName` at the TAIL of `ReloadableServerResources#updateRegistryTags`. | Arc furnace: harmless, because their inputs are locked items. Everything added before the end of `updateRegistryTags` is caught by the late pass (§4.7, 0.2.1). |
| KubeJS | Server scripts run again on every full reload, not on an Age swap. | Scripts must stay idempotent, and recipe and tag scripts must be Age-independent: they declare what a recipe needs (§3.4) instead of branching on `FirmAges.isUnlocked`. |
| Operator `/reload` | Full datapack load; the gate classifies again and applies the current AgeState, and the new master list is what the next Age swap filters. | Consistent, no special case. |
| Boot ordering | Recipes load before `MinecraftServer` and SavedData exist. | Mirror file plus fail-strict fallback (§3.2). |
| Chemical outputs (Mekanism gases etc.) | Not mapped to Ages. | They pass the filter. The machines producing them are block-locked by ProgressiveStages; accepted gap. |

Alternatives, rejected as primary route:
- **Per-mod mixins:** L effort, more than 40 targets, no client sync, and they break on updates. The per-family hook list in `core-integration.md` §3 stays as the fallback for single gaps.
- **KubeJS output removal:** `UnknownKubeRecipe.hasOutput` only sees `getResultItem`, so it misses IE, Ars, MA, DE and Ad Astra, and it is slow in Rhino.
- **ProgressiveStages alone:** 3.0.5 has no machine gate [verified].

**Phase 2, the hot swap, is built (0.9.0, §3.3):** the trigger written here (stall above 10 s) was met in 0.8.0. Tags can be swapped after all: `Registry#bindTags` rebinds a registry's whole tag map in place [verified, javap], which is what a datapack load itself does, so m1 and m3 stay tag-based (`age/AgeOreTags`, §5) and need no per-mod fallback.

### 1.3 Package layout

```
dev.firmages.core
  FirmagesCore                      mod entry, registration, event wiring
  age/     AgeId, AgeIndex, AgeState (SavedData), AgeMirror, AgeService, ReloadScheduler, CacheGeneration
  gate/    RecipeGate, OutputSink, OutputExtractor, JsonOutputWalker, GateReport, extract/<mod>Extractor
  miner/   OreGuard
  reactor/ ReactorView, ReactorFuel, ReactorCycle (pure), ReactorLookup, ReactorFuelPort, ReactorItems,
           ReactorControllerBlock(+BE), ReactorRegistry                        (DE coupling in compat/draconic)
  origin/  OriginRegistry, OriginArena, OriginSavedData, OriginService, OriginEvent, OriginAccess, OriginSpawns, OriginSpawnList (§16)
  shrine/  ShrineHeartBlock(+BE), OfferingPlinthBlock, ShrineSavedData, ShrineStateMachine,
           TierDefinition, TierReloadListener, rite/*, Blessings, Sanctuary, Gathering (DE compat)
  ceremony/ CeremonyService
  net/     payloads + handlers
  command/ FirmagesCommands, SelfTest
  compat/  kubejs (plugin + binding), modonomicon (adapter), jade (FirmagesJadePlugin), geckolib, draconic (DraconicReactors),
           sgjourney (SgjGates), ie, occultism, ars
  client/  ShrineHeartRenderer, CeremonyPlayer, SkyEffects, MusicControl, PreviewBridge
  mixin/   RecipeManagerMixin; GameTestServerMixin (GameTests only); ie.MineralMixMixin; occultism.MineshaftMixin;
           ars.ApparatusMixin; ps.*; ftbquests.* (client, §18.8); ftbq.* (client, §18.9); tfcregistryapi.HelpersMixin (§20)
  perf/    RegistryScanScope (pure), TfcRegistryScan (§20)
  sky/     DeferredSites, ClimateSource(s), SkySavedData; Starfall: StarfallService, StarfallRules, CraterPlan (pure),
           CraterCarver, StarfallData(+Loader), StarfallSite, SkyRegistry, StarfallCoreBlock(+BE), StarfallHotCondition (§24, §25)
  text/    Numbers (player-text quantities, §24.3)
  config/  ServerConfig, ClientConfig, EarlyServerConfig
```

Rules:
- Every class that touches another mod lives under `compat/<mod>` or `mixin/<mod>` and is loaded only when `ModList.get().isLoaded(modid)`.
- Mod-specific mixins are skipped by `FirmagesMixinPlugin` (`IMixinConfigPlugin.shouldApplyMixin` checks `LoadingModList`).
- MixinExtras ships with NeoForge.

### 1.4 Dependencies (update `build.gradle` and `neoforge.mods.toml`)

| Mod | Pack version | Kind | Needed for |
|---|---|---|---|
| ProgressiveStages | 3.0.6 | required, compileOnly (libs/) | stage events and grant API |
| KubeJS | 2101.7.2-build.377 | optional, compileOnly (**add**) | binding `FirmAges` |
| Modonomicon | 1.120.7 | required, compileOnly (**add**) | ring multiblocks, ghost preview |
| Immersive Engineering | 12.4.2-194 | optional | `MineralMix` mixin |
| Draconic Evolution / Brandon's Core / CCL | 3.1.4.633 / 3.2.1.309 / 4.6.1.529 | optional (also in the GameTest run since 0.4.0) | reactor controller and fuel port, Gathering |
| Stargate Journey | 0.6.51 | optional, compileOnly (libs/, fetched from `mods/sgjourney.pw.toml`; also in the GameTest run) | The Origin's return gate (`compat/sgjourney`); the space location is plain data |
| Occultism | 1.224.5 | optional | mineshaft flush, extractor |
| Ars Nouveau | 5.13.3 | optional, compileOnly (**add**) | apparatus flush, extractor |
| Create, Mekanism, AE2, TFC | pinned in `gradle.properties` | optional | extractors, OreGuard drop override |
| Jade | 15.10.6 | optional, compileOnly (libs/, fetched from `mods/jade.pw.toml`; also in the GameTest run, M6) | shrine tooltip (`compat/jade`) |
| GeckoLib | 4.9.3 | optional, compileOnly (**add**, M9) | animated idol |
| FTB Quests/Teams | pinned | optional, compileOnly | quest data; the client opens the FTB Quests screen (`compat/ftbquests`, §18); FTB Teams is read for the one-team guard (optional) |
| FTB Chunks | 2101.1.22 | optional, compileOnly (maven, M10) | Starfall keeps clear of claims (`compat/ftbchunks/Claims`, §25.3); not in the GameTest run |
| TFCRegistryAPI | 1.2 | none (mixin only), compileOnly (libs/, fetched from `mods/tfc-registry-api.pw.toml`; in the GameTest run only with `-PgametestTfc`) | scan scope (§20) |
| EMI, Create Train Utilities, Corgilib, Patchouli, Cave Dweller ReEvolved | pack versions | none (by name: reflection, `@Pseudo` mixins, registry ids) | §18 immersion, §19 hardship; TFC and Patchouli join the GameTest run only with `-PgametestTfc`; Cave Dweller is never in it |

All entries use `ordering = "AFTER"`; everything except ProgressiveStages and Modonomicon is `type = "optional"`.

Mod updates of 2026-10-13 (ProgressiveStages 3.0.5 to 3.0.6, Stargate Journey 0.6.49 to 0.6.51, Occultism 1.224.4 to
1.224.5, Ars Nouveau 5.13.2 to 5.13.3) [verified, javap]: the mixin targets (`syncPlayer`, `sendStageSync`,
`onPlayerJoin`, the mineshaft `tick`/`possibleResults`, the apparatus `memo`/`getRecipe`) have the same bytecode, the
PS API, the SGJ classes of `compat/sgjourney` and the Occultism recipe results only gained members, SGJ data only
gained the Andromeda gate files. The 0.6.1 jar builds byte-identical against them, so its version stays.

## 2. Data model

### 2.1 Age registry tags (source of "which Age an item belongs to")

| Tag | Content |
|---|---|
| `firmages:age_items/<stage>` (item tag, one per `dawn`, `age_0`..`age_9`) | Every gated item, in exactly one tag: the earliest Age that makes it available |
| `firmages:age_blocks/<stage>` (block tag) | Ore and deposit blocks per Age, all grades and rock variants (m1, m3, OreGuard) |
| `firmages:age_fluids/<stage>` (fluid tag) | Molten metals and other fluid outputs (TFC heating/alloy, Create mixing) |

- **Location:** `FirmamentAges/kubejs/data/firmages/tags/...`. This is pack data, reloadable, and needs no mod rebuild to retune.
- **Generator:** `dev/gen_stage_locks.py` writes the tags and the ProgressiveStages stage files from the same 10-v3 material canon. It expands `mod:` entries (for example `mod:create`) from a registry dump (`/firmages dump registry`, §6). This is a hand-off to the workflow that owns `dev/` and `kubejs/`.
- **Untagged items count as always unlocked.** Coverage is therefore checked at startup: `AgeIndex` is compared with ProgressiveStages `LockRegistry.getInstance().getAllResolvedItemLocks()` and `getOreOverrides()` (public, non-API), and every item that PS locks but no age tag contains is logged.
- If all age tags are empty, the filter cannot know what to lock. It then logs ERROR, sets `GateReport.misconfigured`, and warns every operator at login. It never pretends to gate.

### 2.2 AgeIndex (runtime lookup, immutable, swapped atomically)

```java
record AgeIndex(Map<Item, AgeId> items, Map<Block, AgeId> blocks, Map<Fluid, AgeId> fluids, int generation)
```

- Built inside `RecipeManager#apply` from the `ResourceManager` argument with `TagLoader` for the `tags/item`, `tags/block` and `tags/fluid` directories. Tags are not yet bound on the initial load, so the index must not rely on them. Nested `#tag` references are resolved; only `firmages:age_*` tags and the tags they reference are built.
- An item found in two Age tags takes the earliest Age and is logged with WARN.
- `AgeIndex.current()` is a volatile static. Runtime helpers `AgeGate.isLocked(Item|Block|Fluid)` read it together with the `AgeState` snapshot.

### 2.3 AgeState (SavedData `firmages_ages`, overworld data storage)

```json
{ "unlocked": ["dawn","age_0","age_1"], "version": 7, "lastReloadGameTime": 123456 }
```

- It is the union of all Age stages over all teams, and with one team that is the team's set. `dawn` is always unlocked.
- **One-team guard:** at `ServerStartedEvent` and on team creation, if more than one team holds any `age_*` stage, log WARN and message operators. Behaviour stays "union".
- **One fellowship (0.9.0, `team.autoJoin`, default true, `compat/ftbteams/FtbTeamsCompat`):** one tick after a login (FTB Teams sets up the player team in its own login listener), a player who is not in a party joins the party that holds the most Age stages (`PartyTeam.join`, no invite needed). A server without a party gets one, "Firmament" (`TeamManager.createPartyTeam`): its founder is the player whose own team holds the most Ages (FTB Quests 2101.1.36 merges the founder's quest data into a party the founder owns [verified, javap `ServerQuestFile.playerChangedTeam`]), or the joining player while nobody has passed Dawn; every online player outside a party joins it. While the player who holds the Ages is offline, a newcomer reads `firmages.team.waiting` ("<name> holds this world's Ages. The fellowship gathers when they return.") and the party is founded at that player's next login. Joining prints `firmages.team.joined`.
- **Team carry-over (0.9.1, `compat/progressivestages/TeamCarryOver`):** ProgressiveStages 3.0.6 keeps stages per team and starts a founded party with its starting stages only (`TeamStageSync.onPlayerJoinTeam` only syncs the new team's stages and fires no event [verified, javap]; pack server, 0.9.0 review: 22 stages before founding, 0 after; GameTest: `[]` right after founding). AgeState is the truth: when a player enters a party (FTB Teams `TeamEvent.PLAYER_CHANGED`, registered at mod construction when FTB Teams is loaded: founding, joining, an accepted invite), one tick later, when ProgressiveStages resolves the player to the party, the party gets every unlocked Age it lacks in Age order, plus `finale_won` once The Origin's boss fell (`OriginSavedData.won`), through `ProgressiveStagesAPI.grantStage` (bypass on refusal), the shrine's grant path: ProgressiveStages sends its lock and stage sync (one per player at the end of the tick, §3.5) and `grants.js` derives the mob ladder and the helper stages. AgeState does not change, so no ceremony, swap or Firmament Night follows. A party member's login runs the carry-over again (with or without `team.autoJoin`), which repairs a party founded without the carry-over. A player team whose player is now in a party is **dormant** (`FtbTeamsCompat.dormant`): its stages stay stored (`persist_stages_on_leave`), but it neither counts for the one-team guard nor adds Ages in the boot union; nor does a disbanded party (`FtbTeamsCompat.gone`). Only AgeState (the global mirror) is carried over, never what a dormant player team still stores. GameTests `TeamGameTests.foundedPartyKeepsTheAges`, `newcomerSeesTheAges`, `loginRepairsTheParty` (a party founded before the world reached age_9 gets it at a member's login) and `carryOverTakesOnlyTheMirror` (after an admin revoke the founder's dormant player team still stores age_9, and the login repair does not bring it back).

### 2.4 Mirror file `<gamedir>/<level-name>/firmages/ages.json`

- Same content as AgeState plus an ISO timestamp.
- Written atomically (temp file plus `Files.move ATOMIC_MOVE`) on every change.
- Read during the initial recipe load, before SavedData exists. On a dedicated server, `level-name` comes from `server.properties`.
- Singleplayer and dev clients do not read it; they use the fail-strict fallback in §3.2.

### 2.5 Shrine data

> **Superseded (original plan, kept for reference):** the heart's block entity held `ItemStack[9] relics`, `keystoneLent`, a catalyst, a 100,000,000 FE `EnergyStorage`, a stored `Phase`, `validRing`, `Rotation`, the praying UUIDs and the prayer ticks; the heart item carried the relics as the data component `firmages:shrine_contents`. The implementation (0.3.0, unchanged in 0.3.1) keeps the relics on the plinths instead and has no energy buffer, Keystone flag or catalyst yet (M6, M8). Details and reasons: §7.8.

As implemented:

- **SavedData `firmages_shrine` (`ShrineSavedData`):** `GlobalPos heart`, `boolean intact`, `int lastValidRing`, `long lastValidated`, the relic records (plinth position, tier, item id) and the stages the shrine granted (re-granted on login for per-player modes). One shrine per server. M7 (§17): `originals` (position, ring, original block state as `ns:id[prop=value,...]`) of every consecrated position and `maintenanceUntil` (overworld game time, 0 = off).
- **`ShrineHeartBlockEntity`:** saves only `prayerProgress`, `awakeningUntil` and the `interact` rites done since the last awakening (`interacted`, the bell). Ring rotations, the valid ring, the praying players and the validation schedule are a cache rebuilt from the world. The phase is derived, not stored (`ShrineService.phase`).
- **`OfferingPlinthBlockEntity`:** one item, its tier, a relic flag and (M6) a `lent` flag; `OfferingPlinthRenderer` draws the item. A plinth with an item, or waiting for its lent relic, cannot be broken in survival. The SavedData relic record carries `lent` too (§7.4).
- **Heart item:** no data component; a broken and re-placed heart finds its plinths again.
- **Blockstates:** heart `awakened` (0..10), `ready` (bool), `lit` (bool), `maintenance` (bool, M7: drawn as a scaffold cage); plinth `awakened` (bool, holds a relic); consecrated blocks `accent` (0..8), the lamp also `lit` (§17). FTB Quests observation tasks read the heart's `ready`.

### 2.6 Data-driven shrine content

> **Superseded (original plan, kept for reference):** sparse rings anchored at the heart with tags such as `#firmages:shrine/any_cobble`, N = 0..9; tier files with a numbered `plinth`, an `offering` object, an `energy` rite and a `relic_returned` rite for the lent Keystone; blessings as attribute modifiers plus an XP multiplier with values in the server config. The implementation below differs; the details are in §7.8.

As implemented (0.3.0, rings 3..8 and the blessings of tiers 1..8 added in 0.3.1). One reload listener (`ShrineDataLoader`) reads every `data/<ns>/firmages_shrine/**.json`; ids sort, so a later namespace overrides (the pack can replace any file under `kubejs/data/firmages/...`). Invalid files are rejected one by one with an ERROR, unsupported rite types and blessing flags are WARNings.

- **Rings:** `data/firmages/modonomicon/multiblocks/shrine_ring_<N>.json`, N = 0..8 (ring 9, the Gathering, is M8). Type `modonomicon:dense`, top layer first, `0` = the heart (centre of the bottom layer), `P` = the ring's plinth, `_` = any block. Ring N is built in `age_N`, is (2N+5) blocks square and holds plinth N+1 on its border, N+2 blocks north of the heart (any rotation validates), so the rings nest without sharing a block. Rings 3..8 are 4-fold symmetric apart from the plinth. Block matchers use the pack tags `#firmages:shrine/<name>` (`data/firmages/tags/block/shrine/`), whose mod ids are optional entries checked against `dev/data/registry.json`. Materials per ring: §7.8.
- **Tiers:** `data/firmages/firmages_shrine/tier/ring_<N>.json`, one per N = 0..8:

```json
{
  "multiblock": "firmages:shrine_ring_3",
  "grants": "age_4",
  "plinth_key": "P",
  "rites": [ { "type": "blockstate", "key": "K", "property": "lit", "value": "true", "hint": "firmages.shrine.rite.candles" } ],
  "response": { "beam_color": "#9B59FF", "sky_tint": "#4A2A80", "particles": ["minecraft:soul_fire_flame"],
                "sting": "minecraft:block.enchantment_table.use", "lightning": true },
  "blessing": "firmages:attunement"
}
```

  `grants` must be `age_(N+1)` (else the file is rejected); optional `offering` (item id override), `prayer_ticks`, `response.voice` (lang key; default `firmages.shrine.voice.<granted stage>`). `sting` is any sound event id. `tier/fallback.json` (no `multiblock`; any free plinth within `plinth_radius` of the heart takes the offering) serves only a tier that has no own file, so a pack that removes a ring file does not block progression; with the shipped data no tier uses it.

Rite types:

| Type | Meaning | State |
|---|---|---|
| `blockstate` | All ring positions with pattern key K have property P = V | implemented |
| `interact` | A player used a block of a block tag inside the shrine since the last awakening (the bell) | implemented |
| `sky` | Night, the heart sees the sky, no rain | implemented |
| `players_praying` | At least `min` players pray at once; met at once when fewer players are online | implemented |
| `energy` | The ring blocks with pattern key `key` hold `amount` FE together (NeoForge `Capabilities.EnergyStorage.BLOCK`, asked without a side, then per side); the FE is not taken | implemented (M6) |
| `relic_returned` | The relic of tier `relic` is not lent (§7.4); `items` lists what else may come back instead of it (the Awakened Keystone). While this tier is worked the shrine lends that relic, and the tier's offering is refused while it is away | implemented (M6) |

  Shipped rites: ring 0 kindle the heart (`blockstate` `0` `lit`), 1 ring the bell (`interact`), 2 light the four lamps, 3 light the eight candles (`blockstate` `lit`), 4 power the four electric lanterns (`blockstate` `active`), 5 light the four floodlights (`blockstate` `active`: power plus a redstone signal) and charge the four HV capacitors `V` with 1,000,000 FE together (`energy`, M6), 6 the Chorus (`players_praying` 2), 7 the stars (`sky`), 8 the Chorus, the stars and the Arcane Keystone back on its plinth (`relic_returned` `relic` 3, `items` `firmages:awakened_keystone`, M6).
- **Consecration (M7, §17):** `data/firmages/firmages_shrine/consecration.json`: `roles` maps a ring matcher (`#tag` or block id, as in the ring's `mapping`) to one of the eight roles `stone`, `brick`, `pillar`, `lamp`, `metal`, `glass`, `trim`, `scaffold`; optional `patterns` override single pattern keys of one multiblock (`{"firmages:shrine_ring_1": {"G": "metal"}}`). The heart `0` and each ring's plinth key never change. The rings stay the build recipe in Age materials.
- **Offerings:** `data/firmages/firmages_shrine/offerings.json`, a flat object `{"age_0": "firmages:hearthstone", ...}`: key = the Age whose ring holds the plinth (ring N = `age_N`), value = the signature item; the prayer grants `age_(N+1)` (the tier's `grants`). Entries: `age_0` Hearthstone, `age_1` Sky Disc, `age_2` Steel Heart, `age_3` Arcane Keystone, `age_4` Pressure Core, `age_5` Humming Core, `age_6` Data Matrix, `age_7` Star Chart, `age_8` Quantum Core (all KubeJS items, `kubejs/startup_scripts/items.js`). An item that is not registered cannot be laid on the plinth; that Age then comes only from an admin grant. Any key other than `age_0`..`age_8` or a non-string value is an ERROR and stops reading that file (so no comment keys). A tier's `offering` overrides the entry for its ring. `dev/validate_quests.py` reads this file (or the pack override): items in the Age tag of their key (unregistered: warning), every `ring_N` tier grants `age_(N+1)`, every shrine Age's goal quest waits for the next Age with a gamestage task, shows the offering as icon and grants no stage; a file at the old path `kubejs/data/*/shrine/offerings.json` (which the mod never reads) is an error.
- **Blessings:** `data/firmages/firmages_shrine/blessing/<name>.json` with `name` and `description` lang keys, `flags` and (M6) `effects`. The only flag is `sanctuary` (Hearthward, §7.8); its radius grows per awakened tier from the server config (`shrine.sanctuary.baseRadius`, `perTier`). Effect types (§7.9): `attribute` (`attribute` id, `operation` `add_value`|`add_multiplied_base`|`add_multiplied_total`, `amount`), `mob_effect` (`effect` id, `amplifier`), `xp_bonus` (`amount`, 0.10 = +10 %); an unknown type or a bad value rejects the file. Every tier names one blessing: 0 Hearthward (sanctuary), 1 Skyreading (+5 % movement speed), 2 Iron Will (+5 % max health), 3 Attunement (+10 % XP), 4 Tireless Hands (+5 % block break speed), 5 Long Arm (+0.5 block interaction range), 6 Clarity (+5 % max health), 7 Starwalker (fall damage multiplier -0.2, safe fall distance +1), 8 Resolve (+5 % max health, +1 luck). The values live in these files (Doc 11: blessing values are data), not in the server config.

## 3. Core: AgeState and reload orchestration

### 3.1 Hooks

| Hook | Where | Action |
|---|---|---|
| `com.enviouse.progressivestages.common.api.StageChangeEvent` (NeoForge bus) [verified] | `AgeService.onStageChange` | For an Age stage: GRANTED adds it, REVOKED removes it. Deduplicate the same (stage, change) within 1 tick, because team sync may fire once per member [PoC]. Then persist SavedData and the mirror, `CeremonyService.onAgeChanged`, and `ReloadScheduler.request()` |
| `StagesBulkChangedEvent` [verified] | same | Diff it against AgeState and handle it as above |
| `ServerStartedEvent` | `AgeService.reconcileBoot` | If the snapshot used by the boot filter differs from the SavedData, or the mirror was missing, request one reload immediately (without ceremony) |
| `PlayerEvent.PlayerLoggedInEvent` | `AgeService.reconcilePlayer` | Union-check `ProgressiveStagesAPI.getStages(player)` against AgeState. If PS knows more, add the stages and reload |
| `ServerTickEvent.Post` | `ReloadScheduler.tick` | Runs the due Age swap (or full reload) |
| `ServerTickEvent.Post` | `StageSyncCoalescer.onServerTick` | Sends the ProgressiveStages stage syncs deferred in this tick, one per player (§3.5, `sync.coalesce`) |

### 3.2 Snapshot rules for the filter (`AgeState.snapshotForReload()`)

1. If the server is running, use the live AgeState.
2. At initial load on a dedicated server, use the mirror file.
3. If the mirror is missing or corrupt, or on singleplayer/dev, use `gate.bootFallbackStages` (default `["dawn"]`, keep it equal to the PS starting stages) and let `reconcileBoot` reload after start. This path is fail-strict and never fails open.

### 3.3 ReloadScheduler and the Age swap

- `request(reason)` (an Age change) sets `dueTick = now + gate.reloadDelayTicks` (default 60, 3 s into the ceremony) if nothing is pending. Otherwise it extends the due tick up to `gate.coalesceTicks` (default 100), so a quest reward that grants `age_x` and `mob_x` together causes one swap. `requestNow(reason)` (`/firmages reload`, `ages sync`, the boot reconcile) is due on the next tick and marks the run **full**. Requests that run together are full if any of them is; a request during a running swap or reload queues exactly one follow-up (full if any queued request was).
- On the due tick, the server thread sends `ReloadStatePayload(true)` and then runs one of two paths.
- **Age swap** (an Age change with `gate.ageSwap = true`, the default; `age/AgeSwap`):
  1. `RecipeGate.apply`: the master list of the last load (§4.8) is filtered for the new Ages, and `RecipeManager.replaceRecipes(live)` [verified public] replaces the live recipes. Recipes in the manager that the master does not know (added by code after the load) are kept as they are.
  2. `AgeOreTags.apply`: `tfc:prospectable`, `precisionprospecting:prospectable_mineral` and `mekanism:miner_blacklist` are rebound for the new Ages (§5).
  3. `compat/recipes/RecipeCaches.flush`: the server-side recipe caches of the pack's mods [verified, javap against the pack's jars]. Each mod is flushed only when it is loaded, through a nested class, so an absent mod's classes are never linked; a failure skips that mod only. Mekanism 10.7.19: `MekanismRecipeType.clearCache()` and `TagCache.resetTagCaches()`. Create 6.0.10: `RecipeFinder.LISTENER` and `RecipeTrieFinder.LISTENER` (`onResourceManagerReload`). TerraFirmaCraft 4.2.11: `Helpers.updateReloadableData(access, recipes)` (what its `onTagsUpdated` runs: `IndirectHashCollection.reloadAllCaches`, fluid heat, non-decaying outputs). Immersive Engineering 12.4.2: `CachedRecipeList.onTagsUpdated` (bumps its reload count). Mystical Agriculture 8.0.28: `RecipeIngredientCache.INSTANCE.onTagsUpdated` (by reflection). Since the 0.9.0 review also (all by reflection): FastSuite 6.0.7, whose `AuxRecipeManager` replaces the server's recipe manager and caches one `CachedRecipeList` per recipe type with 100 or more recipes, never cleared (the crafting table's 4-argument `getRecipeFor` reads it): its `cachedRecipeListMap` is cleared under its own monitor; Ars Nouveau 5.13.3, what its reload listener queues (`GenericRecipeRegistry.reloadAll`, which holds the Imbuement Chamber's list, `CasterTomeRegistry.reloadTomeData`, `BuddingConversionRegistry`, `AlakarkinosConversionRegistry`, `ScryRitualRegistry`); Immersive Petroleum 4.5.0 `RecipeReloadListener.lists` (distillation tower, coker, high-pressure refinery, reservoirs); AE2 19.2.18 `TransformLogic.clearCache` (in-world transforms); FramedBlocks 10.6.3 `FramingSawRecipeCache.get(false).update`. No `TagsUpdatedEvent` is posted: its listeners take it for the end of a datapack load, and NeoForge's `DataMapLoader.apply` consumes the load's data maps and throws on a second call [verified, dev GameTest]. A scan of every pack jar (jar-in-jar included) for classes that reference `RecipeManager` together with a reload listener, `AddReloadListenerEvent`, `TagsUpdatedEvent`, `ServerStartedEvent` or `OnDatapackSyncEvent`, and for subclasses of `RecipeManager`, found only these, plus listeners the swap already serves through its `OnDatapackSyncEvent` (megacells `CompressionService` and `LavaTransformLogic`, IE arc recycling, Sophisticated Core), More Mekanism Processing (a tag revision counter; tags do not change in a swap), ProgressiveStages (looks recipes up live) and client-side listeners (JEI). `RecipeCaches.stale` compares each cache it can read with what the mod's own rebuild makes from the live recipes, so the probe copies no mod rule (the 0.9.0 probe did and failed on fresh caches: Ars Additions keeps entity-less source spawner recipes in `DEFAULTS`, AE2 takes only the first ingredient of each transform recipe): FastSuite's per-type lists against every recipe of the type; Ars Nouveau's registries (every public recipe list of each), Immersive Petroleum's four maps and AE2's three transform sets (water, any fluid, explosion) are read, rebuilt by the mod's own code and read again; FramedBlocks builds a second framing-saw cache. A stale cache is fresh after the probe. `/firmages selftest server`, case `recipe_caches_fresh`. `/firmages recipes craft <id>` (`compat/recipes/CraftProbe`) asks the live manager for a crafting recipe the way the crafting table does; the case `crafting_table_follows_ages` runs it for up to 150 live and 150 Age-locked recipes (plain `ShapedRecipe` and `ShapelessRecipe`, spread over the id order): every live one finds a live recipe, no locked one finds itself or a recipe that is not live.
  4. `OnDatapackSyncEvent(players, null)` is posted (mods that sync recipe-derived data; a join posts the same event). Then every player gets `ClientboundUpdateRecipesPacket(rm.getOrderedRecipes())` and the recipe book. No tag packet is sent: item and fluid tags do not depend on the Ages. If a sync listener changed the recipe list, the Age-filtered list is restored (logged).
  5. The line `Age swap (<reason>) finished in N ms` gives the step times (filter, ore tags, caches, packets) and the recipe delta.

  The swap does not run when the last load has no master list (the gate or its late pass failed) or when it throws; the scheduler then reloads in full instead and logs it at WARN.
- **Full reload** (`/firmages reload`, `ages sync`, the boot reconcile, `gate.ageSwap = false`, or a swap that could not run): `server.reloadResources(server.getPackRepository().getSelectedIds())` [verified signature]. The gate classifies and filters inside the load (§4), and `AgeOreTags` binds the tags inside it (§5).
- When either completes (still on the server thread): `CacheGeneration.bump()` (§4.6), `ReloadStatePayload(false)`, `firmages:age_status` to everyone (`reloadPending` false unless a follow-up is queued), the mirror file, and the log line with the duration and the mode (`AgeService.lastMode()`: `swap`, or `reload` with the reason).
- **Clients, one EMI reload per Age change:** from the stage change until the swap or reload is done, `firmages:age_status` carries `reloadPending = true` (sent right after the request). Meanwhile the client mixin `mixin/psemi/EmiReloadHoldMixin` (HEAD of ProgressiveStages 3.0.6 `ProgressiveStagesEMIPlugin.triggerEmiReload()`, cancellable, `require = 0`, applied only with EMI loaded) holds back ProgressiveStages' own EMI reload, which would run with the old recipes. When `reloadPending` turns false, `client/immersion/EmiReloadGate` decides. If recipes came without tags (a swap), it calls `EmiReloadManager.reloadTags()`: EMI 1.1.24 waits in "Recipes synchronized, waiting for tags" [verified, javap], so this reloads EMI once with the new recipes and stages. If recipes and tags came (a full reload), EMI reloads by itself. If nothing came, the held-back reload runs. Stage changes without an Age change (mob stages, gamemode) are not held back.
- REVOKED swaps too (with `gate.reloadOnRevoke`) and sends operators the warning "Age revoked: restart the server to clear in-progress items".
- **Measured [dev GameTest, 2026-10-14]:** 4,746 recipes (vanilla, IE, Mekanism, DE). An Age swap takes 5 to 21 ms (median 9 ms over 26 swaps of one run); the full datapack reload with the same Ages takes 1.8 to 2.6 s. Both give the identical recipe id set (4,743) and identical ore tags (`AgeSwapGameTests.swapMatchesFullReload`, log line `GameTest Age swap vs full reload`). The pack server numbers belong in `dev/poc-results.md` once a server run has measured them.

### 3.4 KubeJS binding (`compat/kubejs`, plugin registered via `kubejs.plugins.txt`)

- `FirmAges.stationAge(type, age)`, `FirmAges.requireAge(recipeId, age)`, `FirmAges.untilAge(recipeId, age)` (0.9.0, `gate/RecipeMarkers`): called inside `ServerEvents.recipes`, they declare what a recipe needs. The gate's main pass drains them at the TAIL of `RecipeManager#apply`, so every load starts empty. `stationAge`: every recipe whose type or serializer is `type` needs `age` (the machine's Age), including the recipes the scripts add in the same event. `requireAge`: this recipe id needs `age` on top of its outputs' Ages (several calls add up). `untilAge`: this recipe exists only while `age` is locked (a stand-in that a later variant replaces; `byproducts.js` uses it for its byproduct-stripped copies). Unknown Ages or invalid ids become gate notes; markers that name no recipe are logged after the late pass.
- **Recipe and tag scripts are Age-independent (0.9.0):** an Age swap does not run them, so they add every recipe at every Age and declare its Ages with the three markers above. `grep -rn isUnlocked kubejs/server_scripts/recipes` must stay empty. `FirmAges.isUnlocked` stays for runtime handlers.
- `FirmAges.isUnlocked(stageId)`: boolean.
- `FirmAges.unlockedAges()` and `FirmAges.lockedAges()`: lists of stage ids.
- `FirmAges.lockedOreBlocks()`: block ids of `age_blocks/<locked>`, read from the tag JSON on the reload path, because tag events run before tags are bound.
- `FirmAges.prospectingEnabled()`: `prospecting.enabled` (true while the server config is not loaded yet).

### 3.5 Implementation notes (M1)

- **KubeJS tag pre-capture [verified, dev GameTest log]:** KubeJS 2101 runs `ServerEvents.tags` handlers while the server scripts load, not inside the tag loader. On a reload that happens at the start of `ReloadableServerResources#loadResources`; the mixin `ReloadableServerResourcesMixin` (priority 500) captures that load's resource manager first, so the answer uses the new tag JSON. On the initial load the handlers run before the resource manager exists, so the answer comes from an empty index. `AgeService.checkLoadAnswers` compares every answer given during a load with the final index and, if the initial-load answer was wrong, requests one reload right after start (fail-strict). A handler that reads tag contents makes KubeJS drop the pre-capture and run the handlers inside the tag loader instead, where the initial-load answer is right (M3, §5.1).
- **Boot reconcile:** the reload after start happens when the boot snapshot was used during the load and differs from AgeState, when ProgressiveStages teams hold Ages that AgeState lacked, or when a binding answer was stale. A missing mirror alone does not reload when the fallback equals AgeState, because that reload would rebuild identical data.
- **Bulk events only add Ages:** a `StagesBulkChangedEvent` is one team's view (for example a new player's own team on login), so it cannot prove that the union lost an Age. Ages are removed only by `StageChangeEvent` REVOKED (or `ages simulate revoke`).
- **Boot fallback config:** server configs load after the initial datapack load, so `gate.bootFallbackStages` is read directly from `<world>/serverconfig/firmages-server.toml` (the per-world override), then `config/firmages-server.toml` (where NeoForge 21.1 keeps server configs), then `defaultconfigs/` on a dedicated server; otherwise the default `["dawn"]` applies.
- **Reload cost (0.3.2, `dev/poc-results.md` "Reload performance"):** with the pack at age_9 and 2 players a reload stalls 6.5 to 7.2 s (8.0 s for the first one after a boot), below the 10 s limit, so the reload path stayed and the phase-2 hot swap was not built then (0.8.0 measured 8.4 to 14.7 s, so 0.9.0 built it, §3.3). ProgressiveStages 3.0.5 `syncPlayer` sends the lock sync twice per player (directly and again inside `sendStageSync`); `mixin/ps/SyncPlayerMixin` and `SendStageSyncMixin` skip the second one inside the same `syncPlayer` call only (`compat/progressivestages/LockSyncDedupe`, all injections `require = 0`, packet order unchanged). The `Age reload ... finished` line counts the skipped syncs. Every stage change outside a reload resynced each team member (about 0.3 s each), which froze the tick of an Age grant for 2.6 s with 2 players; see the next item.
- **Grant stall: one stage sync per player and tick (`sync.coalesce`, default true; Felix 2026-10-13):** ProgressiveStages 3.0.6 [verified, javap] answers every grant or revoke with `NetworkHandler.sendStageSync(member, getStages(member))` for each online team member (`StageManager.syncToTeamMembers`, `syncToPlayer`, `syncAllPlayers`, `TeamStageSync.syncStageGrantToTeam`, the FTB Quests hook); `sendStageSync` sends the lock sync (the 0.27 s item walk), then the `stage_sync` payload, the reveal policy and the entity visibility. `sendLockSync` has no other callers than `syncPlayer` and `onPlayerJoin`. An Age grant changes several stages in one tick (the Age, then `stages/grants.js` moves the mob ladder and the helper windows), so `mixin/ps/StageSyncCoalesceMixin` (HEAD of `sendStageSync`, cancellable, `require = 0`) hands each call to `compat/progressivestages/StageSyncCoalescer`: on the server thread it records the player UUID with the stage set of the latest call (`SyncBatch`: one entry per player, first-request order) and cancels the send; `ServerTickEvent.Post` sends one `sendStageSync` per player through the original method, so each player gets one `lock_sync` followed by one `stage_sync` with the state of the end of the tick. Packet order per player is the old one (lock, stage, reveal, visibility); what changes is that the per-change `stage_update` payloads, unlock toasts and other packets of the same tick (the ceremony payload) now come before that pair, and the client never sees the intermediate stage sets. Not deferred: calls off the server thread, calls inside a full sync (`syncPlayer`, `onPlayerJoin`; `LockSyncDedupe.inScope`), which keep their duplicate-lock-sync skip and drop the player's pending sync because they send the newer state, and the sends of the flush itself. A player who left before the flush gets nothing (logged); a respawned player (new `ServerPlayer`, same UUID) gets it. A stopped server drops the batch. The flush logs `ProgressiveStages stage syncs coalesced: N request(s) sent as M sync(s) in T ms` when it saved syncs. GameTest `stageBatchSyncsOncePerPlayer` (two debug players, 5 changes each in one tick, payloads recorded by the debug-player connection): nothing before the end of the tick, then exactly `lock_sync`, `stage_sync` per player, the stage sync equal to the server's final stages (the revoked stage absent); a `syncPlayer` in the same tick sends at once with one lock sync and replaces the deferred sync; with `sync.coalesce` off the same players get one pair per change (4 to 5). Expected in the pack: one lock sync per member per grant, about 0.3 s per member instead of 0.3 s per change and member (2.6 s with 2 players); not yet measured on the pack server.
- **First reload after a boot (firmages-core 0.6.x):** it is about 1 s slower than the next ones (pack logs of 2026-10-01: 9.4 / 8.3 / 7.9 s and 6.8 / 6.1 / 5.9 s). Almost none of it is firmages-core: the recipe gate takes 331 / 305 / 324 ms and the AgeIndex build 95 / 77 / 67 ms in the same three reloads; the rest is KubeJS (2.45 / 2.07 / 1.91 s), recipe parsing and the reload workers warming up. So the mod does not pre-touch its own paths. Instead `gate.warmupReload` (default true, dedicated servers only, never in the GameTest server; since 0.9.0 only with `gate.ageSwap = false`, because no Age change reloads the datapacks any more) requests one ordinary reload with the unchanged Ages `gate.warmupDelayTicks` after `ServerStartedEvent`; it is skipped when a boot reconcile reload is requested, a reload already ran or is pending, or a player is online or joins before it is due. The `Age reload ... finished` line now also names the reload number since start. ProgressiveStages' `ServerEventHandler.onPlayerJoin` (`EntityJoinLevelEvent`: login, dimension change, respawn) has the same duplicate lock sync as `syncPlayer`; `mixin/ps/PlayerJoinMixin` skips it the same way (fail soft, packet order unchanged, counted as "on join"). The reload path itself was already deduplicated from the first reload on (2 skips after the first reload with 2 players). The AgeIndex is not cached across reloads: it costs 40 to 95 ms, and a cache keyed on anything cheaper than reading the tag files could go stale.

## 4. m2: machine-recipe Age gate

### 4.1 Hook

- `mixin/RecipeManagerMixin`, target `net.minecraft.world.item.crafting.RecipeManager`, priority 1500 (above KubeJS 1100).
- `@Inject(method = "apply", at = @At("HEAD"))`: keep a reference to the JSON `Map<ResourceLocation, JsonElement>`. KubeJS rewrites this same map instance at its HEAD (`map.clear(); map.putAll(...)`) [verified], so reading it at TAIL gives the post-KubeJS JSON.
- `@Inject(method = "apply", at = @At("TAIL"))`: `RecipeGate.filter(this, json, resourceManager)` builds `AgeIndex`, drains the scripts' markers (§3.4), classifies every `RecipeHolder` into the master list (§4.8), and calls `this.replaceRecipes(live)` with the recipes the load's snapshot keeps, which rebuilds `byName` and `byType`.
- **[PoC]** Our TAIL must run after Cucumber's `cucumber$apply` (the code-added Mystical Agriculture recipes). The self-test counts MA infusion recipes in a locked Age; if any survive, raise the priority. **[verified, javap + pack server, 0.2.1]** `cucumber$apply` is an `INVOKE_ASSIGN` inside `apply` (after `ImmutableMap.builder()`), so the MA seed and reprocessor recipes are in the builders before our TAIL; they have no JSON, `getResultItem` judges them. Cucumber's `RecipeManagerLoadedEvent` at the RETURN of `updateRegistryTags` adds no recipes in the pack; the late pass would catch it if it did.

### 4.2 Output detection (`OutputSink` collects Items, item tags, Fluids, fluid tags)

1. Typed extractors, first match wins, loaded only when the mod is present:

| Extractor | Reads |
|---|---|
| `CreateExtractor` | `ProcessingRecipe#getRollableResults`, fluid results, `SequencedAssemblyRecipe` result |
| `IEExtractor` | `MultiblockRecipe` item and fluid outputs, including `TagOutput` |
| `MekanismExtractor` | the item/fluid output definitions of the recipe (`getOutputDefinition`) |
| `OccultismExtractor` | `MinerRecipe#getWeightedResult`, ritual result |
| `TfcExtractor` | `ItemStackProvider` result, `result_fluid` |

2. Otherwise `recipe.getResultItem(registries)` if it is not empty.
3. Always also `JsonOutputWalker` on the recipe's JSON, which catches everything else. It descends into keys matching `^(result|results|output|outputs|.*_output|.*_result|.*_fluid)$` and collects `id`, `item`, `fluid` and `tag` string values; counts and chances are ignored.

### 4.3 Decision (first rule that applies)

1. `gate.denyRecipes` lists the recipe: DROP.
2. `gate.allowRecipes` lists the recipe: KEEP. This handles byproduct cases where only a secondary output is locked.
3. `gate.exemptRecipeTypes` lists the type: KEEP. The default list is `immersiveengineering:mineral_mix`, `tfc:collapse`, `tfc:landslide`. It is world data and must never be removed.
4. A `FirmAges.untilAge` Age of the recipe is unlocked: DROP (bucket `superseded`: a later variant took over).
5. The recipe's station is locked: DROP. Stations are the `FirmAges.stationAge` types (matched by type or serializer), the `FirmAges.requireAge` Ages of the recipe id, and the Create heat levels (0.9.0, `n2-emi-leaks.md` P2). For `create:mixing` and `create:compacting`, `heat_requirement = heated` needs `tfcreate:primitive_heater` or `create:blaze_burner` unlocked, and `superheated` needs `create:blaze_burner`. Both Ages are read from the Age index (`GateRules.HEAT_SOURCES`); a source without an Age locks nothing, a disabled source is skipped. A station locks a recipe even when no output was detected.
6. Any collected item or fluid is in a locked Age: DROP. A tag output counts as locked only if all tag members are locked.
7. Otherwise KEEP. When no output was detected at all, record the type as "undetected" for the audit.

The bucket of a dropped recipe is the Age of its latest unmet condition (station or output), then `disabled` after `age_9`, then `superseded`.

Recipe inputs are never checked. Target cost: under 500 ms for 35k recipes; the time is logged on every apply.

### 4.4 GateReport

Kept from the last apply: per recipe type the totals kept, dropped and undetected; dropped counts per Age; the undetected recipe ids; timing; the misconfigured flag. `/firmages recipes audit` writes it to `logs/firmages-recipe-audit.txt` and prints a summary.

### 4.5 Coverage in one pass (all via RecipeManager)

- Vanilla crafting and smelting.
- Create: mixing, compacting, pressing, crushing, milling, deploying, sequenced assembly, mechanical crafting, fan processing.
- All IE machines and multiblocks; all Mekanism machines and factories.
- TFC: anvil, welding, heating, barrel, quern, knapping, casting, pot.
- Occultism: rituals, spirit fire, miner.
- Ars: apparatus, imbuement, crush; Theurgy.
- DE fusion, including the AE2 fusion autocrafter; AE2 patterns and inscriber.
- Mystical Agriculture infusion; Ad Astra machines.

Mekanism addon types (mekmm, Evolved Mekanism) are covered by the filter but unverified in source.

### 4.6 Transient cache flush (`CacheGeneration`)

- `CacheGeneration.bump()` increments a global int after each reload.
- `mixin/occultism/MineshaftMixin` injects at HEAD of the `DimensionalMineshaftBlockEntity` tick: if the stored generation differs, set `possibleResults = null`.
- `mixin/ars/ApparatusMixin` resets the `EnchantingApparatusTile` memo the same way.
- AE2 needs nothing (see §1.2).

### 4.7 Implementation notes (M2, firmages-core 0.2.0)

- **Classes:** `mixin/RecipeManagerMixin` (priority 1500, HEAD keeps the JSON map, TAIL calls `gate/RecipeGate.filter`), `gate/JsonOutputWalker`, `gate/OutputSink`, `gate/GateRules` (pure decision), `gate/GateReport`, `gate/TagView`, `gate/extract/{Create,IE,Mekanism,Occultism}Extractor`, `mixin/ie/TagOutputAccessor`, `mixin/occultism/MineshaftMixin`, `mixin/ars/ApparatusMixin`, `mixin/FirmagesMixinPlugin` (skips `mixin.ie|occultism|ars.*` when the mod is absent).
- **Tag outputs [verified, dev GameTest]:** tags are not bound at `RecipeManager#apply` (on a reload the bound ones are the previous load's). The gate reads the members from the load's own `TagManager` result, linked to the `RecipeManager` by a constructor hook in `ReloadableServerResourcesMixin`. That result is complete, KubeJS tag edits included, because the tag manager is the first reload listener and `SimpleReloadInstance` runs the apply phases in listener order [verified, source]. A tag output locks only if it has members and all are locked; an empty or unknown tag keeps the recipe (it outputs nothing). The recipe then returns with the tag's earliest member Age.
- **No tag resolution during the load:** `Ingredient#getItems`, IE `TagOutput#get` and Occultism `RecipeResult#getStack` cache their first answer in the recipe [verified, javap/source]. Extractors therefore read structure only (IE `TagOutput.rawData` through the accessor, Occultism `tag()`), and `getResultItem` is never called for IE and Occultism recipe classes. Other mods' `getResultItem` is called; a mod that resolves tags lazily there would cache an unbound result (none known in the pack).
- **Extractors:** Create `getRollableResults`/`getFluidResults` and the sequenced-assembly result pool; IE every `IESerializableRecipe` by a field scan (`TagOutput`, `TagOutputList`, `StackWithChance`, stacks; input-named fields skipped), which covers crusher secondaries and arc-furnace slag; Mekanism every public `get*OutputDefinition()` (items and fluids of the returned stacks and output records; chemicals unmapped); Occultism `MinerRecipe#getWeightedResult` and the `RecipeResult`/result-named stack fields of the other recipes. The first extractor that claims a recipe wins; the JSON walk always runs too. Extractor classes are only instantiated inside `ModList.isLoaded` branches (a constructor reference would link them and fail without the mod).
- **JSON walk keys:** SPEC regex plus `output(s)_*`, `result(s)_*` and plurals (TFC `result_item`, `output_item`); a key naming an input (`input`, `ingredient`, `catalyst`, `reagent`) is never an output key; `conditions`, `key`, `pattern`, `components`, `pedestal*` are skipped. Outside output subtrees the walker descends, so IE `secondaries[].output` and Create sequence steps are found. Values under a fluid key are fluids, `item` values items, everything else is checked against both item and fluid Ages (Create `{id, amount}` fluid results).
- **Disabled:** `firmages:age_items/disabled` (and `age_fluids/disabled` if present) is read into `AgeIndex` as "never unlocked": `GateRules` drops such outputs in every state (bucket `disabled`), `AgeGate.isLocked(Item|Fluid)` returns true.
- **Buckets:** "dropped per Age" counts a recipe under the latest locked Age among its outputs (the Age whose unlock brings it back), `disabled` after `age_9`, `denied` for `gate.denyRecipes`.
- **Config at boot:** `gate.enabled|allowRecipes|denyRecipes|exemptRecipeTypes` are read from the toml files during the initial load (`config/EarlyServerConfig`, same candidates as `gate.bootFallbackStages`). `gate.enabled = false` classifies and reports but keeps everything. Exempt matches the recipe type or the serializer id.
- **Failure:** an exception in the gate keeps the load's recipes unfiltered (a broken gate must not delete every recipe), logs ERROR, sets `RecipeGate.lastFailure()`, warns operators at login, and fails the self-test `gate_ran_with_live_ages`.
- **Commands:** `/firmages recipes audit` writes `logs/firmages-recipe-audit.txt` (summary, dropped per Age, types and serializers with no detected output, per type and per serializer totals, undetected ids with serializer, dropped ids per Age with the locking output, extraction errors); `recipes why <id>` prints verdict, reason, bucket, locking output and detected outputs; `recipes locked <age|disabled|denied>` lists dropped ids. `/firmages selftest gate` runs the pure `GateSuite`.
- **Cost [dev GameTest]:** 4,003 recipes (vanilla + IE + Mekanism) in 30 to 50 ms per reload, 143 ms on the first load (class loading). **Pack server (0.2.1, 35,114 recipes incl. 175 late):** 534 to 575 ms on the initial load (class loading of all extractors), 250 to 412 ms on every Age reload (main pass 237 to 388 ms, late pass 11 to 41 ms). Within the 500 ms target on reloads.
- **Late pass (0.2.1):** `mixin/ReloadableServerResourcesLateMixin` (priority 1500) injects at the TAIL of `updateRegistryTags`, after the tags are bound and after every `TagsUpdatedEvent` listener and Create Dragons Plus' own TAIL callback. `RecipeGate.lateFilter` gates every recipe the main pass did not see with the same settings, snapshot and report (JSON from `Recipe.CODEC`), and resolves the result of the IE and Occultism recipes the main pass kept: with bound tags their cached `getResultItem` is the item the machine really makes, so a recipe whose resolved result is locked is dropped too (`reclassify` moves it in every count). The report and the audit summary show both counts and the time is added to the gate time. Pack server: 175 late recipes (Create Dragons Plus sandpaper polishing; 175 dropped at Dawn, 0 from the Iron Age on), 0 to 5 resolved-result drops per Age. GameTest: `LateRecipeInjector` adds two smelting recipes from `TagsUpdatedEvent` on every load; the age_2 one is absent at Dawn and back after the age_2 reload.
- **Cache flush:** `MineshaftMixin` nulls `possibleResults` at the first tick after `CacheGeneration` changed (`mine()` only queries recipes on null [verified, javap]). `ApparatusMixin` captures the constructor's lookup function (`@WrapOperation` on `Memoizer.memoize`) and replaces `memo` with a fresh memo after a change; it targets Ars by name, so the build needs no Ars jar. Both are unexercised in dev (the mods are not in the dev run) **[PoC]**.

### 4.8 Master list and classification (0.9.0, the Age swap)

- `GateRules.classify` turns the rules of §4.3 into a `Classification` that does not depend on the Ages: the config verdict (deny, allow, exempt), the `untilAge` mask, the station clauses and the output clauses. A clause is a bit mask of Ages (bit = `AgeId.ordinal()`) and is met when any of them is unlocked. An item or fluid output is one bit. A tag output has the bits of its members (a member without an Age makes the tag always met, disabled members are skipped, all disabled gives mask 0 = never). The `heated` level has the bits of its two sources. `keep(mask)` is a few integer tests; `verdict(mask)` gives the reason, bucket and entry for the report and `recipes why`. The selftest `swap_keep_equals_verdict_at_every_age_set` checks both against each other for all 1,024 Age sets.
- `gate/RecipeMaster` holds every recipe of one load with its classification, detected outputs and late flag. The main pass builds it. The late pass (§4.7) completes it (recipes added after the apply; IE and Occultism results resolved after the tag bind, which become one more output clause) and publishes it for that `RecipeManager` (weak map, like the gate's other per-load state). It holds about the recipe set an age_9 server keeps loaded anyway.
- `RecipeGate.apply(manager, snapshot)` is the swap's filter: `master.live(snapshot)` in load order plus the recipes the master does not know, then `replaceRecipes`. The report (`/firmages recipes audit`, `why`, `locked`) is rebuilt from the master for the new Ages, so it always describes the live state.
- `RecipeGate.master(manager) == null` (the gate or its late pass failed on that load) makes the scheduler reload in full instead of swapping.

## 5. m3: miner filter, and m1: prospecting

| Target | Hook | Behaviour |
|---|---|---|
| Mekanism Digital Miner | Tag `mekanism:miner_blacklist`: `age/AgeOreTags` adds the ore blocks of every locked Age after each datapack load and on each Age swap (0.9.0; before, the KubeJS script `miner_blacklist.js`). `ThreadMinerSearch.run` and `TileEntityDigitalMiner.tryMineBlock` both honour the tag [verified]; the swap resets Mekanism's `TagCache` | Locked ores are neither found nor counted in the GUI |
| IE Excavator (including TFC-IE Crossover veins) | `mixin/ie/MineralMixMixin`: `@ModifyReturnValue(method = "getRandomOre(Ljava/util/Random;)Lnet/minecraft/world/item/ItemStack;")` on `blusunrize.immersiveengineering.api.excavator.MineralMix`, with `@Shadow getRandomSpoil(Random)` [verified callers: `ExcavatorLogic#fillBucket`] | If the rolled ore item is locked, return `getRandomSpoil(rand)`. Mixes are never removed |
| Occultism mineshaft | covered by m2 (`occultism:miner` recipes) plus the §4.6 flush | New ores appear on the next tick after an unlock |
| All fake-player breakers (Create drill and deployer, Mekanism Lasers, the Digital Miner as a safety net) | `miner/OreGuard`: `BlockEvent.BreakEvent` with `getPlayer() instanceof FakePlayer` on a locked `age_blocks` block → `setCanceled(true)`. The Digital Miner then skips the block [verified]. `BlockDropsEvent` with breaker `null` or a FakePlayer on a locked block → replace the drops with the PS `drop_as` item from `LockRegistry.getOreOverridesFor(block)`, or no drops if there is no override | Covers Create's `BlockHelper.destroyBlockAs`, which always posts `BlockDropsEvent` [verified]. mekanism_lasers and mekmm are **[PoC]** |

m1 (config `prospecting.enabled`, default `true`; Felix confirmed on 2026-09-30) removes the ore blocks of every locked Age from `tfc:prospectable` and `precisionprospecting:prospectable_mineral`; the Age swap of the unlock restores them. Since 0.9.0 this is `age/AgeOreTags` in Java (before, the KubeJS script `prospecting.js`), see §5.1.

### 5.1 Implementation notes (M3 and m1, firmages-core 0.2.0)

- **IE excavator:** `mixin/ie/MineralMixMixin` is an `@Inject` at RETURN of `getRandomOre(Ljava/util/Random;)` (cancellable) rather than `@ModifyReturnValue`, so the `Random` parameter reaches `getRandomSpoil`. `miner/ExcavatorFilter` treats a roll as locked when its item is locked (`age_items`, including `disabled`) or when it is a block item whose block is in a locked `age_blocks` Age (IE mixes resolve to ore block items). Config `miner.ieExcavator`. Verified in the dev GameTest with IE 12.4.2 loaded: 0 locked ores in all default mixes while `age_2` is locked, iron ore rolls again after the unlock.
- **OreGuard:** `miner/OreGuard`, `BreakEvent` (priority HIGH) and `BlockDropsEvent` (priority LOW) as in the table above; the disguise drop is the first `LockRegistry.getOreOverridesFor(block)` entry's `dropAs` item. Config `miner.oreGuard`. Real players are never touched.
- **Age ore tags in Java (0.9.0, `age/AgeOreTags`; replaces the KubeJS scripts `firmages/miner_blacklist.js` and `firmages/prospecting.js`):** `mixin/ReloadableServerResourcesLateMixin` calls `AgeOreTags.onTagsBound` inside `ReloadableServerResources#updateRegistryTags`, right after its loop has bound the load's tags and before `TagsUpdatedEvent` (at the `AbstractFurnaceBlockEntity.invalidateCache()` call), on the initial load and on every full reload. It keeps the load content of the three tags (datapacks plus KubeJS, without any Age filter) and binds the Age-filtered version with `Registry#bindTags` (the block registry's whole tag map with three entries replaced, which is what the load itself does). Ore blocks of locked Ages (`firmages:age_blocks/<age>`) leave `tfc:prospectable` [verified, TFC 4.2.11: `["#c:ores"]`, used by the TFC propick and the Precision Prospecting hammer and drill] and `precisionprospecting:prospectable_mineral` [verified, Precision Prospecting 2.1: its mineral prospector; the prospector items hold the `TagKey` and test it at use] unless `prospecting.enabled` is false, and they join `mekanism:miner_blacklist` [tag verified in Mekanism 10.7.19]. Every Age swap binds them again from the kept content. Nested tags need no flattening, because the bound list holds the resolved blocks. The tools read the tags live on the server, so no tag packet is sent. The scripts had to go: a script that filters by Age leaves load content from which a swap cannot restore an unlocked ore. GameTests: `ageOreTagsAtBoot`, `unlockTwoAgesThroughSwap`, `swapMatchesFullReload` (swap and full reload give identical tags).
- **Initial-load answer [verified, dev GameTest log]:** `getObjectIds()` in the KubeJS pre-capture returns nothing and marks the pre-capture invalid, so KubeJS runs every block tag handler inside the tag loader, after this mod captured the load's resource manager. `FirmAges.lockedOreBlocks()` is then right on the initial load too; `AgeService.checkLoadAnswers` ignores the discarded pre-run answers when in-loader answers exist, so a plain boot needs no extra reload (the 0.1.1 note "every boot costs one extra reload" no longer applies with these scripts). Scripts must call `FirmAges.lockedOreBlocks()` only inside `ServerEvents.tags`. Since 0.9.0 only the debug dump and the dev GameTest probe call it; the pack's tag scripts are Age-independent.

## 6. m4: Draconic reactor refuelling

**Why it cannot be automated today** [verified, DE 1.21 branch]:
- `TileReactorCore` has no inventory. Fuel lives in `ManagedDouble reactableFuel` and `convertedFuel`.
- The only write path is `ReactorMenu.clicked`: awakened draconium block/ingot/nugget = 1296/144/16; chaos fragment large/medium/small = 1296/144/16; the cap is 10368 + 15.
- The stabilizer and the injector register only energy capabilities.

### 6.1 Item capability (S)

- `RegisterCapabilitiesEvent`: `event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DEContent.TILE_REACTOR_STABILIZER.get(), ...)`, and the same for `TILE_REACTOR_INJECTOR`.
- `ReactorFuelHandler implements IItemHandler` resolves the core via `TileReactorComponent#tryGetCore()`.
- Slot 0 is fuel, insert only. It accepts only the three awakened draconium items, only when `reactorState == COLD`, and only up to the cap (the remainder is returned). It adds 1296/144/16 per item to `reactableFuel`.
- Slot 1 is chaos, extract only, only when COLD. `getStackInSlot(1)` shows what `extractItem` would give, largest fragment first. Extraction subtracts from `convertedFuel`.
- Both slots call `core.setChanged()`; the Managed fields sync themselves.
- `ReactorProbe` runs at common setup and resolves the fields and methods once. On any `LinkageError` or reflection failure it disables m4 with a clear log line; it never crashes.

### 6.2 Reactor Controller (M, config `reactor.controller.enabled`)

> **Superseded in part (0.4.0, §6.3):** Felix decided on 2026-09-30 that the controller never starts the reactor; the CHARGE and ACTIVATE states and the keys `autoRestart`, `stableSeconds` and `minFieldPercent` are not built and were removed from the config. The fuel port is `ReactorFuelPort`, the probe is `DraconicReactors.init`.


- A block `firmages:reactor_controller` placed touching a stabilizer or injector. Its states are MONITOR, SHUTDOWN, COOLING, SWAP, READY, and optionally CHARGE and ACTIVATE.
- MONITOR → SHUTDOWN when `convertedFuel / (reactableFuel + convertedFuel) >= reactor.controller.shutdownAtConversion` (default 0.80), via `shutdownReactor()`.
- COOLING waits for COLD. SWAP lets pipes use the capability until `reactableFuel >= reactor.controller.minFuel` and chaos is at or below `maxChaos`. READY emits redstone 15.
- With `reactor.controller.autoRestart = true` (default **false**, open question for Felix): `chargeReactor()`, then `activateReactor()` once `canActivate()`. This requires that `failSafeMode` is on and that the field input rate has covered the drain for `reactor.controller.stableSeconds` (default 10). If the field strength falls below `minFieldPercent` (default 30) at any time, the controller calls `shutdownReactor()`.
- Its comparator output is the fuel percentage.

### 6.3 Implementation notes (M5, firmages-core 0.4.0)

- **Classes:** pure `reactor/ReactorView` (phase, fuel, chaos, shutdown, add fuel, remove chaos), `ReactorFuel` (DE's numbers: block/ingot/nugget and large/medium/small fragment = 1296/144/16, cap 10368 + 15 with DE's integer truncation, the fragment split of the GUI slots, conversion, comparator), `ReactorCycle` (the state machine); MC side `ReactorLookup` (providers that map a reactor part to its core's view; DE registers one, GameTests may add a stub), `ReactorFuelPort` (m4 item port), `ReactorItems` (DE items by registry id, so only `compat/draconic/DraconicReactors` links DE classes), `ReactorControllerBlock`(+`BlockEntity`), `ReactorRegistry`.
- **DE 3.1.4 [verified, javap]:** a stabilizer or injector (`TileReactorComponent`) resolves its core with `tryGetCore()` (null while unbound); `TileReactorCore.reactableFuel`/`convertedFuel` are public `ManagedDouble`s (`add`/`subtract` sync themselves), `reactorState` a `ManagedEnum<ReactorState>` (INVALID, COLD, WARMING_UP, RUNNING, STOPPING, COOLING, BEYOND_HOPE), `shutdownReactor()` acts only while WARMING_UP or RUNNING (`canStop`); DE then goes STOPPING -> COOLING (temperature <= 2000) -> COLD (<= 100). `ReactorMenu.clicked` itself checks no state, the port and the controller add the COLD condition of §6.1. `DraconicReactors.init` (common setup) checks the fields and methods once; a `LinkageError` or a missing member disables the coupling with one ERROR line, the capability registration is guarded the same way.
- **m4 port:** `Capabilities.ItemHandler.BLOCK` on `TILE_REACTOR_STABILIZER` and `TILE_REACTOR_INJECTOR`, as in §6.1 (slot 0 fuel in, slot 1 chaos out, both only while COLD, `reactor.itemHandler`). A chaos rest below 16 cannot leave (no fragment holds it), as in DE's GUI.
- **Controller (`firmages:reactor_controller`, age_9):** every 10 ticks the block entity runs `ReactorCycle` against the core of the first touching bound stabilizer or injector: MONITOR -> SHUTDOWN once `RUNNING` and conversion >= `reactor.controller.shutdownAtConversion` (0.80) -> COOLING until DE reports COLD -> SWAP -> READY. A cold reactor found in MONITOR (new, or stopped by hand) goes straight to SWAP. SWAP takes chaos out first (largest fragments, into its own output slots 1..3, then into touching inventories) and then fuel in (blocks, ingots, nuggets, from its own slot 0, then from touching inventories; never from reactor parts or other controllers) until `reactableFuel >= reactor.controller.minFuel` (10368) or not even a nugget fits; READY also needs `convertedFuel - maxChaos < 16`. READY sets the blockstate `ready=true` (redstone 15 on every side, lamp texture); a start by a player (WARMING_UP/RUNNING) returns to MONITOR; a cold reactor that loses fuel while READY is swapped again. The controller never charges or activates (no CHARGE/ACTIVATE state). Comparator: the unconverted share `round(15 * fuel / (fuel + chaos))`, 0 when empty. Its inventory is open to pipes on every side: insert awakened draconium into slot 0 only, extract from slots 1..3 only; the items drop when the block is broken. Use with an empty hand prints the status; `/firmages reactor status [pos]` lists the loaded controllers. Config `reactor.controller.enabled|shutdownAtConversion|minFuel|maxChaos`.
- **Recipe (pack, `kubejs/server_scripts/recipes/age_9/reactor_controller.js`):** grid, a reactor stabilizer frame between two Mekanism elite control circuits, an awakened draconium ingot above and below. Age map: `firmages:reactor_controller` = age_9.
- **Tests:** U `ReactorCycleTest` (6: full cycle with exactly one shutdown and no start, waiting for fuel and for output room, the leftover fuel that cannot reach `minFuel`, manual stops and restarts, configurable thresholds, DE's arithmetic). G `ReactorGameTests.controllerCycleOnRealReactor` with Draconic Evolution, Brandon's Core and CCL in the GameTest run: a real core with four stabilizers formed by `attemptInitialization` (COLD), the controller on a stabilizer with 16 blocks in slot 0 and a chest of 64 nuggets on top: pipe rules, READY with 10368 fuel, redstone 15 and comparator 15; the port refuses fuel while full and while RUNNING; the burnt state (2073.6 fuel, 8294.4 chaos, RUNNING) is shut down at 80 %, DE cools by itself, the controller swaps 6 large, 3 medium and 5 small fragments out and 6 blocks plus 32 nuggets in (fuel 10361.6, nothing more fits), READY again, the reactor stays COLD; pipes extract the fragments; the port adds 2 ingots when 383 fit and gives chaos largest first down to the rest of 2.4; `/firmages reactor status` runs. The reactor is never charged in the test (temperature 20), so it cannot heat or explode.
- **[PoC] in the pack:** a hopper or pipe chain on the controller with a running reactor in a client (DE's own GUI and field drain during a real burn; SPEC §12 m4 M row).

## 7. m6+m9: the Shrine

Design source: `core-shrine.md` (tier ladder, voice lines, blessings, visuals). Implementation contract:

### 7.1 Blocks

- `firmages:shrine_heart` (with BE, blast resistance 1200). Breaking it needs sneak and a pickaxe. A second placement is refused while `ShrineSavedData.heart` is set elsewhere.
- `firmages:offering_plinth` has no BE. The heart's BER draws the relics.
- Recipe: TFC pit-kiln fired ceramic (the recipe is KubeJS/data, a hand-off).

### 7.2 Validation (`compat/modonomicon/ShrineMultiblocks`, the only class that touches Modonomicon)

- `MultiblockDataManager.get().getMultiblock(id).validate(level, heartPos)` [verified signature] returns the rotation or null. **Since M7** `ShrineMultiblocks.check` runs the same test itself from `simulate` (same rotation order, symmetrical rings in NONE only) and also accepts, at a position whose pattern key has a role, a consecrated block of that role (any accent), so a ring validates in Age material, consecrated, and in every mix (§17).
- The valid ring is the highest N with rings 0..N all valid.
- It runs on offering, on prayer start, on `BreakEvent`/`EntityPlaceEvent`/`ExplosionEvent.Detonate` inside the shrine AABB (cheap AABB check first), and every 200 ticks while loaded. Never every tick.
- A change of the valid ring updates the blockstate `ready`, `ShrineSavedData.intact` and the blessings.

### 7.3 State machine (`ShrineStateMachine`, server only)

`IDLE → READY → OFFERED → RITE_DONE → PRAYING → AWAKENING → IDLE`

| Step | Server logic |
|---|---|
| READY | The next ring is valid and the next plinth is empty |
| OFFERED | `PlayerInteractEvent.RightClickBlock` on the plinth or the heart with the tier's `offering`: take exactly 1 item into `relics[plinth]`. A wrong item is refused with the expected item's name. Sneak-use on the plinth before PRAYING returns the item |
| RITE_DONE | All `rites` of the tier are satisfied (checked on interaction and every 20 ticks) |
| PRAYING | Sneak plus use held on the heart with an empty hand. The server sees repeated use packets about every 4 ticks; a player counts as praying while the last use is at most 10 ticks old and the player is within 6 blocks. Progress per tick = `n` praying players; done at `max(prayer_ticks, n * min_prayer_ticks)`, which gives 10 s / n with a 4 s minimum. Progress decays by 1 per tick when nobody prays |
| AWAKENING | In this order: (1) persist the BE and SavedData; (2) set `pendingShrineGrant = age`; (3) `ProgressiveStagesAPI.grantStage(player, StageId.of(grants), StageCause.API)` [verified]; (4) the `StageChangeEvent` listener sees `pendingShrineGrant` and starts the FULL ceremony at the shrine; (5) blessings are reapplied |

A crash after step 3 loses only the show. There are no C2S packets: all interaction uses vanilla use events.

### 7.4 Keystone lending (age_8)

- Sneak-use on plinth 4 hands out `relics[4]` and sets `keystoneLent`.
- Tier 8 has the rite `relic_returned`: the Awakened Keystone (`firmages:awakened_keystone`, a KubeJS item, `kubejs/startup_scripts/items.js`) laid on plinth 4 clears the flag, becomes `relics[4]` and plays the `shrine_fx` "returned" response.
- The Marid ritual itself is unchanged (pack, `kubejs/server_scripts/recipes/age_8/quantum_age.js`, id `firmages:ritual/awakened_keystone`): Occultism `craft_marid` pentacle, bound Marid book, ingredients `firmages:arcane_keystone`, two `mekanism:alloy_atomic`, `draconicevolution:wyvern_core`, `#firmages:boss_token/age_8`; it takes any Arcane Keystone, the lent one or a second one from the repeatable Djinni ritual. The Ultimate Singularity takes `firmages:awakened_keystone` in the Arcane slot (§14 item 5). The quest "Awakened Keystone" (Marid strand, `dev/gen_quests.py`) tells players to borrow the keystone by sneak-using the Spirit Circle plinth with an empty hand and to lay the Awakened Keystone back; until this milestone ships, that sentence is ahead of the mod and the Djinni route is the working one.

**As implemented (M6, 0.5.0):**

- **Data-driven:** a tier lends a relic when it has a `relic_returned` rite naming that relic's tier (`relic`). The shipped `ring_8.json` lends the relic of tier 3 (plinth 4, the Arcane Keystone) and also takes `firmages:awakened_keystone` back (`items`; the item id is the pack's KubeJS item, `firmages:ritual/awakened_keystone` makes it from an Arcane Keystone).
- **Lend:** sneak-use with an empty hand on that relic plinth while the tier is worked (age_8 highest) gives the item to the player (inventory, else dropped). The plinth stays the relic's place: empty, flag `lent`, tier kept, blockstate `awakened` kept (it still glows), unbreakable in survival. The SavedData relic record stays and is marked `lent`, so the relic count, ring validation, the intact flag and the blessings do not change. Before age_8 (and from age_9 on) relics stay locked as before. No lending during the 12 s awakening.
- **While lent:** the tier's offering (the Quantum Core) is refused on its plinth (`RELIC_LENT`, "Caelum lent ... Bring it back ...") and the rite blocks the prayer with its hint (`firmages.shrine.rite.keystone`). Empty-hand use on the waiting plinth says what it waits for.
- **Return:** use on the waiting plinth with the relic itself (the item id in the record) or one of `items`: one item is enshrined again, the record gets the returned item id (an Awakened Keystone stays as the relic, which is what the Ultimate Singularity needs from the Gathering, §7.5) and `lent` is cleared; sound and end-rod particles stand in for the unbuilt `shrine_fx` payload. A wrong item is refused. The relic can be lent again while the tier is worked.
- **Removed by force** (creative, commands): a waiting plinth drops nothing and its record goes, with a WARN.

### 7.5 The Gathering (ring 9, `compat/draconic/Gathering`)

- Condition: `age_9`, ring 9 valid, relics 1..9 present, Chaotic Core catalyst offered, prayer complete.
- The shrine then moves each relic into its injector via `IFusionInjector#setInjectorStack` [verified] and the catalyst into the core's item handler.
- The players fill the 3 tribute injectors and start the craft in the DE GUI.
- The injector placement rules decide the ring-9 geometry [PoC]. Fallback when DE coupling fails: from `age_9` on, sneak-use on a plinth releases its relic.

**Implemented (0.9.0, `shrine/ShrineGathering`): the release prayer.** The DE coupling above stays later; the shipped Gathering gives the relics back for the players' own fusion (the Ultimate Singularity recipe takes the nine item ids unchanged), so no signature item has to be crafted twice.
- **Condition** (`ShrineRules.gatheringBlock`, unit-tested): `age_9` is the highest Age, the Gathering has not happened (`ShrineSavedData.gathered`), no maintenance, the shrine is intact (rings 0..8), no relic is lent, and the relic of every tier 0..8 rests on its plinth (record plus plinth block entity). Refusals on the actionbar like every prayer: a missing relic names its Age ("The relic of the Arcane Age is missing from its plinth."), a lent one "Bring back every relic first.", a broken ring the ruins line with sparks or ghost, afterwards "Caelum has given back every gift. The gate is yours to open." Plain use on the heart says "Pray together: Caelum gives the nine relics back for the last forging."
- **Rite:** the chorus only (`ShrineRules.gatheringChorus`: two players together once two or more are online, else one); no sky rite. Prayer as for an Age: sneak and hold use on the heart, `shrine.prayerSeconds` / n, at least `shrine.minPrayerSeconds`.
- **Late offerings in age_9:** no tier is worked any more, so every plinth counts as an earlier tier's: a relic that is missing (operator extract, an Age from a quest) is accepted at once as a late offering.
- **Completion:** for the tiers in order each relic leaves its plinth the way a lent relic does (`OfferingPlinthBlockEntity#lend`: record kept and marked `lent`, the plinth keeps its glow and its tier, survival cannot break it) and drops as an item entity 1.2 blocks above the heart with no velocity (no inventory guessing between players), never despawning (`setUnlimitedLifetime`) and invulnerable (fire, lava, explosions). `gathered` is saved (`ShrineSavedData`, NBT `gathered`), the heart shows `awakened=10` (`ShrineRules.heartAwakened`; the blessings and the sanctuary still count nine tiers), the 12 s awakening runs, the HUD status is broadcast (§18.4: "Goal: Beyond the Firmament"), and the GATHERING ceremony plays (`CeremonyService.startGathering`: stage `gathering`, tier 9, FULL, title "The Gathering", voice "Nine gifts. One light. Open the gate.", beam `#F4F0FF`, sky `#1A0A2A`, sting `minecraft:block.end_portal.spawn`, end-rod and reverse-portal particles, the blessing slot "Crowned: The nine relics are yours to forge. Their light stays.", four visual bolts; no reload).
- **Afterwards:** a relic laid back on its waiting plinth is enshrined again (the return path of §7.4) and can be taken again by sneak-use with an empty hand ("Caelum gives ... back to you."), so nothing can be lost. A second Gathering is refused. `/firmages shrine status` says "gathered"; `simulate_pray` completes the Gathering in age_9.
- **Tests:** U `ShrineRulesTest.gatheringConditions` (the condition table, first missing relic, chorus, heart awakened 10 only in age_9). G `ShrineGameTests.gatheringReleasesRelics` (nine consecrated rings built from the ring data with nine stand-in relics: age_8 releases nothing; age_9 lends nothing before the Gathering; a lent relic and a missing relic refuse with their own lines, the missing one returns as a late offering; the prayer releases exactly the nine items above the heart, never despawning and invulnerable; every plinth waits and glows; nine records lent; `gathered` survives the SavedData round trip; one GATHERING payload; HUD status gathered; heart `awakened=10`; intact with nine blessings; a second prayer refused; a relic laid back is enshrined and taken again).

### 7.6 Blessings and sanctuary

- Permanent attribute modifiers with ids `firmages:blessing/<name>`, all values from config: `MAX_HEALTH` ADD_MULTIPLIED_BASE (+0.05 per health blessing), `BLOCK_BREAK_SPEED` (+0.05), `BLOCK_INTERACTION_RANGE` (+0.5), `FALL_DAMAGE_MULTIPLIER` (−0.2), `SAFE_FALL_DISTANCE` (+1), `LUCK` (+1).
- They are reapplied on login, respawn, stage change and intact change, and removed while the shrine is dormant.
- XP: `PlayerXpEvent.PickupXp` scales the orb value (+10 %).
- Sanctuary: `MobSpawnEvent.PositionCheck` → `Result.FAIL` for `MobCategory.MONSTER` with spawn type NATURAL or CHUNK_GENERATION within `12 + 4 * tier` blocks of the heart while intact. Spawners and ritual summons are untouched.
- Skyreading: a Jade line or right-click chat panel with the TFC date, season, temperature and rain forecast.
- **[PoC]** TFC scales max health with nutrition; test that the percentage modifiers stack as expected.

### 7.7 FTB Quests (data hand-off, no code dependency)

- Goal quest per Age: `StageTask age_(N+1)` with the signature item as icon and no stage reward.
- Optional "Raise the ring" quest: `ObservationTask` BLOCK_STATE `firmages:shrine_heart[ready=true,awakened=N]` [verified task type].
- Singularity chapter tasks become StageTasks.

### 7.8 Implementation notes (M4, firmages-core 0.3.0; rings 3..8 in 0.3.1)

- **Classes:** `shrine/ShrineRegistry` (blocks, items, block entities, sounds, creative tab entries), `ShrineHeartBlock`(+`BlockEntity`), `OfferingPlinthBlock`(+`BlockEntity`), `ShrineSavedData`, `ShrineData` (pure parser) + `ShrineDataLoader`, `ShrineTier`, `Blessing`, `ShrineRules` (pure: tier from Ages, prayer math, sanctuary radius), `ShrineService` (validation, offering, rites, prayer, awakening, status), `ShrineState` (hot-path cache), `Blessings`, `ShrineEvents`; `compat/modonomicon/ShrineMultiblocks`; `ceremony/CeremonyService`; `command/ShrineCommands`; `client/*` (only loaded on the physical client).
- **Deviation, plinths hold the relics:** each `offering_plinth` has a block entity with one item, drawn on top by `OfferingPlinthRenderer` (task decision 2026-10-01; SPEC had the heart BER draw them). A plinth with an item cannot be broken in survival (destroy progress 0), blast resistance 1200, pistons cannot move it; if it is removed anyway (creative, commands) the item drops and the relic record goes. The heart item therefore carries no `shrine_contents` component: a broken and re-placed heart finds its plinths again. The plinth blockstate `awakened` marks an enshrined relic.
- **Data (all in the mod jar, overridable by the pack, e.g. `kubejs/data/firmages/...`):** rings `data/firmages/modonomicon/multiblocks/shrine_ring_{0..8}.json` (`modonomicon:dense`, top layer first, `0` = heart, `P` = the ring's plinth, `_` = any block); tiers `data/firmages/firmages_shrine/tier/ring_{0..8}.json`; `tier/fallback.json` for a tier without its own file (no ring; any free plinth within `plinth_radius` 12 of the heart takes the offering): since 0.3.1 every tier has its own file, so the fallback only catches a pack that deletes one; `offerings.json` (Age stage whose signature item it is -> item id; a tier may override with `offering`); `blessing/*.json` (one per tier). The task named `data/firmages/shrine/offerings.json`; it lives next to the tiers instead, one reload listener for all shrine data. Every file of any namespace is read; ids sort, so a later namespace overrides (the GameTest uses `firmages_test:offerings`). Invalid files are rejected one by one with an ERROR; unsupported rite types (`energy`, `relic_returned`) and blessing flags are WARNings and never block a prayer.
- **Materials (block tags `firmages:shrine/*`, TFC ids as optional entries, checked against the TFC 4.2.11 jar and the pack registry dump):** ring 0 Hearth Circle 5x5x3: 8 `hearth_stones` (`#c:cobblestones/normal`, `#c:cobblestones/mossy` = all TFC cobble, plus vanilla cobble) around the heart, 4 `hearth_posts` (`#minecraft:logs`, TFC logs come in via `logs_that_burn`) two high on the corners with `thatch` caps (`tfc:thatch`, hay), plinth 1 at distance 2. Ring 1 Bronze Sanctum 7x7x2: `sanctum_bricks` (TFC rock bricks and cracked bricks), 4 `bronze_blocks` corners (`#c:storage_blocks/bronze`, `bismuth_bronze`, `black_bronze`), a `bells` bell (`tfc:bronze_bell`, `tfc:brass_bell`) on the brick opposite plinth 2 (distance 3). Ring 2 Iron Sanctum 9x9x4: `smooth_stones` (TFC smooth rock) border and four 3-high corner pillars, 8 `iron_bars` (wrought iron and better), 4 `iron_lamps` on the pillars, plinth 3 (distance 4). **0.3.1, rings 3..8** (ring N is (2N+5) square, border at distance N+2 with plinth N+1 in its north middle, corner pillars, 4-fold symmetric; all ids in the tags checked against `dev/data/registry.json` and the Age map, so no block of a ring is from a later Age): ring 3 Spirit Circle 11x11x4 (`sourcestone` Ars sourcestone family border, `otherstone` corner pillars 3 high, `source_gem_blocks` caps, 8 `candles` (TFC and vanilla candles, all with `lit`) on the border; 61 blocks with heart and plinth); ring 4 Foundry Nave 13x13x5 (`coke_bricks` border, `heavy_engineering` bases, `steel_scaffolding` 3 high, `electric_lanterns` on top; 65); ring 5 Tesla Crown 15x15x5 (`aluminium_blocks` border (IE sheetmetal and storage, TFC + IE block), `red_steel_blocks` bases, `hv_capacitors`, `alu_scaffolding` 2 high, `floodlights` on top; 73); ring 6 Data Nave 17x17x4 (`quartz_blocks` AE2 certus border, `steel_casings` bases, `quartz_glass` 2 high, `fluix_blocks` caps; 77); ring 7 Star Spire 19x19x5 (`steel_plating` border, `desh_blocks` pillars 4 high, `ostrum_blocks` caps; 89); ring 8 Quantum Ring 21x21x3 (`calorite_blocks` border, `quantum_casings` (SPS casing, fusion reactor frame) 2 high, `awakened_draconium` pylons; 89). No TFC gem has a block form, so ring 3 takes Ars source gem blocks. Vanilla stand-ins exist only where they are harmless in the pack (cobble, hay); they let the GameTests build ring 0.
- **[verified, javap] Modonomicon 1.120.7 bug:** `DenseMultiblock.simulate` pairs the matcher of local layer y (from the bottom, as `validate` uses it) with the pattern character of layer y counted from the top. `ShrineMultiblocks.positions` detects this with the heart character and maps characters back to their real layer; validation itself is unaffected. A ring must not have its heart in the exact middle layer.
- **Tier and progress:** `ShrineRules.currentTier` = N while `age_N` (0..8) is the highest Age in AgeState; Dawn and age_9 have no tier. `awakened` = highest Age index (age_1 = 1). Valid ring = highest N with every defined ring 0..N complete; a tier needs the rings up to `min(tier, highest defined ring)`. Intact = the rings of the awakened tiers stand. Blockstates `awakened`, `ready`, `lit` are updated on every validation (every 200 ticks, the next tick after a break, place or explosion in the shrine box, on offering, at prayer start, on every Age change).
- **Ritual:** offering by use on the plinth (or on the heart, which puts it onto the current plinth); exactly one item; a wrong item is refused with the expected item's name; a plinth of an earlier tier whose Age came without offering (quest or admin) accepts its item at once as a relic ("late offering", no grant); sneak-use with an empty hand takes an offering back until the prayer completes. Rites implemented: `blockstate` (ring 0: the heart kindled, `lit=true`, by any `#firmages:shrine/kindlers` item: TFC firestarter, flint and steel, fire charge, torches; ring 2: the four lamps `lit=true`), `interact` (ring 1: ring a `#firmages:shrine/bells` bell inside the shrine since the last awakening), `sky`, `players_praying`. Prayer: sneak plus held use on the heart with an empty main hand, caught in `PlayerInteractEvent.RightClickBlock` before vanilla's sneak rule (so an off-hand torch is neither placed nor blocks the prayer); praying = last use at most 10 ticks old and within `shrine.prayRadius`; progress bar on the actionbar every 4 ticks; refusals on the actionbar at most once a second; an incomplete ring sends the ghost preview. Plain use on the heart says what Caelum wants next. Phases are derived from the world (`ShrineService.phase`), not stored; AWAKENING lasts 240 ticks.
- **Grant (order of §7.3 kept):** relic and SavedData persisted, `CeremonyService.expectShrineGrant`, then `ProgressiveStagesAPI.grantStage(player, StageId.of(age), StageCause.API)` for every online player (team mode: the first call grants the team, offline members included; signatures checked with javap on 3.0.5; `grantStageBypass` if PS refuses), then the FULL ceremony. `ShrineSavedData.granted` re-grants on login for per-player modes; a REVOKED stage leaves that list. With nobody online the grant uses `AgeService.simulate` when `debug.allowSimulate` is on (GameTests), otherwise it waits for the next login. The shrine never revokes a stage.
- **Blessing (MVP):** data structure `Blessing(id, name, description, flags)`; implemented flag `sanctuary` = Hearthward of ring 0: `MobSpawnEvent.PositionCheck` FAIL for `MONSTER` with spawn type NATURAL or CHUNK_GENERATION within `shrine.sanctuary.baseRadius + perTier * (awakened - 1)` of the heart while the shrine is intact. A broken shrine pauses it (chat: "The shrine is broken...") and announces the repair. 0.3.1: tiers 1..8 name their design blessings (Skyreading, Iron Will, Attunement, Tireless Hands, Long Arm, Clarity, Starwalker, Resolve) with the `sanctuary` flag and modest wording; the radius growth per awakened tier is the whole effect until §7.6 is built (M6). **Superseded by §7.9 (M6):** tiers 1..8 lost the `sanctuary` flag (Hearthward of tier 0 keeps the ward, whose radius still grows per awakened tier) and carry their own effects.
- **Single shrine:** a second heart is refused at placement while the recorded heart stands; a record whose heart block is gone (chunk loaded) is forgotten.
- **Commands:** `/firmages debug ...` (headless test players, §10) and `/firmages shrine status|locate|relics|extract <plinth>|simulate_pray` (the last needs `debug.allowSimulate`; it completes the prayer with ring and offering in place, skipping rites and praying players) and `/firmages ceremony preview <stage> [full|short]`. `shrine info|validate|resync|setrelic|release` of §10 are covered by `status`, `extract` and the late offering; `resync` is not needed because progress comes from AgeState.
- **Tests:** U `ShrineRulesTest` (7: tier selection, prayer formula for 1..8 players, decay, sanctuary radius, the shipped data parses without errors or warnings with all nine tiers, offerings, blessings and only implemented rites, invalid files are rejected), `ShrineRingFilesTest` (0.3.1: every ring file has one heart in the centre of the bottom layer, one plinth at distance N+2, mapped characters, existing tag files, the tier's rite keys, at most 200 blocks; rings 3..8 4-fold symmetric). G `ShrineGameTests.shrineLadderData` (0.3.1: the loaded data defines tiers 0..8 with their own rings, Modonomicon loaded every ring, one plinth at distance N+2, rite keys present, blessings known, grants age_(N+1), offerings age_0..age_8). G `ShrineGameTests.shrineRitualEndToEnd` (tier-0 ring from vanilla stand-ins validates; a missing post breaks it and refuses the prayer; a wrong offering is refused, the right one accepted with exactly one item taken, an occupied plinth refused; a cold heart refuses, a kindled heart listens; `simulatePray` enshrines the relic, grants age_1 through the simulate path and broadcasts exactly one FULL payload with the tier's colours, voice and blessing; payload codec round trip; relic plinth unbreakable in survival; plinth BE and SavedData NBT round trip; awakened 1, Hearthward radius 12, a broken shrine pauses it and keeps age_1, repair restores it; heart removal clears the record; operator extract). GameTest batches run in hash order, so the test starts from exactly `age_0` and restores Dawn at the end.
- **[PoC] in the client (Felix, `dev/archive/dev-client.md`):** ghost preview alignment (anchored at the block below the heart), beam and sky tint with Sodium and Enhanced Celestials, the plinth item renderer, TFC bell and lamp rites in the world, the sounds (vanilla events through `sounds.json`), the prayer speed-up with 2 players.

### 7.9 Implementation notes (M6, firmages-core 0.5.0)

- **Blessing effects (`shrine/Blessings`):** every second (`ServerTickEvent.Post`, 20 ticks) each online player gets exactly the effects of the blessings of the awakened tiers 0..awakened-1 while the shrine is intact (`ShrineState`), and loses every other blessing modifier. Scope: players **within the blessing radius** of the heart, which is the sanctuary radius `shrine.sanctuary.baseRadius + perTier * (awakened - 1)` (12 at Bronze, 44 at age_9) in the heart's dimension (task decision 2026-10-01, deviation from §7.6 "permanent"); Since M16 the blessing sources of §29.5 (the heart, lit Wayshrines, Sky Anchors) replace the radius rule; `shrine.blessings.everywhere` is removed (S4A). `attribute`: transient modifier `firmages:blessing/<name>/<index>` (not saved; reapplied within a second after login, respawn or a data reload; a changed amount is updated in place); unregistered attributes are skipped with one WARN. `mob_effect`: ambient, no particles, 260 ticks, refreshed at 220 or less, never over a stronger or infinite effect; it fades within 13 s after leaving (night vision does not flicker above 200 ticks). `xp_bonus`: `PlayerXpEvent.PickupXp` raises `ExperienceOrb.value` by `value * bonus` (the fraction by chance), so Mending sees it too. Max health falls back at once when a modifier goes (vanilla clamps the health). No shipped blessing uses `mob_effect` (no aura in the docs, decision 2026-10-01); the type is tested with a synthetic blessing. The Skyreading panel (TFC date, season, rain) is not built; Skyreading gives +5 % movement speed instead. `/firmages shrine status` lists the blessings in force and the scope.
- **Energy rite:** `ShrineService.storedEnergy` sums `IEnergyStorage#getEnergyStored` of the ring positions with the rite's key (side null first, then each side), so any FE storage block of the ring counts; nothing is drained. Hint arguments: the amount and the current sum. Ring 5 uses its four IE HV capacitors (`V`, 4,000,000 FE each); the heart itself still has no buffer, so the FE rites of rings 6 and 8 from `core-shrine.md` (10 M and 100 M FE in the heart) are not shipped (no storage block in those rings). `shrine.energyCapacity` stays unused.
- **Jade (`compat/jade/FirmagesJadePlugin`, `@WailaPlugin`, compileOnly Jade 15.10.6 from `mods/jade.pw.toml` via `fetchLibs`):** client-side block components from synced state only: the heart shows "Awakened tiers: N of 9", whether the next ring stands (`ready`) and "The heart is cold" while unlit; a plinth shows its Age ("Plinth of the Arcane Age"), "Relic: X", "Offering: X", "Its relic is lent out" or "Empty". Config toggles `config.jade.plugin_firmages.shrine_heart` / `offering_plinth`. Jade is in the GameTest run so the plugin class loads on a server; the tooltip itself needs a client **[PoC]**.
- **Tests:** U `ShrineRulesTest` (9: plus blessing effect parsing and rejection, the lending-rite lookup, energy and relic_returned validation, the shipped blessings: every one has an effect, +15 % max health in total, Attunement +10 % XP; ring 5 energy 1 M on `V`, ring 8 lends tier 3), `ShrineRingFilesTest` (energy keys exist in their ring). G `ShrineGameTests.keystoneLending` (age_7 lends nothing; age_8 lends, the player holds it, plinth glows and is unbreakable, record kept and lent, ring and intact unchanged, NBT round trips of plinth and SavedData, the tier-8 offering refused while lent, a wrong item refused, return enshrines one item, then only the rings stop the offering, lend and return again), `blessingEffects` (nine tiers near the heart: max health 23, the modifier ids, speed, break speed, reach 5.0, fall multiplier 0.8 and safe fall 4, luck 1, XP +10 % with an orb of 20 worth 22; outside the radius nothing; a lit Wayshrine restores them far away (a Sky Anchor in any dimension: `AnchorGameTests.anchorInAnyDimension`); a broken shrine nothing; three tiers only theirs; a status effect near only), `energyRiteReadsCapacitors` (a real IE HV capacitor charged through its capability is read back exactly).

### 7.10 The living shrine (0.9.0)

The shrine is visible and audible between ceremonies, and its state reads from far away.
- **Idle beam** (server `shrine.permanentBeam`, default true since 0.9.0; client `effects.idleBeam`, default true): while no ceremony beam runs, the heart's renderer draws a thin vanilla beacon beam (radius 0.08, glow 0.12, height 1024, scrolled by the game time) in the colour of the latest awakened tier's ceremony beam (`tier/ring_N.json` `response.beam_color`, alpha 0x90); none before the first awakening, while the heart is cold or while the shrine is broken (the far-away sign that something broke it); after the finale the beam is the finale's gold `#FFE9A8` and half again as wide ("Crowned"). The colour choice is the pure `ShrineRules.idleBeamColor` (unit-tested). The heart's render box is infinite while the beam shows. Existing worlds keep `permanentBeam = false` in their `serverconfig/firmages-server.toml` until it is set to true there.
- **Hum** (client `effects.ambientDrone`): every 80 ticks, within 16 blocks of the lit, intact heart in the player's dimension, `firmages:shrine.ambient` (mapped to `minecraft:block.beacon.ambient`) plays locally at volume 0.6 x (1 - distance / 16) and pitch 0.5 + 0.06 x awakened.
- **Relic dust:** an enshrined relic's plinth (`awakened=true`, holding its relic) gives off two dust particles in its tier's beam colour whenever the client's block animation picks it (vanilla `animateTick`, about every two seconds near the player).
- **Sync:** `firmages:shrine_sync` (§11) carries the heart's position, `intact`, `finale` (Origin won), `idleBeam` and the nine tier colours; `lit` and `awakened` come from the heart's block state. Besides login and maintenance it goes out within a second (`ShrineConsecration.livingChanged`) whenever a validation or the boss's death changes the heart, the intact flag, the finale or the beam switch. The dead key `shrine.energyCapacity` is gone (the heart never had an FE buffer, §7.9).
- **Tests:** U `ShrineRulesTest.idleBeamColour`. G `ShrineGameTests.livingShrineSync` (the default of `permanentBeam`; a headless player receives intact=false with the heart, the beam switch and the tier colours within a second of a broken ring and intact=true after the repair; the codec round-trips). **[M]** in a client: the beam from 200 blocks with and without Complementary, dark while a ring block is missing, gold after the finale, the hum within 16 blocks only, the dust colours per Age, no FPS loss.

## 8. m7: Age transition ceremony

- `CeremonyService.onAgeChanged(stage, cause)` runs FULL when `pendingShrineGrant` matches. Every other Age grant (admin, quest) gets SHORT: title, sting and beam if the shrine chunk is loaded.
- Server side, before the reload hits (t < 60 ticks):
  - send `AgeTransitionPayload` to all players;
  - spawn visual-only `LightningBolt`s (`setVisualOnly(true)`) around the shrine from ring 2 on;
  - optional short clear weather via `ServerLevel#setWeatherParameters` (config).
- Client side: `client/CeremonyPlayer` runs the whole timeline on **client ticks** (they continue while the server is frozen by the reload):

| t (ticks) | Client effect |
|---|---|
| 0 | `MusicManager#stopPlaying`, ambient fade, particles converge on the idol |
| 40 | Beam in the Age colour (`BeaconRenderer.renderBeaconBeam`), choir swell |
| 80 | Sky tint for 8 s (MVP: `ViewportEvent.ComputeFogColor` plus an AFTER_SKY overlay; later the `ClientLevel#getSkyColor` mixin) |
| 100 | Title = Age name, subtitle = voice line (lang keys `firmages.age.<stage>.title` / `firmages.shrine.voice.<stage>`), one chat line |
| 160 | Blessing toast, new halo ring |
| 300 | Afterglow; with `shrine.permanentBeam` a thin beam stays |

- Title texts move from `kubejs/startup_scripts/00_constants.js` to `assets/firmages/lang/en_us.json` (+ `de_de.json`). **Hand-off:** trim title and firework from `kubejs/server_scripts/stages/on_stage_added.js`.
- While `ReloadStatePayload(true)` is active after the timeline, the actionbar shows "The world realigns..." so an EMI rebuild stutter reads as part of the event.

### 8.1 Implementation notes (m7, firmages-core 0.3.0)

- **Trigger:** `AgeChangeProcessor.Sink.granted` (a single stage grant that really added an Age, after persist and the reload request) calls `CeremonyService.onAgeGranted`. A pending shrine grant of that Age (200 ticks) starts FULL, everything else SHORT (deduplicated per Age for 200 ticks). If the shrine's Age was already held, the shrine starts FULL itself (`ensureFullStarted`). Dawn never gets a ceremony; bulk and boot unions play none.
- **Payload** `firmages:age_transition`: stage, tier, full, `Optional<GlobalPos>` heart, beam ARGB, sky RGB, sting sound id, voice lang key, particle ids, blessing name and description keys. One packet per player; the client runs everything on its own ticks (`client/CeremonyPlayer`), so the effects continue through the reload freeze. Server side before the freeze: visual-only lightning at ticks 15..39 when the tier's response asks for it (ring 2 on) and clear weather (`shrine.clearWeather`) if it rains.
- **FULL timeline (240 ticks, 12 s):** t0 music stops (`SelectMusicEvent` is cancelled while it runs) and the sting plays; t0..40 enchant particles converge on the heart; t40 choir, beacon beam (vanilla `BeaconRenderer.renderBeaconBeam` from the heart BER, animated by the ceremony clock because game time freezes) and the tier's particles along it; t60 the ReloadScheduler reload (server, `gate.reloadDelayTicks`); t80..240 sky tint; t100 title `firmages.age.<stage>.title` ("The Bronze Age dawns"), subtitle and one chat line "Caelum: <voice line>" (`firmages.shrine.voice.<stage>`); t160 chat line with the blessing. SHORT (120 ticks): sting, then title and voice at t10, a short beam if the heart is near, half-strength tint.
- **Sky tint MVP:** `ViewportEvent.ComputeFogColor` blends towards the tint, and an `AFTER_SKY` translucent box around the camera (no depth test, no depth write, so terrain covers it) tints the sky; no mixin. Client config `effects.skyTint`, `effects.stopMusic`, `effects.particleScale` apply.
- **Reload hint:** while `ReloadStatePayload(true)` is active the client re-shows "The world realigns..." every 2 s from its own tick, so it stays visible through the freeze.
- **Sounds:** `firmages:shrine.sting.{stone,bronze,iron}`, `shrine.choir`, `shrine.prayer`, `shrine.refused`, `shrine.accepted`, `shrine.kindled`, mapped in `assets/firmages/sounds.json` onto vanilla sound events (no audio files).
- **Lang:** Age names, titles and voice lines for age_0..age_9 (age_0, given by the First Spark quest, gets a new line: "A spark in the dark. I have seen you."), every shrine message, the blessing and the subtitles in `assets/firmages/lang/en_us.json`. **Hand-off done:** `kubejs/server_scripts/stages/on_stage_added.js` skips the Age stages now (it keeps title and firework for `finale_won` only); the PS `unlock_message` chat line stays.

### 8.2 Echo of the ceremony (0.9.0, `ceremony/CeremonyEcho`)

- A friend who was offline when the team opened a new Age sees it once: 100 ticks after joining (after the terrain screen) the SHORT ceremony of the current Age (the beam only within 64 blocks of the heart) and one chat line "While you were away: Bronze Age, Iron Age." naming every Age that dawned meanwhile.
- Player attachment `firmages:seen_age` (string, saved with the player, kept through death): the newest Age whose ceremony the player saw. Every ceremony broadcast of an Age (FULL or SHORT) sets it for everyone online; the first join only records the current Age (the welcome covers it); a revoked Age lowers it silently. The echo is checked again when it is due, so a ceremony that reached the player meanwhile cancels it; a player who leaves before it plays gets it at the next join.
- The finale and the Gathering are never replayed: they are shared moments (late joiners get `finale_won` silently as before).
- **Test:** G `CeremonyGameTests.missedCeremonyOnce` (first join records age_0 and schedules nothing; two Ages granted while offline; the rejoin loads `age_0` from the saved player data, nothing before the delay, then exactly one SHORT payload of age_2 and one chat line naming both Ages; a further join plays nothing; an online SHORT ceremony marks its Age seen).

## 9. Config (NeoForge `ModConfigSpec`)

`firmages-server.toml` (per world):

| Key | Default | Meaning |
|---|---|---|
| `gate.enabled` | true | m2 filter on/off (off = log only) |
| `gate.bootFallbackStages` | ["dawn"] | Fail-strict snapshot when there is no mirror |
| `gate.reloadDelayTicks` / `gate.coalesceTicks` | 60 / 100 | Age swap (or reload) timing |
| `gate.ageSwap` | true | §3.3: an Age change swaps (re-filter, tag rebind, cache flush, recipe packet) instead of reloading every datapack; false = the full reload of 0.8.x on every Age change |
| `gate.warmupReload` / `gate.warmupDelayTicks` | true / 20 | Dedicated server with `gate.ageSwap = false` only: one reload with the same Ages 20 ticks after start while nobody is online (§3.5) |
| `gate.exemptRecipeTypes` | mineral_mix, tfc:collapse, tfc:landslide | Never filtered |
| `gate.allowRecipes` / `gate.denyRecipes` | [] | Per-recipe overrides |
| `gate.reloadOnRevoke` | true | |
| `miner.ieExcavator` / `miner.oreGuard` | true / true | m3 parts |
| `prospecting.enabled` | true | m1; read by `age/AgeOreTags` on every load and swap |
| `reactor.itemHandler` | true | m4 capability |
| `reactor.controller.enabled` | true | §6.3; there is no auto-restart (Felix, 2026-09-30) |
| `reactor.controller.shutdownAtConversion` / `.minFuel` / `.maxChaos` | 0.80 / 10368 / 0 | §6.3 |
| `origin.gatherRadius` / `origin.gatherCooldownSeconds` / `origin.gatherIdleMinutes` | 6 / 30 / 5 | §16.3: every online non-spectator who acted within the idle minutes (0 = everybody) within this radius of the altar starts the fight; a broken-up Gathering re-arms after the cooldown |
| `team.autoJoin` | true | §2.3: a player outside a party joins the party that holds the Ages; a server without a party gets one, founded by the player who holds the most Ages (the team carry-over gives it the Ages) |
| `shrine.prayerSeconds` / `shrine.minPrayerSeconds` / `shrine.prayRadius` | 10 / 4 / 6 | |
| `shrine.sanctuary.baseRadius` / `.perTier` | 12 / 4 | |
| ~~`shrine.blessings.everywhere`~~ | removed | Removed with M16 (Felix S4A, §29.7): the Sky Anchors carry the blessings away from the shrine; an old file that still holds the key logs one WARN (`ServerConfig.REMOVED_KEYS`) |
| `shrine.permanentBeam` / `shrine.clearWeather` | true / true | §7.10: the idle beam (0.9.0: default true; `energyCapacity` removed) / clear rain for a FULL ceremony |
| `shrine.maintenanceSeconds` | 60 | §17: maintenance mode ends by itself after this many seconds (5..3600) |
| `shrine.consecrationTicks` | 120 | §17: a ring's consecration from the heart outward takes this long (0..1200), after the Age reload |
| `sync.coalesce` | true | §3.5: ProgressiveStages lock and stage sync once per player at the end of the tick instead of once per stage change (Age grant stall) |
| `hardship.caveDwellerUntilAge` | `age_2` | §19: first Age without Cave Dweller ReEvolved (an Age id or `never`) |
| `hardship.toolRuleUntilAge` | `age_0` | §19.2: first Age without the hand-breaking rule (an Age id; `never` or `dawn` = off) |
| `perf.limitTfcRegistryApiScan` | true | §20: TFCRegistryAPI scans only mod files that are or depend on TFC or TFCRegistryAPI; false = its original scan of every mod. Read once at game start (dedicated server: `<world>/serverconfig/`, then `config/`, then `defaultconfigs/`; client: `config/`, then `defaultconfigs/`) |
| `starfall.enabled` / `.carveBlocksPerTick` / `.coolTicks` / `.maxPending` | true / 64 / 48000 / 6 | §25.10 (M10): live falls and old craters on/off; block changes per tick across all craters; glow time of a core; no new fall while this many craters wait for their chunks |
| `starfall.shrineClearance` / `.spawnClearance` / `.claimMarginChunks` / `.inhabitedTicks` | 256 / 128 / 2 / 12000 | §25.3 (M10): the site choice and the base check at carve time |
| `starfall.oldCraters` / `.oldCraterRegion` / `.automationAge` | true / 1024 / `age_6` | §25.3, §25.6 (M10): old craters in new chunks, one candidate per region; the first Age with IE excavator veins at craters (`never` = off) |
| `skyHarvest.enabled` | true | §26.8 (M11) |
| `crown.enabled` / `.requireOpenSky` / `.nightTimeFactor` / `.validateTicks` / `.swingTicks` | true / true / 0.5 / 100 / 10 | §27.12 (M12) |
| `wayshrine.radius` / `.prayerSeconds` / `.pointerRadiusChunks` / `.tutorial` | 24 / 5 / 100 / true | §28.9 (M16); the tutorial only on a new world (§28.4) |
| `balance.alternatorFuelOnly` / `.ae2CellTypes` | true / 126 (63..252) | §34.1, §34.2 |
| `anchor.radius` | 24 | §29.7 (M16b), 8..64 |
| `isles.lodeYield` / `.fallback` / `.fallbackRadius` | 2 / true / 600 | §30.9 (M13) |
| `launch.enabled` / `.fxRange` / `.countdownTicks` / `.autoRelaunch`, `ark.growthTicks` | true / 256 / 100 / true, 200 | §31.9 (M14, M17) |
| `debug.allowSimulate` | false | Enables `/firmages ages simulate` (self-tests only) |

`firmages-client.toml`:

| Key | Default |
|---|---|
| `effects.skyTint` | true |
| `effects.stopMusic` | true (§23: silence during the Age ceremony) |
| `music.ageMusic` | true (§23: the background music follows the world's Age) |
| `effects.particleScale` | 1.0 |
| `effects.ambientDrone` | true (§7.10: the hum near the heart) |
| `effects.idleBeam` | true (§7.10: hide the idle beam on this client) |
| `effects.skyStreaks` | true (§25.9, M10: the streak and the falling body of a star) |
| `effects.crownBeams` | true (§27.12, M12) |
| `effects.launchFx` | true (§31.9, M14) |
| `ui.*`, `chat.blocklist`, `tfc.*` | §18.7 (immersion) |

The immersion keys of `firmages-server.toml` (`ui.emiFromAge`, `ui.jadeFromAge`, `ui.welcome`, `tfc.disableThirst`, `tfc.noNutritionMalus`) are listed in §18.7.

## 10. Commands (`/firmages`, permission level 2)

| Command | Purpose |
|---|---|
| `ages` | Unlocked/locked Ages, mirror status, last reload duration |
| `ages sync` | Reconcile with ProgressiveStages and reload |
| `ages simulate grant|revoke <stage>` | Change AgeState without PS (only with `debug.allowSimulate`) |
| `recipes audit` | Write GateReport (§4.4) |
| `recipes why <recipeId>` | Detected outputs, Ages and decision for one recipe |
| `recipes locked <stage>` | Recipe ids removed for that Age |
| `recipes craft <recipeId>` | Lays a shaped or shapeless recipe's ingredients (live or in the gate's master list) into a crafting input and asks the live recipe manager like the crafting table (4-argument `getRecipeFor`); prints the matching recipe or "no recipe matches" |
| `dump registry` | Items and blocks per mod as JSON for the tag generator |
| `reload` | Force a full, filtered datapack reload now (an Age change only swaps, §3.3) |
| `shrine status` | Heart, rings with missing blocks, tier, offering, plinth, rites, phase, prayer, sanctuary (0.3.0; replaces `info`, `validate`, `resync`); in age_9 whether the Gathering is done or what it waits for (0.9.0) |
| `shrine locate` / `shrine relics` | Heart position / enshrined relics |
| `shrine extract <plinth>` | Admin repair: take the relic or offering off a plinth (replaces `setrelic`, `release`) |
| `shrine simulate_pray` | Complete the current prayer (needs `debug.allowSimulate`) |
| `shrine maintenance on\|off\|status` | M7 (§17): maintenance mode for everyone (the same as the sneak-punch on the heart); status shows the seconds left and the stored originals |
| `shrine consecrate <ring>` | M7 debug: consecrate a complete ring now (from the heart outward over `shrine.consecrationTicks`), awakened or not |
| `shrine originals` | M7: stored originals per ring and how many are not consecrated now (holes); the full list (position, original state) goes to the server log |
| `debug player join|leave <name>`, `debug use <name> <pos> [sneak]`, `debug useitem <name>`, `debug pray <name> <pos> <seconds>`, `debug run <name> <command>`, `debug punch <name> <pos> [sneak]`, `debug mine|break <name> <pos>` | Headless test players for the dedicated test server (needs `debug.allowSimulate`): a real `ServerPlayer` on an in-memory connection (vanilla GameTest mock style, every payload channel accepted), offline UUID of the name; it logs the firmages payloads, the ProgressiveStages `lock_sync` and `stage_sync` payloads (id only), titles and chat it receives as `[debug-player <name>]`; GameTests can record its payloads in order (`DebugPlayerCommands.record`/`recorded`). `punch` is one left click (start of mining, then abort; with `sneak` the maintenance toggle at the heart), `mine` mines the survival way (start, then stop once the destroy progress reaches 0.7; progress 0 aborts). `break` runs the server side of a finished mining (`ServerPlayerGameMode.destroyBlock`: the `BreakEvent` and the drops, no progress check). Used for the server-side shrine proof (`dev/poc-results.md`, "Shrine M4 and ceremony") |
| `ceremony preview <stage> [full|short]` | Play the ceremony to yourself, no grant |
| `reactor status [pos]` | Every loaded reactor controller (or the one at `pos`): state, reason, reactor phase, fuel, chaos, conversion, redstone, comparator, settings, buffers (0.4.0; replaces `reactor info`) |
| `origin tp [players]` | Teleport to The Origin's arrival point (normal teleport path, so the age_9 dimension lock applies outside creative) |
| `origin tp <players> force` | 0.9.0, for tests: the same with a forced visit that passes both age_9 locks (firmages-core's and ProgressiveStages') until the player leaves The Origin, respawns elsewhere or the server stops (§16.1) |
| `origin gathering` | 0.9.0, for tests: starts the Gathering at the altar now for everyone online, wherever they stand, regardless of `origin.gatherRadius` and the cooldown (refused once the finale is won and, N5, while the fight of the last Gathering runs) |
| `origin fight` | 0.9.0, for tests: the final boss is summoned at once, without the Gathering (`OriginEvent.SummonBoss`, §16.3); fails when nothing answers, when the answer is a refusal (the boss is alive) or once the finale is won |
| `origin kill` | N5, for tests: kills every hostile (`Enemy`) loaded in The Origin except the final boss, the boss's helpers among them (1.0: the Gateways wave branch is gone with the mod); fails when there is nothing to kill |
| `origin kill boss` | N5, for tests: kills every loaded entity with the tag `firmages.final_boss` in The Origin with the damage of `/kill` (generic_kill bypasses invulnerability, so a boss's damage cap and invulnerable animations do not apply); its death wins the finale the normal way (§16.3); fails without a boss |
| `origin status` / `origin reset` / `origin rebuild` | Arena version, return gate, altar, Gathering count, whether the fight runs (N5), boss state / let the Gathering and the boss happen again (finale_won stays) / rebuild the arena and place the gate again |
| `balance cells` | §34.2: item types of fresh AE2, portable, MEGA item cells and a fluid cell |
| `balance alternator <pos>` | §34.1: the sources of the kinetic network at pos, their capacity, which burn fuel, and the fuelled share |
| `starfall now [pos] [metal] [size]` | §25.10 (M10): a fall in 120 ticks with streak; without pos the normal site search, with pos exactly there (no base check, no move) |
| `starfall list` / `carve <id>` / `cool <id>` / `heat <id>` / `old <pos>` / `tonight` / `veins` | §25.10 (M10): sites with state, metal, distance, glow, cursor and vein; a temporary ticket and the carve now; the hot-core bonus off or on; an old crater there; the next clear night falls for sure; reconcile the IE veins and list them |
| `sky aurora on\|off\|auto`, `sky aurora status [pos]`, `sky aurora band [radius]` | §26.8 (M11) |
| `sky storm rain\|thunder\|clear [seconds]`, `sky storm strike <pos>`, `sky storm status <pos>` | §26.8 (M11) |
| `crown status [pos]` / `chords` / `swing <pos> <lens,...>` | §27.12 (M12) |
| `wayshrine list` / `status [pos]` / `locate <type>` / `tutorial [here]` / `light <pos>` / `reset <pos> [stone]` / `stone <pos>` | §28.9 (M16) |
| `anchor list` / `status [pos]` | §29.7 (M16) |
| `isles locate [radius]` / `shrine` / `list` / `lode <pos>` / `fallback status\|check\|place [pos]` / `place <pos>` | §30.9 (M13) |
| `launch status [pos]` / `finish <pos>`, `ark status` / `check <island>` / `grant <island> <group>` / `reset <island>` | §31.9 (M14, M17) |
| `selftest <suite|all>` | Server-side test suites (§12; `sky` since M10, §24.7), report in `logs/firmages-selftest.json` |

## 11. Network (all S2C, registered in `RegisterPayloadHandlersEvent`, version "5" since the sky features' M10: `firmages:starfall_streak`; "6" since 0.12.0 (the Dawn grid lock's `gridLock` in `firmages:age_status`, M14's `firmages:launch_fx` and M17's `firmages:ark_sync`, one bump for the release, §24.6); "4" since 0.9.0: `firmages:age_status` gained `reloadPending` and `gathered`, `firmages:shrine_sync` gained the living-shrine fields; "3" since 0.8.1 added `toolRule`)

| Payload | Fields | Client action |
|---|---|---|
| `firmages:age_transition` | stage, tier, variant (FULL/SHORT), `Optional<GlobalPos>` shrine, beam colour, sky tint, sting id | `CeremonyPlayer.start` |
| `firmages:reload_state` | boolean running | actionbar hint |
| `firmages:shrine_preview` | multiblock id, anchor pos, rotation, clear flag | `PreviewBridge` → `MultiblockPreviewRenderer.setMultiblock` + `anchorTo` [verified] |
| `firmages:shrine_fx` | pos, fx type (refused, returned, rekindled, dormant) | small local response |
| `firmages:shrine_sync` | heart dimension, maintenance, seconds left, position -> original block id; since 0.9.0 heart position, intact, finale won, idle beam on, the nine tier beam colours | M7 (§17): client copy for the destroy-progress prediction (consecrated blocks break only in maintenance) and the Jade tooltip; §7.10: idle beam, hum, relic dust; on login, on every maintenance change, at most once a second when the originals, the heart, the intact flag, the finale or the beam switch change |
| `firmages:age_status` | current Age id, goal item id, recipe viewer allowed, block overlay allowed, open TFC tabs (byte, one bit per `InventoryTab`), Cave Dweller hunts, hand-breaking rule holds, `reloadPending` (an Age swap or reload is pending or running), the Gathering done (0.9.0: the age_9 HUD goal key `firmages.hud.goal.age_9_gathered`) | §18.3/18.4/18.6/§19/§3.3/§7.5: EMI and Jade gating, HUD Age indicator and goal, TFC inventory tabs, Cave Dweller sound mute, break-speed prediction, one EMI reload per Age change; on login, after every Age state change and after every swap or reload |
| `firmages:welcome` | none | §18.5: first join of a player: title, then the welcome screen |
| `firmages:starfall_streak` | entry angle, target pos, impact game time, metal, near flag | §25.10 (M10, "5"): `SkyStreaks` draws the streak and, near the crater, the falling body; sent 120 ticks before the impact to every overworld player |
| `firmages:launch_fx` | pos, phase, phase start game time, probe item | §31.7 (M14, "6") |
| `firmages:ark_sync` | stage per island, complete mask | §31.8 (M17, "6") |

The BE state uses vanilla block-entity sync. There is no C2S payload of our own (TFC's `SwitchInventoryTabPacket` is guarded, §18.6).

## 12. Test plan

Test levels:
- **U:** JUnit, no Minecraft (`src/test`).
- **G:** NeoForge GameTest in the dev run (`gameTestServer` run config, vanilla + firmages + Modonomicon as `localRuntime`, test datapack in `src/gametest/resources`).
- **P:** pack self-test on `FirmamentAges/test-server`. A script starts the server headless, sends `firmages selftest all` via stdin, waits for `logs/firmages-selftest.json`, stops the server, and fails on any failed case.
- **M:** manual in a client.

| Feature | Test | Level | Pass criterion |
|---|---|---|---|
| core | Mirror round trip, corrupt file → fallback, atomic write | U | snapshot equals the fallback on corruption |
| core | Scheduler coalescing: 2 requests within 100 ticks → 1 reload; a request during a reload → exactly 1 follow-up | U (fake clock) | counts match |
| core | Boot without mirror: strict snapshot, then 1 reload after start | P | log sequence plus `ages` output |
| core | StageChangeEvent fired twice in one tick → one ceremony, one reload | U (event bus) | counts = 1 |
| core | Reload timing and tick freeze (spark) plus EMI rebuild on 1 client | M (M0) | stall ≤ 10 s, client ≤ 30 s, otherwise phase 2 (missed in 0.8.0: phase 2 built in 0.9.0) |
| core | Scheduler: an Age change swaps; a full request (operator, boot reconcile) makes the merged run and a merged follow-up full; the flag does not stick | U (`scheduler_swap_unless_full_requested`) | modes match |
| swap | Classification: `keep` equals `verdict` for all 1,024 Age sets; station by type or serializer; `requireAge` adds up; `untilAge` supersedes; byproduct copies one at a time; Create heat levels; config rules beat stations; bucket is the latest unmet condition; markers drain per load | U (`GateSuite` swap_*, `RecipeMarkersTest`) | all pass |
| swap | Two grants in one tick give one swap without a datapack load; a full reload with the same Ages then gives the identical recipe ids and ore tags; both durations logged (`GameTest Age swap vs full reload`); the revoke returns to the boot recipe set | G (`AgeSwapGameTests.swapMatchesFullReload`) | identical sets, swap ms far below reload ms |
| swap | Machines follow a swap without a reload: the vanilla furnace lookup, the IE crusher cache and the Mekanism enrichment cache are asked before the swap, find the iron recipe right after it and lose it after the revoke | G (`GateGameTests.unlockTwoAgesThroughSwap`) | found, then gone |
| swap | `/reload` still loads everything with the gate and the live Ages (new master, ore tags, KubeJS probe) and the next Age change swaps on the new master; `gate.ageSwap = false` reloads in full with the same result | G (`vanillaReloadKeepsGateAndSwap`, `ageSwapOffReloadsInFull`) | as described |
| swap | One EMI reload per grant on a client: one `[EMI] Starting Reload` per grant; newly unlocked Create (JEMI) and Mekanism recipes are visible right after it | M | as described |
| swap | The crafting table follows the Ages: `crafting_table_follows_ages` probes up to 150 live and 150 Age-locked shaped and shapeless recipes like `/firmages recipes craft`; `recipe_caches_fresh` compares each readable cache with the mod's own rebuild | G (`selftestSuitesPass`), P (`selftest server` after a swap) | no mismatch |
| core | Team carry-over: a player team with age_9 founds "Firmament", the party is empty right after founding and holds age_9 one tick later, the founder keeps it, AgeState never loses it, the old player team is dormant; a newcomer joins the party and holds age_9; a member's login repairs a party that lacks an Age of AgeState; a dormant team's Age is never carried over | G (`TeamGameTests`) | as described |
| ui | Recipe viewer for operators and creative players in every Age, closed for survival players before `ui.emiFromAge` | U (`viewerOpensForOperatorsAndCreativeInEveryAge`), M (operator in Dawn: EMI and its recipe screen open; survival non-operator: closed) | as described |
| m2 | `JsonOutputWalker` on fixtures copied from the jars (Create mixing, IE crusher with tag output, Mekanism enriching, TFC heating with `result_fluid`, Occultism miner, Ars apparatus, DE fusion, MA infusion, Ad Astra) | U | expected output sets |
| m2 | Vanilla: `minecraft:iron_ingot` tagged `age_2`, locked → smelting recipe absent, crafting impossible; simulate grant + reload → present | G | `byKey` absent/present |
| m2 | Canonical cases `selftest/gate_cases.json` (one recipe per machine family, with its Age): absent while locked, present after `simulate grant` + Age swap (not built yet; the dev GameTests cover furnace, IE and Mekanism) | P | all cases pass |
| m2 | Audit: no undetected recipe types beyond `selftest/accepted_undetected.json` | P | the list is unchanged |
| m2 | `mineral_mix` count identical before and after the filter | P | equal |
| m2 | MA infusion recipe of a locked Age is absent (TAIL ordering against Cucumber) | P | absent |
| m2 | EMI shows new recipes without relog; an AE2 pattern encoded before an unlock still works | M | visible, pattern valid |
| m2 | Machine caches follow the reload: for each `gate_cases` family, a machine that was loaded and idle before the unlock processes the newly unlocked recipe right after it (Create basin, IE crusher, Mekanism enrichment chamber, TFC barrel, Occultism ritual, Ars apparatus, DE fusion, MA infuser, Ad Astra) | M (P where a machine can be driven by command) | processes without chunk reload or relog |
| m3 | `MineralMix#getRandomOre` on the Crossover galena mix, 10,000 rolls with silver locked → 0 silver; unlocked → silver present | P | counts |
| m3 | OreGuard: FakePlayer `BreakEvent` on a locked ore → canceled; `BlockDropsEvent` with null breaker → drops replaced | P (places blocks in a reserved test area) | asserted |
| m3 | `mekanism:miner_blacklist` contains all `lockedOreBlocks()` after every load and swap | G (`ageOreTagsAtBoot`, `unlockTwoAgesThroughSwap`), P | subset check |
| m3 | Digital Miner on a disguised tin vein; Create drill on a locked ore | M | no ore yield |
| m1 | `tfc:prospectable` contains no locked ore after every load and swap | G (same tests), P | disjoint |
| m1 | Prospector's pick over a disguised vein | M | no hit |
| m4 | Capability on a reactor structure loaded from `selftest/reactor_cold.nbt` (captured once by hand): insert fuel while COLD → `reactableFuel` rises; state forced non-COLD → insert returns the full stack; cap respected; chaos extraction units correct | P | field values |
| m4 | `ReactorProbe` with a missing field → feature disabled, no crash | U (fake class) | disabled flag |
| m4 | Hopper chain refuel; controller full cycle; power cut during CHARGE → shutdown | M | reactor never goes critical |
| shrine | State machine transitions, prayer formula (1..4 players), decay, refusal paths | U (world mocked behind interfaces) | table of expected phases |
| shrine | Tier JSON parsing, invalid tier files rejected with log | U | |
| shrine | Validation of a vanilla-block test ring (gametest datapack) incl. break → dormant → repair | G | `validRing` values |
| shrine | Rings 0 and 1 from `selftest/shrine_ring0_1.nbt` validate with TFC blocks | P | `validRing == 1` |
| shrine | Full ritual with 1 and with 2 players; relog restores blessings; second heart refused; heart broken and re-placed keeps relics | M | as described |
| m7 | `ceremony preview` FULL and SHORT; ceremony keeps running while `reload` freezes the server; no double title after the KubeJS trim | M | visuals continuous, one title |

## 13. Milestones

| M | Content | Effort | Done when |
|---|---|---|---|
| M0 | Measure reload stall and EMI rebuild (spark, `/reload`); decide reload vs hot swap | S | numbers in `core-spec.md`, decision logged |
| M1 | core: AgeState, mirror, AgeIndex, ReloadScheduler, KubeJS binding, `ages`/`dump`/`reload` commands, self-test harness | S–M | core U + P tests green |
| M2 | m2 filter, extractors, audit, cache generation flush | M | m2 tests green, audit list reviewed |
| M3 | m3 (IE mixin, OreGuard) + KubeJS tag hand-offs + m1 | S | m3/m1 tests green |
| M4 | Shrine MVP: heart, plinths, rings 0–1, state machine, grant, basic m7 (vanilla sounds/particles, beam, lightning, titles), quest hand-off | M | full ritual in M test grants `age_1` with one title |
| M5 | m4 capability (+ controller) | S (M) | m4 tests green |
| M6 | Rings 2–8 data, blessings, sanctuary, Jade, Keystone lending | M | tier tests green, rings validated in game |
| M7 | Consecration and maintenance mode (§17, Felix 2026-10-01) | M | U and G green, the look checked in a client |
| M8 | The Gathering (after the DE injector PoC) | M | Ultimate Singularity forged at the shrine |
| M9 | GeckoLib idol | L | 10 tier looks animated |
| M9b | Own sounds (CC0 or self-made), particles, sky tint, Sodium check (was M7 until 2026-10-01, M10 until 2026-10-15; the sky features took M10 to M17, §32) | M | M test with Sodium and Enhanced Celestials |

The sky features are M10 to M17 (§32). M0–M4 must ship before the first playtest beyond the Stone Age. Shipping: copy the jar to `FirmamentAges/mods/` and run `packwiz refresh` (hand-off).

**State (2026-10-13, round B, for 0.8.0):** Jade from the Bronze Age (§18.3, H9), the Cave Dweller gate (§19, H12), Enhanced Celestials 2 instead of 1 (§18.1: the join silence is a pack file now, no code). U and G green (`gradlew build`: 93 unit tests; `runGameTestServer`: "All 34 required tests passed"). The sound mute and the despawn of a real dweller need the client and server **[PoC]** of §19.

**State (2026-10-13, immersion, §18):** implemented; U and G green (`gradlew build`: 85 unit tests; `runGameTestServer`: "All 32 required tests passed"; the TFC thirst and nutrition test passed in the opt-in run `-PgametestTfc`). Every client-side part (screens, HUD, EMI, Jade, chat, toasts, TFC displays) is checked by code reading, javap and unit tests only and needs a client **[PoC]** (§18.7).

**State (2026-10-01, M7 branch `core-m7`, after 0.5.0):** M7 (consecration and maintenance mode, §17) is implemented: the consecrated block family (8 roles, accent 0..8, textures v2), `consecration.json`, validation with consecrated equivalents, the ceremony's consecration from the heart outward with the originals stored, the migration of standing awakened rings, maintenance mode with original drops and repair, the heart's revert, commands, sync payload, Jade and KubeJS. U and G green (`gradlew build`: 70 unit tests; `runGameTestServer`: "All 27 required tests passed"). The look (models, overlay, full-bright accent, the heart's scaffold cage) and the Jade lines need a client **[PoC]**.

**State (2026-10-01, M6 branch `core-m6`, for 0.5.0):** M6 is implemented except the Skyreading panel: Keystone lending (§7.4), blessing effects, the energy rite and the Jade tooltip (§7.9); The Origin got its own lock, its spawn list and the boss trigger hardening (§16). U and G green (`gradlew build`: 67 unit tests; `runGameTestServer`: "All 24 required tests passed", with Jade added to the run).

**State (2026-10-01, 0.4.0 branch `core-m5-origin`):** M5 (fuel port and reactor controller, §6.3) and m10 (The Origin and the end-boss scaffolding, §16) are implemented; U and G green (`gradlew build`: 63 unit tests; `runGameTestServer`: 19 GameTests with DE, Brandon's Core, CCL and Stargate Journey in the run).

**State (2026-10-01, before 0.4.0):** M0 measured (server half). M1 shipped as 0.1.1. M2 and M3 implemented in 0.2.0; 0.2.1 adds the late pass (§4.7). M4 (shrine MVP: rings 0..2 plus the fallback tier) and m7 (ceremony) are implemented in 0.3.0 (§7.8, §8.1); 0.3.1 adds the ring and blessing data of M6 for tiers 3..8 (data only, no new effect types): U and G green (`ShrineRulesTest` 7 cases, `ShrineRingFilesTest` 1, `ShrineGameTests` 2 tests, all 14 GameTests pass); 0.3.3 is the first shipped build with both the ring data and the ProgressiveStages sync mixins of 0.3.2 (14 GameTests pass); on the pack server the rings 0..8 validate from pack blocks and tiers 3..8 each grant age_(N+1) through offering, rite and prayer with one reload of 6.2 to 6.6 s (`dev/poc-results.md`, "Shrine ladder and reload v2"); the client side is unverified until Felix's dev-client test. Green at levels U and G, and the pack P runs are recorded in `dev/poc-results.md` ("M2/M3, quests and content"):
- U (`gradlew build`): `CoreSuiteTest` (18), `GateSuiteTest` (16: walker keys and exclusions, deny/allow/exempt, locked and unlocked items, latest-Age bucket, tag all-locked / mixed / empty, disabled, fluid ids, undetected, report and audit text, excavator pick), `JsonOutputWalkerFixtureTest` (15: recipe JSON copied from the Create, IE, Mekanism, Occultism, Ars, TFC and DE jars, plus a rules run on them).
- G (`gradlew runGameTestServer`, vanilla + ProgressiveStages + Modonomicon + KubeJS + IE + Mekanism, 12 tests): boot filter with `[dawn]` on the synthetic `firmages:test/*` recipes (item, tag all-locked, tag mixed, disabled, fluid byproduct, and the two late recipes of `LateRecipeInjector`) and on vanilla iron recipes; the recipes commands and audit file; IE crusher tag outputs through the TagOutput accessor and Mekanism output definitions; mineral mixes exempt and the MineralMix mixin (0 locked ores rolled); the pack's m1/m3 KubeJS scripts against test tags; OreGuard break and drop paths; the excavator filter on a stub mix (10,000 rolls); `age_0` then `age_2` unlocked through real reloads and revoked again (recipes, tags, excavator, OreGuard follow).
- P (pack server, 0.2.1): m2 at Dawn and after each grant age_0 to age_9 (`poc_analyze.py` G-1 to G-4), m3 excavator rolls, Digital Miner blacklist and prospecting tags per Age (`/fa_m3`), Cucumber ordering, Create/Occultism extractors at runtime (0 extraction errors on 35,114 recipes). Still open: the flush mixins (`MineshaftMixin`, `ApparatusMixin`) with a running Mineshaft or apparatus, which needs a client.

## 14. Hand-offs to other workflows (not in this mod)

1. `dev/gen_stage_locks.py`: emit the `age_items`/`age_blocks`/`age_fluids` tags into `kubejs/data/firmages/tags/`, expanding `mod:` entries from `/firmages dump registry`.
2. `kubejs/server_scripts`: the miner-blacklist tag script (m3) and the prospecting tag script (m1, gated by `FirmAges`) are written (`kubejs/server_scripts/firmages/`, §5.1). `stages/on_stage_added.js` is silent for Age stages when firmages-core 0.3.0 or newer is loaded (`Platform.getInfo('firmages').getVersion()`, the binding has no version; it still announces `finale_won`). The Shrine Heart (the pit-kiln fired Hearth Idol set in cobble under charcoal) and Offering Plinth recipes are in `recipes/age_0/stone_age.js`; the shrine block tags and `offerings.json` ship in the mod jar (§2.6, §7.8).
3. FTB Quests (done 2026-10-01, `dev/archive/quests-notes.md`): the Stone, Bronze and Iron goals are StageTasks on the next Age without stage reward and without an item task ("Offer the X at the Shrine"); Dawn's First Spark keeps its `age_0` reward. The Stone Age strand "Hearth & Shrine" ends in "Raise the Shrine", an ObservationTask `block_state` `firmages:shrine_heart[ready=true]`; its step "The Shrine Heart" observes the placed heart (`firmages:shrine_heart`). Both use the block id and the `ready` property of §2.5 as 0.3.0 registers them. Optional "Raise the ring" quests for rings 1 and 2 are still open.
4. Pack: add the jar to `mods/` and run packwiz refresh; the dependency entries in `neoforge.mods.toml` (§1.4).
5. Finale content (pack, 2026-10-01, `kubejs/server_scripts/recipes/age_9/singularity_age.js`), what the mod side relies on:
   - **The Origin's dial address.** The pack item `firmages:origin_coordinates` (paper + ink + a chaos shard, age_9) shows the
     Milky Way galactic address **9, 16, 21, 33, 2, 37** (then the point of origin, 7 chevrons), the address the mod ships in
     `data/firmages/sgjourney/address_region/origin.json` with `randomizable: false` (§16; integration 0.4.0 took the mod's
     GameTest-dialled address over the earlier pack proposal 9, 16, 31, 5, 21, 37). The server runs
     `random_addresses_from_seed = true`, so only a non-randomizable address is the same in every world. If the address
     changes, change the tooltip line in `kubejs/startup_scripts/items.js` (`global.FA_SIGNATURE_TOOLTIPS`) and the GameTest with it.
   - **Ultimate Singularity** (`firmages:fusion/ultimate_singularity`): DE fusion, `techLevel` chaotic, catalyst
     `draconicevolution:chaotic_core`, ingredients the item ids of the nine relics (`firmages:hearthstone` ... `firmages:quantum_core`,
     with `firmages:awakened_keystone` in the Arcane slot), two `#firmages:boss_token/age_9` (chaos shard) and
     `evolvedmekanism:alloy_singular`; 12 injectors, 2,000,000,000 FE. The Gathering (§7.5, M8) only has to move the relics into
     injectors; the recipe needs no change. The Awakened Keystone comes from the pack's Marid ritual
     (`firmages:ritual/awakened_keystone`), which consumes an Arcane Keystone (lent, §7.4, or crafted again).
   - **Reactor Controller** (`firmages:crafting/reactor_controller`): one grid recipe in `recipes/age_9/reactor_controller.js`
     (stabilizer frame, two elite control circuits, two awakened draconium ingots); the duplicate in `singularity_age.js` was dropped at integration.

## 15. Open points

- **0.4.0 (core-m5-origin), needs a client or the pack server:** the controller with pipes and a really burning reactor; The Origin's look (End sky with `ambient_light` 0.1, the lights of the arena) and a real dial from a player-built Classic Stargate with power (the GameTest dials the address with SGJ's `SIMULATE_ENOUGH_ENERGY` from a Milky Way pedestal gate); whether SGJ's address randomizer (`randomizable: false` here) leaves the fixed address alone in an existing world; the return trip (dial home from the Origin DHD, whose energy core SGJ's `generate()` adds); the Mekanism teleporter and Ars warp into The Origin (Doc 08 §10.3; blocked since M6 by `OriginAccess`, still to be seen with the real mods in a client); `mob_9` spawns (M6: own list, §16.4; the Cataclysm entries and their spawn rules need the pack).
- **M6 (core-m6), needs a client or the pack server:** the Jade tooltips; lending with the real Arcane Keystone and the Marid ritual (the GameTest uses stand-ins); the IE HV capacitors of ring 5 charged by real wires (the GameTest charges one through its capability); max-health blessings together with TFC's nutrition health (old PoC); how many `mob_9` mobs really spawn in the lit arena; the boss script's gateway id check (`GatewayRegistry.INSTANCE.getKey(gate.getGateway())`) in the real fight. The Skyreading panel is not built. Since 0.9.0 the Gathering (§7.5) gives the relics back in age_9 (the Awakened Keystone returned to plinth 4 among them); the fusion with the released items in the DE GUI needs the pack server and a client.

- **M7 (core-m7), needs a client or the pack server:** the consecrated models (multipart base plus accent overlay with NeoForge `neoforge_data` full-bright faces, cutout and translucent per part) and the heart's maintenance cage; the client's destroy prediction during maintenance (from `firmages:shrine_sync`); breaking with the real TFC, IE and Mekanism ring blocks and their original drops (loot of the original with the player's tool, else its block item; IE tile drops have no block entity and fall back to the item); consecration of block-entity blocks of the late rings (IE capacitors, lanterns and floodlights, Mekanism casings: their block entity is removed first, stored energy is lost); Cataclysm bosses near the shrine (tags). Doc 11 (German player summary) does not describe M7 yet.
- **Felix (decided 2026-09-30):** m1 build; reactor controller refuels and signals READY by redstone, NO auto-restart (ACTIVATE state stays disabled); offerings are ENSHRINED (kept as relics, Keystone lent in Age 8, Ultimate Singularity forged at the shrine); EMI shows no next-Age preview, by design.
- **PoC:** reload stall and client rebuild (M0); TAIL ordering against Cucumber; StageChangeEvent once per member; mekanism_lasers/mekmm breakers; DE injector placement; TFC nutrition health vs percentage modifiers; sky overlay with Sodium/Enhanced Celestials; rings 3..8 built in the world and their rites (the ids are checked against the registry dump, `lit` and `active` against the blockstate files of the TFC and IE jars; the IE lantern and floodlight `active` flip and TFC candles are unverified in game); machine caches actually refresh after the unlock reload (§12, only `RecipeManager` presence is covered by P tests). The Awakened Keystone item id is `firmages:awakened_keystone` (resolved 2026-10-01, §7.4). `SelectMusicEvent` exists in NeoForge 21.1.252 [verified, review], so the ceremony can suppress music with it instead of a mixin.
- **After M2/M3 (done 2026-10-01, `dev/poc-results.md`):** the audit is reviewed: the 37 types with no detected output are accepted with a reason in `dev/data/accepted_undetected.json` (checked by `poc_analyze.py` G-4), no extractor follow-up. Recipes that only a chance byproduct of a later Age keeps out are not allowlisted; `kubejs/server_scripts/recipes/byproducts.js` strips the byproduct until its Age (checked by G-3). The gate time is measured. `gate_cases.json` is still not written (the G-checks of `poc_analyze.py` cover the pack instead).

## 16. m10: The Origin and the end-boss scaffolding (firmages-core 0.4.0)

Design source: Doc 08 §10 (finale: Ultimate Singularity, Stargate, The Origin, script-first end boss r10 A). The mod provides the place, the way in, the trigger and the result; the fight itself is the final boss (1.0: the mod's own entity in package `boss`, owned by the boss work; until 0.9.x Gateways to Eternity waves and a Cataclysm boss run by a KubeJS script, both removed from the pack for 1.0). Not to be confused with the shrine's Gathering of §7.5 (ring 9, M8).

### 16.1 The dimension

- **Data in the mod jar** (`data/firmages/`): `dimension/origin.json` (generator `minecraft:flat` with no layers, biome `firmages:origin`, no features or structures: a void), `dimension_type/origin.json` (no skylight, End sky effects, `ambient_light` 0.1, fixed time, beds and anchors do not work, height 0..256), `worldgen/biome/origin.json` (no precipitation, no spawns, no features, dark violet fog). Doc 08 §10.3 put the dimension under `kubejs/data/`; it ships in the jar so the GameTests and the arena code see the same files, and the pack can still override it with the same path under `kubejs/data/firmages/`.
- **Lock:** `config/progressivestages/stages/age_9.toml` `[dimensions] locked = ["id:firmages:origin"]` (verified: the id matches the dimension key). ProgressiveStages 3.0.5 `DimensionEnforcer` checks a player's dimension change before travel and bounces a player back who arrived in a locked dimension anyway (`handlePostTravelSafetyNet`); creative players bypass it (`allow_creative_bypass`). **[PoC]** the bounce for Stargate Journey's wormhole transport is unverified (needs a client). G `OriginGameTests.originNeedsAge9` uses a GameTest copy of the stage file (`src/gametest/config/progressivestages/stages/age_9.toml`, copied into the run by `prepareGametestConfig`): a survival player without age_9 is locked out and a teleport leaves him where he was.
- **Own lock (M6, `origin/OriginAccess`):** `EntityTravelToDimensionEvent` (priority HIGH) cancels every travel of a `ServerPlayer` into `firmages:origin` while the server-wide AgeState lacks `age_9`, with an actionbar line (`firmages.origin.locked`). Every dimension change goes through `Entity#changeDimension`, which posts the event [verified, 21.1.252 source]: portals, every command whatever its permission level (`/tp`, `/execute in`, FTB Essentials `/back` and `/tpa`), the Mekanism teleporter, Ars Nouveau warp, and Stargate Journey's wormhole (`Wormhole#transportPlayer` calls `ServerPlayer#teleportTo(ServerLevel, ...)`, entities `changeDimension` [verified, javap 0.6.49]). Creative and spectator pass (like ProgressiveStages' creative bypass); operators in survival do not, unless an operator gives a **forced visit** (0.9.0, `/firmages origin tp <players> force`): the player passes this lock and ProgressiveStages' (`mixin/ps/DimensionLockBypassMixin` at the head of the private `DimensionEnforcer.isDimensionLockedForPlayer(ServerPlayer, ResourceLocation, String)`, which every ProgressiveStages check goes through: the travel event, the bounce after arrival and the once-a-second check of the current dimension [verified, javap 3.0.6]; fails soft) until he leaves The Origin, respawns elsewhere or the server stops (in memory). G `OriginGameTests.operatorCommands`. Vehicles go alone, a rider is checked on his own (vanilla moves passengers one by one). G `OriginGameTests.originTravelLock`: the event is cancelled without age_9 and not for other dimensions; the wormhole call leaves the player outside; creative passes; after a real ProgressiveStages grant of age_9 the same call brings him in and back; the grant is revoked again.
- **Spawns (M6, `mob_9`, `origin/OriginSpawns` + `OriginSpawnList`):** `data/firmages/origin/spawns.json` (`{"spawners": {"monster": [{"type", "weight", "minCount", "maxCount"}]}}`, overridable under `kubejs/data/firmages/origin/`, reloaded with the datapacks) is the whole spawn list of The Origin: `LevelEvent.PotentialSpawns` (LOWEST) replaces every category's candidates there, so biome modifiers add nothing and unlisted categories (passive, ambient, water) stay empty. Shipped (1.0, Cataclysm left the pack): `minecraft:enderman` 10 (1-2); while The Primordial lives, nothing spawns within 48 blocks of it (`ArenaLights.bossNear`); entity ids of absent mods are skipped with a WARN. The dimension type's light rules stay (monsters only on block light 0), so the lit arena stays mostly free. U `OriginSpawnListTest`, G `OriginGameTests.originSpawnList` (monsters = enderman, one entry parsed, no creatures, the overworld untouched).
- **GameTests and datapack dimensions:** vanilla's `GameTestServer` bakes its world from the flat preset with an empty dimension registry, so no datapack dimension loads there [verified, 21.1.252 source]. `mixin/GameTestServerMixin` (test infrastructure; a normal server never runs `GameTestServer`) passes this mod's own datapack dimensions into that bake.

### 16.2 Arena, Stargate address and return gate

- **Arena (`origin/OriginArena`, built once at server start, `OriginSavedData` `firmages_origin` keeps `arena_version` and `gate_built`):** a floating disc of radius 20 at y 64 around (0, 0) (polished deepslate with gilded-blackstone rings and sea lanterns, a blackstone-brick rim with eight lantern posts and a gap to the south, an inverted cone of mixed rock below), the `firmages:origin_altar` at (0, 65, 0) on a crying-obsidian dais, nine small floating islands, one per shrine Age, 45 to 75 blocks out, and a bridge south to the gate pedestal. `/firmages origin rebuild` builds it again; bump `OriginArena.VERSION` to rebuild existing worlds. **Version 2 (0.9.0, Origin echoes):** the island tops are the shrine's consecrated family (consecrated stone with a ring of consecrated trim, accent = the island's Age 0..8; unbreakable and without loot like every consecrated block), so the arena reads as the shrine's last ring; the end rods are gone. Above each island the Age's signature item (`firmages_shrine/offerings.json`) floats 2 blocks up (`origin/OriginEchoes`): a vanilla item display (billboard fixed, full bright, scale 1.5, view range 4, tag `firmages.origin_echo`) with a fixed UUID per island, spawned when its chunk's entities are loaded and it is missing (so never twice, also on old worlds), its item kept in step with the offerings file; while players are in The Origin the server turns each by a twelfth every second and the client interpolates. The rest stays vanilla blocks. `origin status` counts the loaded echoes.
- **Arrival point:** (0, 65, 14) on the arena, between gate and altar, outside the Gathering radius. Players dialling in step out of the gate at z about 30 and walk north past the DHD.
- **Altar `firmages:origin_altar`:** unbreakable in survival (hardness -1), no loot table, light 12; Age map `age_9`; never crafted; listed in `minecraft:wither_immune`/`dragon_immune` (until 0.9.x also in every Cataclysm `*_immune` tag; those files left with Cataclysm in 1.0).
- **Stargate Journey 0.6.49, again 0.6.51 [verified, javap and jar data]:** `data/firmages/sgjourney/space_location/origin.json` (point of origin `sgjourney:floating_islands`, symbols `sgjourney:galaxy_milky_way`, address region `firmages:origin`, `preload_stargate`; `in_stargate_network` defaults to true, `generate_in_address_tables` to false, so the address never appears on cartouches) and `data/firmages/sgjourney/address_region/origin.json` (name `solar_system.firmages.origin` = "The Origin"; Milky Way address **9-16-21-33-2-37** plus the point of origin, extragalactic 1-30-9-16-21-33-2; both `randomizable: false`). The address does not collide with the regions SGJ ships. The quests give the address (hand-off).
- **Return gate:** with Stargate Journey loaded, `OriginArena.buildGate` places SGJ's own template `sgjourney:stargate/milky_way/pedestal/stargate_pedestal_1` (Milky Way gate with its DHD) south of the arena (gate base (0, 66, 31) facing north, DHD (0, 65, 25)) and `compat/sgjourney/SgjGates.finishPlacement` does what SGJ's worldgen does for its structures: `generateInStructure` (SETUP to READY) and `generate()` (the gate joins the network, the DHD gets its energy core). Without that step a code-placed gate never joins the network, because its chunk does not tick. G `originDimensionArenaAndGate`: dimension type and biome, arena, altar, gate and DHD block entities, the space location with the region and the exact address, the gate in `StargateNetwork`. G `overworldGateCanDialTheOrigin`: a pedestal gate placed in the overworld test area dials 9-16-21-33-2-37 with SGJ's `Dialing.Action.SIMULATE_ENOUGH_ENERGY` and gets `CONNECTION_ESTABLISHED_INTERSTELLAR`; an unknown address gets `INVALID_ADDRESS`.
- **Commands:** `/firmages origin tp [players]` (normal teleport, so the age_9 lock applies), `origin tp <players> force`, `origin gathering`, `origin fight` (0.9.0, operators' test shortcuts), `origin kill [boss]` (N5), `origin status`, `origin reset`, `origin rebuild` (§10).
- **Test (0.9.0):** `originDimensionArenaAndGate` also checks every island top (consecrated stone with its accent, trim rim, no end rod) and, with the island chunks forced, one echo per island whose signature item exists in the run (the test datapack's three), with the right item, tag and place, and no duplicate after a second tick.

### 16.3 Gathering trigger, final boss, FINALE

- **Gathering (`OriginService.checkGathering`, every 20 ticks):** true when every online player who is not a spectator and has acted within `origin.gatherIdleMinutes` (5; `ServerPlayer.getLastActionTime`) is alive in The Origin within `origin.gatherRadius` (6) of the altar, and the altar stands. While some of them stand at the altar and others do not, the ones at the altar read `firmages.origin.waiting` on the action bar ("The altar waits for 2 more: Alex, Sam", at most five names). On the transition to true, outside `origin.gatherCooldownSeconds` (30, in memory, reset at boot) since the last start, while the boss has not fallen and (N5) while the fight of the last Gathering does not run, the mod tells the players (`firmages.origin.gathering`), increments the count in `OriginSavedData`, posts `OriginEvent.Gathering` (level, altar, players, count) on `NeoForge.EVENT_BUS` and runs the function tag `#firmages:origin/start` as the server at the altar (permission 2). The mod ships the tag empty; the pack adds its functions under `kubejs/data/firmages/tags/function/origin/start.json`.
- **The fight runs (N5, Felix 2026-10-14; `OriginService.fightActive`):** a living entity with the final-boss tag is loaded in The Origin (1.0: the Gateways gate branch and `origin.finaleGateway` are gone). Players walk away from the altar and back during the fight; before N5 every return after the cooldown started the Gathering again ("The altar answers: ..." each time, a new count, an event the script ignored). Now an arrival while the fight runs starts nothing and the waiting line on the action bar stays off. After a wipe no boss lives, so the next arrival after the cooldown starts the Gathering again; after the win nothing starts.
- **Final boss:** any `LivingEntity` with the scoreboard tag `firmages.final_boss` (`entity.addTag(...)`; a dot, because selectors and `/tag add` cannot read a colon) that dies (`LivingDeathEvent`) **in The Origin** (M6: a tagged death in another dimension is logged with WARN and counts nothing). The first such death sets `won`, grants `finale_won` through ProgressiveStages to every online player (team mode: the first grant covers the team; refused grants retry with `grantStageBypass`; players who log in later receive it), plays the FINALE ceremony, posts `OriginEvent.Victory` (boss level, altar, boss) and runs `#firmages:origin/won`. A second tagged death does nothing. `OriginService` knows only the tag; the boss itself lives in package `boss` (§16.4).
- **FINALE ceremony (`CeremonyService.startFinale`):** one FULL `AgeTransitionPayload` with stage `finale_won`, tier -1, at the altar: title "Beyond the Firmament" (`firmages.age.finale_won.title`), Caelum's line `firmages.shrine.voice.finale_won`, sting `minecraft:ui.toast.challenge_complete`, end-rod and totem particles, a white-violet sky, the blessing slot as "Trophies: MekaSuit and Meka-Tool are unlocked", and eight visual lightning bolts around the altar. No reload (finale_won is no Age). The beam is particles only (the beacon beam is drawn by the shrine heart's renderer). `kubejs/server_scripts/stages/on_stage_added.js` stays silent for `finale_won` from firmages-core 0.4.0 on, so there is one title.
- **KubeJS binding additions:** `FirmAges.originDimension()` (`"firmages:origin"`), `FirmAges.originAltar()` (an `int[3]`: x, y, z), `FirmAges.finalBossTag()` (`"firmages.final_boss"`), `FirmAges.isFinaleWon()`. (M6 to 0.9.x also `FirmAges.finaleGateway()`; removed in 1.0 with Gateways to Eternity.)
- **The boss answers (1.0, `boss/BossEvents`):** the core summons its own final boss, The Primordial (§16.4), 2.5 blocks above the altar (`BossEvents.summon`, only while the altar's chunk ticks entities: a boss in an unloaded chunk would be invisible to `fightActive`). `OriginEvent.Gathering` summons it with the Gathering's size as its party size, unless a tagged boss lives already (a forced Gathering during the fight). `OriginEvent.SummonBoss` is answered with `summoned()` (party size = the players near it when it wakes) or `refuse(...)` (it is already awake, or nobody keeps the altar's chunk loaded); an event another listener answered first is left alone, so a pack can replace the boss with a listener of higher priority. The pack has no boss script any more (`kubejs/server_scripts/finale/` left with 0.10.0; until 0.9.x a KubeJS script ran Gateways to Eternity waves and a Cataclysm boss). Scripts and datapacks may still listen to the events and fill `#firmages:origin/start`; they never grant `finale_won` themselves.
- **Operators' shortcuts (0.9.0):** `/firmages origin gathering` runs the same start (message, count, `OriginEvent.Gathering`, `#firmages:origin/start`) at once for everyone online, regardless of radius and cooldown. `/firmages origin fight` posts `OriginEvent.SummonBoss` (level, altar); The Primordial answers (see above) and the command reports a refusal; without an answer the command fails ("Nothing answered the boss summon"). Both refuse once the finale is won; `gathering` also while the fight runs (N5). N5: `/firmages origin kill` kills the hostiles in The Origin but never the boss (1.0: endermen, or what a pack adds; The Primordial has no adds), `/firmages origin kill boss` kills the tagged boss, whose death goes through `onLivingDeath` like any other (§10).
- **Tests:** G `operatorCommands` (permission 0: none of the three commands exists; a plain tp keeps the lock, the forced tp lets a survival player without age_9 in and both locks let him stay past ProgressiveStages' checks; `gathering` starts at the arrival point 14 blocks out and wakes one Primordial; `fight` is refused while it lives and summons a tagged Primordial above the altar once it is gone; leaving ends the forced visit and the next plain tp is turned back; after the win both refuse). G `gatheringAndFinalBoss` (a creative headless player, `/firmages origin tp`, no start at the arrival point, one start at the altar with one event that wakes one Primordial sized for one, no second start while gathered, none after breaking up within the cooldown, a function tag at the altar, an untagged zombie changes nothing, the tagged zombie's death wins, grants finale_won and sends exactly one FINALE payload at the altar, a second tagged death and a later Gathering do nothing; M6: a tagged zombie dying in the overworld changes nothing). N5: G `noRepeatWhileFightRuns` (forced chunks around the altar; the first arrival starts the Gathering and wakes The Primordial; with it alive and the cooldown at 0, three walks away and back start nothing and summon no second one, count 1; once it is gone (the wipe) the next arrival starts Gathering 2 and wakes it anew). G `operatorKill` (permission 0: neither command; `kill` takes two untagged zombies, spares the tagged boss and a cow, wins nothing, then fails with nothing left; `kill boss` takes the boss and wins the finale once, then fails).

### 16.4 The Primordial (1.0, own boss entity)

- **Entity `firmages:primordial` (`boss/PrimordialEntity`, GeckoLib 4.9.3, a required dependency since 1.0):** the whole end fight in one entity, no adds, no Gateways waves; three phases by health (The Dark Before, Nine Lights, Firmament Shatter), a veil that gates damage and opens only on the counter of the running attack, damage scaled after armour to an effective pool of `boss.effectiveHealth * ((1 - f) + f * players)` (`f` = `boss.perPlayerFactor`), per-attacker hurt cooldown, soft enrage, home above the altar, the final-boss tag from birth (its death wins the finale through the path of 16.3). Design, numbers and tuning knobs: `docs/primordial.md`.
- **Arena:** in phase 2 and 3 the arena's sea lanterns become polished blackstone and come back when it dies, withdraws (wipe) or is removed (`boss/ArenaLights`, positions saved with the entity); while it lives nothing spawns within 48 blocks of it (`OriginSpawns`).
- **Summon:** the core itself, `BossEvents` on `OriginEvent.Gathering` and `OriginEvent.SummonBoss` (§16.3); `/summon firmages:primordial ~ ~ ~ {PartySize:n}` works too (0 or absent: the players present at birth). Config: `[boss]` in `firmages-server.toml` (`effectiveHealth`, `perPlayerFactor`, `veilFactor`, `enrageMinutes`, `starfallEnrageMinutes`, `wipeSeconds`); the Gathering itself keeps `origin.gatherRadius`, `origin.gatherIdleMinutes` and `origin.gatherCooldownSeconds`.
- **Tests:** G `BossGameTests` (phases and lanterns, veil and keel window, per-attacker damage and pool, shatter, finale through the tag, `summonedFightIsWonByScaledDamage`: `/firmages origin fight` without a script, party size from the two players present, every hit loses exactly the scaled amount and stops at the phase floor, the players' last hit grants `finale_won`; home, leftovers, GeckoLib asset parse), G `OriginGameTests` (the Gathering and `origin fight` wake it, §16.3), U `OriginSpawnListTest`.

## 17. M7: Consecration and maintenance mode (Felix, 2026-10-01)

Felix's decisions of 2026-10-01: the shrine becomes **one unified structure**. When Caelum accepts a ring's offering the ring is consecrated: every ring block becomes a block of one mod family with a shared look; the Age materials are replaced completely, one colour accent per Age remains. The transformation is part of the ceremony, ring by ring from the heart outward. Consecrated blocks are unbreakable (survival mining, explosions, pistons, fluids); a team member toggles **maintenance mode** at the heart, and only then they can be broken and drop their original material. A broken ring pauses the blessing only; Ages are never revoked.

### 17.1 Blocks (naming contract with the textures workflow)

- `firmages:consecrated_<role>` for the roles `stone`, `brick`, `pillar`, `lamp`, `metal`, `glass`, `trim`, `scaffold` (`shrine/ConsecratedBlock`, `ConsecratedLampBlock`, registered in `ShrineRegistry.CONSECRATED`). Blockstate `accent` = 0..8, the ring / Age index (ring N is built in age_N); the lamp also has `lit` (default and always set true, light 15).
- Like bedrock: hardness -1, blast resistance 3,600,000, `PushReaction.BLOCK`, `forceSolidOn`, no loot table (no drops by default), `canBeReplaced(fluid)` false, `canEntityDestroy` false, `onBlockExploded` does nothing, no mob spawns on it. `glass` and `scaffold` are see-through (no occlusion, sky light passes). Tag `#firmages:consecrated` is in `minecraft:wither_immune`, `dragon_immune`, `features_cannot_replace`, `mineable/pickaxe` (speed during maintenance), `create:non_movable`, `c:relocation_not_supported` and `mekanism:cardboard_blacklist`. Endermen only take `#minecraft:enderman_holdable`. Block items exist for operators (creative tab Operator Utilities); survival has no source.
- **Models:** blockstate `multipart`: the base model `block/consecrated_<role>` (`cube_all` on `textures/block/consecrated_<role>.png`; the pillar is a `cube_column` with side `consecrated_pillar_side.png` and end `consecrated_pillar_top.png`; the lamp uses `consecrated_lamp.png` for `lit=false` and `consecrated_lamp_lit.png` for `lit=true`; glass `translucent`, scaffold `cutout`) plus, per accent, the overlay model `block/consecrated_accent_<n>` (a full cube with `textures/block/consecrated_accent_<n>.png`, `cutout`, full-bright through the element's NeoForge `neoforge_data` `block_light`/`sky_light` 15, no AO). Item models use the base (the lamp: the lit model). The heart gets `block/shrine_heart_maintenance` (a `consecrated_scaffold` cage, `cutout`) while `maintenance=true`. The textures come from `dev/gen_textures.py` (textures v2, `dev/archive/textures-notes.md`); `--check` fails if a consecrated texture is not used by a model.

### 17.2 Roles and validation

- `consecration.json` (§2.6) maps each ring matcher to a role. Shipped: ring 0 hearth stones `stone`, posts `pillar`, thatch `trim`; ring 1 bricks `brick`, bronze blocks and the bell `metal`; ring 2 smooth stone `stone`, bars `scaffold`, lamps `lamp`; ring 3 sourcestone `brick`, otherstone `pillar`, source gem blocks `glass`, candles `lamp`; ring 4 coke bricks `brick`, heavy engineering `metal`, steel scaffolding `scaffold`, electric lanterns `lamp`; ring 5 aluminium `metal`, red steel `trim`, HV capacitors `pillar`, aluminium scaffolding `scaffold`, floodlights `lamp`; ring 6 certus quartz `stone`, steel casings `metal`, quartz glass `glass`, fluix `trim`; ring 7 steel plating `metal`, desh `pillar`, ostrum `trim`; ring 8 calorite `metal`, quantum casings `pillar`, awakened draconium `lamp`. 537 positions over the nine rings. `ShrineDataLoader` reads each tier's ring JSON (`modonomicon/multiblocks/<path>.json`) through the resource manager and resolves key -> role per ring (`ringRoles`); a key without a role stays Age material (WARN).
- `ShrineMultiblocks.check` (§7.2) accepts a consecrated block of the key's role at any accent. It returns the cells of the best rotation (position, real pattern key, role, ok, consecrated, accent), which the consecration uses. The ghost preview stays the build recipe in Age materials (the Modonomicon multiblocks); for a broken ring of an awakened tier the ghost is not sent (it would mark every consecrated block as wrong): the player sees `wax_off` sparks at the holes and "... is broken (n of m blocks). Sparks mark the gaps: place the original material or a consecrated block of the same kind."

### 17.3 Consecration (`shrine/ShrineConsecration`, server thread)

- **Ceremony:** `ShrineService.complete` (prayer heard) schedules every role position of the accepted ring before the grant. The batch starts 20 ticks after the Age reload is idle (so the reload freeze does not cut it in two) and runs over `shrine.consecrationTicks` (120 = 6 s; the batch clock counts server ticks, but while the server catches up after a freeze, which vanilla does back to back whenever its 15 s rate-limited "Can't keep up" warning stays silent, a tick counts only its real duration, so the spread lasts 6 s of real time; `/tick sprint` and the GameTest server count every tick): offset = horizontal distance from the heart, normalised over the ring, plus one tick per block of height (from the heart outward, bottom up). Each block: the state seen at scheduling must still be there (else skipped; the next validation decides again), the original state goes into `ShrineSavedData.originals` (`ns:id[props]`, sorted, `Consecration.encodeState`), a block entity is removed first (no inventory spills or machine teardown drops; stored energy is lost), then the consecrated state with the ring's accent; `end_rod` and `enchant` particles, at most one `firmages:shrine.consecrate` sound per tick (vanilla `block.amethyst_block.resonate` in `sounds.json`).
- **After every validation** (`afterValidation`, never in maintenance): every standing ring k of an awakened tier (k below `awakened`) whose role positions still hold Age material, or a consecrated block of another accent, is consecrated: up to 4 blocks at once (repairs), more over `shrine.consecrationTicks` (migration). This covers existing worlds after the update (the heart validates on its first tick after load), Ages granted by quests or admins, and repairs. Positions with a scheduled change are skipped.
- **Heart removed:** every scheduled change is dropped and every stored original is put back (bottom up, next tick); the records go. A re-placed heart in the same place consecrates the standing awakened rings again.

### 17.4 Maintenance mode

- **Toggle:** sneak and punch the heart with an empty main hand (`PlayerInteractEvent.LeftClickBlock` START, server side, cancelled so the heart never breaks; 10-tick cooldown per player); the prayer stays sneak plus held use. Also `/firmages shrine maintenance on|off`. Any player may toggle it (one team). On: `maintenanceUntil` = now + `shrine.maintenanceSeconds` (60 s), heart `maintenance=true`, chat to everyone ("X opened the shrine for maintenance: for 60 s consecrated blocks can be broken and give back their original material..."), a sound, the sync payload; while on, the actionbar counts down every second for players within the shrine radius + 16. Off (punch, command or timer): chat, heart `maintenance=false`, re-validation, which consecrates what stands again. The prayer is refused during maintenance ("Caelum does not listen while the shrine is under maintenance.").
- **Breaking:** `ConsecratedBlock.getDestroyProgress` is 0 outside maintenance (client: from `firmages:shrine_sync`), in maintenance the speed of hardness 1.5 (a pickaxe helps). `BlockEvent.BreakEvent` (HIGH) cancels any break of a consecrated block by a survival player outside maintenance (actionbar hint) and by a `FakePlayer` always; creative players may break it (no drop, the record stays). Machines never get past hardness -1. `playerDestroy` drops the stored original: its loot table with the player's tool, else its block item; without a record, or with an unknown block, nothing (WARN in the log).
- **Holes and repair:** a hole only fails the ring (`validRing` falls, `intact` false: blessings pause with the usual chat line; the prayer is refused; Ages stay). Placing the original material (anything the ring's matcher takes) or a consecrated block of the role repairs the ring at once; Age material in an awakened ring is consecrated again on the next validation once maintenance is off. The record of the hole stays until the position is consecrated again (it then holds what was placed).
- **Explosions** (`ExplosionEvent.Detonate` removes consecrated positions from the list, plus the resistance), **pistons**, **endermen** and **fluids** never affect consecrated blocks, in maintenance too.

### 17.5 Commands, sync, Jade, KubeJS

- Commands (§10): `shrine maintenance on|off|status`, `shrine consecrate <ring>` (debug, a complete ring now), `shrine originals` (per-ring counts in chat, the list in the log); `shrine status` gets a consecration line.
- `firmages:shrine_sync` (§11) keeps a client copy of maintenance and the original block ids.
- Jade (`FirmagesJadePlugin.Consecrated`, config `consecrated`): "Consecrated <role>", "Ring of the <Age>" (the accent), "Original: <block>" (from the sync), and "Maintenance mode: n s left" or "Unbreakable (maintenance mode at the heart)"; the heart shows the maintenance line too.
- KubeJS binding: `FirmAges.shrineMaintenance()`, `FirmAges.consecratedBlocks()` (stored originals).

### 17.6 Tests

- U `ConsecrationTest` (3): the shipped `consecration.json` parses, every block key of all nine rings has a role (heart and plinth excluded), all eight roles used, 537 positions, the file loads through `ShrineData` without warnings; bad entries rejected (bad id, unknown role, two-character key, unknown key) and pattern overrides apply to their multiblock only, block-state matchers lose their properties; the original-state string round trip (sorted properties, no properties, malformed strings rejected).
- G `ConsecrationGameTests` (3): `ringValidatesBeforeDuringAfter` (ring 0 from vanilla stand-ins in Age material, half consecrated, fully consecrated, a wrong role fails, any accent fits, Age material next to consecrated blocks, plinth key positions unchanged); `consecrationCycle` (prayer schedules the 20 blocks, nothing before the reload, the ring stays valid while it turns and the inner stones turn before the corner posts, 20 originals with their properties and an NBT round trip, valid and intact; an explosion of power 3, a powered piston and fluids change nothing; survival break refused outside maintenance; the sneak-punch through `ServerPlayerGameMode.handleBlockBreakAction` turns maintenance on with the heart standing and `maintenance=true`, the prayer refused, the block breaks and drops cobblestone, ring and intact fall, Ages stay; cobblestone repairs it without consecration during maintenance; the second punch ends it and the stone is consecrated again with its new original; the 5 s timer ends a mode by itself; removing the heart turns all 20 back); `awakenedRingMigrates` (age_1 held, ring 0 built in Age material: consecrated on the heart's first validation with 20 originals, valid and intact; heart removal reverts). The M4 ritual test now also sees ring 0 consecrating after its prayer.

## 18. Immersion (Felix, 2026-10-13)

Felix's decisions of 2026-10-13: full immersion. In Dawn nothing of the pack's machinery is visible: no mod chat or pop-ups on join, no recipe viewer and no block overlay until a later Age, no advancement toasts, chat or screen, no mod list and no mod names. The quest UI stays FTB Quests on K (themed, with Certain Questing Additions; Felix's choice B); this mod adds a small HUD Age indicator, a first-join welcome and the TerraFirmaCraft survival changes. Pack files (configs, FancyMenu, Default Options, the quest theme) belong to other workflows; this section is the code side. Classes: `immersion/*` (common, pure where possible), `client/immersion/*`, `compat/tfc/*`, `compat/ftbquests/FtbQuestsScreens`, `mixin/client`, `mixin/ars`, `mixin/trainutilities`, `mixin/corgilib`, `mixin/tfc`. Research: `Modpack-Planung/research-raw/ux-chat-and-popups.md`, `ux-screens.md`, `ux-quest-ui.md`.

### 18.1 Silent join

| Source | What it did | Hook |
|---|---|---|
| Enhanced Celestials 2: Core 2.0.3.3 (replaces Enhanced Celestials 6.0.2.6 and its "Enhanced Celestials 2 is here!" screen, round B) | on every join (`ClientPlayerNetworkEvent.LoggingIn`, `AddonPrompts.notifyAll`) a chat line "Enhanced Celestials 2 translation settings not yet configured" while `config/enhancedcelestials2core/translations.json` is missing; with the file, a "translations are incomplete" line for client languages it has no full translation for (`Prompt_Language_When_Translation_Incomplete`); prompts for Default Lunar Events and Shader Support only when those add-ons are missing (both ship). No join screen (`MissingAddonScreen` opens only from `/ecc`) [verified, javap] | no code: the pack ships `config/enhancedcelestials2core/translations.json` (`Translation_Mode` `DISABLED`, so it also never downloads community translations at game start; `Prompt_Language_When_Translation_Incomplete` false). `ui.blockedScreens` stays as a mechanism and ships empty |
| Ars Nouveau 5.13.3 | remote one-time Starbuncle Plush gift and Makeship chat ad | `mixin/ars/RewardsMixin`: `Rewards.init()` cancelled at HEAD (`require = 0`) [verified, javap] |
| Create Train Utilities 3.1.0 | red rename notice on every join (Architectury `PLAYER_JOIN` lambda, no config) | `mixin/trainutilities/JoinNoticeMixin`: `net.adeptstack.Main.lambda$init$0(ServerPlayer)` cancelled (`require = 0`; the lambda name is version-fragile, the chat blocklist is the fallback) [verified, javap] |
| Corgilib 5.0.0.10 | remote announcement (chat or widget), its config has no "off" | `mixin/corgilib/AnnouncementTickerMixin` (client): `checkedAnnouncementTicker(Player)` cancelled [verified, javap] |
| any | system or mod chat | `mixin/client/ChatComponentMixin` on `ChatComponent.addMessage(Component, MessageSignature, GuiMessageTag)` (the one-argument overload delegates to it) [verified, NeoForge 21.1.252 sources]: an unsigned line whose text matches `chat.blocklist` (Java regex, `find`) is dropped and logged at INFO; signed player chat is never touched. Shipped patterns: `^ATTENTION \(Create Train Utilities\)` and `(?i)Starbuncle Plush\|makeship` |

The mod-gated mixins are skipped by `FirmagesMixinPlugin` when their mod is absent (packages `trainutilities`, `corgilib`, `tfc` added); every mod-specific target is `@Pseudo` by name or compiled against the pack jar (TFC).

### 18.2 Advancements, menus, mod names

- **Toasts:** `ToastAddEvent` is cancelled for the classes in `ui.blockedToasts` (`ui.hideToasts`): vanilla `AdvancementToast`, `RecipeToast`, `TutorialToast` and Patchouli's `ClientAdvancements$LexiconToast`. FTB Quests toasts stay, so quest completion is the feedback channel. Advancements themselves stay server side (22 quest tasks need `tfc:story/*` and `tfc:world/*`); the chat line is the pack's `announceAdvancements` gamerule.
- **Screen:** opening `AdvancementsScreen` (key or pause menu) is cancelled and the FTB Quests screen opens on the next client tick instead (`ui.redirectAdvancements`; `ClientQuestFile.openGui()` [verified, javap]). The book item stays disabled (pack).
- **Buttons:** `ScreenEvent.Init.Post` hides the widgets labelled `fml.menu.mods` (title screen `ModsButton`, pause screen full-width button) and `gui.advancements` (pause screen) [verified, NeoForge 21.1.252 sources] (`ui.hideMenuButtons`). On the pause screen Statistics takes the advancements slot at full width and the rows below the mods button move up 24 px; the title screen is only hidden (its layout is FancyMenu's). 0.7.2: on the title screen the icon buttons labelled `options.language` and `options.accessibility` are hidden as well, because FancyMenu 3.9.14 cannot address them: its title-screen identification still maps the pre-1.20.5 keys `narrator.button.language`/`narrator.button.accessibility`, so `mc_titlescreen_language_button`/`_accessibility_button` never resolve on 1.21.1 [verified, javap]. Settings for both stay reachable under Options.
- **Other mods' menu widgets (N5, Felix 2026-10-14):** `ui.hiddenMenuWidgets` lists widgets hidden on the title and pause screens by fully qualified class name or label translation key (`immersion/MenuWidgetFilter`, applied in a LOWEST `ScreenEvent.Init.Post` listener after every mod added its widgets, independent of `ui.hideMenuButtons`). Default: Supplementaries 3.9.9's `net.mehvahdjukaar.supplementaries.client.screens.ConfigButton`, the small gear button (label the literal "s") it adds next to the Realms button of the title screen and next to a pause-screen button (`ClientEvents.addConfigButton`, NORMAL priority; `configs_button` in `supplementaries-client.toml`) that opens Moonlight's list of every mod's config [verified, javap; found by scanning the 216 client jars for `TitleScreen` plus `ScreenEvent$Init`/`addRenderableWidget`]. FancyMenu's layout moves the Realms button, the gear stayed in the middle right.
- **Locked items** (0.8.1, Felix 2026-10-14): ProgressiveStages 3.0.6 `client.ClientEventHandler.onItemTooltip` [verified, javap] replaces line 0 of a locked item with "Unknown Item" and appends its block (an empty line, the "Item Locked" / "Recipe Locked" / "Item and Recipe Locked" message, the required-stage line, the current-stage line, an optional stage description), but leaves every other mod's lines, so TFC's "Melts into ..." showed on locked metal. In the same LOWEST handler, an item that PS reports locked (its own test: not `ClientLockCache.isCreativeBypass()`, and the item's or its recipe's required stages from `ClientLockCache`, else the local `LockRegistry`, contain one that `ClientStageCache.hasStage` lacks; `compat/progressivestages/LockedTooltip`) keeps line 0 and PS's block, found by the plain text of the three locked messages (`StageConfig`), and nothing else (`immersion/TooltipTrim.lockedKeep`; `ui.trimLockedTooltips`). Without PS's block (no stage shows tooltips) only the name stays.
- **TFC size and weight line** (0.8.1, Felix 2026-10-14): TFC 4.2.11 `ItemSizeManager.addTooltipInfo` [verified, javap] adds "⚖ <weight> ⇲ <size>" (gray; `Helpers.translateEnum`, keys `tfc.enum.weight.*` and `tfc.enum.size.*`) to every item; `TFCConfig.CLIENT` (`config.ClientConfig`) has no key for it (every field read). The handler drops every line that holds both a weight and a size key (`TooltipTrim.isSizeWeight`; `tfc.hideSizeWeight`). The size still governs TFC's storage rules; only the line is gone.
- **Ars Nouveau HUD by Age** (0.8.1, Felix 2026-10-14): Ars Nouveau 5.13.3 [verified, javap] registers the GUI layers `ars_nouveau:mana_hud` (shown whenever mana is below the maximum, so from the first spawn on; no client config key hides it) and `ars_nouveau:spell_hud`. `RenderGuiLayerEvent.Pre` cancels both while the Age from `firmages:age_status` is below `ui.manaHudFromAge` (client, default `age_3`, the Arcane Age; before the first status: Dawn).
- **Mod names in tooltips:** a jar scan of the 193 client mods found only Jade adding a mod-name line through `ItemTooltipEvent` (off in `jade.json`); EMI and JEI add theirs in their own panels (pack configs). As a safety net, `ItemTooltipEvent` (LOWEST) drops tooltip lines after the item name whose plain text is exactly a loaded mod's display name or "Minecraft" (`ui.hideModNameTooltips`).

### 18.3 EMI and Jade by Age

- The server decides: `firmages:age_status` (§11) carries `recipeViewer` = current Age >= `ui.emiFromAge` and `blockInfo` = current Age >= `ui.jadeFromAge` (`firmages-server.toml`; defaults: `emiFromAge = age_1`, the Bronze Age, since 0.7.1, and `jadeFromAge = age_1`, the Bronze Age too, since 0.8.0, Felix 2026-10-13 H9; it was `age_0` in 0.7.1 and 0.7.2). The current Age is the highest unlocked Age of the global Age state. Before the first status arrives the client counts the world as Dawn. Sent on login and from `AgeService`'s persist step after every Age change.
- What EMI shows once it is open follows the player's Ages too (§21).
- **EMI** (`client/immersion/EmiBridge`, reflection, no compile dependency): `dev.emi.emi.config.EmiConfig.enabled` (public static boolean; while false EMI draws nothing and takes no input except its toggle) [verified, javap, EMI 1.1.24]. Every client tick `immersion/ViewerGate.target` decides: while the Age is locked EMI is forced off (also after the player's own Ctrl+O); when the Age opens it EMI is switched on once; after that the player's toggle is left alone. Operators (permission level 2, `LocalPlayer.hasPermissions`) and players in creative mode have the viewer in every Age (`ViewerGate.open`, 0.9.1), so an operator can look up recipes in Dawn and the Stone Age; back in survival without operator rights it closes again. Not written to `emi.css`.
- **Jade:** `FirmagesJadePlugin` registers a ray-trace callback at priority 10000 (callbacks run by ascending priority, so it has the last word) that returns `null` while `blockInfo` is false; Jade then shows nothing, blocks and entities alike, its toggle key included [verified, javap, Jade 15.10.6].

### 18.4 HUD Age indicator

- GUI layer `firmages:age_indicator` (below `minecraft:boss_overlay`), top left: a dark panel with a gold base line, the goal item's icon and "<Age> · Goal: <item>" (`firmages.age.<id>.name`, `firmages.hud.goal`; the goal is the current Age's offering from `firmages_shrine/offerings.json`; Dawn and the Singularity Age name their goal by lang key: "the First Spark", "the Gathering"; after the Gathering (0.9.0, `age_status.gathered`) `firmages.hud.goal.age_9_gathered` "Beyond the Firmament").
- `ui.hudMode` (client): `FADE` (default) shows it for `ui.hudFadeSeconds` (6) after joining and after every Age change, the last second sliding up and fading out; while the key **Show Age (hold)** (`key.firmages.show_age`, default **H**, category Firmament Ages) is held it is fully shown and fades over 1 s after release. `ALWAYS`; `NEVER` (the key included). Hidden while F1, F3 or any screen is open. `immersion/HudFade` is the pure timing function.

### 18.5 First-join welcome

- Server: on `PlayerLoggedInEvent` a player without the attachment `firmages:welcomed` (Boolean, saved with the player, kept through death) gets `firmages:welcome` and the flag is set (`ui.welcome`). Per player, not per team, so a friend who joins later is welcomed too. `/firmages welcome reset <players>` clears it, `/firmages welcome show <players>` shows it now.
- Client: once no screen is open (the terrain screen has closed), 2 s later the title "Firmament Ages" with the subtitle "The sky remembers. Kindle the first spark." (10/70/20 ticks), 5 s after that `WelcomeScreen` (pauses singleplayer): three panels, "The Ages" (Dawn, the Shrine, the offering, the prayer), "Your Path" (the quest key, read from the bound `key.ftbquests.quests`, K by default, as the one place to look), "Caelum Watches"; buttons "Show my path (K)" (closes the screen, then opens FTB Quests, so Esc returns to the game) and "Into the world". Panels side by side on wide windows, stacked on narrow ones. All text is lang (`firmages.welcome.*`).

### 18.6 TerraFirmaCraft: no thirst, nutrition only as a bonus

TFC 4.2.11 [verified, javap]: thirst lives in `PlayerInfo.thirst` (start 100); `tick` lowers it through `addThirst` -> `setThirst` (exhaustion times `thirstModifier`, plus heat with `enableThirstOverheating`), and below 20 / 10 it adds slowness, mining fatigue and dehydration damage; the thirst bar is GUI layer `tfc:thirst` (client config `enableThirstBar`). Nutrition: `IPlayerInfo.getHealthModifier()` maps the average nutrition from `nutritionMinimumHealthModifier` (0 %, default 0.2) over `nutritionDefaultHealthModifier` (40 %, the starting nutrition, 0.85) to `nutritionMaximumHealthModifier` (95 %); `ForgeEventHandler.onLivingHurt` divides a player's damage by it and `IngameOverlays.renderHealthBar` multiplies the shown health by it. TFC's server config is per world, so the code does it rather than a shipped config:

- `mixin/tfc/PlayerInfoMixin`: with `tfc.disableThirst` (server, default true) `setThirst` always stores the maximum and `tick` first refills a lower value loaded from an older save. No thirst effect can trigger; drinking still works but changes nothing.
- `mixin/tfc/TfcDamageMixin` and (client) `TfcHealthBarMixin`: with `tfc.noNutritionMalus` (default true) the modifier is at least TFC's starting value (`compat/tfc/NutritionFloor`), so a one-sided diet never costs health and a varied one still raises it up to the maximum.
- Client (`[tfc]` in `firmages-client.toml`): `hideThirstBar` cancels `RenderGuiLayerEvent.Pre` for `tfc:thirst`; `hideNutrition` removes the nutrition lines of food tooltips (`tfc.tooltip.nutrition*`, `hold_shift_for_nutrition_info`, `tfc.enum.nutrient.*`); expiry and traits stay. Food decay, hunger and tool durability are untouched.
- **Inventory tabs by Age** (0.7.2, Felix 2026-10-13). TFC 4.2.11 [verified, javap]: `ClientForgeEventHandler.onInitGuiPost` adds `PlayerInventoryTabButton`s for the tabs Inventory, Calendar, Nutrition, Climate and, when Patchouli is loaded, Book (the Field Guide) to the survival inventory (and the creative one with TFC's `enableTabsInCreative`); `CalendarScreen`, `NutritionScreen` and `ClimateScreen` add the same buttons in their own `init`. A click sends `SwitchInventoryTabPacket`; its server `handle(ServerPlayer)` is the only place that opens the calendar, nutrition and climate menus (`TFCContainerProviders`) and the Field Guide (`PatchouliIntegration.openGui`). Each tab except Inventory has a first Age in `firmages-server.toml [ui]` (`immersion/InventoryTab`): `calendarTabFromAge = "age_0"` (Stone Age), `climateTabFromAge = "age_2"` (Iron Age), `nutritionTabFromAge = "never"` (nutrition only gives a bonus, see above), `fieldGuideTabFromAge = "never"` (hidden for the whole pack); the value is an Age id or `never`, an invalid one is reset to the default. The open tabs travel in `firmages:age_status` (§11) like the EMI and Jade flags.
  - Client: `ScreenEvent.Init.Post` (LOW, after TFC) on every screen removes the tab buttons whose tab is not in the status (`compat/tfc/TfcClient.applyTabs`, `ScreenEvent.Init.removeListener`) and moves the remaining ones up so the n-th visible tab takes the n-th slot (TFC places tab n at `Tab.yIn`, 23 px apart; the button and its icon move, `mixin/tfc/InventoryTabAccessor`). Before the first status the world counts as Dawn: only the Inventory tab. A status change applies the next time a screen opens.
  - Server: `mixin/tfc/SwitchInventoryTabMixin` cancels `SwitchInventoryTabPacket.handle` at HEAD when the tab's Age is not open (`compat/tfc/TfcTabs.allowed`, the same status the client gets), so a client that still shows a button cannot open the page. The Inventory tab always works; the Field Guide item itself is not affected.

### 18.7 Config, network, tests

- `firmages-server.toml`: `[ui] emiFromAge = "age_1"`, `jadeFromAge = "age_1"`, `calendarTabFromAge = "age_0"`, `climateTabFromAge = "age_2"`, `nutritionTabFromAge = "never"`, `fieldGuideTabFromAge = "never"`, `welcome = true`; `[tfc] disableThirst = true`, `noNutritionMalus = true`.
- `firmages-client.toml`: `[ui] hudMode = FADE`, `hudFadeSeconds = 6`, `hideToasts`, `blockedToasts`, `redirectAdvancements`, `hideMenuButtons`, `hideModNameTooltips`, `blockedScreens`, `questParagraphGap = 4` (§18.8, §18.9), `questWindowPercent = 75` (§18.9), `trimLockedTooltips = true`, `manaHudFromAge = "age_3"` (§18.2), `hiddenMenuWidgets` (§18.2, N5); `[chat] blocklist`; `[tfc] hideThirstBar`, `hideNutrition`, `hideSizeWeight` (all true by default).
- Network (§11, S2C, optional): `firmages:age_status` (age id, goal item id, recipeViewer, blockInfo, TFC tab bits, caveDweller §19, toolRule §19.2), `firmages:welcome` (no fields).
- U `ImmersionTest` (14): the status (Dawn hides everything, viewer and overlay open at their Age, highest Age with gaps, offerings per tier, no goal in age_9 or without an offering), config parsing, the TFC tabs (defaults and keys, `never` and Age ids with or without namespace, unknown values fall back to the default, open tabs per Age for every Age, the bit mask), `ViewerGate` (forced off while locked, on once, then the player's choice), the nutrition tooltip keys, `HudFade` (6 s with the last second fading, key hold and release, ALWAYS, NEVER, short show times), the chat blocklist (the Train Utilities literal from the jar and the Makeship line blocked, player chat and Caelum not, invalid patterns skipped), `NutritionFloor`.
- G `ImmersionGameTests` (4; the TFC parts run in the opt-in run `gradlew runGameTestServer -PgametestTfc`, which adds TerraFirmaCraft and Patchouli and in which 13 older shrine and gate tests fail because TFC changes the vanilla stand-in blocks and recipes they use; without TFC it passes empty): `welcomeOncePerPlayer` (headless player: the login status equals the server status and there is one welcome; a rejoin with the saved player data gets the status and no welcome; reset gives exactly one more), `statusFollowsAgesAndConfig` (status = highest Age and `ui.*FromAge`, the defaults `emiFromAge = jadeFromAge = age_1`, config changes apply, a broadcast reaches a player once), `tfcTabsFollowAges` (the `ServerConfig` defaults; for every Age the status after encoding and decoding `firmages:age_status` carries no tab in Dawn, the calendar from age_0, calendar and climate from age_2; the server status follows `ui.*TabFromAge` and a change applies at once; with TFC: TFC's own `SwitchInventoryTabPacket.handle` leaves the inventory menu open for the calendar while `calendarTabFromAge = never` and opens it with `dawn`, the Inventory tab switches back, the Field Guide is refused while `never`), `tfcThirstAndNutrition` (`setThirst(10)` and `addThirst(-60)` stay at 100; with the switch off TFC's value stays and the first tick after switching on refills it; at 0 nutrition TFC's modifier is 0.2, yet 2 damage costs 2 / 0.85; with the switch off 1 / 0.2).
- U `MenuWidgetFilterTest` (3): Supplementaries' gear hidden by default, vanilla buttons, inner classes and simple names not; class or label key, spaces stripped, blank and null entries hide nothing.
- U `TooltipTrimTest` (4): the size and weight line by its keys (a size alone or another TFC line stays), a locked item keeps its name and PS's block with TFC lines before and another mod's line after it, the description line only when it is there, without PS's block only the name.
- **Client [M], for Felix:** no Enhanced Celestials screen or chat line on join; no Train Utilities, Ars or Corgilib text; no advancement, recipe or tutorial toasts; L opens FTB Quests; pause menu without Mods and Advancements and without gaps; EMI and Jade hidden in Dawn (Ctrl+O and Jade's key do nothing); EMI and Jade still hidden in the Stone Age and both back in the Bronze Age without relog; the HUD indicator (look, fade, hold H); the welcome title and screen on a fresh player, the button opens the quest screen; TFC thirst bar gone, food tooltips without nutrition, no "⚖ Light ⇲ Small" line on any item, a locked item showing only "Unknown Item" and PS's lines, no Ars mana bar before the Arcane Age and the bar back from age_3; TFC tabs: Dawn only Inventory, Stone Age plus Calendar, Iron Age plus Climate directly below it, never Nutrition or the Field Guide, no gaps, the TFC health bar showing at least the starting health.

### 18.8 Quest description spacing (Felix, 2026-10-13)

FTB Quests 2101.1.36 `ViewQuestPanel.addDescriptionText` [verified, javap] puts each description line in its own text field (line pitch 9 px, the font height) stacked with a 1 px gap, so a blank line, the pack's paragraph break, costs a full 9 px line (paragraphs 18 px apart), and the subtitle is followed by a fixed 7 px spacer. No theme key controls either. `mixin/ftbquests/ViewQuestPanelMixin` (client; skipped without `ftbquests`): `@WrapOperation` on the third `BlankPanel.add` of the method replaces the field of a blank line with a `VerticalSpaceWidget` of `ui.questParagraphGap` px (default 4, range 0..9) unless the player is in edit mode (there the blank line stays an editable field); `@ModifyConstant` on the 7 makes the subtitle gap the same value. Result: paragraphs 14 px apart, subtitle to text 14 px, lines 9 px. The blank test reads the field's raw text through `mixin/ftbquests/TextFieldAccessor` (FTB Library `TextField.rawText`). With the client config not loaded the values fall back to FTB's own (9 and 7).

The window size, the cut-off with `full_screen_quest: 1` and the section gaps of the quest view: §18.9.

### 18.9 Quest book as a window (Felix A, 2026-10-13)

Felix chose variant A of `Modpack-Planung/research-raw/fb2-quest-screen.md`: the quest book is a centred window of `ui.questWindowPercent` (client, default 75, range 0..100; 0 or 100 = fullscreen as in FTB Quests, 1..49 count as 50) of the GUI-scaled game window, with the chapter list, the quest map and every pop-up inside it, instead of fullscreen. The theme keeps `full_screen_quest: 1`, so the quest view is as large as the window. Research: the note above, sections 2, 5 and 8. All mixins in `mixin/ftbq` (client; skipped without `ftbquests`, `FirmagesMixinPlugin`), pure geometry in `immersion/QuestWindow`. Targets: FTB Quests 2101.1.36, FTB Library 2101.1.37 [verified, javap], Certain Questing Additions 1.2.0.4 [verified, decompiled].

- **Size** (`QuestScreenSizeMixin`): `QuestScreen.onInit()` always calls `setFullscreen()`; at its return the screen is set to `QuestWindow.rect` (width and height times the percentage, rounded down). FTB Library centres a smaller screen itself (`BaseScreen.getX/getY`) and runs `onInit` again on every window resize and GUI scale change (`ScreenWrapper.init` -> `initGui`), so the window follows both. `initGui` lays out only the screen's own widgets; an open quest view is refreshed in the same hook, so it follows a resize too (before, it kept its old size until reopened). While the screen is smaller than the window, `drawDefaultBackground` returns true: the world around it is blurred and dimmed like around other menus (FTB Quests draws no background, which only works fullscreen).
- **Pop-ups** (`BaseScreenModalMixin`): `BaseScreen.pushModalPanel` clamps with `min(panel.getX(), screen width - panel width - 10)`, mixing the absolute `getX()` with the relative `setX`, and puts a panel as large as the screen at -10 (the quest view cut off at the left and top, Felix's screenshot). It now clamps in screen coordinates (`QuestWindow.modalPos`): a panel that fits keeps FTB's 10 px margin where there is room and never starts before the screen's edge; a larger one (FTB's item list of a task is 3/4 of the window high) is centred on the screen and so stays inside the window. Applies to every FTB Library screen; on a fullscreen one it only changes panels that FTB would have pushed off the left or top edge.
- **Quest view drawing** (`BaseScreenModalMixin`): CQA's `ViewQuestPanelMixin.onDraw` (open animation) stores `getY()` (absolute), calls `setY(y + slide)` with the absolute draw position and restores the stored value, i.e. writes absolute values into the relative position. Harmless fullscreen; on a window the view would be drawn one screen offset too low and creep down every frame. While `BaseScreen.draw` draws the quest view, its position is set to the absolute one and the screen's `getY()` reads 0, so both agree; afterwards the relative position is restored.
- **Chapter list** (`ChapterPanelWindowMixin`): `ChapterPanel.getX()` returns its slide position (`-width` collapsed .. 0 expanded) as an absolute x; it is made relative to the screen, and the panel is drawn clipped to the screen (`GuiHelper.pushScissor`), so it never shows in the margin. Mouse input needs no change (FTB lets the mouse reach a child only inside its parent screen).
- **Map centre** (`QuestPanelCentreMixin`): `QuestPanel.draw` computes the map point at the screen centre from `questScreen.width / 2` (relative) and `getX()` (absolute); zooming (CQA's smooth zoom every frame) and the scroll position saved for the next opening use that point, so on a window the map would jump sideways by the screen offset. The centre is now absolute (`width / 2 + screen x`, the same for y).
- **Section gaps of the quest view** (`QuestViewSpacingMixin`; paragraphs and subtitle: §18.8). `ViewQuestPanel.addWidgets` fixes them in code, no theme key: content (task and reward headings, icons, text) at `max(16, title height + 8)`, separator at `16 + h + 6` under the icon rows of height `h` (the vertical divider ends there), text at `16 + h + 12`, a non-fullscreen view `+ 12` taller than content and title, the content panel `height - 17`. Each shrinks by `QuestWindow.sectionGapReduction(ui.questParagraphGap)` = 6 - clamp(gap, 2, 6): 2 px at the default 4 (title to headings 8 -> 6 px, icons to separator 6 -> 4, separator to subtitle 5 -> 3), none at 6..9 (9 = FTB's spacing everywhere). Line height (9 px) and the text column (width - 6) stay. Injection points by ordinal (`BlankPanel.setPosAndSize` #0, constant 12 #0 and #3, constant 6 #1 and #2, constant 17), all checked against the javap listing; a different FTB Quests build fails loudly at the first opening (`defaultRequire = 1`).
- Sizes at 1920x1080 (GUI-scaled window -> quest window, map panel = window - 40 x window - 2, text column = window - 7): GUI 2 960x540 -> 720x405, map 680x403, column 713; GUI 3 640x360 -> 480x270, map 440x268, column 473; auto (scale 4) 480x270 -> 360x202, map 320x200, column 353. On screen the window is about 1440x810 px at every GUI scale. The quest descriptions are hand-wrapped for about 400 to 545 px (`dev/gen_quests.py`), so at GUI 3 the longest lines and at auto scale most lines wrap a second time (ragged text, research §4); joining each paragraph into one description entry fixes that (data, not part of this section).
- U `QuestWindowTest` (7): the rect at 1920x1080 for GUI 2, 3 and 4 and on a 3440x1440 window at GUI 3, 0/100/out-of-range = fullscreen and 1..49 = 50, the window inside and centred for every size and percentage, a screen-sized pop-up at 0 (FTB: -10), small pop-ups keep the 10 px margin and never go negative, larger ones are centred and fit the window, the section-gap reduction per paragraph gap.
- **Client [M], for Felix:** the quest book (K) opens as a centred window with blurred world around it at GUI 2, 3 and auto, and after resizing the game window or changing the GUI scale while it is open (also with a quest open); the quest view fills the window edge to edge (title and first letters visible, no strip); the chapter list slides in at the window's left edge and is not visible outside it; zooming with the mouse wheel keeps the map centre; pop-ups (task item list, context menus) stay inside; `questWindowPercent = 0` gives the old fullscreen book on the next opening.

## 19. Hardship of the early Ages (Felix, 2026-10-13, round B)

Felix's round-B answers on "Firmament Ideen 2" (plan: `Modpack-Planung/research-raw/fb2-roundB-hardship-plan.md`). Most of them are pack files (beds, torches, F3 coordinates, the blood-moon gate); this section is the code side. Classes: `hardship/CaveDwellerGate` (pure), `hardship/CaveDwellerService` (server), the sound handler in `client/immersion/ImmersionClient`; `hardship/ToolRule` (pure) and `hardship/ToolRuleService` (both sides) for §19.2.

### 19.1 Cave Dweller ReEvolved until the Iron Age (H12)

Cave Dweller ReEvolved 1.4-rc2 [verified, javap]: entity `cave_dweller:cave_dweller` (`ModEntityTypes`); its own server-tick timers per dimension spawn it through `Utils.trySpawnMob` (`EntityType.create`, then `ServerLevel.addFreshEntityWithPassengers`, which fires `EntityJoinLevelEvent`); the cave noises come from `CaveDweller.playCaveSoundToSpelunkers` on the noise timer and after every spawn attempt, as the mod's own S2C packet `cave_dweller:cave_sound` (record `CaveSound`: sound id `cave_dweller:cavenoise_1..4`, position, volume, pitch). The client handler `ClientPayloadHandler.handleDataOnMain` plays it as a `SimpleSoundInstance` (`SoundSource.AMBIENT`) through `SoundManager.play`, which posts NeoForge's `PlaySoundEvent`. No server sound event fires, and the noises keep coming when no dweller can exist.

- Config: `firmages-server.toml [hardship] caveDwellerUntilAge` (default `age_2`, the Iron Age; an Age id with or without namespace, or `never`; an invalid value is reset to the default, an unknown one in code counts as the default). The world's Age is the highest unlocked Age of the global Age state (`AgeService`, as for EMI and Jade), never a stage query per event.
- Server (`CaveDwellerService`): from that Age on `EntityJoinLevelEvent` is cancelled for the dweller (matched by registry id, no compile dependency), which covers its timed spawns, `/summon` and dwellers saved in chunks that load later (a refused entity is not tracked, so its chunk saves without it). `AgeService`'s persist step, after every change of the global Age state, calls `despawnIfOver`, which discards the dwellers in loaded chunks. Below that Age nothing is touched.
- Client: `firmages:age_status` carries `caveDweller` (= current Age < `caveDwellerUntilAge`, so the client needs no copy of the server config). While it is false, `PlaySoundEvent` drops every sound of the namespace `cave_dweller` (the cave noises and, should any still play, the dweller's own). Before the first status nothing is muted (the world counts as Dawn, where the dweller hunts).
- The mod's own config (`config/cave_dweller/server_config.toml`, Fzzy Config) is pack data, not part of this mod.
- Tests: U `CaveDwellerGateTest` (4: default `age_2`, config values with `never`, namespaces and unknown values, `hunts` for every Age with the default and with `never`, the status flag including an Age gap and `UNKNOWN`). G `HardshipGameTests.caveDwellerGoneFromItsAge` (the key `hardship.caveDwellerUntilAge` and its default; `age_2` decides by the live global Age; `never` keeps it and `dawn` removes it in every Age, both in the server decision and in the status after encoding and decoding `firmages:age_status`; a pig is never refused; with Cave Dweller loaded a dweller cannot join and the Age-change hook removes one that joined while allowed, without it `despawnIfOver` finds nothing).
- **[PoC]** (Cave Dweller is not in the GameTest run): before `age_2`, below Y 40 in the dark, `/cave_dweller fast_forward minecraft:overworld 12000 12000` gives noises and a dweller; after `/stage grant @s age_2` an existing dweller is gone at once, `/summon cave_dweller:cave_dweller` fails, the same fast-forward gives neither a dweller nor a sound.

### 19.2 Hand-breaking only with the right tool in Dawn (Felix 2026-10-14, option A)

Research: `Modpack-Planung/research-raw/fb3-client-ux.md` §4. TFC 4.2.11 [verified, javap] already makes logs, raw and hardened rock and ores drop nothing without the correct tool, but they still break by hand in 30 to 40 s. Felix chose option A: a hard stop, in Dawn only (option B, Dawn and the Stone Age, was the research recommendation).

- Block tag `firmages:needs_tool_early` (mod data, synced to clients with the other tags): `#minecraft:logs`, `#c:stones/raw`, `#c:stones/hardened`, `#c:ores`, `#c:sandstone/blocks` (all but the first optional). Not the generic `requiresCorrectToolForDrops` flag: it also covers snow. TFC's small ores (surface nuggets) are not in `c:ores` [verified, jar].
- Config: `firmages-server.toml [hardship] toolRuleUntilAge` (default `age_0`: the rule holds while the world is in Dawn; an Age id with or without namespace; `never` or `dawn` turn it off; an invalid value is reset to the default, an unknown one in code counts as the default). The world's Age is the highest unlocked Age of the global Age state.
- The rule (`ToolRule.blocks`): rule active, block in the tag, player neither creative, spectator nor a `FakePlayer` (machines), and the held item's `isCorrectToolForDrops(state)` false. That is the item's tool component: TFC stone axes and hammers have the wooden tier and are correct for `#minecraft:mineable/axe` and `tfc:mineable/hammer` (= `#minecraft:logs`), so the Dawn stone axe fells trees; TFC has no stone pickaxe, so rock and ore cannot be broken in Dawn.
- `PlayerEvent.BreakSpeed` (LOWEST, both sides) sets the speed to 0: destroy progress stays 0, so no crack overlay and no break. The client takes the rule from `firmages:age_status` (`toolRule`; false before the first status, the server alone decides until then), so client and server agree and nothing snaps back. `BlockEvent.BreakEvent` is cancelled on the server for the same condition (safety net). `PlayerInteractEvent.LeftClickBlock` (server, `START`) shows an action-bar hint at most once per 60 s per player: "You need an axe for this." for `#minecraft:mineable/axe`, "You need a pickaxe for this." for `#minecraft:mineable/pickaxe`, else "You need a tool for this." (`firmages.hint.needs_*`).
- Tests: U `ToolRuleTest` (5: default `age_0` holds in Dawn only; config values with `never`, `dawn`, namespaces and unknown values; the predicate with and without tool, tag, exemption and rule; the hint choice and its 60 s limit; the status flag and `UNKNOWN`). G `HardshipGameTests.toolRuleStopsHandBreaking` (the key and its default; the tag holds oak log and iron ore, not dirt; with the rule on in the world's Age: bare hand and pickaxe on a log speed 0, axe breaks, bare hand and wooden pickaxe on iron ore 0, stone pickaxe breaks, dirt by hand breaks, the bare-hand break of a log is refused and the axe break is not, creative is exempt, the status carries `toolRule` through encoding and decoding; with `toolRuleUntilAge` = the world's Age and with `never` everything breaks again).
- **Client [M], for Felix:** in Dawn punching a log or stone shows no crack and nothing happens, the action-bar hint comes once and not again within a minute, a knapped stone axe fells the log; after `/stage grant @s age_0` the hand works again as in TFC (slow, no drop).

### 19.3 The crafting grid opens with the Stone Age; Dawn crafts on the ground (Felix 2026-10-15, HW2A)

Research: the scratch notes `2x2-machbarkeit.md` and `2x2-vorbilder.md`, decision `entscheidung-holz-2x2.md` part 2. No mod does this for NeoForge 1.21.1 (SevTech never hid the 2x2 grid; "No Inventory Crafting" is client-only and always on), so it is code here, with the same boundary as §19.2. Classes: `hardship/GridLock` (pure), `hardship/GridLockService` (both sides), `hardship/GridSlots`, `hardship/GridLockMenu`, `hardship/GroundCraft`, `compat/tfc/TfcPiles`, `compat/progressivestages/CraftLocks`, `client/immersion/GridLockClient`.

- Config: `firmages-server.toml [hardship] gridLockUntilAge` (default `age_0`: the grid is closed while the world is in Dawn; an Age id with or without namespace; `never` or `dawn` keep it open; an invalid value is reset to the default, an unknown one in code counts as the default). The world's Age is the highest unlocked Age of the global Age state. Exempt: creative, spectator, `FakePlayer`.
- Server lock (`mixin/InventoryMenuMixin`, `mixin/curios/CuriosContainerMixin`, `mixin/RecipeBookMenuMixin`): the player inventory's result slot and four grid slots are `GridSlots.Result` and `GridSlots.Input` (still `ResultSlot` and `Slot`; wrapped at their `new` in `InventoryMenu.<init>`; in Curios 9.5.1 replaced at the end of `CuriosContainer.<init>(int, Inventory)`, because a `@WrapOperation` on the `NEW` there did not apply). While locked they are inactive (not drawn, not hovered), a grid slot takes nothing (`mayPlace`, which also refuses EMI's fill packet and Mouse Tweaks drags) and the result gives nothing; a grid slot can always be emptied. `slotsChanged` of both menus first asks `GridLockService.refuseGridChange`: while locked the result is cleared and anything in the grid goes back to the inventory (or drops), so neither the vanilla lookup nor the mixins of ProgressiveStages and Polymorph run. Curios has its own 2x2 with its own `RecipeManager.getRecipeFor` [verified, javap]; without its mixin it would be a way around. `RecipeBookMenu.handlePlacement` is refused for a locked `GridLockMenu`. Not touched: the 3x3 `CraftingMenu` (crafting table, TFC workbench), Create's mechanical crafter, the Sophisticated Backpacks crafting upgrade (own menu), the recipe gate (it filters the recipe manager), FTB Quests (no `only_from_crafting` task in the pack).
- Status: `firmages:age_status` carries `gridLock` (network version "6"); false before the first status, so the server alone decides until then. When an Age change lifts the lock every player online reads "Your hands have learned: the crafting grid is open (E)." (`firmages.gridlock.open`, the inventory key as a keybind).
- Client (`mixin/client/InventoryScreenMixin`, `mixin/curios/CuriosScreenMixin`, `mixin/emi/InventoryRecipeHandlerMixin`, all `require = 0`): while locked the area of grid, arrow and result frame (x 97..170, y 17..52 of the vanilla `inventory.png`, which both screens blit at their left/top) is filled with the background #C6C6C6, the "Crafting" title is not drawn, the recipe-book button is hidden and an open recipe book is closed before layout; all of it follows the lock live. The blank area (x 97..170, y 8..80) carries a wrapped hint in the label colour, "No grid yet. V: lay items on a block. Sneak + Right Button with an empty hand: craft." (`firmages.gridlock.screen_hint`, TFC's place key `tfc.key.place_block` and the use key read from the keybinds), and the first grid screen opened while locked in a client session puts the full sentence in the action bar (`firmages.gridlock.first_open`; review fix 0.12.0: the blank patch alone gave no clue the first time E was pressed). EMI's `InventoryRecipeHandler.supportsRecipe` answers false, so "+" and shift-craft grey out (EMI is for operators only in Dawn, §18.3).
- Ground crafting (`GroundCraft`, needs TerraFirmaCraft): items laid with TFC's place key (V) form a `tfc:placed_item` pile of up to four (one per quarter: TFC slot 0 south-east, 1 south-west, 2 north-east, 3 north-west [verified, javap]; a large item lies alone). `PlayerInteractEvent.RightClickBlock` (HIGH, both sides) with sneak and an empty main hand reads the pile as a 2x2 `CraftingInput`, first as the player sees it (far row on top, `GridLock.viewOrder`), then in every other arrangement (24 orders, `GridLock.orders`), against `RecipeType.CRAFTING`: the ordinary shaped and shapeless 2x2 recipes, no recipe type of its own. On the server every candidate must pass ProgressiveStages as a grid would (`CraftLocks`: crafting enforcement on, `RecipeEnforcer.canCraftRecipe`, the result's recipe-output lock, hidden locked outputs, the ingredient gate; PS 3.0.6 classes outside its `api` package, held by the GameTest). A match crafts with the crafting player set (TFC's damaging remainders), empties the pile, leaves each remainder on its place, gives the result (or drops it), fires `onCraftedBy`, `ItemCraftedEvent`, the recipe-crafted trigger and the recipe unlock; then TFC's `updateBlock` (accessor) removes the empty pile; item particles of the parts and `ITEM_PICKUP` at pitch 0.8. No match: the event is left alone and TFC hands back one item. The client runs the same lookup and claims the click on a match, so TFC's prediction takes nothing first. Fake players do not ground-craft; ground crafting stays allowed after Dawn.
- Hint (`mixin/tfc/PlacedItemInsertMixin` on `PlacedItemBlockEntity.insertItem(Player, ItemStack, int)` returning true, which covers V and right-click): while the grid is locked and the pile now fits a recipe the player may craft, the action bar reads "Sneak and right-click with an empty hand: <result>" (`firmages.hint.ground_craft`), at most once per 60 s per player.
- Dawn coverage: every Dawn whitelist recipe of `config/progressivestages/stages/age_0.toml` that the pack needs fits a pile (stone tools and javelins: head and shaft; firestarter: two sticks; thatch: four straw; rope: two jute fibre; straw and sticks from twigs: one item). `dev/poc_analyze.py` check D-1 keeps it so.
- Tests: U `GridLockTest` (5: default `age_0` equals the hand-breaking boundary and holds in Dawn only; config values with `never`, `dawn`, namespaces, unknown and null; the predicate with exemptions and the 60 s hint; the pile orders, view order first and 24 distinct permutations for every facing; the status flag through the withers and `UNKNOWN`). G `HardshipGameTests.gridLockedInDawn` (key and default; inventory slots 0 to 4 are `GridSlots`, the rest vanilla; locked: inactive, take and give nothing, four planks put straight into the grid make nothing and are back in the inventory, the recipe book places nothing, the 3x3 table still crafts, creative and fake players exempt, the status carries `gridLock` through encoding; open from the configured Age and with `never`, four planks make a crafting table), `groundCraft` (a four-slot handler with vanilla recipes: four planks make a crafting table and the pile empties; coal and a stick make torches on every pair of places, in both orders and for every facing; two planks are read as seen, side by side a pressure plate, one behind the other sticks; a lone dirt fits nothing; a honey bottle makes sugar and the glass bottle stays on its place; a recipe locked by the GameTest stage `gt_ground` is refused; with `-PgametestTfc` `TfcProbe.groundCraft` uses a real `tfc:placed_item` through `useItemOn`: head and stick give a stone axe and the pile is gone, a lone head and a use without sneaking stay TFC's), `curiosGridLocked` (grid and result are `GridSlots`, the locked grid makes nothing and returns the planks, open it crafts). Selftest `server/grid_lock_hooked` (mixins applied to `InventoryMenu` and, with Curios, `CuriosContainer`; TFC pile hooked; status agrees with the server).
- **Client [M], for Felix:** in Dawn the inventory shows no grid, no arrow, no "Crafting" and no recipe-book button, the Curios screen likewise; an operator's EMI "+" is grey; V on a stone axe head and a stick, then sneak + use with an empty hand gives the axe with sound and particles; the hint comes once per minute at most; after `/stage grant @s age_0` and reopening the inventory the grid is there and the chat line came.
## 20. Start-up performance: TFCRegistryAPI scan scope (2026-10-14)

TFCRegistryAPI 1.2 (required by TFCAstikorCarts and TFCRegrowingForests) [verified, javap]: `TFCRegistryAPI.<init>` calls the seven `*RegistryHelper.discoverRegistryEnums()` (metal, ore, rock, sand, soil, soil variant, wood). Each calls `Helpers.discoverRegistryEnums(Class)`, which iterates `ModList.get().getMods()`, opens every mod file with `FileSystems.newFileSystem(path, null)`, walks every `.class` whose path does not contain "mixin" or "client", `Class.forName`s it without initialising and keeps the enums that implement the interface (`RegistryEntry(values, modId)`, keyed by class). Seven full scans of ~200 jars held mod construction for 36.8 s of the 112.8 s server boot and 21.7 s of the client start, and loading client classes on the server wrote ~16,800 FATAL, DISTXFORM, STDERR and Polymorph lines per boot (`Modpack-Planung/research-raw/fb3-server-log-triage.md`, finding A).

- Rule (`perf/RegistryScanScope`, pure): the seven interfaces are TFC's (`RegistryMetal`, `RegistryRock`, `RegistrySoilVariant`, `RegistryWood`) and the API's own (`RegistryOre`, `RegistrySand`, `RegistrySoil`), so an enum that implements one is compiled against TFC or TFCRegistryAPI. The scan keeps every mod file that holds a mod `tfc` or `tfcregistryapi` or a mod that declares any `[[dependencies.<mod>]]` entry on one of them; a kept file keeps all its mod entries in list order, so each kept file is scanned exactly as before (same order, same mod id per entry). In the CurseForge client install of 0.8.0: 20 of 246 mod jars (jar-in-jar mods counted).
- Hook (`mixin/tfcregistryapi/HelpersMixin`, applied only when TFCRegistryAPI is loaded): `@WrapOperation` on `ModList.getMods()` inside `Helpers.discoverRegistryEnums`; the loop body is untouched. `perf/TfcRegistryScan` reads `ModList` entries (`IModInfo.getOwningFile().getFile().getFilePath()`, `getDependencies()`), logs the scope once at INFO ("TFCRegistryAPI enum scan limited to N of M mod files ...") and also drops mod files that are directories: the API cannot open them (`ProviderNotFoundException` aborts loading), and only dev runs have them (firmages-core's own classes).
- Enums only (0.9.1, same mixin): inside a kept file the API's per-class lambda (`lambda$discoverRegistryEnums$1`) `Class.forName`s every class and keeps it only when `isEnum()`. On the server that loaded client classes of the 17 kept TFC mods (a DISTXFORM error each, mixin warnings, an early failing Azimuth mixin apply on Create's `SafeBlockEntityRenderer`; about 112 lines per boot). A `@WrapOperation` on that `Class.forName` reads the class file through the same class loader (`getResourceAsStream`, which defines nothing) and answers `Object.class` when its access flags lack `ACC_ENUM` (`perf/ClassFileAccess` walks the constant pool by hand: ASM is not visible from the game layer, `ClassNotFoundException` in the first TFC GameTest run); `Object.class` fails `isEnum()` exactly like the class would. An enum, a missing or unreadable class file goes to the original call. Follows the same switch.
- Switch: `firmages-server.toml [perf] limitTfcRegistryApiScan` (default true, §9), read once through `EarlyServerConfig` because the API is constructed before any NeoForge config loads; false gives the original scan (for an A/B dump).
- Parity: `dev/check_registry_parity.py jars <mods dir> [--dump <fa_dump dir>]` reads every jar of an install (with jar-in-jar files), applies the same rule and lists every class that implements one of the interfaces (through other interfaces too), plus the TFC enums the API's own mixins add `RegistryOre`/`RegistrySand`/`RegistrySoil` to; it fails when one lies outside the scope, and with `--dump` compares the TFCAstikorCarts items per part and wood with the wood enum constants. `dev/check_registry_parity.py dump <before> <after>` diffs two `/fa_dump` outputs; `kubejs/server_scripts/debug/dump.js` now writes every built-in registry into `registries.json` and the API's registered enums as `tfcregistryapi_enums`.
- Tests: U `RegistryScanScopeTest` (7: roots, dependents kept in order, any dependency counts including only the API, a file kept whole, order and duplicates, nothing to scan, other roots). G `PerfGameTests.tfcRegistryApiScanScope` (opt-in run `-PgametestTfc`, which now also adds TFCRegistryAPI; passes empty without it): the switch is on by default, the scope keeps `tfc` and `tfcregistryapi` and leaves out `minecraft`, `neoforge`, IE, Mekanism and ProgressiveStages, and the API registered every TFC enum value of all seven interfaces (`Metal`, `Ore`, `Rock`, `SandBlockType`, `SoilBlockType`, `SoilBlockType.Variant`, `Wood`), TFC's woods under mod `tfc`.
- **[PoC]** on the pack server and a client: boot time, the scope log line, no TFCRegistryAPI frames in FATAL/DISTXFORM/STDERR, and `check_registry_parity.py dump` between a boot with the switch off and one with it on (both at age_9, `/fa_dump`).

## 21. Recipe viewer, Ponder and guides by Age (2026-10-14)

Research `Modpack-Planung/research-raw/n2-emi-leaks.md`. ProgressiveStages 3.0.6 and KubeJS only remove locked stacks from EMI's index; EMI 1.1.24 then still shows them inside recipes, as tag members and as workstations, and opens their recipes on a click. Fluids (445 source fluids, none Age-tagged), Mekanism chemicals, Patchouli books, enchanted books and potions of later mods, and whole categories built on the client (Mekanism Boiler, SPS, liquifier; IE world data) were visible from age_1 on. Ponder (`/ponder`, level 0) and GuideME (`/guidemec`) showed every later Age from Dawn on.

### 21.1 The view (`immersion/EmiAgeView`, pure; `client/immersion/ClientAgeView`)

- Unlocked Ages: the player's Age stages from ProgressiveStages `ClientStageCache.getStages()` (paths; without ProgressiveStages every Age up to the `firmages:age_status` Age). Dawn is always unlocked; an empty set counts as Dawn only.
- Items and fluids: the synced tags `firmages:age_items/<age>` and `firmages:age_fluids/<age>` (dawn..age_9, `disabled`; `disabled` wins). An item or fluid in a locked Age or in `disabled` is locked; one in no Age tag takes its namespace's Age.
- Namespaces: `assets/firmages/age_namespaces.json` (`{"namespaces": {"create": "age_1", ...}}`), generated by `dev/gen_stage_locks.py` from `dev/age_map.toml [mods]` into `kubejs/assets/firmages/` and read by a client reload listener. A namespace whose Age is not unlocked is locked; unknown namespaces (`c`, `emi`, `jei`) never are.
- Cached until the stages, the tags (`TagsUpdatedEvent` from the server) or the namespace file change; thread-safe (EMI builds it on its reload thread, Ponder on the render thread).

### 21.2 EMI plugin (`compat/emi/FirmagesEmiPlugin`, `@EmiEntrypoint`, compileOnly EMI from its pack metafile through `fetchLibs`)

- `initialize` (every EMI reload): `EmiInitRegistry.disableStacks(predicate)`. A disabled stack drops every recipe with a fully disabled input, output or catalyst (`EmiRecipes.bake`), every fully disabled workstation, every disabled tag member (`EmiTags.reloadTags`) and leaves the index [verified, javap, EMI 1.1.24]. The predicate: an item that is locked, or whose component patch names a locked mod (`patchouli:book`, `modonomicon:book_id`, `minecraft:stored_enchantments`, `minecraft:enchantments`, `minecraft:potion_contents` potion and custom effects); a fluid that is locked; any other stack type (Mekanism chemicals) of a locked mod. Logs `EMI gate: {age=..., locked items=..., locked fluids=..., locked mods=...}`.
- `register`: `removeRecipes` for every recipe of a category whose namespace is locked (IE world data, Mekanism's client-built categories, JEI-only categories through JEMI), and for EMI Loot recipes (`emi_loot:/<category>/<loot namespace>/<path>`, [verified, javap]) whose loot table belongs to a locked mod.
- ProgressiveStages' own EMI hiding and `viewer_static.js` stay as a second layer.

### 21.3 Recipe screens while the viewer is locked

`EmiApi.displayRecipes/displayUses` and JEI's `IRecipesGui.show` through JEMI do not check `EmiConfig.enabled`. `ImmersionClient.screenOpening` cancels `dev.emi.emi.screen.RecipeScreen` and `BoMScreen` (by class name) while the viewer is closed for the player: `recipeViewer` is false (Dawn and the Stone Age by default, §18.3) and the player is neither an operator nor in creative mode (§18.3).

### 21.4 Audit (a GameTest cannot open EMI)

- After every EMI reload that ran the plugin (`EmiReloadManager.isLoaded()` turns true), `compat/emi/EmiAudit` walks every category, recipe (inputs, outputs, catalysts, tag members), workstation and shown index stack off the render thread and logs `EMI audit <age>: <n> categories [...], index <k> stacks, locked stacks visible 0, recipes with a locked stack 0, locked workstations 0, locked categories 0` at INFO; only a number above 0 logs at WARN, with the first 20 offenders. The index is `EmiStackList.filteredStacks`, the list the index panel draws (without disabled and hidden stacks); `EmiApi.getIndexStacks()` returns the unfiltered list in which every disabled stack still stands [verified, javap, EMI 1.1.24], so until N5 every reload logged WARN (0.9.0 client: 19,298 "visible" locked stacks in Dawn, 3 in age_9, all disabled).
- `/firmages_client emi_audit` (client command, with EMI) writes the full result to `logs/firmages-emi-audit-<age>.json`: categories with recipe count and workstation ids, index counts by namespace and stack type, all offenders.
- **[PoC]** Dev client on the test server, `/stage grant @s age_1` .. `age_9` one by one, `grep "EMI audit" logs/latest.log` after each: 0 offenders at every Age and the category list growing Age by Age; at age_1 no Mekanism workstation under Crafting or Smelting, no `ad_astra:fuel`, no Astrodux, no "Blast Furnace Fuel".

### 21.5 One EMI reload per Age grant

ProgressiveStages reloads EMI at every stage change (`ClientStageCache` -> `ProgressiveStagesEMIPlugin.triggerEmiReload()`), three seconds before the Age reload replaces the recipes, which reloads EMI again (research `n2-perf.md` §2.2: 20 reloads for 10 grants).
- `firmages:age_status` carries `reloadPending` (protocol "4"): `AgeService.reloadPending()` = the scheduler is PENDING or RUNNING or has a follow-up queued. The server broadcasts the status again right after it schedules a reload (the persist step sent it before the request) and after every reload.
- Client side: the mixin `mixin/psemi/EmiReloadHoldMixin` and `client/immersion/EmiReloadGate` (§3.3, "Clients, one EMI reload per Age change") hold back ProgressiveStages' reload while `reloadPending` is true and reload EMI once afterwards. The status reaches the client before ProgressiveStages' stage sync because `sync.coalesce` (default true, §3.5) sends that sync at the end of the tick; with coalescing off the order is not guaranteed and a grant may reload EMI twice as before. Stage changes without an Age change (game mode, mob stages) keep their reload. (The n3/emi round had a second mixin, `mixin/ps/EmiReloadDeferMixin`, for the same purpose; the merge for 0.9.0 kept only the swap-aware one.)
- **[PoC]** client log of an age_0..age_9 run: one `[EMI] Starting Reload` per grant.

### 21.6 Ponder

- Client (`compat/ponder/FirmagesPonderPlugin`, added in client setup when Ponder is loaded): `indexExclusions` excludes every locked item; Ponder 1.0.82 collects the exclusions each time `PonderIndexScreen` is built, so the index follows the Age. `mixin/ponder/PonderTagScreenMixin` removes locked items from a tag screen before its layout (the tag screens ignore exclusions). Compiled against `net.createmod.ponder:ponder-neoforge` (the version Create 6.0.10 bundles).
- Server (`immersion/PonderCommandGate`, `CommandEvent`): `/ponder ...` below permission level 2 is cancelled with "Hold [<Ponder key>] on an item to ponder it." (`firmages.ponder.blocked`, gray). Holding the key on an item tooltip still opens that item's scenes.

### 21.7 GuideME

`client/immersion/ClientCommands` removes the client command `guidemec` (GuideME 21.1.19 registers `/guidemec open <guide> [page]` without a permission check) from the root at `RegisterClientCommandsEvent` priority LOWEST, by reflection on Brigadier's `CommandNode.children` and `literals`. GuideME's G key on a held item keeps working; the server command `guideme` needs level 2.

### 21.8 Server side of the same leaks

- `dev/gen_stage_locks.py` writes `kubejs/data/firmages/tags/fluid/age_fluids/<stage>.json` (every fluid in one tag; `disabled` empty). A fluid's Age: the hand-written rule Age of its bucket item (`fluid_buckets`: fluid -> `Fluid#getBucket`, written by `/fa_dump` into `registries.json` and by `/firmages dump registry`; derived Ages and disabled buckets do not count), else for `<ns>:metal/<metal>` the metal's `[tfc_metals]` or `[materials]` Age, else the later of the `[mods]` Age and the `[materials]` words (as items). `AgeIndex` reads the tags, and `GateRules.fluidLock` drops recipes with a locked fluid output with no Java change. `dev/poc_analyze.py` G-1/G-2 mirror fluid outputs.
- `kubejs/server_scripts/recipes/station_ages.js`: age_2 `create:crushing`, `deploying`, `filling`, `emptying`, `twilightforest:uncrafting`, `drying`; age_3 `create_dragons_plus:freezing`, `ending`, `ars_additions:locate_structure`, `source_spawner`; age_4 the five `createbigcannons:*_deployer` types; age_6 `ae2:matter_cannon`, `storage_cell_disassembly`, `crafting_unit_transform`.

### 21.9 Tests

- U `EmiAgeViewTest` (8): unlocked Ages fail closed and ignore other stages; items by Age tag (an Age tag earlier than the mod wins, `disabled` always locked, untagged by namespace); fluids by tag else namespace; namespaces, categories and EMI Loot tables; the loot namespace parser; the open view; the namespace file parser; the shipped `age_namespaces.json` equals `dev/age_map.toml [mods]`. U `ImmersionTest.reloadPendingIsSetByTheServerOnly`.
- G `CoreGameTests` (`/firmages dump registry` writes `fluid_buckets`), the existing immersion GameTests with the extended `age_status`.
- **[PoC]** §21.4 and §21.5 on a client; as a non-op: `/ponder index` gives the line and opens nothing, `/guidemec open ae2:guide` is an unknown command.

### 21.11 Item lists show the player's Ages (0.9.1, `mixin/emi/ListEmiIngredientMixin`, `compat/emi/ShownMembers`)

EMI 1.1.24 drops disabled members from a tag ingredient (`TagEmiIngredient` reads `EmiTags.getValues`), but an item list (`ListEmiIngredient`, what EMI makes of a vanilla `Ingredient` with several items and no tag) returns its `fullList` from `getEmiStacks` [verified, javap], so a recipe slot cycled through locked stacks (`dev/poc_analyze.py` E-1 on the 0.9.0 dump: 67 recipes, for example `tfc:crafting/blue_dye` with lapis powder in the Bronze Age, vanilla tools in `minecraft:iron_nugget_from_smelting`). The mixin answers `getEmiStacks` with the members the plugin's predicate (§21.2) keeps, computed once per EMI reload (keyed on the view of `FirmagesEmiPlugin.view()`). A list whose members are all locked is returned whole, so `EmiHidden.isDisabled` still sees a fully disabled input and EMI still drops the recipe; a list without locked members is returned as is. `hashCode` keeps EMI's full-list value. Client only (mixin `client` list, package `emi` applied only with EMI). E-1 models it and passes when `firmages.mixins.json` lists the mixin.

### 21.10 JEI's restart on the recipe packet (kept, 0.9.1)

JEI 19.64 restarts on every `RecipesUpdatedEvent` once it runs: `StartEventObserver.onRecipesUpdatedEvent` calls `restart()` (state LISTENING with the stop runnable, then JEI_STARTED with the start runnable, every plugin registered again) when `Internal.hasClientSyncedRecipes()`; JEI 19 has no asynchronous loading [verified, javap]. An Age swap sends one recipe packet, so every Age change costs one JEI restart on the client's render thread (about 1.5 to 2 s in the 0.9.0 night run). It is kept. EMI shows the recipes of 52 pack mods only through JEI (JEMI): they have a JEI plugin and no EMI entrypoint (Create and its addons, Immersive Engineering, Draconic Evolution, Mystical Agriculture, Ad Astra, Immersive Petroleum, Create Big Cannons and others; scan of the instance jars, jar-in-jar included, for `@JeiPlugin` and `@EmiEntrypoint`). EMI 1.1.24's `JemiPlugin.register` waits until JEI's runtime is back ("Waiting for JEI to finish reloading") and then copies JEI's categories and recipes [verified, javap]. Without the restart EMI would copy the previous Age's JEI recipes: after a revoke later recipes would stay visible, after a grant the new ones would be missing. Deferring it to the next screen does not remove the cost: the stall would move to the moment the player opens a container (the only place EMI draws), and EMI's reload after the swap would have to wait for it too. No mixin.

### 21.12 Evolved Mekanism's chemical recipes (N5, `compat/emi/EvolvedMekanismEmi`)

Evolved Mekanism 1.2.1-fix3 shows its Chemixer (`evolvedmekanism:chemixing`: two items and a chemical) and APT (`evolvedmekanism:apt`, Mekanism's `ItemStackChemicalToItemStackRecipe`) recipes only in JEI categories that take Mekanism's chemical ingredient type. Mekanism 10.7.19 registers that type with JEI only without EMI, so JEI throws "Unknown ingredient type: ChemicalStack" for the six recipes (plaslitherite, better gold; Age 8) and JEMI copies nothing [verified, javap, 0.9.0 client log]. `FirmagesEmiPlugin.register` (with Evolved Mekanism and Mekanism loaded) adds the categories `evolvedmekanism:firmages_chemixing` and `evolvedmekanism:firmages_apt` (names: the Chemixer block, "Antimatter Protomolecular Transmutator"), their workstations (Chemixer; APT casing, port, supercharging element) and one recipe per recipe-manager entry: item inputs and Mekanism's `ChemicalEmiStack` for the chemical, an arrow, the output; the APT chemical slot says it is used per tick. The Chemixer recipe is read through its public getters by reflection (no Evolved Mekanism jar at compile time). The ids are in Evolved Mekanism's namespace, so the Age view locks the categories with the mod. JEI's own ERROR line for the six recipes stays (JEI still builds its categories); `dev/data/client_log_noise.json` lists it as noise.

## 22. M8: Proof of work, the silo launch and the pack's Ponder scenes (N7, 2026-10-15)

Design source: `Modpack-Planung/research-raw/n7-pacing.md` §7.3 (Tally Chute) and `n7-build.md` §2.3/§2.4 (silo, Ponder). Package `proof` (`ProofRegistry`, `TallyChuteBlock`, `TallyChuteBlockEntity`, `TallyCounts`, `TallyData`, `TallyService`, `TallyTrigger`, `SiloLaunch`, `SiloLaunchTrigger`); compat `compat/adastra/AdAstraRockets`, `compat/create/CreateHatches`, `compat/ponder/FirmagesScenes` and `QuestPonderLink`.

### 22.1 The Tally Chute (`firmages:tally_chute`)

- **Naming contract:** block, item and block entity `firmages:tally_chute`; criterion `firmages:tally`; SavedData `firmages_tally` (overworld storage). Age map `age_1` (`dev/age_map.toml`); the recipe is pack data (Bronze Age, KubeJS).
- **Behaviour:** a full block with an `IItemHandler` on the top and the four sides (`Capabilities.ItemHandler.BLOCK`; the bottom offers nothing, so nothing can be pulled out). An insert goes straight on into the inventory below (its top face, through a `BlockCapabilityCache`); what the outlet refuses is refused, so a hopper, funnel, chute or pipe keeps its items and the line backs up. The chute holds nothing, has no GUI and takes nothing from a hand. Simulated inserts are passed on simulated and count nothing.
- **Counting:** each accepted insert adds the moved count to the chute's own `passed` and, when the chute has an owner and does not feed another Tally Chute (a stack of chutes counts once, at the lowest), to the owner's team in `firmages_tally`. Owner = the placer (`setPlacedBy`). The team is resolved at each count (`TallyService.teamOf`: the owner's FTB team, the owner's own id without FTB Teams), so a player who joins a party later counts for the party. The count is split by the world's current Age (`AgeStatus.current`), so a criterion can ask only for what passed since an Age dawned. Layout: `teams.<team uuid>.<age id>.<item id> = long`.
- **Jade (`FirmagesJadePlugin.Tally`):** "Counted: 27 Wrought Iron Ingot" (the team's total of the last item, from the synced block entity, updated at most every 10 ticks while items pass), "Through this chute: N", or "Counts nothing: no hand placed it" for a chute without an owner (placed by a machine or `/setblock`). Jade's Age gate (§18.3) applies as everywhere.
- **Look:** lumber frame with bronze bands and tally marks, a dark opening on top (`dev/gen_textures.py`, `tally_chute_*`); axe-mineable; drops itself.
- **Not built:** comparator output, a shrine rite type `tally` (n7-pacing question 5).

### 22.2 Criterion `firmages:tally`

```json
{"trigger": "firmages:tally", "conditions": {"item": "tfc:metal/ingot/wrought_iron", "count": 32}}
{"trigger": "firmages:tally", "conditions": {"tag": "c:dusts", "count": 128, "age": "age_4"}}
```

- Exactly one of `item` (an item id) and `tag` (an item tag id, without `#`); `count` (positive int) is the minimum; `age` (optional, `dawn` or `age_0`..`age_9`) counts only what passed in that Age or later; `player` (optional entity predicate) as in every vanilla criterion. A file with both or neither of `item` and `tag`, or with an unknown Age, fails to load (codec error in the log).
- The tag is matched against the counted item ids at test time, so a tag edited later also counts the items that already passed.
- Fired by `TallyService` once a second for every online member of each team whose tally changed, and at login for the joining player, so a member who was away gets it too. FTB Quests' advancement task then completes the quest for the team.
- The pack's proof advancements are hidden data (`kubejs/data/firmages/advancement/proof/<key>.json`, no display), written by the quest generator.

### 22.3 Tests (Tally)

G `ProofGameTests.tallyHopperLine` (hopper -> chute -> chest with 10 cobblestone; hopper -> two chutes -> chest with 5 dirt: everything arrives, the team counts each item once, both chutes know their own `passed`; a chute over a full chest takes nothing and the hopper keeps its 3; the item, tag and Age test advancements in `src/gametest/resources/data/firmages/advancement/test/`), `tallySurvivesSave` (save and load keep team, Age and item), `tallyCountsForTheParty` (with FTB Teams: a chute owned by A counts for A's party; A and B both earn the criterion).

### 22.4 Criterion `firmages:silo_launch`

`{"trigger": "firmages:silo_launch"}` (only the optional `player` predicate). The mod ships `data/firmages/advancement/silo_launch.json` (hidden, no display) for the quest task `t_adv('firmages:silo_launch')`.

- **Hook (`SiloLaunch`):** every Ad Astra rocket that joins a level unlaunched is watched; every 5 ticks the server checks each one. While it stands on `ad_astra:launch_pad` (its own block or the one below) the pad is remembered and, if a moving Create contraption of at least **100 blocks** touches the box 16 blocks around the shaft from the pad up to 24 blocks above it, the time is noted. When `hasLaunched()` turns true the launch is judged once:
  - crew: every `ServerPlayer` among the rocket's passengers (none: `NO_CREW`);
  - depth: in each of the four directions some column 2 to 12 blocks from the pad is solid (`isSolidRender`, not air) from `pad.y + 1` to `pad.y + 8` (else `SHALLOW`);
  - hatch: a 100-block contraption was seen there within the last 60 s or is there now (gantry and piston hatches disassemble once open, so "seen moving while the rocket waited" is the test; else `NO_HATCH`).

  `SILO` grants the criterion to the crew. Every judged launch is logged (pad, dimension, crew size, result).
- **Mods:** Ad Astra is no compile dependency: `AdAstraRockets` finds `earth.terrarium.adastra.common.entities.vehicles.Rocket` by class name and calls the public `hasLaunched()` through a method handle (Ad Astra 1.16.26 [verified, javap]); without Ad Astra nothing is watched. Create's `AbstractContraptionEntity#getContraption().getBlocks()` counts the blocks (Create 6.0.10, compileOnly); without Create no hatch is ever seen.
- **Test:** G `ProofGameTests.siloLaunchWithStandIns` (neither mod is in the GameTest run): `SiloLaunch.useStandIns` swaps in a boat tagged as the rocket, a lodestone as the pad and a hatch probe reporting 150 blocks; a pit 8 deep around the pad; `judge` says `SILO`, `NO_CREW`, `NO_HATCH` and, with one wall 7 high, `SHALLOW`; the crew rides, the hatch is seen and then gone, the launch tag flips, and the rider earns `firmages:silo_launch`. Real rockets and hatches: M (client or pack server).

### 22.5 Ponder scenes and the quest link

- **Storyboards** (`FirmagesPonderPlugin.registerScenes`, only with Create; texts `firmages.ponder.<scene>.header` and `.text_N` in `en_us.json`): "Silo Hatch" on `ad_astra:launch_pad` (only with Ad Astra: shaft, pad and rocket, two glued leaves sliding apart on gantries, the rocket rising, the hatch closing) and "Portcullis" on `create:rope_pulley` (a wall with a gate and a slot, an iron grate under a rope pulley dropping and rising through a gearshift with a lever). Schematics `assets/firmages/ponder/silo_hatch.nbt` (14 x 9 x 14) and `portcullis.nbt` (9 x 8 x 9), written by `dev/gen_ponder_schematics.py` (`--check` compares the content); the scene coordinates in `FirmagesScenes` follow its plans. The same script writes the empty GameTest template `silo_area.nbt`.
- **Quest picture to Ponder and back (`QuestPonderLink`, client, with Ponder, FTB Library and FTB Quests):** a picture with `click_action: custom_event:firmages:ponder/<namespace>/<path>` (FTB Library's `CustomClickEvent`, posted on the client without a server round trip) opens the Ponder scenes of item `<namespace>:<path>` directly. `run_command:/ponder` is no option for players, because §21.6 blocks `/ponder` below permission 2. An item of a locked Age (`ClientAgeView.itemLocked`) or one without scenes opens nothing. When the last Ponder screen closes back to the game, the Chronicle opens again (`FtbQuestsScreens.open`). Example click action: `custom_event:firmages:ponder/create/rope_pulley` opens "Portcullis".
- **Tests:** M only (client screens): the scenes play, the picture opens the scene, Escape returns to the Chronicle, a locked item opens nothing.

## 23. Per-Age music (N7, 2026-10-15; `client/AgeMusic`)

- **Selection:** a `SelectMusicEvent` listener on the client. Where the game would play its generic music (`minecraft:music.game`, `music.creative`, `music.under_water` and every `music.overworld.*` biome track, in any dimension), it plays `firmages:music.age_N` of the world's Age (`ClientAgeStatus`; Dawn plays `age_0`) with vanilla's game-music delays (10 to 20 minutes, no cut-in). Nether, End, menu, credits, boss and other mods' music stay as they are.
- **Tracks (`assets/firmages/sounds.json`, streamed):** for a start TFC's five bundled tracks, which TFC ships but never plays (`tfc:music/m0-dreams_of_the_phae`, `m1-firmavista`, `m2-sycamore_heights`, `m3-terrafirmacraft`, `m4-through_the_willows`; TerraFirmaCraft 4.2.11 jar [verified]), two per Age, all five in age_9. New tracks go into the same events (a resource pack or KubeJS assets can override the file).
- **Firmament nights:** `firmages:music.firmament_night` is the night's track. Enhanced Celestials 2 (core 2.0.3.3 [verified, javap]) plays a lunar event's modifier `enhancedcelestials2core:sound_track` (`SoundTrackModifier`, field `sound_track`, a `SoundEvent` in its direct codec: `{"sound_id": "..."}`) as a looping ambient sound (`LunarSoundHandler`, `BiomeAmbientSoundsHandler.LoopSoundInstance`). While that loop of `firmages:music.firmament_night` plays (seen through `PlaySoundEvent`), the background music is silenced (`overrideMusic(null)`), so the two never overlap; the Age music returns after the night. The pack adds the modifier to its ten `firmament_age_N` lunar events.
- **Ceremony:** with `effects.stopMusic` the music is silenced while the Age ceremony runs (since N7 really silenced with `overrideMusic(null)`; the old `setCanceled(true)` only kept the current choice).
- **Switch:** `music.ageMusic` in `firmages-client.toml` (default true); false leaves vanilla's selection (the night silence and the ceremony silence stay).
- **Tests:** M only (client sound).

## 24. Sky materials and the one-source rule

Design of the eight sky features (merged spec of 2026-10-15, built from the four drafts and two reviews in the
sky workflow). Felix's content lock (2026-10-15): the eight features stand and nothing else is added; **V4A** (from the
Arcane Age one celestial alloy per Age enters that Age's keystone or signature recipe, so the Lens Crown is mandatory);
**V5A** (no fast travel at Wayshrines; the first teleport of the pack is the Mekanism teleporter in age_7). Felix's
answers: **S1A** the names "Wayshrine Stone" and "Shard of <blessing>"; **S2A** the alloy names and lens colours of
§27; **S3A** the aurora on every clear night in the cold band, twice as often in its cold half of the year (§26.3);
**S4A** `shrine.blessings.everywhere` is removed and the Sky Anchors replace it, with a WARN line for old configs
(§29.7, built in M16); **S5A** static isles (§30); **S6B** the Wind Shrine replaces the Glacier Cairn as an isle shrine
on the Drifting Isles from the Industrial Age (§28.4a). Rejected ideas: the Skyroot, Orrery, Heralds, Tally Gauge,
Leylines and the Firmament dimension; nothing here depends on them. "Firmament Night" is the existing lunar event.
Sections 24 to 33 follow the build order; their config, command and network rows are also listed in §9, §10 and §11.

### 24.1 Purpose

Eight features turn the sky into a second material road beside the ores. Each sky material has exactly **one** source. The Lens Crown refines the materials into seven celestial alloys, and from the Arcane Age each Age's keystone or signature recipe takes its alloy (V4A). Wayshrines, Sky Anchors, the Launch Complex and the Origin Archipelago consume alloys. Nothing in the sky makes FE, nothing generates ore, and magic stays optional.

### 24.2 The one-source table

| Material | Id | The one source | From | Consumers |
|---|---|---|---|---|
| Meteoric iron | `firmages:meteoric_iron_{chunk,ingot,double_ingot,sheet,rod}` | Starfall meteorites and cores (§25) | age_2 | Storm Rod, Aurora Net, Lens Mount, Star Anvil, Starlit Iron, Cloudsteel, Wayshrine offering of the Iron Age |
| Starsteel | `firmages:starsteel_{chunk,ingot}` | Starfall from the Electric Age (§25) | age_5 | Aurora Steel, Halo Steel, Void Steel, Information Lens, Firmament Alloy |
| Storm glass | `firmages:storm_glass` (block) | Storm Rod in rain and thunder (§26) | age_2 | Star Anvil, crown caps, lens blanks, Aurora Net, Starlit Iron, Aurora Steel |
| Aurora light | `firmages:aurora_light` (fluid) | Aurora Net under the aurora (§26) | age_4 | Lens Crown fluid ingredient from recipe 4 on (§27.6) |
| Firmament crystal | `firmages:firmament_crystal` | Drifting Isles: clusters and the re-forming lode (§30) | age_4 | Cloudsteel, Industrial Lens, Electric Lens, Halo Steel, Firmament Alloy |
| Voidglass | `firmages:voidglass` | Launch Complex probes (§31) | age_7 | Void Steel, Space Lens, Quasar Alloy, Quantum Lens, Firmament Alloy, Sky Anchor |
| Deep-space sample | `firmages:deep_space_sample` | Launch Complex deep-space flights (§31) | age_8 | Quasar Alloy, the Archipelago's Space crown |
| Celestial alloys (7) | `firmages:<alloy>_ingot`, tags `#firmages:celestial_alloy/age_3`..`age_9` | Lens Crown only (`firmages:starforge` recipes, §27) | age_3..age_9 | V4A recipes (§27.7), Wayshrine offerings, sampler and deep-space modules, Sky Anchor, blessing shards, Archipelago crowns |

Building blocks with no material role: `firmages:sky_rock` (crater shell, Dawn) and `firmages:skystone` (isle body, Dawn).

**Total demand for the whole pack (estimate, main road plus three to five anchors):** about 70 meteoric iron, 5 to 8 starsteel, about 25 storm glass, about 2.5 buckets of aurora light, 6 to 10 firmament crystal, about 14 voidglass (about four sampler flights), 2 to 4 deep-space samples.

### 24.3 Rules that bind every sky feature

1. **One source.** No recipe, loot table, trade or machine outside the source of §24.2 outputs a sky material. Alloys come only from `firmages:starforge` recipes.
2. **No ore duplication.** No sky item is in `#c:ores` or `#c:raw_materials`, so Mekanism and IE ore multiplication never apply.
3. **No FE.** No block of §24 to §31 has an energy capability that outputs, and none needs FE except the Archipelago's work samples (which only hold it).
4. **No hidden ingredients.** No sky recipe uses an item from `dev/age_map.toml [hidden]` (for example `ad_astra:steel_plate`, line 958). Sky recipes name `c:` tags where a tag exists.
5. **Player text.** It is English and follows `dev/style-guide.md`: quantities up to twelve are words ("three blocks"), 13 and up are digits; there are no mod names, no "!" and no meta words; "relic" stays reserved for the nine shrine relics. Jade lines with a count use the shared helper `text/Numbers.words(n)`.
6. **Worldgen data lives in the mod jar** (`src/main/resources/data/firmages/worldgen/...`) for all three structure features (old craters are code, Wayshrines and Drifting Isles are structures). The pack can override any of it from `kubejs/data/firmages/`.
7. **Caelum speaks only at the shrine.** Sky events carry no Caelum line.

### 24.4 Shared plumbing (package `sky/`, built in M10)

- **`sky/DeferredSites`:** a chunk-to-site index (`Long2ObjectMap<ChunkPos -> site ids>`). `ChunkEvent.Load` only looks the chunk up and, when it belongs to a pending site, enqueues the site id (O(1)). The NeoForge 21.1.252 javadoc warns that the event "may be called before the underlying LevelChunk is promoted to FULL" and that level interactions there deadlock [verified]. A `ServerTickEvent.Post` step then checks each queued site: every chunk of its box must be `level.hasChunk` at FULL status. Only then does it run the site's work (crater base check and carve, isle fallback placement). The step has a budget per tick (§25.3). Old craters and isle fallback sites register through the same queue, never inside the event.
- **`sky/ClimateSource`:** an interface with `averageTemp(x, y, z)` and `instantTemp(x, y, z)`. `compat/tfc/TfcClimateSource` reads TFC's `Climate` (and works on the client for loaded chunks through TFC's `UpdateClimateModelPacket` and `ChunkWatchPacket` [verified]). The GameTest run has no TFC, so it uses `TestClimateSource` (values set by the test). Common code never references `net.dries007` classes (SPEC rule: compat only).
- **`sky/SkySavedData`** (overworld storage `firmages_sky`) holds the Starfall sites and the aurora state (§25.8, §26.6).

### 24.5 Age map hand-off (`dev/age_map.toml`)

| Section | Entries |
|---|---|
| `[items]` dawn | `sky_rock`, `skystone` |
| `[items]` age_2 | `meteorite`, `starfall_core`, `meteoric_iron_*`, `meteoric_iron_block`, `storm_rod`, `storm_glass` |
| `[items]` age_3 | `star_anvil`, `lens_mount`, `lens_blank`, `lens/stone`, `lens/bronze`, `lens/iron`, `starlit_iron_ingot` |
| `[items]` age_4 | `aurora_net`, `aurora_light_bucket`, `firmament_crystal`, `lens/arcane`, `cloudsteel_ingot` |
| `[items]` age_5 | `starsteel_*`, `starsteel_meteorite`, `starsteel_block`, `lens/industrial`, `aurora_steel_ingot` |
| `[items]` age_6 | `lens/electric`, `halo_steel_ingot` |
| `[items]` age_7 | `lens/information`, `void_steel_ingot`, `launch_control`, `probe_cradle`, `launch_port`, `probe`, `sampler_module`, `voidglass`, `sky_anchor`, `blessing_shard` |
| `[items]` age_8 | `lens/space`, `quasar_alloy_ingot`, `deep_space_module`, `deep_space_sample` |
| `[items]` age_9 | `lens/quantum`, `firmament_alloy_ingot`, `ark_plinth` |
| `[fluids]` age_4 | `aurora_light` |
| `[ore_blocks]` (format extension) | `"firmages:meteorite" = { stage = "age_2", disguise = "firmages:sky_rock" }`, the same for `firmages:starfall_core` (age_2) and `firmages:starsteel_meteorite` (age_5). `dev/gen_stage_locks.py` learns the key `disguise` (a block id) as the alternative to `rock` (a TFC host rock) |
| `firmages:age_blocks/age_4` | `firmament_crystal_cluster`, `firmament_lode` |

`wayshrine_core` gets no item. `wayshrine_stone` stays Dawn (it drops from the first lit Wayshrine in age_1 on). The ark plinth is never crafted.

### 24.6 Consolidated deltas for §9, §10 and §11

- **Network (§11 heading).** Version "5" (M10) adds `firmages:starfall_streak` (M11 added no `firmages:sky_sync` after all, §26.7). Version "6" (0.12.0) adds `firmages:launch_fx` (M14) and `firmages:ark_sync` (M17), together with the grid lock's `gridLock` field (§19.3); the three branches merged into one release, so one bump covers them. The Lens Crown, the Wayshrines and the Sky Anchors add no payload. There is still no C2S payload. A worktree that adds a payload bumps the version only at merge, in this order.
- **Config (§9).** The keys of §25.10, §26.8, §27.12, §28.9, §29.7, §30.9 and §31.9.
- **Commands (§10).** The command tables of the same sections.

### 24.7 Shared acceptance criteria and tests

1. `dev/validate_recipes` (hand-off, extended) runs over all sky materials of §24.2 and all seven alloys. It finds no recipe, loot table or trade outside the one source, and no alloy output outside `firmages:starforge`.
2. No sky item is in `#c:ores` or `#c:raw_materials` (P `selftest sky`, item tags after every Age swap).
3. No ingredient of any `firmages:*` sky recipe resolves only to items in `[hidden]` (P `selftest sky`, against `dev/data/hidden.json` written by `gen_stage_locks.py`).
4. After `ages simulate grant` up to age_9 the chain of §27.7 is complete: every V4A recipe holds its alloy and every alloy has exactly one live recipe (P `selftest crown`).

## 25. Starfall

### 25.1 Purpose

The only source of meteoric iron (from the Iron Age) and starsteel (from the Electric Age). A falling star is a short event that sends the fellowship out; the metal stays in the crater for good.

### 25.2 Player loop

From the Iron Age a star falls on some clear nights. A streak crosses the sky toward its real bearing, a boom follows, and a smoking crater with a glowing core waits 300 to 2,500 blocks away. While the core glows (two TFC days) every meteorite of that crater drops double; after that it drops normally, forever. Missing a fall costs the bonus, never the metal. Old cold craters lie in the world from generation; their meteorites look like sky rock until the Iron Age. There is **no forecast** (no item, no screen, no player command): the streak, the boom and the smoke column are the signal.

### 25.3 Scheduling, site choice and carving (`sky/StarfallService`, `CraterPlan`, `CraterCarver`; overworld only)

**Scheduling.**
- At TFC dusk (`dayTime % 24000` crosses 13000) the service rolls once for the night. It rolls only if all of these hold: `starfall.enabled`; the current Age has an entry in `starfall.json`; the overworld is not raining (vanilla flag); at least one player is online; and fewer than `starfall.maxPending` sites are uncarved.
- **Pity:** after `pity_nights` clear nights without a fall, the next clear night falls for sure.
- **First fall:** the first clear night after an Age whose `metal` differs from the last fall's metal falls for sure (age_2 meteoric iron, age_5 starsteel). The KubeJS call `FirmAges.starfallTonight()` sets the same guarantee, and `kubejs/server_scripts/firmages/firmament_night.js` calls it on a Firmament Night (no Enhanced Celestials code dependency).
- The impact time is random in 14000..22000 of that night. 120 ticks earlier `firmages:starfall_streak` goes to every overworld player, and the actionbar says "A star falls to the north-east." (eight bearings, lang `firmages.starfall.bearing.<dir>`).

**Site choice** (at roll time; no chunk is loaded or generated).
- The anchor is the shrine heart if recorded, else a random online player. The service makes up to 16 attempts, each at a random bearing and a distance in the Age's `distance` range.
- An attempt is rejected when any of these holds:
  - the noise biome (`getNoiseBiome`, no generation; works under TFC's `RegionBiomeSource` [verified]) is in `#firmages:starfall/no_impact` (oceans, rivers and lakes, with TFC biome ids as optional entries);
  - the column lies within `starfall.shrineClearance` (256) of the heart, or within the sanctuary radius plus 64, whichever is larger;
  - the column lies within `starfall.spawnClearance` (128) of a player's respawn point;
  - the column lies within `starfall.claimMarginChunks` (2) of an FTB Chunks claim (`compat/ftbchunks/Claims`, `FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level, pos))` [verified]; FTB Chunks is a new `compileOnly` dependency in §1.4 and `build.gradle`, and the class loads only with the mod present);
  - the column lies inside the box of a known `firmages:drifting_isle` structure start or an isle fallback record (§30).
- With no site after 16 attempts nothing falls tonight, and the pity count goes on.

**Lazy carving.** The site is stored PENDING in `SkySavedData` and registered with `DeferredSites` (§24.4). It is carved once every chunk of its box (radius + 3) is loaded at FULL status: at impact if they are loaded, otherwise when the tick step finds them loaded. Nothing is force-loaded and nothing is generated.

**Base check at carve time** (the chunks are loaded now). The site is moved once, by up to 128 blocks, and then re-checked, if any chunk of the box meets one of these:
- `getInhabitedTime() > starfall.inhabitedTicks` (12,000, ten minutes of players nearby);
- it holds a block entity whose block is not in `#firmages:starfall/natural_block_entities` (TFC tick-counter blocks, bushes, saplings, beehives, structure chests);
- it holds a block of `#firmages:starfall/player_blocks` (planks, bricks, glass, rails, and slabs and stairs of worked stone; one scan of the box at carve time);
- the surface block (`WORLD_SURFACE`) of any bowl column is in `#firmages:isle/body` (skystone, crystal cluster, lode), which means the column lies under a Drifting Isle;
- fluid covers more than 20 % of the bowl.

Three failed moves give the state BURNED: no crater, no chat line, and the next clear night falls for sure.

**Plan** (`CraterPlan`, pure, deterministic from the site seed).
- The bowl has the size's `radius` and `depth`; depth falls off with the square of the distance, with ±1 noise.
- Everything inside the bowl and up to 12 blocks above it becomes air (trees included).
- A one-block lining of `sky_rock` covers the bowl surface and extends one block outward over the lip.
- The meteorite cluster (an ellipsoid of `meteorites` blocks) sits at the bowl floor around the core.
- An ejecta rim of `sky_rock` runs from radius to radius + 2, one block high with chance 0.4.
- `sky_rock` is in no TFC collapse or landslide tag, while every `tfc:rock/cobble/*` is in `tfc:can_landslide` [verified, TFC 4.2.11 tags]. So the rim and the extended lining leave no exposed landslide block at the bowl.

**Carving over ticks.** At most `starfall.carveBlocksPerTick` (64) `setBlock` calls run per server tick across all sites, with flags `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` (no neighbour updates), core last. The cursor is saved, and an unload mid-carve resumes when the tick step finds the box loaded again. A large crater (about 1,100 changes) takes 18 ticks.

**TFC collapse safety.** No Starfall block is in `#c:ores` or `#c:stones`. `tfc:can_trigger_collapse` is `#c:ores` plus `#c:stones` [verified], so no Starfall block can trigger a collapse. Whether a real crater starts a landslide in a TFC world is still **[PoC]**.

**Impact effects.**
- Players within 256 blocks see the last 3 s of the body (drawn by `SkyStreaks`, no entity), `EXPLOSION_EMITTER` particles, and hear `firmages:starfall.impact`.
- Every overworld player hears `firmages:starfall.distant` from the bearing (volume 1 at 256 blocks, 0.25 beyond 1,500).
- There is no fire, no damage and no block-breaking explosion.

**Old craters.**
- There is one candidate per `starfall.oldCraterRegion` (1024 by 1024) region, kept with chance `old_craters.chance` (0.35) from a hash of the world seed and the region.
- When its centre chunk loads with `isNewChunk()`, the event only enqueues it; the tick step registers and carves it like a live site, with the same checks.
- An old crater is CARVED with a cold core, its metal is always meteoric iron, and its size is `old`. Only new chunks get old craters; on an existing world the live falls cover the need.

### 25.4 Blocks, drops and the glowing core

| Block | Properties | Tool (TFC tier mapping [verified]) | Drop (cold / hot) |
|---|---|---|---|
| `sky_rock` | dark fused stone, building block, Dawn | `mineable/pickaxe` only | itself |
| `meteorite` | meteoric iron mass | `minecraft:needs_diamond_tool` (wrought iron and up; bronze fails) | 1 / 2 `meteoric_iron_chunk` |
| `starsteel_meteorite` | starsteel mass (Electric Age falls) | in `minecraft:incorrect_for_diamond_tool` (steel and up; wrought iron fails) | 1 / 2 `starsteel_chunk` |
| `starfall_core` | `metal` (meteoric_iron, starsteel), `hot` (bool), light 15 when hot and 0 when cold, BE stores `coolsAt` (game time) | as its metal's meteorite | 3 / 6 chunks of its metal |
| `meteoric_iron_block`, `starsteel_block` | storage, nine ingots | pickaxe | itself |

- TFC 4.2.11 ships no `needs_*_tool` tags of its own. Its tiers map onto vanilla's `incorrect_for_*` tags: stone `wooden`, copper `stone`, bronzes `iron`, wrought iron `diamond`, steel and up `netherite` [verified, javap `TFCTiers`].
- `hot` turns false when the game time passes `coolsAt` = impact time + `starfall.coolTicks` (48,000, two TFC days). A crater carved after that is cold from the start. The core BE checks every 100 ticks. The client emits `CAMPFIRE_SIGNAL_SMOKE` above a hot core (`animateTick`).
- **Bonus:** the loot condition `firmages:starfall_hot` looks up the site of the broken block's chunk in the `SkySavedData` chunk index. It is true when the block lies within 12 blocks of that site's core position and the game time is before `coolsAt`. There is no world scan, so drills and excavators pay one map lookup per block.
- Jade: "Glowing: cools in 1 day 6 h" or "Cold".

### 25.5 Sky metals and TFC

- **Meteoric iron is forged, never cast.** There is no TFC metal file and no fluid. The chunk, ingot, double ingot, sheet and rod carry `tfc:item_heat` (data in `kubejs/data/firmages/tfc/item_heat/meteoric_iron/`, with wrought iron's values: forging 921 °C, welding 1228 °C). TFC anvil recipes run at tier 3 (wrought iron anvil): chunk to ingot, two ingots welded to a double ingot, double ingot to sheet, and ingot to rod.
- **Machine route:** the IE metal press makes sheets and rods from the Industrial Age (`station_ages.js`: `metal_press` age_4). The IE arc furnace turns chunks into ingots from the Electric Age (`arc_furnace` age_5 [verified]). In age_2 to age_4 the TFC anvil is the only route.
- **No TFC tool heads.** A meteoric pick would either repeat wrought iron or leapfrog steel, and sky iron is never a second steel (craft.md rule 3).
- **Starsteel** is worked in machines only: IE arc furnace, `starsteel_chunk` + `#c:dusts/coal_coke` gives `starsteel_ingot` and slag. There is no plate (it had no consumer).
- Tags: `c:ingots/meteoric_iron`, `c:sheets/meteoric_iron`, `c:rods/meteoric_iron`, `c:ingots/starsteel`, `c:storage_blocks/meteoric_iron`, `c:storage_blocks/starsteel`.

### 25.6 Automation

- **IE excavator (from `starfall.automationAge`, age_6):** every carved site records a vein. Once the world holds that Age, the service calls `ExcavatorHandler.addVein(Level.OVERWORLD, new MineralVein(new ColumnPos(x, z), mix, radius))` for every recorded site, and at carve time afterwards [verified, javap IE 12.4.2]. It then calls `IESaveData.markInstanceDirty()`, because `addVein` does not mark IE's save data dirty itself [verified]. At `ServerStartedEvent` the service re-checks every recorded site against `getMineralVeinList()` and re-adds any missing vein (idempotent). The vein radius is the crater radius + 8. It depletes like every IE vein, and m3's `ExcavatorFilter` applies to it.
- The mixes are `immersiveengineering:mineral_mix` recipes with `weight` 0, so worldgen never rolls them (weight is a plain int, 0 allowed [verified]). `mineral_mix` is already in `gate.exemptRecipeTypes`.

```json
{"type": "immersiveengineering:mineral_mix", "weight": 0, "fail_chance": 0.1, "biome_predicates": [],
 "ores": [{"chance": 0.85, "output": {"id": "firmages:meteoric_iron_chunk"}}, {"chance": 0.15, "output": {"id": "firmages:sky_rock"}}],
 "spoils": [{"chance": 1.0, "output": {"id": "firmages:sky_rock"}}]}
```

  `firmages:mineral/meteoric` (above) serves meteoric falls and old craters. `firmages:mineral/starfall` (meteoric iron chunk 0.6, starsteel chunk 0.3, sky rock 0.1) serves Electric Age falls and later.
- **Create drills** mine craters from the Iron Age (players' own build). The **Mekanism Digital Miner** is `age_7` (`dev/age_map.toml`). OreGuard (§5) and the disguise of §24.5 apply as for any ore.

### 25.7 Data: `data/firmages/firmages_sky/starfall.json`

```json
{
  "pity_nights": 8,
  "ages": {
    "age_2": {"chance": 0.12, "distance": [300, 800],   "size": "small",  "metal": "meteoric_iron"},
    "age_3": {"chance": 0.12, "distance": [400, 1000],  "size": "small",  "metal": "meteoric_iron"},
    "age_4": {"chance": 0.15, "distance": [600, 1600],  "size": "medium", "metal": "meteoric_iron"},
    "age_5": {"chance": 0.18, "distance": [800, 2000],  "size": "medium", "metal": "starsteel"},
    "age_6": {"chance": 0.18, "distance": [800, 2000],  "size": "medium", "metal": "starsteel"},
    "age_7": {"chance": 0.20, "distance": [1000, 2500], "size": "large",  "metal": "starsteel"}
  },
  "sizes": {"old": {"radius": 4, "depth": 2, "meteorites": [6, 10]}, "small": {"radius": 5, "depth": 3, "meteorites": [10, 14]},
            "medium": {"radius": 7, "depth": 4, "meteorites": [16, 22]}, "large": {"radius": 9, "depth": 5, "meteorites": [24, 32]}},
  "metals": {"meteoric_iron": {"mass": {"firmages:meteorite": 1.0}, "mix": "firmages:mineral/meteoric"},
             "starsteel": {"mass": {"firmages:meteorite": 0.6, "firmages:starsteel_meteorite": 0.4}, "mix": "firmages:mineral/starfall"}},
  "old_craters": {"chance": 0.35}
}
```

- An Age without an entry inherits the latest earlier entry (age_8 and age_9 use age_7). Dawn to age_1 have no live falls.
- Unknown metals, sizes or Ages reject the file with an ERROR (as `ShrineDataLoader` does).
- Expected yield: a cold small crater gives about 15 chunks and a hot one about 30. There is about one fall per two to three real hours of play.

### 25.8 SavedData

`firmages_sky` (overworld) holds:
- per site: id, pos, metal, size, origin (live or old), impact time, `coolsAt`, core pos, state (PENDING, CARVING, CARVED, BURNED), carve cursor, moves, vein added;
- `nightsSinceFall`, `lastMetal`, `guaranteedTonight`;
- the chunk-to-site index (rebuilt on load).

### 25.9 Rendering and sound

- `client/SkyStreaks` draws the streak and the falling body as quads in `RenderLevelStageEvent.AFTER_SKY` (the `SkyEffects` pattern). Client toggle: `effects.skyStreaks`.
- Sounds `firmages:starfall.impact` and `firmages:starfall.distant` map onto vanilla events in `sounds.json` (as §8.1).

### 25.10 Network, config, commands

- **Network:** `firmages:starfall_streak` (bearing start, target pos, impact game time, metal, near flag), sent to every overworld player. Protocol "5" (§24.6).
- **Server config:** `starfall.enabled` (true), `starfall.carveBlocksPerTick` (64), `starfall.coolTicks` (48000), `starfall.maxPending` (6), `starfall.shrineClearance` (256), `starfall.spawnClearance` (128), `starfall.claimMarginChunks` (2), `starfall.inhabitedTicks` (12000), `starfall.oldCraters` (true), `starfall.oldCraterRegion` (1024), `starfall.automationAge` (`age_6`). Balance values are data (§25.7).
- **Client config:** `effects.skyStreaks` (true).

| Command | Purpose |
|---|---|
| `starfall now [pos] [metal] [size]` | A fall in 120 ticks with streak; without pos, the normal site search |
| `starfall list` | Id, state, metal, distance, hot time left, vein |
| `starfall carve <id>` | Adds a temporary ticket and carves now |
| `starfall cool <id>` / `starfall heat <id>` | Debug for the bonus |
| `starfall old <pos>` | An old crater there |
| `starfall tonight` | The same as the KubeJS call |
| `starfall veins` | Reconcile IE veins now and list them |

### 25.11 Tests

- **U** `StarfallRulesTest`: Age table with inheritance, pity, first-fall guarantee, site rejection rules with a fake world (isle box included), deterministic old-crater regions, the shipped `starfall.json` parses, bad files are rejected.
- **U** `CraterPlanTest`: the same seed gives the same plan; block counts lie within the size; the core comes last; the lining covers every bowl column and the lip ring; the rim is only `sky_rock`; nothing lies outside radius + 3.
- **G** `SkyGameTests`, with vanilla stand-in terrain:
  - `forcedImpactCarves`: the carve finishes in the expected ticks, never more than 64 changes per tick, hot core, meteorite count in range.
  - `lazyCarveOnLoad`: PENDING while unloaded; after a forceload the tick step carves (never from inside the load event).
  - `exclusionsMoveTheSite`: near the shrine heart, a chest, a plank road, an inhabited chunk and a skystone surface all move the site; three times gives BURNED and tonight guaranteed.
  - `hotCoreDoubles`: two chunks hot, one after `starfall cool`, with no block scan (counted reads).
  - `toolTiers`: the vanilla tag membership of the four blocks.
  - `skySavedDataRoundTrip`.
  - `excavatorVeinAtAutomationAge` (IE is in the run): no vein in age_5; after the age_6 grant a vein with the mix id and radius exists, and IE's save data is dirty; a vein removed by hand comes back at the reconcile.
  - `skyPayloadCodecs`.
- **U (TFC jar data)** `NoCollapseTagsTest`: no Starfall block in `#c:ores` or `#c:stones`; `sky_rock` is in no `tfc:can_*` tag (read from the TFC jar's tag JSON on the test classpath).
- **M:** the streak and boom from 1,000 blocks, the smoke column, TFC landslides at a real crater.

### 25.12 Acceptance criteria

1. No live fall comes before the Iron Age. The first clear night of the Iron Age and of the Electric Age brings a fall for sure. No fall lands inside a claim, within 256 blocks of the heart, near a respawn point, in an inhabited chunk, on a player road or under an isle.
2. A crater never stalls the server: at most 64 block changes per tick, nothing force-loaded or generated, no level access inside `ChunkEvent.Load`.
3. Missing the glow costs only the double drop. A cold crater still yields its full metal.
4. Meteoric iron goes the TFC anvil way without melting and has no tools. Starsteel comes only from falls from the Electric Age on.
5. From the Information Age every crater feeds an IE excavator, and the vein survives a restart. No other ore enters the world, and no TFC ore sits in a crater.
6. Before the Iron Age, meteorites and cores look like sky rock and drop nothing usable.

### 25.13 Implementation notes (M10, branch `c1/sky-m10`, 2026-10-15)

- **Packages.** `sky/` holds the plumbing (`DeferredSites`, `ClimateSource` with `ClimateSources`, `SkySavedData`) and Starfall (`StarfallService`, `StarfallRules` and `CraterPlan` (pure), `CraterCarver`, `StarfallData` with its loader, `StarfallSite`, `SkyRegistry`, `StarfallCoreBlock`(+BE), `StarfallHotCondition`, `SkyTags`, `SkyMetal`). `text/Numbers` is the words helper. Compat: `compat/tfc/TfcClimateSource`, `compat/ftbchunks/Claims`, `compat/ie/StarfallVeins`. Client: `client/SkyStreaks`. Commands: `command/StarfallCommands`, suite `command/SkySuite` (`selftest sky`, also in `all`).
- **Registry names.** Blocks (with items): `firmages:sky_rock`, `meteorite`, `starsteel_meteorite`, `starfall_core` (`metal` = `meteoric_iron`|`starsteel`, `hot`), `meteoric_iron_block`, `starsteel_block`. Items: `firmages:meteoric_iron_{chunk,ingot,double_ingot,sheet,rod}`, `firmages:starsteel_{chunk,ingot}`. Block entity `firmages:starfall_core`. Sounds `firmages:starfall.impact` (vanilla `entity.generic.explode`), `firmages:starfall.distant` (vanilla `entity.lightning_bolt.thunder`). Loot condition `firmages:starfall_hot`. Mineral mixes `firmages:mineral/meteoric`, `firmages:mineral/starfall` (weight 0, `neoforge:mod_loaded` immersiveengineering). Tags: `#firmages:starfall/no_impact` (biomes), `#firmages:starfall/natural_block_entities`, `#firmages:starfall/player_blocks`, `#firmages:isle/body` (optional M13 entries), the `c:` metal tags of §25.5 plus `c:double_ingots/meteoric_iron`, and the vanilla tool tags of §25.4 (`needs_stone_tool` for the two storage blocks).
- **`ClimateSource`** takes the level: `averageTemp(level, x, y, z)`, `instantTemp(level, x, y, z)`; `ClimateSources.get()` is TFC's climate with TFC loaded, a mild constant without, and the GameTests install `TestClimateSource`.
- **Night and impact.** The roll runs once per overworld day once `dayTime % 24000` is in 13000..19999 (a server that starts mid-night still rolls). The live site is planned at roll time with its impact time and registered with `DeferredSites` only at the impact, so a loaded box is never carved before the star lands. `FirmAges.starfallTonight()` sets the guarantee and, when tonight's roll already ran, rolls again at once. `firmament_night.js` calls it once on the Firmament Night (persistentData key `firmages_night_star`).
- **Carving.** `CraterPlan` resolves `y` against the ground: bowl ops relative to the centre column's ground, lip and rim ops (`surface`) against their own column's ground, read when the carver gets there. The ground skips air, fluids, leaves, logs, replaceable plants and barriers. Unchanged positions cost a read (at most 4,096 reads per tick), not a change. A site that unloads mid-carve drops out of the active list and is queued again by the next chunk load of its box.
- **Base check and moves.** A player block entity is any block entity whose block is not in `#firmages:starfall/natural_block_entities`, except a container that still holds its loot table (an unopened structure chest). The player-block scan covers the box from ground − 8 to ground + 16. Isles are excluded through `StarfallService.registerIsleProbe` (M13 registers the isle starts and fallback records) and the `#firmages:isle/body` surface tag. A moved site also passes the site-choice rules again (shrine, spawn, biome, isle, claims).
- **Operator falls.** `starfall now <pos>` and `starfall old <pos>` are forced: exactly there, no base check, no move.
- **Core tier.** The core's tags are those of the meteorite (`needs_diamond_tool`); `canHarvestBlock` and the destroy progress follow the meteorite of its `metal`, so a starsteel core needs steel like its meteorites.
- **Network.** Protocol "5" carries `firmages:starfall_streak` (entry angle, target, impact time, metal, near flag). M11 adds no payload (§26.7).
- **Pack data (hand-off done here).** TFC heat of meteoric iron in `kubejs/data/firmages/tfc/item_heat/meteoric_iron/`; anvil, welding, IE metal press and arc furnace recipes and the storage block in `kubejs/server_scripts/recipes/age_2/sky_metals.js` and `age_5/starsteel.js`; the Age rows and the `[ore_blocks]` key `disguise` in `dev/age_map.toml` and `dev/gen_stage_locks.py`.
- **Tests.** U: `StarfallRulesTest` (9), `CraterPlanTest` (3), `NoCollapseTagsTest` (1); `gradlew build`: 160 unit tests green. G (`SkyGameTests`, template `sky_area` 25 by 26 by 25): `forcedImpactCarves`, `lazyCarveOnLoad`, `exclusionsMoveTheSite`, `hotCoreDoubles`, `toolTiers`, `skySavedDataRoundTrip`, `skyPayloadCodecs`, `skySelftestAndCommands`, `excavatorVeinAtAutomationAge`; `runGameTestServer`: "All 62 required tests passed". Night rolls and old craters are off in the GameTest run (`StarfallService.setNaturalEvents(false)`), since a live fall could land in another test's area. Not covered by G: the FTB claims rule (FTB Chunks is not in the run) and a skystone surface (no skystone before M13; the isle probe stands in).
- **Open (M).** The streak and the boom from 1,000 blocks, the smoke column, TFC landslides at a real crater (§25.11), the core's emissive overlay with Sodium and the shader.

## 26. Sky Harvest

### 26.1 Purpose

The only source of storm glass (Storm Rod, Iron Age) and aurora light (Aurora Net, Industrial Age). It ties the weather and the cold lands to the main road.

### 26.2 Player loop

In the Iron Age the fellowship builds Storm Rods on the base and feeds them sand. Every rain brings strikes, thunder brings many, and each strike fuses sand into storm glass. In the Industrial Age they carry Aurora Nets north, raise them on high frames under open sky, and catch aurora light on clear nights. They bring it home by pipe, by train or in buckets.

### 26.3 The aurora (`sky/AuroraRules`, pure, the same on server and client)

- `active(x, z, time)` is true when all of these hold:
  - it is night (`dayTime % 24000` in 13000..23000);
  - `averageTemp(x, reference_y, z) <= max_average_temp` (the cold band, hemisphere-proof);
  - the overworld is not raining (vanilla flag);
  - the night roll `hash(auroraSeed, day, x >> 9, z >> 9) < chance`.
- `chance` is `night_chance.cold` when `instantTemp(x, reference_y, z) <= averageTemp(x, reference_y, z)` (the cold half of the year there), else `night_chance.warm`. **Both ship as 1.0** (Felix S3A: "aurora every clear night in the cold band, double in winter"; review fix 0.12.0, the earlier 0.5/0.25 left nights where the Auroras mod drew northern lights overhead while Jade said "No aurora overhead"). Winter doubles the yield instead (`cold_half_factor`, §26.4). A summer group is not stuck waiting for winter.
- **Reference height:** both temperatures are read through `ClimateSource` at the fixed `reference_y` (140), never at the net's or the camera's own height. TFC lowers the average by 0.16225 °C per block above Y 63, capped at 17.82 °C [verified, javap `Helpers.adjustAverageTemperatureByElevation`]. Without a fixed height, every isle top would count as polar, and the server and the client would disagree.
- **Band distance:** TFC's base gradient is about 2.5 °C per 1,000 blocks (-20..30 °C over `temperature_scale` 20,000 [verified, `RegionGenerator`]). TFC's spawn lies at about 5 to 7 °C sea-level average (-5.8 °C at Y 140 on the M11 boot seed), so the planned 0.0 at Y 140 would have put spawn itself in the band. **As built: `max_average_temp` -10.0 at Y 140** (about 2.5 °C at sea level), measured on the copy boot: -7.9 °C 500 blocks north of spawn, -10.3 °C at 1,000, -11.4 °C at 2,000; the band starts about 1,000 blocks poleward.
- Intensity is 0.5..1.0, from the same hash.
- `/firmages sky aurora on|off|auto` overrides everything but the band and the height.
- The aurora is evaluated on the server only (the net, Jade and the status command); no client needs the seed (§26.7).
- Enhanced Celestials is not used: its lunar events are world-wide nights, while the aurora is a place.

### 26.4 Aurora Net (`firmages:aurora_net`, Industrial Age)

- **A single block,** not a multiblock. A net works only at `y >= min_y` (140) with open sky above it (`canSeeSky`), so players build it on high frames of any blocks. Scale comes from more nets. There is no validation code, and a Mekanism pipe connects to any side.
- **Filling:** every 20 ticks while `AuroraRules.active` holds at its (x, z), it adds `mb_per_hour * intensity / 50` mB to its 4,000 mB tank, times `cold_half_factor` (2) in the cold half of the year. The amount is divided by 1 + the number of other nets within `share_radius` (1), so packing nets tight gains nothing.
- **State and Jade:** the blockstate `catching` (light 10, shimmer) shows it. Jade lines: "Catching the aurora", "No aurora overhead", "Too low: needs height 140" or "Needs open sky".
- **Capabilities:** `Capabilities.FluidHandler.BLOCK` on all sides, drain only. There is no energy capability.
- **Yield:** 100 mB per in-game hour at full intensity (200 in the cold half), about 750 mB per clear night per net at average intensity (1,500 in the cold half). Four nets on a cold-band trip fill the pack's whole demand (about 2.5 buckets, §24.2) in one clear night.
- **Aurora light** (`firmages:aurora_light`, bucket, light 12, no world interaction) is a fluid because nets are pumped by pipe or train, and the Lens Crown takes it as a fluid ingredient.
- **Recipe** (Industrial Age, shaped, `kubejs/server_scripts/recipes/age_4/sky_harvest.js`): four `immersiveengineering:stick_steel` (corners), two `immersiveengineering:wirecoil_copper`, two `firmages:meteoric_iron_sheet`, one `firmages:storm_glass` (centre).

### 26.5 Storm Rod (`firmages:storm_rod`, Iron Age)

- **Shape:** two blocks high, placed and broken as one like a door: a lower basin with the block entity and an upper rod.
- **Inventory:** a 16-slot input for `#c:sands` (holds the seven TFC sands [verified]) and a 16-slot output. `Capabilities.ItemHandler.BLOCK` inserts into the input from the top and sides and extracts from the output below. A plain container screen.
- **Weather:** while `level.isRaining()` (global) and the sky is open above the rod tip, each rod rolls every 20 ticks so that it is struck on average every `mean_strike_ticks_rain` (4,800). During `level.isThundering()` the rate is multiplied by `thunder_factor` (4).
- **Local climate is ignored:** an arid base under TFC's local climate still gets strikes when the world rains (a dry storm). This decides the arid-base case: storm glass is needed in the Arcane Age, and no base may be cut off from it.
- **Strike:** it spawns a visual-only `LightningBolt` at the tip (`setVisualOnly(true)`: no fire, no damage, no charged creeper) and fuses `sand_per_strike` (4) sand into storm glass. It uses its own roll, with no mixin on vanilla lightning; vanilla lightning rods stay as they are.
- **Rate:** TFC's climate model gives one rain window of 12,000..24,000 ticks per 66,000 ticks (about 27 % of the time), and thunder needs rain and thunder together (about 2.5 %) [verified, javap `OverworldClimateModel`]. One rod therefore averages about 16 storm glass per real hour, more in thunder. Two rods cover the Iron and Arcane Ages' 13 storm glass within about an hour of play that includes one rain.
- **No FE output:** no energy capability on any side, and no redstone pulse that counts strikes.
- **Jade:** "Waiting for rain", "Waiting for rain: needs open sky", "Storm: fusing sand", or "No sand".
- **Recipe** (Iron Age, TFC workbench, `age_2/sky_metals.js`): `firmages:meteoric_iron_rod` (top), two `tfc:metal/rod/wrought_iron` (middle column), three `tfc:metal/sheet/copper` (basin row).
- **`storm_glass`:** a full glass block with blast resistance 1200 and hardness 3. It drops itself (no silk touch needed) and has a connected glass look.

### 26.6 Data: `data/firmages/firmages_sky/sky_harvest.json`

```json
{
  "aurora": {"min_y": 140, "reference_y": 140, "max_average_temp": -10.0, "night_chance": {"cold": 1.0, "warm": 1.0},
             "mb_per_hour": 100, "cold_half_factor": 2.0, "net_capacity": 4000, "share_radius": 1, "colors": ["#3CFF9E", "#7A5CFF"]},
  "storm": {"mean_strike_ticks_rain": 4800, "thunder_factor": 4, "sand_per_strike": 4,
            "input": "#c:sands", "result": "firmages:storm_glass"}
}
```

`SkySavedData` adds `auroraSeed` (random at creation) and `auroraOverride`.

### 26.7 Rendering and sound

- **As built (M11, 2026-10-16): no renderer of our own.** The pack ships the Auroras mod, which draws the northern lights by TFC latitude, and turns Complementary's aurora off (decision C1-Deko "ein Polarlicht" in `dev/decisions-while-away.md`). A second ribbon set on top would look wrong, so `client/AuroraRenderer`, `firmages:sky_sync` and the client keys `effects.aurora*` are dropped. The Auroras lights mark the cold north; the net's `catching` glow (light 10, a shimmering veil), its chime and Jade say whether this column catches tonight.
- **Sounds:** the strike uses vanilla thunder at reduced volume, and the net plays a soft amethyst chime when it starts catching (`firmages:aurora_net.catch`, mapped onto a vanilla event).

### 26.8 Network, config, commands

- **Network:** none (§26.7).
- **Server config:** `skyHarvest.enabled` (true).
- **Client config:** none (§26.7).

| Command | Purpose |
|---|---|
| `sky aurora on\|off\|auto` | Override for tests and events |
| `sky aurora status [pos]` | Night, both temperatures at `reference_y` against the threshold, cold or warm half, height, sky, roll, chance, intensity, override |
| `sky aurora band [radius]` | Samples the band boundary around the caller (every 256 blocks) and prints the nearest cold-band point; for the PoC of 26.10 |
| `sky storm rain\|thunder\|clear [seconds]` | Sets the overworld weather for a test (default 600 s) |
| `sky storm strike <pos>` | Strikes the Storm Rod at `pos` (either half) once: bolt and fusing, without the roll |
| `sky storm status <pos>` | Jade reason, sand, output room, strikes since load, open sky, rain and thunder |

### 26.9 Tests

- **U** `AuroraRulesTest`: night window, band at the reference height (a net at Y 250 above a warm column stays off), every clear night in both halves of the year and the doubled cold-half fill, rain, override, intensity range, all with a fake `ClimateSource`.
- **G** `SkyGameTests` with `TestClimateSource`:
  - `auroraNetFills`: override on, height and sky give the expected mB; covered, too low or override off give 0; two adjacent nets get half each; a drain like a pipe takes it; no energy capability.
  - `stormRodFusesSand`: forced rain gives the base rate and forced thunder four times it (seeded roll); the bolt is visual-only; four sand become four storm glass; no fire block is placed, no entity is hurt, and no side has an energy capability.
- **M:** the aurora with and without Complementary; strike frequency over a real TFC day.

### 26.10 Acceptance criteria

1. Aurora nets fill only in the cold band (at the reference height), at night, without rain, high and under open sky, and pipes drain them. Every clear cold-band night has an aurora, so no group waits for winter; the cold half fills twice as fast.
2. **[PoC]** On three seeds, `sky aurora band` finds the cold band within 2,000 blocks of world spawn. Otherwise `max_average_temp` is raised in data until it does.
3. Storm Rods make storm glass in every rain, faster in thunder, at any base climate. Over five real hours on the pack server one rod averages 10 to 25 storm glass per real hour **[PoC]**.
4. No block of §25 or §26 exposes an energy capability, and no forecast exists.

## 27. The Lens Crown and the celestial alloys

### 27.1 Purpose

The only refinery of sky materials and, under V4A, a mandatory station from the Arcane Age. A ring of lens mounts around a Star Anvil plays a **chord** of lenses into the anvil under the open sky. Each recipe needs its exact chord. Lenses come one per Age, so the Crown grows with the shrine.

### 27.2 Player loop

In the Arcane Age the fellowship builds the 9 by 9 crown from local stone, four storm glass caps and a Star Anvil, and grid-crafts the first three lenses (Stone, Bronze, Iron). They set the lenses in mounts, power the chord's mounts with redstone, feed the anvil and forge the first Starlit Iron under the open sky. Night halves the time. Each later Age adds one lens (made in the Crown itself) and one alloy for that Age's offering. A comparator and a redstone decoder later run the whole chord library unattended.

### 27.3 Naming and package

- One stem, **crown**, for everything except the recipe type: package `crown/` (`CrownRegistry`, `StarAnvilBlock`(+BE), `LensMountBlock`(+BE), `LensItem`, `Chord` and `CrownRules` (pure), `ChordLibrary` (reload listener), `StarforgeRecipe`(+`Serializer`, `Type`), `CrownService`), data folder `firmages_crown/`, config `crown.*`, commands `/firmages crown`, compat `compat/emi/CrownEmi` and `compat/ponder/CrownScenes`, and `client/CrownRenderer`.
- The recipe type stays `firmages:starforge` ("Starforging" in EMI).
- No kinetic mount: redstone mounts plus Create redstone links already automate every chord, and a Create `KineticBlockEntity` subclass would need Flywheel at compile time and cannot be tested headless.

### 27.4 Blocks and items

| Id | Kind | Age | Notes |
|---|---|---|---|
| `firmages:star_anvil` | block + BE | age_3 | Crown anchor; four input slots, one output, 4,000 mB tank; blockstates `formed`, `active` |
| `firmages:lens_mount` | block + BE | age_3 | Holds one lens; blockstates `facing` (horizontal, toward the anvil), `in` (bool), `lens` (0..9, 9 = empty) |
| `firmages:lens_blank` | item | age_3 | Polished storm glass |
| `firmages:lens/<age>` | nine items | §24.5 | `stone, bronze, iron, arcane, industrial, electric, information, space, quantum` = lens 0..8, stack size 1 |
| seven alloy ingots | items | their Age | `starlit_iron_ingot`, `cloudsteel_ingot`, `aurora_steel_ingot`, `halo_steel_ingot`, `void_steel_ingot`, `quasar_alloy_ingot`, `firmament_alloy_ingot` |

- **Indices are 0-based everywhere:** lens N belongs to shrine tier N, and lens N can be made once tier N is awakened, which is age_(N+1). So the Stone, Bronze and Iron Lenses come in age_3, Arcane in age_4, Industrial in age_5, Electric in age_6, Information in age_7, Space in age_8 and Quantum in age_9.
- Lens N carries the colour of tier N (`tier/ring_N.json` `response.beam_color`: #FFB347, #FFD54A, #E06030, #9B59FF, #F0F0F0, #40E0FF, #40FF80, #E0E8FF, #E0B0FF [verified]). The colour is read from the loaded tier data, never copied.
- Item tags `#firmages:celestial_alloy/age_3` .. `age_9`, one member each (§27.7). Consumers outside the Crown name these tags or the concrete ids.

### 27.5 The crown multiblock

`data/firmages/modonomicon/multiblocks/lens_crown.json`, `modonomicon:dense`, 9 by 9 by 3, top layer first. Key `0` is the Star Anvil in the centre of the bottom layer: the anchor is not in the middle layer, because of the Modonomicon layer bug (§7.8).

| Layer | Content |
|---|---|
| Bottom | A 32-block border `S` (`#firmages:crown/stones`: TFC smooth rock and rock bricks; vanilla smooth stone as the test stand-in), the anvil `0`, and the inside `_` (any block, free for pipes and hoppers) |
| Middle | Four corner pillars `P` (`#firmages:crown/pillars`: TFC rock pillars and polished rock) and twelve sockets `M` on the border, three per side at offsets -2, 0 and +2 (`#firmages:crown/sockets` = the crown stones plus the lens mount) |
| Top | Four storm glass caps `G` (`#firmages:crown/caps`) |

- There are 53 positions, and the pattern is four-fold symmetric, so any rotation validates.
- A socket holding a stone is valid. The crown grows by swapping stones for mounts: three in the Arcane Age, up to nine lenses on twelve sockets.
- **Validation** calls the existing `ShrineMultiblocks.check(level, anchor, id)`; `ringRoles(id)` returns an empty map for a non-ring id, so no consecrated equivalents apply [verified, source]. It runs on anvil placement; on any `BreakEvent`, `EntityPlaceEvent` or `ExplosionEvent.Detonate` inside the crown box; on mount insert or removal; and every `crown.validateTicks` (100) while loaded. A valid crown sets `formed=true` and turns every mount's `facing` toward the anvil.
- **Preview:** right-click on the anvil with an empty hand. An unformed crown sends the ghost through the existing `firmages:shrine_preview` payload (multiblock id, anchor, rotation) and the actionbar line "The crown lacks three blocks." A formed crown names the current chord and the pending forging.

### 27.6 Lenses, chords and the forge cycle

**Mounting.** Right-click a mount with a lens to set it; sneak and right-click with an empty hand to take it back. Mounts have no item handler: lenses stay put and only swing. Survival players cannot break a mount that holds a lens (as the relic plinth, §7.8).

**Swing.** A mount is **in** while it receives a redstone signal (level-triggered, so a sequencer holds a chord as long as it powers the line). A swing takes `crown.swingTicks` (10), and a lens counts only once it is fully in.

**Chord.** The set of lens indices that are fully in. Two copies of one lens count once. A recipe matches only its exact chord, so an extra lens in makes a different chord.

**Chord library.** `data/firmages/firmages_crown/chords.json` holds named chords, each with its comparator signal. Recipes name a chord. There are at most 15 chords (signals 1..15), and lang keys are `firmages.chord.<name>`.

```json
{ "dawn_triad": {"lenses": [0,1,2], "signal": 1}, "forge_triad": {"lenses": [1,2,3], "signal": 2},
  "spark_triad": {"lenses": [2,3,4], "signal": 3}, "lattice_triad": {"lenses": [3,4,5], "signal": 4},
  "orbit_triad": {"lenses": [4,5,6], "signal": 5}, "quantum_triad": {"lenses": [5,6,7], "signal": 6},
  "full_crown": {"lenses": [0,1,2,3,4,5,6,7,8], "signal": 7},
  "stone_and_iron": {"lenses": [0,2], "signal": 8}, "stone_and_arcane": {"lenses": [0,3], "signal": 9},
  "stone_and_industrial": {"lenses": [0,4], "signal": 10}, "stone_and_electric": {"lenses": [0,5], "signal": 11},
  "stone_and_information": {"lenses": [0,6], "signal": 12}, "stone_and_space": {"lenses": [0,7], "signal": 13},
  "anchor_chord": {"lenses": [6], "signal": 14} }
```

Library rules:
- An alloy is the triad of the three newest lenses.
- A lens is the Stone Lens plus the newest lens.
- The last alloy is the full crown.
- `anchor_chord` (the Information Lens alone, from age_7) serves all nine blessing shards (§29). Recipes are chosen by their ingredient set, so one chord can serve many recipes.
- Signal 15 stays free.

**Forge cycle.**
1. The anvil holds the ingredients of one recipe; ingredient sets are disjoint, so at most one recipe matches. A comparator on any side gives that recipe's chord signal (0 = nothing matches).
2. Forging runs while all of these hold: the crown is formed; the chord equals the recipe's chord; the fluid is in the tank; the output slot can take the result; and `level.canSeeSky(anvil.above())` (`crown.requireOpenSky`; rain does not matter; the Moon and Mars count, the Nether, the End and The Origin do not).
3. Time is the recipe's `time` (default 100 ticks). While `level.isNight()` and the sky is open, each tick counts `1 / crown.nightTimeFactor` (factor 0.5, so night is twice as fast).
4. A changed chord resets the progress to 0 and keeps every item. A covered sky pauses it. Nothing is ever lost.
5. When the forging is done, the ingredients and fluid are consumed and the result goes into the output slot. A flash runs down the active beams, and the chord plays once as bell notes (`minecraft:block.note_block.bell`, lens N at pitch 0.5 + N × 0.1875, through `firmages:crown.chord`).

### 27.7 Recipe format (`firmages:starforge`) and the library

`data/<ns>/recipe/starforge/<name>.json` uses the NeoForge `SizedIngredient` flat codec (ingredient fields plus `count`), with one to four ingredients, an optional fluid and `time` 20..1200. The result is an `ItemStack` codec, so components are allowed.

```json
{
  "type": "firmages:starforge",
  "chord": "firmages:spark_triad",
  "ingredients": [ {"item": "firmages:starsteel_ingot"}, {"tag": "c:ingots/red_steel"}, {"item": "firmages:storm_glass"} ],
  "fluid": {"fluid": "firmages:aurora_light", "amount": 100},
  "time": 100,
  "result": {"id": "firmages:aurora_steel_ingot", "count": 2}
}
```

- A file is rejected (ERROR) for an unknown chord, more than four ingredients, a `count` below 1, a non-positive fluid amount or a missing result. Two recipes with identical ingredient lists give a WARN, and the later id loses.
- **Recipe gate (§4):** the walker finds `result` [verified: `JsonOutputWalker` key pattern `results?`]. Alloys, lenses and shards are Age-tagged, so a locked Age's forging never reaches the anvil or EMI. Station Age: `FirmAges.stationAge('firmages:starforge', 'age_3')` in `station_ages.js` (hand-off).
- **The shipped library has 22 recipes and 14 chords; the cap is 25 recipes.**

| # | Chord | Inputs (anvil) | Aurora light | Result | Age |
|---|---|---|---|---|---|
| 1 | dawn_triad 0,1,2 | 2 meteoric iron ingot, storm glass, `#firmages:crown/arcane_gems` | none | 2 Starlit Iron | age_3 |
| 2 | forge_triad 1,2,3 | meteoric iron ingot, `#c:ingots/black_steel`, firmament crystal | none | 2 Cloudsteel | age_4 |
| 3 | stone_and_iron 0,2 | lens blank, 2 `#firmages:crown/arcane_gems` | none | Arcane Lens | age_4 |
| 4 | spark_triad 2,3,4 | starsteel ingot, `#c:ingots/red_steel`, storm glass | 100 mB | 2 Aurora Steel | age_5 |
| 5 | stone_and_arcane 0,3 | lens blank, firmament crystal, 2 `#c:sheets/black_steel` | 100 mB | Industrial Lens | age_5 |
| 6 | lattice_triad 3,4,5 | starsteel ingot, `#c:ingots/blue_steel`, firmament crystal | 100 mB | 2 Halo Steel | age_6 |
| 7 | stone_and_industrial 0,4 | lens blank, firmament crystal, 2 `#c:plates/aluminum` | 100 mB | Electric Lens | age_6 |
| 8 | orbit_triad 4,5,6 | 2 voidglass, `#c:ingots/desh`, starsteel ingot | 100 mB | 2 Void Steel | age_7 |
| 9 | stone_and_electric 0,5 | lens blank, starsteel ingot, 2 `#c:sheets/blue_steel` | 100 mB | Information Lens | age_7 |
| 10 | quantum_triad 5,6,7 | voidglass, `#c:ingots/calorite`, deep-space sample | 250 mB | 2 Quasar Alloy | age_8 |
| 11 | stone_and_information 0,6 | voidglass, 2 `#c:plates/desh` | 250 mB | Space Lens | age_8 |
| 12 | full_crown 0..8 | `evolvedmekanism:alloy_singular`, voidglass, starsteel ingot, firmament crystal | 1,000 mB | Firmament Alloy | age_9 |
| 13 | stone_and_space 0,7 | voidglass, 2 `#c:plates/calorite` | 250 mB | Quantum Lens | age_9 |
| 14..22 | anchor_chord 6 | one `#firmages:anchor/ring_N`, one alloy (§29.4) | none | Shard of tier N | age_7 (tier 8: age_8) |

- `#firmages:crown/arcane_gems` holds the cut TFC gems of the Arcane Age (lapis lazuli, amethyst, emerald, ruby, sapphire, topaz, opal). The Arcane Age has no metal of its own (`[materials]` [verified]), so its gems stand in.
- **Chain check:** every alloy uses materials of its own Age or earlier, and nothing is circular. Voidglass (age_7) needs the sampler module, which needs Halo Steel (age_6), never Void Steel. Quasar Alloy's deep-space sample (age_8) needs the deep-space module, which needs Void Steel (age_7).

### 27.8 Celestial alloys and V4A: the recipe changes (pack hand-off)

| Age | Alloy (id, tag) | Sky inputs + Age metal | Signature recipe (file, id) | Change: one ingredient |
|---|---|---|---|---|
| age_3 | Starlit Iron `firmages:starlit_iron_ingot` | meteoric iron, storm glass + Arcane gem | Arcane Keystone, tech route (`age_3/arcane_age.js`, `firmages:sequenced_assembly/arcane_keystone`) | new fourth step `create:deploying` of Starlit Iron before the press; with `loops: 3` the keystone takes three ingots |
| age_3 | (same) | | Arcane Keystone, magic route (`firmages:ritual/arcane_keystone`) | `tfc:metal/ingot/steel` becomes `firmages:starlit_iron_ingot` |
| age_4 | Cloudsteel `firmages:cloudsteel_ingot` | meteoric iron, firmament crystal + black steel | Pressure Core (`age_4/industrial_age.js`, `firmages:crafting/pressure_core`) | pattern `[' H ','DGD',' T ']` becomes `['AHA','DGD',' T ']`, A = Cloudsteel (two) |
| age_5 | Aurora Steel `firmages:aurora_steel_ingot` | starsteel, storm glass, aurora light + red steel | Humming Core (`age_5/electric_age.js`, `firmages:arc_furnace/humming_core`) | additive `2 #c:sheets/red_steel` becomes `1 firmages:aurora_steel_ingot` (the arc furnace's four additive slots are all used [verified]) |
| age_6 | Halo Steel `firmages:halo_steel_ingot` | starsteel, firmament crystal, aurora light + blue steel | Data Matrix (`age_6/information_age.js`, `firmages:crafting/data_matrix`) | `['BCB','CSC','BTB']` becomes `['ACA','CSC','BTB']`, A = Halo Steel (two of the four blue steel sheets) |
| age_7 | Void Steel `firmages:void_steel_ingot` | voidglass, starsteel, aurora light + desh | Star Chart (`age_7/space_age.js`, `firmages:fusion/star_chart`) | one `#c:plates/desh` becomes Void Steel (injector count stays six) |
| age_8 | Quasar Alloy `firmages:quasar_alloy_ingot` | voidglass, deep-space sample, aurora light + calorite | Quantum Core (`age_8/quantum_age.js`, `firmages:fusion/quantum_core`) | one `#c:plates/calorite` becomes Quasar Alloy (injector count stays seven) |
| age_9 | Firmament Alloy `firmages:firmament_alloy_ingot` | voidglass, starsteel, firmament crystal, aurora light + `evolvedmekanism:alloy_singular` | Ultimate Singularity (`age_9/singularity_age.js`, `firmages:fusion/ultimate_singularity`) | `evolvedmekanism:alloy_singular` becomes Firmament Alloy (which consumes it); the nine relic slots stay, so the Gathering (§7.5) is unaffected |

- **Not touched:** the Hearthstone, Sky Disc and Steel Heart (they come before the Crown), the Age tokens, the Cold Forge, and the Awakened Keystone ritual (it consumes an Arcane Keystone, which already holds Starlit Iron).
- **Quests:** the goal quests list the offering's ingredients (style guide rule 3), so `dev/gen_quests.py` gains the alloy lines (hand-off).
- **Alloy consumers outside the main road:**

| Alloy | Wayshrine offering (§28) | Launch Complex (§31) | Sky Anchor and shards (§29) | Archipelago crown (§31) |
|---|---|---|---|---|
| Starlit Iron | age_3 offering | | shards of tiers 0 to 3 | Arcane island |
| Cloudsteel | age_4 offering | | shard of tier 4 | Industrial island |
| Aurora Steel | age_5 offering | | shard of tier 5 | Electric island |
| Halo Steel | age_6 offering | sampler module | shard of tier 6 | Information island |
| Void Steel | | deep-space module | Sky Anchor, shard of tier 7 | Space island |
| Quasar Alloy | | | shard of tier 8 | Quantum island |
| Firmament Alloy | | | | (none; only the Ultimate Singularity) |

### 27.9 Part recipes (pack, KubeJS, hand-off)

- Lens blank: storm glass on Create sandpaper polishing (`create:sandpaper_polishing`), one to one.
- Stone, Bronze and Iron Lens (age_3, grid; the Crown cannot make its own first lenses): `[' S ','SBS',' S ']`, B = lens blank, S = copper, bronze or wrought iron sheet (TFC).
- Lens Mount: four meteoric iron ingots around a Create precision mechanism.
- Star Anvil: TFC steel anvil, four meteoric iron ingots, four storm glass.

### 27.10 Automation

- **Item and fluid handlers** (`Capabilities.ItemHandler.BLOCK`, `FluidHandler.BLOCK`): the top and sides insert into slots 0..3 (each slot takes what fits the open recipe set) and the tank (aurora light only). The bottom extracts the output only. An AE2 pattern provider in blocking mode pushes one set of items and fluid per pattern; an import bus or hopper under the anvil takes the result.
- **Comparator:** gives the pending recipe's chord signal (§27.6). A decoder (vanilla comparators, or Create redstone links with one frequency per lens) turns the signal into the right mount lines. The puzzle is finite: at most 15 signals and at most nine lines.
- Lens mounts need no hand after setup.

### 27.11 Rendering, sound, Jade, EMI, Ponder

- **`client/CrownRenderer`** (BER of the anvil; render box = the crown box while formed):
  - a thin coloured beam from each lens that is in, down to the anvil top (the vanilla beacon beam routine already used by `ShrineHeartRenderer`, posed along the segment);
  - a short white column into the sky while forging;
  - the input items circling and the output glowing.
- **Mount BER:** the lens glass as a quad tinted with the tier colour, tilted 45 degrees toward the anvil when in. Client toggle: `effects.crownBeams`.
- **Data on the client:** lens colours come from `firmages:shrine_sync` (the nine tier colours, §7.10). There is no new payload; anvil and mount state ride on vanilla block-entity sync.
- **Jade** (`FirmagesJadePlugin.Crown`):
  - anvil: "Chord: Dawn Triad", "Forging Starlit Iron, 45 %", or the reason ("The sky is hidden", "The crown lacks three blocks", "No forging matches", "Swing in: Bronze Lens");
  - mount: "Bronze Lens, in".
- **EMI** (`compat/emi/CrownEmi`, from `FirmagesEmiPlugin.register`): category `firmages:starforge` ("Starforging"), with the Star Anvil and the Lens Mount as workstations. The widget shows the inputs, the fluid, the result, nine dots (filled in the tier colour when in the chord, hollow otherwise), the chord name, the time, and "Twice as fast under the night sky". The Age view (§21) hides locked lenses and alloys like any item.
- **Ponder** (`compat/ponder/CrownScenes`, only with Create; schematics by `dev/gen_ponder_schematics.py`):
  - "The Lens Crown" (frame, sockets, anvil, ghost) on `star_anvil`;
  - "Chords" (mount a lens, redstone in, forge, a roof pauses, night speeds up) on `lens_mount`;
  - "A Chord Library" (pattern provider, comparator, link decoder, output bus) on `star_anvil`.

### 27.11a Pack integration (M15, branch `c2/sky-m15`, 2026-10-16)

- **One switch:** `kubejs/startup_scripts/sky.js` defines `global.FA_SKY` (the alloy of each Age and `REQUIRED`, the sky materials and the items of their sources: Starfall, Sky Harvest, Drifting Isles, Launch Complex). Every V4A change, the crown parts, the Sky Anchor, the Launch Complex parts and the Wayshrine alloy offerings load only while every `REQUIRED` item is registered (`Item.exists` inside the event, so the Age swap needs no script rerun). A jar without one of the sky features keeps the old signature recipes, so the main road never waits for a missing source.
- **Recipes:** the eight changes of §27.8 in `recipes/age_3..age_9`; the parts of §27.9 in `recipes/age_3/lens_crown.js` (lens blank by sandpaper polishing, the first three lenses, mount, anvil); the Sky Anchor in `recipes/age_7/space_age.js`; the Launch Complex parts of §31.3 and §31.4 in `recipes/age_7/launch_complex.js` (launch control, cradle and port from desh, steel and Mekanism parts, the probe as a small desh rocket, one Halo Steel per sampler module and one Void Steel per deep-space module). Station Age: `firmages:starforge` age_3 in `station_ages.js`.
- **Checks:** `dev/poc_analyze.py` S-1 to S-4 (the one-source rule of §24.3 and §24.7 criterion 1, over recipes, ore tags, loot tables and loot modifiers); the R- walk knows the starforge station (anvil, mount and the chord's lenses), the sky world sources (meteoric iron chunk age_2, firmament crystal age_4, starsteel chunk age_5) and the sky machines (Storm Rod, Launch Complex). `dev/predict_dump.py` predicts the merged dump.

### 27.12 Config and commands

| Key (`firmages-server.toml`) | Default | Meaning |
|---|---|---|
| `crown.enabled` | true | false: the anvil never forges (the recipes stay) |
| `crown.requireOpenSky` | true | the sky rule of §27.6 |
| `crown.nightTimeFactor` | 0.5 | 0.1..1.0, forge time factor under the night sky |
| `crown.validateTicks` / `crown.swingTicks` | 100 / 10 | §27.5, §27.6 |

Client config: `effects.crownBeams` (true).

| Command | Purpose |
|---|---|
| `crown status [pos]` | Formed, missing blocks, mounts and lenses, chord, pending recipe, progress, sky, night |
| `crown chords` | The library with its signals |
| `crown swing <pos> <lens,...>` | Debug; needs `debug.allowSimulate` |

KubeJS binding: `FirmAges.crownChord(name)` returns the lens list.

### 27.13 Tests

- **U** `ChordLibraryTest`: the shipped `chords.json` parses; 14 chords; signals unique in 1..15; lenses in 0..8; no empty chord; no two chords with the same lens set; bad files are rejected.
- **U** `StarforgeRecipeTest`: the 22 shipped recipes parse; at most 25 recipes; no identical ingredient lists; every chord uses only lenses that exist in the result's Age (lens N from max(age_3, age_(N+1))); one alloy per Age from age_3 to age_9, and each alloy is in its `celestial_alloy/age_N` tag; aurora light only from recipe 4 (age_5) on; every input's Age is at or below the result's Age.
- **U** `CrownRulesTest`: exact chord match, duplicates count once, swing completion, night factor and rounding, reset on chord change, pause under a covered sky.
- **G** `CrownGameTests` (vanilla stand-ins through `firmages_test` crown tags, test recipes with vanilla items):
  - `crownValidatesAndGrows`: all rotations; a missing pillar fails; stone sockets count; twelve mounts; mount facing set; the ghost payload on an unformed crown.
  - `redstoneChordForges`: three lenses and a hopper feed; powering two of three does nothing; all three give the result after 100 ticks; a fourth lens in does nothing; a chord change mid-way resets and keeps the items; a roof pauses; night halves the time.
  - `comparatorNamesTheChord`: the signals of two recipes, and 0 for a mismatch.
  - `handlersAndSave`: side rules; the tank accepts only the test fluid; anvil and mount NBT round trip; a mount with a lens is unbreakable in survival.
  - `gateFollowsAges`: in age_3 the age_4 recipe is absent from the recipe manager; after the swap it is present.
- **P** `selftest crown`: §24.7 criterion 4; the live `firmages:starforge` recipe count is 22.
- **M** (client, pack): beams with Sodium and Complementary, EMI dots and colours, the three Ponder scenes, an AE2 pattern provider running recipes 1, 2 and 4 unattended through a link decoder for one TFC day, and the AE2 fluid push into the anvil tank **[PoC]**.

### 27.14 Acceptance criteria

1. The 53-position crown validates in every rotation from TFC blocks and storm glass, grows from three to twelve mounts, and shows its ghost.
2. The exact chord plus an open sky forges. Night only halves the time. No state of the world loses an item.
3. There are seven alloys, one per Age from age_3 to age_9, each with exactly one starforge recipe, and nine lenses: the first three by grid, six by the Crown.
4. The eight recipe changes of §27.8 are in the pack, nothing else in a signature chain changed, and the P self-test is green.
5. One chord library runs unattended with redstone (G) and with AE2 plus Create (M).
6. No FE is produced or required, no magic mod is required, and all player text passes the style rules.

## 28. Wayshrines

### 28.1 Purpose

Eight to twelve ruined shrines of Caelum in the wide world, each in its own climate and built from that place's materials. The fellowship restores one, lays one offering of the current Age, does the Age's lighting rite and prays. A lit Wayshrine carries the home shrine's blessings and Hearthward into its radius and shows a beam. It is optional travel content and a consumer of offerings. **No travel** (V5A): no teleport, no sky-ride, no C2S packet.

### 28.2 Player loop

Caelum at the heart names the bearing and distance of the nearest sleeping Wayshrine. The fellowship travels there, right-clicks the core to see the ghost of the restored form, and rebuilds it from the local materials. They lay the offering, do the rite and pray. The shrine lights up for good and follows every new Age without another visit. On its first lighting it drops a Wayshrine Stone, which later becomes the heart of a Sky Anchor.

### 28.3 Rules

- **Ages:** a Wayshrine can be restored from its type's `min_age` (age_1 to age_5). Offerings and rites are defined for age_1 to age_6. A Wayshrine first restored in age_7 or later takes the age_6 entry, so nothing is ever closed.
- **One lighting per Wayshrine.** A lit Wayshrine follows the home shrine and carries each new tier at once: no return trip, no fuel, no decay.
- **No consecration:** restored blocks keep their local look. Pattern blocks break normally. A broken pattern makes the Wayshrine quiet (no blessings, no beam) until it is repaired; it stays lit, and the repair needs no new offering.
- **Co-op:** the state is world-global (one fellowship, §2.3), and any player can do every step.

### 28.4 Types and placement

There are eight types. The Wind Shrine stands on the Drifting Isles and replaces the Glacier Cairn of the drafts (Felix S6B, §28.4a); the other seven are ground shrines. Ground worldgen follows the Cold Forge pattern: jigsaw `size` 1, `step` `surface_structures`, `start_height` -1, `project_start_to_heightmap` `WORLD_SURFACE_WG`, and weathering by processor list.

Placement uses TFC's climate structure placement `tfc:climate` (`ClimateStructurePlacement`; codec fields `spacing`, `separation`, `spread_type`, `climate` with `min_temperature`..`max_temperature`, `min_groundwater`, `max_groundwater`, `min_forest`, `max_forest`, `min_elevation`, `max_elevation`, `fuzzy`, `ignore_rivers` [verified, javap TFC 4.2.11]). It extends `RandomSpreadStructurePlacement`, so locate works with it [verified]. Biome ids come from `TFCBiomes` [verified].

| Type id | Name | Biomes (`#firmages:has_structure/wayshrine/<type>`) | Climate band | min_age | Local materials (restored form) | Terrain |
|---|---|---|---|---|---|---|
| `forest_ring` | Forest Ring | plains, hills, rolling_hills, lowlands | edge forest or more (`min_forest` 2), 4..22 °C | age_1 | logs, rock bricks, thatch, one candle | `beard_thin` |
| `steppe_stones` | Steppe Stones | plains, rolling_hills, plateau | groundwater up to 220, up to edge forest | age_1 | raw rock pillars, rock bricks, bronze block | `beard_thin` |
| `swamp_altar` | Swamp Altar | lowlands, salt_marsh, mud_flats, wet plains and river valleys | groundwater 300 or more | age_2 | mud bricks, logs, wrought iron bars | `beard_box` |
| `desert_arch` | Desert Arch | dune_sea, grassy_dunes, badlands, mesas, buttes | groundwater up to 100, 18 °C or more | age_2 | raw and smooth sandstone, copper block | `beard_thin` |
| `mountain_spire` | Mountain Spire | mountains, old_mountains, highlands | none: the mountain biomes carry the height | age_3 | smooth rock, rock bricks, wrought iron bars | `beard_box` **[PoC]** |
| `wind_shrine` | Wind Shrine | on a Drifting Isle (§28.4a), no biome | none | age_4 | skystone, rock bricks brought up, steel bars, an electric lantern | none (placed by the isle piece) |
| `reef_altar` | Reef Altar | ocean_reef, ocean_atolls | 20 °C or more | age_4 | coral blocks, rock bricks, glass | `OCEAN_FLOOR_WG`, `apply_waterlogging` **[PoC]** |
| `volcanic_ring` | Volcanic Ring | volcanic_mountains, volcanic_island, volcanic_oceanic_mountains | any | age_5 | basalt bricks, smooth basalt, magma, steel block | `beard_box` |

- **Biomes:** each type lists the base biomes of the table and their TFC 4.2.11 variants (the doline, cenote, burren, shilin and tower karst families, glacial and volcanic variants; `dev/gen_wayshrines.py`, all ids in `data/tfc/worldgen/biome`). With the base biomes alone a 0.10.0 copy found no Wayshrine within 4,096 blocks of spawn: the variants cover most of the land. The `tfc:climate` placement stays the real filter.
- **No elevation bound:** `ClimateStructurePlacement` tests the climate at y 0 of the chunk [verified, javap TFC 4.2.11], so `min_elevation` never passes; no type uses it.
- **Density:** one structure set per ground type, `spacing` 72 chunks, `separation` 27, each with a distinct `salt`. The Wind Shrine has no structure set of its own (§28.4a). Measured for acceptance 2 (§28.11) with `/firmages wayshrine census 4000 [spacing separation]` on 20 fresh seeds (1001 to 1020, round C2):

  | Spacing / separation | Seeds with 7 to 11 | Mean | Range | Seeds below 4 |
  |---|---|---|---|---|
  | 64 / 24 | 8 | 10.9 | 3 to 20 | 2 |
  | **72 / 27 (shipped)** | **11** | **10.0** | **5 to 16** | **0** |
  | 80 / 30 (0.11.0) | 7 | 7.0 | 2 to 15 | 3 |
  | 96 / 36 | 6 | 4.7 | 1 to 9 | 6 |
  | 112 / 42 | 2 | 3.6 | 1 to 7 | 9 |

  No spacing reaches 16 of 20 seeds in the window: the count swings with the climate around the spawn (Mountain Spires dominate cold, hilly spawns; Desert Arch and Reef Altar lie beyond 4,000 blocks by design). 72 / 27 is the best measured: the most seeds in the window and none below five.

#### 28.4a The Wind Shrine (Felix S6B)

- **Where:** on the top surface of the largest isle of a Drifting Isle cluster (§30), with chance `wind_shrine_chance` (0.25) per cluster, decided from the structure start's seed, so `locate` and the pointer know it without generating the isle. The isle fallback cluster of an existing world (§30.3) always carries one, so every world has a Wind Shrine within `isles.fallbackRadius` of the heart from the Industrial Age.
- **How:** the isle's `StructurePiece` places the ruin template `structure/wayshrine/wind_shrine.nbt` on the isle top after the body, inside the chunk being generated (the piece is chunk-safe like the body). The fallback placer of §30.3 places the same template after the cluster's pieces, within the same 64 blocks per tick.
- **Reach and Age:** `min_age` age_4, the Age of the airship and the jetpack (§30.7), so the Wind Shrine takes the Industrial and Electric offerings and rites like any Wayshrine (age_4 and age_5: power the light). Its restored form is skystone with rock bricks and steel bars carried up, and an electric lantern on a charged capacitor as the light.
- **Pointer:** the pointer (§28.8) searches `#firmages:wayshrine` and, from age_4, the isle starts whose cluster carries a Wind Shrine.
- **Dependencies:** the template and the shrine data come with M16, the placement with the isle piece of M13 (§32).
- **The tutorial Wayshrine is placed by code, not by worldgen.** Vanilla `concentric_rings` centres on chunk (0, 0), while the TFC preset spawns players anywhere within `spawn_distance` 4000 of `spawn_center` [verified, `world_preset/overworld.json`; test worlds spawned at (3456, 1408) and (576, 1600)]. Biome search also finds nothing under TFC's biome source [poc-results.md].
  - On the first `ServerStartedEvent` with this feature (flag in `firmages_wayshrines`), `WayshrineService.placeTutorial` picks a point 300 to 800 blocks from `level.getSharedSpawnPos()` at a random bearing.
  - It loads the needed chunks once (the `OriginArena` build-once pattern) and checks land (no fluid at the surface) and slope (at most three blocks of height spread over the footprint). It tries up to 16 bearings, then places the `forest_ring` ruin template at the surface and records it.
  - It never builds over players' work (review 0.11.0): each candidate also passes Starfall's base check (§25.3) on the footprint plus eight blocks: no chunk with more than `starfall.inhabitedTicks` of inhabited time, no block entity that is not natural (`#firmages:starfall/natural_block_entities` or a structure container with its loot table), no `#firmages:starfall/player_blocks` from eight below to 16 above the floor, no FTB Chunks claim within `starfall.claimMarginChunks` (`WayshrineTutorial.siteBlocker`).
  - It places itself only on a new world: on a world older than three days (`WayshrineRules.TUTORIAL_MAX_WORLD_TICKS`, 72,000 ticks of game time) the start logs one INFO line and places nothing, so an old server world updating to this feature keeps its land untouched. `/firmages wayshrine tutorial` places one there on purpose (with the site checks), `tutorial here` without them. The other Wayshrines of an old world appear only in chunks generated after the update.
- **Files** (mod jar, `src/main/resources/data/firmages/`, all written by one generator, `dev/gen_wayshrines.py --check`):

| Path | Content |
|---|---|
| `structure/wayshrine/<type>.nbt` | the ruin, at most 11 by 8 by 11; the core in the bottom-layer centre with NBT `{type:"<type>"}`, facing north |
| `worldgen/structure/wayshrine/<type>.json` | jigsaw as above, terrain per the table (the seven ground types) |
| `worldgen/structure_set/wayshrine/<type>.json` | `tfc:climate` placement (the seven ground types) |
| `worldgen/template_pool/wayshrine/<type>.json`, `worldgen/processor_list/wayshrine.json` | template plus processors: mossy and cracked variants, `block_rot` only on `#firmages:wayshrine/rottable` (never the core) |
| `tags/worldgen/structure/wayshrine.json` | the seven ground types, for `/locate structure #firmages:wayshrine` and the pointer (the Wind Shrine is found through its isle, §28.4a) |
| `modonomicon/multiblocks/wayshrine_<type>.json` | the restored form, `modonomicon:dense`, `0` = core, `L` = the light, matchers `#firmages:wayshrine/<type>/<role>` |

  The generator writes the ruin and the restored form from one layout, so they cannot drift. The core must not sit in the exact middle layer (§7.8). TFC rock bricks of every rock count (`#firmages:wayshrine/any_rock_bricks`), so the local rock works. **Every block of a ruin template is a Dawn block** (review fix 0.12.0; ProgressiveStages has no structure locks and the ruins, the isle Wind Shrine included, can be reached from Dawn): the bars of the Swamp Altar, the Mountain Spire and the Wind Shrine are all missing from their ruins, and the Reef Altar's ruin keeps raw limestone where the restored form asks for coral blocks (a role's `ruin` stand-in). `gen_wayshrines.py --check` fails on any ruin block whose Age in `dev/age_map.toml` (`gen_stage_locks.AgeMap.age_of`) is not `dawn`.

### 28.5 Blocks and items

- **`firmages:wayshrine_core`** (BE `WayshrineCoreBlockEntity`): blast resistance 1200, destroy progress 0 in survival, pistons refuse it, and it has no survival item.
  - Blockstate: `phase` (`ruin`, `restored`, `lit`), `intact` (bool), `facing`.
  - The BE saves `type`, the offering stack and `prayerProgress`. It syncs `type`, `missing` (a count) and the offering to the client (vanilla BE sync), and the core's renderer draws the offering on top.
- **`firmages:wayshrine_stone`** ("Wayshrine Stone", Felix S1A): a block and item, one per lit Wayshrine. Blockstate `kind` = the eight types; item component `firmages:wayshrine` (type and position). Its line in the item's hover text is "From the Wind Shrine". It is a decorative block that drops itself, and the heart of every Sky Anchor (§29). It is never a relic.

### 28.6 Data formats (mod jar, overridable from `kubejs/data/firmages/`, reload listener `WayshrineDataLoader`)

`data/<ns>/firmages_wayshrine/type/<type>.json`:

```json
{ "structure": "firmages:wayshrine/volcanic_ring", "multiblock": "firmages:wayshrine_volcanic_ring",
  "min_age": "age_5", "light_key": "L", "ruin_beam": "#E07040" }
```

`data/<ns>/firmages_wayshrine/offerings.json` (the key is the Age in which the offering is laid; the highest key at or below the current Age applies):

```json
{ "age_1": "tfc:metal/double_ingot/bronze", "age_2": "firmages:meteoric_iron_ingot",
  "age_3": "firmages:starlit_iron_ingot", "age_4": "firmages:cloudsteel_ingot",
  "age_5": "firmages:aurora_steel_ingot", "age_6": "firmages:halo_steel_ingot" }
```

- **Shipped until M15 (review 0.11.0):** the celestial alloys have no recipe before M11, M13, M14 and M15, so the shipped file uses sky metal every Age can make: `age_3` `firmages:meteoric_iron_double_ingot`, `age_4` `firmages:meteoric_iron_sheet`, `age_5` and `age_6` `firmages:starsteel_ingot` (age_7 to age_9 fall back to age_6). The jar keeps this list as the fallback; since M15 the pack writes the alloy list above into KubeJS's last data pack (`kubejs/server_scripts/firmages/wayshrine_offerings.js`, `ServerEvents.generateData`) once every sky material is registered (`startup_scripts/sky.js`).

`data/<ns>/firmages_wayshrine/rite/<age>.json` (the lighting rite of that Age; the rite types and parser of §2.6 are reused, and keys refer to the type's multiblock):

```json
{ "rites": [ { "type": "blockstate", "key": "L", "property": "active", "value": "true",
               "hint": "firmages.wayshrine.rite.power" } ] }
```

- Shipped rites:
  - age_1 and age_2: kindle the light (`blockstate L lit`; `#firmages:wayshrine/lights` holds candles, TFC lamps and IE electric lanterns);
  - age_3: the light plus `sky`;
  - age_4 and age_5: power the light (`blockstate L active`, an electric lantern on a charged capacitor brought along);
  - age_6: the light plus `players_praying` 2 (met at once when fewer players are online, as at the shrine).
- A wrong item, an unknown Age key or an unknown rite type rejects that file with an ERROR, as in §2.6.
- **SavedData `firmages_wayshrines`** (overworld storage) holds, per Wayshrine: `pos`, `type`, `phase`, `intact`, `litAge` (info only), `litAt` (game time) and `stoneGiven`. It also holds the tutorial flag and the pointer cache (found and lit structure positions). A core registers itself in `onLoad` (server); a core removed by force drops its record with a WARN.

### 28.7 Flow (`wayshrine/WayshrineService`, server only)

| Step | Logic |
|---|---|
| Ruin | `ShrineMultiblocks.check(level, core, multiblock)` fails (any rotation). Right-click with an empty hand sends `firmages:shrine_preview` with the type's multiblock, the core as anchor and the rotation from `facing`, plus the actionbar line "Seven blocks are missing." (words up to twelve) |
| Restored | The pattern validates: `phase=restored`, and the faint beam stays. Before `min_age`: "This Wayshrine waits for a later Age." |
| Offered | Right-click with the offering of the current Age takes exactly one item. A wrong item is refused with the expected item's name. Sneak and right-click with an empty hand returns it until the prayer ends |
| Rite | The Age's rites hold (checked on interaction and every 20 ticks while a player is within 16 blocks) |
| Prayer | As at the shrine: sneak and hold right-click on the core; `wayshrine.prayerSeconds` divided by the number of praying players, at least 2 s; refusals on the actionbar once a second |
| Lit | The offering is consumed: `phase=lit`, `litAge` set. Server-side particles (end-rod burst) and a sound (`firmages:wayshrine.lit`), as §7.4 does (the `shrine_fx` payload is still unbuilt, §11). One chat line to the fellowship: "The Wind Shrine burns again." The blessing index is rebuilt (§29.5). First lighting only: the Wayshrine Stone drops 1.2 blocks above the core with no velocity, unlimited lifetime and invulnerability (as the Gathering's relics, §7.5) |

Validation runs every 200 ticks while the chunk is loaded, and on the next tick after a break, place or explosion inside the box of a known record (a cheap AABB index first), never every tick. A change of `intact` rebuilds the index.

### 28.8 Beam, finding, Jade

- **Beam:** the core's renderer shares the heart's beam code (`ShrineHeartRenderer`, `BeaconRenderer.renderBeaconBeam`, height 1024, infinite render box).
  - Ruin and restored: radius 0.05, the type's `ruin_beam` at alpha 0x40.
  - Lit and intact: radius 0.08, the colour of the latest awakened tier (from `shrine_sync` and `age_status`), alpha 0x90.
  - Lit but broken: no beam.
  - The client toggle `effects.idleBeam` hides them all. Nothing is drawn for unloaded chunks.
- **Finding:** from age_1, a plain right-click on the home heart adds the line "Caelum: A Wayshrine sleeps to the north-east, about 1,200 blocks." A plain right-click on a lit Wayshrine shows the same pointer on the actionbar, without Caelum's name.
  - The pointer first looks at the cache in `firmages_wayshrines`, then searches `#firmages:wayshrine` within `wayshrine.pointerRadiusChunks` (256) in blocks, skipping referenced (lit) structures. The tutorial Wayshrine comes from the cache.
  - Not `ChunkGenerator.findNearestMapStructure`: its radius counts placement cells, so it searched far beyond the radius and generated structure starts up to 200,000 blocks away (a two-minute tick on the 0.10.0 copy). `WayshrinePointer.search` lists the one candidate chunk of every cell within the radius, sorts them by distance, drops those the placement refuses (`isStructureChunk`: climate, frequency) and only then asks the structure manager; the first valid start wins.
  - Lighting calls `level.structureManager().addReference(start)`, which marks the chunk unsaved, so a lit Wayshrine is skipped like an explorer-map target.
  - There is one search per player per 10 s. None found: "No Wayshrine sleeps within reach."
- **Jade** (`FirmagesJadePlugin`, synced state only): "Wayshrine: Wind Shrine", "Ruined: seven blocks missing", "Offering: Meteoric Iron Ingot", "Lit, carries the blessings within 24 blocks", "Broken: repair it to wake it".

### 28.9 Network, config, commands

- **Network:** no new payload; `shrine_preview` is reused unchanged.
- **Config:** `wayshrine.radius` (24, 8..64), `wayshrine.prayerSeconds` (5), `wayshrine.pointerRadiusChunks` (256, 8..512), `wayshrine.tutorial` (true).

| Command (`/firmages wayshrine ...`) | Purpose |
|---|---|
| `list` | All records: position, type, phase, intact, lit Age, stone given |
| `status [pos]` | The nearest core within 32 blocks or at `pos`: missing blocks per role, offering, rite state, prayer |
| `locate <type>` | The nearest structure of that type (the pointer search, known ones included) |
| `tutorial [here]` | Places the tutorial Wayshrine again (debug) |
| `light <pos>` | Needs `debug.allowSimulate`: completes the offering, rite and prayer of a restored Wayshrine |
| `reset <pos> [stone]` | Back to `ruin` or `restored` by the world; with `stone` also clears `stoneGiven` |
| `stone <pos>` | Admin repair: drops that Wayshrine's stone again |
| `census [radius [spacing separation]]` | The ground Wayshrine starts within `radius` blocks (4,000) of the world spawn, per type, each logged with its position: the pointer's candidates and the structure manager's start check, which for a chunk without data asks the structure (biome, height) and generates nothing (acceptance 2). With a spacing, as if the structure sets had it (the placement re-encoded through its codec, climate and salt kept), to compare densities on one seed |

### 28.10 Tests

- **U** `WayshrineDataTest`: the shipped types, offerings and rites parse without warnings; the offering key falls back (age_8 uses age_6); bad files are rejected.
- **U** `WayshrineFilesTest`: per type one core in the bottom-layer centre and not in the middle layer; key `L` present; at most 11 by 8 by 11; every tag file exists; `gen_wayshrines.py --check` is clean.
- **G** `WayshrineGameTests.restoreAndLight` (a stand-in type from vanilla blocks in `src/gametest/resources`):
  - the ruin refuses and sends one preview payload with the right rotation; the repair gives `restored`;
  - below `min_age` it refuses;
  - a wrong offering is refused, the right one takes one item, and sneaking returns it;
  - an unmet rite blocks the prayer with its hint; the prayer lights it;
  - exactly one Wayshrine Stone drops, invulnerable and never despawning; a second lighting drops none;
  - BE and SavedData NBT round trip.
- **G** `WayshrineGameTests.tutorialPlacement`: on a flat test world the tutorial Wayshrine is placed 300 to 800 blocks from the shared spawn on land, once; a second start places none.
- **G** `WayshrineGameTests.tutorialSiteKeepsClearOfPlayers`: on untouched land a plank, a placed chest or a chunk with inhabited time each make the site unfit; the free site passes. **U** `WayshrineRulesTest.tutorialOnlyOnNewWorlds`: the world-age gate.
- **G** `WayshrineGameTests.blessingsAtWayshrine`: see §29.8.
- **P** `selftest wayshrines`: the eight structures and sets load with `tfc:climate`; every template exists; `locate` finds each type within 256 chunks on the test seed or reports the type as out of reach.
- **M:** beams from 150 blocks, ghost alignment on a rotated ruin, a ruin on a steep slope, the Reef Altar under water.

### 28.11 Acceptance criteria

1. On every new world the tutorial Wayshrine stands 300 to 800 blocks from spawn, on land and away from anything players built; an existing world older than three days gets none by itself.
2. On the pack server, a new world has 5 to 16 more Wayshrines within 4,000 blocks of spawn on every one of 20 test seeds, and 7 to 11 on at least half of them (`/firmages wayshrine census 4000` on fresh copies; `dev/scan_regions.py` would need 250,000 generated chunks per seed). Met in round C2 at 72 / 27: 20 of 20 in 5 to 16, 11 of 20 in 7 to 11 (§28.4). The first target, 7 to 11 on 16 of 20, is out of reach for any spacing (best 11 of 20). No Wayshrine floats or is buried more than two blocks on a slope.
3. Every type validates its restored form from the generator's own layout, and the ruin never validates.
4. The ghost preview, offering, rite, prayer and lighting work as in §28.7 with one player and with two (half the prayer time).
5. A lit Wayshrine gives the awakened tiers' blessings and Hearthward within 24 blocks. A new Age adds its tier without a visit. A broken pattern stops both within one validation, and the repair restores them.
6. The Wayshrine Stone drops once per Wayshrine, never despawns, and survives lava and explosions.
7. There is no teleport, no C2S payload, no FE, and no item of a later Age in any restored form below its `min_age`.

## 29. Sky Anchors and the blessing sources

### 29.1 Purpose

A built anchor that carries the home shrine's blessings to any place in any dimension (Moon, Mars, an outpost, the factory), from the Space Age. It is the in-world successor of `shrine.blessings.everywhere`, which is **removed** (§29.7; Felix S4A, built in M16). Wayshrines are found places in the overworld for age_1 to age_6; anchors are placed by the fellowship from age_7, in every dimension.

### 29.2 Player loop

In the Space Age the fellowship crafts a Sky Anchor around a Wayshrine Stone, forges blessing shards in the Lens Crown (one per tier, from the ring material of that tier and an alloy), and slots the shards they want. A Moon base with an anchor holding the shards of tiers 0 and 2 gets Hearthward and Iron Will.

### 29.3 Blocks and items

- **`firmages:sky_anchor`** (BE `SkyAnchorBlockEntity`): nine shard slots, one per tier 0..8.
  - Right-click with a shard puts it into its tier slot. A second shard of the same tier is refused: "This anchor holds that shard already."
  - Sneak and right-click with an empty hand takes the highest shard back.
  - Breaking it (pickaxe) drops the anchor and its shards.
  - Blockstate `active`. Its BER shows the shards circling the top in their tier colours, and an active anchor shows the lit-Wayshrine beam.
- **`firmages:blessing_shard`** ("Shard of <blessing>", for example "Shard of Iron Will"; Felix S1A): one item with the component `firmages:tier` (0..8). EMI lists the nine variants (`EmiRegistry.addEmiStack`).
- **SavedData `firmages_anchors`:** `dimension`, `pos` and `tiers` (bit mask). It is written on place, change and break, and checked against the block on load.

### 29.4 Recipes (pack, KubeJS, `kubejs/server_scripts/recipes/age_7/space_age.js`; hand-off)

- **Sky Anchor** (shaped): four `#c:plates/desh`, one `#c:plates/steel`, one `firmages:voidglass`, one `firmages:wayshrine_stone` (any kind) in the middle, one `firmages:void_steel_ingot`, one `#c:storage_blocks/desh` as the base. `ad_astra:steel_plate` is hidden, so it is not used. One stone per anchor ties the anchors to the restored Wayshrines (8 to 12 per world, three to five anchors expected).
- **Blessing shard of tier N** (`firmages:starforge`, chord `anchor_chord` = the Information Lens alone):
  - inputs: one `#firmages:anchor/ring_N` and one alloy;
  - the alloy is Starlit Iron for tiers 0..3, Cloudsteel for tier 4, Aurora Steel for 5, Halo Steel for 6, Void Steel for 7 and Quasar Alloy for 8;
  - the result is `{"id": "firmages:blessing_shard", "components": {"firmages:tier": N}}`.
- **Item tags** `#firmages:anchor/ring_0` .. `ring_8` mirror the ring block tags of §7.8 (`#firmages:shrine/hearth_stones`, `#c:storage_blocks/bronze`, `#c:storage_blocks/wrought_iron`, `#firmages:shrine/source_gem_blocks`, `#firmages:shrine/heavy_engineering`, `#firmages:shrine/red_steel_blocks`, `#firmages:shrine/fluix_blocks`, `#firmages:shrine/desh_blocks`, `#firmages:shrine/calorite_blocks`). They are generated from the same list by `dev/gen_stage_locks.py`.
- **Ages:** the shard is `age_7`. Tiers 0..7 can be made from age_7, and tier 8 from age_8 (its Quasar Alloy). The ingredient sets are disjoint because the ring materials differ.

### 29.5 Rules and the blessing sources (`shrine/BlessingSources`, pure, and `BlessingIndex`)

- **When an anchor acts:** while age_7 is open and the home shrine is intact with at least one awakened tier. It gives the blessings of the tiers it holds **and** that are awakened, within `anchor.radius` (24) blocks, in its own dimension. Hearthward (the `sanctuary` flag of tier 0) acts only with the tier-0 shard.
- **Keystone lending (§7.4) and the Gathering (§7.5) change nothing.** A lent relic keeps its record, so `intact` and the awakened tiers stay, and so do the anchors. A shard is never a relic: no plinth takes it, it never satisfies `relic_returned`, the Gathering does not count it, and the Ultimate Singularity does not accept it. The anchor has no prayer, grants nothing and is no second shrine.
- **Shrine maintenance (§17.4)** changes nothing as long as the rings stand. A broken ring silences the heart, the Wayshrines and the anchors alike; the existing "the shrine is broken" announcement covers it.
- **Sources:** `record Source(Kind kind, ResourceKey<Level> dimension, BlockPos pos, int radius, int tierMask, boolean sanctuary)` with the kinds `HEART`, `WAYSHRINE` and `ANCHOR`.
  - Heart: radius `sanctuaryRadius(awakened)` (§7.9, unchanged), mask all awakened tiers.
  - Lit and intact Wayshrine: radius `wayshrine.radius`, mask all awakened tiers, sanctuary when tier 0 is awakened.
  - Anchor: radius `anchor.radius`, mask its shards AND the awakened tiers, sanctuary with the tier-0 shard.
- **`BlessingIndex.current()`** is a volatile immutable list. It is rebuilt on shrine validation, a Wayshrine phase or `intact` change, an anchor change, an Age change and a data reload, and it is empty while the shrine is not intact.
- **`Blessings.apply(p)`:** the mask is the OR of every source in the player's dimension within its radius. `blessingsOf(mask)` replaces `blessingsOf(awakened)`, and `near` means the mask is not 0. `Blessings.onSpawnCheck` refuses a natural monster spawn inside any source with `sanctuary`. With a few dozen sources at most, the loop is trivial; it runs once a second as now.

### 29.6 Rendering, Jade

- `client/SkyAnchorRenderer`: shards circling, plus the beam when active.
- Jade: "Sky Anchor: four shards", "Quiet until the Space Age", "Quiet: the shrine is broken", and the list of blessings in force.

### 29.7 Network, config, commands

- **Network:** none.
- **Config:** `anchor.radius` (24, 8..64). The key `shrine.blessings.everywhere` is **removed**. If an existing file still holds it, the mod logs one WARN: "shrine.blessings.everywhere was removed: Sky Anchors carry the blessings away from the shrine from the Space Age on." The shipped default was false, so the pack itself is unaffected; a server that ran it true loses team-wide blessings in age_1 to age_6. The removal and the WARN line landed with round C1, the anchors with M16b.

| Command | Purpose |
|---|---|
| `anchor list` | Dimension, position, tiers, active |
| `anchor status [pos]` | Shards, awakened mask, blessings in force, reason when quiet |

### 29.8 Tests

- **U** `BlessingSourcesTest`: the union of masks; the radius edge at 24.0 and 24.01; another dimension ignored; sanctuary per kind; empty while not intact.
- **G** `WayshrineGameTests.blessingsAtWayshrine` (an age_3 shrine with three tiers): modifiers at 20 blocks and none at 30; a new Age adds its tier without interaction; a broken pattern removes them within one validation and the repair restores them; a broken home ring silences the Wayshrine; `refusesSpawnAt` inside and outside.
- **G** `AnchorGameTests`:
  - `anchorInAnyDimension`: an anchor in `firmages:origin` with tiers 0 and 2 gives only those blessings and Hearthward; an unawakened tier is inert; the anchor is quiet below age_7.
  - `anchorKeepsThroughLending`: age_8 lend and return, then the age_9 Gathering; the blessings are unchanged.
  - `anchorShards`: insert, duplicate refused, take back, breaking drops all, NBT round trip.
- **G** `ShrineGameTests.blessingEffects` is rewritten: its `everywhere` part becomes "an anchor restores them far away".
- **M:** shards circling on the Moon.

### 29.9 Acceptance criteria

1. On the Moon an anchor with the shards of tiers 0 and 2 gives Hearthward and Iron Will within 24 blocks and nothing at 25; tier 2 without its shard is absent.
2. A shard of a tier that is not awakened does nothing. An anchor before age_7 (after a revoke) is quiet.
3. Lending the Arcane Keystone in age_8 and the Gathering in age_9 leave every anchor's blessings unchanged. A broken ring stops them, and the repair restores them.
4. A player in reach of several sources gets the union of their tiers, each blessing once.
5. The anchor, the shards and the SavedData survive save and load, and breaking drops everything.
6. Every shard recipe is craftable in its Age: tiers 0..7 in age_7, tier 8 in age_8 (P `selftest crown` resolves each input).

### 29.10 Implementation notes (M16b, branch `c2/sky-m15`, 2026-10-16)

- Package `anchor/`: `AnchorRegistry` (block, item, BE, component `firmages:tier` as an int 0..8), `AnchorRules` (pure: mask, highest, active mask, status `ACTIVE`, `BEFORE_AGE`, `BROKEN`, `EMPTY`, `SLEEPING`), `AnchorService` (provider `AnchorService::sources` at `BlessingIndex.registerProvider`; a grant or revoke of age_7 marks the index stale once a second), `AnchorSavedData` (`firmages_anchors`; a record whose loaded chunk holds no anchor is dropped when the index rebuilds; a loaded BE puts its own record back), `SkyAnchorBlock`(+BE), `BlessingShardItem` (a stack without the component counts as tier 0), `client/SkyAnchorRenderer`, `command/AnchorCommands`.
- The anchor's source carries its full shard mask; `BlessingIndex` cuts every mask to the awakened tiers, so an unawakened shard is inert without a second rule. Sanctuary follows the tier-0 shard.
- EMI: the nine shard variants come from the creative tab (Ingredients), which EMI indexes; no `addEmiStack` call is needed.
- Jade adds a fifth line, "Quiet: its shards wait for their rings to awaken", for an anchor whose shards are all of unawakened tiers.
- The nine `#firmages:anchor/ring_N` item tags are written by `dev/gen_stage_locks.py` (`ANCHOR_RINGS`) from the ring block tags; the shard recipes are the M12 starforge files `blessing_shard_0..8`.
- Self-test `wayshrines` gained `anchor_shards` (nine anchor_chord recipes, nine non-empty ring tags) and `anchor_records`.

## 30. Drifting Isles (static)

### 30.1 Purpose

Isles of pale skystone above the clouds, the only source of firmament crystal. Under V4A Cloudsteel takes crystal directly, so **the isles are on the main road from the Industrial Age** (age_4), reached by airship or jetpack. There is no metal, no ore and no FE on an isle.

### 30.2 Player loop

From the ground the fellowship sees the isles glow at night: their crystal clusters shine through the clouds. In the Industrial Age they fly up by hot-air airship or jetpack, break the clusters and, on medium and large isles, the firmament lode, which re-forms at once. Later a Create drill (from the Iron Age's tech) or, from the Space Age, the Digital Miner works the lode, and the crystals go home by airship cargo or an AE2 quantum link.

### 30.3 Height band and placement

- TFC's preset uses `"settings": "minecraft:overworld"` [verified, TFC 4.2.11 `world_preset/overworld.json`], so the build range is Y -64..319 and the vanilla cloud layer sits at Y 192. No pack override of the overworld `dimension_type` or noise settings exists under `kubejs/data` [verified].
- **Band:** top surface at Y 228..272; the underside tip at least at Y 200 (above the clouds); crowns and trees below Y 300 (19 blocks of headroom for an airship envelope docked on top). At each start the generator reads `getBaseHeight(WORLD_SURFACE_WG)` at the centre and keeps the isle at least `min_clearance` (40) above the terrain. A start with no room below Y 272 (high TFC mountains) is skipped.
- **Generation route: a worldgen structure** with its own `Structure` type `firmages:drifting_isle` and one code-shaped `StructurePiece` per isle (a noise-shaped inverted cone, no templates for the body).
  - It is deterministic per seed, `/locate structure` works for operators, it is chunk-safe (pieces write only into the chunk being generated), and `spawn_overrides` keep the isle free of mobs.
  - `TFCChunkGenerator` delegates `createStructures` to vanilla and calls `StructureStart.placeInChunk` per step in its own `applyBiomeDecoration` [verified, javap]. The pack's `tfc_ruins` 1.0.0 already ships plain jigsaw and `random_spread` sets on TFC biome tags, so a datapack structure set generates under TFC. This is a scan check, not a blocking PoC: `selftest isles` locates three isles on the test seed.
- **Density:** structure set `random_spread`, spacing 20 chunks, separation 6. That gives on average one **cluster** of one to four isles per 320 by 320 block cell, over every TFC overworld biome (`#firmages:has_structure/drifting_isle`, oceans included).
- **Size mix:** small r 8..12 (weight 6), medium r 13..18 (weight 3), large r 19..24 (weight 1). Cluster satellites are always small.

```json
// data/firmages/worldgen/structure/drifting_isle.json
{ "type": "firmages:drifting_isle", "biomes": "#firmages:has_structure/drifting_isle",
  "step": "top_layer_modification", "terrain_adaptation": "none",
  "spawn_overrides": { "monster":  { "bounding_box": "full", "spawns": [] },
                       "creature": { "bounding_box": "full", "spawns": [] },
                       "ambient":  { "bounding_box": "full", "spawns": [] } },
  "top_y": { "min": 228, "max": 272 }, "min_clearance": 40, "cluster": { "min": 1, "max": 4 },
  "radius": { "small": [8, 12], "medium": [13, 18], "large": [19, 24] }, "weights": { "small": 6, "medium": 3, "large": 1 },
  "crystal_clusters_per_100_top": 3, "lode_from": "medium", "trees_max": 2, "wind_shrine_chance": 0.25 }
// data/firmages/worldgen/structure_set/drifting_isles.json
{ "structures": [ { "structure": "firmages:drifting_isle", "weight": 1 } ],
  "placement": { "type": "minecraft:random_spread", "spacing": 20, "separation": 6, "salt": 1874201551 } }
```

- **Existing worlds:** structures generate only in new chunks, so an explored world has no isle within its explored land.
  - When the world reaches age_4, and at every server start in age_4 or later, `isles/IsleFallback` checks for a known isle start or fallback record within `isles.fallbackRadius` (600) of the heart (structure search over the set, cached in `firmages_isles`).
  - If there is none, it records one fallback cluster at 300 to 600 blocks from the heart. It registers the cluster with `DeferredSites` (§24.4), and once the box chunks are loaded at FULL it places the same piece shapes with the same height rules, at most 64 blocks per tick.
  - The fallback body is the same skystone, which refuses mob spawns by itself (`isValidSpawn` false), so the missing `spawn_overrides` change nothing.
- **The Wind Shrine** (Felix S6B, §28.4a) stands on the largest isle of one cluster in four (`wind_shrine_chance` 0.25 in `drifting_isle.json`) and always on the fallback cluster. The isle piece places its ruin template after the body; the isle's height rules leave room for it below Y 300.

### 30.4 Blocks and the isle body

| Id | Role | Age map |
|---|---|---|
| `firmages:skystone` | body and top surface; pale stone that lets sky light through; no mob spawns on it | Dawn (building block, drops itself) |
| `firmages:firmament_crystal_cluster` | one-time deposit on the underside, in rim cracks and in a hollow; light 10; drops one `firmament_crystal` | block in `firmages:age_blocks/age_4` |
| `firmages:firmament_lode` | one per medium or large isle, set into the centre of the underside; re-forms (§30.5) | block in `firmages:age_blocks/age_4` |
| `firmages:firmament_crystal` | item | age_4 |

- There is no mossy variant (it was cosmetic only). Weathering, if a look is wanted, is a processor on the top layer.
- **No shadow on the ground:** skystone keeps face culling but returns `propagatesSkylightDown = true` and `getLightBlock = 0`. Land under an isle keeps sky light 15: no dark patch, no spawns, and crops grow. Whether lighting and AO look right with occluding faces is **[PoC]**; the fallback is `noOcclusion` on the underside shell only.
- **No collapse, no landslide:** skystone is in neither `tfc:can_collapse` nor `tfc:can_landslide`. Trees (zero to two, TFC species of the climate below) stand in an enclosed pocket of TFC dirt.
- **One ore model:** the isle carries no ore. TFC vein features only replace `tfc:rock/raw/*`, and skystone is none of them. Tag `#firmages:isle/body` = skystone, crystal cluster and lode (used by Starfall, §25.3).
- **Before age_4** both crystal blocks are in `age_blocks/age_4`, so ProgressiveStages' ore disguise shows them as skystone and OreGuard (§5) stops machines. The clusters still glow at night, which makes the isles visible as lights from the ground.

### 30.5 The re-forming lode (automation)

- **Re-forming:** breaking the lode by any means that drops items (player, fake player, Create drill, or the Mekanism Digital Miner from age_7) yields `isles.lodeYield` (default 2) crystal, and the lode stands again at once. `Block#onRemove` (not by piston; new state air) queues the position in SavedData `firmages_isles`, and the next server tick sets the lode back.
- **Hardness 50** (obsidian is 50), so the drill's rotation is the work.
- **Immune** to explosions, the Wither and the Dragon (the immune tags of consecrated blocks). Pistons and Create contraptions cannot move it (`PushReaction.BLOCK`, `create:non_movable`).
- **[PoC]** Create 6.0.10's `BlockBreakingKineticBlockEntity` and Mekanism's Digital Miner both reach `onRemove`, and the lode re-forms without a duplicate drop.
- **Age by Age:** the Create drill automates the lode in age_4 to age_6, and the Digital Miner from age_7.
- **Transport home** is the players' own logistics: airship cargo, or from age_6 an AE2 quantum link with the isle chunk claimed and force-loaded by FTB Chunks.

### 30.6 Mobs and comfort

There are no spawns inside an isle's box, and no new mob, hazard or wind push. Phantoms at Y 250 (vanilla insomnia) are the only fliers near the isles if the pack keeps `doInsomnia` **[M check]**.

### 30.7 Reaching them in the Industrial Age

- **Means:** Create Aeronautics airships (`aeronautics`, `simulated` age_4) and the Create Jetpack (`create_jetpack` age_4) [verified, `dev/age_map.toml`]. Levitite blend is `age_5` [verified], so age_4 airships fly on hot air and propellers.
- **Air pressure:** Aeronautics' lift and thrust fall with Sable's air pressure: pressure = base_pressure × Bezier(y), loaded from the data-driven `dimension_physics` reload listener. By default it falls roughly as exp(-0.004 × (y - sea level)), so at Y 250 lift is about half of sea level: reachable, not a hard ceiling [verified, javap Sable 2.0.6 and Aeronautics 1.3.2].
- **[PoC, blocking]** An age_4 hot-air airship climbs from Y 90 to Y 275 and holds there, and a copper-backtank jetpack climbs from Y 120 to Y 240 and back. If the airship fails, first ship a Sable `dimension_physics` override for the overworld (pack data) that flattens the curve above Y 192. Only then lower `top_y` in data, never below Y 210.
- **[PoC]** On three seeds the nearest cluster lies within 400 blocks of world spawn; otherwise lower `spacing` to 16.

### 30.8 Rendering and sound

- No renderer: the clusters' light and the vanilla clouds do the work.
- Breaking the lode plays the amethyst cluster sound; re-forming plays a soft chime (`firmages:lode.reform`, mapped onto a vanilla event).

### 30.9 Network, config, commands

- **Network:** none.
- **Config:** `isles.lodeYield` (2, 1..8), `isles.fallback` (true), `isles.fallbackRadius` (600).

| Command | Purpose |
|---|---|
| `isles locate [radius]` | The nearest isle cluster start (default 2,048 blocks) with its isles: size, radius, top, tip, lode, Wind Shrine, trees, clusters |
| `isles shrine` | The nearest Wind Shrine of an isle within the pointer radius (code clusters and starts whose roll is true) |
| `isles list` | The clusters placed by code, their isles (placed, waiting, skipped), the re-form queue and the placer jobs |
| `isles lode <pos>` | The re-form queue state |
| `isles fallback status\|check\|place [pos]` | The fallback record and the known start; runs the check now; records a fallback cluster now (debug) |
| `isles place <pos>` | Records an operator cluster with the Wind Shrine there, placed once its chunks load (no site checks) |
| (vanilla) `/locate structure firmages:drifting_isle` | For operators |

### 30.10 Tests

- **G** `IsleGameTests`:
  - `shapeHasNoOre`: a generated piece in a test level holds only skystone, crystal blocks and the TFC-dirt stand-in pocket, and no `firmages:age_blocks/*` block other than the two crystal blocks.
  - `isleKeepsSkyLight`: sky light 15 on the floor under a radius-12 piece.
  - `lodeReforms`: breaking by player and by fake player gives two crystals, the lode is back on the next tick, and there is no duplicate.
  - `lodeLockedBeforeAge4`: OreGuard cancels a fake-player break.
  - `fallbackPlacesWhenLoaded`: the fallback places nothing while the box is unloaded and places the cluster after a forceload, at most 64 blocks per tick.
- **P** `selftest isles`: three isles located on the test seed; the structure set loads.
- **M:** flights to the isles (PoC §30.7), the look from the ground at night.

### 30.11 Acceptance criteria

1. No isle has an ore block, a monster or a dark ground patch below it.
2. On three new seeds the nearest cluster lies within 400 blocks of spawn. On an existing world in age_4 a fallback cluster exists within 600 blocks of the heart.
3. An age_4 airship and a jetpack reach the band (PoC §30.7).
4. Firmament crystal has exactly one source (§24.7).

### 30.12 Implementation notes (M13, branch `c2/sky-m13`, 2026-10-16)

- **Package `isles/`.** Pure: `IsleSettings` (the JSON fields, codec in `IsleCodecs`), `IsleLayout` (cluster plan from world seed and start chunk, heights from a terrain sampler), `IsleShape` (the body of one isle), `IsleSpec`, `IsleSize`. World: `DriftingIsleStructure` (type `firmages:drifting_isle`), `IslePiece` (piece type `firmages:drifting_isle`, the spec in its NBT), `IsleWriter` (blocks, trees, the Wind Shrine), `IsleSearch` (starts near a point, the way the Wayshrine pointer searches), `IsleService` (lode queue, fallback, placer, Starfall probe), `IsleSavedData` (`firmages_isles`), `IsleRegistry`, `SkystoneBlock`, `FirmamentLodeBlock`, `LodeYieldFunction` (`firmages:lode_yield`). Commands `command/IsleCommands`, suite `command/IsleSuite` (`selftest isles`, also in `all`).
- **One plan, three writers.** A start, the fallback placer and the GameTests resolve the same `IsleSpec` list, and `IsleShape.emit` writes a column range: the piece passes its chunk box, the placer takes all blocks as steps of at most 64 per tick. The cluster's horizontal plan and the Wind Shrine roll are pure functions of the world seed and the start chunk, so the Starfall probe (`IsleSearch.plannedOver`) and the pointer know an isle without loading anything; only the heights need the generator (`getBaseHeight(WORLD_SURFACE_WG)`, sampled at the centre and 16 points under the body).
- **Shape.** Edge radius `r × (0.88 + three sine terms)` (rounder on the shrine's isle), underside `depth × (1 − t)^1.6` with `depth = round(1.15 r) + 4`, a dome of at most one block, the rim one block lower. Medium and large isles carry a hollow open to the top with one to three clusters on its floor; the rest of the clusters sit in rim cracks (35 %) and under the body. Tree pockets are TFC podzol dirt under podzol grass (TFC 4.2 has no loam), trees are the TFC configured features `tfc:tree/{spruce,pine,white_cedar,douglas_fir,aspen,birch}`. The shrine's isle keeps its top flat within seven blocks of the centre and has a radius of at least ten.
- **Wind Shrine (S6B).** `IslePiece.postProcess` calls `WayshrinePlacement.placeIsleShrine` with the chunk box as `clip` on the main isle when the roll is true; the core sits in the top surface at the centre. The fallback cluster always carries it. `WayshrinePointer.nearest` adds the isle shrines from age_4 (`IsleService.nearestShrine`: code clusters, then the nearest starts whose roll is true, only those are loaded to `STRUCTURE_STARTS`); an isle shrine wins over a ground Wayshrine only when it is nearer. `wayshrine locate wind_shrine` uses the same search. The tutorial Wayshrine never lands on an isle top.
- **Lode.** `onRemove` queues the position when the new state is air or a fluid and no piston moved it; the next server tick sets the lode back with the chime (`firmages:lode.reform`). A block set in its place stays (operator fills). The drop is the loot table with `firmages:lode_yield`; no silk touch path, so the lode never drops itself.
- **Age gate.** `[ore_blocks]` disguise: cluster and lode look like skystone and drop skystone before age_4 (ProgressiveStages for players, OreGuard for fake players and machines). A player can break a disguised lode by hand before age_4 and gets skystone; it re-forms like after age_4 (harmless: skystone is a Dawn building block). The isles are hard to reach before the Industrial Age but not out of reach (Create lifts, pillaring), so nothing on them may be a later block: the Wind Shrine ruin holds Dawn blocks only (§28.4).
- **Fallback.** Checked once per server start, on the first tick divisible by 100 in age_4 or later (`isles.fallback`). The site is 300 to 600 blocks from the heart (else the world spawn), at least 96 blocks plus the isle radius from the world spawn, not over an FTB Chunks claim; a fallback whose main isle meets a block when its chunks load picks a new site. Satellites that meet a block are skipped. Players that the world spawn or a respawn places on an isle top are set down on the ground below (first join and respawns without a bed).
- **Config validation.** `top_y` must lie within 208..276 (tip above Y 200, 24 blocks of headroom below Y 300), radii within 4..32, cluster within 1..8.
- **Tests.** U: `IsleLayoutTest` (5); `gradlew build`: 198 unit tests green. G (`IsleGameTests`): `shapeAndSkyLight` (§30.10 `shapeHasNoOre` and `isleKeepsSkyLight` in one: a radius-11 isle written chunk box by chunk box, sky light 15 three blocks under the body), `lodeReforms` (debug player and fake player, two crystals each, back within two ticks, no duplicate, an operator's block stays), `lodeLockedBeforeAge4`, `fallbackPlacesWhenLoaded` (with the Wind Shrine's core and the pointer before and from age_4), `pieceShrineByChunks`; "All 87 required tests passed". Copy boot on a fresh TFC world: an isle cluster 56 blocks from spawn, the Wind Shrine generated and registered 264 blocks out, census of the main isle without any ore, two TFC trees grown (`dev/poc-results.md`, M13).
- **Open (M).** The flights of §30.7 (jar facts in `dev/poc-results.md`: no altitude cap in Create Jetpack, Sable or Aeronautics; Sable pressure about 0.43 at Y 275), the look from the ground at night, skystone lighting and AO.

## 31. The Launch Complex and the Origin Archipelago

### 31.1 Purpose

**Launch Complex (age_7 to age_8):** unmanned probes are the only source of voidglass (age_7) and deep-space samples (age_8). Ad Astra stays the crewed trip and the only source of desh, ostrum and calorite. Under V4A voidglass goes into Void Steel (the age_7 alloy) and the Space Lens, so **the complex is main road in the Space Age**, before the Star Chart.

**Origin Archipelago (post-game):** after `finale_won`, the nine Age islands of The Origin take living samples of their Age and grow into small dioramas. Nothing in the pack waits on it.

### 31.2 Player loop

**Launch Complex.**
1. In the Space Age the fellowship builds an 11 by 11 complex: a pad with the probe cradle, a gantry tower with the launch control, a flame trench, and one to four fuel ports.
2. They load the reusable probe and a sampler module and pipe in diesel, biodiesel or kerosene.
3. The probe climbs on a flame trail. The flight runs on fuel, and the throttle trades fuel for time.
4. The probe returns under canopies with four voidglass.
5. In the Quantum Age a deep-space module brings samples.

**Archipelago.** After the finale the fellowship brings saplings, seeds, a leashed animal, a working machine and four alloy ingots to each island's plinth. The island grows in three steps, and a completed island lights its beam at the altar.

### 31.3 Launch Complex multiblock (`firmages:launch_complex`, Modonomicon dense)

- 11 by 11 by 20. The bottom layer is the pad level, with `firmages:probe_cradle` as key `0` in its centre, so the ghost anchors like the shrine's (`PreviewBridge` anchors at the bottom layer).
- **Pad:** 5 by 5 around the cradle. Its rim cells take `#firmages:launch/rim` = `#firmages:launch/pad` (IE concrete, Mekanism steel casing) plus `firmages:launch_port`.
- **Tower:** a 3 by 3 gantry tower on one side up to the top (`#firmages:launch/frame`: IE steel scaffolding, Create metal girders), with `firmages:launch_control` at its foot.
- **Free cells:** every other cell is `_` (any block), so pipes and decoration fit. Modonomicon's ` ` means AIR [verified, javap `DenseMultiblock`] and is not used.
- **Checked in code after the pattern validates:**
  - the port count is 1..4;
  - **the trench:** a 3-wide, 3-deep air channel under the pad centre that runs to the pad edge and opens to the side, with walls and floor from `#firmages:launch/trench` (TFC fire bricks, IE concrete).
- All ids are checked against `dev/data/registry.json`. The control shows the ghost preview on right-click, like the shrine heart.
- Ghost alignment with the real build is **[PoC]** (M).

### 31.4 Probe, modules, fuel

| Item | Recipe (pack, KubeJS) | Age |
|---|---|---|
| `firmages:probe` (reusable hull, never lost) | Ad Astra and Mekanism parts | age_7 |
| `firmages:sampler_module` (consumed per flight) | Halo Steel (`#firmages:celestial_alloy/age_6`) plus parts | age_7 |
| `firmages:deep_space_module` (consumed per flight) | Void Steel (`#firmages:celestial_alloy/age_7`) plus parts | age_8 |

- **Deadlock rule:** the age_7 payload needs the Information Age alloy, never the Space alloy; the age_8 payload needs the Space alloy.
- **Fuel** is the fluid tag `#ad_astra:fuel`, the same tag the crewed rockets burn. It resolves in the pack to Immersive Petroleum diesel and kerosene, `ad_astra:fuel`, `ad_astra:cryo_fuel`, `#c:fuel` and `#c:biodiesel`. Create Diesel Generators (age_5) puts its biodiesel and diesel into `#c:fuel`, `#c:diesel` and `#c:biodiesel` [verified, jar data and `kubejs/server_scripts/tags/late_ages.js`].
  - Ad Astra's own fuel has no source because the Fuel Refinery is hidden [verified].
  - A plant-oil biodiesel line flies probes as it already flies crewed rockets: accepted.
  - Handlers match the tag, never flowing variants.
- **Flight is work:** the launch takes `launch_mb`; then each tick the control burns fuel at the throttle rate and adds progress, and the flight ends when progress reaches the module's `flight_work`. An empty tank pauses the flight; nothing fails.
- **Throttle:** right-click the control, or give redstone strength 1..15 on its back, mapped to the steps. It trades fuel for time. Step 1 burns 1 mB/t for 1 progress, step 2 burns 3 for 2, and step 3 burns 8 for 4. With `flight_work` 12,000 that is 10, 5 or 2.5 minutes for 12, 18 or 24 buckets.
- **Returns are fixed** (no roll, no loss) and come back with the probe into the control's nine-slot output.

```json
// data/firmages/firmages_launch/fuel.json
{ "fluid_tag": "ad_astra:fuel", "launch_mb": 4000,
  "throttle": [ { "burn": 1, "progress": 1 }, { "burn": 3, "progress": 2 }, { "burn": 8, "progress": 4 } ] }
// data/firmages/firmages_launch/module/sampler.json
{ "module": "firmages:sampler_module", "age": "age_7", "flight_work": 12000,
  "returns": [ { "item": "firmages:voidglass", "count": 4 } ] }
// data/firmages/firmages_launch/module/deep_space.json
{ "module": "firmages:deep_space_module", "age": "age_8", "flight_work": 24000,
  "returns": [ { "item": "firmages:deep_space_sample", "count": 2 }, { "item": "firmages:voidglass", "count": 2 } ] }
```

The loader (`launch/LaunchDataLoader`, a reload listener like `ShrineDataLoader`) rejects a file with an ERROR if a return item is in `#c:ores`, `#c:raw_materials`, the `ad_astra` namespace or any `firmages:age_blocks/*` tag, or if a return's Age is later than the module's Age. A module whose item is not registered is skipped.

### 31.5 Mission state machine (`launch/LaunchControlBlockEntity`, server)

- **Phases:** `IDLE` -> `COUNTDOWN` (100 ticks) -> `ASCENT` (200 ticks) -> `FLIGHT` (fuel-driven) -> `DESCENT` (160 ticks) -> `IDLE`.
- **Start conditions:** a valid complex; a probe and a module in the slots; `launch_mb` in the tank; free output space for the returns; open sky from the cradle to the build limit (heightmap); no redstone hold. An unmet condition waits with a Jade line ("The sky is closed above the cradle.").
- **Silo hatch:** during `COUNTDOWN` and `ASCENT` the control emits redstone 15 on its back face, so a Create gantry hatch over a silo opens by itself (the existing Ponder scene "Silo Hatch", §22.5, teaches the build).
- **Persistence:** breaking the control mid-flight drops it with the mission as the data component `firmages:mission`; placed again, it resumes. Save and load keep the phase, progress and slots.
- One probe per complex is in flight at a time; parallel lines need more complexes.

### 31.6 Automation, proof

- **Capabilities:** the control and ports expose `Capabilities.ItemHandler.BLOCK` (insert probe and modules; extract returns, and the returned probe only when `launch.autoRelaunch` is false) and `Capabilities.FluidHandler.BLOCK` (fuel, `#ad_astra:fuel` only).
- **Logistics:** Mekanism logistical and mechanical pipes are the reference. An AE2 pattern provider in blocking mode pushes module plus fuel as a processing pattern (module in, voidglass out), and an import bus or interface takes the returns. The probe stays in the control and relaunches by default.
- **Crewed criterion:** `firmages:silo_launch` stays the crewed Ad Astra criterion of §22.4; a probe launch never fires it.
- **New criterion `firmages:probe`** (`module` id optional, `count`): counted per team in SavedData `firmages_launch` (`teams.<uuid>.<module> = long`) and credited to the control's owner team (the placer, as the Tally Chute). It fires once a second for online members and at login (the `TallyService` pattern). The quest strand "launch 16 probes" uses it.

### 31.7 Rendering and sound (no GeckoLib)

- **`client/LaunchRenderer`** (BER on the cradle; `shouldRenderOffScreen` true, `getViewDistance` 256, infinite render box like `ShrineHeartRenderer`):
  - the probe's baked item model at scale 3, rising 300 blocks with ease-in over `ASCENT`;
  - a trail of vanilla particles along the path (`campfire_signal_smoke`, `flame`, and `explosion` at ignition), plus flame jets out of the trench exit;
  - on `DESCENT`, the capsule falling back under three white canopies drawn by the same renderer.
  - There is no entity, and nothing collides.
- **Sounds:** vanilla events only (`entity.firework_rocket.launch`, a muffled `entity.generic.explode`, and a `block.fire.ambient` loop). The sky gives no line.
- **Timing:** the payload `firmages:launch_fx` (pos, phase, phase start game time, probe item) goes to players within `launch.fxRange`. The block-entity sync tag carries the same, so a player who arrives late sees the right moment.

### 31.8 Origin Archipelago

**Arena version 3.**
- `OriginService` rebuilds the arena with `OriginArena.buildArena` whenever `arena_version` < `VERSION`, and `buildArena` sets every block without an air check [verified, source]. So version 3 is **not** a full rebuild.
- A dedicated migration step (`arena_version` 2 to 3) widens each island to radius 9..11 with an **air-only** setter and places `firmages:ark_plinth` one block north of the echo (unbreakable, no loot, Age map age_9, never crafted).
- A fresh world builds version 3 directly.
- Before the win, a plinth answers "The island sleeps."

**Samples.**
- Each island has three groups and, for Ages with an alloy, a crown. Groups complete in any order and are recorded for good in `OriginSavedData` (`ark.<age>.<group> = true`).
- Items are inserted by hand or by pipe into the plinth; distinct ids count.
- A fauna sample is a leashed animal brought within four blocks and right-clicked on the plinth with the lead. It becomes persistent and stays, and its later death changes nothing.
- A work sample is a block on the island that satisfies a rule when the plinth checks. Rule types reuse the shrine rites (`blockstate`, `energy`) plus `kinetic` (Create speed of at least `rpm`).

| Island | Flora (distinct) | Fauna (any one) | Work | Crown |
|---|---|---|---|---|
| Stone | four TFC saplings | `tfc:rabbit` | lit `tfc:firepit` | none |
| Bronze | four TFC seeds | `tfc:chicken`, `tfc:duck` | lit `#firmages:ark/bronze_work` (TFC charcoal forge or bloomery) | none |
| Iron | four TFC fruit tree saplings (`#firmages:ark/fruit_saplings`) | `tfc:cow`, `tfc:goat` | `create:water_wheel` at 16 RPM | none |
| Arcane | four archwood saplings or four enchanted books | `tfc:cat`, `ars_nouveau:starbuncle` | lit `#firmages:ark/arcane_work` | four Starlit Iron |
| Industrial | eight IE hemp seeds | `tfc:horse` | `immersiveengineering:crusher` holding FE | four Cloudsteel |
| Electric | four TFC crops of a greenhouse climate | `tfc:sheep` | `immersiveengineering:capacitor_hv` holding FE | four Aurora Steel |
| Information | four Mystical Agriculture seeds | `tfc:pig` | `ae2:controller` online | four Halo Steel |
| Space | four `ad_astra` plants (aeronos, strophar, glacian) | none | `mekanism:teleporter` holding FE | four Void Steel, two deep-space samples |
| Quantum | four items of `#firmages:ark/quantum_flora` | none | `mekanismgenerators:fusion_reactor_controller` active | four Quasar Alloy |

- The ids are default data and are checked against `dev/data/registry.json` and `dev/data/tfc-4.2.11-ids.json` before the build.
- Magic stays optional, because every Arcane group has a non-magic choice.
- The teleporter is only a work sample and is never used to travel.

```json
// data/firmages/firmages_ark/island/age_2.json
{ "island": 2, "flora": { "tag": "firmages:ark/fruit_saplings", "distinct": 4 },
  "fauna": { "entities": ["tfc:cow", "tfc:goat"] },
  "work": { "type": "kinetic", "block": "create:water_wheel", "rpm": 16 },
  "crown": [], "stages": ["firmages:ark/age_2/stage_1", "firmages:ark/age_2/stage_2", "firmages:ark/age_2/stage_3"] }
```

The Archipelago crown entries name `#firmages:celestial_alloy/age_3` .. `age_8` and `firmages:deep_space_sample`.

**Growth and the nine beams.**
- Each completed group places the next stage template (`data/firmages/structure/ark/age_N/stage_K.nbt`, K = groups done) from the plinth outward over `ark.growthTicks`, using the consecration's batch placer (§17).
- Templates write only into air and into blocks of the island's own earlier stages (stored positions), never over a player block.
- An island with every group and its crown is **complete**. A beacon beam in that tier's `response.beam_color` then lights in a ring of nine sockets around `firmages:origin_altar` (radius 3), drawn by the heart's beam routine.
- When all nine are lit, the altar's crown shows the FINALE sky tint once more for 10 s and then stays as nine beams.
- **Sync:** the payload `firmages:ark_sync` (stage per island, complete mask), sent on entering The Origin and on change.

### 31.9 Network, config, commands

- **Network:** `firmages:launch_fx` (protocol "6", M14) and `firmages:ark_sync` (protocol "6", M17; one bump for 0.12.0).
- **Server config:** `launch.enabled` (true), `launch.fxRange` (256), `launch.countdownTicks` (100), `launch.autoRelaunch` (true), `ark.growthTicks` (200, 0..1200).
- **Client config:** `effects.launchFx` (true).

| Command | Purpose |
|---|---|
| `launch status [pos]` | Phase, progress, fuel, slots, wait reason |
| `launch finish <pos>` | Needs `debug.allowSimulate` |
| `ark status` | Groups, crown and stage per island |
| `ark check <island>` | Runs the plinth's work check, as an empty-hand click does |
| `ark grant <island> <group>` / `ark reset <island>` | Debug (`debug.allowSimulate`) |

### 31.10 Tests

- **U** `LaunchDataLoaderTest`, `ThrottleMathTest`, `ArkRulesTest`.
- **G** `LaunchGameTests`:
  - `complexValidates`: the test multiblock; a blocked trench, zero ports or five ports fail; pipes on `_` cells pass.
  - `missionRunsOnFuel`: water in the test copy of the fuel tag; the launch takes `launch_mb`; progress only while burning; an empty tank pauses; the end gives four voidglass and keeps the probe.
  - `throttleShortensFlight`, `closedSkyWaits`, `countdownEmitsRedstone`, `missionSurvivesSave`, `controlDropKeepsMission`, `oreReturnRejected`, `probeCriterionForTheParty`.
- **G** `ArkGameTests`:
  - `inertBeforeFinale`, `samplesCountOnce` (distinct items, a leashed cow, a kinetic stand-in), `stagesNeverOverwritePlayerBlocks`, `completeLightsSocket`;
  - `arenaV3KeepsBuilds`: starts from a version-2 arena carrying player blocks on the disc and the islands; after the migration every player block is unchanged and the plinths stand.
- **M:** the launch spectacle from 200 blocks, a silo hatch opening on the countdown, the ghost alignment of the complex, Archipelago stages in a client.

### 31.11 Acceptance criteria

1. Voidglass and deep-space samples have exactly one source each (§24.7).
2. A probe never vanishes. A flight with an empty tank pauses and resumes. Returns match the module file exactly.
3. No module file with an Ad Astra ore or raw material loads.
4. The age_7 payload needs no Space Age alloy (no deadlock with V4A).
5. The arena migration never changes a player block.
6. Nothing outside §31.8 reads the Archipelago state, and a world that never touches it finishes the pack.

### 31.12 Implementation notes (M14, branch `c2/sky-m14`, 2026-10-16)

- **Package.** `launch/` holds the registry (`LaunchRegistry`), the blocks and block entities (`LaunchControlBlock`(+BE), `ProbeCradleBlock`(+BE), `LaunchPortBlock`(+BE)), the pure rules (`LaunchRules`, `TrenchShape`, `LaunchCounts`), the data (`LaunchData` pure, `LaunchDataLoader`), the mission record (`Mission`), the server side (`LaunchService`, `LaunchSavedData`, `ProbeTrigger`) and the shared player text (`LaunchText`). Client: `client/LaunchRenderer` (BER on the cradle), `client/LaunchFx`. Network: `net/LaunchFxPayload`. Commands: `command/LaunchCommands`, suite `command/LaunchSuite` (`selftest launch`, also in `all`). Jade: `FirmagesJadePlugin.Launch` (control and port).
- **Registry names.** Blocks with items `firmages:launch_control` (`facing`, `throttle` 1..3), `firmages:probe_cradle`, `firmages:launch_port`; items `firmages:probe` (stack 1), `sampler_module`, `deep_space_module` (stack 16), `voidglass`, `deep_space_sample`. Block entities of the same three names. Data components `firmages:mission` (the mission in flight) and `firmages:launch_fuel` (the tank), both copied into the dropped control by its loot table. Criterion `firmages:probe` (`module` optional, `count` default 1), advancements `firmages:probe_first` and `firmages:probe_sixteen` (no display, for the quests). Block tags `#firmages:launch/{pad,rim,frame,trench}` with optional `#firmages_test:launch/*` stand-ins for the GameTests. SavedData `firmages_launch`.
- **Multiblock.** `firmages:launch_complex` (Modonomicon dense, 20 layers): the 5 by 5 pad (`R` rim ring, `P` inside, cradle `0`), the control `C` three blocks before the cradle and the gantry `F` as four corner posts 20 high with a top ring; 110 counted positions (`LaunchSuite.COUNTED`). The control finds its cradle three blocks away on any side and the pattern is checked through `ShrineMultiblocks.check` at the cradle; the control must be the pattern's `C`. Then, in code: one to four `firmages:launch_port` on rim cells, and the trench (`TrenchShape`): a channel 3 wide and 3 deep under the cradle to under the pad edge, its mouth one block beyond the pad (3 by 3, open), back wall, side walls and floor from `#firmages:launch/trench`; any of the four sides may be the open one. The control turns to face the cradle once the complex is formed. Re-validation: every 100 ticks and on a placed, broken or exploded block within 9 blocks (and 5 below, 21 above).
- **Control.** Slots: probe (1), module, nine outputs; tank 16,000 mB of the fuel tag (a step-three sampler flight burns 24,000 mB, so pipes refill it during the flight; an empty tank pauses). Hand: an empty right-click shows the ghost and what the complex lacks, or on a formed complex steps the throttle and names the phase; a probe, module or fuel container goes in; sneak plus empty hand takes the outputs, then the probe, then the modules. Redstone: strength 1..15 on the back selects the step (`LaunchRules.stepFromRedstone`, three steps: 1..5, 6..10, 11..15), a signal on any other face holds the launch, and the back gives 15 during countdown and climb (the back signal is ignored while it emits). The front texture shows the step (three states).
- **Mission.** `IDLE` checks the conditions in the order of `LaunchRules.Wait`; the countdown (`launch.countdownTicks`) takes nothing and aborts when a condition fails. Ignition takes the probe, one module and `launch_mb`; the module's returns are fixed in the mission at ignition. `ASCENT` 200 ticks, `FLIGHT` burns `burn` mB and adds `progress` per tick, `DESCENT` 160 ticks, then the returns go to the outputs and the probe back to its slot (`launch.autoRelaunch`) or to the outputs; a full output waits. A complex that breaks during a mission pauses it. Breaking the control drops it with the mission and the tank; placed again it resumes. A probe flight never fires `firmages:silo_launch`.
- **Automation.** `Capabilities.ItemHandler.BLOCK` and `FluidHandler.BLOCK` on the control and on every linked port, from any side: probe into slot 0, modules into slot 1, fuel (the tag only) into the tank; pipes extract only the outputs and never fuel. An unlinked port offers no capability, and every link change invalidates the port's capability cache.
- **Criterion.** A returned probe counts for the control owner's team (`TallyService.teamOf`, the placer). `LaunchService.fireDirty` fires `firmages:probe` once a second for the online members of changed teams and at login.
- **Rendering.** The cradle's renderer draws the probe's item model at scale 3 on the cradle, climbing `RISE` = 300 blocks with ease-in, falling under three white canopies, with `shouldRenderOffScreen`, view distance 256 and an infinite box in the air. `LaunchFx` adds the trail (`flame`, `campfire_signal_smoke`, `explosion_emitter` at ignition), flame jets and large smoke out of the trench mouth for 70 ticks, poof at the landing, `block.fire.ambient` while climbing, and draws launches outside the loaded chunks from the payload (288 blocks). Server sounds at ignition: `entity.firework_rocket.launch` and a low `entity.generic.explode`. Client config `effects.launchFx`.
- **Network.** Protocol "6" adds `firmages:launch_fx` (cradle, phase, phase start, probe, trench side); the cradle's block-entity sync carries the same for late arrivals.
- **Data.** `fuel.json` and the two module files as in §31.4; the loader parses after the load's tags are bound (`TagsUpdatedEvent`, server data load) and rejects ores, raw materials, `ad_astra` items, `firmages:age_blocks/*` blocks, unknown items and returns later than the module.
- **Tests.** U: `ThrottleMathTest` (8), `LaunchDataLoaderTest` (7); `gradlew build`: 208 unit tests green. G (`LaunchGameTests`, batch `firmages_12_launch`, template `launch_area` 15 by 27 by 15, short flights by `LaunchDataLoader.set`): `complexValidates`, `missionRunsOnFuel`, `throttleShortensFlight`, `closedSkyWaits`, `countdownEmitsRedstone`, `missionSurvivesSave`, `controlDropKeepsMission` (mission and fuel in the item), `oreReturnRejected`, `probeCriterionForTheParty`, `handFillsFuel`; `runGameTestServer`: "All 92 required tests passed". M: one real flight on a copy of the pack (§ "1.0 round C2, sky M14" in `dev/poc-results.md`).
- **Pack hand-off (M15).** Recipes for the complex, the probe and the two modules (deadlock rule of §31.4; `selftest launch` case `module_recipes` stays red until then), the quest strand "launch 16 probes" (`firmages:probe` or the advancement `firmages:probe_sixteen`), and the Age tags of the eight items: the rows are in `dev/age_map.toml [items]`, the tags follow with the next dump and `derive_ages.py`.
- **Open (M).** The spectacle from 200 blocks in a client, a silo hatch opening on the countdown, the ghost alignment of the complex.

### 31.13 Implementation notes (M17, Archipelago part, branch `c2/sky-m17`, 2026-10-16)

- **Package `ark/`.** `ArkData` (pure parser of `data/<ns>/firmages_ark/island/*.json`) with `ArkDataLoader` (reload listener; crown entries carry `neoforge:item_exists` guards, a failed guard leaves the entry out with an INFO line), `ArkRules` (pure: accept, apply, settle, target stage, completion, sockets, growth order), `ArkProgress` (pure) and `ArkState` (NBT, inside `OriginSavedData` under `ark.age_0`..`age_8`), `ArkWork` (work check), `ArkGrowth` (template blocks, scheduling, placement), `ArkService` (events, plinth, operators, sync), `ArkPlinthBlock`, `ArkPlinthInput`, `ArkRegistry`. Client: `client/ArkClient`. Commands: `command/ArkCommands`, suite `command/ArkSuite` (`selftest ark`, also in `all`). Compat: `compat/create/CreateKinetics` (set as `ArkWork.kineticSpeed` only with Create loaded).
- **Islands are 0..8,** the shrine Ages of `OriginArena.islandTop` (Stone .. Quantum); the Singularity Age has none.
- **Arena v3.** `OriginArena.VERSION` = 3. A world below v2 gets `buildArena` (v2 layout) and then the same air-only step as a v2 world: `migrateV3` = `widenIsland` (new ring, trim rim at the edge, deeper rock cone; no top block inside the old footprint, so a dug hole stays) plus `placePlinth` (first free spot of `plinthSpots`: one north of the echo, then seven fallbacks; an existing plinth is found again). `origin rebuild` runs both. The plinth positions are recorded in the island progress.
- **Plinth.** Unbreakable, no loot, light 9, `Capabilities.ItemHandler.BLOCK` without a block entity (one always-empty slot that takes only what the island still asks for; nothing extracts). Right-click order: a led animal, then the held item as a sample; a block item it does not want passes to normal block use; an empty hand also runs the work check and tells what is missing.
- **Groups.** flora (one option of `distinct` ids or `count` items), fauna, work; each done group grows the next stage (K = groups done). The crown grows no stage; it only completes the island. An island whose `fauna.entities` is empty (Space, Quantum) gets its fauna group once the finale is won, so it grows stage 1 right after the win.
- **Rules shipped (`dev/gen_ark.py`).** As the §31.8 table with these choices: Stone flora also takes the eight vanilla saplings (wood decision HW1A); Bronze work is a lit bloomery or a charcoal forge at `heat_level` >= 1; Arcane flora is four archwood saplings **or four TFC flowers** (enchanted books are hidden in this pack and are one item id), work a lit Theurgy brazier or calcination oven **or a kindled Create blaze burner**; Industrial flora eight IE hemp seeds (`count`); Information work `ae2:controller[state=online]`; Space flora from aeronos and strophar mushrooms and caps and glacian leaves; Quantum flora vanilla Nether and End plants (`#firmages:ark/quantum_flora`). Energy rules ask for at least 1 FE.
- **Templates.** 25 by 22 by 25, island top centre at template (12, 6, 12); stage 1 soil and plants, stage 2 the Age's work (north-east), stage 3 a landmark (south-west) and what hangs under the rim; vanilla blocks, and TFC blocks where the pack disables the vanilla one (review fix 0.12.0: wrought iron block, anvil and lamp, firepit, rooted dirt, hanging vines, allium; the stages stand in the world and can be broken, so `gen_ark.py --check` rejects any template block whose item is disabled or hidden); nothing inside radius 2.6 above the surface (plinth and echo). 120 to 233 blocks per stage 1, 15 to 95 per later stage.
- **Growth.** Steps ordered by distance from the plinth over `ark.growthTicks`; a step waits while its chunk is unloaded; the grown blocks are recorded at once (position and block id), so a later stage may replace only those, and `ark reset` removes only those that still stand.
- **Beams.** `ArkClient` draws `BeaconRenderer.renderBeaconBeam` at `ALTAR + ArkRules.socket(i)` (radius 3, toward island `i`) in the tier's `beam_color` for each set bit; the ninth bit sends `celebrate`, and the client shows the FINALE tint (0xD9C2FF) for 200 ticks through `SkyEffects`.
- **Network.** `firmages:ark_sync` = stages (byte per island), complete mask, beam colours, celebrate flag; sent on entering The Origin, at login there, and to everyone in The Origin on change. Protocol "6" for 0.12.0 (one bump with the grid lock and M14, §24.6).
- **Commands.** `ark status`, `ark check <island>` (operators), `ark grant <island> <group>` and `ark reset <island>` (also `debug.allowSimulate`).
- **Tests.** U `ArkRulesTest` (10, including `gen_ark.py --check`). G `ArkGameTests`: `inertBeforeFinale`, `samplesCountOnce`, `stagesNeverOverwritePlayerBlocks`, `completeLightsSocket`, `arenaV3KeepsBuilds`. The GameTest run has no TFC or Create: the islands under test get vanilla rules and a lodestone stands in for the kinetic work.
- **Pack hand-off.** `dev/age_map.toml` row `firmages:ark_plinth` = age_9; the next gate-off dump puts it into `age_items/disabled` and the hidden list like `origin_altar` (`derive_ages.py`).
- **Copy boot (pack, 2026-10-16).** Arena v3 on a new world: 15,945 air blocks and nine plinths in about 0.2 s. After `origin kill boss` the Space and Quantum islands grow stage 1; four TFC saplings by `debug use` grow the Stone island's stage 1 (120 blocks), a led `tfc:rabbit` its stage 2 (17 blocks). `selftest ark` 5 of 5. Details in `dev/poc-results.md`.
- **Open (M).** One island grown in a client: the stages, the beams at the altar from the arena and the FINALE tint after the ninth; a fuelled TFC firepit as the Stone island's work (an unfuelled one goes out on its first tick).

## 32. Work breakdown (M10 to M17)

The milestones are listed in dependency order; the column "Depends on" names the real dependencies. The estimates use the reviews' re-estimates, with the cuts of this merge (no kinetic mount, no starsteel plate, no mossy skystone, no Glacier Cairn, no Sable drift); the Wind Shrine takes the Glacier Cairn's place (S6B).

| M | Scope | Depends on | Effort | Protocol | Done when |
|---|---|---|---|---|---|
| **M10** (built, §25.13) | Sky plumbing (`DeferredSites`, `ClimateSource` with the TFC and test sources, `SkySavedData`, the `text/Numbers` helper) and Starfall (§25): scheduling, site choice with claims and isle exclusion, plan and carver, blocks and loot condition, sky metals and TFC heat data, IE veins with dirty-mark and reconcile, streak renderer and sounds, FTB Chunks `compileOnly` | none | 45–60 h | "5" | §25.11 U and G green, `selftest sky` green, M: streak and crater in the pack |
| **M11** (built, §26, 0.12.0) | Sky Harvest (§26): `AuroraRules` at the reference height, Aurora Net, aurora fluid, Storm Rod with the rain and thunder rates, band command (as built: no `sky_sync`, no renderer, §26.7) | M10 | 25–35 h | (in "5") | §26.9 green, PoC §26.10 (band distance, rod rate) measured |
| **M12** (built, §27, 0.11.0) | Lens Crown, mod side (§27.3 to §27.7, §27.10 to §27.13): recipe type and serializer, Star Anvil, Lens Mount, lenses, chord library, multiblock reuse, comparator, renderer, Jade, EMI, three Ponder scenes, the 22 library recipes as mod data | M10 item ids only (stand-ins in tests) | 50–70 h | none | §27.13 U and G green, M: beams and EMI |
| **M13** (built, §30.12) | Drifting Isles (§30): structure type and piece, skystone lighting, crystal blocks, the re-forming lode, fallback placement, the Wind Shrine's place on the isle (§28.4a, template from M16), plus the flight PoCs (§30.7) | M10 (`DeferredSites`) | 34–46 h | none | §30.10 green, flight PoCs passed or the band lowered |
| **M14** (built, §31.12) | Launch Complex (§31.3 to §31.7): multiblock and trench check, control state machine, modules data, fuel, renderer, `launch_fx`, `firmages:probe` criterion | M12 (alloy ids) | 30–40 h | "6" | `LaunchGameTests` green, M: one real flight in the pack |
| **M15** (built, §27.11a) | V4A integration (pack): the eight recipe changes of §27.8, the part recipes of §27.9, the Storm Rod, Net, anvil and probe recipes, `station_ages.js`, the Age map rows of §24.5 and the `[ore_blocks]` `disguise` key in `gen_stage_locks.py`, quests and goal lines, `dev/validate_recipes` over the sky materials | M10 to M14 | 15–25 h | none | §24.7 criteria 1 to 4 green on the test server (`selftest sky`, `selftest crown`), one playthrough of age_2 to age_5 by commands |
| **M16** (built, §28, §29.10) | Wayshrines, Sky Anchors and the blessing sources (§28, §29): the generator, the eight structures, `tfc:climate` sets, tutorial placement by code, the service, the pointer with cache, `BlessingSources` and `BlessingIndex`, the anchor and shards, removal of `everywhere` | M12 (shard recipes), M13 (the isle piece places the Wind Shrine); the ground Wayshrines need nothing | 60–85 h | none | §28.10 and §29.8 green, `selftest wayshrines`, scan of 20 seeds |
| **M17** (built, §31.13) | Origin Archipelago (§31.8): arena migration v3, plinths, sample rules, 27 stage templates, socket beams, `ark_sync` | M14 (deep-space sample), M12 (alloys) | 30–45 h | "6" (0.12.0) | `ArkGameTests` green, M: one island grown in a client |

**Total: about 290 to 405 hours.**

**Parallel worktrees** (each on its own branch from `dev`; protocol bumps only at merge, one bump per release ("5" in 0.11.0, "6" in 0.12.0)):
- **Worktree A:** M10 then M11 (the sky package).
- **Worktree B:** M12 from day one; it uses stand-in items and registers the real ids once A merges.
- **Worktree C:** M13 once A's `DeferredSites` is merged (or with a copied interface), then M14.
- **Worktree D:** the Wayshrine half of M16 (generator, structures, service; about two thirds of M16) from day one. The anchor half waits for M12.
- **Joins:** M15 after A, B and C have merged. M17 last.

**Playtest gates:**
- M10, M11 and M12 must ship before a playtest beyond the Iron Age.
- M13 must ship before a playtest beyond the Arcane Age.
- M14 must ship before a playtest beyond the Information Age.
- M15 makes V4A live, and the main road is complete only with it.
- M16 and M17 are off the main road.

## 33. Assets needed

**Textures and models (block):**
- `sky_rock`; `meteorite`; `starsteel_meteorite`; `starfall_core` (cold base and an emissive hot overlay, two metals); `meteoric_iron_block`; `starsteel_block`.
- `storm_glass` (connected glass, CTM sheet); `storm_rod` (basin and rod, two-block model); `aurora_net` (translucent animated panel, `catching` variant).
- `aurora_light` still and flowing (animated).
- `star_anvil` (top, side, base, `formed` and `active` variants); `lens_mount` (frame; models `lens_mount` and `lens_mount_in`, y-rotated by `facing`); `lens_glass` (greyscale, tinted).
- `wayshrine_core` (three phases); `wayshrine_stone` (eight `kind` variants).
- `sky_anchor` (base with nine sockets, `active` variant).
- `skystone`; `firmament_crystal_cluster` (emissive); `firmament_lode` (emissive).
- `probe_cradle`; `launch_control` (front with throttle states); `launch_port`.
- `ark_plinth`.

**Textures (item):**
- `meteoric_iron_{chunk,ingot,double_ingot,sheet,rod}`, `starsteel_{chunk,ingot}`, `aurora_light_bucket`.
- `lens_blank`, nine `lens/<age>` (rim in the Age material, glass in the tier colour), seven alloy ingots.
- `blessing_shard` (greyscale, tinted by tier).
- `firmament_crystal`, `voidglass`, `deep_space_sample`.
- `probe` (also the model the launch renderer draws at scale 3), `sampler_module`, `deep_space_module`.

All textures go through `dev/gen_textures.py`, checked with `--check`.

**Structures (`.nbt`):**
- Eight Wayshrine ruins plus their eight restored multiblocks, from `dev/gen_wayshrines.py`.
- 27 Archipelago stage templates (`ark/age_N/stage_1..3`).
- Isles and craters are code-shaped (no templates).
- GameTest templates: a crown test frame, a Wayshrine stand-in, a launch complex stand-in, and a version-2 arena with player blocks.

**Multiblocks (Modonomicon JSON):** `lens_crown`, `launch_complex`, eight `wayshrine_<type>`.

**Sounds** (`sounds.json`, all mapped onto vanilla events as §8.1; own files are optional later):
- `starfall.impact`, `starfall.distant`;
- `aurora_net.catch`;
- `crown.chord` (note-block bell at nine pitches);
- `wayshrine.lit`;
- `lode.reform`;
- the launch sounds are vanilla events directly.

**Ponder scenes** (with schematics from `dev/gen_ponder_schematics.py`): "The Lens Crown", "Chords" and "A Chord Library". The launch build reuses the existing "Silo Hatch".

**Lang (en_us):**
- all blocks and items, the 14 chord names, the eight Wayshrine names, the nine shard names (from the blessing names);
- the Jade, EMI and Ponder texts, the actionbar lines and the eight bearings.
- Everything follows `dev/style-guide.md`.

**Shader pack setting:** Complementary's own aurora off (pack hand-off).

## 34. Balance hooks (harmony round, review 0.11.0)

Small hooks into other mods for the harmony decisions; each is a guarded mixin (`FirmagesMixinPlugin`: package `ae2`
needs AE2, `cca` needs Create Crafts and Additions) or an event listener registered only when its mod is loaded.

### 34.1 The alternator burns fuel (Felix H1A)

- Create Crafts and Additions 1.7.2 [verified, javap]: `AlternatorBlockEntity#tick` produces
  `getEnergyProductionRate((int) getSpeed())` FE per tick (480 × RPM/256 × `generator_efficiency`, the pack sets 0.35:
  168 FE/t at 256 RPM). `mixin/cca/AlternatorMixin` multiplies that value by the fuelled share of the alternator's
  kinetic network (`compat/create/AlternatorFuel`, rounded down by `balance/FuelShare`).
- Share = fuelled capacity / all capacity over `KineticNetwork#sources`, each weighted by `getActualCapacityOf`
  (Create 6.0.10 [verified, javap]). Fuelled: a steam engine's `PoweredShaftBlockEntity` whose boiler has
  `BoilerData#activeHeat > 0` (a fuelled blaze burner; passive heat does not count), and every engine of Create:
  Diesel Generators (they turn only while burning). Not fuelled: water wheel, windmill, hand crank, the electric motor
  (FE to SU to FE), TFC rotation through the torque link, flywheels, the creative motor. Cached per network id once per
  tick. The goggle line keeps the unscaled rate.
- Config `balance.alternatorFuelOnly` (true). `/firmages balance alternator <pos>` lists the network's sources, their
  capacity and whether they burn fuel, and the share.
- Tests: **U** `FuelShareTest` (windmill alone 0 FE/t, coal boiler 168 FE/t, half wind and half steam 84 FE/t, edges).
  Create is not in the GameTest run (it needs Ponder and Flywheel there), so the network glue is checked on a server
  copy with `balance alternator` **[S]**.

### 34.2 AE2 cell types (Felix: freer, as in other expert packs)

- AE2 19.2.18 [verified, javap] clamps a cell's types to the inlined `MAX_ITEM_TYPES = 63` in the constructor of
  `BasicCellInventory`, and its item cells (and MEGA Cells 4.11.0's item cells, both `BasicStorageCell`) are built
  with 63 types. `mixin/ae2/BasicCellInventoryMixin` lifts the clamp, `BasicStorageCellMixin` reports
  `balance.ae2CellTypes` (126, 63..252) for cells built with 63. Fluid cells (18) and portable cells (AE2 and MEGA
  derive theirs from 18 and the tier; the 16k portable item cell holds 36) keep their limits.
- Bytes still bound small cells: a 1k cell at 8 bytes per type fills its bytes before 126 types.
- The server config syncs to the clients, so the cell tooltip shows the same limit. `/firmages balance cells` reads
  the types of fresh 1k, 256k, portable 16k, MEGA 1M item cells and a 64k fluid cell through AE2's `StorageCells`
  (copy boot: 126, 126, 36, 126, 18) **[S]**.

### 34.3 The wireless crafting terminal as a portable grid (AE2 package)

- The pack makes `ae2:wireless_crafting_terminal` from the Industrial Age (`recipes/age_4/wireless_terminal.js`); AE2
  opens it only when it is linked to an ME network (wireless access point, Information Age).
- `compat/ae2/PortableCrafting`: right-click with a terminal whose `WirelessTerminalItem#getLinkedPosition` is null opens
  vanilla's crafting menu (a subclass that stays valid away from a table, with a real level access, so the result slot
  works and the grid returns its items on close), titled with the terminal's name. A linked terminal keeps AE2's
  behaviour, out of range included. ae2wtlib's hotkey is not covered.
- `/firmages debug useitem <name>` (debug players, `debug.allowSimulate`) right-clicks into the air and logs the menu
  that opened **[S]**.

## 35. Proof of work in the shrine offerings (Felix H5A, round C2)

From the Industrial Age an offering waits for a tally: the Age's product must pass the fellowship's Tally Chutes
(§22) in that Age before the shrine takes the relic. The product is one the main road makes in bulk anyway, so the
proof costs automation, not detours.

- **Data:** `data/<ns>/firmages_shrine/tallies.json`, one entry per offering Age (the Age the offering is made in):
  `{"tag": "c:ingots/steel", "count": 512}` or `{"item": "<id>", "count": n, "age": "age_4"}` (`TallyRequirement`:
  exactly one of `item` and `tag`, `count` positive, `age` dawn or age_0..age_9, by default the key). Merged over
  datapacks like `offerings.json`; a broken entry is a load error with its key.
- **Shipped numbers** (harmony plan table 2.2, the product each Age's loop already makes):

  | Offering in | Tally | Count | Why this product |
  |---|---|---|---|
  | age_4 Industrial | `#c:ingots/steel` | 512 | IE blast furnace and the Excavator parts |
  | age_5 Electric | `#c:ingots/aluminum` | 2,048 | arc furnace and HV wiring |
  | age_6 Information | `#c:ingots/osmium` | 4,096 | Mekanism casings and the 3x purification loop |
  | age_7 Space | `#c:ingots/desh` | 4,096 | Ad Astra rockets and the elite tier |
  | age_8 Quantum | `#c:ingots/calorite` | 8,192 | the ultimate tier and the fusion frame |

  The Stone to the Arcane Age keep plain offerings; the Singularity Age has none (the Gathering, §16).
- **Counting:** the numbers of the criterion `firmages:tally` (§22.2): items counted by the Tally Chutes since the
  `age` dawned (`TallyData` per team). The offering player's team counts; without a player (heart line, status,
  Jade, GameTests) the team with the highest count (one fellowship per server, §2.3).
- **Refusal:** `ShrineService.offer` returns `NO_PROOF` and keeps the item in the player's hand; the plinth stays
  empty. Caelum says what is missing (`firmages.shrine.proof.missing`: "Caelum asks for proof of work: your Tally
  Chutes must count 512 Steel Ingot in the Industrial Age. They have counted 140."). The same line follows the
  heart's right-click while the current tier waits for proof, and the Chronicle's goal quests of age_4 to age_8 name
  the tally.
- **Jade:** the heart syncs `proofNeed`, `proofHave`, `proofItem` (update tag, once a second and after a validation);
  the heart line shows "Proof of work: 140 of 512 Steel Ingot" or "Proof of work: 512 Steel Ingot counted".
- **Status:** `/firmages shrine` prints the requirement, the leading team's count and whether it is met.
- **Tests:** U `ShrineRulesTest.talliesParseMergeAndReject`. G `offeringWaitsForProof` (test data
  `firmages_test/firmages_shrine/tallies.json`: 8 iron ingots in the Industrial Age; refused at 0 and at 5 with gold
  that does not count, accepted at 8; the heart syncs 8 of 8; the player read the line on both refusals).

## 36. The heart draws power: rite `energy_consume` (harmony plan table 2.1)

The late Ages give the shrine a real power sink instead of a charged buffer (the `energy` rite of §2.6 only holds
FE in the ring's capacitors and spends nothing).

- **Rite:** `{"type": "energy_consume", "amount": <FE>, "hint": "<lang key>"}` in a tier file or in
  `firmages_shrine/gathering.json`. The heart (`ShrineHeartBlockEntity#energy`, the FE capability on the null side
  and every side) takes FE while the shrine's current work has unmet `energy_consume` rites, up to their sum, and
  never gives any back. The rite holds once that much is drawn; the FE is spent when the prayer is heard
  (`spendDrawn`). The drawn FE belongs to one work (`tier_<N>` or `gathering`): when the work changes, what was drawn
  lapses (INFO line).
- **Shipped:** ring 6 (grants age_7) 10,000,000 FE and ring 8 (grants age_9) 100,000,000 FE, each next to the ring's
  own rites; the Gathering 1,000,000,000 FE. The hold rite `energy` stays on ring 5 (1,000,000 FE in the Tesla
  Crown's capacitors), where the plan keeps it.
- **Messages:** `firmages.shrine.rite.heart_power` and `firmages.shrine.gathering.energy` name the amount and what is
  drawn; Jade shows "Power drawn: 4,000,000 of 10,000,000 FE".
- **Tests:** U `ShrineRulesTest.energyConsumeRite`. G `heartDrawsPower` (ring 6: a simulated insert changes nothing,
  null side and a side both feed, nothing comes back out, half is not enough, the full 10 M holds the rite, the
  prayer spends it and grants age_7; ring 7 draws nothing) and the Gathering part of the finale test (1 G FE drawn,
  spent on the Gathering).

## 37. FE safety net: `balance.feCap`

A generic cap for a power source that a mod's own config cannot tame. Nothing is capped by default.

- **Config:** `balance.feCap`, a list of `"<block id>=<FE per tick>"` (`FeCaps`: bad entries and unknown blocks
  are logged and ignored, a block listed twice takes the last value). Example:
  `feCap = ["createappliedkinetics:energy_provider=0"]`.
- **Pattern** (`balance/EnergyMeter`): one capability provider is registered at `EventPriority.HIGHEST` for every
  block entity type, for `Capabilities.EnergyStorage.BLOCK` and, with Mekanism, for Mekanism's strict energy
  (`compat/mekanism/MekEnergyMeter`, Joules converted with Mekanism's FE conversion). It answers only when it has a
  reason and otherwise returns null, so the block's own provider answers. With a reason it asks the block's own
  capability (a thread-local flag makes it step aside for that one nested lookup) and wraps it:
  - a capped block: what is drawn out of it (`extractEnergy`) counts against its budget of the game tick;
  - a block looked up from the side of a capped block: a pushing generator looks up its neighbour with the side facing
    itself, so `pos.relative(side)` is the source; what the neighbour receives from that side counts against the
    source's budget. This caps generators that push on their own.
  The budget is per block position and game tick (`FeCaps.room`), cleared every 200 ticks.
- **When it applies:** capability caches are not invalidated by a config change; a changed cap applies to new
  lookups (a placed or reloaded block, a restart). Cost when nothing is capped and no audit runs: one map check per
  lookup.
- **Limits:** a source that hands FE out without the capability (a mod's own network, a wireless link) is not seen.
  Hiding a source (`dev/age_map.toml` `[hidden]`) or its mod's config stays the first fix; the cap is the net.
- **Tests:** U `FeCapsTest`. G `BalanceGameTests.feCapLimitsAPushingCapacitor` (an IE LV capacitor capped at 50
  FE/t pushes 50 per tick into an MV capacitor instead of 256; drawn out, at most 50 in a tick).

## 38. Measuring commands (harmony plan section 6)

Both need permission 2, like all of `/firmages`.

- **`/firmages energy audit [radius] [seconds]`** (16 blocks, 10 s; 1..128, 1..600): meters every block with an
  energy capability within `radius` blocks horizontally (all heights) of the caller for `seconds`, through the same
  provider as §37. It invalidates the capabilities in the radius at start and end, so cached lookups are metered.
  Per block: FE in, FE out (drawn out of it, or pushed into a neighbour), the change of its stored FE. Summed per block
  type and divided by the ticks: "in / out / stored change / net use" in FE/t. A generator's output is "out", a
  machine's use is "in" minus the stored change, a cable carries as much in as out. Chat shows the top 15 types, the
  log and `logs/firmages-energy-audit.json` all of them. One audit at a time; a new one replaces the running one.
- **`/firmages ore audit`:** the ore ladder of the live server with the logic of the L- checks of
  `dev/poc_analyze.py` (`balance/OreLadder`, a port): every recipe of every Age (the recipe gate's master list, §8,
  else the recipe manager) encoded to JSON with its codec, every recipe that consumes an ore piece of the metal table
  measured (metal out over the piece's `grade_mb`, with the input count, chances and IE wrappers, quern powder at the
  arc furnace's mB from the furnace's Age). Reports per Age the lowest and highest best tech factor and the best magic
  factor, per station the measured range against the target, and the L-1 table deviations, L-2 tech steps back, L-3
  magic above the next tech step and L-4 unlisted multiplying types. `logs/firmages-ore-audit.json` holds every
  entry. The table is `data/firmages/balance/metals.json`, which the build copies from `kubejs/data/firmages/balance/metals.json` (one
  source; a datapack may override it).
- **Tests:** U `OreLadderTest` (on the real table: melting 1.0, purification with input counts and clumps, the IE
  crusher's wrappers and chances, the metal's first Age, a step back, an unlisted grid recipe, world data skipped).
  G `energyAuditMetersPerBlockType` (the LV capacitor gives about 256 FE/t, the MV one takes it),
  `oreAuditReadsTheTable`.

## 39. The Primordial by numbers (round C2)

The fight length comes from a model, not from a guess (`boss/FightModel`, docs/primordial.md).

- **Inputs from the Draconic Evolution jar** (1.21.1-3.1.4.633 [verified, javap: `EquipCfg`, `DEModules`,
  `ModuleCfg`, `IModularTieredItem`]; the pack's `DraconicEvolution.cfg` keeps the internal defaults): a sword hits
  for `tierDamage x swordDamageMultiplier + damage modules` (Chaotic 2.5 x 7.0 = 17.5; a Chaotic damage module +16,
  one cell of the 8 x 6 Chaotic grid) and swings `2.0 x 1.6 = 3.2` times a second.
- **Boss rules:** armour 12, toughness 8 (vanilla armour formula); one counting hit per attacker per 10 ticks (2 a
  second); new `boss.hitCap` (40): a hit counts at most 40 after armour, then the scaling of §16 (1024 / pool, the
  closed veil x0.25). Without the cap a grid full of damage modules (657.5 per hit) ends the fight in under a minute;
  with it the reference build (two Chaotic damage modules, 49.5, 44.7 after armour) is at the cap.
- **Assumptions:** a player hits 60 % of the time; the veil is open a third of the time in phases 1 and 2 (countered
  slams and pulls) and 60 % in phase 3 (shards); the shatter makes it untouchable 3 s in 25; the phase locks cost 3 s.
- **Settings** (defaults of `[boss]`, the pack's `config/firmages-server.toml` the same): `effectiveHealth` 13000,
  `perPlayerFactor` 0.7 (pools 13,000 / 22,100 / 40,300 for 1 / 2 / 4 players), `veilFactor` 0.25, `hitCap` 40,
  phases at 69 % and 38 % health (each phase a third of the fight), `enrageMinutes` 10 and `starfallEnrageMinutes`
  12 (only a fight slower than the model meets them).
- **Result:** 1 player 8.4 min (505.9 s), 2 players 7.2 min (430.5 s), 4 players 6.5 min (392.8 s). The wake log line
  names the model for the party at hand.
- **Tests:** U `FightModelTest` (the jar stats, armour and the cap, the pools, 6 to 10 minutes for 1, 2 and 4, each
  phase a third within 1 %, the uncapped full grid under a minute). G `BossGameTests` (phase floors at 69 % and 38 %,
  pools, a 1000 hit counted as the cap, the summoned fight won by capped hits).
- **To check on a real fight [S]:** uptime and veil share against a recording with 1, 2 and 4 players; the model's
  assumptions move with them, the settings with the model.
