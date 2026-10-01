// Firmament Ages - Age 0: Stone Age recipes.
// Goal item "Hearthstone" = fired Hearth Idol + copper ingots + charcoal (Doc 08 section 10.2).
// KubeJS TFC 2.1.0 bindings: event.recipes.tfc.knapping(result, knapping_type, pattern) and
// event.recipes.tfc.heating(ingredient, temperature).resultItem(item) (schemas in the KubeJS-TFC repo, 1.21.1).
// The Dawn crafting lock itself is ProgressiveStages config (age_0.toml [recipes].locked_ids), not a script.

ServerEvents.recipes((event) => {
  const { tfc } = event.recipes

  // Clay knapping (knapping type tfc:clay, 5 clay; clay is locked until age_0 in age_0.toml).
  // TFC patterns: a space is a removed square, any other character stays.
  tfc.knapping('firmages:unfired_hearth_idol', 'tfc:clay', [
    ' ### ',
    '#####',
    ' ### ',
    ' ### ',
    '## ##'
  ]).id('firmages:knapping/unfired_hearth_idol')

  // Fired in the pit kiln like all TFC pottery (1399 C, value copied from tfc:heating/ceramic/bowl).
  // Needs kubejs/data/firmages/tfc/item_heat/unfired_hearth_idol.json.
  // POC: check that the idol can be placed and fired in a pit kiln (placed item + straw + logs).
  tfc.heating('firmages:unfired_hearth_idol', 1399)
    .resultItem('firmages:hearth_idol')
    .id('firmages:heating/hearth_idol')

  // The shrine (firmages-core 0.3.0, SPEC section 7): the heart is a fired Hearth Idol set in hearth stones
  // under a coal, so the team fires two idols in the Stone Age: one for the heart, one for the Hearthstone.
  event.shaped('firmages:shrine_heart', [
    ' C ',
    'SIS',
    ' S '
  ], {
    I: 'firmages:hearth_idol',
    S: '#c:cobblestones/normal',
    C: 'minecraft:charcoal'
  }).id('firmages:crafting/shrine_heart')

  // One plinth per ring; the Hearth Circle needs the first.
  event.shaped('firmages:offering_plinth', [
    ' S ',
    'SSS'
  ], {
    S: '#c:cobblestones/normal'
  }).id('firmages:crafting/offering_plinth')

  event.shaped('firmages:hearthstone', [
    ' K ',
    'CIC',
    ' K '
  ], {
    I: 'firmages:hearth_idol',
    K: '#c:ingots/copper',
    C: 'minecraft:charcoal'
  }).id('firmages:crafting/hearthstone')
})
