// Firmament Ages - vanilla station blocks in the recipes of the tech and utility mods.
// A TFC world has no recipe for the vanilla crafting table, furnace, blast furnace, smoker, campfire or anvil,
// so every recipe that consumes one as a part could never be crafted. Among them are the stations of the
// later Ages: the Mekanism Metallurgic Infuser (every Mekanism circuit and alloy), the AE2 Molecular Assembler and
// Pattern Provider, the Ad Astra NASA Workbench (every rocket) and Fuel Refinery.
// Each such input becomes the TFC counterpart the Arcane Age already uses (recipes/age_3/arcane_tfc_inputs.js, MAP);
// the magic mods are rewritten there. Recipes that only transform the vanilla block itself (sawing a crafting
// table, haunting a campfire or lantern) are left alone.
// Ids from the all-Ages /fa_dump_full of 2026-10-01; dev/poc_analyze.py check R-0 reports any recipe that still
// needs one of these blocks. Plain ids keep the reload cost at a map lookup per recipe (dev/poc-results.md,
// "Reload performance").

ServerEvents.recipes((event) => {
  const TFC = {
    'minecraft:crafting_table': '#tfc:workbenches',
    'minecraft:furnace': 'tfc:crucible',
    'minecraft:blast_furnace': 'tfc:blast_furnace',
    'minecraft:smoker': 'tfc:firepit',
    'minecraft:campfire': 'tfc:firepit',
    'minecraft:anvil': '#tfc:anvils'
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
    'minecraft:anvil': [
      'apothic_enchanting:scrap_tome', 'cataclysm:mechanical_fusion_anvil',
      'create_enchantment_industry:smithing/blaze_forger', 'sophisticatedbackpacks:anvil_upgrade'
    ]
  }
  let n = 0
  Object.keys(USERS).forEach((from) => {
    USERS[from].forEach((id) => {
      event.replaceInput({ id: id }, from, TFC[from])
      n++
    })
  })
  // Types without a KubeJS schema (ExtendedAE crystal assembler, DE fusion crafting): replaceInput does not touch
  // them, so their JSON is rewritten and re-added under its own id, as in arcane_tfc_inputs.js.
  const AS_JSON = ['advanced_ae:eaelargeappupgrade', 'draconicevolution:machines/draconium_chest',
    'extendedae:assembler/ex_pattern_provider']
  const json = (v) => JSON.stringify(v.startsWith('#') ? { tag: v.slice(1) } : { item: v }).slice(1, -1)
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
