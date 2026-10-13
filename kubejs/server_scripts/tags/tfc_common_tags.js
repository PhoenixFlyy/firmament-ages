// Firmament Ages - common tags for TFC ores and a few vanilla items that MekaTFC 0.1.0 used to provide.
// MekaTFC left the pack on 2026-10-13 (the pack's own osmium vein replaces it). Besides osmium it tagged every TFC ore
// block as c:ores/<metal>, every TFC ore piece as c:raw_materials/<metal>, the hay block as c:storage_blocks/wheat
// and #c:ingots/wrought_iron as c:ingots/iron, plus the parent tags c:ores, c:raw_materials and c:storage_blocks.
// The pack relies on these: Theurgy's divination rods and its mercury distillation and mineral salt calcination read c:ores and c:raw_materials, the Occultism animal rituals ask for
// c:storage_blocks/wheat, and "wrought iron is iron" (Doc 10 v3 section 8.1). They are kept here as pack data, same
// members as before (all grades and rocks; checked against a dump of pack 0.6.2 with MekaTFC).
// Not kept, because the pack's canon says otherwise (unification.js): TFC alloy sheets in the base-metal sheet tags
// (black bronze as c:sheets/bronze, rose gold as gold, black/blue/red steel as steel), TFC charcoal and lapis powder
// as c:dusts, and Mekanism sulfur dust as c:powders/sulfur.
// The Mekanism-family recipes that would eat these raw materials and ores are removed by fixed id
// (recipes/age_6/ore_ladder.js ORE_LADDER_REMOVALS; dev/poc_analyze.py C-6a reports one that slips through).

const FA_TFC_ORE_METAL = {
  native_copper: 'copper', malachite: 'copper', tetrahedrite: 'copper', native_gold: 'gold',
  hematite: 'iron', limonite: 'iron', magnetite: 'iron', native_silver: 'silver', cassiterite: 'tin',
  bismuthinite: 'bismuth', garnierite: 'nickel', sphalerite: 'zinc'
}
const FA_TFC_ORE_ROCKS = ['andesite', 'basalt', 'chalk', 'chert', 'claystone', 'conglomerate', 'dacite', 'diorite',
  'dolomite', 'gabbro', 'gneiss', 'granite', 'limestone', 'marble', 'phyllite', 'quartzite', 'rhyolite', 'schist',
  'shale', 'slate', 'tuff']

const faTfcOreBlocks = (ore) => {
  const out = []
  ;['poor', 'normal', 'rich'].forEach((g) => FA_TFC_ORE_ROCKS.forEach((rock) => out.push(`tfc:ore/${g}_${ore}/${rock}`)))
  return out
}

ServerEvents.tags('item', (event) => {
  Object.keys(FA_TFC_ORE_METAL).forEach((ore) => {
    const metal = FA_TFC_ORE_METAL[ore]
    event.add(`c:ores/${metal}`, faTfcOreBlocks(ore))
    event.add(`c:raw_materials/${metal}`, ['small', 'poor', 'normal', 'rich'].map((g) => `tfc:ore/${g}_${ore}`))
  })
  // and the parent tags that hold them, as MekaTFC had them
  const metals = ['copper', 'gold', 'iron', 'silver', 'tin', 'bismuth', 'nickel', 'zinc']
  event.add('c:ores', metals.map((m) => `#c:ores/${m}`))
  event.add('c:raw_materials', metals.map((m) => `#c:raw_materials/${m}`))
  event.add('c:storage_blocks', ['#c:storage_blocks/bismuth', '#c:storage_blocks/brass', '#c:storage_blocks/zinc',
    '#c:storage_blocks/wheat'])
  event.add('c:storage_blocks/wheat', 'minecraft:hay_block')
  event.add('c:ingots/iron', '#c:ingots/wrought_iron')
})

ServerEvents.tags('block', (event) => {
  Object.keys(FA_TFC_ORE_METAL).forEach((ore) => event.add(`c:ores/${FA_TFC_ORE_METAL[ore]}`, faTfcOreBlocks(ore)))
  event.add('c:storage_blocks/wheat', 'minecraft:hay_block')
  event.add('c:storage_blocks', '#c:storage_blocks/wheat')
})
