// Firmament Ages - the Mekanism ore ladder on TFC ore pieces (Doc 10 v3 sections 3.3, 6.1, 7.3, 8.1).
// Matrix 6.1, "Erz -> Staub": Purification 3x (age_6), Injection 4x (age_7), Dissolution 5x (age_8). Every TFC ore
// piece (and the MekaTFC osmium and TFC + IE galena, bauxite and uraninite pieces) gets one recipe per grade and step.
// Yield per piece = piece mB x factor / 100 ingots (TFC pieces: small 10, poor 15, normal 25, rich 35 mB; a Mekanism
// ore block counts as one ingot). Clumps and shards are whole items, so a grade takes n pieces for m items with m/n
// within 5 % of the target; slurry is a chemical and takes the exact amount (200 mB = 1 crystal).
// The steps after the first one (clump -> dirty dust -> dust, shard -> clump, crystal -> shard, slurry washing) are
// Mekanism's and More Mekanism Processing's own recipes and stay. The 4x and 5x entry recipes load only once their
// Age is unlocked (FirmAges.isUnlocked): their outputs (shards, slurry) are Information Age items, so the
// firmages-core gate alone would show them in the Information Age.
//
// Removed: every Mekanism-family recipe that eats an ore block, a raw ore or a raw-ore block of a TFC metal or gem.
// TFC puts its ore pieces into c:raw_materials/<metal> and its ore blocks into c:ores/<metal>, so these recipes
// applied vanilla raw-ore ratios to every grade (3 small pieces = 30 mB gave 2,000 mB slurry, 10 ingots). The planet
// and End metals (desh, ostrum, calorite, draconium) are no TFC ores and keep More Mekanism Processing's recipes.
// Also removed (matrix 6.1, losers): crushing ingots to dust in any Mekanism-family crusher, stone and gravel
// crushing, ore blocks from the combiner, and the duplicate silver chain of Mekanism: More Machine.

ServerEvents.tags('item', (event) => {
  // MekaTFC puts its ore pieces into c:raw_materials/osmium and its ore blocks into c:ores/osmium. The pieces keep
  // only the grade recipes below; the blocks drop pieces when mined.
  const GRADES = ['small', 'poor', 'normal', 'rich']
  event.remove('c:raw_materials/osmium', GRADES.map((g) => `mekatfc:ore/${g}_native_osmium`))
  event.remove('c:ores/osmium', /^mekatfc:ore\//)
})

ServerEvents.recipes((event) => {
  const GRADE_MB = { small: 10, poor: 15, normal: 25, rich: 35 }
  const age7 = FirmAges.isUnlocked('age_7')
  const age8 = FirmAges.isUnlocked('age_8')

  // ======================================================================================== removals
  // ORE_LADDER_REMOVALS below: every Mekanism-family recipe (Mekanism, More Mekanism Processing, Mekanism: More
  // Machine, Evolved Mekanism) that eats an ore block, raw ore or raw-ore block of a TFC metal or gem, crushes an
  // ingot, stone, gravel or sand in a Mekanism crusher, makes an ore block in the combiner, or belongs to More
  // Machine's silver chain. The planet and End metals (desh, ostrum, calorite, draconium) keep theirs.
  event.remove(ORE_LADDER_REMOVALS.map((id) => ({ id: id })))
  // MekaTFC's furnace, 2x enrichment and crusher recipes of ore pieces (Doc 10 v3 7.3). The 10 unparseable originals are
  // switched off by same-path overrides with a neoforge:false condition (kubejs/data/mekatfc/recipe/...).
  // Fixed ids, the 15 MekaTFC ships (a regex filter is one scan of all recipes; poc_analyze.py C-M reports a leftover).
  event.remove([
    'mekatfc:blasting/ore/normal_native_osmium', 'mekatfc:blasting/ore/poor_native_osmium',
    'mekatfc:blasting/ore/rich_native_osmium', 'mekatfc:blasting/ore/small_native_osmium',
    'mekatfc:crushing/ore/normal_native_osmium', 'mekatfc:crushing/ore/poor_native_osmium',
    'mekatfc:crushing/ore/rich_native_osmium', 'mekatfc:enriching/ore/normal_native_osmium',
    'mekatfc:enriching/ore/poor_native_osmium', 'mekatfc:enriching/ore/rich_native_osmium',
    'mekatfc:enriching/ore/small_native_osmium', 'mekatfc:smelting/ore/normal_native_osmium',
    'mekatfc:smelting/ore/poor_native_osmium', 'mekatfc:smelting/ore/rich_native_osmium',
    'mekatfc:smelting/ore/small_native_osmium'
  ].map((id) => ({ id: id })))
  // Mekanism's steel and bronze routes (losers of the "Stahl" and "Legieren" rows): enriched iron -> steel dust, and
  // copper + tin infusion. Enriched iron itself stays (fusion reactor glass).
  ;['mekanism:processing/steel/enriched_iron_to_dust', 'mekanism:processing/bronze/dust/from_infusing',
    'mekanism:processing/bronze/ingot/from_infusing'].forEach((id) => event.remove({ id: id }))

  // ======================================================================================== the ladder
  // Smallest n <= 10 pieces with m whole items and |m/n - target| <= 5 % (first found wins, else the closest).
  // No const/let inside the loop body: Rhino reports "redeclaration of var" on the second pass (PoC fix 1).
  const relErr = (target, n) => {
    const m = Math.round(target * n)
    return m > 0 ? Math.abs(m / n - target) / target : 1e9
  }
  const ratio = (target) => {
    let bestN = 1
    for (let n = 1; n <= 10; n++) {
      if (relErr(target, n) <= 0.05) return [n, Math.round(target * n)]
      if (relErr(target, n) < relErr(target, bestN)) bestN = n
    }
    return [bestN, Math.max(1, Math.round(target * bestN))]
  }

  // piece prefix with {g}, the metal, the namespace of its clumps, shards and slurry, and the id base
  const oreLadder = (piecePrefix, metal, ns, idBase) => {
    Object.keys(GRADE_MB).forEach((g) => {
      const piece = piecePrefix.replace('{g}', g)
      const mb = GRADE_MB[g]
      const p = ratio((mb * 3) / 100)
      event.custom({
        type: 'mekanism:purifying',
        chemical_input: { amount: 1, chemical: 'mekanism:oxygen' },
        item_input: { count: p[0], item: piece },
        output: { count: p[1], id: `${ns}:clump_${metal}` },
        per_tick_usage: true
      }).id(`firmages:purifying/${idBase}_${g}`)
      if (age7) {
        const i = ratio((mb * 4) / 100)
        event.custom({
          type: 'mekanism:injecting',
          chemical_input: { amount: 1, chemical: 'mekanism:hydrogen_chloride' },
          item_input: { count: i[0], item: piece },
          output: { count: i[1], id: `${ns}:shard_${metal}` },
          per_tick_usage: true
        }).id(`firmages:injecting/${idBase}_${g}`)
      }
      if (age8) {
        event.custom({
          type: 'mekanism:dissolution',
          chemical_input: { amount: 1, chemical: 'mekanism:sulfuric_acid' },
          item_input: { count: 1, item: piece },
          output: { amount: mb * 10, id: `${ns}:dirty_${metal}` },
          per_tick_usage: true
        }).id(`firmages:dissolution/${idBase}_${g}`)
      }
    })
  }
  const MEK = 'mekanism'
  const MMP = 'moremekanismprocessing'
  // [piece prefix, metal, namespace]; the id base is the ore name (osmium keeps its ids of 2026-09-30)
  ;[
    ['tfc:ore/{g}_native_copper', 'copper', MEK], ['tfc:ore/{g}_malachite', 'copper', MEK], ['tfc:ore/{g}_tetrahedrite', 'copper', MEK],
    ['tfc:ore/{g}_cassiterite', 'tin', MEK], ['tfc:ore/{g}_native_gold', 'gold', MEK],
    ['tfc:ore/{g}_hematite', 'iron', MEK], ['tfc:ore/{g}_limonite', 'iron', MEK], ['tfc:ore/{g}_magnetite', 'iron', MEK],
    ['tfc_ie_addon:ore/{g}_galena', 'lead', MEK], ['tfc_ie_addon:ore/{g}_uraninite', 'uranium', MEK],
    ['mekatfc:ore/{g}_native_osmium', 'osmium', MEK],
    ['tfc:ore/{g}_sphalerite', 'zinc', MMP], ['tfc:ore/{g}_bismuthinite', 'bismuth', MMP], ['tfc:ore/{g}_native_silver', 'silver', MMP],
    ['tfc:ore/{g}_garnierite', 'nickel', MMP], ['tfc_ie_addon:ore/{g}_bauxite', 'aluminum', MMP]
  ].forEach((row) => oreLadder(row[0], row[1], row[2], row[0].split('{g}_')[1]))

  // ======================================================================================== fluorite
  // Doc 10 v3 section 8.1: Mekanism fluorite from TFC cryolite (the Mekanism fluorite ore does not generate). The
  // enrichment chamber gives 2 gems per cryolite (Mekanism's own ore gives 6 per block of 9 pieces' worth).
  event.custom({
    type: 'mekanism:enriching',
    input: { count: 1, item: 'tfc:ore/cryolite' },
    output: { count: 2, id: 'mekanism:fluorite_gem' }
  }).id('firmages:enriching/fluorite_from_cryolite')
})

// The removals of the ore ladder as fixed ids, read on 2026-10-01 from the original recipes with the rules above
// (330 recipes; the 14 combiner recipes of vanilla ore blocks were missed by the earlier JSON pass, whose pattern
// expected "id" before "count"). Plain ids are a map lookup; the JSON pass over 2,700 Mekanism-family recipes cost
// about 65 ms of every reload. dev/poc_analyze.py C-6a, C-6b and C-6e report a recipe that the list misses.
const ORE_LADDER_REMOVALS = [
  'evolvedmekanism:processing/better_gold/dust/from_ingot', 'evolvedmekanism:processing/fluorite/to_end_stone_ore',
  'evolvedmekanism:processing/fluorite/to_netherrack_ore', 'evolvedmekanism:processing/lead/ingot/from_ore_blasting',
  'evolvedmekanism:processing/lead/ingot/from_ore_smelting', 'evolvedmekanism:processing/lead/ore/end_stone_from_raw',
  'evolvedmekanism:processing/lead/ore/netherrack_from_raw',
  'evolvedmekanism:processing/osmium/ingot/from_ore_blasting',
  'evolvedmekanism:processing/osmium/ingot/from_ore_smelting',
  'evolvedmekanism:processing/osmium/ore/end_stone_from_raw',
  'evolvedmekanism:processing/osmium/ore/netherrack_from_raw',
  'evolvedmekanism:processing/plaslitherite/dust/from_ingot',
  'evolvedmekanism:processing/refined_redstone/ingot_to_dust',
  'evolvedmekanism:processing/tin/ingot/from_ore_blasting', 'evolvedmekanism:processing/tin/ingot/from_ore_smelting',
  'evolvedmekanism:processing/tin/ore/end_stone_from_raw', 'evolvedmekanism:processing/tin/ore/netherrack_from_raw',
  'evolvedmekanism:processing/uranium/ingot/from_ore_blasting',
  'evolvedmekanism:processing/uranium/ingot/from_ore_smelting',
  'evolvedmekanism:processing/uranium/ore/end_stone_from_raw',
  'evolvedmekanism:processing/uranium/ore/netherrack_from_raw',
  'mekanism:crushing/blackstone/bricks_to_cracked_bricks', 'mekanism:crushing/blackstone/chiseled_bricks_to_bricks',
  'mekanism:crushing/blackstone/from_cracked_bricks', 'mekanism:crushing/blackstone/from_polished',
  'mekanism:crushing/blackstone/polished_slabs_to_slabs', 'mekanism:crushing/blackstone/polished_stairs_to_stairs',
  'mekanism:crushing/blackstone/polished_wall_to_wall', 'mekanism:crushing/chiseled_nether_bricks_to_nether_bricks',
  'mekanism:crushing/cobblestone_to_gravel', 'mekanism:crushing/deepslate/brick_slabs_to_tile',
  'mekanism:crushing/deepslate/brick_stairs_to_tile', 'mekanism:crushing/deepslate/brick_wall_to_tile',
  'mekanism:crushing/deepslate/bricks_to_cracked_bricks', 'mekanism:crushing/deepslate/cracked_bricks_to_tile',
  'mekanism:crushing/deepslate/from_chiseled', 'mekanism:crushing/deepslate/polished_slabs_to_brick',
  'mekanism:crushing/deepslate/polished_stairs_to_brick', 'mekanism:crushing/deepslate/polished_to_bricks',
  'mekanism:crushing/deepslate/polished_wall_to_brick', 'mekanism:crushing/deepslate/tile_to_cracked_tile',
  'mekanism:crushing/gravel_to_sand', 'mekanism:crushing/nether_bricks_to_cracked_nether_bricks',
  'mekanism:crushing/pointed_dripstone_from_block', 'mekanism:crushing/quartz/smooth_to_bricks',
  'mekanism:crushing/red_sandstone_to_sand', 'mekanism:crushing/sandstone_to_sand',
  'mekanism:crushing/stone/bricks_to_cracked_bricks', 'mekanism:crushing/stone/chiseled_bricks_to_bricks',
  'mekanism:crushing/stone/from_cracked_bricks', 'mekanism:crushing/stone/slabs_to_cobblestone_slabs',
  'mekanism:crushing/stone/stairs_to_cobblestone_stairs', 'mekanism:crushing/stone/to_cobblestone',
  'mekanism:crushing/tuff/chiseled_to_brick', 'mekanism:crushing/tuff/from_polished',
  'mekanism:crushing/tuff/slab_to_brick', 'mekanism:crushing/tuff/slabs_from_polished',
  'mekanism:crushing/tuff/stairs_from_polished', 'mekanism:crushing/tuff/stairs_to_brick',
  'mekanism:crushing/tuff/wall_from_polished', 'mekanism:crushing/tuff/wall_to_brick',
  'mekanism:enriching/ice_shard_or_to_ice_shards', 'mekanism:processing/bronze/dust/from_ingot',
  'mekanism:processing/coal/from_ore', 'mekanism:processing/coal/to_deepslate_ore', 'mekanism:processing/coal/to_ore',
  'mekanism:processing/copper/clump/from_ore', 'mekanism:processing/copper/clump/from_raw_block',
  'mekanism:processing/copper/clump/from_raw_ore', 'mekanism:processing/copper/dust/from_ingot',
  'mekanism:processing/copper/dust/from_ore', 'mekanism:processing/copper/dust/from_raw_block',
  'mekanism:processing/copper/dust/from_raw_ore', 'mekanism:processing/copper/ore/deepslate_from_raw',
  'mekanism:processing/copper/ore/from_raw', 'mekanism:processing/copper/shard/from_ore',
  'mekanism:processing/copper/shard/from_raw_block', 'mekanism:processing/copper/shard/from_raw_ore',
  'mekanism:processing/copper/slurry/dirty/from_ore', 'mekanism:processing/copper/slurry/dirty/from_raw_block',
  'mekanism:processing/copper/slurry/dirty/from_raw_ore', 'mekanism:processing/diamond/from_ore',
  'mekanism:processing/diamond/to_deepslate_ore', 'mekanism:processing/diamond/to_ore',
  'mekanism:processing/emerald/from_ore', 'mekanism:processing/emerald/to_deepslate_ore',
  'mekanism:processing/emerald/to_ore', 'mekanism:processing/fluorite/from_ore',
  'mekanism:processing/gold/clump/from_ore', 'mekanism:processing/gold/clump/from_raw_block',
  'mekanism:processing/gold/clump/from_raw_ore', 'mekanism:processing/gold/dust/from_ingot',
  'mekanism:processing/gold/dust/from_ore', 'mekanism:processing/gold/dust/from_raw_block',
  'mekanism:processing/gold/dust/from_raw_ore', 'mekanism:processing/gold/ore/deepslate_from_raw',
  'mekanism:processing/gold/ore/from_raw', 'mekanism:processing/gold/ore/nether_from_raw',
  'mekanism:processing/gold/shard/from_ore', 'mekanism:processing/gold/shard/from_raw_block',
  'mekanism:processing/gold/shard/from_raw_ore', 'mekanism:processing/gold/slurry/dirty/from_ore',
  'mekanism:processing/gold/slurry/dirty/from_raw_block', 'mekanism:processing/gold/slurry/dirty/from_raw_ore',
  'mekanism:processing/iron/clump/from_ore', 'mekanism:processing/iron/clump/from_raw_block',
  'mekanism:processing/iron/clump/from_raw_ore', 'mekanism:processing/iron/dust/from_ingot',
  'mekanism:processing/iron/dust/from_ore', 'mekanism:processing/iron/dust/from_raw_block',
  'mekanism:processing/iron/dust/from_raw_ore', 'mekanism:processing/iron/ore/deepslate_from_raw',
  'mekanism:processing/iron/ore/from_raw', 'mekanism:processing/iron/shard/from_ore',
  'mekanism:processing/iron/shard/from_raw_block', 'mekanism:processing/iron/shard/from_raw_ore',
  'mekanism:processing/iron/slurry/dirty/from_ore', 'mekanism:processing/iron/slurry/dirty/from_raw_block',
  'mekanism:processing/iron/slurry/dirty/from_raw_ore', 'mekanism:processing/lapis_lazuli/from_ore',
  'mekanism:processing/lapis_lazuli/to_deepslate_ore', 'mekanism:processing/lapis_lazuli/to_ore',
  'mekanism:processing/lead/clump/from_ore', 'mekanism:processing/lead/clump/from_raw_block',
  'mekanism:processing/lead/clump/from_raw_ore', 'mekanism:processing/lead/dust/from_ingot',
  'mekanism:processing/lead/dust/from_ore', 'mekanism:processing/lead/dust/from_raw_block',
  'mekanism:processing/lead/dust/from_raw_ore', 'mekanism:processing/lead/ingot/from_ore_blasting',
  'mekanism:processing/lead/ingot/from_ore_smelting', 'mekanism:processing/lead/ingot/from_raw_blasting',
  'mekanism:processing/lead/ingot/from_raw_smelting', 'mekanism:processing/lead/ore/deepslate_from_raw',
  'mekanism:processing/lead/ore/from_raw', 'mekanism:processing/lead/shard/from_ore',
  'mekanism:processing/lead/shard/from_raw_block', 'mekanism:processing/lead/shard/from_raw_ore',
  'mekanism:processing/lead/slurry/dirty/from_ore', 'mekanism:processing/lead/slurry/dirty/from_raw_block',
  'mekanism:processing/lead/slurry/dirty/from_raw_ore', 'mekanism:processing/netherite/ancient_debris_to_dirty_scrap',
  'mekanism:processing/netherite/ancient_debris_to_scrap', 'mekanism:processing/netherite/dust_to_ancient_debris',
  'mekanism:processing/netherite/ingot_to_dust', 'mekanism:processing/osmium/clump/from_ore',
  'mekanism:processing/osmium/clump/from_raw_block', 'mekanism:processing/osmium/clump/from_raw_ore',
  'mekanism:processing/osmium/dust/from_ingot', 'mekanism:processing/osmium/dust/from_ore',
  'mekanism:processing/osmium/dust/from_raw_block', 'mekanism:processing/osmium/dust/from_raw_ore',
  'mekanism:processing/osmium/ingot/from_ore_blasting', 'mekanism:processing/osmium/ingot/from_ore_smelting',
  'mekanism:processing/osmium/ingot/from_raw_blasting', 'mekanism:processing/osmium/ingot/from_raw_smelting',
  'mekanism:processing/osmium/ore/deepslate_from_raw', 'mekanism:processing/osmium/ore/from_raw',
  'mekanism:processing/osmium/shard/from_ore', 'mekanism:processing/osmium/shard/from_raw_block',
  'mekanism:processing/osmium/shard/from_raw_ore', 'mekanism:processing/osmium/slurry/dirty/from_ore',
  'mekanism:processing/osmium/slurry/dirty/from_raw_block', 'mekanism:processing/osmium/slurry/dirty/from_raw_ore',
  'mekanism:processing/quartz/from_ore', 'mekanism:processing/quartz/to_ore', 'mekanism:processing/redstone/from_ore',
  'mekanism:processing/redstone/to_deepslate_ore', 'mekanism:processing/redstone/to_ore',
  'mekanism:processing/refined_glowstone/ingot_to_dust', 'mekanism:processing/refined_obsidian/dust/from_ingot',
  'mekanism:processing/steel/ingot_to_dust', 'mekanism:processing/tin/clump/from_ore',
  'mekanism:processing/tin/clump/from_raw_block', 'mekanism:processing/tin/clump/from_raw_ore',
  'mekanism:processing/tin/dust/from_ingot', 'mekanism:processing/tin/dust/from_ore',
  'mekanism:processing/tin/dust/from_raw_block', 'mekanism:processing/tin/dust/from_raw_ore',
  'mekanism:processing/tin/ingot/from_ore_blasting', 'mekanism:processing/tin/ingot/from_ore_smelting',
  'mekanism:processing/tin/ingot/from_raw_blasting', 'mekanism:processing/tin/ingot/from_raw_smelting',
  'mekanism:processing/tin/ore/deepslate_from_raw', 'mekanism:processing/tin/ore/from_raw',
  'mekanism:processing/tin/shard/from_ore', 'mekanism:processing/tin/shard/from_raw_block',
  'mekanism:processing/tin/shard/from_raw_ore', 'mekanism:processing/tin/slurry/dirty/from_ore',
  'mekanism:processing/tin/slurry/dirty/from_raw_block', 'mekanism:processing/tin/slurry/dirty/from_raw_ore',
  'mekanism:processing/uranium/clump/from_ore', 'mekanism:processing/uranium/clump/from_raw_block',
  'mekanism:processing/uranium/clump/from_raw_ore', 'mekanism:processing/uranium/dust/from_ingot',
  'mekanism:processing/uranium/dust/from_ore', 'mekanism:processing/uranium/dust/from_raw_block',
  'mekanism:processing/uranium/dust/from_raw_ore', 'mekanism:processing/uranium/ingot/from_ore_blasting',
  'mekanism:processing/uranium/ingot/from_ore_smelting', 'mekanism:processing/uranium/ingot/from_raw_blasting',
  'mekanism:processing/uranium/ingot/from_raw_smelting', 'mekanism:processing/uranium/ore/deepslate_from_raw',
  'mekanism:processing/uranium/ore/from_raw', 'mekanism:processing/uranium/shard/from_ore',
  'mekanism:processing/uranium/shard/from_raw_block', 'mekanism:processing/uranium/shard/from_raw_ore',
  'mekanism:processing/uranium/slurry/dirty/from_ore', 'mekanism:processing/uranium/slurry/dirty/from_raw_block',
  'mekanism:processing/uranium/slurry/dirty/from_raw_ore', 'mekmm:processing/silver/clump/from_ore',
  'mekmm:processing/silver/clump/from_raw_block', 'mekmm:processing/silver/clump/from_raw_ore',
  'mekmm:processing/silver/clump/from_shard', 'mekmm:processing/silver/crystal/from_slurry',
  'mekmm:processing/silver/dirty_dust/from_clump', 'mekmm:processing/silver/dust/from_dirty_dust',
  'mekmm:processing/silver/dust/from_ingot', 'mekmm:processing/silver/dust/from_ore',
  'mekmm:processing/silver/dust/from_raw_block', 'mekmm:processing/silver/dust/from_raw_ore',
  'mekmm:processing/silver/ingot/from_ore_blasting', 'mekmm:processing/silver/ingot/from_ore_smelting',
  'mekmm:processing/silver/ingot/from_raw_blasting', 'mekmm:processing/silver/ingot/from_raw_smelting',
  'mekmm:processing/silver/ore/deepslate_from_raw', 'mekmm:processing/silver/ore/from_raw',
  'mekmm:processing/silver/raw/from_raw_block', 'mekmm:processing/silver/raw_storage_blocks/from_raw',
  'mekmm:processing/silver/shard/from_crystal', 'mekmm:processing/silver/shard/from_ore',
  'mekmm:processing/silver/shard/from_raw_block', 'mekmm:processing/silver/shard/from_raw_ore',
  'mekmm:processing/silver/slurry/clean', 'mekmm:processing/silver/slurry/dirty/from_ore',
  'mekmm:processing/silver/slurry/dirty/from_raw_block', 'mekmm:processing/silver/slurry/dirty/from_raw_ore',
  'moremekanismprocessing:processing/aluminum/clump/from_ore',
  'moremekanismprocessing:processing/aluminum/clump/from_raw_ore',
  'moremekanismprocessing:processing/aluminum/clump/from_raw_storage_blocks',
  'moremekanismprocessing:processing/aluminum/dust/from_ingot',
  'moremekanismprocessing:processing/aluminum/dust/from_ore',
  'moremekanismprocessing:processing/aluminum/dust/from_raw_ore',
  'moremekanismprocessing:processing/aluminum/dust/from_raw_storage_blocks',
  'moremekanismprocessing:processing/aluminum/shard/from_ore',
  'moremekanismprocessing:processing/aluminum/shard/from_raw_ore',
  'moremekanismprocessing:processing/aluminum/shard/from_raw_storage_blocks',
  'moremekanismprocessing:processing/aluminum/slurry/dirty/ore',
  'moremekanismprocessing:processing/aluminum/slurry/dirty/raw_ore',
  'moremekanismprocessing:processing/aluminum/slurry/dirty/raw_storage_blocks',
  'moremekanismprocessing:processing/amethyst/clump/from_ore',
  'moremekanismprocessing:processing/amethyst/gem/from_ore',
  'moremekanismprocessing:processing/amethyst/shard/from_ore',
  'moremekanismprocessing:processing/amethyst/slurry/dirty/ore',
  'moremekanismprocessing:processing/azure_silver/dust/from_ingot',
  'moremekanismprocessing:processing/bismuth/clump/from_ore',
  'moremekanismprocessing:processing/bismuth/clump/from_raw_ore',
  'moremekanismprocessing:processing/bismuth/dust/from_ingot',
  'moremekanismprocessing:processing/bismuth/dust/from_ore',
  'moremekanismprocessing:processing/bismuth/dust/from_raw_ore',
  'moremekanismprocessing:processing/bismuth/shard/from_ore',
  'moremekanismprocessing:processing/bismuth/shard/from_raw_ore',
  'moremekanismprocessing:processing/bismuth/slurry/dirty/ore',
  'moremekanismprocessing:processing/bismuth/slurry/dirty/raw_ore',
  'moremekanismprocessing:processing/calorite/dust/from_ingot',
  'moremekanismprocessing:processing/cinnabar/clump/from_ore',
  'moremekanismprocessing:processing/cinnabar/gem/from_ore',
  'moremekanismprocessing:processing/cinnabar/shard/from_ore',
  'moremekanismprocessing:processing/cinnabar/slurry/dirty/ore',
  'moremekanismprocessing:processing/cobalt/dust/from_ingot',
  'moremekanismprocessing:processing/crimson_iron/dust/from_ingot',
  'moremekanismprocessing:processing/desh/dust/from_ingot',
  'moremekanismprocessing:processing/draconium/dust/from_ingot',
  'moremekanismprocessing:processing/iridium/dust/from_ingot',
  'moremekanismprocessing:processing/lithium/dust/from_ingot',
  'moremekanismprocessing:processing/nickel/clump/from_ore',
  'moremekanismprocessing:processing/nickel/clump/from_raw_ore',
  'moremekanismprocessing:processing/nickel/clump/from_raw_storage_blocks',
  'moremekanismprocessing:processing/nickel/dust/from_ingot',
  'moremekanismprocessing:processing/nickel/dust/from_ore',
  'moremekanismprocessing:processing/nickel/dust/from_raw_ore',
  'moremekanismprocessing:processing/nickel/dust/from_raw_storage_blocks',
  'moremekanismprocessing:processing/nickel/shard/from_ore',
  'moremekanismprocessing:processing/nickel/shard/from_raw_ore',
  'moremekanismprocessing:processing/nickel/shard/from_raw_storage_blocks',
  'moremekanismprocessing:processing/nickel/slurry/dirty/ore',
  'moremekanismprocessing:processing/nickel/slurry/dirty/raw_ore',
  'moremekanismprocessing:processing/nickel/slurry/dirty/raw_storage_blocks',
  'moremekanismprocessing:processing/ostrum/dust/from_ingot',
  'moremekanismprocessing:processing/platinum/dust/from_ingot',
  'moremekanismprocessing:processing/ruby/clump/from_ore', 'moremekanismprocessing:processing/ruby/gem/from_ore',
  'moremekanismprocessing:processing/ruby/shard/from_ore', 'moremekanismprocessing:processing/ruby/slurry/dirty/ore',
  'moremekanismprocessing:processing/sapphire/clump/from_ore',
  'moremekanismprocessing:processing/sapphire/gem/from_ore',
  'moremekanismprocessing:processing/sapphire/shard/from_ore',
  'moremekanismprocessing:processing/sapphire/slurry/dirty/ore',
  'moremekanismprocessing:processing/silver/clump/from_ore',
  'moremekanismprocessing:processing/silver/clump/from_raw_ore',
  'moremekanismprocessing:processing/silver/clump/from_raw_storage_blocks',
  'moremekanismprocessing:processing/silver/dust/from_ingot',
  'moremekanismprocessing:processing/silver/dust/from_ore',
  'moremekanismprocessing:processing/silver/dust/from_raw_ore',
  'moremekanismprocessing:processing/silver/dust/from_raw_storage_blocks',
  'moremekanismprocessing:processing/silver/shard/from_ore',
  'moremekanismprocessing:processing/silver/shard/from_raw_ore',
  'moremekanismprocessing:processing/silver/shard/from_raw_storage_blocks',
  'moremekanismprocessing:processing/silver/slurry/dirty/ore',
  'moremekanismprocessing:processing/silver/slurry/dirty/raw_ore',
  'moremekanismprocessing:processing/silver/slurry/dirty/raw_storage_blocks',
  'moremekanismprocessing:processing/sulfur/clump/from_ore', 'moremekanismprocessing:processing/sulfur/dust/from_ore',
  'moremekanismprocessing:processing/sulfur/shard/from_ore',
  'moremekanismprocessing:processing/sulfur/slurry/dirty/ore',
  'moremekanismprocessing:processing/titanium/dust/from_ingot',
  'moremekanismprocessing:processing/tungsten/dust/from_ingot',
  'moremekanismprocessing:processing/zinc/clump/from_ore',
  'moremekanismprocessing:processing/zinc/clump/from_raw_ore',
  'moremekanismprocessing:processing/zinc/clump/from_raw_storage_blocks',
  'moremekanismprocessing:processing/zinc/dust/from_ingot', 'moremekanismprocessing:processing/zinc/dust/from_ore',
  'moremekanismprocessing:processing/zinc/dust/from_raw_ore',
  'moremekanismprocessing:processing/zinc/dust/from_raw_storage_blocks',
  'moremekanismprocessing:processing/zinc/shard/from_ore',
  'moremekanismprocessing:processing/zinc/shard/from_raw_ore',
  'moremekanismprocessing:processing/zinc/shard/from_raw_storage_blocks',
  'moremekanismprocessing:processing/zinc/slurry/dirty/ore',
  'moremekanismprocessing:processing/zinc/slurry/dirty/raw_ore',
  'moremekanismprocessing:processing/zinc/slurry/dirty/raw_storage_blocks'
]
