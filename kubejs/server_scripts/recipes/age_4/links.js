// Firmament Ages - cross-mod links of the Industrial Age (harmony plan section 4, package A9; dev/quest-hints-links.md).
// Link 6: AFC rubber gets its industrial customers. The insulated copper coil takes a rubber bar in the middle of the
//   hemp, and the conveyor belt a rubber bar in place of one leather (the Create belt is in recipes/age_2/links.js).
// Link 25, Industrial Age component: the Arcane Gearbox (recipes/age_4/industrial_age.js) is made from Iron Age parts
//   (gearbox, precision mechanisms, steel), the Pressure Core and the reinforced blast bricks of the Improved Blast
//   Furnace take it. Nothing to change here.

ServerEvents.recipes((event) => {
  // ======================================================================================== link 6: rubber in IE
  // Same ids as Immersive Engineering's: its manual shows them.
  event.remove({ id: 'immersiveengineering:crafting/wirecoil_copper_ins' })
  event.remove({ id: 'immersiveengineering:crafting/conveyor_basic' })
  event.shaped('4x immersiveengineering:wirecoil_copper_ins', ['csc', 'srs', 'csc'], {
    c: '#c:fabric_hemp',
    s: 'immersiveengineering:wirecoil_copper',
    r: 'afc:rubber_bar'
  }).id('immersiveengineering:crafting/wirecoil_copper_ins')
  // The iron slot keeps both tags of IE's recipe (A5 left wrought iron beside #c:ingots/iron), so it is JSON.
  event.custom({
    type: 'minecraft:crafting_shaped',
    category: 'misc',
    pattern: ['lrl', 'iRi'],
    key: {
      l: { tag: 'c:leathers' },
      r: { item: 'afc:rubber_bar' },
      i: [{ tag: 'c:ingots/iron' }, { tag: 'c:ingots/wrought_iron' }],
      R: { tag: 'c:dusts/redstone' }
    },
    result: { id: 'immersiveengineering:conveyor_basic', count: 8 }
  }).id('immersiveengineering:crafting/conveyor_basic')
})
