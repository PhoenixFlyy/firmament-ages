// Firmament Ages - Age 5: Electric Age goal (Doc 08 sections 2.2, 7.2 and 10.2; Doc 10 v3 section 6.1).
// Canonical stations of the Age: IE Arc Furnace (melting, alloys, steel), IE Assembler (grid autocrafting), HV power.
// Recipe formats as the /fa_dump_full export lists IE's own arc furnace recipes (2026-09-30).
// Goal: the Humming Core (Attuned Circuit, IE HV capacitor, aluminium plates, red steel sheets, Wither drop).

ServerEvents.recipes((event) => {
  // ---- magic tail: Attuned Circuit (Doc 08 section 7.2) ---------------------------------------------------------
  // IE circuit board with a spirit attuned crystal. A grid recipe, so the IE Assembler automates it.
  event.shapeless('firmages:attuned_circuit', ['immersiveengineering:circuit_board', 'occultism:spirit_attuned_crystal'])
    .id('firmages:crafting/attuned_circuit')

  // ---- goal: Humming Core -------------------------------------------------------------------------------------
  // Fused in the IE Arc Furnace (the Electric Age HV station): the HV capacitor is the main input, the four additive
  // slots take the Attuned Circuit, aluminium plates, red steel sheets and the age_5 boss token (Nether Star).
  // The diesel jerrycan of Doc 08 10.2 is left out: the arc furnace takes no fluids; diesel is the Oil strand's
  // keystone instead (decision log 2026-10-01).
  event.custom({
    type: 'immersiveengineering:arc_furnace',
    input: { item: 'immersiveengineering:capacitor_hv' },
    additives: [
      { item: 'firmages:attuned_circuit' },
      { basePredicate: { tag: 'c:plates/aluminum' }, count: 4 },
      { basePredicate: { tag: 'c:sheets/red_steel' }, count: 2 },
      { tag: 'firmages:boss_token/age_5' }
    ],
    results: [{ id: 'firmages:humming_core', count: 1 }],
    time: 800,
    energy: 1638400
  }).id('firmages:arc_furnace/humming_core')
})
