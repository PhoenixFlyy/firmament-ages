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
  // Reload cost: every type, regex or output filter is one scan of all ~35,000 recipes. The removed types are
  // collected and removed with one OR filter, the id patterns with one regex, both at the end of this handler.
  const removeIdPatterns = []
  const removeTypes = []

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
    .forEach((t) => removeTypes.push({ type: t }))
  removeIdPatterns.push(/^evolvedmekanism:alloying\/compat\/ae2\//)

  // ======================================================================================== MekaSuit modules
  // Every MekaSuit and Meka-Tool module is built on the module base, which asked for four c:nuggets/bronze. The
  // material canon has no bronze nugget (Doc 10 v3 8.1: Mekanism's bronze nugget is a loser, disabled; the Create Big
  // Cannons bronze scrap has no source), so no module could be made (Felix 2026-10-13 late): TFC bronze ingots instead,
  // the rest of Mekanism's recipe unchanged. The modules' vanilla tools are swapped in gear_rule.js.
  event.remove({ id: 'mekanism:module_base' })
  event.shaped('2x mekanism:module_base', ['NIN', 'I#I', 'NIN'], {
    N: '#c:ingots/bronze',
    I: '#c:ingots/tin',
    '#': 'mekanism:hdpe_sheet'
  }).id('mekanism:module_base')

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
  // The infuser is an Information Age station (recipes/station_ages.js); blue steel waits for the Space Age as well.
  infuse('tfc:metal/ingot/weak_red_steel', 'tfc:metal/ingot/red_steel', 'firmages:metallurgic_infusing/red_steel')
  infuse('tfc:metal/ingot/weak_blue_steel', 'tfc:metal/ingot/blue_steel', 'firmages:metallurgic_infusing/blue_steel')
  FirmAges.requireAge('firmages:metallurgic_infusing/blue_steel', 'age_7')

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
  }).id('theurgy:crafting/shaped/sulfuric_flux_emitter') // same id: the Theurgy guide book shows it
  // No metal without ore (plan fix 8, 2026-10-15): reformation no longer makes osmium or uranium sulfur (the osmium vein
  // and the uraninite of the Space Age stay the only sources), and no metal niter comes from earth, gem or mineral
  // niter (stone -> metal). Transmuting one metal sulfur or metal niter into another stays: it starts from ore.
  const reformIds = []
  event.forEachRecipe({ type: 'theurgy:reformation' }, (r) => {
    const id = String(r.getId())
    if (/^theurgy:reformation\/alchemical_sulfur_(osmium|uranium)_from_/.test(id)
      || /^theurgy:reformation\/alchemical_niter_metals_[a-z]+_from_alchemical_niter_(earthen_matters|gems|other_minerals)_/.test(id)) reformIds.push(id)
  })
  reformIds.forEach((id) => event.remove({ id: id }))
  console.info(`[firmages] reformation: ${reformIds.length} recipes into osmium, uranium or metal niter from non-metals removed`)

  // ======================================================================================== Mystical Agriculture
  // Doc 08 section 4.4: hard-coded vanilla outputs to the canonical items (the tag outputs are pinned in
  // config/cucumber-tags.json), the farmland from TFC dirt instead of vanilla farmland, and no hoe-tilling recipe
  // (it eats a hoe). The crops of locked or foreign materials are off in config/mysticalcustomization/configure-crops.json;
  // tools, armour, paxels and augments are disabled (stages/disabled.toml).
  // One recipe each (the original recipes of 2026-10-01); a mod filter would scan all recipes three times.
  event.replaceOutput({ id: 'mysticalagriculture:essence/minecraft/coal' }, 'minecraft:coal', 'tfc:ore/bituminous_coal')
  event.replaceOutput({ id: 'mysticalagriculture:essence/minecraft/amethyst' }, 'minecraft:amethyst_shard', 'tfc:gem/amethyst')
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
  // Metal essence gives TFC ore pieces, not ingots (Felix 2026-10-15, harmony H4A; plan fix 2): eight essence in a
  // ring make two normal pieces (50 mB of metal) instead of 2 to 8 ingots, so Mystical Agriculture feeds the ore ladder
  // (Purification 3x, Injection 4x, Dissolution 5x) instead of skipping it. One piece per metal of the pack's metal
  // table (kubejs/data/firmages/balance/metals.json, the first TFC or TFC-IE ore of the metal); netherite essence gives ancient debris.
  // Same recipe ids as Mystical Agriculture's, so its guide and EMI pages keep working.
  const MA_METAL_PIECES = {
    'minecraft/iron_ingot': ['iron', 'tfc:ore/normal_hematite'],
    'minecraft/copper_ingot': ['copper', 'tfc:ore/normal_native_copper'],
    'minecraft/gold_ingot': ['gold', 'tfc:ore/normal_native_gold'],
    'common/tin_ingot': ['tin', 'tfc:ore/normal_cassiterite'],
    'common/silver_ingot': ['silver', 'tfc:ore/normal_native_silver'],
    'common/zinc_ingot': ['zinc', 'tfc:ore/normal_sphalerite'],
    'common/nickel_ingot': ['nickel', 'tfc:ore/normal_garnierite'],
    'common/lead_ingot': ['lead', 'tfc_ie_addon:ore/normal_galena'],
    'common/aluminum_ingot': ['aluminum', 'tfc_ie_addon:ore/normal_bauxite'],
    'common/uranium_ingot': ['uranium', 'tfc_ie_addon:ore/normal_uraninite'],
    'mekanism/osmium_ingot': ['osmium', 'firmages:ore/normal_native_osmium']
  }
  Object.keys(MA_METAL_PIECES).forEach((path) => {
    const id = `mysticalagriculture:essence/${path}`
    const essence = `mysticalagriculture:${MA_METAL_PIECES[path][0]}_essence`
    event.remove({ id: id })
    event.shaped(`2x ${MA_METAL_PIECES[path][1]}`, ['EEE', 'E E', 'EEE'], { E: essence }).id(id)
  })
  event.remove({ id: 'mysticalagriculture:essence/minecraft/netherite_ingot' })
  event.shaped('minecraft:ancient_debris', ['EEE', 'E E', 'EEE'], { E: 'mysticalagriculture:netherite_essence' })
    .id('mysticalagriculture:essence/minecraft/netherite_ingot')

  // ======================================================================================== Hostile Neural Networks
  // Plan fix 7: no metal without ore. The two living-matter recipes that made ingots give TFC ore pieces at a quarter of
  // the ingot count (8 vanilla iron -> 2 normal hematite, 6 gold -> 2 normal native gold, like the essence above);
  // the Loot Fabricator's metal drops are rewritten in kubejs/data/hostilenetworks/data_models/.
  // Same ingredients and ids as HNN's own recipes.
  event.remove({ id: 'hostilenetworks:living_matter/overworldian/iron_ingot' })
  event.shapeless('2x tfc:ore/normal_hematite', ['4x hostilenetworks:overworld_prediction', 'minecraft:rotten_flesh'])
    .id('hostilenetworks:living_matter/overworldian/iron_ingot')
  event.remove({ id: 'hostilenetworks:living_matter/hellish/gold_ingot' })
  event.shapeless('2x tfc:ore/normal_native_gold', ['minecraft:glowstone_dust', '#c:ingots/iron', 'hostilenetworks:nether_prediction'])
    .id('hostilenetworks:living_matter/hellish/gold_ingot')

  // ======================================================================================== goal: Data Matrix
  // A grid recipe like the Steel Heart and the Pressure Core: the Mekanism and AE2 parts carry the Age's chains,
  // and an AE2 Molecular Assembler can craft it. The token slot takes the age_6 token: an AE2 singularity from the
  // Matter Condenser (256,000 condenser power, a real automation line).
  event.shaped('firmages:data_matrix', ['BCB', 'CSC', 'BTB'], {
    B: '#c:sheets/blue_steel',
    C: 'mekanism:advanced_control_circuit',
    S: 'ae2:cell_component_64k',
    T: '#firmages:age_token/age_6'
  }).id('firmages:crafting/data_matrix')

  event.remove(removeTypes)
  event.remove({ id: new RegExp(removeIdPatterns.map((r) => r.source).join('|')) })
})
