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
| m10 | The Origin (datapack dimension `firmages:origin`, arena, Stargate Journey address, return gate) and the end-boss scaffolding (Origin Gathering trigger, `finale_won` on the tagged boss's death, FINALE ceremony) | build (0.4.0, §16); the boss itself is KubeJS + Gateways to Eternity + Cataclysm |
| later | Own end-boss entity, world events per Age | not in this spec |
| no | Age Codex screen, team chronicle, lore fragments, Dawn flair | never. Progress UI is the FTB Quests screen (key K) |
| later, if needed | Own interactive quest overview | only if FTB Quests on K turns out not clear or interactive enough (Felix, note on idea 5). It would replace, not duplicate, the K screen. Not in this spec |

Ages in order: `dawn`, `age_0` (Stone) … `age_9` (Singularity). Signature items (KubeJS items, namespace `firmages`) for `age_0`..`age_8`: Hearthstone, Sky Disc, Steel Heart, Arcane Keystone, Pressure Core, Humming Core, Data Matrix, Star Chart, Quantum Core. Offering the Age-N item grants `age_(N+1)`.

## 1. Architecture

### 1.1 Principle: global first, per-mod only where needed

The pack has exactly one FTB team, so the team's stages are the server's stages. firmages-core keeps a **server-global Age set** (`AgeState`), mirrored from ProgressiveStages. The set drives:

1. **m2, one global recipe filter.** A mixin on `RecipeManager#apply` at TAIL drops every recipe whose output is in a locked Age. Every mod's machines, caches and client sync then follow automatically.
2. **One datapack reload per Age unlock.** `server.reloadResources(selectedIds)` runs inside the m7 ceremony and rebuilds recipes, tags and all mod caches through each mod's own tested `/reload` path.
3. **Per-mod hooks only where output does not come from recipes, or where removing a recipe would damage world data:** the IE excavator (mineral mixes are rolled at worldgen and must never be removed), fake-player block breaking, prospecting tags, and the DE reactor.

ProgressiveStages stays responsible for per-player item use, pickup, block interaction, EMI hiding and ore disguise. firmages-core never re-implements these.

### 1.2 Evaluation of the global simplification (honest)

| Aspect | Finding | Consequence |
|---|---|---|
| Server stall | Measured on the test server (35,213 recipes): 4.2 s recipes + KubeJS apply, about 15 s total datapack load at boot. All `apply` phases run on the server thread [verified, javap]. Estimate 6 to 10 s tick freeze per unlock, about 10 times per playthrough. | Acceptable if hidden in the ceremony. `reloadResources` blocks the server thread in `managedBlock` [verified, 21.1.252 source], so no ticks and no outgoing keep-alives happen during the stall; the server-side 15 s keep-alive check does not run while frozen, but the client's Netty `ReadTimeoutHandler(30)` drops players after 30 s without packets. Hard limit 30 s, target below 15 s. Measure first (M0). |
| Client resync | Tags and recipes are sent to every client. EMI (with its JEI backend) rebuilds its index; Patchouli and Modonomicon books resync. Unmeasured, expected seconds to tens of seconds per client. | The ceremony runs client-timed, so visuals continue during the rebuild. Measure in M0. |
| Monotonic progress | Ages only get unlocked; a reload only adds recipes. In-progress work (AE2 patterns, Create sequenced items, TFC anvil plans, Mekanism cached recipes) never points at a removed recipe. | No invalidation in normal play. |
| AE2 | A crafting pattern for a locked recipe cannot be encoded (terminal needs the recipe). A pattern turns "Invalid Pattern" only after a revoke; the item is kept and re-decodes after the recipe returns [verified]. | Revoke = admin repair, documented as "restart afterwards". |
| EMI/JEI | Locked recipes never reach the client, which matches `show_locked_recipes = false`. There is no next-Age recipe preview in EMI. Book pages that reference locked recipes stay empty until unlock. | The quests on K must explain what comes next. Books are gated anyway. |
| Stale per-block-entity caches | Occultism `DimensionalMineshaftBlockEntity.possibleResults` and Ars `EnchantingApparatusTile` memo keep old results [verified]. | These only delay an unlock. Flush them with a generation counter (§4.6). |
| Code-added recipes after TAIL | IE arc-furnace recycling recipes are built later from crafting recipes and escape the filter. Create Dragons Plus writes 175 sandpaper recipes into `byType`/`byName` at the TAIL of `ReloadableServerResources#updateRegistryTags`. | Arc furnace: harmless, because their inputs are locked items. Everything added before the end of `updateRegistryTags` is caught by the late pass (§4.7, 0.2.1). |
| KubeJS | Server scripts run again on every reload. | Scripts must stay idempotent (they are data scripts today). |
| Operator `/reload` | Re-applies the same filter with the current AgeState. | Consistent, no special case. |
| Boot ordering | Recipes load before `MinecraftServer` and SavedData exist. | Mirror file plus fail-strict fallback (§3.2). |
| Chemical outputs (Mekanism gases etc.) | Not mapped to Ages. | They pass the filter. The machines producing them are block-locked by ProgressiveStages; accepted gap. |

Alternatives, rejected as primary route:
- **Per-mod mixins:** L effort, more than 40 targets, no client sync, and they break on updates. The per-family hook list in `core-integration.md` §3 stays as the fallback for single gaps.
- **KubeJS output removal:** `UnknownKubeRecipe.hasOutput` only sees `getResultItem`, so it misses IE, Ars, MA, DE and Ad Astra, and it is slow in Rhino.
- **ProgressiveStages alone:** 3.0.5 has no machine gate [verified].

**Phase-2 fallback (hot swap), only if M0 measures the stall above 10 s or the client rebuild above 30 s:** keep the unfiltered master list and call `RecipeManager.replaceRecipes(filtered)` [verified public]. Flush the known caches: `MekanismRecipeType.clearCache()`, TFC `IndirectHashCollection.reloadAllCaches(manager)`, Create `RecipeFinder`/`RecipeTrieFinder`, IE `CachedRecipeList` counter. Then send `ClientboundUpdateRecipesPacket`. This costs under 1 s, but tags cannot be hot-swapped, so m1 and m3 then need the Java fallbacks.

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
           ars.ApparatusMixin; ps.*
  config/  ServerConfig, ClientConfig
```

Rules:
- Every class that touches another mod lives under `compat/<mod>` or `mixin/<mod>` and is loaded only when `ModList.get().isLoaded(modid)`.
- Mod-specific mixins are skipped by `FirmagesMixinPlugin` (`IMixinConfigPlugin.shouldApplyMixin` checks `LoadingModList`).
- MixinExtras ships with NeoForge.

### 1.4 Dependencies (update `build.gradle` and `neoforge.mods.toml`)

| Mod | Pack version | Kind | Needed for |
|---|---|---|---|
| ProgressiveStages | 3.0.5 | required, compileOnly (libs/) | stage events and grant API |
| KubeJS | 2101.7.2-build.377 | optional, compileOnly (**add**) | binding `FirmAges` |
| Modonomicon | 1.120.7 | required, compileOnly (**add**) | ring multiblocks, ghost preview |
| Immersive Engineering | 12.4.2-194 | optional | `MineralMix` mixin |
| Draconic Evolution / Brandon's Core / CCL | 3.1.4.633 / 3.2.1.309 / 4.6.1.529 | optional (also in the GameTest run since 0.4.0) | reactor controller and fuel port, Gathering |
| Stargate Journey | 0.6.49 | optional, compileOnly (libs/, fetched from `mods/sgjourney.pw.toml`; also in the GameTest run) | The Origin's return gate (`compat/sgjourney`); the space location is plain data |
| Occultism | 1.224.4 | optional | mineshaft flush, extractor |
| Ars Nouveau | 5.13.2 | optional, compileOnly (**add**) | apparatus flush, extractor |
| Create, Mekanism, AE2, TFC | pinned in `gradle.properties` | optional | extractors, OreGuard drop override |
| Jade | 15.10.6 | optional, compileOnly (libs/, fetched from `mods/jade.pw.toml`; also in the GameTest run, M6) | shrine tooltip (`compat/jade`) |
| GeckoLib | 4.9.3 | optional, compileOnly (**add**, M9) | animated idol |
| FTB Quests/Teams | pinned | none at runtime | quest data only; FTB Teams is read for the one-team guard (optional) |

All entries use `ordering = "AFTER"`; everything except ProgressiveStages and Modonomicon is `type = "optional"`.

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
| `ServerTickEvent.Post` | `ReloadScheduler.tick` | Runs the due reload |

### 3.2 Snapshot rules for the filter (`AgeState.snapshotForReload()`)

1. If the server is running, use the live AgeState.
2. At initial load on a dedicated server, use the mirror file.
3. If the mirror is missing or corrupt, or on singleplayer/dev, use `gate.bootFallbackStages` (default `["dawn"]`, keep it equal to the PS starting stages) and let `reconcileBoot` reload after start. This path is fail-strict and never fails open.

### 3.3 ReloadScheduler

- `request(reason)` sets `dueTick = now + gate.reloadDelayTicks` (default 60, 3 s into the ceremony) if no reload is pending. Otherwise it extends the due tick up to `gate.coalesceTicks` (default 100), so a quest reward that grants `age_x` and `mob_x` together causes one reload.
- On the due tick, the server thread runs:
  1. Send `ReloadStatePayload(true)` to all players.
  2. `server.reloadResources(server.getPackRepository().getSelectedIds())` [verified signature].
  3. When that completes (still on the server thread): `CacheGeneration.bump()`, `ReloadStatePayload(false)`, and a log line with the duration.
- A request that arrives during a running reload queues exactly one follow-up.
- REVOKED reloads too and sends operators the warning "Age revoked: restart the server to clear in-progress items".

### 3.4 KubeJS binding (`compat/kubejs`, plugin registered via `kubejs.plugins.txt`)

- `FirmAges.isUnlocked(stageId)`: boolean.
- `FirmAges.unlockedAges()` and `FirmAges.lockedAges()`: lists of stage ids.
- `FirmAges.lockedOreBlocks()`: block ids of `age_blocks/<locked>`, read from the tag JSON on the reload path, because tag events run before tags are bound.
- `FirmAges.prospectingEnabled()`: `prospecting.enabled` (true while the server config is not loaded yet).

### 3.5 Implementation notes (M1)

- **KubeJS tag pre-capture [verified, dev GameTest log]:** KubeJS 2101 runs `ServerEvents.tags` handlers while the server scripts load, not inside the tag loader. On a reload that happens at the start of `ReloadableServerResources#loadResources`; the mixin `ReloadableServerResourcesMixin` (priority 500) captures that load's resource manager first, so the answer uses the new tag JSON. On the initial load the handlers run before the resource manager exists, so the answer comes from an empty index. `AgeService.checkLoadAnswers` compares every answer given during a load with the final index and, if the initial-load answer was wrong, requests one reload right after start (fail-strict). A handler that reads tag contents makes KubeJS drop the pre-capture and run the handlers inside the tag loader instead, where the initial-load answer is right (M3, §5.1).
- **Boot reconcile:** the reload after start happens when the boot snapshot was used during the load and differs from AgeState, when ProgressiveStages teams hold Ages that AgeState lacked, or when a binding answer was stale. A missing mirror alone does not reload when the fallback equals AgeState, because that reload would rebuild identical data.
- **Bulk events only add Ages:** a `StagesBulkChangedEvent` is one team's view (for example a new player's own team on login), so it cannot prove that the union lost an Age. Ages are removed only by `StageChangeEvent` REVOKED (or `ages simulate revoke`).
- **Boot fallback config:** server configs load after the initial datapack load, so `gate.bootFallbackStages` is read directly from `<world>/serverconfig/firmages-server.toml` (the per-world override), then `config/firmages-server.toml` (where NeoForge 21.1 keeps server configs), then `defaultconfigs/` on a dedicated server; otherwise the default `["dawn"]` applies.
- **Reload cost (0.3.2, `dev/poc-results.md` "Reload performance"):** with the pack at age_9 and 2 players a reload stalls 6.5 to 7.2 s (8.0 s for the first one after a boot), below the 10 s limit, so the reload path stays and the phase-2 hot swap is not built. ProgressiveStages 3.0.5 `syncPlayer` sends the lock sync twice per player (directly and again inside `sendStageSync`); `mixin/ps/SyncPlayerMixin` and `SendStageSyncMixin` skip the second one inside the same `syncPlayer` call only (`compat/progressivestages/LockSyncDedupe`, all injections `require = 0`, packet order unchanged). The `Age reload ... finished` line counts the skipped syncs. Open: every stage change outside a reload still resyncs each team member (about 0.3 s each), which freezes the tick of an Age grant for 2.6 s with 2 players.

## 4. m2: machine-recipe Age gate

### 4.1 Hook

- `mixin/RecipeManagerMixin`, target `net.minecraft.world.item.crafting.RecipeManager`, priority 1500 (above KubeJS 1100).
- `@Inject(method = "apply", at = @At("HEAD"))`: keep a reference to the JSON `Map<ResourceLocation, JsonElement>`. KubeJS rewrites this same map instance at its HEAD (`map.clear(); map.putAll(...)`) [verified], so reading it at TAIL gives the post-KubeJS JSON.
- `@Inject(method = "apply", at = @At("TAIL"))`: `RecipeGate.filter(this, json, resourceManager)` builds `AgeIndex`, classifies every `RecipeHolder`, and calls `this.replaceRecipes(kept)`, which rebuilds `byName` and `byType`.
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
4. Any collected item or fluid is in a locked Age: DROP. A tag output counts as locked only if all tag members are locked.
5. Otherwise KEEP. When no output was detected at all, record the type as "undetected" for the audit.

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

## 5. m3: miner filter, and m1: prospecting

| Target | Hook | Behaviour |
|---|---|---|
| Mekanism Digital Miner | Tag `mekanism:miner_blacklist`: a KubeJS `ServerEvents.tags('block')` script adds `FirmAges.lockedOreBlocks()` on every reload (**hand-off to kubejs/**). `ThreadMinerSearch.run` and `TileEntityDigitalMiner.tryMineBlock` both honour the tag [verified] | Locked ores are neither found nor counted in the GUI |
| IE Excavator (including TFC-IE Crossover veins) | `mixin/ie/MineralMixMixin`: `@ModifyReturnValue(method = "getRandomOre(Ljava/util/Random;)Lnet/minecraft/world/item/ItemStack;")` on `blusunrize.immersiveengineering.api.excavator.MineralMix`, with `@Shadow getRandomSpoil(Random)` [verified callers: `ExcavatorLogic#fillBucket`] | If the rolled ore item is locked, return `getRandomSpoil(rand)`. Mixes are never removed |
| Occultism mineshaft | covered by m2 (`occultism:miner` recipes) plus the §4.6 flush | New ores appear on the next tick after an unlock |
| All fake-player breakers (Create drill and deployer, Mekanism Lasers, the Digital Miner as a safety net) | `miner/OreGuard`: `BlockEvent.BreakEvent` with `getPlayer() instanceof FakePlayer` on a locked `age_blocks` block → `setCanceled(true)`. The Digital Miner then skips the block [verified]. `BlockDropsEvent` with breaker `null` or a FakePlayer on a locked block → replace the drops with the PS `drop_as` item from `LockRegistry.getOreOverridesFor(block)`, or no drops if there is no override | Covers Create's `BlockHelper.destroyBlockAs`, which always posts `BlockDropsEvent` [verified]. mekanism_lasers and mekmm are **[PoC]** |

m1 (config `prospecting.enabled`, default `true`; Felix confirmed on 2026-09-30) is a KubeJS tag script (**hand-off**). It removes `FirmAges.lockedOreBlocks()` from `tfc:prospectable` and `precisionprospecting:prospectable_mineral` on every reload; the unlock reload restores them. No Java is needed. The Java fallback, used only with the hot swap, is `@WrapOperation` on `Helpers.isBlock` in `PropickItem#scanAreaFor` (overload `(Block, TagKey)`) and `PropickItem#useOn` (overload `(BlockState, TagKey)`, checked against TFC 4.2.11 bytecode), plus the equivalent calls in `ProspectorItem#scanAreaFor/useOn` [verified in research]. Both overloads must be wrapped.

### 5.1 Implementation notes (M3 and m1, firmages-core 0.2.0)

- **IE excavator:** `mixin/ie/MineralMixMixin` is an `@Inject` at RETURN of `getRandomOre(Ljava/util/Random;)` (cancellable) rather than `@ModifyReturnValue`, so the `Random` parameter reaches `getRandomSpoil`. `miner/ExcavatorFilter` treats a roll as locked when its item is locked (`age_items`, including `disabled`) or when it is a block item whose block is in a locked `age_blocks` Age (IE mixes resolve to ore block items). Config `miner.ieExcavator`. Verified in the dev GameTest with IE 12.4.2 loaded: 0 locked ores in all default mixes while `age_2` is locked, iron ore rolls again after the unlock.
- **OreGuard:** `miner/OreGuard`, `BreakEvent` (priority HIGH) and `BlockDropsEvent` (priority LOW) as in the table above; the disguise drop is the first `LockRegistry.getOreOverridesFor(block)` entry's `dropAs` item. Config `miner.oreGuard`. Real players are never touched.
- **KubeJS hand-offs (pack files, also run by the dev GameTests):** `kubejs/server_scripts/firmages/miner_blacklist.js` adds `FirmAges.lockedOreBlocks()` to `mekanism:miner_blacklist` [tag verified in Mekanism 10.7.19]; `kubejs/server_scripts/firmages/prospecting.js` (gated by `FirmAges.prospectingEnabled()`) removes them from `tfc:prospectable` [verified, TFC 4.2.11: `["#c:ores"]`, used by the TFC propick and the Precision Prospecting hammer and drill] and `precisionprospecting:prospectable_mineral` [verified, Precision Prospecting 2.1: its mineral prospector]. KubeJS 2101 `remove` only drops direct entries, so a tag that contains a locked ore is flattened to its resolved members minus the locked ores (`getObjectIds()`, registered ids only).
- **Initial-load answer [verified, dev GameTest log]:** `getObjectIds()` in the KubeJS pre-capture returns nothing and marks the pre-capture invalid, so KubeJS runs every block tag handler inside the tag loader, after this mod captured the load's resource manager. `FirmAges.lockedOreBlocks()` is then right on the initial load too; `AgeService.checkLoadAnswers` ignores the discarded pre-run answers when in-loader answers exist, so a plain boot needs no extra reload (the 0.1.1 note "every boot costs one extra reload" no longer applies with these scripts). Scripts must call `FirmAges.lockedOreBlocks()` only inside `ServerEvents.tags`.

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

### 7.6 Blessings and sanctuary

- Permanent attribute modifiers with ids `firmages:blessing/<name>`, all values from config: `MAX_HEALTH` ADD_MULTIPLIED_BASE (+0.05 per health blessing), `BLOCK_BREAK_SPEED` (+0.05), `BLOCK_INTERACTION_RANGE` (+0.5), `FALL_DAMAGE_MULTIPLIER` (−0.2), `SAFE_FALL_DISTANCE` (+1), `LUCK` (+1).
- They are reapplied on login, respawn, stage change and intact change, and removed while the shrine is dormant.
- XP: `PlayerXpEvent.PickupXp` scales the orb value (+10 %).
- Sanctuary: `MobSpawnEvent.PositionCheck` → `Result.FAIL` for `MobCategory.MONSTER` with spawn type NATURAL or CHUNK_GENERATION within `12 + 4 * tier` blocks of the heart while intact. Spawners, Gateways and ritual summons are untouched.
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
- **[PoC] in the client (Felix, `dev/dev-client.md`):** ghost preview alignment (anchored at the block below the heart), beam and sky tint with Sodium and Enhanced Celestials, the plinth item renderer, TFC bell and lamp rites in the world, the sounds (vanilla events through `sounds.json`), the prayer speed-up with 2 players.

### 7.9 Implementation notes (M6, firmages-core 0.5.0)

- **Blessing effects (`shrine/Blessings`):** every second (`ServerTickEvent.Post`, 20 ticks) each online player gets exactly the effects of the blessings of the awakened tiers 0..awakened-1 while the shrine is intact (`ShrineState`), and loses every other blessing modifier. Scope: players **within the blessing radius** of the heart, which is the sanctuary radius `shrine.sanctuary.baseRadius + perTier * (awakened - 1)` (12 at Bronze, 44 at age_9) in the heart's dimension (task decision 2026-10-01, deviation from §7.6 "permanent"); `shrine.blessings.everywhere = true` makes attributes and XP team-wide as §7.6 planned, status effects stay within the radius. `attribute`: transient modifier `firmages:blessing/<name>/<index>` (not saved; reapplied within a second after login, respawn or a data reload; a changed amount is updated in place); unregistered attributes are skipped with one WARN. `mob_effect`: ambient, no particles, 260 ticks, refreshed at 220 or less, never over a stronger or infinite effect; it fades within 13 s after leaving (night vision does not flicker above 200 ticks). `xp_bonus`: `PlayerXpEvent.PickupXp` raises `ExperienceOrb.value` by `value * bonus` (the fraction by chance), so Mending sees it too. Max health falls back at once when a modifier goes (vanilla clamps the health). No shipped blessing uses `mob_effect` (no aura in the docs, decision 2026-10-01); the type is tested with a synthetic blessing. The Skyreading panel (TFC date, season, rain) is not built; Skyreading gives +5 % movement speed instead. `/firmages shrine status` lists the blessings in force and the scope.
- **Energy rite:** `ShrineService.storedEnergy` sums `IEnergyStorage#getEnergyStored` of the ring positions with the rite's key (side null first, then each side), so any FE storage block of the ring counts; nothing is drained. Hint arguments: the amount and the current sum. Ring 5 uses its four IE HV capacitors (`V`, 4,000,000 FE each); the heart itself still has no buffer, so the FE rites of rings 6 and 8 from `core-shrine.md` (10 M and 100 M FE in the heart) are not shipped (no storage block in those rings). `shrine.energyCapacity` stays unused.
- **Jade (`compat/jade/FirmagesJadePlugin`, `@WailaPlugin`, compileOnly Jade 15.10.6 from `mods/jade.pw.toml` via `fetchLibs`):** client-side block components from synced state only: the heart shows "Awakened tiers: N of 9", whether the next ring stands (`ready`) and "The heart is cold" while unlit; a plinth shows its Age ("Plinth of the Arcane Age"), "Relic: X", "Offering: X", "Its relic is lent out" or "Empty". Config toggles `config.jade.plugin_firmages.shrine_heart` / `offering_plinth`. Jade is in the GameTest run so the plugin class loads on a server; the tooltip itself needs a client **[PoC]**.
- **Tests:** U `ShrineRulesTest` (9: plus blessing effect parsing and rejection, the lending-rite lookup, energy and relic_returned validation, the shipped blessings: every one has an effect, +15 % max health in total, Attunement +10 % XP; ring 5 energy 1 M on `V`, ring 8 lends tier 3), `ShrineRingFilesTest` (energy keys exist in their ring). G `ShrineGameTests.keystoneLending` (age_7 lends nothing; age_8 lends, the player holds it, plinth glows and is unbreakable, record kept and lent, ring and intact unchanged, NBT round trips of plinth and SavedData, the tier-8 offering refused while lent, a wrong item refused, return enshrines one item, then only the rings stop the offering, lend and return again), `blessingEffects` (nine tiers near the heart: max health 23, the modifier ids, speed, break speed, reach 5.0, fall multiplier 0.8 and safe fall 4, luck 1, XP +10 % with an orb of 20 worth 22; outside the radius nothing; `everywhere` restores them far away; a broken shrine nothing; three tiers only theirs; a status effect near only), `energyRiteReadsCapacitors` (a real IE HV capacitor charged through its capability is read back exactly).

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

## 9. Config (NeoForge `ModConfigSpec`)

`firmages-server.toml` (per world):

| Key | Default | Meaning |
|---|---|---|
| `gate.enabled` | true | m2 filter on/off (off = log only) |
| `gate.bootFallbackStages` | ["dawn"] | Fail-strict snapshot when there is no mirror |
| `gate.reloadDelayTicks` / `gate.coalesceTicks` | 60 / 100 | Reload timing |
| `gate.exemptRecipeTypes` | mineral_mix, tfc:collapse, tfc:landslide | Never filtered |
| `gate.allowRecipes` / `gate.denyRecipes` | [] | Per-recipe overrides |
| `gate.reloadOnRevoke` | true | |
| `miner.ieExcavator` / `miner.oreGuard` | true / true | m3 parts |
| `prospecting.enabled` | true | m1; read by the KubeJS script through `FirmAges` |
| `reactor.itemHandler` | true | m4 capability |
| `reactor.controller.enabled` | true | §6.3; there is no auto-restart (Felix, 2026-09-30) |
| `reactor.controller.shutdownAtConversion` / `.minFuel` / `.maxChaos` | 0.80 / 10368 / 0 | §6.3 |
| `origin.gatherRadius` / `origin.gatherCooldownSeconds` | 6 / 30 | §16.3: every online non-spectator within this radius of the altar starts the fight; a broken-up Gathering re-arms after the cooldown |
| `origin.finaleGateway` | `firmages:the_origin` | §16.3: the Gateways to Eternity gate the boss script opens and whose completion in The Origin summons the final boss (`FirmAges.finaleGateway()`) |
| `shrine.prayerSeconds` / `shrine.minPrayerSeconds` / `shrine.prayRadius` | 10 / 4 / 6 | |
| `shrine.sanctuary.baseRadius` / `.perTier` | 12 / 4 | |
| `shrine.blessings.everywhere` | false | §7.9: false = blessing effects only within the sanctuary radius of the heart; true = attributes and XP team-wide (§7.6). The effect values themselves are data (`firmages_shrine/blessing/*.json`) |
| `shrine.energyCapacity` | 100000000 | unused: the heart has no FE buffer; the `energy` rite reads the ring's storage blocks (§7.9) |
| `shrine.permanentBeam` / `shrine.clearWeather` | false / true | |
| `shrine.maintenanceSeconds` | 60 | §17: maintenance mode ends by itself after this many seconds (5..3600) |
| `shrine.consecrationTicks` | 120 | §17: a ring's consecration from the heart outward takes this long (0..1200), after the Age reload |
| `debug.allowSimulate` | false | Enables `/firmages ages simulate` (self-tests only) |

`firmages-client.toml`:

| Key | Default |
|---|---|
| `effects.skyTint` | true |
| `effects.stopMusic` | true |
| `effects.particleScale` | 1.0 |
| `effects.ambientDrone` | true |

## 10. Commands (`/firmages`, permission level 2)

| Command | Purpose |
|---|---|
| `ages` | Unlocked/locked Ages, mirror status, last reload duration |
| `ages sync` | Reconcile with ProgressiveStages and reload |
| `ages simulate grant|revoke <stage>` | Change AgeState without PS (only with `debug.allowSimulate`) |
| `recipes audit` | Write GateReport (§4.4) |
| `recipes why <recipeId>` | Detected outputs, Ages and decision for one recipe |
| `recipes locked <stage>` | Recipe ids removed for that Age |
| `dump registry` | Items and blocks per mod as JSON for the tag generator |
| `reload` | Force a filtered reload now |
| `shrine status` | Heart, rings with missing blocks, tier, offering, plinth, rites, phase, prayer, sanctuary (0.3.0; replaces `info`, `validate`, `resync`) |
| `shrine locate` / `shrine relics` | Heart position / enshrined relics |
| `shrine extract <plinth>` | Admin repair: take the relic or offering off a plinth (replaces `setrelic`, `release`) |
| `shrine simulate_pray` | Complete the current prayer (needs `debug.allowSimulate`) |
| `shrine maintenance on\|off\|status` | M7 (§17): maintenance mode for everyone (the same as the sneak-punch on the heart); status shows the seconds left and the stored originals |
| `shrine consecrate <ring>` | M7 debug: consecrate a complete ring now (from the heart outward over `shrine.consecrationTicks`), awakened or not |
| `shrine originals` | M7: stored originals per ring and how many are not consecrated now (holes); the full list (position, original state) goes to the server log |
| `debug player join|leave <name>`, `debug use <name> <pos> [sneak]`, `debug pray <name> <pos> <seconds>`, `debug run <name> <command>`, `debug punch <name> <pos> [sneak]`, `debug mine <name> <pos>` | Headless test players for the dedicated test server (needs `debug.allowSimulate`): a real `ServerPlayer` on an in-memory connection (vanilla GameTest mock style, every payload channel accepted), offline UUID of the name; it logs the firmages payloads, titles and chat it receives as `[debug-player <name>]`. `punch` is one left click (start of mining, then abort; with `sneak` the maintenance toggle at the heart), `mine` mines the survival way (start, then stop once the destroy progress reaches 0.7; progress 0 aborts). Used for the server-side shrine proof (`dev/poc-results.md`, "Shrine M4 and ceremony") |
| `ceremony preview <stage> [full|short]` | Play the ceremony to yourself, no grant |
| `reactor status [pos]` | Every loaded reactor controller (or the one at `pos`): state, reason, reactor phase, fuel, chaos, conversion, redstone, comparator, settings, buffers (0.4.0; replaces `reactor info`) |
| `origin tp [players]` | Teleport to The Origin's arrival point (normal teleport path, so the age_9 dimension lock applies outside creative) |
| `origin status` / `origin reset` / `origin rebuild` | Arena version, return gate, altar, Gathering count, boss state / let the Gathering and the boss happen again (finale_won stays) / rebuild the arena and place the gate again |
| `selftest <suite|all>` | Server-side test suites (§12), report in `logs/firmages-selftest.json` |

## 11. Network (all S2C, registered in `RegisterPayloadHandlersEvent`, version "1")

| Payload | Fields | Client action |
|---|---|---|
| `firmages:age_transition` | stage, tier, variant (FULL/SHORT), `Optional<GlobalPos>` shrine, beam colour, sky tint, sting id | `CeremonyPlayer.start` |
| `firmages:reload_state` | boolean running | actionbar hint |
| `firmages:shrine_preview` | multiblock id, anchor pos, rotation, clear flag | `PreviewBridge` → `MultiblockPreviewRenderer.setMultiblock` + `anchorTo` [verified] |
| `firmages:shrine_fx` | pos, fx type (refused, returned, rekindled, dormant) | small local response |
| `firmages:shrine_sync` | heart dimension, maintenance, seconds left, position -> original block id | M7 (§17): client copy for the destroy-progress prediction (consecrated blocks break only in maintenance) and the Jade tooltip; on login, on every maintenance change, at most once a second when the originals change |

The BE state uses vanilla block-entity sync. There is no C2S payload.

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
| core | Reload timing and tick freeze (spark) plus EMI rebuild on 1 client | M (M0) | stall ≤ 10 s, client ≤ 30 s, otherwise phase 2 |
| m2 | `JsonOutputWalker` on fixtures copied from the jars (Create mixing, IE crusher with tag output, Mekanism enriching, TFC heating with `result_fluid`, Occultism miner, Ars apparatus, DE fusion, MA infusion, Ad Astra) | U | expected output sets |
| m2 | Vanilla: `minecraft:iron_ingot` tagged `age_2`, locked → smelting recipe absent, crafting impossible; simulate grant + reload → present | G | `byKey` absent/present |
| m2 | Canonical cases `selftest/gate_cases.json` (one recipe per machine family, with its Age): absent while locked, present after `simulate grant` + reload | P | all cases pass |
| m2 | Audit: no undetected recipe types beyond `selftest/accepted_undetected.json` | P | the list is unchanged |
| m2 | `mineral_mix` count identical before and after the filter | P | equal |
| m2 | MA infusion recipe of a locked Age is absent (TAIL ordering against Cucumber) | P | absent |
| m2 | EMI shows new recipes without relog; an AE2 pattern encoded before an unlock still works | M | visible, pattern valid |
| m2 | Machine caches follow the reload: for each `gate_cases` family, a machine that was loaded and idle before the unlock processes the newly unlocked recipe right after it (Create basin, IE crusher, Mekanism enrichment chamber, TFC barrel, Occultism ritual, Ars apparatus, DE fusion, MA infuser, Ad Astra) | M (P where a machine can be driven by command) | processes without chunk reload or relog |
| m3 | `MineralMix#getRandomOre` on the Crossover galena mix, 10,000 rolls with silver locked → 0 silver; unlocked → silver present | P | counts |
| m3 | OreGuard: FakePlayer `BreakEvent` on a locked ore → canceled; `BlockDropsEvent` with null breaker → drops replaced | P (places blocks in a reserved test area) | asserted |
| m3 | `mekanism:miner_blacklist` contains all `lockedOreBlocks()` after reload | P | subset check |
| m3 | Digital Miner on a disguised tin vein; Create drill on a locked ore | M | no ore yield |
| m1 | `tfc:prospectable` contains no locked ore after reload | P | disjoint |
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
| M10 | Own sounds (CC0 or self-made), particles, sky tint, Sodium check (was M7 until 2026-10-01) | M | M test with Sodium and Enhanced Celestials |

M0–M4 must ship before the first playtest beyond the Stone Age. Shipping: copy the jar to `FirmamentAges/mods/` and run `packwiz refresh` (hand-off).

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
3. FTB Quests (done 2026-10-01, `dev/quests-notes.md`): the Stone, Bronze and Iron goals are StageTasks on the next Age without stage reward and without an item task ("Offer the X at the Shrine"); Dawn's First Spark keeps its `age_0` reward. The Stone Age strand "Hearth & Shrine" ends in "Raise the Shrine", an ObservationTask `block_state` `firmages:shrine_heart[ready=true]`; its step "The Shrine Heart" observes the placed heart (`firmages:shrine_heart`). Both use the block id and the `ready` property of §2.5 as 0.3.0 registers them. Optional "Raise the ring" quests for rings 1 and 2 are still open.
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
- **M6 (core-m6), needs a client or the pack server:** the Jade tooltips; lending with the real Arcane Keystone and the Marid ritual (the GameTest uses stand-ins); the IE HV capacitors of ring 5 charged by real wires (the GameTest charges one through its capability); max-health blessings together with TFC's nutrition health (old PoC); how many `mob_9` mobs really spawn in the lit arena; the boss script's gateway id check (`GatewayRegistry.INSTANCE.getKey(gate.getGateway())`) in the real fight. The Skyreading panel is not built. Relics stay locked from age_9 on until the Gathering (M8) or its fallback release exists, so an Awakened Keystone returned to plinth 4 waits there.

- **M7 (core-m7), needs a client or the pack server:** the consecrated models (multipart base plus accent overlay with NeoForge `neoforge_data` full-bright faces, cutout and translucent per part) and the heart's maintenance cage; the client's destroy prediction during maintenance (from `firmages:shrine_sync`); breaking with the real TFC, IE and Mekanism ring blocks and their original drops (loot of the original with the player's tool, else its block item; IE tile drops have no block entity and fall back to the item); consecration of block-entity blocks of the late rings (IE capacitors, lanterns and floodlights, Mekanism casings: their block entity is removed first, stored energy is lost); Cataclysm bosses near the shrine (tags). Doc 11 (German player summary) does not describe M7 yet.
- **Felix (decided 2026-09-30):** m1 build; reactor controller refuels and signals READY by redstone, NO auto-restart (ACTIVATE state stays disabled); offerings are ENSHRINED (kept as relics, Keystone lent in Age 8, Ultimate Singularity forged at the shrine); EMI shows no next-Age preview, by design.
- **PoC:** reload stall and client rebuild (M0); TAIL ordering against Cucumber; StageChangeEvent once per member; mekanism_lasers/mekmm breakers; DE injector placement; TFC nutrition health vs percentage modifiers; sky overlay with Sodium/Enhanced Celestials; rings 3..8 built in the world and their rites (the ids are checked against the registry dump, `lit` and `active` against the blockstate files of the TFC and IE jars; the IE lantern and floodlight `active` flip and TFC candles are unverified in game); machine caches actually refresh after the unlock reload (§12, only `RecipeManager` presence is covered by P tests). The Awakened Keystone item id is `firmages:awakened_keystone` (resolved 2026-10-01, §7.4). `SelectMusicEvent` exists in NeoForge 21.1.252 [verified, review], so the ceremony can suppress music with it instead of a mixin.
- **After M2/M3 (done 2026-10-01, `dev/poc-results.md`):** the audit is reviewed: the 37 types with no detected output are accepted with a reason in `dev/data/accepted_undetected.json` (checked by `poc_analyze.py` G-4), no extractor follow-up. Recipes that only a chance byproduct of a later Age keeps out are not allowlisted; `kubejs/server_scripts/recipes/byproducts.js` strips the byproduct until its Age (checked by G-3). The gate time is measured. `gate_cases.json` is still not written (the G-checks of `poc_analyze.py` cover the pack instead).

## 16. m10: The Origin and the end-boss scaffolding (firmages-core 0.4.0)

Design source: Doc 08 §10 (finale: Ultimate Singularity, Stargate, The Origin, script-first end boss r10 A). The mod provides the place, the way in, the trigger and the result; the fight itself (Gateways to Eternity waves with Maledictus and Ignis, then "The Primordial", a Cataclysm boss with KubeJS attributes and phases) is a KubeJS script of the pack. Not to be confused with the shrine's Gathering of §7.5 (ring 9, M8).

### 16.1 The dimension

- **Data in the mod jar** (`data/firmages/`): `dimension/origin.json` (generator `minecraft:flat` with no layers, biome `firmages:origin`, no features or structures: a void), `dimension_type/origin.json` (no skylight, End sky effects, `ambient_light` 0.1, fixed time, beds and anchors do not work, height 0..256), `worldgen/biome/origin.json` (no precipitation, no spawns, no features, dark violet fog). Doc 08 §10.3 put the dimension under `kubejs/data/`; it ships in the jar so the GameTests and the arena code see the same files, and the pack can still override it with the same path under `kubejs/data/firmages/`.
- **Lock:** `config/progressivestages/stages/age_9.toml` `[dimensions] locked = ["id:firmages:origin"]` (verified: the id matches the dimension key). ProgressiveStages 3.0.5 `DimensionEnforcer` checks a player's dimension change before travel and bounces a player back who arrived in a locked dimension anyway (`handlePostTravelSafetyNet`); creative players bypass it (`allow_creative_bypass`). **[PoC]** the bounce for Stargate Journey's wormhole transport is unverified (needs a client). G `OriginGameTests.originNeedsAge9` uses a GameTest copy of the stage file (`src/gametest/config/progressivestages/stages/age_9.toml`, copied into the run by `prepareGametestConfig`): a survival player without age_9 is locked out and a teleport leaves him where he was.
- **Own lock (M6, `origin/OriginAccess`):** `EntityTravelToDimensionEvent` (priority HIGH) cancels every travel of a `ServerPlayer` into `firmages:origin` while the server-wide AgeState lacks `age_9`, with an actionbar line (`firmages.origin.locked`). Every dimension change goes through `Entity#changeDimension`, which posts the event [verified, 21.1.252 source]: portals, every command whatever its permission level (`/tp`, `/execute in`, FTB Essentials `/back` and `/tpa`), the Mekanism teleporter, Ars Nouveau warp, and Stargate Journey's wormhole (`Wormhole#transportPlayer` calls `ServerPlayer#teleportTo(ServerLevel, ...)`, entities `changeDimension` [verified, javap 0.6.49]). Creative and spectator pass (like ProgressiveStages' creative bypass); operators in survival do not. Vehicles go alone, a rider is checked on his own (vanilla moves passengers one by one). G `OriginGameTests.originTravelLock`: the event is cancelled without age_9 and not for other dimensions; the wormhole call leaves the player outside; creative passes; after a real ProgressiveStages grant of age_9 the same call brings him in and back; the grant is revoked again.
- **Spawns (M6, `mob_9`, `origin/OriginSpawns` + `OriginSpawnList`):** `data/firmages/origin/spawns.json` (`{"spawners": {"monster": [{"type", "weight", "minCount", "maxCount"}]}}`, overridable under `kubejs/data/firmages/origin/`, reloaded with the datapacks) is the whole spawn list of The Origin: `LevelEvent.PotentialSpawns` (LOWEST) replaces every category's candidates there, so biome modifiers add nothing and unlisted categories (passive, ambient, water) stay empty. Shipped: `minecraft:enderman` 10 (1-2), `cataclysm:endermaptera` 6 (1-3), `cataclysm:ignited_revenant` 2 (1), `cataclysm:ender_golem` 1 (1); entity ids of absent mods are skipped with a WARN. The dimension type's light rules stay (monsters only on block light 0), so the lit arena stays mostly free. U `OriginSpawnListTest`, G `OriginGameTests.originSpawnList` (monsters = enderman in the run without Cataclysm, no creatures, the overworld untouched).
- **GameTests and datapack dimensions:** vanilla's `GameTestServer` bakes its world from the flat preset with an empty dimension registry, so no datapack dimension loads there [verified, 21.1.252 source]. `mixin/GameTestServerMixin` (test infrastructure; a normal server never runs `GameTestServer`) passes this mod's own datapack dimensions into that bake.

### 16.2 Arena, Stargate address and return gate

- **Arena (`origin/OriginArena`, built once at server start, `OriginSavedData` `firmages_origin` keeps `arena_version` and `gate_built`):** a floating disc of radius 20 at y 64 around (0, 0) (polished deepslate with gilded-blackstone rings and sea lanterns, a blackstone-brick rim with eight lantern posts and a gap to the south, an inverted cone of mixed rock below), the `firmages:origin_altar` at (0, 65, 0) on a crying-obsidian dais, nine small floating islands, one per shrine Age (mossy cobble, cut copper, smooth stone, amethyst, bricks, oxidized copper, quartz bricks, end stone bricks, crying obsidian on top, an end rod each), 45 to 75 blocks out, and a bridge south to the gate pedestal. Vanilla blocks only. `/firmages origin rebuild` builds it again; bump `OriginArena.VERSION` to rebuild existing worlds.
- **Arrival point:** (0, 65, 14) on the arena, between gate and altar, outside the Gathering radius. Players dialling in step out of the gate at z about 30 and walk north past the DHD.
- **Altar `firmages:origin_altar`:** unbreakable in survival (hardness -1), no loot table, light 12; Age map `age_9`; never crafted; listed (optional entries) in every Cataclysm `*_immune` block tag and in `minecraft:wither_immune`/`dragon_immune`, because Cataclysm bosses break blocks outside those tags whatever their hardness (integration 0.4.0: after the gate waves and the boss the altar was gone, so no later Gathering could start; with the tags it survives).
- **Stargate Journey 0.6.49 [verified, javap and jar data]:** `data/firmages/sgjourney/space_location/origin.json` (point of origin `sgjourney:floating_islands`, symbols `sgjourney:galaxy_milky_way`, address region `firmages:origin`, `preload_stargate`; `in_stargate_network` defaults to true, `generate_in_address_tables` to false, so the address never appears on cartouches) and `data/firmages/sgjourney/address_region/origin.json` (name `solar_system.firmages.origin` = "The Origin"; Milky Way address **9-16-21-33-2-37** plus the point of origin, extragalactic 1-30-9-16-21-33-2; both `randomizable: false`). The address does not collide with the regions SGJ ships. The quests give the address (hand-off).
- **Return gate:** with Stargate Journey loaded, `OriginArena.buildGate` places SGJ's own template `sgjourney:stargate/milky_way/pedestal/stargate_pedestal_1` (Milky Way gate with its DHD) south of the arena (gate base (0, 66, 31) facing north, DHD (0, 65, 25)) and `compat/sgjourney/SgjGates.finishPlacement` does what SGJ's worldgen does for its structures: `generateInStructure` (SETUP to READY) and `generate()` (the gate joins the network, the DHD gets its energy core). Without that step a code-placed gate never joins the network, because its chunk does not tick. G `originDimensionArenaAndGate`: dimension type and biome, arena, altar, gate and DHD block entities, the space location with the region and the exact address, the gate in `StargateNetwork`. G `overworldGateCanDialTheOrigin`: a pedestal gate placed in the overworld test area dials 9-16-21-33-2-37 with SGJ's `Dialing.Action.SIMULATE_ENOUGH_ENERGY` and gets `CONNECTION_ESTABLISHED_INTERSTELLAR`; an unknown address gets `INVALID_ADDRESS`.
- **Commands:** `/firmages origin tp [players]` (normal teleport, so the age_9 lock applies), `origin status`, `origin reset`, `origin rebuild` (§10).

### 16.3 Gathering trigger, final boss, FINALE

- **Gathering (`OriginService.checkGathering`, every 20 ticks):** true when every online player who is not a spectator is alive in The Origin within `origin.gatherRadius` (6) of the altar, and the altar stands. On the transition to true, outside `origin.gatherCooldownSeconds` (30, in memory, reset at boot) since the last start and while the boss has not fallen, the mod tells the players (`firmages.origin.gathering`), increments the count in `OriginSavedData`, posts `OriginEvent.Gathering` (level, altar, players, count) on `NeoForge.EVENT_BUS` and runs the function tag `#firmages:origin/start` as the server at the altar (permission 2). The mod ships the tag empty; the pack adds its functions under `kubejs/data/firmages/tags/function/origin/start.json`.
- **Final boss:** any `LivingEntity` with the scoreboard tag `firmages.final_boss` (`entity.addTag(...)`; a dot, because selectors and `/tag add` cannot read a colon) that dies (`LivingDeathEvent`) **in The Origin** (M6: a tagged death in another dimension is logged with WARN and counts nothing). The first such death sets `won`, grants `finale_won` through ProgressiveStages to every online player (team mode: the first grant covers the team; refused grants retry with `grantStageBypass`; players who log in later receive it), plays the FINALE ceremony, posts `OriginEvent.Victory` (boss level, altar, boss) and runs `#firmages:origin/won`. A second tagged death does nothing. The boss AI, the waves and the phases stay out of the mod.
- **FINALE ceremony (`CeremonyService.startFinale`):** one FULL `AgeTransitionPayload` with stage `finale_won`, tier -1, at the altar: title "Beyond the Firmament" (`firmages.age.finale_won.title`), Caelum's line `firmages.shrine.voice.finale_won`, sting `minecraft:ui.toast.challenge_complete`, end-rod and totem particles, a white-violet sky, the blessing slot as "Trophies: MekaSuit and Meka-Tool are unlocked", and eight visual lightning bolts around the altar. No reload (finale_won is no Age). The beam is particles only (the beacon beam is drawn by the shrine heart's renderer). `kubejs/server_scripts/stages/on_stage_added.js` stays silent for `finale_won` from firmages-core 0.4.0 on, so there is one title.
- **KubeJS binding additions:** `FirmAges.originDimension()` (`"firmages:origin"`), `FirmAges.originAltar()` (an `int[3]`: x, y, z), `FirmAges.finalBossTag()` (`"firmages.final_boss"`), `FirmAges.isFinaleWon()`, and (M6) `FirmAges.finaleGateway()` (`origin.finaleGateway`, default `"firmages:the_origin"`).
- **Hand-off to the boss script (KubeJS):** listen with `NativeEvents.onEvent('dev.firmages.core.origin.OriginEvent$Gathering', e => ...)` or put a function into `#firmages:origin/start`; spawn the Gateways to Eternity gate and the waves around `FirmAges.originAltar()`; give the final boss `FirmAges.finalBossTag()`; do not grant `finale_won` from the script; open the gate `FirmAges.finaleGateway()` and summon the boss only when exactly that gate completes in The Origin (M6: `kubejs/server_scripts/finale/the_primordial.js` reads the id of `GateEvent$Completed`'s gate with `GatewayRegistry.INSTANCE.getKey(gate.getGateway())` [javap, Gateways 5.1.0 / Placebo 9.9.2] and ignores any other gate). After a wipe the Gathering fires again once the players come back after the cooldown, so the script must ignore a start while its own fight runs (or check its own state).
- **The pack's boss script** (`kubejs/server_scripts/finale/the_primordial.js`, content fixes 2026-10-01) takes the dimension from `FirmAges.originDimension()`, the altar from `FirmAges.originAltar()` and the tag from `FirmAges.finalBossTag()` (no copies of the mod's values); it ignores a Gathering outside that dimension or while its gate (`FirmAges.finaleGateway()`, default `firmages:the_origin`, `kubejs/data/firmages/gateways/the_origin.json`; integrated with 0.5.0) or a tagged boss is alive, and spawns the boss only on `GateEvent$Completed` of that gateway id in that dimension. A wipe re-arms after `origin.gatherCooldownSeconds`. Its own death listener (same tag) only announces and removes the phase helpers (tag `firmages.primordial_add`); `finale_won` stays with the mod.
- **Tests:** G `gatheringAndFinalBoss` (a creative headless player, `/firmages origin tp`, no start at the arrival point, one start at the altar with one event, no second start while gathered, none after breaking up within the cooldown, a function tag at the altar, an untagged zombie changes nothing, the tagged zombie's death wins, grants finale_won and sends exactly one FINALE payload at the altar, a second tagged death and a later Gathering do nothing; M6: a tagged zombie dying in the overworld changes nothing).

## 17. M7: Consecration and maintenance mode (Felix, 2026-10-01)

Felix's decisions of 2026-10-01: the shrine becomes **one unified structure**. When Caelum accepts a ring's offering the ring is consecrated: every ring block becomes a block of one mod family with a shared look; the Age materials are replaced completely, one colour accent per Age remains. The transformation is part of the ceremony, ring by ring from the heart outward. Consecrated blocks are unbreakable (survival mining, explosions, pistons, fluids); a team member toggles **maintenance mode** at the heart, and only then they can be broken and drop their original material. A broken ring pauses the blessing only; Ages are never revoked.

### 17.1 Blocks (naming contract with the textures workflow)

- `firmages:consecrated_<role>` for the roles `stone`, `brick`, `pillar`, `lamp`, `metal`, `glass`, `trim`, `scaffold` (`shrine/ConsecratedBlock`, `ConsecratedLampBlock`, registered in `ShrineRegistry.CONSECRATED`). Blockstate `accent` = 0..8, the ring / Age index (ring N is built in age_N); the lamp also has `lit` (default and always set true, light 15).
- Like bedrock: hardness -1, blast resistance 3,600,000, `PushReaction.BLOCK`, `forceSolidOn`, no loot table (no drops by default), `canBeReplaced(fluid)` false, `canEntityDestroy` false, `onBlockExploded` does nothing, no mob spawns on it. `glass` and `scaffold` are see-through (no occlusion, sky light passes). Tag `#firmages:consecrated` is in `minecraft:wither_immune`, `dragon_immune`, `features_cannot_replace`, `mineable/pickaxe` (speed during maintenance), every Cataclysm `*_immune` tag, `create:non_movable`, `c:relocation_not_supported` and `mekanism:cardboard_blacklist`. Endermen only take `#minecraft:enderman_holdable`. Block items exist for operators (creative tab Operator Utilities); survival has no source.
- **Models:** blockstate `multipart`: the base model `block/consecrated_<role>` (`cube_all` on `textures/block/consecrated_<role>.png`; the pillar is a `cube_column` with side `consecrated_pillar_side.png` and end `consecrated_pillar_top.png`; the lamp uses `consecrated_lamp.png` for `lit=false` and `consecrated_lamp_lit.png` for `lit=true`; glass `translucent`, scaffold `cutout`) plus, per accent, the overlay model `block/consecrated_accent_<n>` (a full cube with `textures/block/consecrated_accent_<n>.png`, `cutout`, full-bright through the element's NeoForge `neoforge_data` `block_light`/`sky_light` 15, no AO). Item models use the base (the lamp: the lit model). The heart gets `block/shrine_heart_maintenance` (a `consecrated_scaffold` cage, `cutout`) while `maintenance=true`. The textures come from `dev/gen_textures.py` (textures v2, `dev/textures-notes.md`); `--check` fails if a consecrated texture is not used by a model.

### 17.2 Roles and validation

- `consecration.json` (§2.6) maps each ring matcher to a role. Shipped: ring 0 hearth stones `stone`, posts `pillar`, thatch `trim`; ring 1 bricks `brick`, bronze blocks and the bell `metal`; ring 2 smooth stone `stone`, bars `scaffold`, lamps `lamp`; ring 3 sourcestone `brick`, otherstone `pillar`, source gem blocks `glass`, candles `lamp`; ring 4 coke bricks `brick`, heavy engineering `metal`, steel scaffolding `scaffold`, electric lanterns `lamp`; ring 5 aluminium `metal`, red steel `trim`, HV capacitors `pillar`, aluminium scaffolding `scaffold`, floodlights `lamp`; ring 6 certus quartz `stone`, steel casings `metal`, quartz glass `glass`, fluix `trim`; ring 7 steel plating `metal`, desh `pillar`, ostrum `trim`; ring 8 calorite `metal`, quantum casings `pillar`, awakened draconium `lamp`. 537 positions over the nine rings. `ShrineDataLoader` reads each tier's ring JSON (`modonomicon/multiblocks/<path>.json`) through the resource manager and resolves key -> role per ring (`ringRoles`); a key without a role stays Age material (WARN).
- `ShrineMultiblocks.check` (§7.2) accepts a consecrated block of the key's role at any accent. It returns the cells of the best rotation (position, real pattern key, role, ok, consecrated, accent), which the consecration uses. The ghost preview stays the build recipe in Age materials (the Modonomicon multiblocks); for a broken ring of an awakened tier the ghost is not sent (it would mark every consecrated block as wrong): the player sees `wax_off` sparks at the holes and "... is broken (n of m blocks). Sparks mark the gaps: place the original material or a consecrated block of the same kind."

### 17.3 Consecration (`shrine/ShrineConsecration`, server thread)

- **Ceremony:** `ShrineService.complete` (prayer heard) schedules every role position of the accepted ring before the grant. The batch starts 20 ticks after the Age reload is idle (so the reload freeze does not cut it in two) and runs over `shrine.consecrationTicks` (120 = 6 s): offset = horizontal distance from the heart, normalised over the ring, plus one tick per block of height (from the heart outward, bottom up). Each block: the state seen at scheduling must still be there (else skipped; the next validation decides again), the original state goes into `ShrineSavedData.originals` (`ns:id[props]`, sorted, `Consecration.encodeState`), a block entity is removed first (no inventory spills or machine teardown drops; stored energy is lost), then the consecrated state with the ring's accent; `end_rod` and `enchant` particles, at most one `firmages:shrine.consecrate` sound per tick (vanilla `block.amethyst_block.resonate` in `sounds.json`).
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
