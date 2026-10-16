// Firmament Ages - the sky road (firmages-core SPEC 24.2, 27.8, 27.9, 29.4).
// V4A (Felix): from the Arcane Age one celestial alloy per Age goes into that Age's keystone or signature recipe, so
// the Lens Crown is mandatory. The pack switches the eight signature recipes, the crown parts and the Sky Anchor over
// to the sky road only while every sky material and the station that yields it is registered: without one of them a
// later alloy (and with it a goal item) would have no source, and the main road must never wait for a feature that is
// not in the jar. The server scripts check REQUIRED with Item.exists inside the recipe event, never against the Ages, so
// the firmages-core Age swap keeps working without a script rerun.

global.FA_SKY = {
  // The one source of each sky material (SPEC 24.2) and the material itself; firmament crystal comes from the Drifting
  // Isles (world generation), so only its item is listed.
  REQUIRED: [
    'firmages:meteoric_iron_ingot', 'firmages:starsteel_ingot', // Starfall (M10)
    'firmages:storm_rod', 'firmages:storm_glass', 'firmages:aurora_net', 'firmages:aurora_light_bucket', // Sky Harvest (M11)
    'firmages:firmament_crystal', // Drifting Isles (M13)
    'firmages:launch_control', 'firmages:probe', 'firmages:sampler_module', 'firmages:deep_space_module',
    'firmages:voidglass', 'firmages:deep_space_sample' // Launch Complex (M14)
  ],

  // The alloy each V4A signature recipe takes (SPEC 27.8), by Age.
  ALLOYS: {
    age_3: 'firmages:starlit_iron_ingot',
    age_4: 'firmages:cloudsteel_ingot',
    age_5: 'firmages:aurora_steel_ingot',
    age_6: 'firmages:halo_steel_ingot',
    age_7: 'firmages:void_steel_ingot',
    age_8: 'firmages:quasar_alloy_ingot',
    age_9: 'firmages:firmament_alloy_ingot'
  }
}
