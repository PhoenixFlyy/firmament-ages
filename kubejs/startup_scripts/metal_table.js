// Firmament Ages - the metal table for the recipe scripts (harmony plan 2026-10-15, package A4).
// kubejs/data/firmages/balance/metals.json holds every ore factor of the ladder: Create crushing 1.5x, Theurgy 2.0x,
// IE Crusher 2.5x (+ powder, 5 % secondary metal), Purification 3x, Injection 4x, Dissolution 5x, the powder rate of the
// Enrichment Chamber, the Arc Furnace dust energy and the Mineshaft grades. A startup script, because only those may
// set a global (KubeJS 2101): the table is read once per game start, so a changed metals.json needs a restart, not a
// /reload. dev/poc_analyze.py reads the same file and measures the recipes against it (checks L-1 to L-7).
// The file sits in the datapack folder so it ships with kubejs/; Minecraft ignores the unknown "balance" folder.

global.FA_METALS = (() => {
  const t = JSON.parse(String(JsonIO.readString('kubejs/data/firmages/balance/metals.json')))
  const factor = (station) => {
    const s = t.stations[station]
    if (!s) throw new Error(`[firmages] metal table: no station ${station}`)
    return Number(s.factor)
  }
  // n pieces -> m whole outputs for a target of `target` outputs per piece, n <= max_pieces: the smallest exact n
  // (within 0.5 %), else the smallest n within the tolerance, else the closest. No const/let inside the loop body
  // (Rhino "redeclaration of var", PoC fix 1).
  const relErr = (target, n) => {
    const m = Math.round(target * n)
    return m > 0 ? Math.abs(m / n - target) / target : 1e9
  }
  const ratio = (target) => {
    let bestN = 1
    let firstOk = 0
    for (let n = 1; n <= t.max_pieces; n++) {
      if (relErr(target, n) <= 0.005) return [n, Math.round(target * n)]
      if (!firstOk && relErr(target, n) <= t.tolerance) firstOk = n
      if (relErr(target, n) < relErr(target, bestN)) bestN = n
    }
    const n0 = firstOk || bestN
    return [n0, Math.max(1, Math.round(target * n0))]
  }
  return {
    table: t,
    GRADE_MB: t.grade_mb,
    GRADES: Object.keys(t.grade_mb),
    factor: factor,
    ratio: ratio,
    // items (one ingot each) per ore piece of a grade at a station
    perPiece: (station, grade) => (t.grade_mb[grade] * factor(station)) / t.units.item_mb
  }
})()
