// Firmament Ages - Age 9: Singularity Age, the Draconic reactor controller of firmages-core (SPEC section 6.2).
// The controller sits against a reactor stabilizer or injector, shuts the reactor down at 80 % conversion, swaps
// chaos for awakened draconium when it is cold and signals READY by redstone; starting stays a player action
// (Doc 11 section 6, decision 2). Grid recipe at the TFC workbench from parts of the Ages before: a stabilizer
// frame (age_7), two awakened draconium ingots (age_8), two Mekanism elite control circuits (age_6).

ServerEvents.recipes((event) => {
  event.shaped('firmages:reactor_controller', [
    ' A ',
    'CFC',
    ' A '
  ], {
    A: 'draconicevolution:awakened_draconium_ingot',
    C: 'mekanism:elite_control_circuit',
    F: 'draconicevolution:reactor_prt_stab_frame'
  }).id('firmages:crafting/reactor_controller')
})
