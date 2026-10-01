// Firmament Ages - Age 6: Information Age (Doc 08 sections 2.2, 4.4, 6, 7.2 and 10.2; Doc 10 v3 sections 6, 7, 8).
// Canonical stations of the Age: Mekanism basic/advanced machines (ore 3x in ore_ladder.js, Metallurgic Infuser for
// Mekanism alloys and red steel, Osmium Compressor, PRC for HDPE, Gas-Burning Generator, Energy Cubes), AE2 +
// ExtendedAE (storage, autocrafting; the AE2 Inscriber is the one processor station), Applied Mekanistics, HNN for
// mob drops, Mystical Agriculture as the infinite-resource farm. The End opens through a built portal frame.
// Retired with this Age (helper stages, stages/grants.js): Tom's Simple Storage, IE LV/MV capacitors, the Create
// Mechanical Spawner.
// Goal: the Data Matrix (Mekanism advanced control circuits, AE2 64k storage component, blue steel sheets,
// dragon's breath).

ServerEvents.recipes((event) => {
  const removeIdPatterns = []

  // ======================================================================================== Mekanism stations
  // Doc 10 v3 7.2 / matrix 6.1 losers: the Precision Sawmill (the IE Sawmill saws), More Machine's planting station
  // and recycler (they bypass TFC farming and every ore chain), its stamper, lathe, rolling mill and presser (second
  // plate, rod, wire and AE2-processor stations next to the IE Metal Press and the AE2 Inscriber), Evolved
  // Mekanism's melter and solidifier (an own molten-metal economy next to the TFC fluids: "Flüssige Metalle: nur
  // tfc:metal/<m>") and its alloyer copies of AE2 processors and fluix.
  ;['mekanism:sawing', 'mekanism:planting', 'mekanism:recycling', 'mekanism:melting', 'mekanism:solidification',
    'mekanism:stamping', 'mekanism:lathing', 'mekanism:rolling_mill', 'mekanism:pressing',
    'mekmm:stamper', 'mekmm:lathe', 'mekmm:rolling_mill', 'mekmm:presser', 'mekmm:pressing', 'mekmm:planting', 'mekmm:recycler',
    'evolvedmekanism:melting', 'evolvedmekanism:solidifying']
    .forEach((t) => event.remove({ type: t }))
  removeIdPatterns.push(/^evolvedmekanism:alloying\/compat\/ae2\//)

  // ======================================================================================== AE2: one processor station
  // Create: Applied Kinetics 1.21.1 has no processor recipes (it ships an energy provider and an ME proxy only), so
  // the AE2 Inscriber stays the one press and processor station (decision log). ExtendedAE's circuit cutter and its
  // crystal-assembler processor copies are second stations of the same items; Mekanism's certus/quartz-blend
  // enrichment is a second silicon route next to the AE2 smelting one.
  removeIdPatterns.push(/^extendedae:cutter\//)
  ;['calculation_processor', 'engineering_processor', 'logic_processor'].forEach((p) => event.remove({ id: `extendedae:assembler/${p}` }))
  event.remove({ id: 'mekanism:compat/ae2/certus_quartz_dust_to_silicon' })
  event.remove({ id: 'extendedae:mek/quartz_blend' })
  // The inscriber presses come from meteorites, which do not generate in a TFC world (Doc 10 v3 7.1). One grid recipe
  // each from blue steel (the TFC metal of this Age) and the material the press prints; the inscriber copies them.
  const PRESS = { silicon_press: '#c:silicon', logic_processor_press: '#c:ingots/gold',
    calculation_processor_press: 'ae2:certus_quartz_crystal', engineering_processor_press: '#c:gems/diamond' }
  Object.keys(PRESS).forEach((p) => {
    event.shaped(`ae2:${p}`, [' B ', 'BMB', ' B '], { B: '#c:sheets/blue_steel', M: PRESS[p] }).id(`firmages:crafting/${p}`)
  })

  // ======================================================================================== red and blue steel
  // Matrix 6.1 "Schwarz-/Rot-/Blaustahl": the Metallurgic Infuser turns the weak alloy (IE Arc Furnace, recipes/age_5)
  // into the finished steel with carbon, the weld with pig iron and the hammering of the TFC way in one step. Red steel
  // from this Age; blue steel from the Space Age (loaded only then: blue steel is itself an Information Age metal).
  const infuse = (from, to, id) => event.custom({
    type: 'mekanism:metallurgic_infusing',
    item_input: { count: 1, item: from },
    chemical_input: { amount: 20, tag: 'mekanism:carbon' },
    output: { count: 1, id: to },
    per_tick_usage: false
  }).id(id)
  // Both load only with the Age of their station (recipes/station_ages.js cannot remove recipes added in this event).
  if (FirmAges.isUnlocked('age_6')) infuse('tfc:metal/ingot/weak_red_steel', 'tfc:metal/ingot/red_steel', 'firmages:metallurgic_infusing/red_steel')
  if (FirmAges.isUnlocked('age_7')) {
    infuse('tfc:metal/ingot/weak_blue_steel', 'tfc:metal/ingot/blue_steel', 'firmages:metallurgic_infusing/blue_steel')
  }

  // ======================================================================================== the End (Doc 08 section 6)
  // TFC has no strongholds, so the portal frame is crafted (12 frames + 12 eyes of ender light the portal as in vanilla).
  // Reinforced alloy ties it to the Mekanism strand of this Age; the frame item is age_6 (dev/age_map.toml).
  event.shaped('2x minecraft:end_portal_frame', ['OPO', 'ORO', 'OOO'], {
    O: 'minecraft:obsidian', P: 'minecraft:ender_pearl', R: 'mekanism:alloy_reinforced'
  }).id('firmages:crafting/end_portal_frame')

  // ======================================================================================== magic tail (Doc 08 section 7.2)
  // Ars Archmage: the spell book upgrade takes a Mekanism advanced control circuit instead of the totem of undying
  // (no evokers in a TFC world). Ars has no KubeJS schema for book_upgrade, so the recipe is rebuilt as JSON.
  event.remove({ id: 'ars_nouveau:archmage_book_upgrade' })
  event.custom({
    type: 'ars_nouveau:book_upgrade',
    category: 'misc',
    ingredients: [{ item: 'ars_nouveau:apprentice_spell_book' }, { tag: 'c:ender_pearls' }, { tag: 'c:ender_pearls' },
      { tag: 'c:ender_pearls' }, { tag: 'c:gems/emerald' }, { tag: 'c:gems/emerald' },
      { item: 'mekanism:advanced_control_circuit' }, { item: 'minecraft:nether_star' }, { item: 'ars_nouveau:wilden_tribute' }],
    result: { id: 'ars_nouveau:archmage_spell_book', count: 1 }
  }).id('ars_nouveau:archmage_book_upgrade')
  // Theurgy Reformation: the sulfuric flux emitter (the heart of the reformation array) takes Mekanism reinforced alloy.
  event.remove({ id: 'theurgy:crafting/shaped/sulfuric_flux_emitter' })
  event.shaped('theurgy:sulfuric_flux_emitter', [' a ', 'gSg', 'sRs'], {
    a: '#c:gems/sal_ammoniac', g: '#c:ingots/gold', S: '#theurgy:alchemical_sulfurs', s: '#c:stones', R: 'mekanism:alloy_reinforced'
  }).id('firmages:crafting/sulfuric_flux_emitter')

  // ======================================================================================== Mystical Agriculture
  // Doc 08 section 4.4: hard-coded vanilla outputs to the canonical items (the tag outputs are pinned in
  // config/cucumber-tags.json), the farmland from TFC dirt instead of vanilla farmland, and no hoe-tilling recipe
  // (it eats a hoe). The crops of locked or foreign materials are off in config/mysticalcustomization/configure-crops.json;
  // tools, armour, paxels and augments are disabled (stages/disabled.toml).
  event.replaceOutput({ mod: 'mysticalagriculture' }, 'minecraft:iron_ingot', 'tfc:metal/ingot/wrought_iron')
  event.replaceOutput({ mod: 'mysticalagriculture' }, 'minecraft:coal', 'tfc:ore/bituminous_coal')
  event.replaceOutput({ mod: 'mysticalagriculture' }, 'minecraft:amethyst_shard', 'tfc:gem/amethyst')
  ;['inferium', 'prudentium', 'tertium', 'imperium', 'supremium'].forEach((t) => {
    event.replaceInput({ id: `mysticalagriculture:${t}_farmland` }, 'minecraft:farmland', '#tfc:dirt')
    event.remove({ id: `mysticalagriculture:${t}_farmland_till` })
  })
  event.replaceInput({ id: 'mysticalagradditions:insanium_farmland' }, 'minecraft:farmland', '#tfc:dirt')
  event.remove({ id: 'mysticalagradditions:insanium_farmland_till' })
  event.remove({ id: 'mysticalagriculture:farmland_till' })
  // Essence into a vanilla water or lava bucket: a TFC world has no vanilla bucket to fill (TFC barrels and buckets
  // hold the fluids).
  event.remove({ id: 'mysticalagriculture:essence/minecraft/water_bucket' })
  event.remove({ id: 'mysticalagriculture:essence/minecraft/lava_bucket' })

  // ======================================================================================== goal: Data Matrix
  // A grid recipe like the Steel Heart and the Pressure Core: the Mekanism and AE2 parts carry the Age's chains,
  // and an AE2 Molecular Assembler can craft it. The boss slot is the age_6 token (dragon's breath, Ender Dragon).
  event.shaped('firmages:data_matrix', ['BCB', 'CSC', 'BTB'], {
    B: '#c:sheets/blue_steel',
    C: 'mekanism:advanced_control_circuit',
    S: 'ae2:cell_component_64k',
    T: '#firmages:boss_token/age_6'
  }).id('firmages:crafting/data_matrix')

  event.remove({ id: new RegExp(removeIdPatterns.map((r) => r.source).join('|')) })
})
