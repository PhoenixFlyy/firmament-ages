// Firmament Ages - static removals and unification (Doc 10 v3 sections 7.2, 7.3, 8.1).
// Scope of this draft: everything that exists by the Iron Age (TFC, Create, Occultism silver, FTB Quests book),
// plus the IE hammer grid recipes (Doc 10 v3 section 7.3).
// Age-bound recipe shaping lives in recipes/age_N/*.js; dynamic locks live in the ProgressiveStages TOMLs.
// Recipe ids below were checked against Create 6.0.10 generated data (tag mc1.21.1-6.0.10).

ServerEvents.recipes((event) => {
  // ---- FTB Quests: the screen opens with K (config/defaultoptions/keybindings.txt), no book --------------
  event.remove({ output: 'ftbquests:book' })

  // ---- Create: TFC is the only zinc/brass/plate source -------------------------------------------------
  // Plates: TFC sheets are canonical (tags c:plates/<metal> get the TFC sheets in tags/unification.js).
  ;['iron_ingot', 'copper_ingot', 'gold_ingot', 'brass_ingot'].forEach((i) => event.remove({ id: `create:pressing/${i}` }))
  event.remove({ output: /^create:(iron|copper|golden|brass)_sheet$/ })

  // Brass: TFC brass from the crucible (Bronze Age) or the WoodenCog heated basin (Iron Age).
  event.remove({ id: 'create:mixing/brass_ingot' })
  event.remove({ output: 'create:brass_ingot' })

  // Zinc: TFC sphalerite is the only zinc. Create zinc ore/raw/ingot/block recipes go; the zinc nugget stays.
  event.remove({ output: /^create:(zinc_ingot|zinc_block|raw_zinc|raw_zinc_block)$/ })
  event.remove({ id: /^create:(blasting|smelting)\/zinc_ingot_from_/ })

  // Crushing: no Create crushing of vanilla/mod ores, raw metals or the four Create stones (Doc 10 v3 7.3).
  // TFC ore pieces -> canonical dust is added in recipes/age_2/iron_age.js.
  event.remove({ type: 'create:crushing', input: '#c:ores' })
  event.remove({ type: 'create:crushing', input: '#c:raw_materials' })
  // Same by id, for raw blocks (c:storage_blocks/raw_<metal>) and conditional compat ores.
  event.remove({ id: /^create:crushing\/raw_/ })
  event.remove({ id: /^create:crushing\/.*_ore$/ })
  event.remove({ id: /^create:crushing\/compat\/.*ore/ })
  ;['crimsite', 'asurine', 'veridium', 'ochrum'].forEach((s) => {
    event.remove({ id: `create:crushing/${s}` })
    event.remove({ id: `create:crushing/${s}_recycling` })
  })
  // Washing of crushed ores (crushed_raw_* are hidden).
  event.remove({ type: 'create:splashing', input: /^create:crushed_raw_/ })
  // POC: TFCreate Compat ore doubling recipes - recipe ids unknown until /kubejs dump in the PoC:
  // event.remove({ mod: 'tfcreate', type: 'create:crushing' })

  // ---- Immersive Engineering: no plates or ore dust from the Engineer's Hammer in the grid ----------------
  // Doc 10 v3 section 7.3 (IE row): all crafting/plate_*_hammering, crafting/hammercrushing_* and
  // crafting/raw_hammercrushing_* go; plates come from the TFC anvil / Create press / IE Metal Press.
  event.remove({ id: /^immersiveengineering:crafting\/(plate_.*_hammering|hammercrushing_.*|raw_hammercrushing_.*)$/ })

  // ---- Occultism: TFC silver is canonical --------------------------------------------------------------
  // Item ids checked against Occultism release/v1.21.1-1.224.4 item models.
  event.remove({ output: /^occultism:(silver_ingot|silver_block|silver_nugget|silver_dust|raw_silver_block)$/ })
  // Recipes that ask for Occultism silver take any silver ingot instead (TFC via c:ingots/silver).
  // POC: check that the sacrificial bowl recipes use the item, not the tag.
  event.replaceInput({ mod: 'occultism' }, 'occultism:silver_ingot', '#c:ingots/silver')
  event.replaceInput({ mod: 'occultism' }, 'occultism:silver_nugget', '#c:nuggets/silver')
})
