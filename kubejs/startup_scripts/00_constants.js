// priority: 1000
// Firmament Ages - shared constants.
// Startup scripts run first on client and server; `global` is shared with server_scripts and
// client_scripts, so every other script reads its stage names from here.
// All player-facing strings are English.

global.FA = {
  NS: 'firmages',

  // Age stages, cumulative, in order. Stage ids must match config/progressivestages/stages/*.toml.
  AGES: ['dawn', 'age_0', 'age_1', 'age_2', 'age_3', 'age_4', 'age_5', 'age_6', 'age_7', 'age_8', 'age_9'],

  AGE_TITLES: {
    dawn: 'Dawn',
    age_0: 'Age 0: Stone Age',
    age_1: 'Age 1: Bronze Age',
    age_2: 'Age 2: Iron Age',
    age_3: 'Age 3: Arcane Age',
    age_4: 'Age 4: Industrial Age',
    age_5: 'Age 5: Electric Age',
    age_6: 'Age 6: Information Age',
    age_7: 'Age 7: Space Age',
    age_8: 'Age 8: Quantum Age',
    age_9: 'Age 9: Singularity Age',
    finale_won: 'Beyond the Firmament'
  },

  AGE_SUBTITLES: {
    age_0: 'Fire is tamed. Clay and copper await.',
    age_1: 'The first alloys. New veins can be found.',
    age_2: 'The map opens. Iron veins become visible.',
    age_3: 'Spirits stir. The Nether opens.',
    age_4: 'Coke, steel and the first current.',
    age_5: 'The grid hums.',
    age_6: 'Data flows. The End opens.',
    age_7: 'The sky is no longer the limit.',
    age_8: 'Matter bends.',
    age_9: 'The Stargate waits.',
    finale_won: 'Trophies unlocked.'
  },

  // Mob ladder: which mob_N belongs to which age stage (Doc 08 section 2.3 / section 5).
  MOB_STAGE_FOR: {
    dawn: 'mob_0', age_0: 'mob_0', age_1: 'mob_1', age_2: 'mob_2', age_3: 'mob_3', age_4: 'mob_4',
    age_5: 'mob_5', age_6: 'mob_6', age_7: 'mob_7', age_8: 'mob_8', age_9: 'mob_9'
  },

  // true: mob_0..mob_N are all kept (see mob_1.toml header for why). false: Doc 08 swap (revoke mob_(N-1)).
  MOB_LADDER_CUMULATIVE: true,

  // Helper stages with a window [grantedWith, revokedWith). null = never revoked.
  // Doc 08 section 2.3 and Doc 10 v3 section 6.2.
  HELPER_WINDOWS: {
    ftbchunks_mapping: ['age_2', null],
    tool_toms_storage: ['age_2', 'age_6'],
    tool_ie_alloy_kiln: ['age_4', 'age_5'],
    tool_ie_capacitor: ['age_4', 'age_6'],
    tool_mech_spawner: ['age_4', 'age_6']
  },

  // FTB Chunks quota granted with the Industrial Age (decision r8 A).
  CHUNK_QUOTA: { stage: 'age_4', claims: 25, forceLoads: 25 },

  // F3 shows coordinates from the Bronze Age on; gamerule reducedDebugInfo until then (Felix 2026-10-13, H5).
  COORDS_FROM_AGE: 'age_1',

  // Items that never appear in the pack (Doc 10 v3 section 7.2, all Ages).
  // Used by server_scripts/tags/hidden_from_viewers.js (tag c:hidden_from_recipe_viewers) and by
  // client_scripts/viewer_static.js (KubeJS RecipeViewerEvents). The PS stage "disabled" locks them too.
  HIDDEN_ITEMS: [
    'create:zinc_ore', 'create:deepslate_zinc_ore', 'create:raw_zinc', 'create:raw_zinc_block',
    'create:zinc_ingot', 'create:zinc_block', 'create:brass_ingot',
    'create:iron_sheet', 'create:copper_sheet', 'create:golden_sheet', 'create:brass_sheet',
    'create:crushed_raw_iron', 'create:crushed_raw_gold', 'create:crushed_raw_copper', 'create:crushed_raw_zinc',
    'create:crushed_raw_osmium', 'create:crushed_raw_platinum', 'create:crushed_raw_silver', 'create:crushed_raw_tin',
    'create:crushed_raw_lead', 'create:crushed_raw_quicksilver', 'create:crushed_raw_aluminum',
    'create:crushed_raw_uranium', 'create:crushed_raw_nickel',
    'occultism:silver_ore', 'occultism:silver_ore_deepslate', 'occultism:raw_silver', 'occultism:raw_silver_block',
    'occultism:silver_ingot', 'occultism:silver_block', 'occultism:silver_nugget', 'occultism:silver_dust',
    'ftbquests:book',
    // Arcane and Industrial Age (Doc 10 v3 sections 1.2, 1.6, 7.2); same list as stages/disabled.toml.
    'occultism:book_of_calling_foliot_transport_items', 'ars_additions:warp_index',
    'ars_additions:stabilized_warp_index', 'immersiveengineering:pickaxe_steel', 'immersiveengineering:shovel_steel',
    'immersiveengineering:axe_steel', 'immersiveengineering:hoe_steel', 'immersiveengineering:sword_steel',
    'immersiveengineering:armor_steel_helmet', 'immersiveengineering:armor_steel_chestplate',
    'immersiveengineering:armor_steel_leggings', 'immersiveengineering:armor_steel_boots',
    'immersiveengineering:windmill', 'immersiveengineering:windmill_blade', 'immersiveengineering:windmill_sail',
    'immersiveengineering:watermill', 'immersiveengineering:waterwheel_segment', 'immersiveengineering:dynamo',
    'immersiveengineering:thermoelectric_generator', 'immersiveengineering:refinery',
    'immersiveengineering:conveyor_dropper', 'immersiveengineering:conveyor_extract',
    'immersiveengineering:conveyor_redstone', 'immersiveengineering:conveyor_splitter',
    'immersiveengineering:conveyor_vertical', 'immersiveengineering:ingot_silver',
    'immersiveengineering:ingot_nickel', 'immersiveengineering:ingot_steel', 'immersiveengineering:nugget_copper',
    'immersiveengineering:plate_copper', 'immersiveengineering:plate_iron', 'immersiveengineering:plate_gold',
    'immersiveengineering:plate_silver', 'immersiveengineering:plate_nickel', 'immersiveengineering:plate_steel',
    'immersiveengineering:ore_silver', 'immersiveengineering:ore_nickel',
    'immersiveengineering:deepslate_ore_silver', 'immersiveengineering:deepslate_ore_nickel',
    'immersiveengineering:raw_silver', 'immersiveengineering:raw_nickel', 'immersiveengineering:raw_block_silver',
    'immersiveengineering:raw_block_nickel', 'createaddition:barbed_wire', 'createaddition:bioethanol_bucket',
    'createaddition:biomass', 'createaddition:biomass_pellet', 'createaddition:biomass_pellet_block',
    'createaddition:brass_rod', 'createaddition:cake_base', 'createaddition:cake_base_baked',
    'createaddition:capacitor', 'createaddition:chocolate_cake', 'createaddition:connector',
    'createaddition:copper_rod', 'createaddition:copper_spool', 'createaddition:copper_wire',
    'createaddition:creative_energy', 'createaddition:diamond_grit', 'createaddition:diamond_grit_sandpaper',
    'createaddition:digital_adapter', 'createaddition:electric_pump', 'createaddition:electrum_amulet',
    'createaddition:electrum_block', 'createaddition:electrum_ingot', 'createaddition:electrum_nugget',
    'createaddition:electrum_rod', 'createaddition:electrum_sheet', 'createaddition:electrum_spool',
    'createaddition:electrum_wire', 'createaddition:festive_spool', 'createaddition:gold_rod',
    'createaddition:gold_spool', 'createaddition:gold_wire', 'createaddition:honey_cake', 'createaddition:iron_rod',
    'createaddition:iron_wire', 'createaddition:large_connector', 'createaddition:modular_accumulator',
    'createaddition:portable_energy_interface', 'createaddition:redstone_relay', 'createaddition:rolling_mill',
    'createaddition:seed_oil_bucket', 'createaddition:servo_motor', 'createaddition:small_light_connector',
    'createaddition:spool', 'createaddition:straw', 'createaddition:tesla_coil', 'createaddition:zinc_sheet',
    // Electric to Singularity Age (Doc 10 v3 section 7.2); same list as stages/disabled.toml, generated from the registry.
    // Immersive Petroleum: the gas generator (the IE Diesel Generator is the one FE generator of the Electric Age)
    'immersivepetroleum:gas_generator',
    // Create: Diesel Generators: everything except the engines and their upgrades (Doc 10 v3 section 7.2)
    'createdieselgenerators:andesite_girder', 'createdieselgenerators:andesite_girder_strut',
    'createdieselgenerators:asphalt_block', 'createdieselgenerators:asphalt_slab',
    'createdieselgenerators:asphalt_stairs', 'createdieselgenerators:basin_lid',
    'createdieselgenerators:biodiesel_bucket', 'createdieselgenerators:black_cement_bucket',
    'createdieselgenerators:blue_cement_bucket', 'createdieselgenerators:brown_cement_bucket',
    'createdieselgenerators:bulk_fermenter', 'createdieselgenerators:burner', 'createdieselgenerators:canister',
    'createdieselgenerators:chemical_sprayer', 'createdieselgenerators:chemical_sprayer_lighter',
    'createdieselgenerators:chemical_turret', 'createdieselgenerators:chip_wood_beam',
    'createdieselgenerators:chip_wood_block', 'createdieselgenerators:chip_wood_slab',
    'createdieselgenerators:chip_wood_stairs', 'createdieselgenerators:crude_oil_bucket',
    'createdieselgenerators:cyan_cement_bucket', 'createdieselgenerators:diesel_bucket',
    'createdieselgenerators:distillation_controller', 'createdieselgenerators:entity_filter',
    'createdieselgenerators:ethanol_bucket', 'createdieselgenerators:gasoline_bucket',
    'createdieselgenerators:gray_cement_bucket', 'createdieselgenerators:green_cement_bucket',
    'createdieselgenerators:hammer', 'createdieselgenerators:kelp_handle',
    'createdieselgenerators:light_blue_cement_bucket', 'createdieselgenerators:light_gray_cement_bucket',
    'createdieselgenerators:lighter', 'createdieselgenerators:lime_cement_bucket',
    'createdieselgenerators:magenta_cement_bucket', 'createdieselgenerators:mold',
    'createdieselgenerators:oil_barrel', 'createdieselgenerators:oil_scanner',
    'createdieselgenerators:orange_cement_bucket', 'createdieselgenerators:pink_cement_bucket',
    'createdieselgenerators:plant_oil_bucket', 'createdieselgenerators:pumpjack_bearing',
    'createdieselgenerators:pumpjack_crank', 'createdieselgenerators:pumpjack_head',
    'createdieselgenerators:pumpjack_hole', 'createdieselgenerators:purple_cement_bucket',
    'createdieselgenerators:red_cement_bucket', 'createdieselgenerators:sheet_metal_panel',
    'createdieselgenerators:track_layers_bag', 'createdieselgenerators:white_cement_bucket',
    'createdieselgenerators:wire_cutters', 'createdieselgenerators:wood_chip',
    'createdieselgenerators:yellow_cement_bucket',
    // Mekanism (Doc 10 v3 section 7.2): QIO, Dimensional Stabilizer, Anchor Upgrade, Formulaic Assemblicator, Precision Sawmill and the sawing factories, Mekanism tin/lead/uranium/steel/bronze ingots and nuggets, every Mekanism ore block, raw ore and raw-ore block
    'mekanism:advanced_sawing_factory', 'mekanism:basic_sawing_factory', 'mekanism:block_raw_lead',
    'mekanism:block_raw_osmium', 'mekanism:block_raw_tin', 'mekanism:block_raw_uranium',
    'mekanism:deepslate_fluorite_ore', 'mekanism:deepslate_lead_ore', 'mekanism:deepslate_osmium_ore',
    'mekanism:deepslate_silver_ore', 'mekanism:deepslate_tin_ore', 'mekanism:deepslate_uranium_ore',
    'mekanism:dimensional_stabilizer', 'mekanism:elite_sawing_factory', 'mekanism:fluorite_ore',
    'mekanism:formulaic_assemblicator', 'mekanism:ingot_bronze', 'mekanism:ingot_lead', 'mekanism:ingot_steel',
    'mekanism:ingot_tin', 'mekanism:ingot_uranium', 'mekanism:lead_ore', 'mekanism:nugget_bronze',
    'mekanism:nugget_lead', 'mekanism:nugget_steel', 'mekanism:nugget_tin', 'mekanism:nugget_uranium',
    'mekanism:osmium_ore', 'mekanism:portable_qio_dashboard', 'mekanism:precision_sawmill', 'mekanism:qio_dashboard',
    'mekanism:qio_drive_array', 'mekanism:qio_drive_base', 'mekanism:qio_drive_hyper_dense',
    'mekanism:qio_drive_supermassive', 'mekanism:qio_drive_time_dilating', 'mekanism:qio_exporter',
    'mekanism:qio_importer', 'mekanism:qio_redstone_adapter', 'mekanism:raw_lead', 'mekanism:raw_osmium',
    'mekanism:raw_tin', 'mekanism:raw_uranium', 'mekanism:silver_ore', 'mekanism:tin_ore',
    'mekanism:ultimate_sawing_factory', 'mekanism:upgrade_anchor', 'mekanism:uranium_ore',
    // Mekanism Generators: heat, solar, advanced solar, wind and bio generators and the solar and geothermal MekaSuit modules (no passive or cheap power)
    'mekanismgenerators:advanced_solar_generator', 'mekanismgenerators:bio_generator',
    'mekanismgenerators:heat_generator', 'mekanismgenerators:module_geothermal_generator_unit',
    'mekanismgenerators:module_solar_recharging_unit', 'mekanismgenerators:solar_generator',
    'mekanismgenerators:wind_generator',
    // Mekanism Tools: the bronze, steel, osmium and lapis sets and every paxel (refined obsidian and glowstone stay)
    'mekanismtools:bronze_axe', 'mekanismtools:bronze_boots', 'mekanismtools:bronze_chestplate',
    'mekanismtools:bronze_helmet', 'mekanismtools:bronze_hoe', 'mekanismtools:bronze_leggings',
    'mekanismtools:bronze_paxel', 'mekanismtools:bronze_pickaxe', 'mekanismtools:bronze_shield',
    'mekanismtools:bronze_shovel', 'mekanismtools:bronze_sword', 'mekanismtools:diamond_paxel',
    'mekanismtools:gold_paxel', 'mekanismtools:iron_paxel', 'mekanismtools:lapis_lazuli_axe',
    'mekanismtools:lapis_lazuli_boots', 'mekanismtools:lapis_lazuli_chestplate', 'mekanismtools:lapis_lazuli_helmet',
    'mekanismtools:lapis_lazuli_hoe', 'mekanismtools:lapis_lazuli_leggings', 'mekanismtools:lapis_lazuli_paxel',
    'mekanismtools:lapis_lazuli_pickaxe', 'mekanismtools:lapis_lazuli_shield', 'mekanismtools:lapis_lazuli_shovel',
    'mekanismtools:lapis_lazuli_sword', 'mekanismtools:netherite_paxel', 'mekanismtools:osmium_axe',
    'mekanismtools:osmium_boots', 'mekanismtools:osmium_chestplate', 'mekanismtools:osmium_helmet',
    'mekanismtools:osmium_hoe', 'mekanismtools:osmium_leggings', 'mekanismtools:osmium_paxel',
    'mekanismtools:osmium_pickaxe', 'mekanismtools:osmium_shield', 'mekanismtools:osmium_shovel',
    'mekanismtools:osmium_sword', 'mekanismtools:refined_glowstone_paxel', 'mekanismtools:refined_obsidian_paxel',
    'mekanismtools:steel_axe', 'mekanismtools:steel_boots', 'mekanismtools:steel_chestplate',
    'mekanismtools:steel_helmet', 'mekanismtools:steel_hoe', 'mekanismtools:steel_leggings',
    'mekanismtools:steel_paxel', 'mekanismtools:steel_pickaxe', 'mekanismtools:steel_shield',
    'mekanismtools:steel_shovel', 'mekanismtools:steel_sword', 'mekanismtools:stone_paxel',
    'mekanismtools:wood_paxel',
    // Mekanism Additions: obsidian TNT
    'mekanismadditions:obsidian_tnt',
    // Mekanism: More Machine: replicators and UU matter, recycler, planting station, their factories, the duplicate silver family, the stamper, lathe, rolling mill and presser (second plate, rod, wire and processor stations), creative factories and the passive large heat/wind and solar heat generators
    'mekmm:advanced_lathing_factory', 'mekmm:advanced_planting_factory', 'mekmm:advanced_pressing_factory',
    'mekmm:advanced_recycling_factory', 'mekmm:advanced_replicating_factory', 'mekmm:advanced_rolling_mill_factory',
    'mekmm:advanced_stamping_factory', 'mekmm:basic_lathing_factory', 'mekmm:basic_planting_factory',
    'mekmm:basic_pressing_factory', 'mekmm:basic_recycling_factory', 'mekmm:basic_replicating_factory',
    'mekmm:basic_rolling_mill_factory', 'mekmm:basic_stamping_factory', 'mekmm:block_raw_silver',
    'mekmm:block_silver', 'mekmm:chemical_replicator', 'mekmm:clump_silver', 'mekmm:cnc_lathe',
    'mekmm:cnc_rolling_mill', 'mekmm:cnc_stamper', 'mekmm:creative_centrifuging_factory',
    'mekmm:creative_crystallizing_factory', 'mekmm:creative_dissolving_factory', 'mekmm:creative_lathing_factory',
    'mekmm:creative_liquifying_factory', 'mekmm:creative_oxidizing_factory', 'mekmm:creative_painting_factory',
    'mekmm:creative_pigment_extracting_factory', 'mekmm:creative_planting_factory', 'mekmm:creative_pressing_factory',
    'mekmm:creative_pressurised_reacting_factory', 'mekmm:creative_recycling_factory',
    'mekmm:creative_replicating_factory', 'mekmm:creative_rolling_mill_factory', 'mekmm:creative_stamping_factory',
    'mekmm:creative_washing_factory', 'mekmm:crystal_silver', 'mekmm:deepslate_silver_ore',
    'mekmm:dense_lathing_factory', 'mekmm:dense_planting_factory', 'mekmm:dense_pressing_factory',
    'mekmm:dense_recycling_factory', 'mekmm:dense_replicating_factory', 'mekmm:dense_rolling_mill_factory',
    'mekmm:dense_stamping_factory', 'mekmm:dirty_dust_silver', 'mekmm:dust_silver', 'mekmm:elite_lathing_factory',
    'mekmm:elite_planting_factory', 'mekmm:elite_pressing_factory', 'mekmm:elite_recycling_factory',
    'mekmm:elite_replicating_factory', 'mekmm:elite_rolling_mill_factory', 'mekmm:elite_stamping_factory',
    'mekmm:enriched_silver', 'mekmm:fluid_replicator', 'mekmm:ingot_silver', 'mekmm:large_heat_generator',
    'mekmm:large_wind_generator', 'mekmm:multiversal_lathing_factory', 'mekmm:multiversal_planting_factory',
    'mekmm:multiversal_pressing_factory', 'mekmm:multiversal_recycling_factory',
    'mekmm:multiversal_replicating_factory', 'mekmm:multiversal_rolling_mill_factory',
    'mekmm:multiversal_stamping_factory', 'mekmm:nugget_silver', 'mekmm:overclocked_lathing_factory',
    'mekmm:overclocked_planting_factory', 'mekmm:overclocked_pressing_factory', 'mekmm:overclocked_recycling_factory',
    'mekmm:overclocked_replicating_factory', 'mekmm:overclocked_rolling_mill_factory',
    'mekmm:overclocked_stamping_factory', 'mekmm:planting_station', 'mekmm:presser', 'mekmm:quantum_lathing_factory',
    'mekmm:quantum_planting_factory', 'mekmm:quantum_pressing_factory', 'mekmm:quantum_recycling_factory',
    'mekmm:quantum_replicating_factory', 'mekmm:quantum_rolling_mill_factory', 'mekmm:quantum_stamping_factory',
    'mekmm:raw_silver', 'mekmm:recycler', 'mekmm:replicator', 'mekmm:scrap', 'mekmm:scrap_box', 'mekmm:shard_silver',
    'mekmm:silver_ore', 'mekmm:solar_heat_generator', 'mekmm:ultimate_lathing_factory',
    'mekmm:ultimate_planting_factory', 'mekmm:ultimate_pressing_factory', 'mekmm:ultimate_recycling_factory',
    'mekmm:ultimate_replicating_factory', 'mekmm:ultimate_rolling_mill_factory', 'mekmm:ultimate_stamping_factory',
    'mekmm:uu_matter',
    // Evolved Mekanism: the creative tier, the paxels, the sawing factories, the solar generators, the solidification chamber and its molds (no molten-metal economy next to the TFC fluids), its QIO drive
    'evolvedmekanism:advanced_solar_generator', 'evolvedmekanism:better_gold_paxel',
    'evolvedmekanism:creative_alloying_factory', 'evolvedmekanism:creative_combining_factory',
    'evolvedmekanism:creative_compressing_factory', 'evolvedmekanism:creative_control_circuit',
    'evolvedmekanism:creative_crushing_factory', 'evolvedmekanism:creative_enriching_factory',
    'evolvedmekanism:creative_induction_cell', 'evolvedmekanism:creative_induction_provider',
    'evolvedmekanism:creative_infusing_factory', 'evolvedmekanism:creative_injecting_factory',
    'evolvedmekanism:creative_logistical_transporter', 'evolvedmekanism:creative_mechanical_pipe',
    'evolvedmekanism:creative_personal_barrel', 'evolvedmekanism:creative_personal_chest',
    'evolvedmekanism:creative_pressurized_tube', 'evolvedmekanism:creative_purifying_factory',
    'evolvedmekanism:creative_sawing_factory', 'evolvedmekanism:creative_smelting_factory',
    'evolvedmekanism:creative_solar_generator', 'evolvedmekanism:creative_thermodynamic_conductor',
    'evolvedmekanism:creative_tier_installer', 'evolvedmekanism:creative_universal_cable',
    'evolvedmekanism:dense_sawing_factory', 'evolvedmekanism:dense_solar_generator',
    'evolvedmekanism:elite_solar_generator', 'evolvedmekanism:mold_coin', 'evolvedmekanism:mold_dust',
    'evolvedmekanism:mold_gear', 'evolvedmekanism:mold_gem', 'evolvedmekanism:mold_ingot',
    'evolvedmekanism:mold_nugget', 'evolvedmekanism:mold_plate', 'evolvedmekanism:mold_rod',
    'evolvedmekanism:mold_storage_block', 'evolvedmekanism:mold_wire', 'evolvedmekanism:multiversal_sawing_factory',
    'evolvedmekanism:multiversal_solar_generator', 'evolvedmekanism:overclocked_sawing_factory',
    'evolvedmekanism:overclocked_solar_generator', 'evolvedmekanism:plaslitherite_paxel',
    'evolvedmekanism:qio_drive_black_hole', 'evolvedmekanism:qio_drive_boosted', 'evolvedmekanism:qio_drive_creative',
    'evolvedmekanism:qio_drive_hypra_solidified', 'evolvedmekanism:qio_drive_singularity',
    'evolvedmekanism:quantum_sawing_factory', 'evolvedmekanism:quantum_solar_generator',
    'evolvedmekanism:refined_redstone_paxel', 'evolvedmekanism:solidification_chamber',
    'evolvedmekanism:ultimate_solar_generator',
    // AE2: Vibration Chamber
    'ae2:vibration_chamber',
    // Harmony plan fixes 4, 5 and 19 (2026-10-15): passive or free power and a dead upgrade.
    'ae2:crystal_resonance_generator', 'createappliedkinetics:energy_provider', 'evolvedmekanism:upgrade_solar',
    // AdvancedAE: Quantum Armor and the strength card (it only upgrades the Quantum Armor)
    'advanced_ae:quantum_boots', 'advanced_ae:quantum_chestplate', 'advanced_ae:quantum_helmet',
    'advanced_ae:quantum_leggings', 'advanced_ae:strength_card',
    // Draconic Evolution: Energy Core and its stabilizer and pylon, the crystal network, generator, mob grinder, stabilized spawner, draconium chest, disenchanter, the dislocator portal blocks, the energy-link modules of the crystal network (the flux gate and the reactor stay)
    'draconicevolution:basic_io_crystal', 'draconicevolution:basic_relay_crystal',
    'draconicevolution:basic_wireless_crystal', 'draconicevolution:crystal_binder', 'draconicevolution:disenchanter',
    'draconicevolution:dislocator_pedestal', 'draconicevolution:dislocator_receptacle',
    'draconicevolution:draconic_io_crystal', 'draconicevolution:draconic_relay_crystal',
    'draconicevolution:draconic_wireless_crystal', 'draconicevolution:draconium_chest',
    'draconicevolution:energy_core', 'draconicevolution:energy_core_stabilizer', 'draconicevolution:energy_pylon',
    'draconicevolution:generator', 'draconicevolution:grinder', 'draconicevolution:item_chaotic_energy_link',
    'draconicevolution:item_draconic_energy_link', 'draconicevolution:item_wyvern_energy_link',
    'draconicevolution:stabilized_spawner', 'draconicevolution:wyvern_io_crystal',
    'draconicevolution:wyvern_relay_crystal', 'draconicevolution:wyvern_wireless_crystal',
    // Ad Astra: Compressor, Etrionic Blast Furnace, Energizer, Coal Generator, Solar Panel, Fuel Refinery, its iron and steel plates, rods and steel
    'ad_astra:coal_generator', 'ad_astra:compressor', 'ad_astra:energizer', 'ad_astra:etrionic_blast_furnace',
    'ad_astra:fuel_refinery', 'ad_astra:iron_plate', 'ad_astra:iron_rod', 'ad_astra:solar_panel',
    'ad_astra:steel_ingot', 'ad_astra:steel_nugget', 'ad_astra:steel_plate', 'ad_astra:steel_rod',
    // Stargate Journey: the naquadah generator
    'sgjourney:naquadah_generator_core', 'sgjourney:naquadah_generator_mark_i',
    'sgjourney:naquadah_generator_mark_ii',
    // Mystical Agriculture and Agradditions: every tool, weapon, armour piece, paxel and augment (Doc 08 section 4.4)
    'mysticalagradditions:awakened_supremium_paxel', 'mysticalagradditions:imperium_paxel',
    'mysticalagradditions:inferium_paxel', 'mysticalagradditions:prudentium_paxel',
    'mysticalagradditions:supremium_paxel', 'mysticalagradditions:tertium_paxel',
    'mysticalagriculture:absorption_i_augment', 'mysticalagriculture:absorption_ii_augment',
    'mysticalagriculture:absorption_iii_augment', 'mysticalagriculture:absorption_iv_augment',
    'mysticalagriculture:absorption_v_augment', 'mysticalagriculture:attack_aoe_i_augment',
    'mysticalagriculture:attack_aoe_ii_augment', 'mysticalagriculture:attack_aoe_iii_augment',
    'mysticalagriculture:awakened_supremium_axe', 'mysticalagriculture:awakened_supremium_boots',
    'mysticalagriculture:awakened_supremium_bow', 'mysticalagriculture:awakened_supremium_chestplate',
    'mysticalagriculture:awakened_supremium_crossbow', 'mysticalagriculture:awakened_supremium_fishing_rod',
    'mysticalagriculture:awakened_supremium_helmet', 'mysticalagriculture:awakened_supremium_hoe',
    'mysticalagriculture:awakened_supremium_leggings', 'mysticalagriculture:awakened_supremium_pickaxe',
    'mysticalagriculture:awakened_supremium_scythe', 'mysticalagriculture:awakened_supremium_shears',
    'mysticalagriculture:awakened_supremium_shovel', 'mysticalagriculture:awakened_supremium_sickle',
    'mysticalagriculture:awakened_supremium_sword', 'mysticalagriculture:blindness_resistance_augment',
    'mysticalagriculture:diamond_scythe', 'mysticalagriculture:diamond_sickle',
    'mysticalagriculture:fire_resistance_augment', 'mysticalagriculture:flight_augment',
    'mysticalagriculture:haste_i_augment', 'mysticalagriculture:haste_ii_augment',
    'mysticalagriculture:haste_iii_augment', 'mysticalagriculture:health_boost_i_augment',
    'mysticalagriculture:health_boost_ii_augment', 'mysticalagriculture:health_boost_iii_augment',
    'mysticalagriculture:health_boost_iv_augment', 'mysticalagriculture:health_boost_v_augment',
    'mysticalagriculture:hunger_resistance_augment', 'mysticalagriculture:imperium_axe',
    'mysticalagriculture:imperium_boots', 'mysticalagriculture:imperium_bow',
    'mysticalagriculture:imperium_chestplate', 'mysticalagriculture:imperium_crossbow',
    'mysticalagriculture:imperium_fishing_rod', 'mysticalagriculture:imperium_helmet',
    'mysticalagriculture:imperium_hoe', 'mysticalagriculture:imperium_leggings',
    'mysticalagriculture:imperium_pickaxe', 'mysticalagriculture:imperium_scythe',
    'mysticalagriculture:imperium_shears', 'mysticalagriculture:imperium_shovel',
    'mysticalagriculture:imperium_sickle', 'mysticalagriculture:imperium_sword', 'mysticalagriculture:inferium_axe',
    'mysticalagriculture:inferium_boots', 'mysticalagriculture:inferium_bow',
    'mysticalagriculture:inferium_chestplate', 'mysticalagriculture:inferium_crossbow',
    'mysticalagriculture:inferium_fishing_rod', 'mysticalagriculture:inferium_helmet',
    'mysticalagriculture:inferium_hoe', 'mysticalagriculture:inferium_leggings',
    'mysticalagriculture:inferium_pickaxe', 'mysticalagriculture:inferium_scythe',
    'mysticalagriculture:inferium_shears', 'mysticalagriculture:inferium_shovel',
    'mysticalagriculture:inferium_sickle', 'mysticalagriculture:inferium_sword',
    'mysticalagriculture:jump_boost_i_augment', 'mysticalagriculture:jump_boost_ii_augment',
    'mysticalagriculture:jump_boost_iii_augment', 'mysticalagriculture:luck_i_augment',
    'mysticalagriculture:luck_ii_augment', 'mysticalagriculture:luck_iii_augment',
    'mysticalagriculture:mining_aoe_i_augment', 'mysticalagriculture:mining_aoe_ii_augment',
    'mysticalagriculture:mining_aoe_iii_augment', 'mysticalagriculture:mining_aoe_iv_augment',
    'mysticalagriculture:mining_fatigue_resistance_augment', 'mysticalagriculture:nausea_resistance_augment',
    'mysticalagriculture:night_vision_augment', 'mysticalagriculture:no_fall_damage_augment',
    'mysticalagriculture:pathing_aoe_i_augment', 'mysticalagriculture:pathing_aoe_ii_augment',
    'mysticalagriculture:pathing_aoe_iii_augment', 'mysticalagriculture:pathing_aoe_iv_augment',
    'mysticalagriculture:poison_resistance_augment', 'mysticalagriculture:prudentium_axe',
    'mysticalagriculture:prudentium_boots', 'mysticalagriculture:prudentium_bow',
    'mysticalagriculture:prudentium_chestplate', 'mysticalagriculture:prudentium_crossbow',
    'mysticalagriculture:prudentium_fishing_rod', 'mysticalagriculture:prudentium_helmet',
    'mysticalagriculture:prudentium_hoe', 'mysticalagriculture:prudentium_leggings',
    'mysticalagriculture:prudentium_pickaxe', 'mysticalagriculture:prudentium_scythe',
    'mysticalagriculture:prudentium_shears', 'mysticalagriculture:prudentium_shovel',
    'mysticalagriculture:prudentium_sickle', 'mysticalagriculture:prudentium_sword',
    'mysticalagriculture:slow_falling_augment', 'mysticalagriculture:slowness_resistance_augment',
    'mysticalagriculture:speed_i_augment', 'mysticalagriculture:speed_ii_augment',
    'mysticalagriculture:speed_iii_augment', 'mysticalagriculture:step_assist_augment',
    'mysticalagriculture:strength_i_augment', 'mysticalagriculture:strength_ii_augment',
    'mysticalagriculture:strength_iii_augment', 'mysticalagriculture:supremium_axe',
    'mysticalagriculture:supremium_boots', 'mysticalagriculture:supremium_bow',
    'mysticalagriculture:supremium_chestplate', 'mysticalagriculture:supremium_crossbow',
    'mysticalagriculture:supremium_fishing_rod', 'mysticalagriculture:supremium_helmet',
    'mysticalagriculture:supremium_hoe', 'mysticalagriculture:supremium_leggings',
    'mysticalagriculture:supremium_pickaxe', 'mysticalagriculture:supremium_scythe',
    'mysticalagriculture:supremium_shears', 'mysticalagriculture:supremium_shovel',
    'mysticalagriculture:supremium_sickle', 'mysticalagriculture:supremium_sword', 'mysticalagriculture:tertium_axe',
    'mysticalagriculture:tertium_boots', 'mysticalagriculture:tertium_bow', 'mysticalagriculture:tertium_chestplate',
    'mysticalagriculture:tertium_crossbow', 'mysticalagriculture:tertium_fishing_rod',
    'mysticalagriculture:tertium_helmet', 'mysticalagriculture:tertium_hoe', 'mysticalagriculture:tertium_leggings',
    'mysticalagriculture:tertium_pickaxe', 'mysticalagriculture:tertium_scythe', 'mysticalagriculture:tertium_shears',
    'mysticalagriculture:tertium_shovel', 'mysticalagriculture:tertium_sickle', 'mysticalagriculture:tertium_sword',
    'mysticalagriculture:tilling_aoe_i_augment', 'mysticalagriculture:tilling_aoe_ii_augment',
    'mysticalagriculture:tilling_aoe_iii_augment', 'mysticalagriculture:tilling_aoe_iv_augment',
    'mysticalagriculture:unattuned_augment', 'mysticalagriculture:water_breathing_augment',
    'mysticalagriculture:weakness_resistance_augment', 'mysticalagriculture:wither_resistance_augment',
    // Mystical Agriculture soulium daggers (the dagger eats a golden sword, the tiers eat the dagger)
    'mysticalagriculture:creative_soulium_dagger', 'mysticalagriculture:hostile_soulium_dagger', 'mysticalagriculture:passive_soulium_dagger', 'mysticalagriculture:soulium_dagger',
    // One steel slab: the TerraFirmaCraft steel slab and its weathered stages (the Immersive Engineering slab stays)
    'tfc:metal/block/steel_slab', 'tfc:metal/exposed_block/steel_slab', 'tfc:metal/weathered_block/steel_slab',
    'tfc:metal/oxidized_block/steel_slab',
    // Immersive Engineering Resonanz Observer (chunk loader multiblock): chunk loading is FTB Chunks
    'immersiveengineering:chunk_loader',
    // Immersive Engineering shaders and shader grab bags (Felix 2026-10-14: removed completely; sources closed in
    // kubejs/data/immersiveengineering/, see dev/age_map.toml [hidden])
    'immersiveengineering:shader_bag_common', 'immersiveengineering:shader_bag_epic',
    'immersiveengineering:shader_bag_immersiveengineering_masterwork', 'immersiveengineering:shader_bag_rare',
    'immersiveengineering:shader_bag_uncommon', 'immersiveengineering:shader_acepride',
    'immersiveengineering:shader_ancient', 'immersiveengineering:shader_angelsthesis',
    'immersiveengineering:shader_argo', 'immersiveengineering:shader_astartes',
    'immersiveengineering:shader_bipride', 'immersiveengineering:shader_chloris',
    'immersiveengineering:shader_crescentrose', 'immersiveengineering:shader_crimsonlotus',
    'immersiveengineering:shader_darkfire', 'immersiveengineering:shader_dominator',
    'immersiveengineering:shader_dragonsbreath', 'immersiveengineering:shader_enbypride',
    'immersiveengineering:shader_erruption', 'immersiveengineering:shader_exia', 'immersiveengineering:shader_eyas',
    'immersiveengineering:shader_felix', 'immersiveengineering:shader_fox', 'immersiveengineering:shader_gaypride',
    'immersiveengineering:shader_glacis', 'immersiveengineering:shader_harrowed', 'immersiveengineering:shader_hawk',
    'immersiveengineering:shader_hollow', 'immersiveengineering:shader_ikelos',
    'immersiveengineering:shader_kindled', 'immersiveengineering:shader_lesbianpride',
    'immersiveengineering:shader_locus', 'immersiveengineering:shader_lususnaturae',
    'immersiveengineering:shader_magnum', 'immersiveengineering:shader_massfusion',
    'immersiveengineering:shader_matrix', 'immersiveengineering:shader_microshark',
    'immersiveengineering:shader_milo', 'immersiveengineering:shader_netherforged',
    'immersiveengineering:shader_normandy', 'immersiveengineering:shader_omnitool',
    'immersiveengineering:shader_phoenix', 'immersiveengineering:shader_qrow', 'immersiveengineering:shader_radiant',
    'immersiveengineering:shader_regal', 'immersiveengineering:shader_rosequartz',
    'immersiveengineering:shader_sharkface', 'immersiveengineering:shader_sponsor',
    'immersiveengineering:shader_stormflower', 'immersiveengineering:shader_sunstrike',
    'immersiveengineering:shader_sutherland', 'immersiveengineering:shader_taken',
    'immersiveengineering:shader_transpride', 'immersiveengineering:shader_trident',
    'immersiveengineering:shader_twili', 'immersiveengineering:shader_usurper',
    'immersiveengineering:shader_vanguard', 'immersiveengineering:shader_vaulttec',
    'immersiveengineering:shader_waaagh', 'immersiveengineering:shader_warbird',
    // Exposure: the Interplanar Projector loads pictures from a URL or the player's disk (n7-world; projecting is off
    // in defaultconfigs/exposure-server.toml)
    'exposure:interplanar_projector', 'exposure:broken_interplanar_projector',
    // 1.0 round C (vorauswahl 2026-10-15): the hidden parts of Artifacts, Ars Energistique, Building Gadgets 2, AeroEngine,
    // Draconic Additions and Alex's Mobs Continued; same list as [hidden] in dev/age_map.toml
    'artifacts:eternal_steak', 'artifacts:everlasting_beef', 'artifacts:pickaxe_heater', 'artifacts:digging_claws',
    'artifacts:rooted_boots', 'artifacts:chorus_totem', 'artifacts:helium_flamingo',
    'artifacts:universal_attractor', 'arseng:source_acceptor', 'arseng:cable_source_acceptor',
    'buildinggadgets2:gadget_copy_paste', 'buildinggadgets2:gadget_cut_paste', 'buildinggadgets2:template_manager',
    'buildinggadgets2:template', 'buildinggadgets2:redprint', 'aeroengineering:air_to_air_missile',
    'aeroengineering:missile_assembly_station', 'aeroengineering:missile_thruster',
    'draconicadditions:chaos_container', 'draconicadditions:chaos_crystalizer', 'draconicadditions:chaos_extractor',
    'draconicadditions:chaos_heart', 'draconicadditions:chaos_infuser', 'draconicadditions:chaos_liquifier',
    'draconicadditions:fake_reactor_core', 'draconicadditions:fake_reactor_injector',
    'draconicadditions:fake_reactor_stabilizer', 'draconicadditions:hermal', 'draconicadditions:inert_potato_boots',
    'draconicadditions:inert_potato_chest', 'draconicadditions:inert_potato_helm',
    'draconicadditions:inert_potato_legs', 'draconicadditions:infused_potato_boots',
    'draconicadditions:infused_potato_chest', 'draconicadditions:infused_potato_helm',
    'draconicadditions:infused_potato_legs', 'draconicadditions:item_chaos_injector',
    'draconicadditions:item_chaotic_auto_feed', 'draconicadditions:item_semi_stable_chaos',
    'draconicadditions:item_stable_chaos', 'draconicadditions:item_unstable_chaos', 'alexsmobs:acacia_blossom',
    'alexsmobs:ambergris', 'alexsmobs:ancient_dart', 'alexsmobs:banana', 'alexsmobs:banana_peel',
    'alexsmobs:banana_slug_slime', 'alexsmobs:banana_slug_slime_block', 'alexsmobs:banner_pattern_australia_0',
    'alexsmobs:banner_pattern_australia_1', 'alexsmobs:banner_pattern_bear', 'alexsmobs:banner_pattern_brazil',
    'alexsmobs:banner_pattern_new_mexico', 'alexsmobs:bear_dust', 'alexsmobs:bear_fur', 'alexsmobs:bison_carpet',
    'alexsmobs:bison_fur', 'alexsmobs:bison_fur_block', 'alexsmobs:blobfish', 'alexsmobs:blobfish_bucket',
    'alexsmobs:blood_sac', 'alexsmobs:blood_sprayer', 'alexsmobs:bone_serpent_tooth',
    'alexsmobs:cachalot_whale_tooth', 'alexsmobs:caiman_egg', 'alexsmobs:capsid', 'alexsmobs:centipede_leg',
    'alexsmobs:centipede_leggings', 'alexsmobs:chorus_on_a_stick', 'alexsmobs:cockroach_ootheca',
    'alexsmobs:cockroach_wing', 'alexsmobs:cockroach_wing_fragment', 'alexsmobs:comb_jelly_bucket',
    'alexsmobs:cooked_catfish', 'alexsmobs:cooked_lobster_tail', 'alexsmobs:cooked_moose_ribs',
    'alexsmobs:cosmic_cod', 'alexsmobs:cosmic_cod_bucket', 'alexsmobs:crocodile_chestplate',
    'alexsmobs:crocodile_egg', 'alexsmobs:crocodile_scute', 'alexsmobs:crystalized_banana_slug_mucus',
    'alexsmobs:devils_hole_pupfish_bucket', 'alexsmobs:dimensional_carver', 'alexsmobs:dimensional_carver_shard_0',
    'alexsmobs:dimensional_carver_shard_1', 'alexsmobs:dimensional_carver_shard_10',
    'alexsmobs:dimensional_carver_shard_2', 'alexsmobs:dimensional_carver_shard_3',
    'alexsmobs:dimensional_carver_shard_4', 'alexsmobs:dimensional_carver_shard_5',
    'alexsmobs:dimensional_carver_shard_6', 'alexsmobs:dimensional_carver_shard_7',
    'alexsmobs:dimensional_carver_shard_8', 'alexsmobs:dimensional_carver_shard_9', 'alexsmobs:dropbear_claw',
    'alexsmobs:echolocator', 'alexsmobs:effect_item', 'alexsmobs:elastic_tendon', 'alexsmobs:ender_residue',
    'alexsmobs:enderiophage_rocket', 'alexsmobs:endolocator', 'alexsmobs:falconry_glove',
    'alexsmobs:falconry_glove_hand', 'alexsmobs:falconry_glove_inventory', 'alexsmobs:falconry_hood',
    'alexsmobs:fancy_item', 'alexsmobs:farseer_arm', 'alexsmobs:fedora', 'alexsmobs:fish_bones',
    'alexsmobs:fish_oil', 'alexsmobs:frilled_shark_bucket', 'alexsmobs:froststalker_helmet',
    'alexsmobs:froststalker_horn', 'alexsmobs:gazelle_horn', 'alexsmobs:ghostly_pickaxe', 'alexsmobs:gongylidia',
    'alexsmobs:guster_eye', 'alexsmobs:gustmaker', 'alexsmobs:halo', 'alexsmobs:hemolymph_blaster',
    'alexsmobs:hemolymph_sac', 'alexsmobs:kangaroo_burger', 'alexsmobs:komodo_spit', 'alexsmobs:komodo_spit_bottle',
    'alexsmobs:large_catfish_bucket', 'alexsmobs:lava_bottle', 'alexsmobs:leafcutter_ant_chamber',
    'alexsmobs:leafcutter_ant_pupa', 'alexsmobs:leafcutter_anthill', 'alexsmobs:lobster_bucket',
    'alexsmobs:lobster_tail', 'alexsmobs:lost_tentacle', 'alexsmobs:maggot', 'alexsmobs:medium_catfish_bucket',
    'alexsmobs:mimic_octopus_bucket', 'alexsmobs:mimicream', 'alexsmobs:moose_antler', 'alexsmobs:moose_headgear',
    'alexsmobs:moose_ribs', 'alexsmobs:mosquito_larva', 'alexsmobs:mosquito_proboscis',
    'alexsmobs:mosquito_repellent_stew', 'alexsmobs:mungal_spores', 'alexsmobs:music_disc_daze',
    'alexsmobs:music_disc_thime', 'alexsmobs:mysterious_worm', 'alexsmobs:novelty_hat', 'alexsmobs:pigshoes',
    'alexsmobs:platypus_egg', 'alexsmobs:pocket_sand', 'alexsmobs:poison_bottle', 'alexsmobs:potted_flutter',
    'alexsmobs:pupfish_locator', 'alexsmobs:rainbow_glass', 'alexsmobs:rainbow_jelly', 'alexsmobs:raw_catfish',
    'alexsmobs:red_sand_circle', 'alexsmobs:rocky_chestplate', 'alexsmobs:rocky_shell', 'alexsmobs:sand_circle',
    'alexsmobs:sculk_boomer', 'alexsmobs:serrated_shark_tooth', 'alexsmobs:shark_tooth',
    'alexsmobs:shark_tooth_arrow', 'alexsmobs:shattered_dimensional_carver', 'alexsmobs:shed_snake_skin',
    'alexsmobs:shield_of_the_deep', 'alexsmobs:shrimp_fried_rice', 'alexsmobs:skelewag_sword',
    'alexsmobs:skelewag_sword_hand', 'alexsmobs:skelewag_sword_inventory', 'alexsmobs:skreecher_soul',
    'alexsmobs:small_catfish_bucket', 'alexsmobs:sombrero', 'alexsmobs:sopa_de_macaco', 'alexsmobs:soul_heart',
    'alexsmobs:spawn_egg_alligator_snapping_turtle', 'alexsmobs:spawn_egg_anaconda',
    'alexsmobs:spawn_egg_bald_eagle', 'alexsmobs:spawn_egg_banana_slug', 'alexsmobs:spawn_egg_bison',
    'alexsmobs:spawn_egg_blobfish', 'alexsmobs:spawn_egg_bone_serpent', 'alexsmobs:spawn_egg_bunfungus',
    'alexsmobs:spawn_egg_cachalot_whale', 'alexsmobs:spawn_egg_caiman', 'alexsmobs:spawn_egg_catfish',
    'alexsmobs:spawn_egg_centipede', 'alexsmobs:spawn_egg_cockroach', 'alexsmobs:spawn_egg_comb_jelly',
    'alexsmobs:spawn_egg_cosmaw', 'alexsmobs:spawn_egg_cosmic_cod', 'alexsmobs:spawn_egg_crimson_mosquito',
    'alexsmobs:spawn_egg_crocodile', 'alexsmobs:spawn_egg_crow', 'alexsmobs:spawn_egg_devils_hole_pupfish',
    'alexsmobs:spawn_egg_dropbear', 'alexsmobs:spawn_egg_endergrade', 'alexsmobs:spawn_egg_enderiophage',
    'alexsmobs:spawn_egg_farseer', 'alexsmobs:spawn_egg_flutter', 'alexsmobs:spawn_egg_fly',
    'alexsmobs:spawn_egg_frilled_shark', 'alexsmobs:spawn_egg_froststalker', 'alexsmobs:spawn_egg_gazelle',
    'alexsmobs:spawn_egg_giant_squid', 'alexsmobs:spawn_egg_grizzly_bear', 'alexsmobs:spawn_egg_guster',
    'alexsmobs:spawn_egg_hammerhead_shark', 'alexsmobs:spawn_egg_jerboa', 'alexsmobs:spawn_egg_laviathan',
    'alexsmobs:spawn_egg_leafcutter_ant', 'alexsmobs:spawn_egg_lobster', 'alexsmobs:spawn_egg_maned_wolf',
    'alexsmobs:spawn_egg_mantis_shrimp', 'alexsmobs:spawn_egg_mimic_octopus', 'alexsmobs:spawn_egg_mimicube',
    'alexsmobs:spawn_egg_moose', 'alexsmobs:spawn_egg_mungus', 'alexsmobs:spawn_egg_murmur',
    'alexsmobs:spawn_egg_orca', 'alexsmobs:spawn_egg_rain_frog', 'alexsmobs:spawn_egg_rocky_roller',
    'alexsmobs:spawn_egg_seagull', 'alexsmobs:spawn_egg_seal', 'alexsmobs:spawn_egg_skelewag',
    'alexsmobs:spawn_egg_skreecher', 'alexsmobs:spawn_egg_skunk', 'alexsmobs:spawn_egg_snow_leopard',
    'alexsmobs:spawn_egg_soul_vulture', 'alexsmobs:spawn_egg_spectre', 'alexsmobs:spawn_egg_straddler',
    'alexsmobs:spawn_egg_stradpole', 'alexsmobs:spawn_egg_sunbird', 'alexsmobs:spawn_egg_tarantula_hawk',
    'alexsmobs:spawn_egg_terrapin', 'alexsmobs:spawn_egg_tiger', 'alexsmobs:spawn_egg_triops',
    'alexsmobs:spawn_egg_tusklin', 'alexsmobs:spawn_egg_underminer', 'alexsmobs:spawn_egg_void_worm',
    'alexsmobs:spawn_egg_warped_mosco', 'alexsmobs:spawn_egg_warped_toad', 'alexsmobs:spiked_scute',
    'alexsmobs:spiked_turtle_shell', 'alexsmobs:squid_grapple', 'alexsmobs:stink_bottle', 'alexsmobs:stink_ray',
    'alexsmobs:stink_ray_empty_hand', 'alexsmobs:stink_ray_empty_inventory', 'alexsmobs:stink_ray_hand',
    'alexsmobs:stink_ray_inventory', 'alexsmobs:straddle_helmet', 'alexsmobs:straddle_saddle',
    'alexsmobs:straddleboard', 'alexsmobs:straddlite', 'alexsmobs:straddlite_block', 'alexsmobs:stradpole_bucket',
    'alexsmobs:tab_icon', 'alexsmobs:tarantula_hawk_elytra', 'alexsmobs:tarantula_hawk_wing',
    'alexsmobs:tarantula_hawk_wing_fragment', 'alexsmobs:tendon_whip', 'alexsmobs:terrapin_bucket',
    'alexsmobs:terrapin_egg', 'alexsmobs:transmutation_table', 'alexsmobs:triops_bucket', 'alexsmobs:triops_eggs',
    'alexsmobs:unsettling_kimono', 'alexsmobs:vine_lasso', 'alexsmobs:vine_lasso_hand',
    'alexsmobs:vine_lasso_inventory', 'alexsmobs:void_worm_beak', 'alexsmobs:void_worm_effigy',
    'alexsmobs:void_worm_eye', 'alexsmobs:void_worm_mandible', 'alexsmobs:warped_mixture', 'alexsmobs:warped_muscle',
    // Round G2: firmages-core structure blocks with an item for the creative pick-block only (they keep their block's
    // Age in dev/age_map.toml, so Jade still names the blocks; SPEC 24.8).
    'firmages:firmament_crystal_cluster', 'firmages:firmament_lode', 'firmages:wayshrine_core'
  ]
}
// Only plain data goes into `global`: functions created in the startup context are not reliably callable
// from server or client scripts, so each script defines its own helpers.

// >>> generated:wood_classes >>>
// Wood classes (generated by dev/gen_wood_classes.py from dev/wood_classes.toml; do not edit).
global.FA.WOOD_CLASSES = {
  'classes': {
    'oak': {
      'nether': false,
      'vanilla': {
        'log': 'minecraft:oak_log',
        'wood': 'minecraft:oak_wood',
        'stripped_log': 'minecraft:stripped_oak_log',
        'stripped_wood': 'minecraft:stripped_oak_wood',
        'planks': 'minecraft:oak_planks',
        'slab': 'minecraft:oak_slab',
        'stairs': 'minecraft:oak_stairs',
        'sapling': 'minecraft:oak_sapling',
        'logs_tag': 'minecraft:oak_logs'
      },
      'lead': {
        'id': 'tfc:oak',
        'logs_tag': 'tfc:oak_logs',
        'log': 'tfc:wood/log/oak',
        'stripped_log': 'tfc:wood/stripped_log/oak',
        'planks': 'tfc:wood/planks/oak',
        'slab': 'tfc:wood/planks/oak_slab',
        'stairs': 'tfc:wood/planks/oak_stairs',
        'lumber': 'tfc:wood/lumber/oak'
      },
      'species': [
        {
          'id': 'tfc:oak',
          'log': 'tfc:wood/log/oak',
          'wood': 'tfc:wood/wood/oak',
          'sapling': 'tfc:wood/sapling/oak',
          'variant': false,
          'planks': 'tfc:wood/planks/oak'
        },
        {
          'id': 'tfc:ash',
          'log': 'tfc:wood/log/ash',
          'wood': 'tfc:wood/wood/ash',
          'sapling': 'tfc:wood/sapling/ash',
          'variant': false,
          'planks': 'tfc:wood/planks/ash'
        },
        {
          'id': 'tfc:maple',
          'log': 'tfc:wood/log/maple',
          'wood': 'tfc:wood/wood/maple',
          'sapling': 'tfc:wood/sapling/maple',
          'variant': false,
          'planks': 'tfc:wood/planks/maple'
        },
        {
          'id': 'tfc:sycamore',
          'log': 'tfc:wood/log/sycamore',
          'wood': 'tfc:wood/wood/sycamore',
          'sapling': 'tfc:wood/sapling/sycamore',
          'variant': false,
          'planks': 'tfc:wood/planks/sycamore'
        },
        {
          'id': 'afc:beech',
          'log': 'afc:wood/log/beech',
          'wood': 'afc:wood/wood/beech',
          'sapling': 'afc:wood/sapling/beech',
          'variant': false,
          'planks': 'afc:wood/planks/beech'
        },
        {
          'id': 'afc:black_oak',
          'log': 'afc:wood/log/black_oak',
          'wood': 'afc:wood/wood/black_oak',
          'sapling': 'afc:wood/sapling/black_oak',
          'variant': true
        }
      ]
    },
    'birch': {
      'nether': false,
      'vanilla': {
        'log': 'minecraft:birch_log',
        'wood': 'minecraft:birch_wood',
        'stripped_log': 'minecraft:stripped_birch_log',
        'stripped_wood': 'minecraft:stripped_birch_wood',
        'planks': 'minecraft:birch_planks',
        'slab': 'minecraft:birch_slab',
        'stairs': 'minecraft:birch_stairs',
        'sapling': 'minecraft:birch_sapling',
        'logs_tag': 'minecraft:birch_logs'
      },
      'lead': {
        'id': 'tfc:birch',
        'logs_tag': 'tfc:birch_logs',
        'log': 'tfc:wood/log/birch',
        'stripped_log': 'tfc:wood/stripped_log/birch',
        'planks': 'tfc:wood/planks/birch',
        'slab': 'tfc:wood/planks/birch_slab',
        'stairs': 'tfc:wood/planks/birch_stairs',
        'lumber': 'tfc:wood/lumber/birch'
      },
      'species': [
        {
          'id': 'tfc:birch',
          'log': 'tfc:wood/log/birch',
          'wood': 'tfc:wood/wood/birch',
          'sapling': 'tfc:wood/sapling/birch',
          'variant': false,
          'planks': 'tfc:wood/planks/birch'
        },
        {
          'id': 'tfc:aspen',
          'log': 'tfc:wood/log/aspen',
          'wood': 'tfc:wood/wood/aspen',
          'sapling': 'tfc:wood/sapling/aspen',
          'variant': false,
          'planks': 'tfc:wood/planks/aspen'
        },
        {
          'id': 'afc:eucalyptus',
          'log': 'afc:wood/log/eucalyptus',
          'wood': 'afc:wood/wood/eucalyptus',
          'sapling': 'afc:wood/sapling/eucalyptus',
          'variant': false,
          'planks': 'afc:wood/planks/eucalyptus'
        },
        {
          'id': 'afc:ginkgo',
          'log': 'afc:wood/log/ginkgo',
          'wood': 'afc:wood/wood/ginkgo',
          'sapling': 'afc:wood/sapling/ginkgo',
          'variant': false,
          'planks': 'afc:wood/planks/ginkgo'
        },
        {
          'id': 'afc:poplar',
          'log': 'afc:wood/log/poplar',
          'wood': 'afc:wood/wood/poplar',
          'sapling': 'afc:wood/sapling/poplar',
          'variant': true
        },
        {
          'id': 'afc:rainbow_eucalyptus',
          'log': 'afc:wood/log/rainbow_eucalyptus',
          'wood': 'afc:wood/wood/rainbow_eucalyptus',
          'sapling': 'afc:wood/sapling/rainbow_eucalyptus',
          'variant': true
        }
      ]
    },
    'spruce': {
      'nether': false,
      'vanilla': {
        'log': 'minecraft:spruce_log',
        'wood': 'minecraft:spruce_wood',
        'stripped_log': 'minecraft:stripped_spruce_log',
        'stripped_wood': 'minecraft:stripped_spruce_wood',
        'planks': 'minecraft:spruce_planks',
        'slab': 'minecraft:spruce_slab',
        'stairs': 'minecraft:spruce_stairs',
        'sapling': 'minecraft:spruce_sapling',
        'logs_tag': 'minecraft:spruce_logs'
      },
      'lead': {
        'id': 'tfc:spruce',
        'logs_tag': 'tfc:spruce_logs',
        'log': 'tfc:wood/log/spruce',
        'stripped_log': 'tfc:wood/stripped_log/spruce',
        'planks': 'tfc:wood/planks/spruce',
        'slab': 'tfc:wood/planks/spruce_slab',
        'stairs': 'tfc:wood/planks/spruce_stairs',
        'lumber': 'tfc:wood/lumber/spruce'
      },
      'species': [
        {
          'id': 'tfc:spruce',
          'log': 'tfc:wood/log/spruce',
          'wood': 'tfc:wood/wood/spruce',
          'sapling': 'tfc:wood/sapling/spruce',
          'variant': false,
          'planks': 'tfc:wood/planks/spruce'
        },
        {
          'id': 'tfc:pine',
          'log': 'tfc:wood/log/pine',
          'wood': 'tfc:wood/wood/pine',
          'sapling': 'tfc:wood/sapling/pine',
          'variant': false,
          'planks': 'tfc:wood/planks/pine'
        },
        {
          'id': 'tfc:douglas_fir',
          'log': 'tfc:wood/log/douglas_fir',
          'wood': 'tfc:wood/wood/douglas_fir',
          'sapling': 'tfc:wood/sapling/douglas_fir',
          'variant': false,
          'planks': 'tfc:wood/planks/douglas_fir'
        },
        {
          'id': 'tfc:white_cedar',
          'log': 'tfc:wood/log/white_cedar',
          'wood': 'tfc:wood/wood/white_cedar',
          'sapling': 'tfc:wood/sapling/white_cedar',
          'variant': false,
          'planks': 'tfc:wood/planks/white_cedar'
        },
        {
          'id': 'tfc:sequoia',
          'log': 'tfc:wood/log/sequoia',
          'wood': 'tfc:wood/wood/sequoia',
          'sapling': 'tfc:wood/sapling/sequoia',
          'variant': false,
          'planks': 'tfc:wood/planks/sequoia'
        },
        {
          'id': 'afc:cypress',
          'log': 'afc:wood/log/cypress',
          'wood': 'afc:wood/wood/cypress',
          'sapling': 'afc:wood/sapling/cypress',
          'variant': false,
          'planks': 'afc:wood/planks/cypress'
        },
        {
          'id': 'afc:araucaria',
          'log': 'afc:wood/log/araucaria',
          'wood': 'afc:wood/wood/araucaria',
          'sapling': 'afc:wood/sapling/araucaria',
          'variant': false,
          'planks': 'afc:wood/planks/araucaria'
        },
        {
          'id': 'afc:redcedar',
          'log': 'afc:wood/log/redcedar',
          'wood': 'afc:wood/wood/redcedar',
          'sapling': 'afc:wood/sapling/redcedar',
          'variant': true
        },
        {
          'id': 'afc:kauri',
          'log': 'afc:wood/log/kauri',
          'wood': 'afc:wood/wood/kauri',
          'sapling': 'afc:wood/sapling/kauri',
          'variant': true
        }
      ]
    },
    'jungle': {
      'nether': false,
      'vanilla': {
        'log': 'minecraft:jungle_log',
        'wood': 'minecraft:jungle_wood',
        'stripped_log': 'minecraft:stripped_jungle_log',
        'stripped_wood': 'minecraft:stripped_jungle_wood',
        'planks': 'minecraft:jungle_planks',
        'slab': 'minecraft:jungle_slab',
        'stairs': 'minecraft:jungle_stairs',
        'sapling': 'minecraft:jungle_sapling',
        'logs_tag': 'minecraft:jungle_logs'
      },
      'lead': {
        'id': 'afc:teak',
        'logs_tag': 'afc:teak_logs',
        'log': 'afc:wood/log/teak',
        'stripped_log': 'afc:wood/stripped_log/teak',
        'planks': 'afc:wood/planks/teak',
        'slab': 'afc:wood/planks/teak_slab',
        'stairs': 'afc:wood/planks/teak_stairs',
        'lumber': 'afc:wood/lumber/teak'
      },
      'species': [
        {
          'id': 'tfc:kapok',
          'log': 'tfc:wood/log/kapok',
          'wood': 'tfc:wood/wood/kapok',
          'sapling': 'tfc:wood/sapling/kapok',
          'variant': false,
          'planks': 'tfc:wood/planks/kapok'
        },
        {
          'id': 'tfc:palm',
          'log': 'tfc:wood/log/palm',
          'wood': 'tfc:wood/wood/palm',
          'sapling': 'tfc:wood/sapling/palm',
          'variant': false,
          'planks': 'tfc:wood/planks/palm'
        },
        {
          'id': 'afc:teak',
          'log': 'afc:wood/log/teak',
          'wood': 'afc:wood/wood/teak',
          'sapling': 'afc:wood/sapling/teak',
          'variant': false,
          'planks': 'afc:wood/planks/teak'
        },
        {
          'id': 'afc:hevea',
          'log': 'afc:wood/log/hevea',
          'wood': 'afc:wood/wood/hevea',
          'sapling': 'afc:wood/sapling/hevea',
          'variant': false,
          'planks': 'afc:wood/planks/hevea'
        },
        {
          'id': 'afc:tualang',
          'log': 'afc:wood/log/tualang',
          'wood': 'afc:wood/wood/tualang',
          'sapling': 'afc:wood/sapling/tualang',
          'variant': false,
          'planks': 'afc:wood/planks/tualang'
        },
        {
          'id': 'afc:mahoe',
          'log': 'afc:wood/log/mahoe',
          'wood': 'afc:wood/wood/mahoe',
          'sapling': 'afc:wood/sapling/mahoe',
          'variant': false,
          'planks': 'afc:wood/planks/mahoe'
        }
      ]
    },
    'acacia': {
      'nether': false,
      'vanilla': {
        'log': 'minecraft:acacia_log',
        'wood': 'minecraft:acacia_wood',
        'stripped_log': 'minecraft:stripped_acacia_log',
        'stripped_wood': 'minecraft:stripped_acacia_wood',
        'planks': 'minecraft:acacia_planks',
        'slab': 'minecraft:acacia_slab',
        'stairs': 'minecraft:acacia_stairs',
        'sapling': 'minecraft:acacia_sapling',
        'logs_tag': 'minecraft:acacia_logs'
      },
      'lead': {
        'id': 'tfc:acacia',
        'logs_tag': 'tfc:acacia_logs',
        'log': 'tfc:wood/log/acacia',
        'stripped_log': 'tfc:wood/stripped_log/acacia',
        'planks': 'tfc:wood/planks/acacia',
        'slab': 'tfc:wood/planks/acacia_slab',
        'stairs': 'tfc:wood/planks/acacia_stairs',
        'lumber': 'tfc:wood/lumber/acacia'
      },
      'species': [
        {
          'id': 'tfc:acacia',
          'log': 'tfc:wood/log/acacia',
          'wood': 'tfc:wood/wood/acacia',
          'sapling': 'tfc:wood/sapling/acacia',
          'variant': false,
          'planks': 'tfc:wood/planks/acacia'
        },
        {
          'id': 'afc:baobab',
          'log': 'afc:wood/log/baobab',
          'wood': 'afc:wood/wood/baobab',
          'sapling': 'afc:wood/sapling/baobab',
          'variant': false,
          'planks': 'afc:wood/planks/baobab'
        },
        {
          'id': 'afc:gum_arabic',
          'log': 'afc:wood/log/gum_arabic',
          'wood': 'afc:wood/wood/gum_arabic',
          'sapling': 'afc:wood/sapling/gum_arabic',
          'variant': true
        }
      ]
    },
    'dark_oak': {
      'nether': false,
      'vanilla': {
        'log': 'minecraft:dark_oak_log',
        'wood': 'minecraft:dark_oak_wood',
        'stripped_log': 'minecraft:stripped_dark_oak_log',
        'stripped_wood': 'minecraft:stripped_dark_oak_wood',
        'planks': 'minecraft:dark_oak_planks',
        'slab': 'minecraft:dark_oak_slab',
        'stairs': 'minecraft:dark_oak_stairs',
        'sapling': 'minecraft:dark_oak_sapling',
        'logs_tag': 'minecraft:dark_oak_logs'
      },
      'lead': {
        'id': 'tfc:hickory',
        'logs_tag': 'tfc:hickory_logs',
        'log': 'tfc:wood/log/hickory',
        'stripped_log': 'tfc:wood/stripped_log/hickory',
        'planks': 'tfc:wood/planks/hickory',
        'slab': 'tfc:wood/planks/hickory_slab',
        'stairs': 'tfc:wood/planks/hickory_stairs',
        'lumber': 'tfc:wood/lumber/hickory'
      },
      'species': [
        {
          'id': 'tfc:blackwood',
          'log': 'tfc:wood/log/blackwood',
          'wood': 'tfc:wood/wood/blackwood',
          'sapling': 'tfc:wood/sapling/blackwood',
          'variant': false,
          'planks': 'tfc:wood/planks/blackwood'
        },
        {
          'id': 'tfc:hickory',
          'log': 'tfc:wood/log/hickory',
          'wood': 'tfc:wood/wood/hickory',
          'sapling': 'tfc:wood/sapling/hickory',
          'variant': false,
          'planks': 'tfc:wood/planks/hickory'
        },
        {
          'id': 'tfc:chestnut',
          'log': 'tfc:wood/log/chestnut',
          'wood': 'tfc:wood/wood/chestnut',
          'sapling': 'tfc:wood/sapling/chestnut',
          'variant': false,
          'planks': 'tfc:wood/planks/chestnut'
        },
        {
          'id': 'afc:ironwood',
          'log': 'afc:wood/log/ironwood',
          'wood': 'afc:wood/wood/ironwood',
          'sapling': 'afc:wood/sapling/ironwood',
          'variant': false,
          'planks': 'afc:wood/planks/ironwood'
        },
        {
          'id': 'afc:ipe',
          'log': 'afc:wood/log/ipe',
          'wood': 'afc:wood/wood/ipe',
          'sapling': 'afc:wood/sapling/ipe',
          'variant': false,
          'planks': 'afc:wood/planks/ipe'
        }
      ]
    },
    'mangrove': {
      'nether': false,
      'vanilla': {
        'log': 'minecraft:mangrove_log',
        'wood': 'minecraft:mangrove_wood',
        'stripped_log': 'minecraft:stripped_mangrove_log',
        'stripped_wood': 'minecraft:stripped_mangrove_wood',
        'planks': 'minecraft:mangrove_planks',
        'slab': 'minecraft:mangrove_slab',
        'stairs': 'minecraft:mangrove_stairs',
        'sapling': 'minecraft:mangrove_propagule',
        'logs_tag': 'minecraft:mangrove_logs'
      },
      'lead': {
        'id': 'tfc:mangrove',
        'logs_tag': 'tfc:mangrove_logs',
        'log': 'tfc:wood/log/mangrove',
        'stripped_log': 'tfc:wood/stripped_log/mangrove',
        'planks': 'tfc:wood/planks/mangrove',
        'slab': 'tfc:wood/planks/mangrove_slab',
        'stairs': 'tfc:wood/planks/mangrove_stairs',
        'lumber': 'tfc:wood/lumber/mangrove'
      },
      'species': [
        {
          'id': 'tfc:mangrove',
          'log': 'tfc:wood/log/mangrove',
          'wood': 'tfc:wood/wood/mangrove',
          'sapling': 'tfc:wood/sapling/mangrove',
          'variant': false,
          'planks': 'tfc:wood/planks/mangrove'
        },
        {
          'id': 'tfc:willow',
          'log': 'tfc:wood/log/willow',
          'wood': 'tfc:wood/wood/willow',
          'sapling': 'tfc:wood/sapling/willow',
          'variant': false,
          'planks': 'tfc:wood/planks/willow'
        },
        {
          'id': 'afc:mahogany',
          'log': 'afc:wood/log/mahogany',
          'wood': 'afc:wood/wood/mahogany',
          'sapling': 'afc:wood/sapling/mahogany',
          'variant': false,
          'planks': 'afc:wood/planks/mahogany'
        }
      ]
    },
    'cherry': {
      'nether': false,
      'vanilla': {
        'log': 'minecraft:cherry_log',
        'wood': 'minecraft:cherry_wood',
        'stripped_log': 'minecraft:stripped_cherry_log',
        'stripped_wood': 'minecraft:stripped_cherry_wood',
        'planks': 'minecraft:cherry_planks',
        'slab': 'minecraft:cherry_slab',
        'stairs': 'minecraft:cherry_stairs',
        'sapling': 'minecraft:cherry_sapling',
        'logs_tag': 'minecraft:cherry_logs'
      },
      'lead': {
        'id': 'afc:fig',
        'logs_tag': 'afc:fig_logs',
        'log': 'afc:wood/log/fig',
        'stripped_log': 'afc:wood/stripped_log/fig',
        'planks': 'afc:wood/planks/fig',
        'slab': 'afc:wood/planks/fig_slab',
        'stairs': 'afc:wood/planks/fig_stairs',
        'lumber': 'afc:wood/lumber/fig'
      },
      'species': [
        {
          'id': 'tfc:rosewood',
          'log': 'tfc:wood/log/rosewood',
          'wood': 'tfc:wood/wood/rosewood',
          'sapling': 'tfc:wood/sapling/rosewood',
          'variant': false,
          'planks': 'tfc:wood/planks/rosewood'
        },
        {
          'id': 'afc:fig',
          'log': 'afc:wood/log/fig',
          'wood': 'afc:wood/wood/fig',
          'sapling': 'afc:wood/sapling/fig',
          'variant': false,
          'planks': 'afc:wood/planks/fig'
        },
        {
          'id': 'afc:rubber_fig',
          'log': 'afc:wood/log/rubber_fig',
          'wood': 'afc:wood/wood/rubber_fig',
          'sapling': 'afc:wood/sapling/rubber_fig',
          'variant': true
        }
      ]
    },
    'crimson': {
      'nether': true,
      'vanilla': {
        'log': 'minecraft:crimson_stem',
        'wood': 'minecraft:crimson_hyphae',
        'stripped_log': 'minecraft:stripped_crimson_stem',
        'stripped_wood': 'minecraft:stripped_crimson_hyphae',
        'planks': 'minecraft:crimson_planks',
        'slab': 'minecraft:crimson_slab',
        'stairs': 'minecraft:crimson_stairs',
        'sapling': null,
        'logs_tag': 'minecraft:crimson_stems'
      },
      'lead': {
        'id': 'beneath:crimson',
        'logs_tag': 'beneath:crimson_logs',
        'log': 'beneath:wood/log/crimson',
        'stripped_log': 'beneath:wood/stripped_log/crimson',
        'planks': 'beneath:wood/planks/crimson',
        'slab': 'beneath:wood/planks/crimson_slab',
        'stairs': 'beneath:wood/planks/crimson_stairs',
        'lumber': 'beneath:wood/lumber/crimson'
      },
      'species': [
        {
          'id': 'beneath:crimson',
          'log': 'beneath:wood/log/crimson',
          'wood': 'beneath:wood/wood/crimson',
          'sapling': 'beneath:wood/sapling/crimson',
          'variant': false,
          'planks': 'beneath:wood/planks/crimson'
        }
      ]
    },
    'warped': {
      'nether': true,
      'vanilla': {
        'log': 'minecraft:warped_stem',
        'wood': 'minecraft:warped_hyphae',
        'stripped_log': 'minecraft:stripped_warped_stem',
        'stripped_wood': 'minecraft:stripped_warped_hyphae',
        'planks': 'minecraft:warped_planks',
        'slab': 'minecraft:warped_slab',
        'stairs': 'minecraft:warped_stairs',
        'sapling': null,
        'logs_tag': 'minecraft:warped_stems'
      },
      'lead': {
        'id': 'beneath:warped',
        'logs_tag': 'beneath:warped_logs',
        'log': 'beneath:wood/log/warped',
        'stripped_log': 'beneath:wood/stripped_log/warped',
        'planks': 'beneath:wood/planks/warped',
        'slab': 'beneath:wood/planks/warped_slab',
        'stairs': 'beneath:wood/planks/warped_stairs',
        'lumber': 'beneath:wood/lumber/warped'
      },
      'species': [
        {
          'id': 'beneath:warped',
          'log': 'beneath:wood/log/warped',
          'wood': 'beneath:wood/wood/warped',
          'sapling': 'beneath:wood/sapling/warped',
          'variant': false,
          'planks': 'beneath:wood/planks/warped'
        }
      ]
    }
  },
  'bamboo': [
    [
      'tfc:plant/golden_bamboo',
      'minecraft:bamboo'
    ],
    [
      'tfc:golden_bamboo_block',
      'minecraft:bamboo_block'
    ]
  ],
  'class_saplings': {
    'tfc:wood/sapling/oak': 'minecraft:oak_sapling',
    'tfc:wood/sapling/ash': 'minecraft:oak_sapling',
    'tfc:wood/sapling/maple': 'minecraft:oak_sapling',
    'tfc:wood/sapling/sycamore': 'minecraft:oak_sapling',
    'afc:wood/sapling/beech': 'minecraft:oak_sapling',
    'afc:wood/sapling/black_oak': 'minecraft:oak_sapling',
    'tfc:wood/sapling/birch': 'minecraft:birch_sapling',
    'tfc:wood/sapling/aspen': 'minecraft:birch_sapling',
    'afc:wood/sapling/eucalyptus': 'minecraft:birch_sapling',
    'afc:wood/sapling/ginkgo': 'minecraft:birch_sapling',
    'afc:wood/sapling/poplar': 'minecraft:birch_sapling',
    'afc:wood/sapling/rainbow_eucalyptus': 'minecraft:birch_sapling',
    'tfc:wood/sapling/spruce': 'minecraft:spruce_sapling',
    'tfc:wood/sapling/pine': 'minecraft:spruce_sapling',
    'tfc:wood/sapling/douglas_fir': 'minecraft:spruce_sapling',
    'tfc:wood/sapling/white_cedar': 'minecraft:spruce_sapling',
    'tfc:wood/sapling/sequoia': 'minecraft:spruce_sapling',
    'afc:wood/sapling/cypress': 'minecraft:spruce_sapling',
    'afc:wood/sapling/araucaria': 'minecraft:spruce_sapling',
    'afc:wood/sapling/redcedar': 'minecraft:spruce_sapling',
    'afc:wood/sapling/kauri': 'minecraft:spruce_sapling',
    'tfc:wood/sapling/kapok': 'minecraft:jungle_sapling',
    'tfc:wood/sapling/palm': 'minecraft:jungle_sapling',
    'afc:wood/sapling/teak': 'minecraft:jungle_sapling',
    'afc:wood/sapling/hevea': 'minecraft:jungle_sapling',
    'afc:wood/sapling/tualang': 'minecraft:jungle_sapling',
    'afc:wood/sapling/mahoe': 'minecraft:jungle_sapling',
    'tfc:wood/sapling/acacia': 'minecraft:acacia_sapling',
    'afc:wood/sapling/baobab': 'minecraft:acacia_sapling',
    'afc:wood/sapling/gum_arabic': 'minecraft:acacia_sapling',
    'tfc:wood/sapling/blackwood': 'minecraft:dark_oak_sapling',
    'tfc:wood/sapling/hickory': 'minecraft:dark_oak_sapling',
    'tfc:wood/sapling/chestnut': 'minecraft:dark_oak_sapling',
    'afc:wood/sapling/ironwood': 'minecraft:dark_oak_sapling',
    'afc:wood/sapling/ipe': 'minecraft:dark_oak_sapling',
    'tfc:wood/sapling/mangrove': 'minecraft:mangrove_propagule',
    'tfc:wood/sapling/willow': 'minecraft:mangrove_propagule',
    'afc:wood/sapling/mahogany': 'minecraft:mangrove_propagule',
    'tfc:wood/sapling/rosewood': 'minecraft:cherry_sapling',
    'afc:wood/sapling/fig': 'minecraft:cherry_sapling',
    'afc:wood/sapling/rubber_fig': 'minecraft:cherry_sapling'
  }
}
// <<< generated:wood_classes <<<
