// Firmament Ages - Age 3: the parts of the Lens Crown (firmages-core SPEC 27.9). The crown itself forges with the
// firmages:starforge recipes of the mod (data/firmages/recipe/starforge, station Age in recipes/station_ages.js).
// The Crown cannot make its own first lenses, so the Stone, Bronze and Iron Lens are grid-crafted from a lens blank
// in a ring of TFC sheets; every later lens comes out of the Crown. The parts load only while the sky road is complete
// (startup_scripts/sky.js): a Star Anvil without storm glass caps or alloys to forge would be a dead end in EMI.

ServerEvents.recipes((event) => {
  if (!global.FA_SKY.REQUIRED.every((id) => Item.exists(id))) {
    console.info(`[firmages] Lens Crown parts and V4A wait for the sky materials: ${global.FA_SKY.REQUIRED.filter((id) => !Item.exists(id)).join(', ')}`)
    return
  }

  // ---- lens blank: storm glass polished on Create sandpaper, one to one
  event.custom({
    type: 'create:sandpaper_polishing',
    ingredients: [{ item: 'firmages:storm_glass' }],
    results: [{ id: 'firmages:lens_blank' }]
  }).id('firmages:sandpaper_polishing/lens_blank')

  // ---- the first three lenses: lens N belongs to shrine tier N (copper, bronze, wrought iron sheets)
  const FIRST_LENSES = { stone: 'c:sheets/copper', bronze: 'c:sheets/bronze', iron: 'c:sheets/wrought_iron' }
  Object.keys(FIRST_LENSES).forEach((lens) => {
    event.shaped(`firmages:lens/${lens}`, [' S ', 'SBS', ' S '], {
      S: `#${FIRST_LENSES[lens]}`,
      B: 'firmages:lens_blank'
    }).id(`firmages:crafting/lens/${lens}`)
  })

  // ---- Lens Mount: four meteoric iron ingots around a Create precision mechanism
  event.shaped('firmages:lens_mount', [' M ', 'MPM', ' M '], {
    M: '#c:ingots/meteoric_iron',
    P: 'create:precision_mechanism'
  }).id('firmages:crafting/lens_mount')

  // ---- Star Anvil: a TFC steel anvil, four meteoric iron ingots, four storm glass
  event.shaped('firmages:star_anvil', ['MGM', 'GAG', 'MGM'], {
    M: '#c:ingots/meteoric_iron',
    G: 'firmages:storm_glass',
    A: 'tfc:metal/anvil/steel'
  }).id('firmages:crafting/star_anvil')
})
