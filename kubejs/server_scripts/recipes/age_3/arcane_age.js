// Firmament Ages - Age 3: Arcane Age recipes.
// Occultism Dimensional Mineshaft: the first endless ore source gives only RICH TFC ore pieces of the metals
// the Arcane Age has unlocked (Doc 08 section 3 rule 6 and section 7: "nur Rich-Erze freigeschalteter Metalle").
// Occultism ships vanilla, Mekanism, IE, Create and Mystical Agriculture ores, raw-ore blocks and gems for its
// miners; all of those are replaced. Kept: Occultism's own materials (iesnium, otherstone, the mining core),
// Theurgy sal ammoniac (no world vein in TFC) and plain world blocks (stone, gravel, clay, obsidian, glowstone).
//
// Miner tags (Occultism 1.224.4 data/occultism/tags/item/miners): "ores" = every miner (Foliot, Djinni, Afrit,
// Marid), "deeps" = Afrit and Marid, "master" = Marid, "eldritch" = the ancient miner, "basic_resources" =
// the unspecialised Foliot. The Afrit (age_5) and Marid (age_8) miners are item-locked until their Ages.

ServerEvents.recipes((event) => {
  // Rich ore pieces of the metals unlocked by age_3 (dev/age_map.toml [tfc_ores]: copper 0, tin/zinc/bismuth/
  // silver/gold 1, iron 2). Weights follow Occultism's own ore weights (iron 750, copper 584, tin 602,
  // silver 381, gold 311, zinc 186), split evenly across the TFC ores of one metal; bismuth takes zinc's weight.
  const RICH_ORES = {
    native_copper: 195, malachite: 195, tetrahedrite: 194,
    cassiterite: 602,
    sphalerite: 186,
    bismuthinite: 186,
    native_silver: 381,
    native_gold: 311,
    hematite: 250, limonite: 250, magnetite: 250
  }

  // Every ore-type output of the shipped miner recipes. Ids from /fa_dump (2026-09-30, 92 miner recipes).
  const REMOVE = [
    /^occultism:miner\/ores\/(?!clay$|crying_obsidian$|glowstone$|gravel$|magma_block$|obsidian$|otherrock$|otherstone$|sal_ammoniac_ore$).+/,
    /^occultism:miner\/deeps\/.+/,
    /^occultism:miner\/eldritch\/(?!clay$|glowstone_dust$|mining_dim_core$|raw_iesnium$|sal_ammoniac$).+/,
    /^occultism:miner\/master\/ancient_debris$/,
    // End stone belongs to the End (age_6).
    /^occultism:miner\/basic_resources\/end_stone$/
  ]
  REMOVE.forEach((id) => event.remove({ type: 'occultism:miner', id: id }))

  Object.keys(RICH_ORES).forEach((ore) => {
    event.custom({
      type: 'occultism:miner',
      ingredient: { tag: 'occultism:miners/ores' },
      result: { type: 'occultism:weighted_item', stack: { id: `tfc:ore/rich_${ore}`, count: 1 }, weight: RICH_ORES[ore] }
    }).id(`firmages:miner/ores/rich_${ore}`)
  })
})
