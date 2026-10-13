// Firmament Ages - osmium in TFC metalworking (the pack's osmium vein, startup_scripts/osmium.js; decision 2026-10-13).
// What MekaTFC 0.1.0 did for osmium, now with pack items: the ore pieces melt in a TFC crucible or vessel into molten
// osmium, which casts into Mekanism's osmium ingot (Doc 10 v3 section 8.1: osmium stays a Mekanism material). This is
// the way to the first osmium ingots of the Information Age, before the Purification Chamber (ore_ladder.js) runs.
// Two ingots weld into an osmium double ingot, which welds with a wrought iron sheet into a Mekanism Steel Casing.
// The fluid is Evolved Mekanism's molten osmium (nothing else uses it); its TFC melting point and heat are in
// kubejs/data/firmages/tfc/fluid_heat/osmium.json, item heat in kubejs/data/firmages/tfc/item_heat/.
// Piece amounts as every TFC ore: small 10, poor 15, normal 25, rich 35 mB; ingot 100 mB.

ServerEvents.recipes((event) => {
  const FLUID = 'evolvedmekanism:molten_osmium'
  const MELT = 1230
  const PIECE_MB = { small: 10, poor: 15, normal: 25, rich: 35 }
  Object.keys(PIECE_MB).forEach((g) => {
    event.custom({
      type: 'tfc:heating',
      ingredient: { item: `firmages:ore/${g}_native_osmium` },
      result_fluid: { id: FLUID, amount: PIECE_MB[g] },
      temperature: MELT
    }).id(`firmages:heating/ore/${g}_native_osmium`)
  })
  ;[['nugget', 'c:nuggets/osmium', 10], ['ingot', 'c:ingots/osmium', 100], ['double_ingot', 'c:double_ingots/osmium', 200]]
    .forEach((m) => {
      event.custom({
        type: 'tfc:heating',
        ingredient: { tag: m[1] },
        result_fluid: { id: FLUID, amount: m[2] },
        temperature: MELT
      }).id(`firmages:heating/metal/${m[0]}/osmium`)
    })
  ;[['ingot_mold', 0.1], ['fire_ingot_mold', 0.01]].forEach((m) => {
    event.custom({
      type: 'tfc:casting',
      mold: { item: `tfc:ceramic/${m[0]}` },
      fluid: { fluid: FLUID, amount: 100 },
      result: { id: 'mekanism:ingot_osmium', count: 1 },
      break_chance: m[1]
    }).id(`firmages:casting/osmium_ingot_${m[0]}`)
  })
  event.custom({
    type: 'tfc:welding',
    first_input: { tag: 'c:ingots/osmium' },
    second_input: { tag: 'c:ingots/osmium' },
    result: { id: 'firmages:metal/double_ingot/osmium', count: 1 },
    tier: 3
  }).id('firmages:welding/osmium_double_ingot')
  event.custom({
    type: 'tfc:welding',
    first_input: { tag: 'c:sheets/wrought_iron' },
    second_input: { tag: 'c:double_ingots/osmium' },
    result: { id: 'mekanism:steel_casing', count: 1 },
    tier: 3
  }).id('firmages:welding/steel_casing')
})
