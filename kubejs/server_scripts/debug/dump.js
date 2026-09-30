// Firmament Ages - PoC helper commands that also run from the server console / RCON (op only).
//   /fa_dump       write local/firmages/recipes.json (every loaded recipe: id, type, result, count, the
//                  ProgressiveStages stage that locks the recipe id, its result by recipe lock, its result item)
//                  and local/firmages/item_tags.json (every item tag with its items). dev/poc_analyze.py reads both.
//   /fa_dims       list loaded dimensions with their ProgressiveStages dimension lock, then the number of
//                  loaded [[ores.overrides]] rows per stage (ore disguise) and which of them target unknown blocks
//   /fa_recipe <regex>  print up to 15 recipes whose id matches: type, result and the first items of each
//                  ingredient (to check replaceInput results, e.g. /fa_recipe andesite_alloy)
// /fa_selftest (stage bookkeeping without a player) lives in stages/grants.js, next to reconcile().
// Used by dev/poc-results.md. Nothing here runs unless an operator types the command.

(() => {
  const BuiltInRegistries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries')
  const LockRegistry = Java.loadClass('com.enviouse.progressivestages.common.lock.LockRegistry')
  const OUT = 'local/firmages'

  const opt = (o) => (o && o.isPresent() ? String(o.get()) : '')
  const itemId = (item) => String(BuiltInRegistries.ITEM.getKey(item))

  // Rhino rejects const/let declared directly inside a try block, so the result lookup lives here.
  const resultOf = (recipe, access, locks) => {
    const stack = recipe.getResultItem(access)
    if (!stack || stack.isEmpty()) return ['', 0, '', '']
    const item = stack.getItem()
    return [itemId(item), stack.getCount(), opt(locks.getRequiredStageForRecipeByOutput(item)), opt(locks.getRequiredStage(item))]
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
        opt(locks.getRequiredStageForRecipe(id)), res[2], res[3]])
    })
    const tags = {}
    BuiltInRegistries.ITEM.getTags().forEach((pair) => {
      const items = []
      pair.getSecond().forEach((h) => items.push(String(h.unwrapKey().get().location())))
      tags[String(pair.getFirst().location())] = items
    })
    JsonIO.write(`${OUT}/recipes.json`, { columns: ['id', 'type', 'result', 'count', 'recipe_lock', 'output_lock', 'item_lock'], recipes: recipes })
    JsonIO.write(`${OUT}/item_tags.json`, tags)
    event.respond(Text.gold(`[fa_dump] ${recipes.length} recipes (${failed} without a readable result), ` +
      `${Object.keys(tags).length} item tags -> ${OUT}/`))
    if (firstError) event.respond(Text.gray(`  first unreadable result: ${firstError}`))
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
})()
