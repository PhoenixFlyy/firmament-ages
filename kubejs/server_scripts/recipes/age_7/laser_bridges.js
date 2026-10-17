// Firmament Ages - Laser Bridges & Doors in the Space Age (research n7-build, dev/age_map.toml: laserbridges = age_7).
// The two laser sources are built from desh plate around an etrionic capacitor (iron ingots and an End crystal before).
// Laser Bridges 6.2 has no recipe files of its own: its library Deimos adds every recipe as
// deimos:deimosgeneratedcrafting<n>, numbered by load order, so the two raw-material recipes are found by result and
// End crystal and replaced by pack recipes with fixed ids (the type comes first in the filter: an input test on every
// recipe would parse the TFC Regrowing Forests recipes for Dynamic Trees TFC that global_removals.js drops, and log
// 40 errors). The conversions between the two sources and the full-block versions keep the jar's recipes.

ServerEvents.recipes((event) => {
  event.remove({ type: 'minecraft:crafting_shaped', output: 'laserbridges:laser_source_block', input: 'minecraft:end_crystal' })
  event.remove({ type: 'minecraft:crafting_shaped', output: 'laserbridges:laser_fence_source_block', input: 'minecraft:end_crystal' })
  event.shaped('laserbridges:laser_source_block', ['DGD', 'DCD', 'DDD'], {
    D: 'ad_astra:desh_plate', G: 'minecraft:glass', C: 'ad_astra:etrionic_capacitor'
  }).id('firmages:laserbridges/laser_source_block')
  event.shaped('laserbridges:laser_fence_source_block', ['DDD', 'DCG', 'DDD'], {
    D: 'ad_astra:desh_plate', G: 'minecraft:glass', C: 'ad_astra:etrionic_capacitor'
  }).id('firmages:laserbridges/laser_fence_source_block')
})
