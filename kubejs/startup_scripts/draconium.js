// Firmament Ages - the pack's draconium vein (dev/decisions-while-away.md, 2026-10-14; research n7-adventure 4). The
// End is an optional trip, so draconium must not come only from the End: a deep TFC vein in igneous intrusive rock,
// visible from the Space Age (age_7, dev/age_map.toml [ore_families]). Copies the osmium vein (startup_scripts/
// osmium.js). Needs a full game restart after changes.
//
// One ore block per grade and rock (firmages:ore/<grade>_draconium/<rock>, model tfc:block/ore = the raw rock texture
// plus a grade overlay), mined into ore pieces (firmages:ore/<grade>_draconium, startup_scripts/items.js) and a small
// surface piece as the vein indicator (firmages:ore/small_draconium, a TFC groundcover block). The pieces go through the
// Mekanism ore ladder into More Mekanism Processing's draconium chain (server_scripts/recipes/age_6/ore_ladder.js).
// Data (vein, loot, tags): kubejs/data/firmages/, kubejs/data/tfc/. Textures: dev/gen_textures.py.

global.FA_DRACONIUM = {
  GRADES: ['poor', 'normal', 'rich'],
  // rock -> TFC rock category modifier (TFC ore hardness = 6.5 + modifier, resistance 10); igneous intrusive only
  ROCKS: { granite: 0.2, diorite: 0.2, gabbro: 0.2 },
  GRADE_NAMES: { poor: 'Poor', normal: 'Normal', rich: 'Rich' }
}

StartupEvents.registry('block', (event) => {
  const D = global.FA_DRACONIUM
  D.GRADES.forEach((g) => {
    Object.keys(D.ROCKS).forEach((rock) => {
      const rockName = rock.charAt(0).toUpperCase() + rock.slice(1)
      event.create(`firmages:ore/${g}_draconium/${rock}`)
        .displayName(`${D.GRADE_NAMES[g]} ${rockName} Draconium`)
        .hardness(6.5 + D.ROCKS[rock])
        .resistance(10)
        .requiresTool(true)
        .stoneSoundType()
        .mapColor('stone')
        .renderType('cutout_mipped')   // the overlay has transparent pixels
        .tagBlock('minecraft:mineable/pickaxe')
        .tagBlock('c:ores')            // TFC prospecting (tfc:prospectable = #c:ores) and TFC collapse
        .tagBlock('c:ores/draconium')
        .parentModel('tfc:block/ore')
        .textures({ all: `tfc:block/rock/raw/${rock}`, overlay: `firmages:block/ore/${g}_draconium` })
    })
  })
  // Vein indicator on the surface (TFC "small ore"): a groundcover block that is its own item and drops itself.
  event.create('firmages:ore/small_draconium', 'tfc:groundcover')
    .ore()
    .groundcoverModelShape('native_silver')
    .texture('firmages:item/ore/small_draconium')
    .displayName('Small Draconium')
})
