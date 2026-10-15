// Firmament Ages - Age 4: the AE2 wireless crafting terminal, early and dear (AE2 package, 2026-10-15; Felix: "may
// come earlier with a very expensive recipe", autocrafting stays in the Information Age).
// Industrial Age: one Create mechanical crafting from four black steel double sheets (16 black steel ingots), four
// precision mechanisms, four IE steel components, two electron tubes, an LV capacitor, a Create stock link (the
// wireless part) and a TFC workbench (the crafting grid). It exists only until the Information Age (FirmAges.untilAge):
// from then on AE2's own recipes (wireless receiver + crafting terminal + dense energy cell, or wireless terminal +
// workbench + calculation processor) take over, and they wait for that Age (FirmAges.requireAge), because their
// output is an Industrial Age item now (dev/age_map.toml [items]).
// The terminal links to an ME network through a wireless access point; AE2 itself (controller, drive, access point)
// stays an Information Age mod.

ServerEvents.recipes((event) => {
  event.custom({
    type: 'create:mechanical_crafting',
    accept_mirrored: true,
    category: 'misc',
    key: {
      D: { tag: 'c:double_sheets/black_steel' },
      M: { item: 'create:precision_mechanism' },
      C: { item: 'immersiveengineering:component_steel' },
      K: { item: 'create:stock_link' },
      E: { item: 'create:electron_tube' },
      W: { tag: 'tfc:workbenches' },
      L: { item: 'immersiveengineering:capacitor_lv' }
    },
    pattern: ['DMD', 'CKC', 'EWE', 'CLC', 'DMD'],
    result: { id: 'ae2:wireless_crafting_terminal', count: 1 }
  }).id('firmages:mechanical_crafting/wireless_crafting_terminal')
  FirmAges.untilAge('firmages:mechanical_crafting/wireless_crafting_terminal', 'age_6')
  FirmAges.requireAge('ae2:network/wireless_crafting_terminal', 'age_6')
  FirmAges.requireAge('ae2:network/upgrade_wireless_crafting_terminal', 'age_6')
})
