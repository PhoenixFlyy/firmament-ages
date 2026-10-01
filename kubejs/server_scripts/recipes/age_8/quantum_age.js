// Firmament Ages - Age 8: Quantum Age goal (Doc 08 sections 2.2 and 10.2; Doc 10 v3 sections 5 and 6.1).
// Canonical stations of the Age: Mekanism fusion and the SPS (antimatter), Ad Astra tier 3-4 rockets (Venus
// calorite, Glacio ice shards), DE Fusion Crafting with awakened (draconic) injectors.
// Recipe format as the /fa_dump_full export lists DE's own fusion recipes (2026-09-30).
// Goal: the Quantum Core, the last offering at the shrine (age_8 -> age_9): Mekanism antimatter pellets, DE
// Awakened Core, calorite plates, Glacio ice shards, Leviathan drop.

ServerEvents.recipes((event) => {
  const consume = (ing) => ({ ingredient: ing, consume: true })
  // The Awakened Core is the catalyst on the fusion core; seven injectors at draconic level.
  event.custom({
    type: 'draconicevolution:fusion_crafting',
    result: { id: 'firmages:quantum_core', count: 1 },
    catalyst: { item: 'draconicevolution:awakened_core' },
    totalEnergy: 64000000,
    techLevel: 'draconic',
    ingredients: [
      consume({ item: 'mekanism:pellet_antimatter' }),
      consume({ item: 'mekanism:pellet_antimatter' }),
      consume({ tag: 'c:plates/calorite' }),
      consume({ tag: 'c:plates/calorite' }),
      consume({ item: 'ad_astra:ice_shard' }),
      consume({ item: 'ad_astra:ice_shard' }),
      consume({ tag: 'firmages:boss_token/age_8' })
    ]
  }).id('firmages:fusion/quantum_core')
})
