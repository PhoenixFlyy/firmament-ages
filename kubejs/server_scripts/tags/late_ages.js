// Firmament Ages - item tags for the Electric Age (age_5) to the Quantum Age (age_8): boss tokens and the
// signature items. Ids checked against the /fa_dump registry (dev/data/registry.json) and the Cataclysm 3.33 loot
// tables (2026-10-01).

ServerEvents.tags('item', (event) => {
  // Boss tokens: the real drop OR a fallback token (Doc 08 section 5.1). Fallback tokens are not registered yet.
  event.add('firmages:boss_token/age_5', 'minecraft:nether_star')       // The Wither
  event.add('firmages:boss_token/age_6', 'minecraft:dragon_breath')     // Ender Dragon (Doc 08 10.2: dragon's breath in the Data Matrix)
  event.add('firmages:boss_token/age_7', 'cataclysm:witherite_block')   // The Harbinger, guaranteed drop
  event.add('firmages:boss_token/age_8', 'cataclysm:abyssal_egg')       // The Leviathan, guaranteed drop

  event.add('firmages:signature_items', ['firmages:humming_core', 'firmages:data_matrix', 'firmages:star_chart', 'firmages:quantum_core'])
})
