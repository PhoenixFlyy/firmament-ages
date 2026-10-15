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

// Tooltip line of the expedition items of the Age tokens (dev/decisions-while-away.md, 2026-10-14).
global.FA_ITEM_TOOLTIPS = {
  'firmages:frontier_compass': 'Right-click it to learn where the nearest Cold Forge lies.',
  'firmages:frontier_shard': 'Kept in the Cold Forge. Finish it on the deployer and the press.',
  'firmages:hollow_keystone': 'Found in Nether fortresses. A spout, a deployer and a press fill it.',
  'firmages:flight_recorder': 'Kept in the temple on Mars. It goes into the Star Chart.'
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
  // Filled from a Hollow Keystone on the Create line or made in the Djinni pentacle ritual (recipes/age_3/arcane_age.js);
  // later awakened by the Marid (age_8).
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
  event.create('firmages:origin_coordinates').displayName('Coordinates of The Origin').maxStackSize(1).rarity('rare') // paper + large chaos fragment (recipes/age_9)

  // ---- Expedition items of the Age tokens (dev/decisions-while-away.md, 2026-10-14) ---------------------------------
  // Iron Age: the compass points to the Cold Forge (server_scripts/firmages/frontier_compass.js), its chest keeps the
  // shard, the Create line finishes it into the relic (recipes/age_2/iron_age.js). Arcane Age: the Hollow Keystone from
  // the Nether fortresses, filled on the Create line (recipes/age_3/arcane_age.js). Space Age: the flight recorder from
  // the Mars temple (kubejs/data/firmages/loot_modifiers/). The incomplete items are Create sequenced assembly items.
  event.create('firmages:frontier_compass').displayName('Frontier Compass').maxStackSize(1)
  event.create('firmages:frontier_shard').displayName('Frontier Shard').maxStackSize(16)
  event.create('firmages:incomplete_frontier_relic', 'create:sequenced_assembly').displayName('Incomplete Frontier Relic')
  event.create('firmages:frontier_relic').displayName('Frontier Relic').maxStackSize(16).rarity('uncommon')
  event.create('firmages:hollow_keystone').displayName('Hollow Keystone').maxStackSize(16)
  event.create('firmages:incomplete_arcane_keystone', 'create:sequenced_assembly').displayName('Incomplete Arcane Keystone')
  event.create('firmages:flight_recorder').displayName('Flight Recorder').maxStackSize(16).rarity('uncommon')

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

  // ---- Draconium (the pack's draconium vein, startup_scripts/draconium.js) --------------------------------------
  // Ore pieces of the three grades (the small piece is the indicator block's item); the Mekanism ore ladder makes
  // them into More Mekanism Processing's draconium clumps, shards and slurry (recipes/age_6/ore_ladder.js).
  event.create('firmages:ore/poor_draconium').displayName('Poor Draconium')
  event.create('firmages:ore/normal_draconium').displayName('Normal Draconium')
  event.create('firmages:ore/rich_draconium').displayName('Rich Draconium')
})
