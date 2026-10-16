// Firmament Ages - cross-mod links of the Iron Age (harmony plan section 4, package A9; dev/quest-hints-links.md).
// Rule of every link file: one part of a main-road mod per step, the earlier Age feeds the later one, base ingots stay.
// Link 2 (the Smithing Die): the Create press takes over the anvil's most repeated forging. Sheets and double sheets
//   are already on the Create line (TFCreate pressing of double ingots, its heated compacting of two sheets with flux);
//   rods were the anvil's last mass part (precision mechanisms, train tracks, the alternator). A hot ingot under the
//   press (WoodenCog heated pressing, like its sheets) gives the anvil's two rods. Tool heads stay on the anvil: a tool
//   is made once, not on a line (dev/decisions-while-away.md, 2026-10-16).
// Link 6, first customer (Iron Age): AFC rubber becomes Create belts.

ServerEvents.recipes((event) => {
  // ======================================================================================== link 2: rods on the press
  // Working temperature = TFC's forging temperature (60 % of the melting point), as WoodenCog's own sheet recipes read
  // it; WoodenCog lists 162 C for bismuth bronze (its bismuth value), the TFC value is 591 C.
  const WORK_C = {
    bismuth: 162, bismuth_bronze: 591, black_bronze: 642, black_steel: 891, blue_steel: 924, brass: 558, bronze: 570,
    cast_iron: 921, copper: 648, gold: 636, nickel: 872, red_steel: 924, rose_gold: 576, silver: 577, steel: 924,
    sterling_silver: 570, tin: 138, wrought_iron: 921, zinc: 252
  }
  Object.keys(WORK_C).forEach((m) => {
    event.custom({
      type: 'woodencog:heated_pressing',
      ingredients: [{ type: 'woodencog:heated', ingredient: { tag: `c:ingots/${m}` }, min_temp: WORK_C[m], max_temp: 3000 }],
      results: [{ type: 'heated', internal: { id: `tfc:metal/rod/${m}`, count: 2 }, copy_heat: true }],
      heat_requirement: 0
    }).id(`firmages:heated_pressing/rod_${m}`)
  })
  // Ad Astra's compat recipe pressed ONE steel ingot (any member of ad_astra:steel_ingots, the TFC ingot included) into
  // a TFC steel sheet, which is two ingots of metal, and it took the same press slot as the hot-ingot die above.
  event.remove({ id: 'create:pressing/steel_ingot' })

  // ======================================================================================== link 6: rubber belts
  // AFC rubber (latex from hevea or rubber fig, vulcanized with sulfur) is the belt material that scales: three bars
  // make four belts, where dried kelp makes one from six. The recipe waits for the Iron Age, when rubber can be made.
  event.shaped('4x create:belt_connector', ['RRR'], { R: 'afc:rubber_bar' }).id('firmages:crafting/belt_connector_rubber')
  FirmAges.requireAge('firmages:crafting/belt_connector_rubber', 'age_2')
})
