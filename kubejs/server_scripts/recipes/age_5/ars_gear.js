// Firmament Ages - the gear rule for Ars Elemental and Ars Elemancy armour (Doc 08 section 8, Felix's condition 15;
// 1.0 round C, dev/decisions-while-away.md 2026-10-15): no armour piece is an ingredient of a better one.
// Both mods upgrade armour on the Enchanting Apparatus (sauce:armor_upgrade): an Ars Nouveau set becomes an element
// set, two element sets become a dual set (Elemancy), two dual sets the Elemancer set. Each recipe here becomes a
// plain apparatus recipe (no kept data) whose armour inputs are the material of what they stood for: an Ars Nouveau
// piece -> magebloom fiber, an element piece -> the essence of its element, a dual piece -> the dual essence. The
// mark of mastery, essences and Source cost of the original stay. Where the mod has two recipes for one piece (the
// two orders of a dual set), the first one stays and the other is removed.
// dev/poc_analyze.py checks C-3g, C-4j and C-G guard it.

ServerEvents.recipes((event) => {
  const ELEMENT = { fire: 'ars_nouveau:fire_essence', aqua: 'ars_nouveau:water_essence',
    earth: 'ars_nouveau:earth_essence', air: 'ars_nouveau:air_essence' }
  const DUAL = ['cinder', 'lava', 'mire', 'silt', 'tempest', 'vapor', 'elemancer']
  const material = (id) => {
    let m = /^ars_nouveau:(sorcerer|arcanist|battlemage)_[a-z]+$/.exec(id)
    if (m) return 'ars_nouveau:magebloom_fiber'
    m = /^ars_elemental:(fire|aqua|earth|air)_[a-z_]+$/.exec(id)
    if (m) return ELEMENT[m[1]]
    m = /^ars_elemancy:([a-z]+)_[a-z_]+$/.exec(id)
    if (m && DUAL.indexOf(m[1]) >= 0) return `ars_elemancy:${m[1]}_essence`
    return null
  }
  const todo = []
  // cinder_boots_2 of Ars Elemancy 1.18.3 still names the old type ars_elemancy:armor_upgrade (Sauce reads it the same)
  event.forEachRecipe([{ type: 'sauce:armor_upgrade' }, { type: 'ars_elemancy:armor_upgrade' }], (r) => {
    const id = String(r.getId())
    if (!/^ars_(elemental|elemancy):/.test(id)) return
    todo.push([id, JSON.parse(String(r.json))])
  })
  const seen = {}
  let changed = 0
  let dropped = 0
  todo.forEach((row) => {
    const id = row[0]
    // let, not const: Rhino rejects a block-scoped const on the second call ("redeclaration of var j")
    let j = row[1]
    const out = j.result && j.result.id
    event.remove({ id: id })
    if (seen[out]) {
      dropped++
      return
    }
    seen[out] = true
    const fix = (ing) => {
      const to = ing && ing.item ? material(ing.item) : null
      return to ? { item: to } : ing
    }
    j.reagent = fix(j.reagent)
    j.pedestalItems = (j.pedestalItems || []).map(fix)
    j.type = 'ars_nouveau:enchanting_apparatus'
    j.keepNbtOfReagent = false
    event.custom(j).id(id)
    changed++
  })
  console.info(`[firmages] ars gear: ${changed} armour recipes from materials, ${dropped} duplicates removed`)
})
