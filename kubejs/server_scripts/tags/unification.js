// Firmament Ages - tag unification up to the Iron Age (Doc 10 v3 section 8).
// TFC 4.2.11 tags its metals as c:ingots/<metal>, c:sheets/<metal>, c:double_ingots/<metal> and
// c:ingots/wrought_iron (verified in the TFC data tree). Create 6.0.10 asks for c:plates/iron, c:ingots/iron,
// c:storage_blocks/iron|copper and c:plates/gold (verified in its recipes). These additions bridge the two.
// POC: Almost Unified (unify.json tagOwnerships, Doc 10 v3 8.2) may already do the same; then drop the
// duplicates here. Check with /kubejs list_tag c:plates/iron in the PoC.

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

  // Zinc nugget: Create's nugget is canonical and is the andesite alloy ingredient (Doc 10 v3 section 8.1).
  event.removeAll('c:nuggets/zinc')
  event.add('c:nuggets/zinc', 'create:zinc_nugget')

  // Canonical dusts of TFC-only metals (Doc 08 section 4.1).
  event.add('c:dusts/zinc', 'firmages:dust/zinc')
  event.add('c:dusts/bismuth', 'firmages:dust/bismuth')

  // Signature items, used by the Ultimate Singularity quest/recipe later.
  event.add('firmages:signature_items', ['firmages:hearthstone', 'firmages:sky_disc', 'firmages:steel_heart'])

  // Boss tokens: recipe slots take the real drop OR the fallback token (Doc 08 section 5.1).
  // POC: Twilight Forest 4.8 trophy id for the Lich; the "Frontier Sigil" fallback token is not registered yet.
  event.add('firmages:boss_token/age_2', 'twilightforest:lich_trophy')
})
