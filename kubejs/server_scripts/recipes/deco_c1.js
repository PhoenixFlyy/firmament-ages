// Firmament Ages - the deco mods of round C1 in TFC materials (content lock 2026-10-15, vorauswahl.md "Nehmen").
// The other new mods need no change: Advanced Chimneys, To the Bat Poles!, Connected Glass, Akashic Tome and A handful
// of acorns use tags that TFC items fill (c:ingots/iron, c:storage_blocks/iron, minecraft:logs, c:glass_blocks,
// c:cobblestones) or TFC items themselves; dev/derive_ages.py gives each output the Age of its inputs.

ServerEvents.recipes((event) => {
  // ---- Bibliocraft Legacy: the display case has no Every Compat module (16 colours per vanilla wood), and the
  // vanilla-wood originals are hidden like every deco mod's (dev/age_map.toml [hidden]). The oak display cases stay as
  // the one family and take any wooden slab, so a TFC or AFC slab makes them.
  event.replaceInput({ id: /^bibliocraft:color\/[a-z_]+\/wood\/oak\/display_case$/ }, 'minecraft:oak_slab',
    '#minecraft:wooden_slabs')

  // Every Compat builds the fancy sign in every TFC and AFC wood but writes no recipe for it (Bibliocraft's recipe ids
  // are wood/<wood>/fancy_sign); this is Bibliocraft's own pattern with the planks of that wood.
  const WOODS = {
    tfc: ['acacia', 'ash', 'aspen', 'birch', 'blackwood', 'chestnut', 'douglas_fir', 'hickory', 'kapok', 'mangrove',
      'maple', 'oak', 'palm', 'pine', 'rosewood', 'sequoia', 'spruce', 'sycamore', 'white_cedar', 'willow'],
    afc: ['araucaria', 'baobab', 'beech', 'cypress', 'eucalyptus', 'fig', 'ginkgo', 'hevea', 'ipe', 'ironwood', 'mahoe',
      'mahogany', 'teak', 'tualang']
  }
  Object.keys(WOODS).forEach((ns) => WOODS[ns].forEach((wood) => {
    event.shaped(`everycomp:bcl/${ns}/${wood}_fancy_sign`, ['P#P', 'P#P', ' R '], {
      P: `${ns}:wood/planks/${wood}`,
      '#': 'minecraft:paper',
      R: '#c:rods/wooden'
    }).id(`firmages:bibliocraft/fancy_sign/${ns}/${wood}`)
  }))

  // The printing table copies enchanted books for experience; enchantments come from the pack's enchanting only.
  event.remove({ id: 'bibliocraft:enchanted_book_cloning_in_printing_table' })
})
