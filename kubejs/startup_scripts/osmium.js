// Firmament Ages - the pack's osmium vein (Doc 08 v3 section 3.3, Doc 10 v3 section 8.1: osmium stays a Mekanism
// material, the ore is a pack TFC vein, visible from age_5). Replaces MekaTFC 0.1.0 (decision 2026-10-13).
// Needs a full game restart after changes.
//
// TFC style, like the TFC + IE Crossover veins: one ore block per grade and rock (firmages:ore/<grade>_native_osmium/
// <rock>, model tfc:block/ore = the raw rock texture plus a grade overlay), mined into ore pieces
// (firmages:ore/<grade>_native_osmium), and a small surface piece as the vein indicator (firmages:ore/
// small_native_osmium, a TFC groundcover block). The rocks are those of MekaTFC's vein: igneous and metamorphic.
// Data (vein, loot, tags, heat, recipes): kubejs/data/firmages/, kubejs/data/tfc/, server_scripts/recipes/age_6/
// osmium.js and ore_ladder.js. Textures: dev/gen_textures.py. Ages: dev/age_map.toml [ore_families].

global.FA_OSMIUM = {
  GRADES: ['poor', 'normal', 'rich'],
  // rock -> TFC rock category (TFC ore hardness = 6.5 + category modifier, resistance 10; Ore.create in TFC 4.2.11)
  ROCKS: {
    granite: 0.2, diorite: 0.2, gabbro: 0.2,                       // igneous intrusive
    rhyolite: 0.0, basalt: 0.0, andesite: 0.0, dacite: 0.0,        // igneous extrusive
    quartzite: -0.2, schist: -0.2, gneiss: -0.2                    // metamorphic
  },
  GRADE_NAMES: { poor: 'Poor', normal: 'Normal', rich: 'Rich' }
}

StartupEvents.registry('block', (event) => {
  const O = global.FA_OSMIUM
  O.GRADES.forEach((g) => {
    Object.keys(O.ROCKS).forEach((rock) => {
      const rockName = rock.charAt(0).toUpperCase() + rock.slice(1)
      event.create(`firmages:ore/${g}_native_osmium/${rock}`)
        .displayName(`${O.GRADE_NAMES[g]} ${rockName} Native Osmium`)
        .hardness(6.5 + O.ROCKS[rock])
        .resistance(10)
        .requiresTool(true)
        .stoneSoundType()
        .mapColor('stone')
        .tagBlock('minecraft:mineable/pickaxe')
        .tagBlock('c:ores')
        .tagBlock('c:ores/osmium')
        .parentModel('tfc:block/ore')
        .textures({ all: `tfc:block/rock/raw/${rock}`, overlay: `firmages:block/ore/${g}_native_osmium` })
    })
  })
  // Vein indicator on the surface (TFC "small ore"): a groundcover block that is its own item and drops itself.
  event.create('firmages:ore/small_native_osmium', 'tfc:groundcover')
    .ore()
    .groundcoverModelShape('native_silver')
    .texture('firmages:item/ore/small_native_osmium')
    .displayName('Small Native Osmium')
})
