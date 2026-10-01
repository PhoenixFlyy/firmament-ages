// Firmament Ages - Age 4: Industrial Age recipes (Doc 08 sections 2.2, 4.2 and 9.3; Doc 10 v3 sections 3, 6, 7, 8).
// Immersive Engineering is the Industrial Age backbone: Coke Oven, Blast Furnace, Crusher, Metal Press, Alloy Kiln
// (window [age_4, age_5), stage tool_ie_alloy_kiln), LV power. Every IE output is the canonical item of Doc 10 v3
// section 8.1 (TFC ingots, sheets and rods for TFC metals; Mekanism / IE / pack dusts). Recipe ids and formats were
// read from the /fa_dump_full export of the loaded recipes (2026-09-30), after Almost Unified.
// Goal: the Pressure Core (black steel double sheet, IE Heavy Engineering Block, Arcane Gearbox, Monstrosity drop).

ServerEvents.recipes((event) => {
  // Reload cost: a plain id removal is a map lookup, but every regex, output or type filter scans all ~35,000
  // recipes (about 17 ms per call on every Age reload). Open-ended id patterns and output patterns are therefore
  // collected here and removed in one pass each at the end of this handler; fixed ids are removed one by one.
  const removeIdPatterns = []
  const removeOutputPatterns = []
  const anyOf = (list) => new RegExp(list.map((r) => r.source).join('|'))

  // ======================================================================================== coke oven
  // TFC + IE Crossover cokes TFC bituminous coal and lignite. IE's own recipes want vanilla coal (no TFC source).
  event.remove({ id: 'immersiveengineering:cokeoven/coke' })
  event.remove({ id: 'immersiveengineering:cokeoven/coke_block' })

  // ======================================================================================== crusher (ore -> dust)
  // Matrix Doc 10 v3 6.1: IE Crusher = 2x plus by-products. TFC ore pieces sit in c:raw_materials/<metal> and TFC
  // ore blocks in c:ores/<metal>, so IE's tag recipes crushed a 10 mB small piece into 1.33 dusts (133 mB). They go,
  // like the arc furnace ore doubling (Doc 10 v3 7.3) and TFC + IE's ore -> 2x TFC powder recipes.
  const oreTag = /"tag":"c:(ores|raw_materials)\/|"tag":"c:storage_blocks\/raw_/
  const oreRecipes = []
  ;['immersiveengineering:crusher', 'immersiveengineering:arc_furnace'].forEach((type) => {
    event.forEachRecipe({ type: type }, (r) => {
      if (oreTag.test(String(r.json.get('input')))) oreRecipes.push(String(r.getId()))
    })
  })
  oreRecipes.forEach((id) => event.remove({ id: id }))
  removeIdPatterns.push(/^tfc_ie_addon:crusher\/ore\//)
  event.remove({ id: 'tfc_ie_addon:crusher/wrought_iron_ingot' }) // same as immersiveengineering:crusher/ingot_iron

  // The IE Crusher takes ONE item and its main output has no chance, so 2x of a 10-35 mB piece cannot be a whole
  // dust. Main output: the TFC mineral powder in quern amount (the by-product: glass colourant, pigment); the metal
  // comes as the canonical dust with chance = 2 x piece mB / 100, plus IE's classic secondary metal at 5 % of that.
  const GRADE_MB = { small: 10, poor: 15, normal: 25, rich: 35 }
  const QUERN_POWDER = { small: 2, poor: 3, normal: 5, rich: 7 }
  const ORES = {
    // piece prefix: [powder, canonical dust, secondary dust or null]
    'tfc:ore/{g}_native_copper': ['tfc:powder/native_copper', 'mekanism:dust_copper', 'mekanism:dust_gold'],
    'tfc:ore/{g}_malachite': ['tfc:powder/malachite', 'mekanism:dust_copper', 'mekanism:dust_gold'],
    'tfc:ore/{g}_tetrahedrite': ['tfc:powder/tetrahedrite', 'mekanism:dust_copper', 'mekanism:dust_gold'],
    'tfc:ore/{g}_cassiterite': ['tfc:powder/cassiterite', 'mekanism:dust_tin', null],
    'tfc:ore/{g}_sphalerite': ['tfc:powder/sphalerite', 'firmages:dust/zinc', null],
    'tfc:ore/{g}_bismuthinite': ['tfc:powder/bismuthinite', 'firmages:dust/bismuth', null],
    'tfc:ore/{g}_native_gold': ['tfc:powder/native_gold', 'mekanism:dust_gold', null],
    'tfc:ore/{g}_native_silver': ['tfc:powder/native_silver', 'immersiveengineering:dust_silver', 'mekanism:dust_lead'],
    'tfc:ore/{g}_hematite': ['tfc:powder/hematite', 'mekanism:dust_iron', 'immersiveengineering:dust_nickel'],
    'tfc:ore/{g}_limonite': ['tfc:powder/limonite', 'mekanism:dust_iron', 'immersiveengineering:dust_nickel'],
    'tfc:ore/{g}_magnetite': ['tfc:powder/magnetite', 'mekanism:dust_iron', 'immersiveengineering:dust_nickel'],
    'tfc:ore/{g}_garnierite': ['tfc:powder/garnierite', 'immersiveengineering:dust_nickel', null],
    'tfc_ie_addon:ore/{g}_galena': ['tfc_ie_addon:powder/galena', 'mekanism:dust_lead', 'immersiveengineering:dust_silver'],
    'tfc_ie_addon:ore/{g}_bauxite': ['tfc_ie_addon:powder/bauxite', 'immersiveengineering:dust_aluminum', null],
    'tfc_ie_addon:ore/{g}_uraninite': ['tfc_ie_addon:powder/uraninite', 'mekanism:dust_uranium', 'mekanism:dust_lead']
  }
  // Pack recipes in a machine of this Age load only with it (recipes/station_ages.js cannot remove recipes added in the
  // same event), so a later station never shows an earlier output before the station exists.
  const age4 = FirmAges.isUnlocked('age_4')
  const round3 = (x) => Math.round(x * 1000) / 1000
  if (age4) Object.keys(ORES).forEach((pattern) => {
    const row = ORES[pattern]
    Object.keys(GRADE_MB).forEach((g) => {
      const piece = pattern.replace('{g}', g)
      const dustChance = round3((GRADE_MB[g] * 2) / 100)
      const secondaries = [{ chance: dustChance, output: { item: row[1] } }]
      if (row[2]) secondaries.push({ chance: round3(dustChance * 0.05), output: { item: row[2] } })
      event.custom({
        type: 'immersiveengineering:crusher',
        energy: 1000 + 100 * GRADE_MB[g],
        input: { item: piece },
        result: { basePredicate: { item: row[0] }, count: QUERN_POWDER[g] },
        secondaries: secondaries
      }).id(`firmages:crusher/${piece.split(':')[1].replace('ore/', '')}`)
    })
  })

  // ======================================================================================== metal press
  // TFC sheets come from TWO ingots (TFC + IE mold_sheet), like the anvil. IE's own plate mold pressed ONE ingot
  // into a TFC sheet after Almost Unified: a second recipe and double yield. It stays only for the IE-canon metals
  // (aluminium, lead, constantan, electrum, uranium: IE plate) and the planet metals.
  ;['brass', 'bronze', 'copper', 'gold', 'iron', 'nickel', 'silver', 'steel', 'tin', 'zinc', 'rose_gold']
    .forEach((m) => event.remove({ id: `immersiveengineering:metalpress/plate_${m}` }))
  event.remove({ id: 'tfc_ie_addon:metalpress/plate_wrought_iron' })

  // ======================================================================================== alloy kiln
  // Output TFC alloys in TFC ratios (Doc 10 v3 section 8.1; TFC 4.2.11 and TFC + IE alloy data):
  // bronze and brass 90 % copper, rose gold 75 % gold, sterling silver 67 % silver, constantan and electrum 50/50.
  // Bismuth bronze and black bronze need three metals and stay with the crucible and the heated basin.
  ;['bronze', 'brass', 'rose_gold', 'constantan', 'electrum', 'invar'].forEach((m) => event.remove({ id: `immersiveengineering:alloysmelter/${m}` }))
  const ALLOYS = [
    ['bronze', 'c:ingots/copper', 9, 'c:ingots/tin', 1, 'tfc:metal/ingot/bronze', 10],
    ['brass', 'c:ingots/copper', 9, 'c:ingots/zinc', 1, 'tfc:metal/ingot/brass', 10],
    ['rose_gold', 'c:ingots/gold', 3, 'c:ingots/copper', 1, 'tfc:metal/ingot/rose_gold', 4],
    ['sterling_silver', 'c:ingots/silver', 2, 'c:ingots/copper', 1, 'tfc:metal/ingot/sterling_silver', 3],
    ['constantan', 'c:ingots/copper', 1, 'c:ingots/nickel', 1, 'immersiveengineering:ingot_constantan', 2],
    ['electrum', 'c:ingots/gold', 1, 'c:ingots/silver', 1, 'immersiveengineering:ingot_electrum', 2]
  ]
  const sized = (tagId, n) => (n > 1 ? { basePredicate: { tag: tagId }, count: n } : { tag: tagId })
  if (age4) ALLOYS.forEach((a) => {
    event.custom({
      type: 'immersiveengineering:alloy',
      input0: sized(a[1], a[2]),
      input1: sized(a[3], a[4]),
      result: { basePredicate: { item: a[5] }, count: a[6] },
      time: 200
    }).id(`firmages:alloy_kiln/${a[0]}`)
  })

  // ======================================================================================== disabled IE parts
  // Doc 10 v3 section 7.2: IE steel gear (TFC steel gear is canonical), wind/water mills and the kinetic dynamo
  // (the C&A Alternator is the one SU -> FE bridge), conveyors except the basic belt (it is a block of the Metal
  // Press, Assembler and Auto Workbench multiblocks), the thermoelectric generator (no passive power), the refinery
  // (biodiesel), IE silver/nickel/steel ingots and IE plates of TFC metals. Items are also in disabled.toml.
  removeOutputPatterns.push(/^immersiveengineering:(pickaxe|shovel|axe|hoe|sword)_steel$/)
  removeOutputPatterns.push(/^immersiveengineering:armor_steel_(helmet|chestplate|leggings|boots)$/)
  removeOutputPatterns.push(/^immersiveengineering:(windmill|windmill_blade|windmill_sail|watermill|waterwheel_segment|dynamo|thermoelectric_generator)$/)
  removeOutputPatterns.push(/^immersiveengineering:conveyor_(dropper|extract|redstone|splitter|vertical)$/)
  event.remove([{ type: 'immersiveengineering:refinery' }, { type: 'immersiveengineering:thermoelectric_source' }])
  // Nothing may make a disabled IE item (raw silver/nickel block conversions and the like). Item ids are
  // [a-z0-9_:/] only, so they go into the pattern unescaped.
  removeOutputPatterns.push(new RegExp(`^(${global.FA.HIDDEN_ITEMS.filter((i) => i.indexOf('immersiveengineering:') === 0).join('|')})$`))

  // TFC + IE Crossover switches off IE's blast brick, alloy brick and reinforced blast brick recipes and adds none,
  // so the Blast Furnace and the Alloy Kiln could not be built. TFC fire bricks take the place of vanilla bricks.
  event.shaped('3x immersiveengineering:blastbrick', ['NFN', 'FMF', 'NFN'], {
    N: '#c:bricks/nether',
    F: 'tfc:ceramic/fire_brick',
    M: 'minecraft:magma_block'
  }).id('firmages:crafting/blastbrick')
  event.shaped('2x immersiveengineering:alloybrick', ['SF', 'FS'], {
    S: '#c:sandstone/blocks',
    F: 'tfc:ceramic/fire_brick'
  }).id('firmages:crafting/alloybrick')

  // ======================================================================================== Create Big Cannons
  // CBC ships its own alloying, steel and cast iron (heated mixing and compacting); these are second stations for
  // TFC metals (Doc 10 v3 section 6.1 losers "CBC-Stahl", "CBC-Gusseisen"). Melting and forging for cannon casts stay.
  removeIdPatterns.push(/^createbigcannons:mixing\/alloy_/)
  event.remove({ id: 'createbigcannons:compacting/iron_to_cast_iron_ingot' })
  event.remove({ id: 'createbigcannons:compacting/iron_to_cast_iron_block' })

  // ======================================================================================== Create Crafts & Additions
  // Only the Electric Motor (FE -> SU) and the Alternator (SU -> FE) stay (Doc 10 v3 section 1.2). Their spools and
  // capacitor are C&A items, so both are rebuilt around IE parts: the Industrial Age gate in the recipe.
  event.remove({ mod: 'createaddition' })
  // A type filter takes one id, not a regex: the three C&A recipe types (CARecipes, createaddition 1.7.1).
  event.remove(['charging', 'rolling', 'liquid_burning'].map((t) => ({ type: `createaddition:${t}` })))
  removeOutputPatterns.push(/^createaddition:/)
  event.custom({
    type: 'create:mechanical_crafting',
    accept_mirrored: true,
    category: 'misc',
    key: {
      A: { item: 'create:andesite_alloy' },
      I: { tag: 'c:plates/iron' },
      R: { tag: 'c:rods/iron' },
      S: { item: 'immersiveengineering:coil_lv' }
    },
    pattern: ['  A  ', ' ISI ', 'ISRSI', ' ISI ', '  A  '],
    result: { id: 'createaddition:alternator', count: 1 }
  }).id('firmages:mechanical_crafting/alternator')
  event.custom({
    type: 'create:mechanical_crafting',
    accept_mirrored: true,
    category: 'misc',
    key: {
      A: { item: 'create:andesite_alloy' },
      B: { tag: 'c:plates/brass' },
      C: { item: 'immersiveengineering:component_iron' },
      R: { tag: 'c:rods/iron' },
      S: { item: 'immersiveengineering:coil_lv' }
    },
    pattern: ['  A  ', ' BSB ', 'BSRSB', ' BCB '],
    result: { id: 'createaddition:electric_motor', count: 1 }
  }).id('firmages:mechanical_crafting/electric_motor')

  // ======================================================================================== Create 6 packages
  // Doc 10 v3 section 2.1: the package tier (item-locked until age_4) carries an IE part in its recipe.
  // Packager and Frogport take an IE iron mechanical component, Stock Link and Chain Conveyor a steel one;
  // Stock Ticker, Factory Gauge, Redstone Requester and the Repackager are made from these.
  event.remove({ id: 'create:crafting/logistics/packager' })
  event.remove({ id: 'tfcreate:packager' })
  event.shaped('create:packager', [' C ', 'CAC', 'RCR'], {
    A: 'create:cardboard_block',
    C: 'immersiveengineering:component_iron',
    R: '#c:dusts/redstone'
  }).id('firmages:crafting/packager')
  event.remove({ id: 'create:crafting/logistics/package_frogport' })
  event.remove({ id: 'tfcreate:package_frogport' })
  event.shaped('create:package_frogport', ['B', 'A', 'C'], {
    A: 'create:item_vault',
    B: 'tfc:glue',
    C: 'immersiveengineering:component_iron'
  }).id('firmages:crafting/package_frogport')
  event.remove({ id: 'create:crafting/logistics/stock_link' })
  event.shaped('create:stock_link', ['T', 'V', 'K'], {
    T: 'create:transmitter',
    V: 'create:item_vault',
    K: 'immersiveengineering:component_steel'
  }).id('firmages:crafting/stock_link')
  event.remove({ id: 'create:crafting/kinetics/chain_conveyor' })
  event.shaped('2x create:chain_conveyor', [' C ', 'CLC', ' K '], {
    C: 'create:andesite_casing',
    L: 'create:large_cogwheel',
    K: 'immersiveengineering:component_steel'
  }).id('firmages:crafting/chain_conveyor')

  // ======================================================================================== gear rule (age_4 items)
  // Doc 08 section 8: no tool or armour is an ingredient of a better one. Smithing upgrades keep the template and
  // the upgrade material, but the base becomes a TFC black steel part of the same slot (the Age 4 metal).
  const PART = {
    helmet: 'tfc:metal/unfinished_helmet/black_steel', chestplate: 'tfc:metal/unfinished_chestplate/black_steel',
    leggings: 'tfc:metal/unfinished_greaves/black_steel', boots: 'tfc:metal/unfinished_boots/black_steel',
    pickaxe: 'tfc:metal/pickaxe_head/black_steel', axe: 'tfc:metal/axe_head/black_steel',
    shovel: 'tfc:metal/shovel_head/black_steel', hoe: 'tfc:metal/hoe_head/black_steel',
    sword: 'tfc:metal/sword_blade/black_steel'
  }
  Object.keys(PART).forEach((slot) => {
    event.replaceInput({ id: `minecraft:netherite_${slot}_smithing` }, `minecraft:diamond_${slot}`, PART[slot])
  })
  ;['helmet', 'chestplate', 'leggings', 'boots'].forEach((slot) => {
    ;['cursium', 'ignitium'].forEach((set) =>
      event.replaceInput({ id: `cataclysm:smithing/${set}_${slot}` }, `minecraft:netherite_${slot}`, PART[slot]))
  })
  event.replaceInput({ id: 'cataclysm:smithing/monstrous_helm' }, 'minecraft:netherite_helmet', PART.helmet)
  event.replaceInput({ id: 'cataclysm:the_incinerator' }, 'minecraft:netherite_sword', PART.sword)
  // Apotheosis tier upgrades golden -> diamond are a gear chain; diamond gear keeps its plain grid recipes.
  removeIdPatterns.push(/^apotheosis:smithing\/upgrade_golden_.+_to_diamond_.+$/)
  // IE shield: from a vanilla shield (itself gear, and without a TFC recipe) -> a steel double sheet.
  event.replaceInput({ id: 'immersiveengineering:crafting/shield' }, 'minecraft:shield', 'tfc:metal/double_sheet/steel')
  // Create netherite diving gear and backtank: direct from the copper-free parts, never from the copper piece.
  ;['backtank', 'diving_helmet', 'diving_boots']
    .forEach((p) => event.remove({ id: `create:crafting/appliances/netherite_${p}_from_netherite` }))
  event.replaceInput({ id: 'create:crafting/appliances/netherite_backtank' }, 'create:copper_backtank', 'create:fluid_tank')
  event.replaceInput({ id: 'create:crafting/appliances/netherite_diving_helmet' }, 'create:copper_diving_helmet', PART.helmet)
  event.replaceInput({ id: 'create:crafting/appliances/netherite_diving_boots' }, 'create:copper_diving_boots', PART.boots)
  // Create Jetpack (Doc 08 section 8: "Create Jetpack 5.2.1 (direkt)"): no backtank, and a propeller for the
  // elytra (no End cities in this pack). The netherite jetpack is its own recipe, not an upgrade of the jetpack.
  ;['jetpack', 'netherite_jetpack', 'netherite_jetpack_upgrade', 'netherite_jetpack_upgrade_from_netherite']
    .forEach((id) => event.remove({ id: `create_jetpack:${id}` }))
  const jetpack = (id, plate, extra) => ({
    type: 'create:mechanical_crafting',
    accept_mirrored: true,
    category: 'misc',
    key: {
      P: { tag: plate },
      S: { item: 'create:shaft' },
      Y: { item: 'create:precision_mechanism' },
      X: extra,
      C: { item: 'create:chute' },
      E: { item: 'create:propeller' }
    },
    pattern: [' PSP ', 'PYXYP', 'PCECP', ' C C '],
    result: { id: id, count: 1 }
  })
  event.custom(jetpack('create_jetpack:jetpack', 'c:plates/brass', { item: 'create:fluid_tank' })).id('firmages:mechanical_crafting/jetpack')
  event.custom(jetpack('create_jetpack:netherite_jetpack', 'c:ingots/netherite', { item: 'create:fluid_tank' }))
    .id('firmages:mechanical_crafting/netherite_jetpack')

  // ======================================================================================== magic tail: Arcane Gearbox
  // Doc 08 section 7.2: a pack item from source gems, crafted in the grid so a Wixie can automate it; it sits in the
  // Improved Blast Furnace (reinforced blast bricks) and in the Pressure Core.
  event.shaped('firmages:arcane_gearbox', ['GSG', 'SBS', 'GSG'], {
    G: '#c:gems/source',
    S: '#c:plates/steel',
    B: 'create:gearbox'
  }).id('firmages:crafting/arcane_gearbox')
  event.shaped('4x immersiveengineering:blastbrick_reinforced', ['BSB', 'SGS', 'BSB'], {
    B: 'immersiveengineering:blastbrick',
    S: '#c:plates/steel',
    G: 'firmages:arcane_gearbox'
  }).id('firmages:crafting/blastbrick_reinforced')

  // ======================================================================================== goal: Pressure Core
  // Doc 08 sections 9.3 and 10.2. The boss slot is the age_4 boss token (Netherite Monstrosity horn).
  event.shaped('firmages:pressure_core', [' H ', 'DGD', ' T '], {
    H: 'immersiveengineering:heavy_engineering',
    D: '#c:double_sheets/black_steel',
    G: 'firmages:arcane_gearbox',
    T: '#firmages:boss_token/age_4'
  }).id('firmages:crafting/pressure_core')

  // Boss fallback stage 1 (Doc 08 section 5.1): the Summoning Rituals altar calls the Netherite Monstrosity.
  // JSON keys as the Summoning Rituals 3.14.2 KubeJS schema writes them (item_inputs, entity_outputs.entity.id).
  // Its spawn lock (age_4.toml) keeps it out of the world before the Industrial Age.
  if (FirmAges.isUnlocked('age_3')) event.custom({ // the altar is an Arcane Age station (recipes/station_ages.js)
    type: 'summoningrituals:altar',
    initiator: { tag: 'c:ingots/netherite' },
    item_inputs: [
      { count: 4, tag: 'c:rods/blaze' },
      { count: 2, item: 'tfc:metal/double_ingot/black_steel' },
      { count: 8, item: 'minecraft:magma_block' }
    ],
    entity_outputs: [{ entity: { id: 'cataclysm:netherite_monstrosity' } }],
    ticks: 200
  }).id('firmages:altar/netherite_monstrosity')

  // The collected patterns, one pass each (see the top of this handler).
  event.remove({ id: anyOf(removeIdPatterns) })
  event.remove({ output: anyOf(removeOutputPatterns) })
})
