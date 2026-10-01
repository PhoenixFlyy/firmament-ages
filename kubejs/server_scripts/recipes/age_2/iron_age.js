// Firmament Ages - Age 2: Iron Age recipes.
// Create brass tier, Crushing Wheels (TFC ore piece -> canonical dust, 1.5-1.9x), trains (Doc 10 v3 section 2.1).
// The brass-tier items themselves are item-locked until age_2 (dev/age_map.toml); brass comes from TFC
// (crucible) or the WoodenCog heated basin, create:mixing/brass_ingot is removed in global_removals.js.

ServerEvents.recipes((event) => {
  const { tfc } = event.recipes

  // ---- Crushing Wheels: TFC ore pieces -> canonical dust (Doc 08 section 4.1, matrix Doc 10 v3 6.1) ----
  // One dust = 100 mB when melted. Expected output = ore mB x YIELD, expressed as the chance of one dust
  // (TFC ore pieces: small 10, poor 15, normal 25, rich 35 mB; verified in tfc:heating/ore/*).
  // POC: YIELD 1.7 sits in the 1.5-1.9 band; tune against Theurgy spagyrics (2.5x) and the IE Crusher (2x).
  const YIELD = 1.7
  const GRADE_MB = { small: 10, poor: 15, normal: 25, rich: 35 }
  // Canonical dusts: Mekanism for Mekanism metals, IE for IE-only metals, KubeJS for TFC-only metals.
  // Mekanism/IE are item-locked until their Ages; these dusts are age_2 items (dev/age_map.toml [items]).
  const ORE_DUST = {
    native_copper: 'mekanism:dust_copper', malachite: 'mekanism:dust_copper', tetrahedrite: 'mekanism:dust_copper',
    cassiterite: 'mekanism:dust_tin',
    sphalerite: 'firmages:dust/zinc',
    bismuthinite: 'firmages:dust/bismuth',
    native_gold: 'mekanism:dust_gold',
    native_silver: 'immersiveengineering:dust_silver',
    hematite: 'mekanism:dust_iron', limonite: 'mekanism:dust_iron', magnetite: 'mekanism:dust_iron'
  }
  Object.keys(ORE_DUST).forEach((ore) => {
    Object.keys(GRADE_MB).forEach((grade) => {
      const chance = Math.min(1, (GRADE_MB[grade] * YIELD) / 100)
      event.custom({
        type: 'create:crushing',
        ingredients: [{ item: `tfc:ore/${grade}_${ore}` }],
        processing_time: 250,
        results: [{ id: ORE_DUST[ore], chance: Math.round(chance * 1000) / 1000 }]
      }).id(`firmages:crushing/${grade}_${ore}`)
    })
  })

  // ---- Dusts melt like their metal (temperatures from tfc:heating/metal/ingot/*, TFC 4.2.11) ----------
  // Item heat definitions: kubejs/data/firmages/tfc/item_heat/dust_*.json.
  const DUST_MELT = [
    ['mekanism:dust_copper', 'tfc:metal/copper', 1080],
    ['mekanism:dust_tin', 'tfc:metal/tin', 230],
    ['firmages:dust/zinc', 'tfc:metal/zinc', 420],
    ['firmages:dust/bismuth', 'tfc:metal/bismuth', 270],
    ['mekanism:dust_gold', 'tfc:metal/gold', 1060],
    ['immersiveengineering:dust_silver', 'tfc:metal/silver', 961],
    // Iron ores melt to cast iron in TFC; the dust follows the ore, not the ingot.
    ['mekanism:dust_iron', 'tfc:metal/cast_iron', 1535]
  ]
  DUST_MELT.forEach((row) => {
    const dust = row[0]
    tfc.heating(dust, row[2])
      .resultFluid(Fluid.of(row[1], 100))
      .id(`firmages:heating/${dust.replace(/[:/]/g, '_')}`)
  })

  // POC: Mekanism and IE ship their own smelting recipes dust -> ingot (vanilla furnace / their machines).
  // Vanilla ore smelting is off in TFC; check in EMI that no furnace recipe turns these dusts into
  // non-TFC ingots, otherwise: event.remove({ type: 'minecraft:smelting', input: '#c:dusts' })

  // ---- Signature item: Steel Heart (Doc 08 section 10.2) ---------------------------------------------
  // Steel sheet, Create Precision Mechanism, wrought iron double sheet, Lich trophy via boss-token tag.
  // Food is in no recipe on purpose (Survival keystone is a quest requirement instead).
  event.shaped('firmages:steel_heart', [
    ' S ',
    'WPW',
    ' T '
  ], {
    S: '#c:sheets/steel',
    W: '#c:double_sheets/wrought_iron',
    P: 'create:precision_mechanism',
    T: '#firmages:boss_token/age_2'
  }).id('firmages:crafting/steel_heart')

  // The only precision mechanism recipe (TFCreate's sequenced assembly on a gold sheet) lists create:crushed_raw_gold
  // as a 2 % scrap output. That item is in age_items/disabled, and the firmages-core recipe gate drops every recipe
  // with a disabled output, so the precision mechanism (and with it the Steel Heart) had no recipe at all.
  // The scrap entry is removed; the recipe stays as it is otherwise.
  const SCRAP_DISABLED = { 'tfcreate:sequenced_assembly/precision_mechanism': ['create:crushed_raw_gold'] }
  Object.keys(SCRAP_DISABLED).forEach((id) => {
    const found = []
    event.forEachRecipe({ id: id }, (r) => found.push(JSON.parse(String(r.json))))
    if (found.length !== 1) {
      console.error(`[firmages] ${id}: expected 1 recipe, found ${found.length}`)
      return
    }
    const json = found[0]
    json.results = json.results.filter((res) => SCRAP_DISABLED[id].indexOf(res.id) < 0)
    event.remove({ id: id })
    event.custom(json).id(id)
  })

  // TODO (Doc 08 section 9.4, Iron Age backfill): Sequenced Assembly "Smithing Die" for tool heads, rods and
  // armour parts, Create press on a heated bloom, heated compacting/welding over the TFCreate Fuel Heater.
  // Needs the WoodenCog/TFCreate recipe types from the PoC dump (/kubejs dump registry recipe_type).
})
