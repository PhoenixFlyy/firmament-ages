// priority: -100
// Firmament Ages - machine recipes wait for the Age of their station (Felix's rule: a recipe of a locked Age works
// nowhere, and EMI shows it only once its Age is unlocked).
// The firmages-core gate judges a recipe by its OUTPUTS only (SPEC section 4.3). A machine of a later Age that makes
// an earlier item (an IE Arc Furnace turning dust into a TFC ingot, a Mekanism enrichment chamber making a dust)
// would therefore load, and show in EMI, long before the machine can be built. This script removes every recipe of a
// machine type while the Age of that machine is locked; the unlock reload brings them back. Grid recipes, TFC,
// Create and vanilla types are never touched (their stations are TFC blocks or come with Create in the Bronze Age).
// World data types (IE mineral mixes = excavator veins, IP reservoirs, fuels, fertilisers) are never removed.
// Ages: dev/age_map.toml [mods] for the mod default, overridden per type from Doc 10 v3 matrix 6.1 (IE MV/HV
// machines age_5, Mekanism fission age_7, the antiprotonic nucleosynthesizer and Evolved Mekanism age_8, Theurgy
// reformation age_6). Cost: one type filter (about 1.5 ms) per locked type and reload.

ServerEvents.recipes((event) => {
  // recipe type -> Age of its station
  const STATION_AGE = {}
  const put = (age, types) => types.forEach((t) => { STATION_AGE[t] = age })

  // ---- Arcane Age magic stations (Doc 08 section 7) ----
  put('age_3', ['occultism:crushing', 'occultism:crystallize', 'occultism:miner', 'occultism:ritual', 'occultism:spirit_fire',
    'occultism:spirit_trade', 'ars_nouveau:crush', 'ars_nouveau:enchanting_apparatus', 'ars_nouveau:imbuement',
    'ars_nouveau:glyph', 'ars_nouveau:alakarkinos_conversion', 'ars_nouveau:budding_conversion', 'ars_nouveau:scry_ritual',
    'ars_nouveau:summon_ritual', 'ars_additions:bulk_scribing', 'ars_additions:charm_charging', 'ars_additions:imbue_scroll',
    'theurgy:accumulation', 'theurgy:calcination', 'theurgy:catalysation', 'theurgy:digestion', 'theurgy:distillation',
    'theurgy:fermentation', 'theurgy:incubation', 'theurgy:liquefaction', 'summoningrituals:altar',
    'create_enchantment_industry:grinding', 'create_enchantment_industry:infusing', 'apotheosis:gem_cutting',
    'apotheosis:reforging', 'apotheosis:salvaging', 'apothic_enchanting:infusion'])
  put('age_6', ['theurgy:reformation']) // Reformation Array, magic tail of the Information Age (Doc 08 section 7.2)

  // ---- Industrial Age: IE LV machines, Advanced TFC Tech, Create Big Cannons, Cataclysm ----
  put('age_4', ['immersiveengineering:alloy', 'immersiveengineering:blast_furnace', 'immersiveengineering:coke_oven',
    'immersiveengineering:crusher', 'immersiveengineering:metal_press', 'immersiveengineering:blueprint',
    'immersiveengineering:fermenter', 'immersiveengineering:squeezer', 'advancedtfctech:beamhouse',
    'advancedtfctech:fleshing_machine', 'advancedtfctech:grist_mill', 'advancedtfctech:power_loom', 'advancedtfctech:thresher',
    'createbigcannons:melting', 'cataclysm:weapon_fusion', 'cataclysm:amethyst_bless'])

  // ---- Electric Age: IE MV/HV machines and Immersive Petroleum (Doc 10 v3 matrix 6.1, column 5) ----
  put('age_5', ['immersiveengineering:arc_furnace', 'immersiveengineering:sawmill', 'immersiveengineering:mixer',
    'immersiveengineering:bottling_machine', 'immersiveengineering:cloche', 'immersivepetroleum:distillationtower',
    'immersivepetroleum:cokerunit', 'immersivepetroleum:hydrotreater'])

  // ---- Information Age: Mekanism basic/advanced machines, AE2, ExtendedAE, Mystical Agriculture ----
  put('age_6', ['mekanism:chemical_conversion', 'mekanism:chemical_infusing', 'mekanism:combining', 'mekanism:compressing',
    'mekanism:crushing', 'mekanism:crystallizing', 'mekanism:dissolution', 'mekanism:energy_conversion', 'mekanism:enriching',
    'mekanism:evaporating', 'mekanism:injecting', 'mekanism:metallurgic_infusing', 'mekanism:oxidizing', 'mekanism:painting',
    'mekanism:pigment_extracting', 'mekanism:pigment_mixing', 'mekanism:purifying', 'mekanism:reaction', 'mekanism:rotary',
    'mekanism:separating', 'mekanism:washing', 'ae2:inscriber', 'ae2:charger', 'ae2:transform', 'ae2:entropy',
    'extendedae:circuit_cutter', 'extendedae:crystal_assembler', 'extendedae:crystal_fixer',
    'mysticalagriculture:infusion', 'mysticalagriculture:reprocessor', 'mysticalagriculture:soul_extraction',
    'mysticalagriculture:enchanter', 'mysticalagriculture:soulium_spawner', 'mysticalagriculture:awakening'])

  // ---- Space Age: Mekanism fission chain, Ad Astra, DE Fusion Crafting ----
  put('age_7', ['mekanism:activating', 'mekanism:centrifuging', 'ad_astra:cryo_freezing', 'ad_astra:nasa_workbench',
    'ad_astra:oxygen_loading', 'ad_astra:space_station_recipe', 'draconicevolution:fusion_crafting'])

  // ---- Quantum Age: antimatter and Evolved Mekanism ----
  put('age_8', ['mekanism:nucleosynthesizing', 'mekanism:apt', 'mekanism:chemixing'])

  // ---- Singularity Age: Stargate Journey ----
  put('age_9', ['sgjourney:crystallizing', 'sgjourney:advanced_crystallizing', 'sgjourney:naquadah_liquidizing',
    'sgjourney:naquadah_heavy_liquidizing'])

  let removed = 0
  Object.keys(STATION_AGE).forEach((type) => {
    if (FirmAges.isUnlocked(STATION_AGE[type])) return
    event.remove({ type: type })
    removed++
  })
  if (removed) console.info(`[firmages] station Ages: removed the recipes of ${removed} machine types of locked Ages`)
})
