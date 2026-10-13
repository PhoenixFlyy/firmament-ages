// Firmament Ages - the Mystical Agriculture veins (decision 8A, 2026-10-13): MA's own ore generation is off
// (config/mysticalagriculture-common.toml, single ore model of Doc 08 v3 section 3.1), so the inferium and
// prosperity ores come from TFC-style pack veins like the osmium vein (startup_scripts/osmium.js), hidden until the
// Information Age (age_6). Needs a full game restart after changes.
//
// Like TFC's gem and mineral ores (tfc:ore/lapis_lazuli/<rock>): one grade, one ore block per host rock
// (firmages:ore/<ore>/<rock>, model tfc:block/ore = the raw rock texture plus MA's own ore overlay), mined into MA's
// own drop (loot: kubejs/data/firmages/loot_table/blocks/ore/, same counts and fortune bonus as MA's ore). The third
// vein places MA's own soulstone with soulium ore in basalt (no pack block). Vein data: kubejs/data/firmages/worldgen/.
// Ages and the ore disguise: dev/age_map.toml [ore_families] and [ore_blocks].

global.FA_MA_VEINS = {
  // rock -> TFC rock category modifier (TFC ore hardness = 6.5 + modifier, resistance 10; RockCategory in TFC 4.2.11)
  ROCKS: {
    granite: 0.2, diorite: 0.2, gabbro: 0.2,                                     // igneous intrusive
    rhyolite: 0.0, basalt: 0.0, andesite: 0.0, dacite: 0.0,                      // igneous extrusive
    quartzite: -0.2, slate: -0.2, phyllite: -0.2, schist: -0.2, gneiss: -0.2, marble: -0.2,   // metamorphic
    shale: -0.4, claystone: -0.4, limestone: -0.4, conglomerate: -0.4, dolomite: -0.4, chert: -0.4, chalk: -0.4,
    tuff: -0.4                                                                    // sedimentary
  },
  ORES: {
    // the base essence of every seed: all 21 rocks, shallow to middle depth
    inferium: { name: 'Inferium Ore', rocks: 'all' },
    // the seed and infusion material: igneous and metamorphic rocks only, deep
    prosperity: { name: 'Prosperity Ore', rocks: ['granite', 'diorite', 'gabbro', 'rhyolite', 'basalt', 'andesite',
      'dacite', 'quartzite', 'slate', 'phyllite', 'schist', 'gneiss', 'marble'] }
  }
}

StartupEvents.registry('block', (event) => {
  const V = global.FA_MA_VEINS
  Object.keys(V.ORES).forEach((ore) => {
    const o = V.ORES[ore]
    const rocks = o.rocks === 'all' ? Object.keys(V.ROCKS) : o.rocks
    rocks.forEach((rock) => {
      const rockName = rock.charAt(0).toUpperCase() + rock.slice(1)
      event.create(`firmages:ore/${ore}/${rock}`)
        .displayName(`${rockName} ${o.name}`)
        .hardness(6.5 + V.ROCKS[rock])
        .resistance(10)
        .requiresTool(true)
        .stoneSoundType()
        .mapColor('stone')
        .renderType('cutout_mipped')   // the overlay has transparent pixels (MA renders its ores cutout_mipped)
        .tagBlock('minecraft:mineable/pickaxe')
        .tagBlock('c:ores')            // TFC prospecting (tfc:prospectable = #c:ores) and TFC collapse
        .tagBlock(`c:ores/${ore}`)
        .parentModel('tfc:block/ore')
        .textures({ all: `tfc:block/rock/raw/${rock}`, overlay: `mysticalagriculture:block/${ore}_ore` })
    })
  })
})
