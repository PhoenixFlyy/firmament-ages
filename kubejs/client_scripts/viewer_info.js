// Firmament Ages - recipe-viewer information pages of the sky materials (firmages-core SPEC 24.2): they come from the
// world and from firmages-core's own blocks, so no recipe shows where they come from. KubeJS 2101
// RecipeViewerEvents.addInformation('item' | 'fluid', event.add(id, [lines])) registers an EMI information page per
// stack; firmages-core's EMI plugin disables a stack of a later Age, and EMI drops a page whose stack is disabled, so
// each page appears with its Age. Reload in game: F3+T.

const FA_SKY_INFO = {
  'firmages:meteorite': [
    'Falls from the sky from the Iron Age: on some clear nights a star streaks toward its real bearing and leaves a',
    'smoking crater a few thousand blocks away. Old cold craters lie in new land.',
    'Break it for meteoric iron chunks.'
  ],
  'firmages:starfall_core': [
    'The heart of a fresh crater. While it glows, two days, every meteorite of its crater drops double.'
  ],
  'firmages:meteoric_iron_chunk': [
    'Breaks from the meteorites of a crater (Iron Age). Heat it and work it on the anvil into meteoric iron.',
    'The only source of meteoric iron.'
  ],
  'firmages:starsteel_meteorite': [
    'Falls from the sky from the Electric Age, like the meteorites of iron. Break it for starsteel chunks.'
  ],
  'firmages:starsteel_chunk': [
    'Breaks from starsteel meteorites (Electric Age). The arc furnace melts it into starsteel, its only source.'
  ],
  'firmages:storm_glass': [
    'Only from a Storm Rod (Iron Age): filled with sand, in any rain, lightning strikes it now and then and fuses',
    'four sand into storm glass. Four times as often in a thunderstorm.'
  ],
  'firmages:firmament_crystal': [
    'Grows only on the Drifting Isles, at height 228 to 272 (Industrial Age): in clusters and in one lode per',
    'cluster that stands again after it is broken. Reach the isles by airship or jetpack.'
  ],
  'firmages:aurora_light_bucket': [
    'Only from an Aurora Net (Industrial Age) at height 140 or more under open sky in the cold north. It catches',
    'aurora light every clear night there, twice as much in the cold half of the year. Pipe or bucket it out.'
  ],
  'firmages:voidglass': [
    'Only from a probe with a sampler module, launched from the Launch Complex (Space Age).'
  ],
  'firmages:deep_space_sample': [
    'Only from a probe with a deep-space module, launched from the Launch Complex (Quantum Age).'
  ]
}

// The lines above are source layout only: each page is one paragraph that EMI wraps itself.
const faInfoText = (id) => [Text.of(FA_SKY_INFO[id].join(' '))]

RecipeViewerEvents.addInformation('item', (event) => {
  Object.keys(FA_SKY_INFO).forEach((id) => event.add(id, faInfoText(id)))
})

RecipeViewerEvents.addInformation('fluid', (event) => {
  event.add('firmages:aurora_light', faInfoText('firmages:aurora_light_bucket'))
})
