// Firmament Ages - the Arcane Catalyst (harmony plan link 24, firmages-core SPEC 41): optional magic for the
// Information Age's machines. Placed against an Immersive Engineering or Mekanism machine (block tag
// firmages:catalysable, kubejs/data/firmages/tags/block/catalysable.json) it takes Source from a jar nearby and needs
// a turning shaft beside it; the machine then works half again as fast and the catalyst pays the extra operations'
// FE once more on top. No recipe and no Age asks for it.
// Recipe: an Arcane Core and a Mekanism steel casing (the Age's own part) between Halo Steel, precision mechanisms and
// a block of source gems.

ServerEvents.recipes((event) => {
  event.shaped('firmages:arcane_catalyst', ['HAH', 'PCP', 'HGH'], {
    H: 'firmages:halo_steel_ingot',
    A: 'ars_nouveau:arcane_core',
    P: 'create:precision_mechanism',
    C: 'mekanism:steel_casing',
    G: 'ars_nouveau:source_gem_block'
  }).id('firmages:crafting/arcane_catalyst')
})
