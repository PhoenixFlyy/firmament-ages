// Firmament Ages - cross-mod links of the Electric Age (harmony plan section 4, package A9; dev/quest-hints-links.md).
// Link 7: the new generator contains the old one. The IE Generator Block (the moving part of the Diesel Generator)
//   takes a C&A Alternator in place of its iron mechanical component; four blocks per alternator.
// Link 8: IE power digs Mekanism's metal. An excavator vein of native osmium with garnierite (nickel) beside it.
// Link 25, Electric Age component: the Attuned Circuit (recipes/age_5/electric_age.js) is made from Industrial Age
//   parts (IE circuit board, advanced electronic components, electrum wire); the Humming Core takes it, and so does the
//   Huge Diesel Engine below, where it replaces the flint and steel. The Information Age's advanced control circuit takes
//   it next (recipes/age_6/links.js).

ServerEvents.recipes((event) => {
  // ======================================================================================== link 7: generator block
  event.remove({ id: 'immersiveengineering:crafting/generator' })
  event.shaped('4x immersiveengineering:generator', ['ici', 'cAc', 'ici'], {
    i: '#c:sheetmetals/steel',
    c: 'immersiveengineering:coil_mv',
    A: 'createaddition:alternator'
  }).id('immersiveengineering:crafting/generator') // same id: the IE manual shows it

  // ======================================================================================== link 8: osmium vein
  // An IE mineral mix is world data (a vein is rolled when a chunk is made, never removed; firmages-core
  // gate.exemptRecipeTypes). Osmium pieces are Electric Age items (the vein is visible from age_5), so the excavator gives
  // them from this Age on; the firmages-core excavator filter turns any roll of a locked item into spoil. Grades and
  // weight follow the TFC + IE iron mixes: about 15 mB of metal per dig (the mixes average 13.6, dev/miner_ladder.py).
  event.custom({
    type: 'immersiveengineering:mineral_mix',
    biome_predicates: [['tfc_ie_addon:generate_mineral_mix']],
    fail_chance: 0.05,
    weight: 20,
    ores: [
      { chance: 0.3, output: { id: 'firmages:ore/small_native_osmium' } },
      { chance: 0.2, output: { id: 'firmages:ore/poor_native_osmium' } },
      { chance: 0.1, output: { id: 'firmages:ore/normal_native_osmium' } },
      { chance: 0.05, output: { id: 'firmages:ore/rich_native_osmium' } },
      { chance: 0.2, output: { id: 'tfc:ore/small_garnierite' } },
      { chance: 0.1, output: { id: 'tfc:ore/poor_garnierite' } },
      { chance: 0.05, output: { id: 'tfc:ore/normal_garnierite' } }
    ],
    spoils: [
      { chance: 0.5, output: { id: 'tfc:rock/raw/gabbro' } },
      { chance: 0.3, output: { id: 'tfc:rock/gravel/gabbro' } },
      { chance: 0.2, output: { id: 'minecraft:gravel' } }
    ]
  }).id('firmages:mineral/native_osmium')

  // ======================================================================================== link 25: Huge Diesel Engine
  // The engine's ignition is the Electric Age component. Only the Diesel Generators engines stay (electric_age.js), and
  // this is the largest of them.
  event.remove({ id: 'createdieselgenerators:crafting/huge_diesel_engine' })
  event.shaped('createdieselgenerators:huge_diesel_engine', ['AFA', 'SES', 'PBP'], {
    A: '#c:ingots/andesite_alloy',
    F: 'firmages:attuned_circuit',
    S: '#c:plates/brass',
    E: 'create:steam_engine',
    P: 'create:fluid_pipe',
    B: '#c:storage_blocks/brass'
  }).id('createdieselgenerators:crafting/huge_diesel_engine')
})
