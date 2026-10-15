// Firmament Ages - Building Gadgets 2 in the Electric Age (1.0 round C, vorauswahl M069; dev/decisions-while-away.md
// 2026-10-15). The three kept gadgets (building, exchanging, destruction) are built from Electric Age parts: aluminium
// sheets, an IE circuit board, electronic components and an MV capacitor (the gadgets run on FE,
// config/buildinggadgets2-common.toml). Copy-paste, cut-paste and the template manager are hidden ([hidden] in
// dev/age_map.toml); the exchanging gadget leaves TFC ores and raw rock alone (#buildinggadgets2:deny).

ServerEvents.recipes((event) => {
  const parts = {
    A: '#c:plates/aluminum',
    C: 'immersiveengineering:circuit_board',
    B: 'immersiveengineering:component_electronic',
    M: 'immersiveengineering:capacitor_mv'
  }
  const gadget = (id, pattern, extra) => {
    event.remove({ id: `buildinggadgets2:${id}` })
    event.shaped(`buildinggadgets2:${id}`, pattern, Object.assign({}, parts, extra)).id(`firmages:crafting/${id}`)
  }
  gadget('gadget_building', ['ACA', 'DMD', 'ABA'], { D: '#c:gems/diamond' })
  gadget('gadget_exchanging', ['ACA', 'LML', 'ABA'], { L: '#c:gems/lapis' })
  gadget('gadget_destruction', ['ACA', 'EME', 'ABA'], { E: '#c:ender_pearls' })
})
