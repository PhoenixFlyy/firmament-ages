// Firmament Ages - MekaTFC osmium in the Mekanism ore ladder (Doc 10 v3 sections 3.3, 6.1, 7.3, 8.1).
// MekaTFC 0.1.0 (kept, decision s2 B) adds the TFC-style osmium vein (visible from age_5) and osmium ore pieces.
// Its 10 purifying, injecting and dissolution recipes do not load (old Mekanism JSON: no "per_tick_usage", the
// dissolution output keyed "chemical" instead of "id"). They are rebuilt here per grade in the Mekanism 10.7 format
// (checked against mekanism:processing/osmium/* in Mekanism 10.7.19.85). Osmium stays a Mekanism item.
//
// Ladder (Doc 10 v3 matrix 6.1): Purification 3x (age_6), Injection 4x (age_7), Dissolution 5x (age_8).
// Yield per piece = piece mB x factor / 100 ingots (TFC pieces: small 10, poor 15, normal 25, rich 35 mB; a
// Mekanism ore block counts as one ingot). Clumps and shards are whole items, so a grade takes n pieces for m
// items with m/n within 5 % of the target; slurry is a chemical and takes the exact amount (200 mB = 1 crystal).
// The same helper serves the TFC ores when the Information Age content lands.

ServerEvents.tags('item', (event) => {
  // MekaTFC puts its ore pieces into c:raw_materials/osmium and its ore blocks into c:ores/osmium. Mekanism's own
  // raw-ore recipes then gave 3 small pieces (30 mB) 2,000 mB of slurry (10 ingots). The pieces keep only the
  // grade recipes below; the blocks drop pieces when mined.
  const GRADES = ['small', 'poor', 'normal', 'rich']
  event.remove('c:raw_materials/osmium', GRADES.map((g) => `mekatfc:ore/${g}_native_osmium`))
  event.remove('c:ores/osmium', /^mekatfc:ore\//)
})

ServerEvents.recipes((event) => {
  const GRADE_MB = { small: 10, poor: 15, normal: 25, rich: 35 }

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

  const oreLadder = (piecePrefix, metal, idBase) => {
    Object.keys(GRADE_MB).forEach((g) => {
      const piece = `${piecePrefix.replace('{g}', g)}`
      const mb = GRADE_MB[g]
      const p = ratio((mb * 3) / 100)
      event.custom({
        type: 'mekanism:purifying',
        chemical_input: { amount: 1, chemical: 'mekanism:oxygen' },
        item_input: { count: p[0], item: piece },
        output: { count: p[1], id: `mekanism:clump_${metal}` },
        per_tick_usage: true
      }).id(`firmages:purifying/${idBase}_${g}`)
      const i = ratio((mb * 4) / 100)
      event.custom({
        type: 'mekanism:injecting',
        chemical_input: { amount: 1, chemical: 'mekanism:hydrogen_chloride' },
        item_input: { count: i[0], item: piece },
        output: { count: i[1], id: `mekanism:shard_${metal}` },
        per_tick_usage: true
      }).id(`firmages:injecting/${idBase}_${g}`)
      event.custom({
        type: 'mekanism:dissolution',
        chemical_input: { amount: 1, chemical: 'mekanism:sulfuric_acid' },
        item_input: { count: 1, item: piece },
        output: { amount: mb * 10, id: `mekanism:dirty_${metal}` },
        per_tick_usage: true
      }).id(`firmages:dissolution/${idBase}_${g}`)
    })
  }
  oreLadder('mekatfc:ore/{g}_native_osmium', 'osmium', 'native_osmium')

  // MekaTFC recipes that load but break the canon: the vanilla furnace for ores (smelting, blasting), 2x
  // enrichment of ore pieces and the Mekanism crusher on ore pieces (Doc 10 v3 section 7.3: the crusher keeps
  // clump -> dirty dust only). Melting in a TFC crucible (tfc:heating, tfc:casting) stays the hand route.
  event.remove({ id: /^mekatfc:(smelting|blasting|enriching|crushing)\/ore\// })
  // The 10 unparseable originals are switched off by same-path overrides with a neoforge:false condition
  // (kubejs/data/mekatfc/recipe/{dissolution,injecting,purifying}/ore/), so nothing tries to parse them.
})
