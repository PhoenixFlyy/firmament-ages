// Firmament Ages - Laser Bridges & Doors in the Space Age (research n7-build, dev/age_map.toml: laserbridges = age_7).
// The laser source is built from desh plate around an etrionic capacitor (vanilla iron and an End crystal before).
// The fence variant converts from it in the grid (the jar's own recipes).

ServerEvents.recipes((event) => {
  event.replaceInput({ id: 'laserbridges:laser_source_block' }, 'minecraft:iron_ingot', 'ad_astra:desh_plate')
  event.replaceInput({ id: 'laserbridges:laser_source_block' }, 'minecraft:end_crystal', 'ad_astra:etrionic_capacitor')
})
