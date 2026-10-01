// Firmament Ages - Age 7: Space Age (Doc 08 sections 2.2, 6 and 10.2; Doc 10 v3 sections 5, 6.1, 7.2, 8.1).
// Canonical stations of the Age: Mekanism Elite (fission, Industrial Turbine, ore 4x in age_6/ore_ladder.js, Induction
// Matrix, Digital Miner, Teleporter), Ad Astra tier 1-2 rockets (Moon desh, Mars ostrum), DE Fusion Crafting with
// basic and wyvern injectors (the Age's pedestal crafting), MEGA Cells.
// Planet metals (Doc 10 v3 matrix 6.1, last row): the Mekanism ore chain (More Mekanism Processing's desh, ostrum
// and calorite recipes) and the IE Metal Press. Ad Astra's own processing and machines are hidden: Compressor,
// Etrionic Blast Furnace, Fuel Refinery, Energizer, Coal Generator, Solar Panel, its iron and steel (stages/disabled.toml).
// Rocket fuel: Immersive Petroleum diesel (Ad Astra reads #c:diesel) and kerosene (tags/late_ages.js).
// Goal: the Star Chart (desh and ostrum plates, Mekanism polonium pellet, Wyvern Core, Harbinger drop).

ServerEvents.recipes((event) => {
  // ======================================================================================== Ad Astra machines
  // The Ad Astra compressor, alloying and refinery recipes go, and so do its furnace recipes of planet ores and raw
  // ores (desh, ostrum, calorite, and the planet copper, iron, gold, coal, diamond, lapis, ice shard and cheese ores;
  // matrix 6.1 "Schmelzen" loser: "Vanilla-Ofen für Erze"): they use Ad Astra's own tags, so the global furnace-ore
  // removal misses them. Fixed ids, 12 + 50, read from the original recipes on 2026-10-01 (a type or regex filter is
  // one scan of all recipes per reload); poc_analyze.py C-7a reports any recipe of these types or ad_astra
  // smelting/blasting id ..._from_..._ore that is left.
  event.remove(AD_ASTRA_REMOVALS.map((id) => ({ id: id })))

  // ======================================================================================== plates: the IE Metal Press
  // IE's own tag recipes press desh, ostrum and calorite plates (immersiveengineering:metalpress/plate_<m>, one ingot ->
  // one plate as for the IE-canon metals). Create's press copies are a second plate station (matrix 6.1 losers:
  // "Ad Astra Compressor", Create plates), as are its one-ingot plates of the IE metals.
  ;['desh', 'ostrum', 'calorite'].forEach((m) => event.remove({ id: `create:pressing/${m}_ingot` }))
  ;['aluminum', 'constantan', 'lead', 'uranium', 'electrum', 'nickel', 'silver', 'steel'].forEach((m) =>
    event.remove({ id: `create:pressing/compat/immersiveengineering/plate_${m}` }))

  // ======================================================================================== parts of disabled blocks
  // Recipes that still ask for a block disabled in this pack get the working counterpart: the DE Energy Transfuser
  // took an Energy Core stabilizer (the Energy Core is disabled), More Machine's large Solar Neutron Activator an
  // advanced solar generator (no passive power).
  event.replaceInput({ id: 'draconicevolution:machines/energy_transfuser' }, 'draconicevolution:energy_core_stabilizer', 'draconicevolution:draconium_core')
  event.replaceInput({ id: 'mekmm:large_solar_neutron_activator' }, 'mekanismgenerators:advanced_solar_generator', 'mekanism:solar_neutron_activator')

  // ======================================================================================== goal: Star Chart
  // Polonium pellet as the catalyst on the fusion core; six injectors at wyvern level.
  const consume = (ing) => ({ ingredient: ing, consume: true })
  event.custom({
    type: 'draconicevolution:fusion_crafting',
    result: { id: 'firmages:star_chart', count: 1 },
    catalyst: { item: 'mekanism:pellet_polonium' },
    totalEnergy: 16000000,
    techLevel: 'wyvern',
    ingredients: [
      consume({ tag: 'c:plates/desh' }),
      consume({ tag: 'c:plates/desh' }),
      consume({ tag: 'c:plates/ostrum' }),
      consume({ tag: 'c:plates/ostrum' }),
      consume({ item: 'draconicevolution:wyvern_core' }),
      consume({ tag: 'firmages:boss_token/age_7' })
    ]
  }).id('firmages:fusion/star_chart')
})

// Ad Astra machine and planet-ore furnace recipes, as fixed ids (see the removals at the top of the handler).
const AD_ASTRA_REMOVALS = [
  'ad_astra:alloying/steel_ingot_from_alloying_iron_ingot_and_barrier',
  'ad_astra:blasting/calorite_ingot_from_blasting_deepslate_calorite_ore',
  'ad_astra:blasting/calorite_ingot_from_blasting_raw_calorite',
  'ad_astra:blasting/calorite_ingot_from_blasting_venus_calorite_ore',
  'ad_astra:blasting/cheese_from_blasting_moon_cheese_ore', 'ad_astra:blasting/coal_from_blasting_glacio_coal_ore',
  'ad_astra:blasting/coal_from_blasting_venus_coal_ore',
  'ad_astra:blasting/copper_ingot_from_blasting_glacio_copper_ore',
  'ad_astra:blasting/desh_ingot_from_blasting_deepslate_desh_ore',
  'ad_astra:blasting/desh_ingot_from_blasting_moon_desh_ore', 'ad_astra:blasting/desh_ingot_from_blasting_raw_desh',
  'ad_astra:blasting/diamond_from_blasting_mars_diamond_ore',
  'ad_astra:blasting/diamond_from_blasting_venus_diamond_ore',
  'ad_astra:blasting/gold_ingot_from_blasting_venus_gold_ore',
  'ad_astra:blasting/ice_shard_from_blasting_deepslate_ice_shard_ore',
  'ad_astra:blasting/ice_shard_from_blasting_glacio_ice_shard_ore',
  'ad_astra:blasting/ice_shard_from_blasting_mars_ice_shard_ore',
  'ad_astra:blasting/ice_shard_from_blasting_moon_ice_shard_ore',
  'ad_astra:blasting/iron_ingot_from_blasting_glacio_iron_ore',
  'ad_astra:blasting/iron_ingot_from_blasting_mars_iron_ore',
  'ad_astra:blasting/iron_ingot_from_blasting_mercury_iron_ore',
  'ad_astra:blasting/iron_ingot_from_blasting_moon_iron_ore',
  'ad_astra:blasting/lapis_lazuli_from_blasting_glacio_lapis_ore',
  'ad_astra:blasting/ostrum_ingot_from_blasting_deepslate_ostrum_ore',
  'ad_astra:blasting/ostrum_ingot_from_blasting_mars_ostrum_ore',
  'ad_astra:blasting/ostrum_ingot_from_blasting_raw_ostrum',
  'ad_astra:compressing/calorite_plate_from_compressing_calorite_blocks',
  'ad_astra:compressing/calorite_plate_from_compressing_calorite_ingots',
  'ad_astra:compressing/desh_plate_from_compressing_desh_blocks',
  'ad_astra:compressing/desh_plate_from_compressing_desh_ingots',
  'ad_astra:compressing/iron_plate_from_compressing_iron_block',
  'ad_astra:compressing/iron_plate_from_compressing_iron_ingot',
  'ad_astra:compressing/ostrum_plate_from_compressing_ostrum_blocks',
  'ad_astra:compressing/ostrum_plate_from_compressing_ostrum_ingots',
  'ad_astra:compressing/steel_plate_from_compressing_steel_blocks',
  'ad_astra:compressing/steel_plate_from_compressing_steel_ingots', 'ad_astra:refining/fuel_from_refining_oil',
  'ad_astra:smelting/calorite_ingot_from_smelting_deepslate_calorite_ore',
  'ad_astra:smelting/calorite_ingot_from_smelting_raw_calorite',
  'ad_astra:smelting/calorite_ingot_from_smelting_venus_calorite_ore',
  'ad_astra:smelting/cheese_from_smelting_moon_cheese_ore', 'ad_astra:smelting/coal_from_smelting_glacio_coal_ore',
  'ad_astra:smelting/coal_from_smelting_venus_coal_ore',
  'ad_astra:smelting/copper_ingot_from_smelting_glacio_copper_ore',
  'ad_astra:smelting/desh_ingot_from_smelting_deepslate_desh_ore',
  'ad_astra:smelting/desh_ingot_from_smelting_moon_desh_ore', 'ad_astra:smelting/desh_ingot_from_smelting_raw_desh',
  'ad_astra:smelting/diamond_from_smelting_mars_diamond_ore',
  'ad_astra:smelting/diamond_from_smelting_venus_diamond_ore',
  'ad_astra:smelting/gold_ingot_from_smelting_venus_gold_ore',
  'ad_astra:smelting/ice_shard_from_smelting_deepslate_ice_shard_ore',
  'ad_astra:smelting/ice_shard_from_smelting_glacio_ice_shard_ore',
  'ad_astra:smelting/ice_shard_from_smelting_mars_ice_shard_ore',
  'ad_astra:smelting/ice_shard_from_smelting_moon_ice_shard_ore',
  'ad_astra:smelting/iron_ingot_from_smelting_glacio_iron_ore',
  'ad_astra:smelting/iron_ingot_from_smelting_mars_iron_ore',
  'ad_astra:smelting/iron_ingot_from_smelting_mercury_iron_ore',
  'ad_astra:smelting/iron_ingot_from_smelting_moon_iron_ore',
  'ad_astra:smelting/lapis_lazuli_from_smelting_glacio_lapis_ore',
  'ad_astra:smelting/ostrum_ingot_from_smelting_deepslate_ostrum_ore',
  'ad_astra:smelting/ostrum_ingot_from_smelting_mars_ostrum_ore',
  'ad_astra:smelting/ostrum_ingot_from_smelting_raw_ostrum'
]
