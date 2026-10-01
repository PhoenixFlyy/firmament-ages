// Firmament Ages - Age 5: Electric Age (Doc 08 sections 2.2, 7.2 and 10.2; Doc 10 v3 sections 6.1, 6.2, 7).
// Canonical stations of the Age: IE Arc Furnace (melting, alloys, steel, black steel), IE Sawmill, IE Mixer and
// Bottling Machine, IE Assembler (grid autocrafting), IE MV/HV power, the IE Diesel Generator (the only FE
// generator), Immersive Petroleum (pumpjack, distillation tower: diesel), the IE Excavator on the TFC + IE mineral
// mixes. The Alloy Kiln retires with this Age (helper stage tool_ie_alloy_kiln, stages/grants.js).
// Recipe formats as the /fa_dump_full export lists IE's own arc furnace recipes (2026-09-30).
// Goal: the Humming Core (Attuned Circuit, IE HV capacitor, aluminium plates, red steel sheets, Wither drop).

ServerEvents.recipes((event) => {
  // Pack recipes in the Arc Furnace load only with its Age (recipes/station_ages.js cannot remove recipes added in the
  // same event), so the arc furnace steels never show before the Electric Age.
  const arc = (json, id) => { if (FirmAges.isUnlocked('age_5')) event.custom(json).id(id) }
  const sized = (tagId, n) => (n > 1 ? { basePredicate: { tag: tagId }, count: n } : { tag: tagId })

  // ======================================================================================== arc furnace: TFC steels
  // TFC + IE 2.1.3 ships weak steel from 2 BLACK steel + black bronze + nickel (a typo: TFC weak steel is 50-70 %
  // steel, 15-25 % nickel, 15-25 % black bronze). Rebuilt with steel, same ratio 2:1:1 -> 4.
  event.remove({ id: 'tfc_ie_addon:arcfurnace/weak_steel' })
  arc({
    type: 'immersiveengineering:arc_furnace',
    input: sized('c:ingots/steel', 2),
    additives: [{ tag: 'c:ingots/black_bronze' }, { tag: 'c:ingots/nickel' }],
    results: [{ id: 'tfc:metal/ingot/weak_steel', count: 4 }],
    time: 100,
    energy: 51200
  }, 'firmages:arc_furnace/weak_steel')
  // Black steel (Doc 10 v3 matrix 6.1, "IE Arc Furnace: Schwarzstahl (Pack-Rezept)"): TFC welds weak steel and pig
  // iron into high carbon black steel and hammers it into black steel; the arc furnace does both, with slag.
  arc({
    type: 'immersiveengineering:arc_furnace',
    input: { item: 'tfc:metal/ingot/weak_steel' },
    additives: [{ item: 'tfc:metal/ingot/pig_iron' }],
    results: [{ id: 'tfc:metal/ingot/black_steel', count: 1 }],
    slag: { tag: 'c:slag' },
    time: 400,
    energy: 204800
  }, 'firmages:arc_furnace/black_steel')
  // Weak red steel, the alloy step only (TFC: black steel 50-55 %, steel 20-25 %, brass and rose gold 10-15 % each),
  // in the same 5:2:1:1 -> 9 shape as TFC + IE's weak blue steel. Red steel itself stays a hand weld in this Age; the
  // Mekanism Metallurgic Infuser takes over that step in the Information Age (recipes/age_6).
  arc({
    type: 'immersiveengineering:arc_furnace',
    input: sized('c:ingots/black_steel', 5),
    additives: [sized('c:ingots/steel', 2), { tag: 'c:ingots/brass' }, { tag: 'c:ingots/rose_gold' }],
    results: [{ id: 'tfc:metal/ingot/weak_red_steel', count: 9 }],
    time: 100,
    energy: 51200
  }, 'firmages:arc_furnace/weak_red_steel')

  // ======================================================================================== disabled parts of the Age
  // Immersive Petroleum: the gas generator is a second FE generator next to the IE Diesel Generator (Doc 10 v3 7.2).
  // It is disabled, so the firmages-core gate drops its recipe (no output filter: a full scan per reload).
  // Create: Diesel Generators: only the engines and their upgrades stay; they run on IP diesel through #c:diesel.
  // Its pumpjack, oil, distillation, fermenting, cement, asphalt and tools are a second oil economy (Doc 10 v3 7.2).
  const CDG_KEEP = ['diesel_engine', 'large_diesel_engine', 'huge_diesel_engine', 'engine_piston', 'engine_silencer',
    'engine_turbocharger'].map((p) => `createdieselgenerators:crafting/${p}`)
  const cdg = []
  event.forEachRecipe({ mod: 'createdieselgenerators' }, (r) => {
    const id = String(r.getId())
    if (CDG_KEEP.indexOf(id) < 0) cdg.push(id)
  })
  cdg.forEach((id) => event.remove({ id: id }))
  // One OR filter: a type filter per call is one scan of all recipes.
  event.remove(['basin_fermenting', 'bulk_fermenting', 'casting', 'compression_molding', 'distillation', 'hammering', 'wire_cutting']
    .map((t) => ({ type: `createdieselgenerators:${t}` })))

  // ======================================================================================== magic tail: Afrit
  // Doc 08 section 7.2: the Afrit binding book needs an IE HV coil block. One grid recipe each (the IE Assembler
  // automates them); Occult Engineering's mixer copies of the same books go (one station per item).
  ;['occultism:crafting/book_of_binding_afrit', 'occultism:crafting/book_of_binding_afrit_from_empty',
    'occultengineering:mixing/book_of_binding_afrit', 'occultengineering:mixing/book_of_binding_afrit_from_empty']
    .forEach((id) => event.remove({ id: id }))
  event.shaped('occultism:book_of_binding_afrit', ['cpf', 'pbp', ' H '], {
    b: 'occultism:taboo_book', c: 'occultism:purified_ink', f: 'occultism:awakened_feather', p: '#c:dyes/yellow',
    H: 'immersiveengineering:coil_hv'
  }).id('occultism:crafting/book_of_binding_afrit') // same id: the Occultism guide book shows it
  event.shaped('occultism:book_of_binding_afrit', [' p ', 'pbp', ' H '], {
    b: 'occultism:book_of_binding_empty', p: '#c:dyes/yellow', H: 'immersiveengineering:coil_hv'
  }).id('occultism:crafting/book_of_binding_afrit_from_empty')

  // ======================================================================================== magic tail: Attuned Circuit
  // IE circuit board with a spirit attuned crystal (Doc 08 section 7.2). A grid recipe, so the IE Assembler automates it.
  event.shapeless('firmages:attuned_circuit', ['immersiveengineering:circuit_board', 'occultism:spirit_attuned_crystal'])
    .id('firmages:crafting/attuned_circuit')

  // ======================================================================================== goal: Humming Core
  // Fused in the IE Arc Furnace (the Electric Age HV station): the HV capacitor is the main input, the four additive
  // slots take the Attuned Circuit, aluminium plates, red steel sheets and the age_5 boss token (Nether Star).
  // The diesel jerrycan of Doc 08 10.2 is left out: the arc furnace takes no fluids; diesel is the Oil strand's
  // keystone instead (decision log 2026-10-01).
  arc({
    type: 'immersiveengineering:arc_furnace',
    input: { item: 'immersiveengineering:capacitor_hv' },
    additives: [
      { item: 'firmages:attuned_circuit' },
      { basePredicate: { tag: 'c:plates/aluminum' }, count: 4 },
      { basePredicate: { tag: 'c:sheets/red_steel' }, count: 2 },
      { tag: 'firmages:boss_token/age_5' }
    ],
    results: [{ id: 'firmages:humming_core', count: 1 }],
    time: 800,
    energy: 1638400
  }, 'firmages:arc_furnace/humming_core')
})
