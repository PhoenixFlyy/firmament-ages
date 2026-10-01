// Firmament Ages - Age 9: Singularity Age, the finale (Doc 08 sections 2.2, 5.1, 6 and 10; Doc 10 v3 section 5;
// mod/firmages-core/SPEC.md sections 6, 7.5 and 14).
// Strands: Chaos (Chaos Guardian, chaos shards, chaotic injectors, the Draconic Reactor with the firmages-core
// Reactor Controller), Singularity (the Ultimate Singularity by DE Fusion Crafting from the nine relics), Stargate
// (naquadah from Mercury, the Classic Stargate, The Origin).
// Stations: DE Fusion Crafting at chaotic level, the IE Arc Furnace and the Mekanism Metallurgic Infuser and
// Antiprotonic Nucleosynthesizer for naquadah (Stargate Journey's furnace processing is removed), Stargate Journey's
// liquidizers and crystallizers for its own crystals (no other station makes them).

ServerEvents.recipes((event) => {
  const consume = (ing) => ({ ingredient: ing, consume: true })

  // ======================================================================================== the Ultimate Singularity
  // Doc 08 10.1 step 3: DE Fusion Crafting, techLevel chaotic, Chaotic Core catalyst, the nine signature items
  // (with the Awakened Keystone in the Arcane slot), two chaos shards and an Evolved Mekanism alloy, about 2 billion
  // FE. The ingredients are the item ids themselves: the shrine's relics (released from their plinths from age_9 on,
  // or moved into the injectors by the Gathering, SPEC 7.5 / M8) or copies crafted again. Twelve injectors.
  event.custom({
    type: 'draconicevolution:fusion_crafting',
    result: { id: 'firmages:ultimate_singularity', count: 1 },
    catalyst: { item: 'draconicevolution:chaotic_core' },
    totalEnergy: 2000000000,
    techLevel: 'chaotic',
    ingredients: [
      consume({ item: 'firmages:hearthstone' }),
      consume({ item: 'firmages:sky_disc' }),
      consume({ item: 'firmages:steel_heart' }),
      consume({ item: 'firmages:awakened_keystone' }),
      consume({ item: 'firmages:pressure_core' }),
      consume({ item: 'firmages:humming_core' }),
      consume({ item: 'firmages:data_matrix' }),
      consume({ item: 'firmages:star_chart' }),
      consume({ item: 'firmages:quantum_core' }),
      consume({ tag: 'firmages:boss_token/age_9' }),
      consume({ tag: 'firmages:boss_token/age_9' }),
      consume({ item: 'evolvedmekanism:alloy_singular' })
    ]
  }).id('firmages:fusion/ultimate_singularity')

  // ======================================================================================== naquadah (Doc 08 10.1 step 4)
  // Raw naquadah comes from the pack vein on Mercury (kubejs/data/firmages/worldgen, biome modifier). Stargate
  // Journey's own processing (furnace smelting and blasting, the raw-iron and raw-copper mixtures that a TFC world
  // cannot fill) is replaced by the stations of the Age.
  ;['sgjourney:naquadah_ingot_from_smelting', 'sgjourney:naquadah_ingot_from_blasting', 'sgjourney:refined_naquadah_from_blasting',
    'sgjourney:naquadah_iron_alloy_from_smelting', 'sgjourney:naquadah_iron_alloy_from_blasting',
    'sgjourney:naquadah_copper_alloy_from_smelting', 'sgjourney:naquadah_copper_alloy_from_blasting',
    'sgjourney:naquadah_iron_mixture', 'sgjourney:naquadah_copper_mixture', 'sgjourney:naquadah_from_blasting']
    .forEach((id) => event.remove({ id: id }))
  event.custom({
    type: 'immersiveengineering:arc_furnace',
    input: { item: 'sgjourney:raw_naquadah' },
    additives: [],
    results: [{ id: 'sgjourney:naquadah_ingot', count: 1 }],
    slag: { tag: 'c:slag' },
    time: 200,
    energy: 102400
  }).id('firmages:arc_furnace/naquadah_ingot')
  event.custom({
    type: 'immersiveengineering:arc_furnace',
    input: { item: 'sgjourney:naquadah_ingot' },
    additives: [{ tag: 'c:ingots/steel' }],
    results: [{ id: 'sgjourney:naquadah_iron_alloy', count: 2 }],
    time: 200,
    energy: 102400
  }).id('firmages:arc_furnace/naquadah_iron_alloy')
  event.custom({
    type: 'mekanism:metallurgic_infusing',
    item_input: { count: 1, item: 'sgjourney:naquadah_ingot' },
    chemical_input: { amount: 40, tag: 'mekanism:refined_obsidian' },
    output: { count: 1, id: 'sgjourney:refined_naquadah' },
    per_tick_usage: false
  }).id('firmages:metallurgic_infusing/refined_naquadah')
  event.custom({
    type: 'mekanism:nucleosynthesizing',
    item_input: { count: 1, item: 'sgjourney:refined_naquadah' },
    chemical_input: { amount: 4, chemical: 'mekanism:antimatter' },
    output: { count: 1, id: 'sgjourney:pure_naquadah' },
    duration: 400,
    per_tick_usage: false
  }).id('firmages:nucleosynthesizing/pure_naquadah')

  // Stargate Journey's crystal recipes name vanilla gem items, which Almost Unified does not reach in its own recipe
  // types: the TFC gems take their place (the crafting-crystal tag gets #c:gems/diamond in tags/late_ages.js).
  const GEMS = [[/"item":"minecraft:diamond"/g, '"tag":"c:gems/diamond"'], [/"item":"minecraft:diamond_block"/g, '"tag":"c:storage_blocks/diamond"'],
    [/"item":"minecraft:lapis_lazuli"/g, '"tag":"c:gems/lapis"'], [/"item":"minecraft:emerald"/g, '"tag":"c:gems/emerald"']]
  const crystals = []
  event.forEachRecipe([{ type: 'sgjourney:crystallizing' }, { type: 'sgjourney:advanced_crystallizing' }], (r) => {
    const text = String(r.json)
    let next = text
    GEMS.forEach((g) => { next = next.replace(g[0], g[1]) })
    if (next !== text) crystals.push([String(r.getId()), next])
  })
  crystals.forEach((c) => {
    event.remove({ id: c[0] })
    event.custom(JSON.parse(c[1])).id(c[0])
  })

  // Five Stargate Journey grid recipes take a filled vanilla bucket of liquid or heavy liquid naquadah; a TFC world
  // has no vanilla bucket (and TFC buckets do not hold these fluids). They take the solid form instead: pure
  // naquadah (from the nucleosynthesizer above) and its block. The machines still run on the fluids through pipes.
  ;['sgjourney:advanced_crystal_base', 'sgjourney:advanced_crystallizer', 'sgjourney:crystallizer', 'sgjourney:heavy_naquadah_liquidizer',
    'sgjourney:naquadah_fuel_rod'].forEach((id) => {
    event.replaceInput({ id: id }, 'sgjourney:liquid_naquadah_bucket', 'sgjourney:pure_naquadah')
    event.replaceInput({ id: id }, 'sgjourney:heavy_liquid_naquadah_bucket', 'sgjourney:pure_naquadah_block')
  })

  // ======================================================================================== the Classic Stargate
  // Doc 08 10.1 step 4.3: the Ultimate Singularity is an ingredient of the base block (one Stargate per team).
  event.remove({ id: 'sgjourney:classic_stargate_base_block' })
  event.shaped('sgjourney:classic_stargate_base_block', ['AGA', 'NUN', 'TDT'], {
    A: '#sgjourney:naquadah_iron_alloy', G: 'sgjourney:communication_crystal', N: '#sgjourney:refined_naquadah',
    U: 'firmages:ultimate_singularity', T: 'sgjourney:transfer_crystal', D: 'sgjourney:control_crystal'
  }).id('firmages:crafting/classic_stargate_base_block')

  // The address of The Origin (firmages-core registers the Space Location, SPEC section 14): a paper with the
  // coordinates, written with a chaos shard of the Guardian.
  event.shapeless('firmages:origin_coordinates', ['minecraft:paper', 'minecraft:ink_sac', '#firmages:boss_token/age_9'])
    .id('firmages:crafting/origin_coordinates')

  // ======================================================================================== the Draconic Reactor Controller
  // firmages-core M5 (SPEC 6.2) registers firmages:reactor_controller: it refuels the reactor from pipes and emits
  // redstone 15 when the reactor is READY; starting it stays manual. The recipe loads only once the block exists, so
  // this script also runs against a firmages-core build without M5.
  if (Item.exists('firmages:reactor_controller')) {
    event.shaped('firmages:reactor_controller', ['DCD', 'RWR', 'DCD'], {
      D: '#c:ingots/draconium_awakened', C: 'mekanism:ultimate_control_circuit', R: 'minecraft:comparator',
      W: 'draconicevolution:wyvern_core'
    }).id('firmages:crafting/reactor_controller')
  }
})
