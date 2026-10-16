// Firmament Ages - Age 7: the parts of the Launch Complex (firmages-core SPEC 31.3, 31.4; pack hand-off of M14/M15).
// The complex is the one source of voidglass (sampler flights, Space Age) and deep-space samples (deep-space flights,
// Quantum Age). Its blocks are desh, steel and Mekanism work; the probe is a small desh rocket; each flight consumes a
// module. Deadlock rule (SPEC 31.4): the sampler module takes the Information Age alloy (Halo Steel), never Void Steel,
// and the deep-space module takes the Space Age alloy (Void Steel). One alloy per module keeps the starsteel demand of
// the sky road inside SPEC 24.2. No hidden item (Ad Astra's steel plate and rod) is used. Only while the sky road is
// complete (startup_scripts/sky.js).

ServerEvents.recipes((event) => {
  if (!global.FA_SKY.REQUIRED.every((id) => Item.exists(id))) return

  // ---- Launch Control: the gantry's computer at the tower's foot
  event.shaped('firmages:launch_control', ['DCD', 'SKS', 'DED'], {
    D: '#c:plates/desh',
    C: 'mekanism:elite_control_circuit',
    S: '#c:plates/steel',
    K: 'mekanism:steel_casing',
    E: 'immersiveengineering:component_electronic_adv'
  }).id('firmages:crafting/launch_control')

  // ---- Probe Cradle: the pad's centre, a heavy engineering block clamped in desh
  event.shaped('firmages:probe_cradle', ['DSD', 'SHS', 'DSD'], {
    D: '#c:plates/desh',
    S: '#c:plates/steel',
    H: 'immersiveengineering:heavy_engineering'
  }).id('firmages:crafting/probe_cradle')

  // ---- Launch Port: the fuel inlet on the pad's rim
  event.shaped('firmages:launch_port', ['DPD', 'PTP', 'DPD'], {
    D: '#c:plates/desh',
    P: 'immersiveengineering:fluid_pipe',
    T: 'mekanism:basic_fluid_tank'
  }).id('firmages:crafting/launch_port')

  // ---- Probe: a reusable desh rocket in miniature (never lost on a flight)
  event.shaped('firmages:probe', [' N ', 'DTD', 'FEF'], {
    N: 'ad_astra:rocket_nose_cone',
    D: '#c:plates/desh',
    T: 'ad_astra:desh_tank',
    F: 'ad_astra:rocket_fin',
    E: 'ad_astra:desh_engine'
  }).id('firmages:crafting/probe')

  // ---- Sampler Module (Space Age, consumed per flight; returns voidglass)
  event.shaped('firmages:sampler_module', [' C ', 'DAD', ' H '], {
    C: 'mekanism:advanced_control_circuit',
    D: '#c:plates/desh',
    A: '#firmages:celestial_alloy/age_6',
    H: 'minecraft:hopper'
  }).id('firmages:crafting/sampler_module')

  // ---- Deep-Space Module (Quantum Age, consumed per flight; returns deep-space samples and voidglass)
  event.shaped('firmages:deep_space_module', [' C ', 'PAP', ' H '], {
    C: 'mekanism:elite_control_circuit',
    P: '#c:plates/calorite',
    A: '#firmages:celestial_alloy/age_7',
    H: 'minecraft:hopper'
  }).id('firmages:crafting/deep_space_module')
})
