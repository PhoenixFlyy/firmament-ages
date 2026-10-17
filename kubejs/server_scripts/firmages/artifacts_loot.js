// Firmament Ages - Artifacts as loot of the pack's own places (vorauswahl M043, dev/decisions-while-away.md 2026-10-15).
// Artifacts' own sources do not exist in a TFC world (vanilla structure chests, campsites on #minecraft:is_overworld,
// suspicious sand) or are switched off (config/artifacts/general.toml: chest rarity 10000, archaeology and mob
// equipment 0). The trinkets come from the TFC ruins, the Cold Forge and the fortresses of the Beneath instead.
// Each list is one Age (dev/age_map.toml, Artifacts rows); firmages-core's loot gate (SPEC 19.5) removes a trinket of a
// later Age when the chest is opened, so a ruin opened in the Bronze Age only yields Bronze Age trinkets.
// dev/derive_ages.py CODE_DROPS mirrors these lists (a LootJS pool is no data file the jar scan can read).

(() => {
  const AGE_1 = ['anglers_hat', 'cowboy_hat', 'novelty_drinking_hat', 'plastic_drinking_hat', 'superstitious_hat',
    'villager_hat', 'lucky_scarf', 'whoopee_cushion', 'umbrella', 'snorkel', 'flippers', 'kitty_slippers', 'snowshoes',
    'golden_hook', 'onion_ring', 'bunny_hoppers']
  const AGE_2 = ['running_shoes', 'cloud_in_a_bottle', 'aqua_dashers', 'steadfast_spikes', 'feral_claws', 'power_glove',
    'pocket_piston', 'charm_of_sinking', 'charm_of_shrinking', 'night_vision_goggles', 'antidote_vessel', 'panic_necklace']
  const AGE_3 = ['fire_gauntlet', 'flame_pendant', 'shock_pendant', 'thorn_pendant', 'cross_necklace', 'strider_shoes',
    'withered_bracelet', 'obsidian_skull', 'vampiric_glove', 'scarf_of_invisibility']
  const AGE_4 = ['crystal_heart', 'warp_drive']

  // One extra roll per chest: `chance` in 100 that it holds a trinket of `lists` (equal weight per trinket).
  const PLACES = [
    { table: 'firmages:chests/ruin_small', chance: 12, lists: [AGE_1, AGE_2] },
    { table: 'firmages:chests/ruin_rich', chance: 30, lists: [AGE_1, AGE_2, AGE_3, AGE_4] },
    { table: 'firmages:chests/cold_forge', chance: 50, lists: [AGE_2] },
    { table: 'minecraft:chests/nether_bridge', chance: 20, lists: [AGE_3] }
  ]

  LootJS.lootTables((event) => {
    PLACES.forEach((place) => {
      const items = []
      place.lists.forEach((list) => list.forEach((id) => items.push(`artifacts:${id}`)))
      // Weights: every trinket 1, the empty entry so that the trinkets together hit `chance` in 100.
      const empty = Math.max(0, Math.round(items.length * (100 - place.chance) / place.chance))
      event.getLootTable(place.table).createPool((pool) => {
        pool.rolls(1)
        items.forEach((id) => pool.addEntry(LootEntry.of(id).withWeight(1)))
        if (empty > 0) pool.addEntry(LootEntry.empty().withWeight(empty))
      })
    })
  })
})()
