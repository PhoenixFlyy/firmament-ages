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
  ;['ad_astra:compressing', 'ad_astra:alloying', 'ad_astra:refining'].forEach((t) => event.remove({ type: t }))
  // Ad Astra smelts and blasts its planet ores (desh, ostrum, calorite, and the planet copper, iron, gold, coal,
  // diamond, lapis, ice shard and cheese ores) and raw ores in the vanilla furnace (matrix 6.1 "Schmelzen" loser:
  // "Vanilla-Ofen für Erze"); these recipes use Ad Astra's own tags, so the global furnace-ore removal missed them.
  event.remove({ id: /^ad_astra:(smelting|blasting)\/.+_from_(smelting|blasting)_(.+_ore|raw_.+)$/ })

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
