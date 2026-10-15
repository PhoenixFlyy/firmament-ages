// Firmament Ages - AeroEngine on Electric Age fuel (1.0 round C, vorauswahl M014; dev/decisions-while-away.md 2026-10-15).
// The jet engines burn only AeroEngine's own aviation kerosene (ModFluids.isAviationKerosene, javap 1.3.0). The mod
// mixes it from coal and a blaze cake; in the pack it is Immersive Petroleum's kerosene from the distillation tower,
// refined in a heated Create mixer. The guided missiles and their assembly station are hidden ([hidden] in
// dev/age_map.toml), the firmages-core gate drops their recipes.

ServerEvents.recipes((event) => {
  event.remove({ id: 'aeroengineering:mixing/aviation_kerosene' })
  event.custom({
    type: 'create:mixing',
    ingredients: [{ type: 'neoforge:single', fluid: 'immersivepetroleum:kerosene', amount: 500 }],
    results: [{ id: 'aeroengineering:aviation_kerosene', amount: 500 }],
    heat_requirement: 'heated'
  }).id('firmages:mixing/aviation_kerosene')
})
