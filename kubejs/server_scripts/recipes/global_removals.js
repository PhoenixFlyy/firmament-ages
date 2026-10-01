// Firmament Ages - static removals and unification (Doc 10 v3 sections 7.2, 7.3, 8.1).
// Scope of this draft: everything that exists by the Iron Age (TFC, Create, Occultism silver, FTB Quests book),
// plus the IE hammer grid recipes (Doc 10 v3 section 7.3).
// Age-bound recipe shaping lives in recipes/age_N/*.js; dynamic locks live in the ProgressiveStages TOMLs.
// Recipe ids below were checked against Create 6.0.10 generated data (tag mc1.21.1-6.0.10).

ServerEvents.recipes((event) => {
  // Reload cost: every output or regex id filter scans all ~35,000 recipes (about 17 ms per call on every Age
  // reload), a plain id is a map lookup. Open-ended id patterns are collected and removed in one pass at the end of
  // this handler.
  // Items that no recipe may make (the FTB Quests book, Create sheets, brass and zinc, Occultism silver) are in
  // [hidden] of dev/age_map.toml, so they are in the tag firmages:age_items/disabled and the firmages-core recipe
  // gate drops every recipe that makes one (SPEC section 4.3); an output filter here would only repeat that at the
  // cost of a full scan (dev/poc-results.md, "Content fixes after the late Ages"). poc_analyze.py G-1 checks it.
  const removeIdPatterns = []
  const anyOf = (list) => new RegExp(list.map((r) => r.source).join('|'))

  // ---- FTB Quests: the screen opens with K (config/defaultoptions/keybindings.txt), no book --------------
  // ftbquests:book is disabled (the gate drops its recipe).

  // ---- Twilight Forest: gear rule (Doc 08 section 8). Fiery armour and tools keep their direct recipes from fiery
  // ingots; the variants that upgrade a finished vanilla iron piece go.
  ;['helmet', 'chestplate', 'leggings', 'boots', 'pickaxe', 'sword'].forEach((s) => event.remove({ id: `twilightforest:equipment/fiery_iron_${s}` }))

  // ---- Create: TFC is the only zinc/brass/plate source -------------------------------------------------
  // Plates: TFC sheets are canonical (tags c:plates/<metal> get the TFC sheets in tags/unification.js).
  ;['iron_ingot', 'copper_ingot', 'gold_ingot', 'brass_ingot'].forEach((i) => event.remove({ id: `create:pressing/${i}` }))
  // create:iron_sheet, copper_sheet, golden_sheet and brass_sheet are disabled (the gate drops their recipes).

  // Brass: TFC brass from the crucible (Bronze Age) or the WoodenCog heated basin (Iron Age).
  event.remove({ id: 'create:mixing/brass_ingot' }) // create:brass_ingot is disabled

  // Zinc: TFC sphalerite is the only zinc. Create zinc ore/raw/ingot/block recipes go (zinc ingot, block, raw zinc
  // and raw zinc block are disabled); the zinc nugget stays.
  removeIdPatterns.push(/^create:(blasting|smelting)\/zinc_ingot_from_/)

  // Crushing: no Create crushing of vanilla/mod ores, raw metals or the four Create stones (Doc 10 v3 7.3).
  // TFC ore pieces -> canonical dust is added in recipes/age_2/iron_age.js.
  event.remove({ type: 'create:crushing', input: '#c:ores' })
  event.remove({ type: 'create:crushing', input: '#c:raw_materials' })
  // Same by id, for raw blocks (c:storage_blocks/raw_<metal>) and conditional compat ores.
  removeIdPatterns.push(/^create:crushing\/raw_/)
  removeIdPatterns.push(/^create:crushing\/.*_ore$/)
  removeIdPatterns.push(/^create:crushing\/compat\/.*ore/)
  ;['crimsite', 'asurine', 'veridium', 'ochrum'].forEach((s) => {
    event.remove({ id: `create:crushing/${s}` })
    event.remove({ id: `create:crushing/${s}_recycling` })
  })
  // Washing of crushed ores (crushed_raw_* are hidden).
  event.remove({ type: 'create:splashing', input: /^create:crushed_raw_/ })
  // POC: TFCreate Compat ore doubling recipes - recipe ids unknown until /kubejs dump in the PoC:
  // event.remove({ mod: 'tfcreate', type: 'create:crushing' })

  // ---- No vanilla furnace for ores (Doc 10 v3 section 6.1, Schmelzen losers) ----------------------------------
  // TFC has no vanilla furnace, but Create fans run smelting and blasting recipes (bulk blasting). TFC ore pieces sit
  // in c:raw_materials/<metal> and ore blocks in c:ores/<metal>, so a 10 mB small piece gave a whole ingot.
  // Occultism iesnium (Nether ore, no TFC counterpart) keeps its smelting.
  const furnaceOre = []
  ;['minecraft:smelting', 'minecraft:blasting'].forEach((type) => {
    event.forEachRecipe({ type: type }, (r) => {
      const ing = String(r.json.get('ingredient'))
      if (/"tag":"c:(ores|raw_materials)\//.test(ing) && ing.indexOf('iesnium') < 0) furnaceOre.push(String(r.getId()))
    })
  })
  furnaceOre.forEach((id) => event.remove({ id: id }))

  // ---- Immersive Engineering: no plates or ore dust from the Engineer's Hammer in the grid ----------------
  // Doc 10 v3 section 7.3 (IE row): all crafting/plate_*_hammering, crafting/hammercrushing_* and
  // crafting/raw_hammercrushing_* go; plates come from the TFC anvil / Create press / IE Metal Press.
  removeIdPatterns.push(/^immersiveengineering:crafting\/(plate_.*_hammering|hammercrushing_.*|raw_hammercrushing_.*)$/)

  // ---- Occultism: TFC silver is canonical --------------------------------------------------------------
  // Item ids checked against Occultism release/v1.21.1-1.224.4 item models.
  // Occultism silver ingot, block, nugget, dust and raw block are disabled (the gate drops their recipes).
  // Recipes that ask for Occultism silver take any silver ingot instead (TFC via c:ingots/silver): the Occultism
  // recipes that name the silver ingot or nugget (original recipes of 2026-10-01; the ones that make Occultism silver
  // are left out, the gate drops them). Plain ids: a mod filter scanned all recipes twice. poc_analyze.py C-3s
  // reports a recipe that still asks for Occultism silver.
  const OCCULT_SILVER = ['occultism:ritual/craft_infused_lenses', 'occultism:ritual/craft_satchel',
    'occultism:ritual/summon_foliot_crusher', 'occultism:ritual/craft_familiar_ring', 'occultism:ritual/craft_ritual_satchel_t1',
    'occultism:ritual/craft_ritual_satchel_t2', 'occultism:ritual/craft_ender_satchel', 'occultism:crafting/magic_lamp_empty',
    'occultism:ritual/craft_soul_gem', 'occultism:ritual/craft_infused_pickaxe', 'occultism:crafting/lens_frame_alt',
    'occultism:crafting/silver_sacrificial_bowl', 'occultism:crafting/storage_remote_inert', 'occultism:crafting/lens_frame',
    'occultism:crafting/dark_silver_sacrificial_bowl'].map((id) => ({ id: id }))
  event.replaceInput(OCCULT_SILVER, 'occultism:silver_ingot', '#c:ingots/silver')
  event.replaceInput(OCCULT_SILVER, 'occultism:silver_nugget', '#c:nuggets/silver')

  // The collected patterns, one pass (see the top of this handler).
  event.remove({ id: anyOf(removeIdPatterns) })
})
