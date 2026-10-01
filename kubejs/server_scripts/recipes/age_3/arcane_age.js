// Firmament Ages - Age 3: Arcane Age recipes (Doc 08 sections 2.2 and 7, Doc 10 v3 sections 1.6, 6 and 7).
// Three required strands with one proof item each: Spirits (Occultism: Spirit Attuned Crystal + iesnium ingot),
// Source (Ars Nouveau: Wilden Tribute from the Wilden Chimera) and Alchemy (Theurgy: Mercury Catalyst). The goal,
// the Arcane Keystone, is made from the three proofs, a TFC steel ingot and a source gem block in the Djinni
// pentacle ritual (Doc 08 section 7.1). TFC input adaptations of the magic mods live in arcane_tfc_inputs.js.
//
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
  // One type pass, the patterns tested on the ~90 miner ids: every regex filter scans all ~35,000 recipes (about
  // 17 ms each on every Age reload).
  const minerIds = []
  event.forEachRecipe({ type: 'occultism:miner' }, (r) => {
    const id = String(r.getId())
    if (REMOVE.some((re) => re.test(id))) minerIds.push(id)
  })
  minerIds.forEach((id) => event.remove({ id: id }))

  Object.keys(RICH_ORES).forEach((ore) => {
    event.custom({
      type: 'occultism:miner',
      ingredient: { tag: 'occultism:miners/ores' },
      result: { type: 'occultism:weighted_item', stack: { id: `tfc:ore/rich_${ore}`, count: 1 }, weight: RICH_ORES[ore] }
    }).id(`firmages:miner/ores/rich_${ore}`)
  })
})

ServerEvents.recipes((event) => {
  // ---- One carrier per magic function (Doc 10 v3 section 1.6, section 7.3) ----------------------------------
  // Ore yield is Theurgy's job: the Occultism crusher keeps only Occultism's own and non-metal materials
  // (no ore, raw ore, raw block, Mekanism clump or metal ingot recipes; iesnium stays).
  const removeCrushing = []
  event.forEachRecipe({ type: 'occultism:crushing' }, (r) => {
    const m = /"tag":"([^"]+)"/.exec(String(r.json.get('ingredient')))
    const tagName = m ? m[1] : ''
    const iesnium = tagName.indexOf('iesnium') >= 0 // Occultism's own Nether metal stays
    const oreLike = /^c:(ores|raw_materials|clumps)\//.test(tagName) || /^c:storage_blocks\/raw_/.test(tagName)
    const metalIngot = /^c:ingots\//.test(tagName)
    if ((oreLike || metalIngot) && !iesnium) removeCrushing.push(String(r.getId()))
  })
  removeCrushing.forEach((id) => event.remove({ id: id }))
  // Item transport is the Starbuncles' job; the Foliot transporter ritual goes (Doc 10 v3 section 7.3).
  event.remove({ id: 'occultism:ritual/summon_foliot_transport_items' })
  // Remote storage is Occultism's Dimensional Storage until AE2 (age_6); the Ars Additions Warp Index goes.
  event.remove({ id: 'ars_additions:apparatus/warp_index' })
  event.remove({ id: 'ars_additions:apparatus/stabilized_warp_index' })
  // Ore leak (Doc 08 section 3.4): the Ritual of Scrying must not find diamonds (kimberlite opens with age_4).
  event.remove({ id: 'ars_nouveau:scry_ritual/diamond_ores' })

  // ---- Theurgy spagyrics on TFC ore pieces (Doc 08 section 4.2: Arcane Age ore line, 2.5x) --------------------
  // TFC ore pieces sit in c:raw_materials/<metal> and TFC ore blocks in c:ores/<metal>, so Theurgy's standard
  // recipes (raw -> 3 sulfur, ore block -> 5 sulfur) would turn a 10 mB small piece into 300 mB. They go.
  const standardOre = []
  event.forEachRecipe({ type: 'theurgy:liquefaction' }, (r) => {
    const t = String(r.json.get('ingredient'))
    if (/"tag":"c:(ores|raw_materials)\//.test(t) || /"tag":"c:storage_blocks\/raw_/.test(t)) standardOre.push(String(r.getId()))
  })
  standardOre.forEach((id) => event.remove({ id: id }))
  // Liquefaction takes one item without a count, so only RICH pieces (35 mB) are dissolved: one sulfur = one
  // canonical dust (100 mB), about 2.9x. Poor, normal and small pieces go to the IE Crusher or Create.
  // The Mineshaft gives only rich pieces, so the Arcane ore line is Mineshaft -> Liquefaction -> Incubator.
  const SPAGYRIC = {
    'tfc:ore/rich_native_copper': 'copper', 'tfc:ore/rich_malachite': 'copper', 'tfc:ore/rich_tetrahedrite': 'copper',
    'tfc:ore/rich_cassiterite': 'tin', 'tfc:ore/rich_sphalerite': 'zinc', 'tfc:ore/rich_native_gold': 'gold',
    'tfc:ore/rich_native_silver': 'silver', 'tfc:ore/rich_hematite': 'iron', 'tfc:ore/rich_limonite': 'iron',
    'tfc:ore/rich_magnetite': 'iron', 'tfc:ore/rich_garnierite': 'nickel', 'tfc_ie_addon:ore/rich_galena': 'lead',
    'tfc_ie_addon:ore/rich_bauxite': 'aluminum', 'tfc_ie_addon:ore/rich_uraninite': 'uranium',
    'mekatfc:ore/rich_native_osmium': 'osmium'
  }
  Object.keys(SPAGYRIC).forEach((piece) => {
    event.custom({
      type: 'theurgy:liquefaction',
      ingredient: { item: piece },
      solvent: { ingredient: { fluid: 'theurgy:sal_ammoniac' }, amount: 10 },
      result: { type: 'theurgy:item', id: `theurgy:alchemical_sulfur_${SPAGYRIC[piece]}`, count: 1 },
      time: 100
    }).id(`firmages:liquefaction/${piece.replace(/^[a-z_]+:ore\//, '')}`)
  })
  // Incubation gives the canonical DUST (Doc 10 v3 section 8.1), not an ingot: iron ores melt to cast iron in
  // TFC, and the wrought iron ingot must keep coming from the bloomery.
  const CANON_DUST = {
    iron: 'mekanism:dust_iron', copper: 'mekanism:dust_copper', gold: 'mekanism:dust_gold', tin: 'mekanism:dust_tin',
    lead: 'mekanism:dust_lead', osmium: 'mekanism:dust_osmium', uranium: 'mekanism:dust_uranium',
    silver: 'immersiveengineering:dust_silver', nickel: 'immersiveengineering:dust_nickel',
    aluminum: 'immersiveengineering:dust_aluminum', zinc: 'firmages:dust/zinc'
  }
  Object.keys(CANON_DUST).forEach((m) => {
    event.remove({ id: `theurgy:incubation/ingots_${m}_from_alchemical_sulfur_${m}` })
    event.custom({
      type: 'theurgy:incubation',
      mercury: { item: 'theurgy:mercury_shard' },
      salt: { item: 'theurgy:alchemical_salt_mineral' },
      sulfur: { item: `theurgy:alchemical_sulfur_${m}` },
      result: { type: 'theurgy:item', id: CANON_DUST[m], count: 1 },
      time: 100
    }).id(`firmages:incubation/dust_${m}`)
  })

  // ---- World resources a TFC world does not generate ----------------------------------------------------------
  // Archwood trees and source berries grow only in Ars biomes, which TFC's biome source never places (0 Ars blocks
  // in the 3,219-chunk scan of 2026-09-30). A TFC sapling takes on the colour of a TFC gem powder from the quern.
  const ARCHWOOD = { blue: 'lapis_lazuli', red: 'ruby', green: 'emerald', purple: 'amethyst' }
  Object.keys(ARCHWOOD).forEach((c) => {
    event.shaped(`ars_nouveau:${c}_archwood_sapling`, [' P ', 'PSP', ' P '], {
      P: `tfc:powder/${ARCHWOOD[c]}`,
      S: '#minecraft:saplings'
    }).id(`firmages:crafting/${c}_archwood_sapling`)
  })
  event.shapeless('ars_nouveau:sourceberry_bush', ['tfc:food/blueberry', 'tfc:powder/lapis_lazuli', 'tfc:powder/lapis_lazuli'])
    .id('firmages:crafting/sourceberry_bush')
  // "Arcane Soil" (Doc 08 section 7.2): Datura and Magebloom only grow on vanilla farmland; on TFC farmland they
  // break at the next block update (setblock test on the pack server, 2026-09-30). Amethyst-charged TFC dirt gives
  // vanilla farmland; once it dries to dirt, any hoe tills it again.
  event.shaped('8x minecraft:farmland', ['DDD', 'DAD', 'DDD'], {
    D: '#tfc:dirt',
    A: '#c:gems/amethyst'
  }).id('firmages:crafting/arcane_soil')
  // Summoning Rituals ships no altar recipe; the altar is only for boss summons (Doc 10 v3 section 1.6).
  event.shaped('summoningrituals:altar', [' A ', 'GSG', 'RRR'], {
    A: 'occultism:spirit_attuned_gem',
    G: '#c:ingots/gold',
    S: '#c:storage_blocks/source',
    R: '#c:stones'
  }).id('firmages:crafting/summoning_altar')

  // ---- Goal: Arcane Keystone (Doc 08 sections 7.1 and 10.2) --------------------------------------------------
  // Djinni crafting pentacle, bound Djinni book. The Wilden Tribute slot is the age_3 boss token (Doc 08 section 5.1).
  event.custom({
    type: 'occultism:ritual',
    ritual_type: 'occultism:craft',
    pentacle_id: 'occultism:craft_djinni',
    activation_item: { item: 'occultism:book_of_binding_bound_djinni' },
    duration: 240,
    ritual_dummy: { id: 'occultism:ritual_dummy/custom_ritual_craft', count: 1 },
    ingredients: [
      { item: 'occultism:spirit_attuned_crystal' },
      { item: 'occultism:iesnium_ingot' },
      { tag: 'firmages:boss_token/age_3' },
      { item: 'theurgy:mercury_catalyst' },
      { item: 'tfc:metal/ingot/steel' },
      { item: 'ars_nouveau:source_gem_block' }
    ],
    result: { id: 'firmages:arcane_keystone', count: 1 }
  }).id('firmages:ritual/arcane_keystone')
})
