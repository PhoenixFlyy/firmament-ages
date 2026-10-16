// Firmament Ages - cross-mod links of the Arcane Age (harmony plan section 4, package A9; dev/quest-hints-links.md).
// Link 3: Create builds the magic miner. The Foliot and Djinni binding books (the spirits of the Dimensional Mineshaft
//   and of the Arcane Keystone ritual) take a Create precision mechanism, the Iron Age's own part, in place of one dye.
//   One grid recipe per book, so the IE Assembler or a Wixie can automate them; Occult Engineering's mixer and spout
//   copies of the same books go (one station per item), as for the Afrit (recipes/age_5) and the Marid (recipes/age_8).
// Link 4 (Mineshaft pieces into the crushers or Theurgy, never ingots) is in recipes/age_3/arcane_age.js (harmony A4).
// Link 5 (Ars Source as a fluid for Create) is in this file below.

ServerEvents.recipes((event) => {
  // ======================================================================================== link 3: binding books
  ;['foliot', 'djinni'].forEach((s) => {
    ;[`occultism:crafting/book_of_binding_${s}`, `occultism:crafting/book_of_binding_${s}_from_empty`,
      `occultengineering:mixing/book_of_binding_${s}`, `occultengineering:mixing/book_of_binding_${s}_from_empty`]
      .forEach((id) => event.remove({ id: id }))
  })
  const BOOK = { foliot: '#c:dyes/blue', djinni: '#c:dyes/purple' }
  Object.keys(BOOK).forEach((s) => {
    // same ids as Occultism's: its guide book shows them
    event.shaped(`occultism:book_of_binding_${s}`, ['cdf', 'dbd', ' P '], {
      b: 'occultism:taboo_book', c: 'occultism:purified_ink', f: 'occultism:awakened_feather', d: BOOK[s],
      P: 'create:precision_mechanism'
    }).id(`occultism:crafting/book_of_binding_${s}`)
    event.shaped(`occultism:book_of_binding_${s}`, [' d ', 'dbd', ' P '], {
      b: 'occultism:book_of_binding_empty', d: BOOK[s], P: 'create:precision_mechanism'
    }).id(`occultism:crafting/book_of_binding_${s}_from_empty`)
  })
})
