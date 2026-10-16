// Firmament Ages - Age 4: the Aurora Net of the Sky Harvest (firmages-core SPEC 26.4, sky M11).
// A single block that catches aurora light on clear nights in the cold band, at height 140 or above under open sky.
// It is the only source of aurora light (the Lens Crown's fluid ingredient). Four IE steel rods as the frame, two
// copper wire coils as the mesh, two meteoric iron sheets and a storm glass pane at the centre.

ServerEvents.recipes((event) => {
  event.shaped('firmages:aurora_net', ['SWS', 'MGM', 'SWS'], {
    S: '#c:rods/steel',
    W: 'immersiveengineering:wirecoil_copper',
    M: '#c:sheets/meteoric_iron',
    G: 'firmages:storm_glass'
  }).id('firmages:crafting/aurora_net')
})
