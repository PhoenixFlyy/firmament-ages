// Firmament Ages - Age 6: Information Age goal (Doc 08 sections 2.2 and 10.2; Doc 10 v3 section 6.1).
// Canonical stations of the Age: Mekanism (Metallurgic Infuser for its alloys, the advanced control circuit) and
// AE2 + ExtendedAE (storage components, Molecular Assembler autocrafting).
// Goal: the Data Matrix (Mekanism advanced control circuits, AE2 64k storage component, blue steel sheets,
// dragon's breath).

ServerEvents.recipes((event) => {
  // A grid recipe like the Steel Heart and the Pressure Core: the Mekanism and AE2 parts carry the Age's chains,
  // and an AE2 Molecular Assembler can craft it. The boss slot is the age_6 token (dragon's breath, Ender Dragon).
  event.shaped('firmages:data_matrix', ['BCB', 'CSC', 'BTB'], {
    B: '#c:sheets/blue_steel',
    C: 'mekanism:advanced_control_circuit',
    S: 'ae2:cell_component_64k',
    T: '#firmages:boss_token/age_6'
  }).id('firmages:crafting/data_matrix')
})
