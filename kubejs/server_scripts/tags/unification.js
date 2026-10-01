// Firmament Ages - tag unification up to the Iron Age (Doc 10 v3 section 8).
// TFC 4.2.11 tags its metals as c:ingots/<metal>, c:sheets/<metal>, c:double_ingots/<metal> and
// c:ingots/wrought_iron (verified in the TFC data tree). Create 6.0.10 asks for c:plates/iron, c:ingots/iron,
// c:storage_blocks/iron|copper and c:plates/gold (verified in its recipes). These additions bridge the two.
// Almost Unified 1.4.2 only picks the target item per tag (materials.json); the tag contents are set here.

ServerEvents.tags('item', (event) => {
  // Wrought iron IS iron in this pack (Doc 10 v3 section 8.1).
  event.add('c:ingots/iron', 'tfc:metal/ingot/wrought_iron')
  event.remove('c:ingots/iron', 'minecraft:iron_ingot')
  event.add('c:storage_blocks/iron', 'tfc:metal/block/wrought_iron')
  event.add('c:storage_blocks/copper', 'tfc:metal/block/copper')

  // TFC sheets are the canonical plates.
  const SHEETS = {
    iron: 'wrought_iron', copper: 'copper', gold: 'gold', brass: 'brass', bronze: 'bronze', tin: 'tin',
    zinc: 'zinc', silver: 'silver', steel: 'steel', nickel: 'nickel', bismuth: 'bismuth',
    bismuth_bronze: 'bismuth_bronze', black_bronze: 'black_bronze', rose_gold: 'rose_gold',
    sterling_silver: 'sterling_silver', cast_iron: 'cast_iron'
  }
  Object.keys(SHEETS).forEach((m) => event.add(`c:plates/${m}`, `tfc:metal/sheet/${SHEETS[m]}`))
  ;['iron', 'copper', 'gold', 'brass'].forEach((m) => event.remove(`c:plates/${m}`, `create:${m === 'gold' ? 'golden' : m}_sheet`))

  // Andesite tier (late Bronze Age): Create's iron parts are made from any bronze instead (Doc 10 v3 2.1).
  event.add('firmages:plates/any_bronze', ['tfc:metal/sheet/bronze', 'tfc:metal/sheet/bismuth_bronze', 'tfc:metal/sheet/black_bronze'])
  event.add('firmages:ingots/any_bronze', ['tfc:metal/ingot/bronze', 'tfc:metal/ingot/bismuth_bronze', 'tfc:metal/ingot/black_bronze'])
  event.add('firmages:storage_blocks/any_bronze', ['tfc:metal/block/bronze', 'tfc:metal/block/bismuth_bronze', 'tfc:metal/block/black_bronze'])

  // Almost Unified (config/almostunified/unification/materials.json) turns every member of a unify tag into
  // the tag's target item, in recipe inputs AND outputs. TFC puts its alloys and ore powders into the base-metal
  // tags, so without these removals AU turned rose gold into gold, black/red/blue steel into steel, cast iron
  // into iron and a 5 mB TFC ore powder into a 100 mB Mekanism dust (PoC 2026-09-30, dev/poc-results.md).
  const NOT_THE_METAL = {
    'c:ingots/iron': ['tfc:metal/ingot/cast_iron', 'tfc:metal/ingot/pig_iron'],
    'c:storage_blocks/iron': ['tfc:metal/block/cast_iron'],
    'c:ingots/gold': ['tfc:metal/ingot/rose_gold'],
    'c:storage_blocks/gold': ['tfc:metal/block/rose_gold'],
    'c:ingots/silver': ['tfc:metal/ingot/sterling_silver'],
    'c:storage_blocks/silver': ['tfc:metal/block/sterling_silver'],
    'c:ingots/bronze': ['tfc:metal/ingot/bismuth_bronze', 'tfc:metal/ingot/black_bronze'],
    'c:storage_blocks/bronze': ['tfc:metal/block/bismuth_bronze', 'tfc:metal/block/black_bronze'],
    'c:ingots/steel': ['tfc:metal/ingot/black_steel', 'tfc:metal/ingot/blue_steel', 'tfc:metal/ingot/red_steel',
      'tfc:metal/ingot/high_carbon_steel', 'tfc:metal/ingot/high_carbon_black_steel',
      'tfc:metal/ingot/high_carbon_blue_steel', 'tfc:metal/ingot/high_carbon_red_steel'],
    'c:storage_blocks/steel': ['tfc:metal/block/black_steel', 'tfc:metal/block/blue_steel', 'tfc:metal/block/red_steel']
  }
  Object.keys(NOT_THE_METAL).forEach((tag) => event.remove(tag, NOT_THE_METAL[tag]))
  // TFC ore and gem powders are small fractions of an ingot or gem, not dusts (Doc 10 v3 8.1: "Verlierer").
  const TFC_POWDERS = ['native_copper', 'malachite', 'tetrahedrite', 'cassiterite', 'native_gold', 'native_silver',
    'hematite', 'magnetite', 'limonite', 'sphalerite', 'bismuthinite', 'garnierite', 'diamond', 'emerald']
  ;['copper', 'tin', 'gold', 'silver', 'iron', 'zinc', 'bismuth', 'nickel', 'diamond', 'emerald'].forEach((m) =>
    event.remove(`c:dusts/${m}`, TFC_POWDERS.map((p) => `tfc:powder/${p}`)))
  // TFC rod where TFC has one (Doc 10 v3 8.1); wrought iron is iron.
  event.add('c:rods/iron', 'tfc:metal/rod/wrought_iron')

  // Zinc nugget: Create's nugget is canonical and is the andesite alloy ingredient (Doc 10 v3 section 8.1).
  event.removeAll('c:nuggets/zinc')
  event.add('c:nuggets/zinc', 'create:zinc_nugget')

  // Canonical dusts of TFC-only metals (Doc 08 section 4.1).
  event.add('c:dusts/zinc', 'firmages:dust/zinc')
  event.add('c:dusts/bismuth', 'firmages:dust/bismuth')

  // Signature items, used by the Ultimate Singularity quest/recipe later.
  event.add('firmages:signature_items', ['firmages:hearthstone', 'firmages:sky_disc', 'firmages:steel_heart'])

  // Boss tokens: recipe slots take the tag (Doc 08 section 5.1). The Frontier Trial gateway (Frontier Sigil,
  // firmages/boss_fallback.js) pays out the Lich trophy itself, so the tag holds only the real drop.
  event.add('firmages:boss_token/age_2', 'twilightforest:lich_trophy')

  // Twilight Forest portal (Iron Age dimension, Doc 08 section 6). Its default activator #c:gems/diamond is age_4 in
  // dev/age_map.toml, so the portal could not open in the Iron Age. The Iron Age gem opens it instead: polished quartz
  // from the TFCreate quartz vein (tfcreate:quartz_{rock}, age_2). dev/validate_quests.py checks this line against
  // the Age of the chapter whose quest asks for the dimension (dev/decisions-while-away.md).
  event.removeAll('twilightforest:portal/activator')
  event.add('twilightforest:portal/activator', 'tfcreate:polished_quartz')
})
