// Firmament Ages - tags of the Electric Age (age_5) to the Singularity Age (age_9): boss tokens, the signature
// items, rocket fuel. Ids checked against the /fa_dump registry (dev/data/registry.json) and the Cataclysm 3.33 loot
// tables (2026-10-01).

ServerEvents.tags('item', (event) => {
  // Boss tokens: the real drop OR a fallback token (Doc 08 section 5.1). Fallback tokens are not registered yet.
  event.add('firmages:boss_token/age_5', 'minecraft:nether_star')       // The Wither
  event.add('firmages:boss_token/age_6', 'minecraft:dragon_breath')     // Ender Dragon (Doc 08 10.2: dragon's breath in the Data Matrix)
  event.add('firmages:boss_token/age_7', 'cataclysm:witherite_block')   // The Harbinger, guaranteed drop
  event.add('firmages:boss_token/age_8', 'cataclysm:abyssal_egg')       // The Leviathan, guaranteed drop
  event.add('firmages:boss_token/age_9', 'draconicevolution:chaos_shard') // Chaos Guardian (Doc 08 5.1: the "Chaos Trial" gateway pays out here)

  // Stargate Journey's crystal base takes a crafting crystal: vanilla diamond or a unity shard; TFC diamonds join.
  event.add('sgjourney:crafting_crystal', '#c:gems/diamond')

  event.add('firmages:signature_items', ['firmages:humming_core', 'firmages:data_matrix', 'firmages:star_chart', 'firmages:quantum_core',
    'firmages:awakened_keystone', 'firmages:ultimate_singularity'])
})

ServerEvents.tags('fluid', (event) => {
  // Doc 10 v3 8.1 ("Treibstoff: IP Diesel/Benzin; ad_astra:fuel per Pack-Rezept aus der IP-Kette") and Doc 08 13
  // ("Raketentreibstoff aus IP per Fluid-Tag"): Ad Astra rockets burn #ad_astra:fuel, which already holds #c:diesel
  // (IP diesel); IP kerosene, the lighter cut of the distillation tower, joins it. The Ad Astra Fuel Refinery is
  // disabled, so Ad Astra's own fuel fluid has no source.
  event.add('ad_astra:fuel', 'immersivepetroleum:kerosene')
})
