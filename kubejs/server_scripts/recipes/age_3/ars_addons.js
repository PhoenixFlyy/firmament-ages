// Firmament Ages - Ars Nouveau add-ons of the Arcane Age (1.0 round C, vorauswahl M040 Ars Elemental, M041 Ars Ocultas;
// dev/decisions-while-away.md 2026-10-15).
// Ars Ocultas' Ritual Altar takes a Drygmy shard, and Drygmies are gone from the pack (no Drygmy spawns in a TFC world,
// [derived_hidden] in dev/age_map.toml). Conjuration essence takes its place: the Drygmy is a conjuration familiar.
// Ars Elemental's flashing archwood comes from its own sapling recipe (four Ars saplings), not from the flashing forest:
// its biomes are off (config/ars_elemental-common.toml extra_biomes = 0) and TFC's biome source never places them.
// The bee ritual and glyph take TFC beeswax instead of the vanilla honeycomb, which a TFC world does not make
// (same swap as recipes/deco_inputs.js).

ServerEvents.recipes((event) => {
  const swap = (id, from, to) => {
    let json = null
    event.forEachRecipe({ id: id }, (r) => { json = String(r.json) })
    if (json === null) {
      console.warn(`[firmages] ars add-ons: no recipe ${id}`)
      return
    }
    const next = json.split(`"item":"${from}"`).join(`"item":"${to}"`)
    if (next === json) {
      console.warn(`[firmages] ars add-ons: ${id} has no ${from}`)
      return
    }
    event.remove({ id: id })
    event.custom(JSON.parse(next)).id(id)
  }
  swap('ars_ocultas:altar', 'ars_nouveau:drygmy_shard', 'ars_nouveau:conjuration_essence')
  swap('ars_elemental:ritual_pollination', 'minecraft:honeycomb', 'firmalife:beeswax')
  swap('ars_elemental:glyph_summon_bee', 'minecraft:honeycomb', 'firmalife:beeswax')
})
