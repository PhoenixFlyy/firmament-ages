// Firmament Ages - recipes that ask by item id for an item no recipe makes (leftovers run, 2026-10-01).
// create:iron_sheet, copper_sheet, golden_sheet and brass_sheet are in age_items/disabled (TFC sheets are the plates,
// tags/unification.js), and a TFC world makes no vanilla copper or gold ingot, iron block or lapis, so about 100
// recipes of Create addons, AE2 and its addons could never be crafted. Each such input becomes the common tag of the
// same material (DEAD below; the TFC item is in every one of them). A vanilla barrel in these recipes (the createdeco
// shipping containers, the Create Dragons Plus fragile fluid tank) becomes a TFC barrel here too: a recipe re-added by
// this script is out of reach of the replaceInput in tfc_station_inputs.js.
// Ids from the all-Ages /fa_dump_full of 2026-10-01; plain ids keep the reload cost at a map lookup per recipe.
// The four sequenced assemblies whose chance outputs are disabled sheets are rewritten in recipes/byproducts.js, which
// applies the same sheet map. dev/poc_analyze.py check C-Di reads DEAD and fails if any recipe still names one of them.

ServerEvents.recipes((event) => {
  // BEGIN DEAD
  const DEAD = {
    'create:iron_sheet': 'c:plates/iron', 'create:copper_sheet': 'c:plates/copper',
    'create:golden_sheet': 'c:plates/gold', 'create:brass_sheet': 'c:plates/brass',
    'minecraft:copper_ingot': 'c:ingots/copper', 'minecraft:gold_ingot': 'c:ingots/gold',
    'minecraft:iron_block': 'c:storage_blocks/iron', 'minecraft:lapis_lazuli': 'c:gems/lapis'
  }
  // END DEAD
  const BARREL = { 'minecraft:barrel': 'tfc:barrels' }
  const COLORS = ['black', 'blue', 'brown', 'cyan', 'gray', 'green', 'light_blue', 'light_gray', 'lime', 'magenta', 'orange',
    'pink', 'purple', 'red', 'white', 'yellow']
  const DECALS = ['creeper', 'cross', 'down', 'down_left', 'down_right', 'electrical', 'fire', 'fire_diamond', 'flow', 'fluid',
    'ice', 'left', 'no_entry', 'radioactive', 'right', 'skull', 'top_left', 'top_right', 'up', 'warning']
  const IDS = [
    // Create sheets
    'bellsandwhistles:metro/metro_sheet', 'bellsandwhistles:pilots/brass_pilot', 'bellsandwhistles:pilots/copper_pilot',
    'bellsandwhistles:pilots/metal_pilot',
    'bits_n_bobs:crafting/cogwheel_chain_carriage', 'bits_n_bobs:crafting/large_nixie_tube', 'bits_n_bobs:crafting/nixie_board',
    'create_connected:crafting/kinetics/brass_chute', 'create_connected:crafting/kinetics/centrifugal_clutch',
    'create_connected:crafting/kinetics/fluid_vessel', 'create_connected:crafting/kinetics/freewheel_clutch',
    'create_connected:crafting/kinetics/item_silo', 'create_connected:crafting/kinetics/kinetic_battery',
    'create_connected:crafting/kinetics/overstress_clutch', 'create_connected:crafting/kinetics/sequenced_pulse_generator',
    'create_dragons_plus:crafting/fragile_fluid_tank',
    'create_enchantment_industry:crafting/affix_augmentor', 'create_enchantment_industry:crafting/infuser',
    'create_enchantment_industry:sequenced_assembly/brass_affix_template',
    'create_enchantment_industry:sequenced_assembly/crystal_affix_template',
    'create_hypertube:hypertube', 'create_hypertube:hypertube_funnel', 'create_hypertube:hypertube_junction',
    'create_hypertube:sequenced_assembly/tube_scanner',
    'create_mobile_packages:mobile_packager',
    'create_pattern_schematics:pattern_schematic', 'create_pattern_schematics:pattern_schematic_from_substitute',
    'createappliedkinetics:energy_provider',
    'occultengineering:crafting/mechanical_pulverizer',
    'railways:sequenced_assembly/track_monorail',
    'simulated:navigation_table',
    'trainutilities:processing_unit', 'trainutilities:prototype_door_db', 'trainutilities:prototype_door_sf',
    'trainutilities:sound_unit', 'trainutilities:speaker_membrane',
    // vanilla copper and gold ingots, lapis (AdvancedAE reaction chamber, ExtendedAE crystal assembler)
    'advanced_ae:skybronze', 'advanced_ae:quantum_alloy', 'advanced_ae:entroingot', 'extendedae:assembler/entro_ingot_transformation',
    // vanilla iron block (copying the AE2 inscriber presses)
    'ae2:inscriber/calculation_processor_press', 'ae2:inscriber/engineering_processor_press', 'ae2:inscriber/logic_processor_press',
    'ae2:inscriber/silicon_press', 'megacells:inscriber/accumulation_processor_press_extra',
    'advanced_ae:quantum_processor_press_from_iron'
  ].concat(DECALS.map((d) => `createdeco:decal_${d}`))
    // Create's dyed placards (registered under minecraft:), createdeco's shipping containers (same namespace)
    .concat(COLORS.filter((c) => c !== 'white').map((c) => `minecraft:${c}_placard`))
    .concat(COLORS.map((c) => `minecraft:${c}_shipping_container`))
  const todo = []
  const rewrite = (ids, map) => {
    const re = new RegExp(`"item":"(${Object.keys(map).join('|')})"`, 'g')
    ids.forEach((id) => {
      event.forEachRecipe({ id: id }, (r) => {
        const text = String(r.json)
        const next = text.replace(re, (m, from) => `"tag":"${map[from]}"`)
        if (next !== text) todo.push([id, next])
      })
    })
  }
  rewrite(IDS, Object.assign({}, DEAD, BARREL))
  todo.forEach((t) => {
    event.remove({ id: t[0] })
    event.custom(JSON.parse(t[1])).id(t[0])
  })
  console.info(`[firmages] dead inputs: ${todo.length} of ${IDS.length} recipes now take the common tag`)
})
