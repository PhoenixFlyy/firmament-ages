// Firmament Ages - Age 8: Quantum Age (Doc 08 sections 2.2, 7.2 and 10.2; Doc 10 v3 sections 5 and 6.1).
// Canonical stations of the Age: Mekanism fusion and the SPS (antimatter), the 5x ore chain (age_6/ore_ladder.js),
// Evolved Mekanism tiers (its creative tier is disabled), Ad Astra tier 3-4 rockets (Venus calorite, Mercury,
// Glacio ice shards), DE Fusion Crafting with awakened (draconic) injectors.
// Magic tail: the Marid binding book needs Mekanism atomic alloy and a DE Wyvern Core; the Marid ritual awakens an
// Arcane Keystone into the Awakened Keystone (the Marid strand's keystone, an ingredient of the Ultimate Singularity).
// Goal: the Quantum Core, the last offering at the shrine (age_8 -> age_9): Mekanism antimatter pellets, DE
// Awakened Core, calorite plates, Glacio ice shards, Leviathan drop.

ServerEvents.recipes((event) => {
  // ======================================================================================== magic tail: Marid
  // Doc 08 section 7.2. One grid recipe per book (the IE Assembler or an AE2 Molecular Assembler automates them);
  // Occult Engineering's mixer copies of the same books go (one station per item).
  ;['occultism:crafting/book_of_binding_marid', 'occultism:crafting/book_of_binding_marid_from_empty',
    'occultengineering:mixing/book_of_binding_marid', 'occultengineering:mixing/book_of_binding_marid_from_empty']
    .forEach((id) => event.remove({ id: id }))
  event.shaped('occultism:book_of_binding_marid', ['cof', 'pbp', 'AWA'], {
    b: 'occultism:taboo_book', c: 'occultism:purified_ink', f: 'occultism:awakened_feather', o: '#c:dyes/green',
    p: '#c:dyes/green', A: 'mekanism:alloy_atomic', W: 'draconicevolution:wyvern_core'
  }).id('occultism:crafting/book_of_binding_marid') // same id: the Occultism guide book shows it
  event.shaped('occultism:book_of_binding_marid', [' o ', 'pbp', 'AWA'], {
    b: 'occultism:book_of_binding_empty', o: '#c:dyes/green', p: '#c:dyes/green', A: 'mekanism:alloy_atomic',
    W: 'draconicevolution:wyvern_core'
  }).id('occultism:crafting/book_of_binding_marid_from_empty')

  // The Awakened Keystone: the Marid pentacle with the bound Marid book, like the Djinni ritual of the Arcane Keystone
  // (recipes/age_3/arcane_age.js). It takes an Arcane Keystone: the one lent from the shrine (SPEC section 7.4, M6) or
  // a second one from the Djinni ritual, which stays repeatable (decision log).
  event.custom({
    type: 'occultism:ritual',
    ritual_type: 'occultism:craft',
    pentacle_id: 'occultism:craft_marid',
    duration: 400,
    ritual_dummy: { id: 'occultism:ritual_dummy/custom_ritual_craft', count: 1 },
    activation_item: { item: 'occultism:book_of_binding_bound_marid' },
    ingredients: [
      { item: 'firmages:arcane_keystone' },
      { item: 'mekanism:alloy_atomic' },
      { item: 'mekanism:alloy_atomic' },
      { item: 'draconicevolution:wyvern_core' },
      { tag: 'firmages:boss_token/age_8' }
    ],
    result: { id: 'firmages:awakened_keystone', count: 1 }
  }).id('firmages:ritual/awakened_keystone')

  // AdvancedAE's reaction chamber asked for the disabled AE2 Vibration Chamber; the Gas-Burning Generator takes its place.
  event.replaceInput({ id: 'advanced_ae:reactionchamber' }, 'ae2:vibration_chamber', 'mekanismgenerators:gas_burning_generator')

  // ======================================================================================== goal: Quantum Core
  // The Awakened Core is the catalyst on the fusion core; seven injectors at draconic level.
  const consume = (ing) => ({ ingredient: ing, consume: true })
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
