// priority: -100
// Firmament Ages - machine recipes wait for the Age of their station (Felix's rule: a recipe of a locked Age works
// nowhere, and EMI shows it only once its Age is unlocked).
// The firmages-core gate judges a recipe by its outputs (SPEC section 4.3). A machine of a later Age that makes an
// earlier item (an IE Arc Furnace turning dust into a TFC ingot, a Mekanism enrichment chamber making a dust) would
// therefore load, and show in EMI, long before the machine can be built. This script declares the Age of each machine
// type (FirmAges.stationAge); the gate keeps every recipe whose type or serializer is that type out while the Age is
// locked, the pack's own recipes added in any event included. The script itself is Age-independent: an Age change
// re-filters the recipes of the last load without running it again (the Age swap, firmages-core SPEC section 3.3).
// Grid recipes, TFC and vanilla types are not listed. Create's stations follow their tier: press, mixer, millstone, fan
// and saw come with Create in the Bronze Age (age_1), so their types stay unlisted; the deployer, spout, item drain and
// crushing wheel are Iron Age (age_2), so crushing, deploying, filling and emptying wait for age_2. The basin heat
// levels (heated: a heater, superheated: a Blaze Burner) are station locks inside the gate, read from the Age tags of
// tfcreate:primitive_heater and create:blaze_burner. World data types (IE mineral mixes = excavator veins, IP reservoirs, fuels, fertilisers) are
// never listed (gate.exemptRecipeTypes).
// Ages: dev/age_map.toml [mods] for the mod default, overridden per type from Doc 10 v3 matrix 6.1 (IE MV/HV
// machines age_5, Mekanism fission age_7, the antiprotonic nucleosynthesizer and Evolved Mekanism age_8, Theurgy
// reformation age_6). dev/poc_analyze.py reads the put('age_N', [...]) calls below.

ServerEvents.recipes((event) => {
  // recipe type -> Age of its station
  const STATION_AGE = {}
  const put = (age, types) => types.forEach((t) => { STATION_AGE[t] = age })

  // ---- Iron Age: Create deployer, spout, item drain, crushing wheel ----
  put('age_2', ['create:crushing', 'create:deploying', 'create:filling', 'create:emptying'])

  // ---- Arcane Age magic stations (Doc 08 section 7) ----
  put('age_3', ['occultism:crushing', 'occultism:crystallize', 'occultism:miner', 'occultism:ritual', 'occultism:spirit_fire',
    'occultism:spirit_trade', 'ars_nouveau:crush', 'ars_nouveau:enchanting_apparatus', 'ars_nouveau:imbuement',
    'ars_nouveau:glyph', 'ars_nouveau:alakarkinos_conversion', 'ars_nouveau:budding_conversion', 'ars_nouveau:scry_ritual',
    'ars_nouveau:summon_ritual', 'ars_nouveau:enchantment', 'ars_nouveau:armor_upgrade', 'ars_nouveau:reactive_enchantment',
    'ars_nouveau:spell_write', 'ars_nouveau:dispel_entity', 'ars_nouveau:prestidigitation', 'ars_additions:bulk_scribing', 'ars_additions:charm_charging', 'ars_additions:imbue_scroll',
    'theurgy:accumulation', 'theurgy:calcination', 'theurgy:catalysation', 'theurgy:digestion', 'theurgy:distillation',
    'theurgy:fermentation', 'theurgy:incubation', 'theurgy:liquefaction',
    'create_enchantment_industry:grinding', 'create_enchantment_industry:infusing', 'apotheosis:gem_cutting',
    'apotheosis:reforging', 'apotheosis:salvaging', 'apothic_enchanting:infusion',
    // fan processing with the Enchantment Industry catalysts (powder snow, dragon head) and Ars Additions' rituals
    'create_dragons_plus:freezing', 'create_dragons_plus:ending', 'ars_additions:locate_structure',
    'ars_additions:source_spawner'])
  put('age_6', ['theurgy:reformation']) // Reformation Array, magic tail of the Information Age (Doc 08 section 7.2)
  // The Lens Crown of the sky road (firmages-core SPEC 27.7): the Star Anvil forges from the Arcane Age on
  put('age_3', ['firmages:starforge'])

  // ---- Industrial Age: IE LV machines, Advanced TFC Tech, Create Big Cannons ----
  put('age_4', ['immersiveengineering:alloy', 'immersiveengineering:blast_furnace', 'immersiveengineering:coke_oven',
    'immersiveengineering:crusher', 'immersiveengineering:metal_press', 'immersiveengineering:blueprint',
    'immersiveengineering:fermenter', 'immersiveengineering:squeezer', 'advancedtfctech:beamhouse',
    'advancedtfctech:fleshing_machine', 'advancedtfctech:grist_mill', 'advancedtfctech:power_loom', 'advancedtfctech:thresher',
    'createbigcannons:melting',
    // Create Big Cannons' deployer assembly of shells and cartridges
    'createbigcannons:autocannon_ammo_container_filling_deployer', 'createbigcannons:big_cartridge_filling_deployer',
    'createbigcannons:cartridge_assembly_deployer', 'createbigcannons:munition_fuzing_deployer',
    'createbigcannons:tracer_application_deployer'])

  // ---- Electric Age: IE MV/HV machines and Immersive Petroleum (Doc 10 v3 matrix 6.1, column 5) ----
  put('age_5', ['immersiveengineering:arc_furnace', 'immersiveengineering:sawmill', 'immersiveengineering:mixer',
    'immersiveengineering:bottling_machine', 'immersiveengineering:cloche', 'immersivepetroleum:distillationtower',
    'immersivepetroleum:cokerunit', 'immersivepetroleum:hydrotreater'])

  // ---- Information Age: Mekanism basic/advanced machines, AE2, ExtendedAE, Mystical Agriculture ----
  put('age_6', ['mekanism:chemical_conversion', 'mekanism:chemical_infusing', 'mekanism:combining', 'mekanism:compressing',
    'mekanism:crushing', 'mekanism:crystallizing', 'mekanism:dissolution', 'mekanism:energy_conversion', 'mekanism:enriching',
    'mekanism:evaporating', 'mekanism:injecting', 'mekanism:metallurgic_infusing', 'mekanism:oxidizing', 'mekanism:painting',
    'mekanism:pigment_extracting', 'mekanism:pigment_mixing', 'mekanism:purifying', 'mekanism:reaction', 'mekanism:rotary',
    'mekanism:separating', 'mekanism:smelting', 'mekanism:washing', 'ae2:inscriber', 'ae2:charger', 'ae2:transform', 'ae2:entropy',
    'ae2:matter_cannon', 'ae2:storage_cell_disassembly', 'ae2:crafting_unit_transform',
    'extendedae:circuit_cutter', 'extendedae:crystal_assembler', 'extendedae:crystal_fixer',
    'mysticalagriculture:infusion', 'mysticalagriculture:reprocessor', 'mysticalagriculture:soul_extraction',
    'mysticalagriculture:enchanter', 'mysticalagriculture:soulium_spawner', 'mysticalagriculture:awakening',
    // More Mekanism Processing's own serializers of the same machines (the type filter matches the JSON type)
    'moremekanismprocessing:tag_purifying', 'moremekanismprocessing:tag_injecting', 'moremekanismprocessing:tag_enriching',
    'moremekanismprocessing:tag_crushing', 'moremekanismprocessing:tag_crystallizing'])

  // ---- Space Age: Mekanism fission chain, Ad Astra, DE Fusion Crafting ----
  put('age_7', ['mekanism:activating', 'mekanism:centrifuging', 'ad_astra:cryo_freezing', 'ad_astra:nasa_workbench',
    'ad_astra:oxygen_loading', 'ad_astra:space_station_recipe', 'draconicevolution:fusion_crafting'])

  // ---- Quantum Age: antimatter and Evolved Mekanism ----
  put('age_8', ['mekanism:nucleosynthesizing', 'mekanism:apt', 'mekanism:chemixing', 'advanced_ae:reaction'])

  // ---- Singularity Age: Stargate Journey ----
  put('age_9', ['sgjourney:crystallizing', 'sgjourney:advanced_crystallizing', 'sgjourney:naquadah_liquidizing',
    'sgjourney:naquadah_heavy_liquidizing'])

  Object.keys(STATION_AGE).forEach((type) => FirmAges.stationAge(type, STATION_AGE[type]))
  console.info(`[firmages] station Ages: ${Object.keys(STATION_AGE).length} machine types declared to the recipe gate`)
})
