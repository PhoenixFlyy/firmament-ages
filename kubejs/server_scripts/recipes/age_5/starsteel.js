// Firmament Ages - Age 5: starsteel, the sky metal of the Electric Age falls (firmages-core SPEC 25.5, sky M10).
// Starfall craters from the Electric Age on are its only source (starsteel meteorites and starsteel cores drop
// starsteel chunks). It is worked in machines only: the IE arc furnace (station age_5) smelts a chunk with coke dust
// into an ingot and slag. There is no starsteel plate and no TFC route. Until the Lens Crown alloys arrive (M15), the
// starsteel ingot is the Wayshrine offering from the Electric Age on (firmages_wayshrine/offerings.json).

ServerEvents.recipes((event) => {
  event.custom({
    type: 'immersiveengineering:arc_furnace',
    input: { item: 'firmages:starsteel_chunk' },
    additives: [{ tag: 'c:dusts/coal_coke' }],
    results: [{ id: 'firmages:starsteel_ingot', count: 1 }],
    slag: { tag: 'c:slag' },
    time: 400,
    energy: 204800
  }).id('firmages:arc_furnace/starsteel_ingot')

  event.shaped('firmages:starsteel_block', ['III', 'III', 'III'], { I: '#c:ingots/starsteel' })
    .id('firmages:crafting/starsteel_block')
  event.shapeless('9x firmages:starsteel_ingot', ['firmages:starsteel_block'])
    .id('firmages:crafting/starsteel_from_block')
})
