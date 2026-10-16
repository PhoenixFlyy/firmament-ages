// Firmament Ages - cross-mod links of the Space Age (harmony plan section 4, package A9; dev/quest-hints-links.md).
// Link 15: Create stays needed. The Digital Miner is built on the Mechanical Crafter (Mekanism's grid recipe goes); the
//   fusion frames follow in recipes/age_8/links.js.
// Link 16: Mekanism flies the rocket. Mekanism oxygen fills the Oxygen Loader already (Mekanism tags it c:oxygen, which
//   ad_astra:oxygen holds). The tier 1 and 2 rockets burn diesel or kerosene from the oil chain, the tier 3 and 4 rockets
//   liquid hydrogen (Mekanism's Rotary Condensentrator) or cryo fuel (the Cryo Freezer, the efficient fuel).
// Link 17 is dropped (0.13.0 review): a desh plate in the elite tier installer only delayed in-place upgrades, since the
//   elite and ultimate machines keep their own grid recipes from the Information Age. Mekanism's installers stay.
// Link 18: the new generator contains the old one. The fission reactor port takes an advanced electrolysis core, the
//   heart of the Large Gas-Burning Generator; the turbine casing takes IE steel sheet metal.
// Link 19: AE2 carries Draconic Evolution. The wyvern crafting injector takes an AE2 engineering processor in place of
//   one diamond.
// Link 25, Space Age component: the wyvern core takes the advanced control circuit of the Information Age. The Star
//   Chart and every wyvern part of Draconic Evolution need it.

ServerEvents.recipes((event) => {
  // ======================================================================================== link 15: Digital Miner
  event.remove({ id: 'mekanism:digital_miner' })
  event.custom({
    type: 'create:mechanical_crafting',
    accept_mirrored: true,
    category: 'misc',
    key: {
      A: { tag: 'mekanism:alloys/atomic' },
      C: { tag: 'c:circuits/basic' },
      S: { item: 'mekanism:logistical_sorter' },
      R: { item: 'mekanism:robit' },
      T: { item: 'mekanism:teleportation_core' },
      X: { item: 'mekanism:steel_casing' }
    },
    pattern: ['ACA', 'SRS', 'TXT'],
    result: { id: 'mekanism:digital_miner', count: 1 }
  }).id('firmages:mechanical_crafting/digital_miner')

  // ======================================================================================== link 18: fission and turbine
  event.remove({ id: 'mekanismgenerators:fission_reactor/port' })
  event.shaped('2x mekanismgenerators:fission_reactor_port', [' F ', 'FCF', ' E '], {
    F: 'mekanismgenerators:fission_reactor_casing',
    C: '#c:circuits/elite',
    E: 'mekmm:advanced_electrolysis_core'
  }).id('mekanismgenerators:fission_reactor/port')
  event.remove({ id: 'mekanismgenerators:turbine/casing' })
  event.shaped('4x mekanismgenerators:turbine_casing', [' S ', 'SOS', ' M '], {
    S: '#c:ingots/steel',
    O: '#c:ingots/osmium',
    M: 'immersiveengineering:sheetmetal_steel'
  }).id('mekanismgenerators:turbine/casing')

  // ======================================================================================== link 19 and 25: Draconic parts
  const consume = (ing) => ({ ingredient: ing, consume: true })
  event.remove({ id: 'draconicevolution:machines/wyvern_crafting_injector' })
  event.custom({
    type: 'draconicevolution:fusion_crafting',
    result: { id: 'draconicevolution:wyvern_crafting_injector', count: 1 },
    catalyst: { item: 'draconicevolution:basic_crafting_injector' },
    totalEnergy: 32000,
    techLevel: 'draconium',
    ingredients: [
      consume({ item: 'draconicevolution:wyvern_core' }),
      consume({ tag: 'c:gems/diamond' }),
      consume({ item: 'draconicevolution:draconium_core' }),
      consume({ item: 'ae2:engineering_processor' }),
      consume({ item: 'draconicevolution:draconium_core' }),
      consume({ tag: 'c:gems/diamond' }),
      consume({ tag: 'c:storage_blocks/draconium' }),
      consume({ tag: 'c:gems/diamond' })
    ]
  }).id('draconicevolution:machines/wyvern_crafting_injector')
  event.remove({ id: 'draconicevolution:components/wyvern_core' })
  event.shaped('draconicevolution:wyvern_core', ['ABA', 'BCB', 'AMA'], {
    A: '#c:ingots/draconium',
    B: 'draconicevolution:draconium_core',
    C: '#c:nether_stars',
    M: 'mekanism:advanced_control_circuit'
  }).id('draconicevolution:components/wyvern_core')
})

// ======================================================================================== link 16: rocket fuel by tier
// Ad Astra reads one fluid tag per rocket tier (ad_astra:tier_N_rocket_fuel; each held #ad_astra:fuel). The rover keeps
// #ad_astra:fuel (tags/late_ages.js adds kerosene to it).
ServerEvents.tags('fluid', (event) => {
  ;['ad_astra:tier_1_rocket_fuel', 'ad_astra:tier_2_rocket_fuel'].forEach((t) => {
    event.removeAll(t)
    event.add(t, ['#c:diesel', 'immersivepetroleum:kerosene'])
  })
  ;['ad_astra:tier_3_rocket_fuel', 'ad_astra:tier_4_rocket_fuel'].forEach((t) => {
    event.removeAll(t)
    event.add(t, ['#c:hydrogen', 'ad_astra:cryo_fuel'])
  })
})
