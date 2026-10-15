// Firmament Ages - Alex's Mobs Continued (1.0 round C, V1B; dev/decisions-while-away.md 2026-10-15).
// Gear rule (Doc 08 section 8): the roadrunner boots take leather instead of a pair of leather boots.

ServerEvents.recipes((event) => {
  event.replaceInput({ id: 'alexsmobs:roadrunner_boots' }, 'minecraft:leather_boots', 'minecraft:leather')
})
