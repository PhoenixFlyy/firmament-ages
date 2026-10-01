// Firmament Ages - pack items (namespace firmages). Needs a full game restart after changes.
// Signature items: one per Age, crafted once, consumed by the Ultimate Singularity later (Doc 08 section 10.2).
// Scope: the goals of the Stone Age to the Quantum Age, plus the magic tail. Textures go to kubejs/assets/firmages/textures/item/<path>.png;
// until they exist the game shows the missing-texture checkerboard.
// POC: check that KubeJS 2101 accepts ids with a sub path (firmages:dust/zinc, Doc 10 v3 section 8.1 naming).

// Tooltip line of every signature item: its Age and where it goes. client_scripts/item_tooltips.js shows them.
global.FA_SIGNATURE_TOOLTIPS = {
  'firmages:hearthstone': 'Signature item of the Stone Age. Offer it at the shrine.',
  'firmages:sky_disc': 'Signature item of the Bronze Age. Offer it at the shrine.',
  'firmages:steel_heart': 'Signature item of the Iron Age. Offer it at the shrine.',
  'firmages:arcane_keystone': 'Signature item of the Arcane Age. Offer it at the shrine.',
  'firmages:pressure_core': 'Signature item of the Industrial Age. Offer it at the shrine.',
  'firmages:humming_core': 'Signature item of the Electric Age. Offer it at the shrine.',
  'firmages:data_matrix': 'Signature item of the Information Age. Offer it at the shrine.',
  'firmages:star_chart': 'Signature item of the Space Age. Offer it at the shrine.',
  'firmages:quantum_core': 'Signature item of the Quantum Age. Offer it at the shrine.',
  'firmages:awakened_keystone': 'The Arcane Keystone, awakened by a Marid. It belongs in the Ultimate Singularity.',
  'firmages:ultimate_singularity': 'All nine Ages in one. It goes into the base block of the Stargate.',
  // The dial address of The Origin. firmages-core registers its Space Location with this galactic address of the
  // Milky Way, not randomized (mod/firmages-core/SPEC.md section 14); keep both in step.
  'firmages:origin_coordinates': 'Dial The Origin: 9, 16, 31, 5, 21, 37, then the point of origin (Milky Way, 7 chevrons).'
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

  // ---- Canonical dusts for TFC-only metals (Doc 08 section 4.1) ---------------------------
  // Crushing Wheels (Iron Age) turn TFC ore pieces into dust; dusts melt back in TFC vessels.
  event.create('firmages:dust/zinc').displayName('Zinc Dust')
  event.create('firmages:dust/bismuth').displayName('Bismuth Dust')
})
