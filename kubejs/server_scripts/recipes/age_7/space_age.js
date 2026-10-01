// Firmament Ages - Age 7: Space Age goal (Doc 08 sections 2.2 and 10.2; Doc 10 v3 sections 5 and 6.1).
// Canonical stations of the Age: Mekanism fission (polonium), Ad Astra tier 1-2 rockets (Moon desh, Mars ostrum),
// DE Fusion Crafting with basic and wyvern injectors (the Age's pedestal crafting).
// Recipe format as the /fa_dump_full export lists DE's own fusion recipes (2026-09-30).
// Goal: the Star Chart (desh and ostrum plates, Mekanism polonium pellet, Wyvern Core, Harbinger drop).

ServerEvents.recipes((event) => {
  const consume = (ing) => ({ ingredient: ing, consume: true })
  // Polonium pellet as the catalyst on the fusion core; six injectors at wyvern level.
  event.custom({
    type: 'draconicevolution:fusion_crafting',
    result: { id: 'firmages:star_chart', count: 1 },
    catalyst: { item: 'mekanism:pellet_polonium' },
    totalEnergy: 16000000,
    techLevel: 'wyvern',
    ingredients: [
      consume({ tag: 'c:plates/desh' }),
      consume({ tag: 'c:plates/desh' }),
      consume({ tag: 'c:plates/ostrum' }),
      consume({ tag: 'c:plates/ostrum' }),
      consume({ item: 'draconicevolution:wyvern_core' }),
      consume({ tag: 'firmages:boss_token/age_7' })
    ]
  }).id('firmages:fusion/star_chart')
})
