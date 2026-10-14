// Firmament Ages - pack items (namespace firmages). Needs a full game restart after changes.
// Signature items: one per Age, crafted once, consumed by the Ultimate Singularity later (Doc 08 section 10.2).
// Scope: the goals of the Stone Age to the Quantum Age, plus the magic tail. Textures go to kubejs/assets/firmages/textures/item/<path>.png;
// until they exist the game shows the missing-texture checkerboard.
// POC: check that KubeJS 2101 accepts ids with a sub path (firmages:dust/zinc, Doc 10 v3 section 8.1 naming).

// Tooltip line of every signature item: the Age it is offered for and where it goes. client_scripts/item_tooltips.js shows them.
global.FA_SIGNATURE_TOOLTIPS = {
  'firmages:hearthstone': 'The offering of the Stone Age. Lay it on the shrine.',
  'firmages:sky_disc': 'The offering of the Bronze Age. Lay it on the shrine.',
  'firmages:steel_heart': 'The offering of the Iron Age. Lay it on the shrine.',
  'firmages:arcane_keystone': 'The offering of the Arcane Age. Lay it on the shrine.',
  'firmages:pressure_core': 'The offering of the Industrial Age. Lay it on the shrine.',
  'firmages:humming_core': 'The offering of the Electric Age. Lay it on the shrine.',
  'firmages:data_matrix': 'The offering of the Information Age. Lay it on the shrine.',
  'firmages:star_chart': 'The offering of the Space Age. Lay it on the shrine.',
  'firmages:quantum_core': 'The offering of the Quantum Age. Lay it on the shrine.',
  'firmages:awakened_keystone': 'The Arcane Keystone, awakened by a Marid. It belongs in the Ultimate Singularity.',
  'firmages:ultimate_singularity': 'All nine Ages in one. It goes into the base block of the Stargate.',
  // The dial address of The Origin. firmages-core registers its Space Location with this galactic address of the
  // Milky Way, not randomized (mod/firmages-core/SPEC.md section 14); keep both in step.
  'firmages:origin_coordinates': 'Dial The Origin: 9, 16, 21, 33, 2, 37, then the point of origin (Milky Way, 7 chevrons).'
}

// Boss fallback sigils (Doc 08 section 5.1): item id -> name, Age, the gate it opens, the boss it stands in for and
// what the gate pays out. Read by the item registry below, client_scripts (tooltips, gate names) and
// server_scripts/firmages/boss_fallback.js (opening). The rewards themselves are in the gateway JSON.
global.FA_SIGILS = {
  'firmages:frontier_sigil': { name: 'Frontier Sigil', age: 'age_2', gate: 'firmages:frontier_trial', gateName: 'Frontier Trial',
    boss: 'the Naga and the Lich', reward: 'a Naga trophy and a Lich trophy' },
  'firmages:wild_sigil': { name: 'Wild Sigil', age: 'age_3', gate: 'firmages:wild_trial', gateName: 'Wild Trial',
    boss: 'the Wilden Chimera', reward: 'a Wilden Tribute' },
  'firmages:forge_sigil': { name: 'Forge Sigil', age: 'age_4', gate: 'firmages:forge_trial', gateName: 'Forge Trial',
    boss: 'the Netherite Monstrosity', reward: 'a Monstrous Horn' },
  'firmages:wither_sigil': { name: 'Wither Sigil', age: 'age_5', gate: 'firmages:wither_trial', gateName: 'Wither Trial',
    boss: 'the Wither', reward: 'a Nether Star' },
  'firmages:end_sigil': { name: 'End Sigil', age: 'age_6', gate: 'firmages:end_trial', gateName: 'End Trial',
    boss: 'the Ender Dragon', reward: 'four bottles of dragon\'s breath (from the Space Age on also a Dragon Heart)' },
  'firmages:space_sigil': { name: 'Space Sigil', age: 'age_7', gate: 'firmages:space_trial', gateName: 'Space Trial',
    boss: 'the Harbinger', reward: 'a Witherite Block' },
  'firmages:abyss_sigil': { name: 'Abyss Sigil', age: 'age_8', gate: 'firmages:abyss_trial', gateName: 'Abyss Trial',
    boss: 'the Leviathan', reward: 'an Abyssal Egg' },
  'firmages:chaos_sigil': { name: 'Chaos Sigil', age: 'age_9', gate: 'firmages:chaos_trial', gateName: 'Chaos Trial',
    boss: 'the Chaos Guardian', reward: 'four Chaos Shards' }
}

StartupEvents.registry('item', event => {
  // ---- Age 0: Stone Age goal -------------------------------------------------------------
  event.create('firmages:unfired_hearth_idol').displayName('Unfired Hearth Idol').maxStackSize(1)
  event.create('firmages:hearth_idol').displayName('Hearth Idol').maxStackSize(1)
  event.create('firmages:hearthstone').displayName('Hearthstone').maxStackSize(1)

  // ---- Age 1: Bronze Age goal ------------------------------------------------------------
  event.create('firmages:sky_disc').displayName('Sky Disc').maxStackSize(1)

  // ---- Age 2: Iron Age goal --------------------------------------------------------------
  event.create('firmages:steel_heart').displayName('Steel Heart').maxStackSize(1)

  // ---- Age 3: Arcane Age goal (Doc 08 sections 7.1 and 10.2) -------------------------------
  // Made in the Djinni pentacle ritual (recipes/age_3/arcane_age.js); later awakened by the Marid (age_8).
  event.create('firmages:arcane_keystone').displayName('Arcane Keystone').maxStackSize(1).rarity('rare')

  // ---- Age 4: Industrial Age goal and its magic part (Doc 08 sections 7.2, 9.3, 10.2) --------
  // Arcane Gearbox: source gems in a steel gearbox; crafted in the grid, so a Wixie can automate it.
  event.create('firmages:arcane_gearbox').displayName('Arcane Gearbox')
  event.create('firmages:pressure_core').displayName('Pressure Core').maxStackSize(1).rarity('rare')

  // ---- Magic tail of later Ages (Doc 08 section 7.2) -------------------------------------------
  event.create('firmages:attuned_circuit').displayName('Attuned Circuit')                  // age_5: IE circuit board + spirit attuned crystal (recipes/age_5)
  event.create('firmages:awakened_keystone').displayName('Awakened Keystone').maxStackSize(1).rarity('epic') // age_8: Marid ritual (recipes/age_8)

  // ---- Age 5 to 8 goals: the shrine offerings of tiers 5..8 (Doc 08 section 10.2, firmages-core offerings.json) ----
  event.create('firmages:humming_core').displayName('Humming Core').maxStackSize(1).rarity('rare')   // IE Arc Furnace (recipes/age_5)
  event.create('firmages:data_matrix').displayName('Data Matrix').maxStackSize(1).rarity('rare')     // Mekanism + AE2 parts (recipes/age_6)
  event.create('firmages:star_chart').displayName('Star Chart').maxStackSize(1).rarity('epic')       // DE Fusion Crafting, wyvern (recipes/age_7)
  event.create('firmages:quantum_core').displayName('Quantum Core').maxStackSize(1).rarity('epic')   // DE Fusion Crafting, draconic (recipes/age_8)

  // ---- Age 9: the finale (Doc 08 section 10) ------------------------------------------------------------------
  event.create('firmages:ultimate_singularity').displayName('Ultimate Singularity').maxStackSize(1).rarity('epic') // DE fusion, chaotic (recipes/age_9)
  event.create('firmages:origin_coordinates').displayName('Coordinates of The Origin').maxStackSize(1).rarity('rare') // paper + chaos shard (recipes/age_9)

  // ---- Boss fallback sigils (Doc 08 section 5.1) ---------------------------------------------------------------
  // One per boss checkpoint whose drop goes into a signature item. Used on a block, a sigil opens its Gateways to
  // Eternity gate (kubejs/data/firmages/gateways/<age>_trial.json, server_scripts/firmages/boss_fallback.js) with
  // stand-in waves; the gate pays out the boss drop that the firmages:boss_token/<age> tag already holds.
  // One explicit create() per id: dev/gen_textures.py --check reads the ids from these lines.
  const sigil = (b, id) => b.displayName(global.FA_SIGILS[id].name).maxStackSize(16).rarity('uncommon')
  sigil(event.create('firmages:frontier_sigil'), 'firmages:frontier_sigil')
  sigil(event.create('firmages:wild_sigil'), 'firmages:wild_sigil')
  sigil(event.create('firmages:forge_sigil'), 'firmages:forge_sigil')
  sigil(event.create('firmages:wither_sigil'), 'firmages:wither_sigil')
  sigil(event.create('firmages:end_sigil'), 'firmages:end_sigil')
  sigil(event.create('firmages:space_sigil'), 'firmages:space_sigil')
  sigil(event.create('firmages:abyss_sigil'), 'firmages:abyss_sigil')
  sigil(event.create('firmages:chaos_sigil'), 'firmages:chaos_sigil')

  // ---- Canonical dusts for TFC-only metals (Doc 08 section 4.1) ---------------------------
  // Crushing Wheels (Iron Age) turn TFC ore pieces into dust; dusts melt back in TFC vessels.
  event.create('firmages:dust/zinc').displayName('Zinc Dust')
  event.create('firmages:dust/bismuth').displayName('Bismuth Dust')

  // ---- Osmium (the pack's osmium vein, startup_scripts/osmium.js) ----------------------------------------------
  // Ore pieces of the three grades (the small piece is the indicator block's item), and the double ingot that
  // TFC welds from two Mekanism osmium ingots (welded with a wrought iron sheet into a Mekanism Steel Casing).
  event.create('firmages:ore/poor_native_osmium').displayName('Poor Native Osmium')
  event.create('firmages:ore/normal_native_osmium').displayName('Normal Native Osmium')
  event.create('firmages:ore/rich_native_osmium').displayName('Rich Native Osmium')
  event.create('firmages:metal/double_ingot/osmium').displayName('Osmium Double Ingot')
})
