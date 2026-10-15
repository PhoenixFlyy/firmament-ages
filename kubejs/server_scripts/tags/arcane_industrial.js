// Firmament Ages - tags for the Arcane Age (age_3) and the Industrial Age (age_4).
// Item tags feed recipes/age_3/*.js and recipes/age_4/*.js; block and entity tags adapt the magic mods to the
// TFC world (Doc 08 section 7.2 "TFC-Nacharbeit"). All ids were checked against the /fa_dump registry (2026-09-30).

ServerEvents.tags('item', (event) => {
  // TFC blows its own glass bottles; vanilla glass bottles have no recipe in a TFC world.
  event.add('firmages:glass_bottles', ['minecraft:glass_bottle', 'tfc:silica_glass_bottle', 'tfc:hematitic_glass_bottle',
    'tfc:olivine_glass_bottle', 'tfc:volcanic_glass_bottle'])

  // Gems of the Arcane Age veins (Doc 08 section 3.2: lapis, amethyst and the other gems open with age_3).
  // Diamond (kimberlite) opens with age_4, so Arcane Age magic recipes take one of these instead of a diamond.
  // Lapis and amethyst are left out: they are the source gem inputs.
  event.add('firmages:gems/arcane', ['tfc:gem/emerald', 'tfc:gem/ruby', 'tfc:gem/sapphire', 'tfc:gem/topaz', 'tfc:gem/opal'])

  // Tallow: Occultism candles take c:tallow; the butcher knife gives occultism:tallow from the TFC animals
  // once they are in the entity tags below.

  // Age tokens (dev/decisions-while-away.md, 2026-10-14): the pack's own trial of each Age, no boss. Every recipe slot
  // reads the tag, so changing its content rewires the recipe, the quest task and every second use at once.
  // Arcane: the Hollow Keystone from the chests of the Nether fortresses, filled on the Create line into the Arcane
  // Keystone (recipes/age_3/arcane_age.js; the Djinni ritual with the Wilden Tribute stays the magic alternative).
  // Industrial: the built-up steel cannon chamber of Create Big Cannons (cast, bored, built up, heated).
  event.add('firmages:age_token/age_3', 'firmages:hollow_keystone')
  event.add('firmages:age_token/age_4', 'createbigcannons:built_up_steel_cannon_chamber')

  event.add('firmages:signature_items', ['firmages:arcane_keystone', 'firmages:pressure_core'])
})

ServerEvents.tags('block', (event) => {
  // Theurgy divination rods must not find ores that are still disguised (Doc 08 section 3.4, Felix's ore rule).
  // Every rod tier is an Arcane Age item, so every tier refuses the ore blocks of age_4 and later
  // (tags generated from dev/age_map.toml).
  const LATER = ['age_4', 'age_5', 'age_6', 'age_7', 'age_8', 'age_9'].map((a) => `#firmages:age_blocks/${a}`)
  ;['t1', 't2', 't3', 't4'].forEach((t) => event.add(`theurgy:divination_rod_${t}_disallowed_blocks`, LATER))

  // Ars creatures (Starbuncle, Drygmy, Whirlisprig) spawn only on animals_spawnable_on; TFC grass is not in it.
  event.add('minecraft:animals_spawnable_on', '#tfc:grass')
})

ServerEvents.tags('entity_type', (event) => {
  // Occultism sacrifices and butcher-knife tallow ask for conventional animal tags; TFC animals are not in them.
  const ANIMALS = {
    'c:cows': ['tfc:cow', 'tfc:yak', 'tfc:musk_ox'],
    'c:pigs': ['tfc:pig', 'tfc:boar'],
    'c:sheep': ['tfc:sheep'],
    'c:goats': ['tfc:goat'],
    'c:chickens': ['tfc:chicken', 'tfc:duck', 'tfc:quail'],
    'c:horses': ['tfc:horse'],
    'c:donkeys': ['tfc:donkey'],
    'c:mules': ['tfc:mule'],
    'c:llamas': ['tfc:alpaca'],
    'c:pandas': ['tfc:panda'],
    'c:rabbits': ['tfc:rabbit'],
    'c:foxes': ['tfc:fox'],
    'c:wolves': ['tfc:wolf'],
    'c:cats': ['tfc:cat'],
    'c:camels': ['tfc:dromedary_camel', 'tfc:bactrian_camel'],
    'c:fish': ['#tfc:small_fish']
  }
  Object.keys(ANIMALS).forEach((tag) => event.add(tag, ANIMALS[tag]))
})
