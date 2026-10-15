// Firmament Ages - tags of the Electric Age (age_5) to the Singularity Age (age_9): Age tokens, the signature
// items, rocket fuel. Ids checked against the /fa_dump registry (dev/data/registry.json).

ServerEvents.tags('item', (event) => {
  // Age tokens (dev/decisions-while-away.md, 2026-10-14): the pack's own trial of each Age, no boss. The known bosses
  // stay optional shortcuts: a Wither drops a Nether Star, the Chaos Guardian chaos shards.
  event.add('firmages:age_token/age_5', 'minecraft:nether_star')          // IE Arc Furnace (recipes/age_5); Afrit ritual, Wither
  event.add('firmages:age_token/age_6', 'ae2:singularity')                // AE2 Matter Condenser
  event.add('firmages:age_token/age_7', 'firmages:flight_recorder')       // the Mars temple chest (loot modifier)
  event.add('firmages:age_token/age_8', 'evolvedmekanism:alloy_subatomic') // antiprotonic nucleosynthesizer
  // Chaos the team makes in its own Draconic reactor (recipes/age_9/chaos_bootstrap.js): a large fragment per slot. A
  // chaos shard of the optional Chaos Guardian breaks into nine of them (DE's own recipe).
  event.add('firmages:age_token/age_9', 'draconicevolution:large_chaos_frag')

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
