// Firmament Ages - Alex's Mobs Continued, only the species TerraFirmaCraft lacks, each in its TFC climate
// (vorauswahl V1B, dev/decisions-while-away.md 2026-10-15, species table there).
// The species spawn through kubejs/data/firmages/neoforge/biome_modifier/alexsmobs_*_spawn_tfc.json in TFC landforms
// (firmages:wildlife_land, _water, _ocean); TFC biomes are landforms, not climates, so this script turns away a
// natural spawn whose average temperature (deg C) or yearly rainfall (mm) at the spot is outside the species' window,
// as TerraFirmaCraft does for its own animals. Alex's Mobs' own spawning is off (config/amc.json: every weight 0).

(() => {
  const Climate = Java.loadClass('net.dries007.tfc.util.climate.Climate')
  // species: [min temp, max temp, min rain, max rain]
  const WINDOWS = {
    elephant: [18, 99, 100, 300],
    rhinoceros: [18, 99, 100, 350],
    gorilla: [20, 99, 350, 999],
    capuchin_monkey: [20, 99, 300, 999],
    gelada_monkey: [5, 20, 150, 400],
    kangaroo: [15, 99, 75, 250],
    emu: [12, 99, 75, 300],
    platypus: [8, 25, 200, 999],
    toucan: [20, 99, 300, 999],
    hummingbird: [15, 99, 150, 999],
    blue_jay: [5, 20, 150, 400],
    anteater: [18, 99, 150, 450],
    roadrunner: [15, 99, 0, 150],
    shoebill: [20, 99, 250, 999],
    sugar_glider: [15, 99, 200, 999],
    potoo: [20, 99, 300, 999],
    flying_fish: [18, 99, 0, 999],
    mudskipper: [20, 99, 200, 999],
    rattlesnake: [15, 99, 0, 200],
    komodo_dragon: [22, 99, 100, 300],
    raccoon: [0, 22, 150, 999],
    tasmanian_devil: [5, 18, 150, 400],
    // G3 (Felix O1A): sea species TFC lacks; rain does not matter at sea
    comb_jelly: [-99, 99, 0, 9999],
    blobfish: [-99, 15, 0, 9999],
    mimic_octopus: [18, 99, 0, 9999],
    cachalot_whale: [-99, 99, 0, 9999],
    hammerhead_shark: [15, 99, 0, 9999],
    frilled_shark: [-99, 99, 0, 9999],
    giant_squid: [-99, 99, 0, 9999]
  }
  const CHECKED = ['NATURAL', 'CHUNK_GENERATION']

  Object.keys(WINDOWS).forEach((species) => {
    const win = WINDOWS[species]
    EntityEvents.checkSpawn(`alexsmobs:${species}`, (event) => {
      if (CHECKED.indexOf(String(event.getType())) < 0) return
      const level = event.getLevel()
      if (String(level.dimension().location()) !== 'minecraft:overworld') return
      const pos = event.getBlock().getPos()
      const temp = Climate.getAverageTemperature(level, pos)
      const rain = Climate.getAverageRainfall(level, pos)
      if (temp < win[0] || temp > win[1] || rain < win[2] || rain > win[3]) event.cancel()
    })
  })
})()
