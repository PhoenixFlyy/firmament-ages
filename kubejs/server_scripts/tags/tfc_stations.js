// Firmament Ages - the TFC counterparts of vanilla blocks that a TFC world cannot make, as item tags for
// recipes/tfc_station_inputs.js and recipes/age_3/arcane_tfc_inputs.js. Only TFC and Arborfirmacraft woods: the
// Twilight Forest chests are made from these, so a tag with them (c:chests) would let a Twilight chest make two more.

ServerEvents.tags('item', (event) => {
  event.add('firmages:chests/tfc', /^(tfc|afc):wood\/chest\//)
  event.add('firmages:chests/trapped_tfc', /^(tfc|afc):wood\/trapped_chest\//)
  event.add('firmages:lecterns', /^(tfc|afc):wood\/lectern\//)
})
