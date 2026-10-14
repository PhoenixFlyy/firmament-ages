// Firmament Ages - signature blocks of the pack (research n2-deco-mods section 5). Needs a full game restart after
// changes. Textures: dev/gen_textures.py (LANTERN_PALETTES, ROCK_TONES). Ages: dev/age_map.toml [items].
// Recipes: server_scripts/recipes/signature_blocks.js (lanterns) and kubejs/data/firmages/recipe/chisel/ (bricks).
//
// Age Lanterns: one per Age, always lit (light 15, no fuel), the vanilla lantern shape (model
// minecraft:block/template_lantern) in the Age's metal and accent colour. Crafted from any TFC lamp plus one material
// of the Age. The vanilla lantern is disabled in the pack; these replace it as the decorative light of each Age.
// Engraved bricks: one per TFC rock, the TFC brick texture with a carved star, sun and moon (model tfc:block/ore =
// texture plus overlay, like the pack's ore blocks). Made with the TFC chisel (smooth mode) from chiseled rock.

global.FA_SIGNATURE_BLOCKS = {
  // lantern key -> Age, display name, the Age material of its recipe (one item or tag)
  LANTERNS: {
    stone: { age: 'age_0', name: 'Stone Age Lantern', material: 'minecraft:charcoal' },
    bronze: { age: 'age_1', name: 'Bronze Age Lantern', material: 'tfc:metal/ingot/bronze' },
    iron: { age: 'age_2', name: 'Iron Age Lantern', material: 'tfc:metal/ingot/wrought_iron' },
    arcane: { age: 'age_3', name: 'Arcane Age Lantern', material: 'ars_nouveau:source_gem' },
    industrial: { age: 'age_4', name: 'Industrial Age Lantern', material: 'immersiveengineering:coal_coke' },
    electric: { age: 'age_5', name: 'Electric Age Lantern', material: '#c:ingots/aluminum' },
    information: { age: 'age_6', name: 'Information Age Lantern', material: 'ae2:certus_quartz_crystal' },
    space: { age: 'age_7', name: 'Space Age Lantern', material: 'ad_astra:desh_ingot' },
    quantum: { age: 'age_8', name: 'Quantum Age Lantern', material: 'draconicevolution:awakened_draconium_ingot' },
    singularity: { age: 'age_9', name: 'Singularity Age Lantern', material: 'draconicevolution:small_chaos_frag' }
  },
  // every TFC rock (TFC 4.2.11 has 21)
  ROCKS: ['andesite', 'basalt', 'chalk', 'chert', 'claystone', 'conglomerate', 'dacite', 'diorite', 'dolomite',
    'gabbro', 'gneiss', 'granite', 'limestone', 'marble', 'phyllite', 'quartzite', 'rhyolite', 'schist', 'shale',
    'slate', 'tuff']
}

StartupEvents.registry('block', (event) => {
  const B = global.FA_SIGNATURE_BLOCKS
  Object.keys(B.LANTERNS).forEach((key) => {
    // modelGenerator, not parentModel + box: KubeJS adds box elements to a parent model, which would hide the lantern
    event.create(`firmages:lantern/${key}`)
      .displayName(B.LANTERNS[key].name)
      .hardness(3.5)
      .resistance(3.5)
      .requiresTool(true)
      .soundType('lantern')
      .mapColor('metal')
      .lightLevel(1.0)
      .renderType('cutout')
      .notSolid()
      .box(5, 0, 5, 11, 9, 11)
      .tagBlock('minecraft:mineable/pickaxe')
      .modelGenerator((m) => {
        m.parent('minecraft:block/template_lantern')
        m.texture('lantern', `firmages:block/lantern/${key}`)
      })
  })
  B.ROCKS.forEach((rock) => {
    const rockName = rock.charAt(0).toUpperCase() + rock.slice(1)
    event.create(`firmages:rock/engraved_bricks/${rock}`)
      .displayName(`Engraved ${rockName} Bricks`)
      .hardness(4.0)
      .resistance(10)
      .requiresTool(true)
      .stoneSoundType()
      .mapColor('stone')
      .renderType('cutout_mipped')   // the overlay has transparent pixels
      .tagBlock('minecraft:mineable/pickaxe')
      .parentModel('tfc:block/ore')
      .textures({ all: `tfc:block/rock/bricks/${rock}`, overlay: `firmages:block/rock/engraved/${rock}` })
  })
})
