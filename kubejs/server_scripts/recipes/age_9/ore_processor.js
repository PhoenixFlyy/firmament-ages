// Firmament Ages - the Ore Processor of the Singularity Age (harmony plan link 23 and section 5, firmages-core SPEC 40).
// Every ore piece of the metal table (kubejs/data/firmages/balance/metals.json, global.FA_METALS) gets one
// firmages:ore_processing recipe: n pieces of a grade for m dusts of the metal (the table's ratio for the station
// "ore_processor", 6x), with Mekanism hydrofluoric acid and Immersive Petroleum kerosene per dust; lubricant makes the
// operation faster, never richer. The station Age (age_9, recipes/station_ages.js) keeps the recipes out until the
// Singularity Age. dev/poc_analyze.py measures them with the rest of the ladder (L-1, L-2).
// The block: a Firmament Alloy shell around a Mekanism Chemical Dissolution Chamber (the 5x step it follows), an
// Awakened Core, two ultimate control circuits and an Auto Lubricator.

ServerEvents.recipes((event) => {
  const M = global.FA_METALS
  const P = M.table.ore_processor
  Object.keys(M.table.metals).forEach((metal) => {
    const d = M.table.metals[metal]
    if (d.stations.indexOf('ore_processor') < 0) return
    d.ores.forEach((pre) => {
      M.GRADES.forEach((g) => {
        const piece = pre.replace('{g}', g)
        const r = M.ratio(M.perPiece('ore_processor', g))
        const dusts = r[1]
        event.custom({
          type: 'firmages:ore_processing',
          input: { item: piece, count: r[0] },
          result: { id: d.dust, count: dusts },
          chemical: { id: P.chemical, amount: P.chemical_per_dust * dusts },
          fluid: { tag: P.fluid_tag, amount: P.fluid_per_dust * dusts },
          lubricant: { tag: P.lubricant_tag, amount: P.lubricant_per_dust * dusts },
          time: P.ticks_per_dust * dusts
        }).id(`firmages:ore_processing/${piece.split(':')[0]}/${piece.split('/')[1]}`)
      })
    })
  })

  event.shaped('firmages:ore_processor', ['FAF', 'UDU', 'FLF'], {
    F: 'firmages:firmament_alloy_ingot',
    A: 'draconicevolution:awakened_core',
    U: 'mekanism:ultimate_control_circuit',
    D: 'mekanism:chemical_dissolution_chamber',
    L: 'immersivepetroleum:auto_lubricator'
  }).id('firmages:crafting/ore_processor')
})
