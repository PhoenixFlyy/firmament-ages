// Firmament Ages - locked byproducts (policy in dev/decisions-while-away.md, 2026-10-01).
// The firmages-core recipe gate drops every recipe with an output of a locked Age, byproducts included. When only a
// chance byproduct is later than the main output and the station, the recipe would wait for the byproduct's Age.
// Instead this script adds a copy without the byproducts that are still locked, one copy per step of their Ages:
// "firmages:byproducts/<namespace>/<path>/before_<age>" lacks every byproduct of <age> and later and exists only while
// <age> is locked (FirmAges.untilAge); the gate keeps it out until the Ages of the byproducts it still has are open.
// Once every byproduct Age is open the original recipe takes over (the gate waits for its byproducts). A "disabled"
// byproduct never unlocks, so the last copy without it replaces the original under the original's id.
// The script is Age-independent: an Age change re-filters the recipes of the last load (the Age swap, firmages-core
// SPEC section 3.3). No gate allowlist.
// dev/poc_analyze.py check G-3 finds every such recipe in the all-Ages dump and fails if one is missing here.
// Table: recipe id -> { byproduct item id: Age }. One JSON object between the markers (poc_analyze reads it).
// BEGIN BYPRODUCTS
const FA_BYPRODUCTS = {
  "tfcreate:sequenced_assembly/precision_mechanism": { "create:crushed_raw_gold": "disabled" },
  "create:splashing/gravel": { "minecraft:iron_nugget": "age_2" },
  "create:crushing/tuff": { "minecraft:iron_nugget": "age_2", "immersiveengineering:nugget_electrum": "age_4" },
  "create:crushing/tuff_recycling": { "minecraft:iron_nugget": "age_2", "immersiveengineering:nugget_electrum": "age_4" },
  "immersiveengineering:crusher/blaze_powder": { "mekanism:dust_sulfur": "age_6" },
  "simulated:sequenced_assembly/engine_assembly": { "create:iron_sheet": "disabled" },
  "simulated:sequenced_assembly/gyroscopic_mechanism": { "create:iron_sheet": "disabled", "create:crushed_raw_iron": "disabled" },
  "create_connected:sequenced_assembly/control_chip": { "create:golden_sheet": "disabled", "create:iron_sheet": "disabled", "create:crushed_raw_gold": "disabled", "create:electron_tube": "age_2" },
  "create_factory_logistics:sequenced_assembly/fluid_mechanism": { "create:copper_sheet": "disabled", "create:crushed_raw_copper": "disabled" }
}
// END BYPRODUCTS

ServerEvents.recipes((event) => {
  const AGE_ORDER = ['dawn', 'age_0', 'age_1', 'age_2', 'age_3', 'age_4', 'age_5', 'age_6', 'age_7', 'age_8', 'age_9']
  // Item id of one result entry: Create {id, chance} / {item}, IE secondaries {output: {item}}.
  const entryItem = (e) => {
    if (!e) return null
    if (e.output) return entryItem(e.output)
    return e.id || e.item || null
  }
  const SHEET_TAG = { 'create:iron_sheet': 'c:plates/iron', 'create:copper_sheet': 'c:plates/copper',
    'create:golden_sheet': 'c:plates/gold', 'create:brass_sheet': 'c:plates/brass' }
  const SHEET_RE = new RegExp(`"item":"(${Object.keys(SHEET_TAG).join('|')})"`, 'g')
  let copies = 0
  Object.keys(FA_BYPRODUCTS).forEach((id) => {
    const table = FA_BYPRODUCTS[id]
    const found = []
    event.forEachRecipe({ id: id }, (r) => found.push(String(r.json)))
    if (found.length !== 1) {
      console.error(`[firmages] byproducts: ${id}: expected 1 recipe, found ${found.length}`)
      return
    }
    // The Ages that bring byproducts back, earliest first; "disabled" never does.
    const ages = []
    Object.keys(table).forEach((item) => {
      if (table[item] !== 'disabled' && ages.indexOf(table[item]) < 0) ages.push(table[item])
    })
    ages.sort((a, b) => AGE_ORDER.indexOf(a) - AGE_ORDER.indexOf(b))
    const disabled = Object.keys(table).some((item) => table[item] === 'disabled')
    // Step k: ages[0..k-1] are open. The last step without a disabled byproduct is the original itself.
    const steps = []
    for (let k = 0; k <= ages.length; k++) if (k < ages.length || disabled) steps.push(k)
    steps.forEach((k) => {
      const open = ages.slice(0, k)
      const strip = Object.keys(table).filter((item) => table[item] === 'disabled' || open.indexOf(table[item]) < 0)
      const json = JSON.parse(found[0])
      const keep = (e) => strip.indexOf(entryItem(e)) < 0
      let changed = false
      ;['results', 'secondaries'].forEach((key) => {
        if (!Array.isArray(json[key])) return
        const before = json[key].length
        json[key] = json[key].filter(keep)
        if (json[key].length !== before) changed = true
      })
      if (!changed) {
        console.error(`[firmages] byproducts: ${id}: none of ${strip} found in its results`)
        return
      }
      // A copy made here also loses its disabled Create sheet inputs (the Create sheets of DEAD in dead_inputs.js):
      // the control chip starts on a golden sheet, the fluid mechanism on a copper sheet.
      const text = JSON.stringify(json).replace(SHEET_RE, (m, from) => `"tag":"${SHEET_TAG[from]}"`)
      if (k === ages.length) {
        event.remove({ id: id })
        event.custom(JSON.parse(text)).id(id)
      } else {
        const copyId = `firmages:byproducts/${id.replace(':', '/')}/before_${ages[k]}`
        event.custom(JSON.parse(text)).id(copyId)
        FirmAges.untilAge(copyId, ages[k])
      }
      copies++
    })
  })
  console.info(`[firmages] byproducts: ${copies} recipe copies without locked byproducts`)
})
