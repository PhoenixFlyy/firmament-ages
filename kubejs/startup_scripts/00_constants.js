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

  // Items that never appear in the pack (Doc 10 v3 section 7.2, scope: content that exists by the Industrial Age).
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
    'createaddition:spool', 'createaddition:straw', 'createaddition:tesla_coil', 'createaddition:zinc_sheet'
  ]
}
// Only plain data goes into `global`: functions created in the startup context are not reliably callable
// from server or client scripts, so each script defines its own helpers.
