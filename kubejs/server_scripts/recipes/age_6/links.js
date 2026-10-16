// Firmament Ages - cross-mod links of the Information Age (harmony plan section 4, package A9; dev/quest-hints-links.md).
// Link 9: the Electric Age builds the Information Age. The Mekanism Steel Casing (every Mekanism machine, the generators,
//   fission casings, fusion frames) holds an IE Heavy Engineering Block. It is the casing's only recipe: the TFC weld of
//   an osmium double ingot onto a wrought iron sheet (age_6/osmium.js) went with this link.
// Link 10: IE electronics carry Mekanism. The basic control circuit is an IE circuit board with osmium and redstone, a
//   grid recipe for the IE Assembler; the Metallurgic Infuser no longer makes it.
// Link 11: the oil chain stays after the Electric Age. The IP Hydrotreater cracks naphtha into Mekanism ethene (it made
//   petroleum gas, which no generator of the pack burns); a Rotary Condensentrator turns it into the gas that the
//   Gas-Burning Generators burn and the Pressurized Reaction Chamber makes HDPE from.
// Link 12 (IE crusher powder into the Enrichment Chamber) is in recipes/age_6/ore_ladder.js, link 13 (Mystical
//   Agriculture essence gives TFC ore pieces) in recipes/age_6/information_age.js (harmony A4, H4A).
// Link 14: Mekanism carries AE2. The ME Controller holds a Steel Casing, the ME Drive an advanced control circuit.
// Link 25, Information Age component: the advanced control circuit takes the Attuned Circuit of the Electric Age; the
//   Data Matrix, the ME Drive and every advanced Mekanism machine need it.

ServerEvents.recipes((event) => {
  // ======================================================================================== link 9: steel casing
  event.remove({ id: 'mekanism:steel_casing' })
  event.shaped('mekanism:steel_casing', ['SOS', 'GHG', 'SGS'], {
    S: '#c:ingots/steel',
    O: '#c:ingots/osmium',
    G: '#c:glass_blocks/cheap',
    H: 'immersiveengineering:heavy_engineering'
  }).id('mekanism:steel_casing')

  // ======================================================================================== link 10: basic circuit
  event.remove({ id: 'mekanism:control_circuit/basic' })
  event.shaped('2x mekanism:basic_control_circuit', [' R ', 'OCO', ' R '], {
    R: '#c:dusts/redstone',
    O: '#c:ingots/osmium',
    C: 'immersiveengineering:circuit_board'
  }).id('firmages:crafting/basic_control_circuit')

  // ======================================================================================== link 11: naphtha -> ethene
  // 1,500 mB naphtha (from 6,000 mB crude oil) give 1,000 mB ethene, 11.28 M FE in a Gas-Burning Generator
  // (kubejs/data/firmages/balance/fuels.json), against about 4 M FE for the diesel of the same oil: the Information Age
  // fuel is worth more per barrel, as the fuel ladder wants. Same id as Immersive Petroleum's recipe.
  event.remove({ id: 'immersivepetroleum:hydrotreater/naphtha_cracking' })
  event.custom({
    type: 'immersivepetroleum:hydrotreater',
    input: { tag: 'c:naphtha', amount: 1500 },
    secondary_input: { tag: 'minecraft:water', amount: 500 },
    result: { id: 'mekanism:ethene', amount: 1000 },
    energy: 192000,
    time: 300
  }).id('immersivepetroleum:hydrotreater/naphtha_cracking')

  // ======================================================================================== link 14: AE2 on Mekanism
  // Same ids as AE2's: its guide shows them.
  event.remove({ id: 'ae2:network/blocks/controller' })
  event.shaped('ae2:controller', ['aba', 'bcb', 'aSa'], {
    a: 'ae2:smooth_sky_stone_block',
    b: 'ae2:fluix_crystal',
    c: 'ae2:engineering_processor',
    S: 'mekanism:steel_casing'
  }).id('ae2:network/blocks/controller')
  event.remove({ id: 'ae2:network/blocks/storage_drive' })
  event.shaped('ae2:drive', ['aba', 'cCc', 'aba'], {
    a: '#c:ingots/iron',
    b: 'ae2:engineering_processor',
    c: 'ae2:fluix_glass_cable',
    C: 'mekanism:advanced_control_circuit'
  }).id('ae2:network/blocks/storage_drive')

  // ======================================================================================== link 25: advanced circuit
  // Four basic circuits, four infused alloys and one Attuned Circuit make four advanced circuits: half the alloy of
  // Mekanism's own recipe (two per circuit) and a quarter of an Attuned Circuit, which the IE Assembler makes. The
  // infuser route (basic circuit + redstone) goes, or the component would be skipped.
  event.remove({ id: 'mekanism:control_circuit/advanced' })
  event.remove({ id: 'mekanism:control_circuit/infused_advanced' })
  event.shaped('4x mekanism:advanced_control_circuit', ['ACA', 'CTC', 'ACA'], {
    A: '#mekanism:alloys/infused',
    C: '#c:circuits/basic',
    T: 'firmages:attuned_circuit'
  }).id('mekanism:control_circuit/advanced')
})
