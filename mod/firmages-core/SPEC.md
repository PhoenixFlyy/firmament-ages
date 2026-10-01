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
| later | Own end boss, The Origin dimension, world events per Age | not in this spec |
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
  reactor/ ReactorFuelHandler, ReactorControllerBlock(+BE), ReactorProbe          (DE present only)
  shrine/  ShrineHeartBlock(+BE), OfferingPlinthBlock, ShrineSavedData, ShrineStateMachine,
           TierDefinition, TierReloadListener, rite/*, Blessings, Sanctuary, Gathering (DE compat)
  ceremony/ CeremonyService
  net/     payloads + handlers
  command/ FirmagesCommands, SelfTest
  compat/  kubejs (plugin + binding), modonomicon (adapter), jade, geckolib, draconic, ie, occultism, ars
  client/  ShrineHeartRenderer, CeremonyPlayer, SkyEffects, MusicControl, PreviewBridge
  mixin/   RecipeManagerMixin; ie.MineralMixMixin; occultism.MineshaftMixin; ars.ApparatusMixin; client.ClientLevelSkyMixin
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
| Draconic Evolution / Brandon's Core / CCL | 3.1.4.633 / 3.2.1.309 / 4.6.1.529 | optional | reactor, Gathering |
| Occultism | 1.224.4 | optional | mineshaft flush, extractor |
| Ars Nouveau | 5.13.2 | optional, compileOnly (**add**) | apparatus flush, extractor |
| Create, Mekanism, AE2, TFC | pinned in `gradle.properties` | optional | extractors, OreGuard drop override |
| Jade | 15.10.6 | optional, compileOnly (**add**) | shrine tooltip |
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

- **SavedData `firmages_shrine`:** `GlobalPos heart`, `boolean intact`, `int lastValidRing`, `long lastValidated`. Only one shrine per server.
- **ShrineHeartBlockEntity:** `ItemStack[9] relics` (slots 1..9), `boolean keystoneLent`, `ItemStack catalyst`, `EnergyStorage buffer` (capacity 100,000,000 FE, configurable), `Phase phase`, `int validRing`, `Rotation rotation`, `UUID[] praying` (transient), `int prayerProgressTicks`. Synced with `getUpdateTag`/`ClientboundBlockEntityDataPacket`.
- **Heart item:** the relics are stored as the data component `firmages:shrine_contents`, so a broken and re-placed heart restores the shrine.
- **Heart blockstate:** `awakened` (0..10), `ready` (bool), `lit` (bool). FTB Quests observation tasks and Jade read these.

### 2.6 Data-driven shrine content

- **Rings:** `data/firmages/modonomicon/multiblocks/shrine_ring_<N>.json` (type `modonomicon:sparse`, anchored at the heart, block matchers use pack tags such as `#firmages:shrine/any_cobble`). N = 0..9.
- **Tiers:** `data/firmages/firmages_shrine/tier/ring_<N>.json`, loaded by our own `SimpleJsonResourceReloadListener`:

```json
{
  "multiblock": "firmages:shrine_ring_2",
  "plinth": 3,
  "offering": { "item": "firmages:steel_heart" },
  "grants": "age_3",
  "rites": [ { "type": "blockstate", "key": "L", "property": "lit", "value": "true" } ],
  "prayer_ticks": 200,
  "response": { "beam_color": "#E06030", "particles": ["minecraft:flame"], "sting": "firmages:shrine.sting.iron",
                "voice": "firmages.shrine.voice.age_3", "sky_tint": "#803018", "lightning": true },
  "blessing": "firmages:iron_will"
}
```

Rite types:

| Type | Meaning |
|---|---|
| `blockstate` | All multiblock positions with key K have property P = V |
| `interact` | A player used a block of a tag inside the shrine since READY (the bell) |
| `energy` | The heart buffer holds at least X FE, consumed at AWAKENING |
| `sky` | Night, heart can see the sky, no rain |
| `players_praying` | At least N players pray at once. With `min_online_ratio` the rule applies only when 2 or more players are online |
| `relic_returned` | The lent Keystone is back on plinth 4 |

Blessings live in `data/firmages/firmages_shrine/blessing/<name>.json`: attribute modifiers, flags such as `sanctuary` and `skyreading`, and an XP multiplier. Their values can be overridden in the server config.

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

- A block `firmages:reactor_controller` placed touching a stabilizer or injector. Its states are MONITOR, SHUTDOWN, COOLING, SWAP, READY, and optionally CHARGE and ACTIVATE.
- MONITOR → SHUTDOWN when `convertedFuel / (reactableFuel + convertedFuel) >= reactor.controller.shutdownAtConversion` (default 0.80), via `shutdownReactor()`.
- COOLING waits for COLD. SWAP lets pipes use the capability until `reactableFuel >= reactor.controller.minFuel` and chaos is at or below `maxChaos`. READY emits redstone 15.
- With `reactor.controller.autoRestart = true` (default **false**, open question for Felix): `chargeReactor()`, then `activateReactor()` once `canActivate()`. This requires that `failSafeMode` is on and that the field input rate has covered the drain for `reactor.controller.stableSeconds` (default 10). If the field strength falls below `minFieldPercent` (default 30) at any time, the controller calls `shutdownReactor()`.
- Its comparator output is the fuel percentage.

## 7. m6+m9: the Shrine

Design source: `core-shrine.md` (tier ladder, voice lines, blessings, visuals). Implementation contract:

### 7.1 Blocks

- `firmages:shrine_heart` (with BE, blast resistance 1200). Breaking it needs sneak and a pickaxe. A second placement is refused while `ShrineSavedData.heart` is set elsewhere.
- `firmages:offering_plinth` has no BE. The heart's BER draws the relics.
- Recipe: TFC pit-kiln fired ceramic (the recipe is KubeJS/data, a hand-off).

### 7.2 Validation (`compat/modonomicon/ShrineMultiblocks`, the only class that touches Modonomicon)

- `MultiblockDataManager.get().getMultiblock(id).validate(level, heartPos)` [verified signature] returns the rotation or null.
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
- Tier 8 has the rite `relic_returned`: the Awakened Keystone (item id [PoC]) laid on plinth 4 clears the flag and plays the `shrine_fx` "returned" response.
- The Marid ritual itself is unchanged.

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
| `reactor.controller.enabled` / `.autoRestart` | true / false | |
| `reactor.controller.shutdownAtConversion` / `.minFuel` / `.maxChaos` / `.stableSeconds` / `.minFieldPercent` | 0.80 / 10368 / 0 / 10 / 30 | |
| `shrine.prayerSeconds` / `shrine.minPrayerSeconds` / `shrine.prayRadius` | 10 / 4 / 6 | |
| `shrine.sanctuary.baseRadius` / `.perTier` | 12 / 4 | |
| `shrine.blessings.<name>.<value>` | from §7.6 | |
| `shrine.energyCapacity` | 100000000 | |
| `shrine.permanentBeam` / `shrine.clearWeather` | false / true | |
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
| `shrine info|validate|resync` | State, validation result with missing blocks, catch up with stages |
| `shrine setrelic <slot> [item]` / `release <slot>` | Admin repair |
| `ceremony preview <stage> [full|short]` | Play the ceremony to yourself, no grant |
| `reactor info` | State of the looked-at reactor and controller |
| `selftest <suite|all>` | Server-side test suites (§12), report in `logs/firmages-selftest.json` |

## 11. Network (all S2C, registered in `RegisterPayloadHandlersEvent`, version "1")

| Payload | Fields | Client action |
|---|---|---|
| `firmages:age_transition` | stage, tier, variant (FULL/SHORT), `Optional<GlobalPos>` shrine, beam colour, sky tint, sting id | `CeremonyPlayer.start` |
| `firmages:reload_state` | boolean running | actionbar hint |
| `firmages:shrine_preview` | multiblock id, anchor pos, rotation, clear flag | `PreviewBridge` → `MultiblockPreviewRenderer.setMultiblock` + `anchorTo` [verified] |
| `firmages:shrine_fx` | pos, fx type (refused, returned, rekindled, dormant) | small local response |

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
| M7 | Own sounds (CC0 or self-made), particles, sky tint, Sodium check | M | M test with Sodium and Enhanced Celestials |
| M8 | The Gathering (after the DE injector PoC) | M | Ultimate Singularity forged at the shrine |
| M9 | GeckoLib idol | L | 10 tier looks animated |

M0–M4 must ship before the first playtest beyond the Stone Age. Shipping: copy the jar to `FirmamentAges/mods/` and run `packwiz refresh` (hand-off).

**State (2026-10-01):** M0 measured (server half). M1 shipped as 0.1.1. M2 and M3 implemented in 0.2.0; 0.2.1 adds the late pass (§4.7). Green at levels U and G, and the pack P runs are recorded in `dev/poc-results.md` ("M2/M3, quests and content"):
- U (`gradlew build`): `CoreSuiteTest` (18), `GateSuiteTest` (16: walker keys and exclusions, deny/allow/exempt, locked and unlocked items, latest-Age bucket, tag all-locked / mixed / empty, disabled, fluid ids, undetected, report and audit text, excavator pick), `JsonOutputWalkerFixtureTest` (15: recipe JSON copied from the Create, IE, Mekanism, Occultism, Ars, TFC and DE jars, plus a rules run on them).
- G (`gradlew runGameTestServer`, vanilla + ProgressiveStages + Modonomicon + KubeJS + IE + Mekanism, 12 tests): boot filter with `[dawn]` on the synthetic `firmages:test/*` recipes (item, tag all-locked, tag mixed, disabled, fluid byproduct, and the two late recipes of `LateRecipeInjector`) and on vanilla iron recipes; the recipes commands and audit file; IE crusher tag outputs through the TagOutput accessor and Mekanism output definitions; mineral mixes exempt and the MineralMix mixin (0 locked ores rolled); the pack's m1/m3 KubeJS scripts against test tags; OreGuard break and drop paths; the excavator filter on a stub mix (10,000 rolls); `age_0` then `age_2` unlocked through real reloads and revoked again (recipes, tags, excavator, OreGuard follow).
- P (pack server, 0.2.1): m2 at Dawn and after each grant age_0 to age_9 (`poc_analyze.py` G-1 to G-4), m3 excavator rolls, Digital Miner blacklist and prospecting tags per Age (`/fa_m3`), Cucumber ordering, Create/Occultism extractors at runtime (0 extraction errors on 35,114 recipes). Still open: the flush mixins (`MineshaftMixin`, `ApparatusMixin`) with a running Mineshaft or apparatus, which needs a client.

## 14. Hand-offs to other workflows (not in this mod)

1. `dev/gen_stage_locks.py`: emit the `age_items`/`age_blocks`/`age_fluids` tags into `kubejs/data/firmages/tags/`, expanding `mod:` entries from `/firmages dump registry`.
2. `kubejs/server_scripts`: the miner-blacklist tag script (m3) and the prospecting tag script (m1, gated by `FirmAges`) are written (`kubejs/server_scripts/firmages/`, §5.1); still open: trim `stages/on_stage_added.js`; the Shrine Heart pit-kiln recipe; the shrine block tags (`#firmages:shrine/*`).
3. FTB Quests: goal quests become StageTasks without stage rewards; optional "Raise the ring" observation quests.
4. Pack: add the jar to `mods/` and run packwiz refresh; the dependency entries in `neoforge.mods.toml` (§1.4).

## 15. Open points

- **Felix (decided 2026-09-30):** m1 build; reactor controller refuels and signals READY by redstone, NO auto-restart (ACTIVATE state stays disabled); offerings are ENSHRINED (kept as relics, Keystone lent in Age 8, Ultimate Singularity forged at the shrine); EMI shows no next-Age preview, by design.
- **PoC:** reload stall and client rebuild (M0); TAIL ordering against Cucumber; StageChangeEvent once per member; mekanism_lasers/mekmm breakers; DE injector placement; TFC nutrition health vs percentage modifiers; sky overlay with Sodium/Enhanced Celestials; all TFC/IE/Ad Astra/DE block ids and blockstates in rings and rites; machine caches actually refresh after the unlock reload (§12, only `RecipeManager` presence is covered by P tests); the Awakened Keystone item id. `SelectMusicEvent` exists in NeoForge 21.1.252 [verified, review], so the ceremony can suppress music with it instead of a mixin.
- **After M2/M3 (done 2026-10-01, `dev/poc-results.md`):** the audit is reviewed: the 37 types with no detected output are accepted with a reason in `dev/data/accepted_undetected.json` (checked by `poc_analyze.py` G-4), no extractor follow-up. Recipes that only a chance byproduct of a later Age keeps out are not allowlisted; `kubejs/server_scripts/recipes/byproducts.js` strips the byproduct until its Age (checked by G-3). The gate time is measured. `gate_cases.json` is still not written (the G-checks of `poc_analyze.py` cover the pack instead).
