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
  // One pass per Mekanism-family namespace (a mod filter costs about 3 ms); only the ore-processing serializers are
  // read as JSON (painting and pigment recipes, about 850, are skipped by their type).
  const PLANET = /"c:(ores|raw_materials)\/(desh|ostrum|calorite|draconium)"|"c:storage_blocks\/raw_(desh|ostrum|calorite|draconium)"/
  const ORE_INPUT = /"tag":"c:(ores|raw_materials)\/|"tag":"c:storage_blocks\/raw_|"item":"(mekanism|mekmm|evolvedmekanism):(deepslate_|end_stone_|netherrack_)?[a-z]+_ore"|"item":"(mekanism|mekmm):raw_/
  const CRUSH_TYPES = ['mekanism:crushing', 'moremekanismprocessing:tag_crushing'] // serializer ids (JSON "type")
  const ORE_TYPES = /^(mekanism:(purifying|injecting|dissolution|enriching|crushing|combining)|moremekanismprocessing:tag_.*|minecraft:(smelting|blasting))$/
  const drop = []
  ;['mekanism', 'moremekanismprocessing', 'mekmm', 'evolvedmekanism', 'mekatfc', 'extendedae', 'advanced_ae'].forEach((ns) => {
    event.forEachRecipe({ mod: ns }, (r) => {
      const id = String(r.getId())
      if (ns === 'mekmm' && id.indexOf('mekmm:processing/silver/') === 0) { drop.push(id); return }
      const type = r.json.has('type') ? String(r.json.get('type').getAsString()) : String(r.getType())
      if (!ORE_TYPES.test(type)) return
      const json = String(r.json)
      if (ORE_INPUT.test(json) && !PLANET.test(json) && type !== 'minecraft:crafting_shaped' && type !== 'minecraft:crafting_shapeless') {
        drop.push(id)
        return
      }
      if (CRUSH_TYPES.indexOf(type) >= 0) {
        // the crusher keeps clump -> dirty dust, bio fuel and gem/obsidian/quartz to dust
        if (/"tag":"c:ingots\//.test(json) || /"id":"minecraft:(cobblestone|gravel|sand|red_sand|.*_tiles?|.*bricks?.*|.*stone.*|deepslate.*|tuff.*)"/.test(json)) drop.push(id)
        return
      }
      if (type === 'mekanism:combining' && /"output":\{"id":"[a-z_]+:[a-z_]*(_ore|ancient_debris)"/.test(json)) drop.push(id)
    })
  })
  drop.forEach((id) => event.remove({ id: id }))
  // MekaTFC's furnace, 2x enrichment and crusher recipes of ore pieces (Doc 10 v3 7.3). The 10 unparseable originals are
  // switched off by same-path overrides with a neoforge:false condition (kubejs/data/mekatfc/recipe/...).
  event.remove({ id: /^mekatfc:(smelting|blasting|enriching|crushing)\/ore\// })
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
