// Firmament Ages - PoC helper commands that also run from the server console / RCON (op only).
//   /fa_dump       write local/firmages/recipes.json (every loaded recipe: id, type, result, count, the
//                  ProgressiveStages stages that lock the recipe id, its result by recipe lock, its result item;
//                  several stages are joined with "|"; plus the Ages firmages-core had unlocked), local/firmages/item_tags.json (every item tag with its
//                  items) and local/firmages/registries.json (every item, block and fluid id).
//                  dev/poc_analyze.py reads the first two, dev/gen_stage_locks.py --registry reads all three.
//   /fa_dump_full [regex]  write local/firmages/recipes_full.json: every loaded recipe (or those whose id matches) as
//                  the JSON its serializer writes, after Almost Unified and KubeJS (dev/poc_analyze.py content checks)
//   /fa_dims       list loaded dimensions with their ProgressiveStages dimension lock, then the number of
//                  loaded [[ores.overrides]] rows per stage (ore disguise) and which of them target unknown blocks
//   /fa_m3 [rolls]  firmages-core M3 at the current Ages: IE mineral mix rolls, prospecting and miner-blacklist tags (m3.json)
//   /fa_recipe <regex>  print up to 15 recipes whose id matches: type, result and the first items of each
//                  ingredient (to check replaceInput results, e.g. /fa_recipe andesite_alloy)
// /fa_selftest (stage bookkeeping without a player) lives in stages/grants.js, next to reconcile().
// Used by dev/poc-results.md. Nothing here runs unless an operator types the command.

(() => {
  const BuiltInRegistries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries')
  const LockRegistry = Java.loadClass('com.enviouse.progressivestages.common.lock.LockRegistry')
  const OUT = 'local/firmages'

  const opt = (o) => (o && o.isPresent() ? String(o.get()) : '')
  // All gating stages of a lock lookup (a Java Set<StageId>), sorted and joined, '' when unlocked.
  const all = (set) => {
    const out = []
    set.forEach((s) => out.push(String(s)))
    return out.sort().join('|')
  }
  const keysOf = (registry) => {
    const out = []
    registry.keySet().forEach((k) => out.push(String(k)))
    return out.sort()
  }
  const itemId = (item) => String(BuiltInRegistries.ITEM.getKey(item))

  // Rhino rejects const/let declared directly inside a try block, so the result lookup lives here.
  const resultOf = (recipe, access, locks) => {
    // Explicit overload: Ars caster tomes also have getResultItem(ResourceLocation), and Rhino calls the plain
    // form ambiguous.
    const stack = recipe['getResultItem(net.minecraft.core.HolderLookup$Provider)'](access)
    if (!stack || stack.isEmpty()) return ['', 0, '', '']
    const item = stack.getItem()
    return [itemId(item), stack.getCount(), all(locks.getRequiredStagesForRecipeByOutput(item)), all(locks.getRequiredStages(item))]
  }

  ServerEvents.basicCommand('fa_dump', (event) => {
    const server = event.server
    const locks = LockRegistry.getInstance()
    const access = server.registryAccess()
    const recipes = []
    let failed = 0
    let firstError = ''
    server.getRecipeManager().getRecipes().forEach((holder) => {
      const id = holder.id()
      const recipe = holder.value()
      let res = ['', 0, '', '']
      try {
        res = resultOf(recipe, access, locks)
      } catch (e) {
        if (!failed) firstError = `${id}: ${e}`
        failed++
      }
      recipes.push([String(id), String(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())), res[0], res[1],
        all(locks.getRequiredStagesForRecipe(id)), res[2], res[3]])
    })
    const tags = {}
    BuiltInRegistries.ITEM.getTags().forEach((pair) => {
      const items = []
      pair.getSecond().forEach((h) => items.push(String(h.unwrapKey().get().location())))
      tags[String(pair.getFirst().location())] = items
    })
    // The Ages firmages-core had unlocked when its recipe gate filtered this recipe set (poc_analyze.py G- checks).
    const unlocked = []
    FirmAges.unlockedAges().forEach((s) => unlocked.push(String(s)))
    JsonIO.write(`${OUT}/recipes.json`, { columns: ['id', 'type', 'result', 'count', 'recipe_lock', 'output_lock', 'item_lock'],
      unlocked: unlocked, recipes: recipes })
    JsonIO.write(`${OUT}/item_tags.json`, tags)
    JsonIO.write(`${OUT}/registries.json`, { item: keysOf(BuiltInRegistries.ITEM), block: keysOf(BuiltInRegistries.BLOCK),
      fluid: keysOf(BuiltInRegistries.FLUID) })
    event.respond(Text.gold(`[fa_dump] ${recipes.length} recipes (${failed} without a readable result), ` +
      `${Object.keys(tags).length} item tags -> ${OUT}/`))
    if (firstError) event.respond(Text.gray(`  first unreadable result: ${firstError}`))
  })

  // /fa_dump_full [regex]: every loaded recipe (or those whose id matches) as the JSON its serializer writes,
  // after Almost Unified and KubeJS changed it -> local/firmages/recipes_full.json ({id: json text}).
  // dev/poc_analyze.py reads it for ingredient and output checks. Unencodable recipes are counted.
  const encodeRecipe = (recipe, ops) => {
    const res = RecipeClass.CODEC.encodeStart(ops, recipe).result()
    return res.isPresent() ? String(res.get().toString()) : null
  }
  const RecipeClass = Java.loadClass('net.minecraft.world.item.crafting.Recipe')
  ServerEvents.basicCommand('fa_dump_full', (event) => {
    const text = String(event.input).trim()
    const re = text ? new RegExp(text) : null
    const ops = Java.loadClass('net.minecraft.resources.RegistryOps')
      .create(Java.loadClass('com.mojang.serialization.JsonOps').INSTANCE, event.server.registryAccess())
    const out = {}
    let n = 0
    let failed = 0
    event.server.getRecipeManager().getRecipes().forEach((holder) => {
      const id = String(holder.id())
      if (re && !re.test(id)) return
      let json = null
      try {
        json = encodeRecipe(holder.value(), ops)
      } catch (e) {
        json = null
      }
      if (json === null) failed++
      else {
        out[id] = json
        n++
      }
    })
    JsonIO.write(`${OUT}/recipes_full.json`, out)
    event.respond(Text.gold(`[fa_dump_full] ${n} recipes encoded, ${failed} not encodable -> ${OUT}/recipes_full.json`))
  })

  const ingredientText = (ing) => {
    const ids = []
    // IngredientKJS: getStackArray() (vanilla getItems() is not reachable from scripts) and getTagKey().
    const tag = ing.getTagKey()
    if (tag) return `#${tag.location()}`
    const items = ing.getStackArray()
    for (let i = 0; i < items.length && i < 3; i++) ids.push(itemId(items[i].getItem()))
    return ids.length ? ids.join('|') + (items.length > 3 ? `|+${items.length - 3}` : '') : '-'
  }

  ServerEvents.basicCommand('fa_recipe', (event) => {
    const re = new RegExp(String(event.input).trim() || '^$')
    const access = event.server.registryAccess()
    let shown = 0
    let matched = 0
    event.server.getRecipeManager().getRecipes().forEach((holder) => {
      if (!re.test(String(holder.id()))) return
      matched++
      if (shown >= 15) return
      shown++
      const recipe = holder.value()
      let result = '?'
      try {
        result = resultOf(recipe, access, LockRegistry.getInstance())
      } catch (e) {
        result = '?'
      }
      if (typeof result !== 'string') result = result[0] ? `${result[1]}x ${result[0]}` : '-'
      const ings = []
      recipe.getIngredients().forEach((ing) => ings.push(ingredientText(ing)))
      event.respond(Text.gray(`${holder.id()} [${BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())}] -> ${result} <= ${ings.join(', ')}`))
    })
    event.respond(Text.gold(`[fa_recipe] ${matched} match(es), ${shown} shown`))
  })

  ServerEvents.basicCommand('fa_dims', (event) => {
    const locks = LockRegistry.getInstance()
    event.server.getAllLevels().forEach((level) => {
      const id = level.dimension
      const lock = opt(locks.getRequiredStageForDimension(id))
      event.respond(Text.gray(`  ${id} lock=${lock || '-'}`))
    })
    const perStage = {}
    let unknown = 0
    locks.getOreOverrides().forEach((o) => {
      const st = String(o.requiredStage)
      perStage[st] = (perStage[st] || 0) + 1
      if (String(BuiltInRegistries.BLOCK.getKey(BuiltInRegistries.BLOCK.get(o.target))) !== String(o.target)) unknown++
    })
    event.respond(Text.gold(`[fa_dims] ore overrides: ${JSON.stringify(perStage)}; spoof active=${locks.isOreSpoofActive()}; ` +
      `targets that are not registered blocks: ${unknown}`))
  })

  // /fa_m3 [rolls]: firmages-core M3 at the current Ages -> local/firmages/m3.json.
  // Excavator: every IE mineral mix (IE and TFC-IE Crossover veins) is rolled <rolls> times through
  // MineralMix#getRandomOre, the method the firmages mixin filters; a roll is locked when its item is in
  // age_items/<locked Age> or age_items/disabled, or when it places a block of age_blocks/<locked Age>.
  // Block tags: per Age, how many age_blocks members sit in tfc:prospectable, precisionprospecting:prospectable_mineral
  // and mekanism:miner_blacklist, plus how many locked ore blocks are missing from the blacklist.
  const blockTagMembers = () => {
    const out = {}
    BuiltInRegistries.BLOCK.getTags().forEach((pair) => {
      const ids = []
      pair.getSecond().forEach((h) => ids.push(String(h.unwrapKey().get().location())))
      out[String(pair.getFirst().location())] = ids
    })
    return out
  }
  const toSet = (arr) => {
    const out = new Set()
    arr.forEach((x) => out.add(x))
    return out
  }
  ServerEvents.basicCommand('fa_m3', (event) => {
    const rolls = parseInt(String(event.input).trim(), 10) || 2000
    const lockedAges = []
    FirmAges.lockedAges().forEach((s) => lockedAges.push(String(s)))
    const lockedItems = new Set()
    BuiltInRegistries.ITEM.getTags().forEach((pair) => {
      const tag = String(pair.getFirst().location())
      const st = tag.startsWith('firmages:age_items/') ? tag.substring(19) : null
      if (st && (st === 'disabled' || lockedAges.indexOf(st) >= 0)) {
        pair.getSecond().forEach((h) => lockedItems.add(String(h.unwrapKey().get().location())))
      }
    })
    const btags = blockTagMembers()
    const lockedBlocks = new Set()
    lockedAges.forEach((st) => (btags[`firmages:age_blocks/${st}`] || []).forEach((b) => lockedBlocks.add(b)))
    const Block = Java.loadClass('net.minecraft.world.level.block.Block')
    const Random = Java.loadClass('java.util.Random')
    const lockedStack = (stack) => {
      if (!stack || stack.isEmpty()) return false
      if (lockedItems.has(itemId(stack.getItem()))) return true
      return lockedBlocks.has(String(BuiltInRegistries.BLOCK.getKey(Block.byItem(stack.getItem()))))
    }
    const mixes = []
    let lockedRolls = 0
    let spoilRolls = 0
    event.server.getRecipeManager().getRecipes().forEach((holder) => {
      const mix = holder.value()
      if (String(BuiltInRegistries.RECIPE_TYPE.getKey(mix.getType())) !== 'immersiveengineering:mineral_mix') return
      const outs = []
      const lockedOuts = []
      mix.outputs.forEach((o) => {
        const st = o.stack().get()
        const id = itemId(st.getItem())
        outs.push(id)
        if (lockedStack(st)) lockedOuts.push(id)
      })
      const spoils = new Set()
      mix.spoils.forEach((o) => spoils.add(itemId(o.stack().get().getItem())))
      const rnd = new Random(42)
      let bad = 0
      let spoil = 0
      let got = null
      // No const inside the loop body: Rhino reports "redeclaration of var" on the second pass.
      for (let i = 0; i < rolls; i++) {
        got = mix.getRandomOre(rnd)
        if (lockedStack(got)) bad++
        else if (!got.isEmpty() && spoils.has(itemId(got.getItem())) && outs.indexOf(itemId(got.getItem())) < 0) spoil++
      }
      lockedRolls += bad
      spoilRolls += spoil
      mixes.push({ id: String(holder.id()), outputs: outs, locked_outputs: lockedOuts, locked_rolls: bad, spoil_rolls: spoil })
    })
    const ages = ['dawn', 'age_0', 'age_1', 'age_2', 'age_3', 'age_4', 'age_5', 'age_6', 'age_7', 'age_8', 'age_9']
    const tags = {}
    ;['tfc:prospectable', 'precisionprospecting:prospectable_mineral', 'mekanism:miner_blacklist'].forEach((t) => {
      const members = toSet(btags[t] || [])
      const perAge = {}
      ages.forEach((st) => {
        const n = (btags[`firmages:age_blocks/${st}`] || []).filter((b) => members.has(b)).length
        if (n) perAge[st] = n
      })
      let locked = 0
      members.forEach((b) => { if (lockedBlocks.has(b)) locked++ })
      tags[t] = { size: members.size, locked_members: locked, per_age: perAge }
    })
    const blacklist = toSet(btags['mekanism:miner_blacklist'] || [])
    const oreBlocks = []
    FirmAges.lockedOreBlocks().forEach((b) => oreBlocks.push(String(b)))
    const notBlacklisted = oreBlocks.filter((b) => !blacklist.has(b))
    const unlocked = []
    FirmAges.unlockedAges().forEach((s) => unlocked.push(String(s)))
    JsonIO.write(`${OUT}/m3.json`, { unlocked: unlocked, rolls_per_mix: rolls, locked_ore_blocks: oreBlocks.length,
      locked_ore_blocks_not_blacklisted: notBlacklisted, excavator: mixes, block_tags: tags })
    const withLocked = mixes.filter((m) => m.locked_outputs.length).length
    event.respond(Text.gold(`[fa_m3] at ${unlocked.join(',')}: ${mixes.length} mineral mixes (${withLocked} with a locked ore), ` +
      `${mixes.length * rolls} rolls, ${lockedRolls} locked, ${spoilRolls} spoils; locked ore blocks ${oreBlocks.length}, ` +
      `not in mekanism:miner_blacklist ${notBlacklisted.length}; ` + Object.keys(tags).map((t) =>
      `${t} ${tags[t].size} (locked ${tags[t].locked_members})`).join(', ') + ` -> ${OUT}/m3.json`))
  })
})()
