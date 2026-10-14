// Firmament Ages - Age Lanterns (startup_scripts/signature_blocks.js): any TFC lamp plus one material of the Age.
// Each lantern is in its Age tag (dev/age_map.toml), so the firmages-core gate drops its recipe until that Age.
// The engraved bricks are TFC chisel recipes (data files in kubejs/data/firmages/recipe/chisel/engraved_bricks/).

ServerEvents.recipes((event) => {
  const L = global.FA_SIGNATURE_BLOCKS.LANTERNS
  Object.keys(L).forEach((key) => {
    event.shapeless(`firmages:lantern/${key}`, ['#tfc:lamps', L[key].material])
      .id(`firmages:crafting/lantern/${key}`)
  })
})
