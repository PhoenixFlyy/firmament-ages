// Firmament Ages - MrCrayfish's Furniture: Refurbished, the Electric Age home (research n7-build, Felix 2026-10-14;
// dev/age_map.toml: refurbished_furniture = age_5, Every Compat's rfm/ copies too).
// Its appliances (fridge, stove, microwave, television, computer, ceiling lights, doorbell, its own generator) are
// built from Electric Age parts: aluminium instead of iron, IE copper wire instead of copper ingots, an alternator
// instead of the vanilla blast furnace, a light bulb instead of the redstone lamp. Every recipe of the namespace and of
// Every Compat's rfm/ copies goes through the same map, the workbench's own "constructing" recipes included.
// Ids from the mod jar (no fa_dump with this mod yet).

ServerEvents.recipes((event) => {
  const MAP = {
    'minecraft:iron_ingot': { tag: 'c:ingots/aluminum' },
    'minecraft:iron_nugget': { tag: 'c:nuggets/aluminum' },
    'minecraft:copper_ingot': { item: 'immersiveengineering:wire_copper' },
    'minecraft:gold_ingot': { tag: 'c:ingots/gold' },
    'minecraft:blast_furnace': { item: 'createaddition:alternator' },
    'minecraft:redstone_lamp': { item: 'immersiveengineering:light_bulb' }
  }
  const re = new RegExp(`"item":"(${Object.keys(MAP).join('|')})"`, 'g')
  const todo = []
  event.forEachRecipe([{ mod: 'refurbished_furniture' }, { id: /^everycomp:rfm\// }], (r) => {
    const text = String(r.json)
    const next = text.replace(re, (m, from) => MAP[from].tag ? `"tag":"${MAP[from].tag}"` : `"item":"${MAP[from].item}"`)
    if (next !== text) todo.push([String(r.getId()), next])
  })
  todo.forEach((t) => {
    event.remove({ id: t[0] })
    event.custom(JSON.parse(t[1])).id(t[0])
  })
  console.info(`[firmages] refurbished furniture: ${todo.length} recipes take Electric Age parts`)
})
