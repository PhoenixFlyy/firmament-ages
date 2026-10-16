// Firmament Ages - One Iron (harmony plan 2026-10-15, fix 10; package A5, 2026-10-16).
// Wrought iron (tfc:metal/ingot/wrought_iron, tag c:ingots/iron) is the pack's iron. The vanilla iron ingot was a
// second iron: Occultism smelted iron dust into it, and about fifty recipes of other mods asked for it by item id
// (Stargate Journey, Ad Astra, Advanced AE, Macaw's, Create Factory Logistics ...). Now:
// - every recipe that asks for minecraft:iron_ingot takes #c:ingots/iron instead (one pass over all recipes);
// - iron dust smelts into cast iron in a furnace or on a Create fan (like the ore in the crucible), and into wrought
//   iron only in the IE Arc Furnace (Electric Age, recipes/age_5/electric_age.js) and the Mekanism Energized Smelter
//   (Information Age, below), so the Mekanism 3x/4x/5x iron line ends in the pack's iron;
// - minecraft:iron_ingot is in [hidden] of dev/age_map.toml (tag firmages:age_items/disabled): the gate drops every
//   recipe that still makes one, and EMI does not show it. dev/poc_analyze.py U-2 fails on any recipe left with it.
// The file name sorts after recipes/age_*, so the pass also sees the pack's own recipes.

ServerEvents.recipes((event) => {
  event.replaceInput({ input: 'minecraft:iron_ingot' }, 'minecraft:iron_ingot', '#c:ingots/iron')
  // Three recipe types wrap their ingredients in a form replaceInput does not see (an "ingredient" object with an
  // "amount", or an item with a "count"); found in the all-Ages dump of 2026-10-16. Their JSON is rewritten as is.
  ;['advanced_ae:skysteel', 'sgjourney:crystallizing/tollan_upgrade_crystal', 'mysticalagriculture:enchanter/impaling']
    .forEach((id) => {
      const found = []
      event.forEachRecipe({ id: id }, (r) => found.push(String(r.json)))
      if (found.length !== 1) return
      const json = JSON.parse(found[0].split('"item":"minecraft:iron_ingot"').join('"tag":"c:ingots/iron"'))
      event.remove({ id: id })
      event.custom(json).id(id)
    })

  ;['occultism:smelting/iron_ingot_from_dust', 'occultism:blasting/iron_ingot_from_dust'].forEach((id) =>
    event.replaceOutput({ id: id }, 'minecraft:iron_ingot', 'tfc:metal/ingot/cast_iron'))

  // Mekanism lists its own smelting recipes before the furnace recipes it wraps (MekanismRecipeType#getRecipesUncached,
  // Mekanism 10.7.19), so this one wins over the cast iron recipe above in the Energized Smelter.
  event.custom({
    type: 'mekanism:smelting',
    input: { count: 1, tag: 'c:dusts/iron' },
    output: { count: 1, id: 'tfc:metal/ingot/wrought_iron' }
  }).id('firmages:smelting/wrought_iron_from_dust')
})
