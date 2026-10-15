// Firmament Ages - Exposure (n7-world): the film camera from TFC materials.
// Exposure 1.9.19 recipes read from the jar (data/exposure/recipe/*.json) and replaced by id. Ages come from
// dev/age_map.toml: the mod is Bronze Age (brass, silver, leather, lens); high-sensitivity black-and-white film is Iron
// Age (sulfur), colour film and the chromatic sheet are Industrial Age. The Interplanar Projector (loads pictures from
// a URL or a file on the player's PC) is hidden and switched off in defaultconfigs/exposure-server.toml.
// Developing is dry chemistry here instead of potions (water bottles are Iron Age in this pack): soda ash develops,
// salt fixes; colour film adds sulfur and saltpeter. Exposure's own Create sequenced-assembly developing (spout with
// water or potion fluid) stays as the automated way.

ServerEvents.recipes((event) => {
  const replace = (id, make) => {
    event.remove({ id: id })
    make().id(id.replace(/^exposure:/, 'firmages:exposure/'))
  }

  // ---- Bronze Age: camera, stand, lightroom, black-and-white film --------------------------------------
  replace('exposure:camera', () => event.shaped('exposure:camera', ['MSB', 'SLS', 'KKK'], {
    M: 'tfc:brass_mechanisms',
    S: 'tfc:metal/sheet/brass',
    B: '#minecraft:buttons',
    L: 'tfc:lens',
    K: 'minecraft:leather'
  }))
  replace('exposure:camera_stand', () => event.shaped('exposure:camera_stand', [' R ', 'SSS', 'WSW'], {
    R: 'tfc:metal/rod/brass',
    S: 'minecraft:stick',
    W: '#minecraft:wooden_slabs'
  }))
  replace('exposure:lightroom', () => event.shaped('exposure:lightroom', ['SLC', 'PPP', 'PPP'], {
    S: 'tfc:metal/sheet/brass',
    L: 'tfc:lens',
    C: 'tfc:candle',
    P: '#minecraft:planks'
  }))
  // silver halide on paper with a glue (gelatine) layer, on a brass spool
  replace('exposure:black_and_white_film', () => event.shaped('exposure:black_and_white_film', ['PGP', 'RSR', 'PGP'], {
    P: 'minecraft:paper',
    G: 'tfc:glue',
    R: 'tfc:metal/rod/brass',
    S: 'tfc:metal/ingot/silver'
  }))
  replace('exposure:album', () => event.shapeless('exposure:album', ['minecraft:writable_book', 'minecraft:leather']))
  replace('exposure:black_and_white_film_developing', () => event.custom({
    type: 'exposure:film_developing',
    category: 'misc',
    film: { tag: 'exposure:black_and_white_film_rolls' },
    ingredients: [{ item: 'tfc:powder/soda_ash' }, { item: 'tfc:powder/salt' }],
    result: { id: 'exposure:developed_black_and_white_film' }
  }))

  // ---- Iron Age: high-sensitivity black-and-white film (sulfur sensitising) -----------------------------
  replace('exposure:high_sensitivity_black_and_white_film', () => event.shaped(
    'exposure:high_sensitivity_black_and_white_film', ['PGP', 'RSR', 'PDP'], {
      P: 'minecraft:paper',
      G: 'tfc:glue',
      R: 'tfc:metal/rod/brass',
      S: 'tfc:metal/ingot/silver',
      D: 'tfc:powder/sulfur'
    }))

  // ---- Industrial Age: colour film (three dye layers) ---------------------------------------------------
  replace('exposure:color_film', () => event.shaped('exposure:color_film', ['RGB', 'PSP', 'TKT'], {
    R: '#c:dyes/red',
    G: '#c:dyes/green',
    B: '#c:dyes/blue',
    P: 'minecraft:paper',
    S: 'tfc:metal/ingot/silver',
    T: 'tfc:metal/rod/steel',
    K: 'tfc:glue'
  }))
  replace('exposure:high_sensitivity_color_film', () => event.shaped('exposure:high_sensitivity_color_film', ['RGB', 'PSP', 'TDT'], {
    R: '#c:dyes/red',
    G: '#c:dyes/green',
    B: '#c:dyes/blue',
    P: 'minecraft:paper',
    S: 'tfc:metal/ingot/silver',
    T: 'tfc:metal/rod/steel',
    D: 'tfc:powder/sulfur'
  }))
  replace('exposure:color_film_developing', () => event.custom({
    type: 'exposure:film_developing',
    category: 'misc',
    film: { tag: 'exposure:color_film_rolls' },
    ingredients: [{ item: 'tfc:powder/soda_ash' }, { item: 'tfc:powder/sulfur' }, { item: 'tfc:powder/saltpeter' }],
    result: { id: 'exposure:developed_color_film' }
  }))
})
