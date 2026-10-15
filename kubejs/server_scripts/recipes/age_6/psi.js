// Firmament Ages - Psi, the programming magic of the Information Age (1.0 round C, V3B; dev/decisions-while-away.md
// 2026-10-15). Psi has no ores of its own: psidust, psimetal and the psigem come from redstone, TFC gold and diamond by
// its trick crafting, so the ore model stays whole. Its iron parts take Mekanism osmium (the Information Age metal,
// osmium.js), and the CAD Assembler needs two basic control circuits. The Encyclopaedia Psionica (a Patchouli book
// without a recipe in Psi 110) comes from a book and psidust.

ServerEvents.recipes((event) => {
  const todo = []
  event.forEachRecipe({ mod: 'psi' }, (r) => {
    if (String(r.getId()) === 'psi:assembler') return // replaced below
    const text = String(r.json)
    const next = text.split('"tag":"c:ingots/iron"').join('"tag":"c:ingots/osmium"')
    if (next !== text) todo.push([String(r.getId()), next])
  })
  todo.forEach((t) => {
    event.remove({ id: t[0] })
    event.custom(JSON.parse(t[1])).id(t[0])
  })
  event.remove({ id: 'psi:assembler' })
  event.shaped('psi:cad_assembler', ['OPO', 'C C', ' O '], {
    O: '#c:ingots/osmium',
    P: 'minecraft:piston',
    C: 'mekanism:basic_control_circuit'
  }).id('firmages:crafting/psi_cad_assembler')
  event.shapeless(Item.of('patchouli:guide_book[patchouli:book="psi:encyclopaedia_psionica"]'),
    ['minecraft:book', 'psi:psidust']).id('firmages:crafting/psi_encyclopaedia')
  console.info(`[firmages] psi: ${todo.length} recipes take osmium`)
})
