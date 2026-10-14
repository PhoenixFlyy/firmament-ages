// Firmament Ages - drop tag entries that point at items/blocks no mod registers.
// A single missing required entry makes vanilla discard the whole tag AND every tag that includes it.
// Found on the first test-server boot (boot report of pack 0.1.0, Modpack-Planung/archive/dev/boot-report.md):
//  - WoodenCog 1.2.19 tags woodencog:unfired_fireclay_crucible / woodencog:fireclay_crucible, which it
//    does not register, and so broke tfc:unfired_pottery.
// (The MekaTFC 0.1.0 entries for lead, galena and cupronickel ids that TFC 4.2.11 lacks left with the mod,
// 2026-10-13.)
// Remove this file once the mods ship fixed data (check the boot log for "Couldn't load tag").

ServerEvents.tags('item', (event) => {
  event.remove('tfc:unfired_pottery', 'woodencog:unfired_fireclay_crucible')
  event.remove('woodencog:unburnable', 'woodencog:fireclay_crucible')
})
