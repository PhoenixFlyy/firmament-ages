// Firmament Ages - the boss fallback sigils (Doc 08 section 5.1; opening in server_scripts/firmages/boss_fallback.js).
// One grid recipe per sigil from the materials of its Age, never from the boss drop it stands in for. Each sigil is in
// its Age tag (dev/age_map.toml), so the firmages-core gate drops its recipe until that Age.

ServerEvents.recipes((event) => {
  const sigil = (id, pattern, key) => event.shaped(`firmages:${id}`, pattern, key).id(`firmages:crafting/${id}`)
  const CROSS = [' A ', 'BCB', ' A ']

  // Iron Age: steel, polished quartz (the Twilight portal gem) and a precision mechanism.
  sigil('frontier_sigil', CROSS, { A: '#c:sheets/steel', B: 'tfcreate:polished_quartz', C: 'create:precision_mechanism' })
  // Arcane Age: the three magic mods (Theurgy mercury, Occultism essence, an Ars source gem block). No Wilden drop:
  // the gate stands in for the Wilden themselves.
  sigil('wild_sigil', CROSS, { A: 'theurgy:mercury_shard', B: 'occultism:otherworld_essence', C: 'ars_nouveau:source_gem_block' })
  // Industrial Age: black steel sheets, blaze rods, netherite (the Monstrosity altar's initiator).
  sigil('forge_sigil', CROSS, { A: 'tfc:metal/sheet/black_steel', B: 'minecraft:blaze_rod', C: '#c:ingots/netherite' })
  // Electric Age: aluminium plates, soul sand and one wither skeleton skull (the vanilla summon takes three).
  sigil('wither_sigil', CROSS, { A: '#c:plates/aluminum', B: 'minecraft:soul_sand', C: 'minecraft:wither_skeleton_skull' })
  // Information Age: ender pearls, osmium and a basic control circuit.
  sigil('end_sigil', CROSS, { A: '#c:ender_pearls', B: '#c:ingots/osmium', C: 'mekanism:basic_control_circuit' })
  // Space Age: desh and ostrum plates around a Nether Star (the gate calls a Wither).
  sigil('space_sigil', CROSS, { A: 'ad_astra:desh_plate', B: 'ad_astra:ostrum_plate', C: 'minecraft:nether_star' })
  // Quantum Age: calorite plates, Glacio ice shards and hypercharged alloy.
  sigil('abyss_sigil', CROSS, { A: 'ad_astra:calorite_plate', B: 'ad_astra:ice_shard', C: 'evolvedmekanism:alloy_hypercharged' })
  // Singularity Age: awakened draconium, naquadah ingots (Mercury vein, Arc Furnace) and a dragon heart.
  sigil('chaos_sigil', CROSS, { A: '#c:ingots/draconium_awakened', B: 'sgjourney:naquadah_ingot', C: 'draconicevolution:dragon_heart' })
})
