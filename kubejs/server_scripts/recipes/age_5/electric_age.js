// Firmament Ages - Age 5: Electric Age (Doc 08 sections 2.2, 7.2 and 10.2; Doc 10 v3 sections 6.1, 6.2, 7).
// Canonical stations of the Age: IE Arc Furnace (melting, alloys, steel, black steel), IE Sawmill, IE Mixer and
// Bottling Machine, IE Assembler (grid autocrafting), IE MV/HV power, the IE Diesel Generator (the only FE
// generator), Immersive Petroleum (pumpjack, distillation tower: diesel), the IE Excavator on the TFC + IE mineral
// mixes. The Alloy Kiln retires with this Age (helper stage tool_ie_alloy_kiln, stages/grants.js).
// Recipe formats as the /fa_dump_full export lists IE's own arc furnace recipes (2026-09-30).
// Goal: the Humming Core (Attuned Circuit, IE HV capacitor, aluminium plates, red steel sheets, a Nether Star).

ServerEvents.recipes((event) => {
  // The Arc Furnace is an Electric Age station (recipes/station_ages.js): the gate keeps these recipes out until age_5,
  // so the arc furnace steels never show before the Electric Age.
  const arc = (json, id) => event.custom(json).id(id)
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

  // ======================================================================================== arc furnace: dust -> ingot
  // Harmony plan fix 14 (2026-10-16): IE melts a dust for 51,200 FE, eight times a crusher pass, while a Create fan
  // smelts it for nothing. At the metal table's "arc_furnace.dust_energy" (6,400 FE) the Arc Furnace is the Electric
  // Age's smelter. It is also the iron route: iron dust gives wrought iron only here and in the Energized Smelter
  // (Information Age); a furnace or a fan gives cast iron (recipes/unify_iron.js, the One Iron rule).
  const DUST_ENERGY = Number(global.FA_METALS.table.arc_furnace.dust_energy)
  ;['aluminum', 'brass', 'bronze', 'cobalt', 'constantan', 'copper', 'electrum', 'gold', 'iron', 'lead', 'nickel', 'osmium',
    'platinum', 'silver', 'steel', 'tin', 'tungsten', 'uranium', 'zinc'].forEach((m) => {
    const id = `immersiveengineering:arcfurnace/dust_${m}`
    const found = []
    event.forEachRecipe({ id: id }, (r) => found.push(String(r.json)))
    if (found.length !== 1) return  // an optional metal (cobalt, platinum, tungsten) without its dust tag
    const json = JSON.parse(found[0])
    json.energy = DUST_ENERGY
    event.remove({ id: id })
    event.custom(json).id(id)
  })

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
  // Every recipe of the Diesel Generators machine types (fermenting, casting, compression molding, distillation,
  // hammering, wire cutting) has a createdieselgenerators id (all 47 on 2026-10-01), so the pass above takes them too;
  // a type filter would be one more scan of all recipes. poc_analyze.py C-5a reports a Diesel Generators output left.
  // The 18 Create mixing recipes of the mod (cement, asphalt, biodiesel) are in a format KubeJS cannot read; data
  // overrides remove them before KubeJS reads them (dev/gen_recipe_overrides.py, reason pack-removes).

  // ======================================================================================== the empty bucket
  // A TFC world has no recipe for the vanilla bucket (recipes that ask for one take the TFC wooden bucket,
  // tfc_station_inputs.js); its only source was Twilight Forest loot until that mod left (dev/decisions-while-away.md,
  // 2026-10-14). The Oil strand fills buckets with crude oil, lubricant and diesel, so the bucket is an Electric Age
  // item (dev/age_map.toml [items]), pressed from steel sheets like the vanilla shape.
  event.shaped('minecraft:bucket', ['S S', ' S '], { S: '#c:sheets/steel' }).id('firmages:crafting/bucket')

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

  // ======================================================================================== Attuned Circuit
  // Two grid recipes, so the IE Assembler automates both (dev/decisions-while-away.md, 2026-10-15: tech is required,
  // magic a bonus):
  // - tech: an IE circuit board tuned by a quartz crystal, with two advanced electronic components and electrum wire;
  // - magic (Doc 08 section 7.2, the magic tail): the circuit board with a spirit attuned crystal, cheaper.
  event.shaped('firmages:attuned_circuit', [' Q ', 'ECE', ' W '], {
    Q: '#c:gems/quartz',
    E: 'immersiveengineering:component_electronic_adv',
    C: 'immersiveengineering:circuit_board',
    W: '#c:wires/electrum'
  }).id('firmages:crafting/attuned_circuit_tuned')
  event.shapeless('firmages:attuned_circuit', ['immersiveengineering:circuit_board', 'occultism:spirit_attuned_crystal'])
    .id('firmages:crafting/attuned_circuit')

  // ======================================================================================== the Nether Star (age_5 token)
  // dev/decisions-while-away.md, 2026-10-14: no boss is mandatory. Tech route: the Arc Furnace fuses one wither
  // skeleton skull with soul sand, a gold block and electrum plates into a star, at a high HV cost. One skull, not the
  // three of a Wither summon: the Draconic Evolution cores of the later Ages ask for dozens of stars. Magic route: the
  // Afrit pentacle (the Afrit is an Electric Age spirit). The Wither stays an optional shortcut; Theurgy reformation
  // (Information Age) multiplies stars for teams that go the alchemy way.
  arc({
    type: 'immersiveengineering:arc_furnace',
    input: { item: 'minecraft:wither_skeleton_skull' },
    additives: [
      { basePredicate: { item: 'minecraft:soul_sand' }, count: 8 },
      { tag: 'c:storage_blocks/gold' },
      { basePredicate: { tag: 'c:plates/electrum' }, count: 2 }
    ],
    results: [{ id: 'minecraft:nether_star', count: 1 }],
    time: 1200,
    energy: 3276800
  }, 'firmages:arc_furnace/nether_star')
  event.custom({
    type: 'occultism:ritual',
    ritual_type: 'occultism:craft',
    pentacle_id: 'occultism:craft_afrit',
    activation_item: { item: 'occultism:book_of_binding_bound_afrit' },
    duration: 300,
    ritual_dummy: { id: 'occultism:ritual_dummy/custom_ritual_craft', count: 1 },
    ingredients: [
      { item: 'minecraft:wither_skeleton_skull' }, { item: 'minecraft:wither_skeleton_skull' },
      { item: 'minecraft:wither_skeleton_skull' }, { item: 'minecraft:soul_sand' }, { item: 'minecraft:soul_sand' },
      { item: 'firmages:attuned_circuit' }, { item: 'occultism:afrit_essence' }, { tag: 'c:storage_blocks/gold' }
    ],
    result: { id: 'minecraft:nether_star', count: 1 }
  }).id('firmages:ritual/nether_star')

  // ======================================================================================== goal: Humming Core
  // Fused in the IE Arc Furnace (the Electric Age HV station): the HV capacitor is the main input, the four additive
  // slots take the Attuned Circuit, aluminium plates, red steel sheets and the age_5 token (a Nether Star).
  // The diesel jerrycan of Doc 08 10.2 is left out: the arc furnace takes no fluids; diesel is the Oil strand's
  // keystone instead (decision log 2026-10-01).
  arc({
    type: 'immersiveengineering:arc_furnace',
    input: { item: 'immersiveengineering:capacitor_hv' },
    additives: [
      { item: 'firmages:attuned_circuit' },
      { basePredicate: { tag: 'c:plates/aluminum' }, count: 4 },
      { basePredicate: { tag: 'c:sheets/red_steel' }, count: 2 },
      { tag: 'firmages:age_token/age_5' }
    ],
    results: [{ id: 'firmages:humming_core', count: 1 }],
    time: 800,
    energy: 1638400
  }, 'firmages:arc_furnace/humming_core')
})
