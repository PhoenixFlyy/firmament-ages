// Firmament Ages - Age 1: Bronze Age recipes.
// Decision s3 A (round 3): Create enters LATE in the Bronze Age with the andesite tier; crushing, brass and
// trains are Iron Age (Doc 10 v3 section 2.1). Create recipes have no stage check, so the Bronze Age gate is
// the material: andesite alloy needs TFC zinc (sphalerite, visible from age_1) and andesite-tier parts need
// TFC bronze sheets from the bronze anvil. The brass tier is item-locked in age_2.toml.
// Create JSON is written with event.custom (KubeJS Create 2101.3.1 is a beta; format checked against
// Create 6.0.10 generated recipes: "ingredients" + "results":[{"id",...,"chance"}]).

ServerEvents.recipes((event) => {
  const { tfc } = event.recipes

  // ---- Zinc nugget (canonical Create nugget, TFC zinc) -----------------------------------------------
  // POC: 1 ingot (100 mB) -> 9 nuggets (9 x 10 mB) loses 10 mB on purpose; no 9 -> 1 way back (Doc 10 v3 8.1).
  event.shapeless('9x create:zinc_nugget', ['#c:ingots/zinc']).id('firmages:crafting/zinc_nugget')
  tfc.heating('create:zinc_nugget', 420)
    .resultFluid(Fluid.of('tfc:metal/zinc', 10))
    .id('firmages:heating/create_zinc_nugget')

  // ---- Andesite alloy: zinc only, from TFC andesite (Doc 10 v3 section 7.3) ----------------------------
  event.remove({ id: 'create:crafting/materials/andesite_alloy' }) // iron nugget variant
  event.remove({ id: 'create:mixing/andesite_alloy' }) // iron nugget variant
  // POC: which TFC andesite form (cobble, raw, loose rock) feels right; cobble = 4 loose rocks.
  event.replaceInput({ id: 'create:crafting/materials/andesite_alloy_from_zinc' }, 'minecraft:andesite', 'tfc:rock/cobble/andesite')
  event.replaceInput({ id: 'create:mixing/andesite_alloy_from_zinc' }, 'minecraft:andesite', 'tfc:rock/cobble/andesite')

  // ---- Andesite tier: iron parts become bronze parts ---------------------------------------------------
  // Everything Create crafts except the Iron Age items keeps its recipe but takes any bronze instead of iron.
  // POC: walk the Create andesite tier in EMI (press, mixer, fan, saw, drill, harvester, belts, water wheel).
  const IRON_AGE_OUTPUTS = [
    'create:precision_mechanism', 'create:electron_tube', 'create:deployer', 'create:mechanical_arm',
    'create:mechanical_crafter', 'create:crushing_wheel', 'create:steam_engine', 'create:steam_whistle',
    'create:spout', 'create:fluid_pipe', 'create:mechanical_pump', 'create:fluid_tank', 'create:fluid_valve',
    'create:smart_fluid_pipe', 'create:smart_chute', 'create:rotation_speed_controller',
    'create:sequenced_gearshift', 'create:elevator_pulley', 'create:track', 'create:track_station',
    'create:track_signal', 'create:track_observer', 'create:controls', 'create:railway_casing',
    'create:schematicannon', 'create:schematic_table'
  ]
  const andesiteTier = { mod: 'create', not: [{ output: IRON_AGE_OUTPUTS }, { output: /^create:brass_/ }] }
  event.replaceInput(andesiteTier, '#c:plates/iron', '#firmages:plates/any_bronze')
  event.replaceInput(andesiteTier, '#c:ingots/iron', '#firmages:ingots/any_bronze')
  event.replaceInput(andesiteTier, '#c:storage_blocks/iron', '#firmages:storage_blocks/any_bronze')

  // ---- Create press: TFC double ingot -> TFC sheet (never 1 ingot -> 1 sheet, Doc 10 v3 section 7.3) ---
  // Metals gate themselves: a steel sheet needs steel, which only exists from the Iron Age.
  // POC: TFCreate Compat may ship the same recipes; remove duplicates after /kubejs dump.
  const SHEET_METALS = ['copper', 'bronze', 'bismuth_bronze', 'black_bronze', 'brass', 'rose_gold', 'sterling_silver',
    'tin', 'zinc', 'bismuth', 'gold', 'silver', 'nickel', 'wrought_iron', 'cast_iron', 'steel', 'black_steel',
    'red_steel', 'blue_steel']
  SHEET_METALS.forEach((m) => {
    event.custom({
      type: 'create:pressing',
      ingredients: [{ tag: `c:double_ingots/${m}` }],
      results: [{ id: `tfc:metal/sheet/${m}` }]
    }).id(`firmages:pressing/tfc_sheet_${m}`)
  })

  // ---- Create washing = automated gold panning (Doc 08 section 9.4, late Bronze Age) ------------------
  // POC: chances are placeholders; TFC's own panning table is data (tfc:panning), compare in the PoC.
  // Deposits of ores that are not yet visible are disguised as gravel (age_N.toml), so they are not
  // recognisable before their Age; tin deposits still wash out tin (known leak, Doc 08 section 3.4).
  const ROCKS = ['andesite', 'basalt', 'chalk', 'chert', 'claystone', 'conglomerate', 'dacite', 'diorite', 'dolomite',
    'gabbro', 'gneiss', 'granite', 'limestone', 'marble', 'phyllite', 'quartzite', 'rhyolite', 'schist', 'shale',
    'slate', 'tuff']
  ;['cassiterite', 'native_copper', 'native_gold', 'native_silver'].forEach((ore) => {
    ROCKS.forEach((rock) => {
      event.custom({
        type: 'create:splashing',
        ingredients: [{ item: `tfc:deposit/${ore}/${rock}` }],
        results: [
          { id: `tfc:ore/small_${ore}`, chance: 0.5 },
          { id: `tfc:ore/small_${ore}`, chance: 0.25 },
          { id: `tfc:rock/loose/${rock}`, chance: 0.5 }
        ]
      }).id(`firmages:splashing/deposit_${ore}_${rock}`)
    })
  })

  // ---- Millstone = mechanised TFC quern (Doc 08 section 9.4) -----------------------------------------
  // POC: copies every tfc:quern recipe whose result is a plain item stack. TFC results can be
  // ItemStackProviders with modifiers; those are skipped. Check the copied list in EMI.
  try {
    const querns = []
    event.forEachRecipe({ type: 'tfc:quern' }, (r) => {
      querns.push({ id: String(r.getId()), json: JSON.parse(String(r.json)) })
    })
    querns.forEach((q) => {
      const result = q.json.result
      if (!result || !result.id || result.modifiers || result.stack) return
      event.custom({
        type: 'create:milling',
        ingredients: [q.json.ingredient],
        processing_time: 100,
        results: [{ id: result.id, count: result.count || 1 }]
      }).id(`firmages:milling/from_${q.id.replace(/[:/]/g, '_')}`)
    })
  } catch (e) {
    console.error(`[firmages] quern -> millstone copy failed: ${e}`)
  }

  // ---- Signature item: Sky Disc (Doc 08 section 10.2) -------------------------------------------------
  // Bronze double sheet from the bronze anvil (TFC tag tfc:double_sheets/any_bronze) + gold sheets.
  event.shaped('firmages:sky_disc', [
    ' G ',
    'GBG',
    ' G '
  ], {
    B: '#tfc:double_sheets/any_bronze',
    G: '#c:sheets/gold'
  }).id('firmages:crafting/sky_disc')
})
