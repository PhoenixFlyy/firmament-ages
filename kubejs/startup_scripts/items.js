// Firmament Ages - pack items (namespace firmages). Needs a full game restart after changes.
// Signature items: one per Age, crafted once, consumed by the Ultimate Singularity later (Doc 08 section 10.2).
// Scope: Stone Age to Industrial Age goals, plus stubs of the later magic tail. Textures go to kubejs/assets/firmages/textures/item/<path>.png;
// until they exist the game shows the missing-texture checkerboard.
// POC: check that KubeJS 2101 accepts ids with a sub path (firmages:dust/zinc, Doc 10 v3 section 8.1 naming).

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

  // ---- Magic tail stubs for later Ages (Doc 08 section 7.2); recipes come with their Ages ----
  event.create('firmages:attuned_circuit').displayName('Attuned Circuit')                  // age_5: IE circuit board + spirit attuned crystal
  event.create('firmages:awakened_keystone').displayName('Awakened Keystone').maxStackSize(1).rarity('epic') // age_8: Marid ritual

  // ---- Canonical dusts for TFC-only metals (Doc 08 section 4.1) ---------------------------
  // Crushing Wheels (Iron Age) turn TFC ore pieces into dust; dusts melt back in TFC vessels.
  event.create('firmages:dust/zinc').displayName('Zinc Dust')
  event.create('firmages:dust/bismuth').displayName('Bismuth Dust')
})
