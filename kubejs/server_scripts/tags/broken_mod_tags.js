// Firmament Ages - drop tag entries that point at items/blocks no mod registers.
// A single missing required entry makes vanilla discard the whole tag AND every tag that includes it.
// Found on the first test-server boot (dev/boot-report.md):
//  - MekaTFC 0.1.0 tags TFC-namespace lead/galena/cupronickel ids that TFC 4.2.11 does not have
//    (tfc:metal/ingot/lead, tfc:ore/poor_galena/granite, ...). That broke c:ores, c:ingots, c:dusts,
//    c:sheets, c:storage_blocks, c:raw_materials and, through #c:ores, tfc:prospectable,
//    tfc:can_collapse, tfc:can_start_collapse, tfc:can_trigger_collapse, tfc:monster_spawns_on and
//    tfc:powderkeg_breaking_blocks. Lead in this pack comes from TFC + IE Crossover (tfc_ie_addon).
//  - WoodenCog 1.2.19 tags woodencog:unfired_fireclay_crucible / woodencog:fireclay_crucible, which it
//    does not register, and so broke tfc:unfired_pottery.
// Remove this file once the mods ship fixed data (check the boot log for "Couldn't load tag").

const FA_TFC_ROCKS = ['andesite', 'basalt', 'chalk', 'chert', 'claystone', 'conglomerate', 'dacite', 'diorite',
  'dolomite', 'gabbro', 'gneiss', 'granite', 'limestone', 'marble', 'phyllite', 'quartzite', 'rhyolite', 'schist',
  'shale', 'slate', 'tuff']
const FA_GALENA_ORES = []
;['poor', 'normal', 'rich'].forEach((grade) => {
  FA_TFC_ROCKS.forEach((rock) => FA_GALENA_ORES.push(`tfc:ore/${grade}_galena/${rock}`))
})

ServerEvents.tags('item', (event) => {
  event.remove('c:ingots/lead', 'tfc:metal/ingot/lead')
  event.remove('c:ingots/cupronickel', 'tfc:metal/ingot/cupronickel')
  event.remove('c:sheets/lead', 'tfc:metal/sheet/lead')
  event.remove('c:storage_blocks/lead', 'tfc:metal/block/lead')
  event.remove('c:storage_blocks/cupronickel', 'tfc:metal/block/cupronickel')
  event.remove('c:dusts/lead', 'tfc:powder/galena')
  event.remove('c:raw_materials/lead', ['tfc:ore/small_galena', 'tfc:ore/poor_galena', 'tfc:ore/normal_galena',
    'tfc:ore/rich_galena'])
  event.remove('c:ores/lead', FA_GALENA_ORES)
  event.remove('tfc:unfired_pottery', 'woodencog:unfired_fireclay_crucible')
  event.remove('woodencog:unburnable', 'woodencog:fireclay_crucible')
})

ServerEvents.tags('block', (event) => {
  event.remove('c:ores/lead', FA_GALENA_ORES)
  event.remove('c:storage_blocks/lead', 'tfc:metal/block/lead')
  event.remove('c:storage_blocks/cupronickel', 'tfc:metal/block/cupronickel')
})
