// Firmament Ages - the TFC counterparts of vanilla blocks that a TFC world cannot make, as item tags for
// recipes/age_3/arcane_tfc_inputs.js. Only TFC and Arborfirmacraft woods.

ServerEvents.tags('item', (event) => {
  event.add('firmages:lecterns', /^(tfc|afc):wood\/lectern\//)
})
