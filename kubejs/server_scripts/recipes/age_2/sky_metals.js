// Firmament Ages - Age 2: meteoric iron, the sky metal of the falling stars (firmages-core SPEC 25.5, sky M10).
// Starfall craters are its only source (meteorite blocks and the core drop meteoric iron chunks). Meteoric iron is
// forged, never cast: no TFC metal and no fluid. Its heat data (wrought iron's values: forging 921 C, welding
// 1228 C) is in kubejs/data/firmages/tfc/item_heat/meteoric_iron/. On the TFC anvil at tier 3 (wrought iron anvil):
// chunk to ingot, two ingots welded to a double ingot, double ingot to sheet, ingot to two rods. There are no
// meteoric tools (sky iron is never a second steel). The IE metal press (station age_4, recipes/station_ages.js)
// makes sheets and rods, the IE arc furnace (age_5) smelts chunks into ingots; in age_2 to age_4 the anvil is the
// only route. The Storm Rod recipe of the Sky Harvest (sky M11) joins this file.

ServerEvents.recipes((event) => {
  const RULES = ['hit_last', 'hit_second_last', 'hit_third_last']
  event.custom({
    type: 'tfc:anvil',
    ingredient: { item: 'firmages:meteoric_iron_chunk' },
    result: { id: 'firmages:meteoric_iron_ingot', count: 1 },
    rules: ['hit_last', 'draw_second_last', 'hit_third_last'],
    tier: 3
  }).id('firmages:anvil/meteoric_iron_ingot')
  event.custom({
    type: 'tfc:welding',
    first_input: { tag: 'c:ingots/meteoric_iron' },
    second_input: { tag: 'c:ingots/meteoric_iron' },
    result: { id: 'firmages:meteoric_iron_double_ingot', count: 1 },
    tier: 3
  }).id('firmages:welding/meteoric_iron_double_ingot')
  event.custom({
    type: 'tfc:anvil',
    ingredient: { tag: 'c:double_ingots/meteoric_iron' },
    result: { id: 'firmages:meteoric_iron_sheet', count: 1 },
    rules: RULES,
    tier: 3
  }).id('firmages:anvil/meteoric_iron_sheet')
  event.custom({
    type: 'tfc:anvil',
    ingredient: { tag: 'c:ingots/meteoric_iron' },
    result: { id: 'firmages:meteoric_iron_rod', count: 2 },
    rules: ['bend_last', 'draw_second_last', 'draw_third_last'],
    tier: 3
  }).id('firmages:anvil/meteoric_iron_rod')

  // ---- machines: the IE metal press (age_4) and arc furnace (age_5) ----
  event.custom({
    type: 'immersiveengineering:metal_press',
    input: { tag: 'c:ingots/meteoric_iron' },
    mold: 'immersiveengineering:mold_plate',
    result: { id: 'firmages:meteoric_iron_sheet', count: 1 },
    energy: 2400
  }).id('firmages:metal_press/meteoric_iron_sheet')
  event.custom({
    type: 'immersiveengineering:metal_press',
    input: { tag: 'c:ingots/meteoric_iron' },
    mold: 'immersiveengineering:mold_rod',
    result: { id: 'firmages:meteoric_iron_rod', count: 2 },
    energy: 2400
  }).id('firmages:metal_press/meteoric_iron_rod')
  event.custom({
    type: 'immersiveengineering:arc_furnace',
    input: { item: 'firmages:meteoric_iron_chunk' },
    additives: [],
    results: [{ id: 'firmages:meteoric_iron_ingot', count: 1 }],
    slag: { tag: 'c:slag' },
    time: 200,
    energy: 102400
  }).id('firmages:arc_furnace/meteoric_iron_ingot')

  // ---- storage block ----
  event.shaped('firmages:meteoric_iron_block', ['III', 'III', 'III'], { I: '#c:ingots/meteoric_iron' })
    .id('firmages:crafting/meteoric_iron_block')
  event.shapeless('9x firmages:meteoric_iron_ingot', ['firmages:meteoric_iron_block'])
    .id('firmages:crafting/meteoric_iron_from_block')
})
