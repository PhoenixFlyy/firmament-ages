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

  // ---- Brass nugget (canonical Create nugget, TFC brass), the same way as zinc -------------------------------
  // Create's own nugget recipe from c:ingots/brass does not survive the load (leftovers run, 2026-10-01), so no recipe
  // made create:brass_nugget although about 30 recipes ask for it (Simulated's gyroscopic mechanism, lamps, rails).
  event.shapeless('9x create:brass_nugget', ['#c:ingots/brass']).id('firmages:crafting/brass_nugget')
  tfc.heating('create:brass_nugget', 930)
    .resultFluid(Fluid.of('tfc:metal/brass', 10))
    .id('firmages:heating/create_brass_nugget')

  // ---- Andesite alloy: zinc only, from TFC andesite (Doc 10 v3 section 7.3) ----------------------------
  event.remove({ id: 'create:crafting/materials/andesite_alloy' }) // iron nugget variant
  event.remove({ id: 'create:mixing/andesite_alloy' }) // iron nugget variant
  // WoodenCog knaps andesite alloy from andesite rocks alone (and deploys the same with a chisel): no zinc.
  event.remove({ id: 'woodencog:rock_knapping/andesite_alloy' })
  event.remove({ id: 'woodencog:rock_knapping/andesite_alloy_deploying' })
  // POC: which TFC andesite form (cobble, raw, loose rock) feels right; cobble = 4 loose rocks.
  event.replaceInput({ id: 'create:crafting/materials/andesite_alloy_from_zinc' }, 'minecraft:andesite', 'tfc:rock/cobble/andesite')
  event.replaceInput({ id: 'create:mixing/andesite_alloy_from_zinc' }, 'minecraft:andesite', 'tfc:rock/cobble/andesite')

  // ---- Andesite tier: iron parts become bronze parts ---------------------------------------------------
  // Everything Create crafts except the Iron Age items keeps its recipe but takes any bronze instead of iron.
  // POC: walk the Create andesite tier in EMI (press, mixer, fan, saw, drill, harvester, belts, water wheel).
  // Fixed ids: the Create and Create Connected recipes outside the Iron Age outputs (precision mechanism, electron
  // tube, deployer, arm, crafter, crushing wheel, steam engine and whistle, fluid parts, smart chute, speed controller,
  // sequenced gearshift, elevator pulley, trains, schematics, brass) that ask for iron in any form: plates, ingots,
  // blocks, nuggets, the iron trapdoor, the compass (wrought iron), slimeballs (no slime in a TFC world before the Iron
  // Age; TFC glue, as TFCreate does) and the vanilla barrel tag (only minecraft:barrel). Read from the original recipes
  // on 2026-10-01 and in the sweep of 2026-10-14 (Modpack-Planung/research-raw/fb3-create-tier-sweep.md);
  // create:pressing/iron_ingot is removed in global_removals.js. Plain ids are a map lookup; resolving the tier from
  // all 1,738 Create recipes cost about 120 ms of every reload. dev/poc_analyze.py check C-1a reports a Create,
  // Create Connected or TFCreate recipe of an early-Age item that still asks for iron.
  const andesiteTier = [
    'create:crafting/kinetics/empty_blaze_burner', 'create:crafting/kinetics/item_vault',
    'create:crafting/kinetics/mechanical_drill', 'create:crafting/kinetics/mechanical_press',
    'create:crafting/kinetics/mechanical_saw', 'create:crafting/kinetics/metal_bracket',
    'create:crafting/kinetics/metal_girder', 'create:crafting/kinetics/propeller', 'create:crafting/kinetics/super_glue',
    'create:crafting/kinetics/whisk', 'create:crafting/logistics/packager', 'create:crafting/logistics/redstone_contact',
    'create:industrial_iron_block_from_ingots_iron_stonecutting', 'create:weathered_iron_block_from_ingots_iron_stonecutting',
    'create:crafting/kinetics/mechanical_harvester', 'create:crafting/kinetics/mechanical_plough',
    'create:crafting/curiosities/minecart_coupling', 'create:crafting/kinetics/filter',
    'create:crafting/kinetics/encased_chain_drive', 'create:crafting/logistics/item_hatch',
    'create:crafting/kinetics/sticker', 'create:crafting/kinetics/sticky_mechanical_piston',
    'create:crafting/kinetics/speedometer'
  ].map((id) => ({ id: id }))
  // Create Connected's freewheel and centrifugal clutch and item silo carry a create_connected:feature_enabled
  // condition, and replaceInput left them unchanged (0.8.1 dump: still create:iron_sheet, unified to #c:plates/iron).
  // Their bronze versions are datapack overrides under the same ids, condition kept:
  // kubejs/data/create_connected/recipe/crafting/kinetics/{freewheel_clutch,centrifugal_clutch,item_silo}.json
  event.replaceInput(andesiteTier, '#c:plates/iron', '#firmages:plates/any_bronze')
  event.replaceInput(andesiteTier, '#c:ingots/iron', '#firmages:ingots/any_bronze')
  event.replaceInput(andesiteTier, '#c:storage_blocks/iron', '#firmages:storage_blocks/any_bronze')
  event.replaceInput(andesiteTier, '#c:nuggets/iron', '#firmages:rods/any_bronze')
  event.replaceInput(andesiteTier, 'minecraft:iron_trapdoor', '#firmages:trapdoors/any_bronze')
  event.replaceInput(andesiteTier, 'minecraft:compass', '#firmages:plates/any_bronze')
  event.replaceInput(andesiteTier, '#c:slimeballs', 'tfc:glue')
  event.replaceInput(andesiteTier, '#c:barrels/wooden', '#tfc:barrels')

  // TFCreate replaces seven Create recipes with tfcreate: ids, and mod:tfcreate is grid-locked until age_2
  // (age_2.toml), so their items had no Bronze Age recipe. The same recipes under firmages: ids (the firmages
  // namespace opens at age_0, the item lock keeps the outputs at age_1), iron parts as bronze; tags only, so Almost
  // Unified leaves them alone. Then the iron twins of items the andesite tier makes from bronze, which EMI would show
  // as a second, locked iron recipe; tfcreate:drill_head and saw_blade lose their only use ([derived_hidden]).
  ;['chute', 'rope_pulley', 'goggles', 'copper_valve_handle', 'brown_toolbox', 'desk_bell', 'weighted_ejector',
    'item_vault', 'metal_bracket', 'super_glue', 'filter', 'item_hatch', 'sticker', 'sticky_mechanical_piston',
    'mechanical_press', 'mechanical_drill', 'mechanical_saw', 'encased_chain_drive', 'encased_chain_drive_from_steel',
    'redstone_contact', 'anvil/propeller', 'anvil/whisk', 'anvil/drill_head', 'anvil/saw_blade'
  ].forEach((x) => event.remove({ id: `tfcreate:${x}` }))
  event.shaped('4x create:chute', ['A', 'I', 'A'], { A: '#firmages:plates/any_bronze', I: '#firmages:ingots/any_bronze' })
    .id('firmages:crafting/create/chute')
  event.shaped('create:rope_pulley', ['B', 'C', 'I'], {
    B: 'create:andesite_casing', C: '#minecraft:wool', I: '#firmages:plates/any_bronze'
  }).id('firmages:crafting/create/rope_pulley')
  event.shaped('create:goggles', [' S ', 'GPG'], { G: '#c:glass_blocks', P: '#c:plates/gold', S: '#c:strings' })
    .id('firmages:crafting/create/goggles')
  event.shaped('create:copper_valve_handle', ['CCC', ' S '], { C: '#c:plates/copper', S: '#c:ingots/andesite_alloy' })
    .id('firmages:crafting/create/copper_valve_handle')
  event.shaped('create:brown_toolbox', [' C ', 'SWS', ' L '], {
    C: 'create:cogwheel', L: '#c:leathers', S: '#c:plates/gold', W: '#c:chests/wooden'
  }).id('firmages:crafting/create/brown_toolbox')
  event.shapeless('create:desk_bell', ['create:andesite_casing', '#c:plates/gold']).id('firmages:crafting/create/desk_bell')
  event.shaped('create:weighted_ejector', ['A', 'D', 'I'], { A: '#c:plates/gold', D: 'create:depot', I: 'create:cogwheel' })
    .id('firmages:crafting/create/weighted_ejector')

  // ---- Other early mods: iron or an Iron Age vanilla part in a tool of an earlier Age ----------------------
  // Totem carving is Bronze Age flair (Doc 08); the totem torch, the liquid trash can and the framing saw are Stone
  // Age; the Sophisticated upgrade base (11 upgrades: pickup, filters, stack starter and downgrades, tank, pump,
  // battery) and the Astikor seed drill (next to the plow and the carts) are Bronze Age.
  event.replaceInput({ id: 'totemic:totem_whittling_knife' }, '#c:ingots/iron', '#firmages:ingots/any_bronze')
  event.replaceInput({ id: 'totemic:totem_torch' }, 'minecraft:torch', 'tfc:torch')
  event.replaceInput({ id: 'trashcans:liquid_trash_can' }, '#c:buckets/empty', 'tfc:wooden_bucket')
  event.replaceInput({ id: 'framedblocks:framing_saw' }, '#c:ingots/iron', '#c:ingots/copper')
  event.replaceInput({ id: 'sophisticatedbackpacks:upgrade_base' }, '#c:ingots/iron', '#firmages:ingots/any_bronze')
  const SEED_DRILL_WOODS = ['acacia', 'araucaria', 'ash', 'aspen', 'baobab', 'beech', 'birch', 'blackwood', 'chestnut',
    'cypress', 'douglas_fir', 'eucalyptus', 'fig', 'ginkgo', 'hevea', 'hickory', 'ipe', 'ironwood', 'kapok', 'mahoe',
    'mahogany', 'mangrove', 'maple', 'oak', 'palm', 'pine', 'rosewood', 'sequoia', 'spruce', 'sycamore', 'teak',
    'tualang', 'white_cedar', 'willow']
  event.replaceInput(SEED_DRILL_WOODS.map((w) => ({ id: `tfcastikorcarts:crafting/seed_drill/${w}` })),
    'minecraft:hopper', '#firmages:plates/any_bronze')

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
  // Rhino throws "redeclaration of var" for const/let declared directly inside a try block.
  const querns = []
  try {
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

  // ---- Deco in TFC woods: Bronze Age, like the rest of the furniture -----------------------------------
  // Every Compat builds Macaw's classic and cottage doors in TFC and AFC woods from the Classic and Cottage Door
  // Print, which Macaw's draws from a vanilla oak or spruce door; Handcrafted's counters (and Every Compat's) take
  // calcite (Arcane Age). A TFC or AFC wooden door or trapdoor now makes the print (tags in tags/unification.js), any
  // smooth TFC rock the counter top.
  // Ids and JSON from the all-Ages fa_dump_full of 2026-10-14; dev/predict_dump.py mirrors this section.
  // POC: Every Compat's recipes come from Moonlight's runtime pack; check one TFC classic door and counter in EMI.
  event.replaceInput({ id: 'mcwdoors:print_oak' }, 'minecraft:oak_door', '#firmages:wooden_doors/tfc')
  event.replaceInput({ id: 'mcwdoors:print_spruce' }, 'minecraft:spruce_door', '#firmages:wooden_trapdoors/tfc')
  const counters = []
  event.forEachRecipe({ id: /^(handcrafted:[a-z_]+_counter|everycomp:hc\/.+_counter)$/ }, (r) => {
    let j = JSON.parse(String(r.json))
    let hit = false
    Object.keys(j.key || {}).forEach((k) => {
      let v = j.key[k]
      // Every Compat wraps every key in Moonlight's wood swap; calcite has no wood, so the wrapper goes.
      let inner = v.type === 'moonlight:block_type_swap' ? v.ingredient : v
      if (inner && inner.item === 'minecraft:calcite') {
        j.key[k] = { tag: 'c:stones/smooth' }
        hit = true
      }
    })
    if (hit) counters.push({ id: String(r.getId()), json: j })
  })
  counters.forEach((c) => {
    event.remove({ id: c.id })
    event.custom(c.json).id(c.id)
  })
  console.info(`[firmages] deco: ${counters.length} counters take smooth rock`)
})
