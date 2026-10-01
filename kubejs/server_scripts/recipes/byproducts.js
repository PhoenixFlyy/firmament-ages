// Firmament Ages - locked byproducts (policy in dev/decisions-while-away.md, 2026-10-01).
// The firmages-core recipe gate drops every recipe with an output of a locked Age, byproducts included. When only a
// chance byproduct is later than the main output and the station, the recipe would wait for the byproduct's Age.
// Instead this script removes the byproduct while its Age is locked (FirmAges.isUnlocked reads the Ages of the reload
// in progress) and always for "disabled" items. The unlock reload brings the full recipe back. No gate allowlist.
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
  const lockedNow = (age) => age === 'disabled' || !FirmAges.isUnlocked(age)
  const STATION_AGE = { 'immersiveengineering:crusher': 'age_4' } // the machine types of FA_BYPRODUCTS above age_2
  // Item id of one result entry: Create {id, chance} / {item}, IE secondaries {output: {item}}.
  const entryItem = (e) => {
    if (!e) return null
    if (e.output) return entryItem(e.output)
    return e.id || e.item || null
  }
  const SHEET_TAG = { 'create:iron_sheet': 'c:plates/iron', 'create:copper_sheet': 'c:plates/copper',
    'create:golden_sheet': 'c:plates/gold', 'create:brass_sheet': 'c:plates/brass' }
  const SHEET_RE = new RegExp(`"item":"(${Object.keys(SHEET_TAG).join('|')})"`, 'g')
  let stripped = 0
  Object.keys(FA_BYPRODUCTS).forEach((id) => {
    const table = FA_BYPRODUCTS[id]
    const strip = Object.keys(table).filter((item) => lockedNow(table[item]))
    if (!strip.length) return
    const found = []
    event.forEachRecipe({ id: id }, (r) => found.push(JSON.parse(String(r.json))))
    if (found.length !== 1) {
      console.error(`[firmages] byproducts: ${id}: expected 1 recipe, found ${found.length}`)
      return
    }
    const json = found[0]
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
    // A recipe rewritten here also loses its disabled Create sheet inputs (the Create sheets of DEAD in dead_inputs.js):
    // the control chip starts on a golden sheet, the fluid mechanism on a copper sheet.
    const text = JSON.stringify(json).replace(SHEET_RE, (m, from) => `"tag":"${SHEET_TAG[from]}"`)
    event.remove({ id: id })
    // While the station's Age is locked the recipe stays removed (recipes/station_ages.js removes the mod's original,
    // but not a recipe re-added here).
    if (STATION_AGE[json.type] && lockedNow(STATION_AGE[json.type])) return
    event.custom(JSON.parse(text)).id(id)
    stripped++
  })
  console.info(`[firmages] byproducts: ${stripped} recipe(s) without their locked byproducts`)
})
