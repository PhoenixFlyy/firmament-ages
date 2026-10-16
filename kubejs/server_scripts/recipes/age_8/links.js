// Firmament Ages - cross-mod links of the Quantum Age (harmony plan section 4, package A9; dev/quest-hints-links.md).
// Link 15: Create stays needed. The fusion reactor frames are built on the Mechanical Crafter (Mekanism's grid recipe
//   goes); four frames per craft as before.
// Link 17: the ultimate tier installer takes a calorite plate in place of the planks.
// Link 20: the patterns and the power of the earlier Ages. The awakened core (the heart of every awakened part of
//   Draconic Evolution and the catalyst of the Quantum Core) takes an AE2 256k storage component in place of one wyvern
//   core; the fusion reactor controller takes a fission reactor port in place of its chemical tank.
// Link 21: the Supercritical Phase Shifter keeps Mekanism's cost (1,000 mB polonium and 1,000,000 J per mB of
//   polonium for 1 mB antimatter, config/Mekanism/general.toml [sps]): only a fusion plant feeds it. No recipe change.
// Link 25, Quantum Age component: the awakened core (link 20 above). The Quantum Core takes it as its catalyst, the
//   chaotic core (Singularity Age) takes four.

ServerEvents.recipes((event) => {
  // ======================================================================================== link 15: fusion frames
  event.remove({ id: 'mekanismgenerators:reactor/frame' })
  event.custom({
    type: 'create:mechanical_crafting',
    accept_mirrored: true,
    category: 'misc',
    key: {
      A: { tag: 'c:alloys/ultimate' },
      P: { tag: 'c:pellets/polonium' },
      X: { item: 'mekanism:steel_casing' }
    },
    pattern: ['APA', 'PXP', 'APA'],
    result: { id: 'mekanismgenerators:fusion_reactor_frame', count: 4 }
  }).id('firmages:mechanical_crafting/fusion_reactor_frame')

  // ======================================================================================== link 17: ultimate installer
  event.remove({ id: 'mekanism:tier_installer/ultimate' })
  event.shaped('mekanism:ultimate_tier_installer', ['ACA', 'IPI', 'ACA'], {
    A: '#mekanism:alloys/atomic',
    C: '#c:circuits/ultimate',
    I: '#c:gems/diamond',
    P: '#c:plates/calorite'
  }).id('mekanism:tier_installer/ultimate')

  // ======================================================================================== link 20: awakened core
  const consume = (ing) => ({ ingredient: ing, consume: true })
  event.remove({ id: 'draconicevolution:components/awakened_core' })
  event.custom({
    type: 'draconicevolution:fusion_crafting',
    result: { id: 'draconicevolution:awakened_core', count: 1 },
    catalyst: { tag: 'c:nether_stars' },
    totalEnergy: 1000000,
    techLevel: 'wyvern',
    ingredients: [
      consume({ item: 'draconicevolution:wyvern_core' }),
      consume({ item: 'draconicevolution:wyvern_core' }),
      consume({ tag: 'c:ingots/draconium_awakened' }),
      consume({ tag: 'c:ingots/draconium_awakened' }),
      consume({ tag: 'c:ingots/draconium_awakened' }),
      consume({ tag: 'c:ingots/draconium_awakened' }),
      consume({ item: 'draconicevolution:wyvern_core' }),
      consume({ item: 'ae2:cell_component_256k' })
    ]
  }).id('draconicevolution:components/awakened_core')

  // ======================================================================================== link 20: fusion controller
  event.remove({ id: 'mekanismgenerators:reactor/controller' })
  event.shaped('mekanismgenerators:fusion_reactor_controller', ['CGC', 'FPF', 'FFF'], {
    C: '#c:circuits/ultimate',
    G: '#c:glass_panes',
    F: 'mekanismgenerators:fusion_reactor_frame',
    P: 'mekanismgenerators:fission_reactor_port'
  }).id('mekanismgenerators:reactor/controller')
})
