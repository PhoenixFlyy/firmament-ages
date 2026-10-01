// Firmament Ages - vanilla station blocks in the recipes of the tech and utility mods.
// A TFC world has no recipe for the vanilla crafting table, furnace, blast furnace, smoker, campfire, anvil,
// enchanting table, barrel or trapped chest, and no vanilla bucket or milk bucket, so every recipe that consumes one
// as a part could never be crafted. Among them are the stations of the
// later Ages: the Mekanism Metallurgic Infuser (every Mekanism circuit and alloy), the AE2 Molecular Assembler and
// Pattern Provider, the Ad Astra NASA Workbench (every rocket) and Fuel Refinery.
// Each such input becomes the TFC counterpart the Arcane Age already uses (recipes/age_3/arcane_tfc_inputs.js, MAP);
// the magic mods are rewritten there. Recipes that only transform the vanilla block itself (sawing a crafting
// table, haunting a campfire or lantern) are left alone.
// Ids from the all-Ages /fa_dump_full of 2026-10-01; dev/poc_analyze.py check R-0 reports any recipe that still
// needs one of these blocks. Plain ids keep the reload cost at a map lookup per recipe (dev/poc-results.md,
// "Reload performance").

ServerEvents.recipes((event) => {
  const TWILIGHT_WOODS = ['canopy', 'dark', 'mangrove', 'mining', 'sorting', 'time', 'transformation', 'twilight_oak']
  const TFC = {
    'minecraft:crafting_table': '#tfc:workbenches',
    'minecraft:furnace': 'tfc:crucible',
    'minecraft:blast_furnace': 'tfc:blast_furnace',
    'minecraft:smoker': 'tfc:firepit',
    'minecraft:campfire': 'tfc:firepit',
    'minecraft:anvil': '#tfc:anvils',
    // Not a station, but the same problem: a TFC world has no vanilla bucket recipe (the Arcane MAP uses the TFC
    // wooden bucket too). Among the users: the Mekanism Dynamic Tank, Osmium Compressor, Electric Pump and basic
    // mechanical pipe (the Space Age fission chain needs the PRC and the oxidizer, both built on the Dynamic Tank).
    'minecraft:bucket': 'tfc:wooden_bucket',
    // Content fixes after the late Ages (2026-10-01): Apothic Enchanting's table is the pack's enchanting table
    // (tome, diamonds, obsidian); TFC barrels, chests and trapped chests (tags/tfc_stations.js); milk is a fluid in a
    // TFC bucket, so the ingredient is TFC's fluid-content test for 1000 mB of milk.
    'minecraft:enchanting_table': 'apothic_enchanting:apothic_enchanting_table',
    'minecraft:barrel': '#tfc:barrels',
    'minecraft:chest': '#firmages:chests/tfc',
    'minecraft:trapped_chest': '#firmages:chests/trapped_tfc',
    'minecraft:milk_bucket': { type: 'tfc:fluid_content', fluid: { fluid: 'minecraft:milk', amount: 1000 } }
  }
  const USERS = {
    'minecraft:crafting_table': [
      'ad_astra:nasa_workbench', 'advanced_ae:pick_craft_card',
      'ae2:materials/cardcrafting', 'ae2:network/blocks/pattern_providers_interface',
      'ae2:network/crafting/molecular_assembler', 'ae2:network/parts/terminals_crafting',
      'ae2:network/upgrade_wireless_crafting_terminal', 'create:crafting/appliances/crafting_blueprint',
      'create:crafting/kinetics/mechanical_crafter', 'engineered_schematics:schematic_table',
      'sophisticatedbackpacks:crafting_upgrade', 'toms_storage:crafting_terminal', 'twilightforest:uncrafting_table'
    ],
    'minecraft:furnace': [
      'ad_astra:coal_generator', 'ad_astra:fuel_refinery', 'ae2:network/blocks/energy_vibration_chamber',
      'draconicevolution:machines/generator',
      'mekanism:fuelwood_heater', 'mekanism:metallurgic_infuser', 'mekanismgenerators:generator/heat',
      'mysticalagriculture:furnace', 'sophisticatedbackpacks:smelting_upgrade'
    ],
    'minecraft:blast_furnace': [
      'ad_astra:etrionic_blast_furnace', 'simulated:red_portable_engine', 'sophisticatedbackpacks:blasting_upgrade'
    ],
    'minecraft:smoker': ['sophisticatedbackpacks:smoking_upgrade'],
    'minecraft:campfire': [
      'apotheosis:burning_spawner_rune', 'railways:crafting/smokestack_caboosestyle',
      'railways:crafting/smokestack_coalburner', 'railways:crafting/smokestack_long',
      'railways:crafting/smokestack_oilburner', 'railways:crafting/smokestack_streamlined',
      'railways:crafting/smokestack_woodburner'
    ],
    'minecraft:bucket': [
      'advanced_ae:reactionchamber', 'draconicevolution:fluid_gate', 'draconicevolution:rain_sensor',
      'framedblocks:framed_tank', 'immersiveengineering:crafting/chemthrower', 'immersiveengineering:crafting/jerrycan',
      'immersivepetroleum:oil_can', 'mekanism:dynamic_tank', 'mekanism:electric_pump', 'mekanism:osmium_compressor',
      'mekanism:thermal_evaporation/controller', 'mekanism:transmitter/mechanical_pipe/basic',
      'mekanismgenerators:saturating_condenser', 'sgjourney:naquadah_liquidizer', 'sophisticatedbackpacks:pump_upgrade',
      'toms_storage:paint_kit'
    ],
    'minecraft:anvil': [
      'apothic_enchanting:scrap_tome', 'cataclysm:mechanical_fusion_anvil',
      'create_enchantment_industry:smithing/blaze_forger', 'sophisticatedbackpacks:anvil_upgrade'
    ],
    'minecraft:enchanting_table': [
      'apotheosis:augmenting_table', 'apotheosis:simple_reforging_table', 'apothic_enchanting:library',
      'create_enchantment_industry:smithing/blaze_enchanter', 'draconicevolution:machines/energy_transfuser',
      'mysticalagriculture:enchanter'
    ],
    'minecraft:barrel': ['create_dragons_plus:crafting/fragile_fluid_tank', 'simulated:velocity_sensor'].concat(
      ['black', 'blue', 'brown', 'cyan', 'gray', 'green', 'light_blue', 'light_gray', 'lime', 'magenta', 'orange', 'pink',
        'purple', 'red', 'white', 'yellow'].map((c) => `create:crafting/logistics/${c}_postbox`)),
    'minecraft:chest': TWILIGHT_WOODS.map((w) => `twilightforest:wood/${w}_chest`),
    'minecraft:trapped_chest': TWILIGHT_WOODS.map((w) => `twilightforest:wood/${w}_trapped_chest`)
  }
  let n = 0
  Object.keys(USERS).forEach((from) => {
    USERS[from].forEach((id) => {
      event.replaceInput({ id: id }, from, TFC[from])
      n++
    })
  })
  // Steam 'n' Rails boilers (about 130 colour and wrap variants): one regex id filter instead of 130 ids.
  event.replaceInput({ id: /^railways:mechanical_crafting\/.*locometal_boiler$/ }, 'minecraft:bucket', 'tfc:wooden_bucket')
  // Types without a KubeJS schema (ExtendedAE crystal assembler, DE fusion crafting, the FramedBlocks framing saw,
  // Sophisticated Core upgrade recipes): replaceInput does not touch
  // them, so their JSON is rewritten and re-added under its own id, as in arcane_tfc_inputs.js.
  // Create's cake takes the milk ingredient, which replaceInput cannot write.
  const AS_JSON = ['advanced_ae:eaelargeappupgrade', 'draconicevolution:machines/draconium_chest',
    'extendedae:assembler/ex_pattern_provider', 'framedblocks:framing_saw/framed_tank', 'sophisticatedbackpacks:pump_upgrade',
    'create:crafting/curiosities/cake']
  const json = (v) => JSON.stringify(typeof v !== 'string' ? v : v.startsWith('#') ? { tag: v.slice(1) } : { item: v }).slice(1, -1)
  const ITEM_RE = new RegExp(`"item":"(${Object.keys(TFC).join('|')})"`, 'g')
  const todo = []
  AS_JSON.forEach((id) => {
    event.forEachRecipe({ id: id }, (r) => {
      let text = String(r.json)
      let next = text.replace(ITEM_RE, (m, from) => json(TFC[from]))
      if (next !== text) todo.push([id, next])
    })
  })
  todo.forEach((t) => {
    event.remove({ id: t[0] })
    event.custom(JSON.parse(t[1])).id(t[0])
  })
  console.info(`[firmages] TFC station inputs: ${n} replacements, ${todo.length} recipes rewritten as JSON`)
})
