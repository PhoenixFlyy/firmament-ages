// Firmament Ages - Age 3: the magic mods in a TFC world (Doc 08 section 7.2 "TFC-Nacharbeit", Doc 10 v3 section 1.6).
// Occultism, Ars Nouveau (+ Additions, Creo), Theurgy, Occult Engineering and Summoning Rituals ask for vanilla
// items that a TFC world never generates and that have no recipe here (vanilla tools and armour, crafting table,
// furnace, glass bottle, oak sapling, wheat, andesite, ...). Every such input is swapped for its TFC counterpart.
// Found with a recipe-graph closure over the /fa_dump_full export (2026-09-30): an item counts as obtainable when a
// TFC-world source or a recipe from obtainable inputs makes it (dev/poc_analyze.py, check "arcane chain").
//
// Technique: Ars Nouveau recipe types have no KubeJS schema, so replaceInput does not touch them. Every recipe of
// the magic mods that names one of the items below is re-read as JSON, rewritten and re-added under its own id.
// Only ingredient objects ("item": ...) change; results use "id" in 1.21 and stay untouched.
// Gear rule (Doc 08 section 8): tools and armour inside these recipes become tool heads, unfinished armour or
// sheets, never a finished tool or armour piece; the three apparatus upgrades of a finished tool (GEAR_FIX) take
// materials instead.

ServerEvents.recipes((event) => {
  const item = (id) => ({ item: id })
  const tag = (id) => ({ tag: id })

  // vanilla item -> TFC ingredient. Tool metals: stone/copper for stone tools, wrought iron for iron, steel for
  // diamond, black steel for netherite; gold has no TFC tools, so gold gear becomes gold double ingots or sheets.
  const MAP = {
    'minecraft:glass_bottle': tag('firmages:glass_bottles'),
    'minecraft:bucket': item('tfc:wooden_bucket'),
    'minecraft:crafting_table': tag('tfc:workbenches'),
    'minecraft:furnace': item('tfc:crucible'),
    'minecraft:blast_furnace': item('tfc:blast_furnace'),
    'minecraft:smoker': item('tfc:firepit'),
    'minecraft:campfire': item('tfc:firepit'),
    'minecraft:anvil': tag('tfc:anvils'),
    // Content fixes after the late Ages (2026-10-01): a soul campfire is a firepit like the campfire, the TFC lecterns
    // (tags/tfc_stations.js) for the Ars storage lectern, feathers for the fletching table of the projectile glyph,
    // and milk, a fluid in a TFC bucket, through TFC's fluid-content test (abjuration essence, the dispel glyph and
    // two Ars Additions charms).
    'minecraft:soul_campfire': item('tfc:firepit'),
    'minecraft:lectern': tag('firmages:lecterns'),
    'minecraft:fletching_table': tag('c:feathers'),
    'minecraft:milk_bucket': { type: 'tfc:fluid_content', fluid: { fluid: 'minecraft:milk', amount: 1000 } },
    'minecraft:lantern': tag('tfc:lamps'),
    'minecraft:tnt': item('minecraft:gunpowder'),
    'minecraft:mushroom_stew': item('minecraft:brown_mushroom'),
    'minecraft:torchflower': tag('minecraft:small_flowers'),
    'minecraft:torchflower_seeds': tag('minecraft:small_flowers'),
    'minecraft:oak_sapling': tag('minecraft:saplings'),
    'minecraft:spruce_sapling': tag('minecraft:saplings'),
    'minecraft:birch_sapling': tag('minecraft:saplings'),
    'minecraft:jungle_sapling': tag('minecraft:saplings'),
    'minecraft:acacia_sapling': tag('minecraft:saplings'),
    'minecraft:dark_oak_sapling': tag('minecraft:saplings'),
    'minecraft:wheat': item('tfc:food/wheat'),
    'minecraft:carrot': item('tfc:food/carrot'),
    'minecraft:potato': item('tfc:food/potato'),
    'minecraft:apple': item('tfc:food/red_apple'),
    'minecraft:pumpkin': item('tfc:pumpkin'),
    'minecraft:porkchop': item('tfc:food/pork'),
    'minecraft:beef': item('tfc:food/beef'),
    'minecraft:mutton': item('tfc:food/mutton'),
    'minecraft:chicken': item('tfc:food/chicken'),
    'minecraft:stone': tag('c:stones'),
    'minecraft:cobblestone': tag('c:cobblestones/normal'),
    'minecraft:andesite': item('tfc:rock/raw/andesite'),
    'minecraft:diorite': item('tfc:rock/raw/diorite'),
    'minecraft:granite': item('tfc:rock/raw/granite'),
    'minecraft:deepslate': item('tfc:rock/raw/gabbro'),
    'minecraft:calcite': item('tfc:rock/raw/marble'),
    'minecraft:tuff': item('tfc:rock/raw/basalt'),
    'minecraft:dripstone_block': item('tfc:rock/raw/limestone'),
    'minecraft:sand': tag('c:sands'),
    'minecraft:gravel': tag('c:gravels'),
    'minecraft:fishing_rod': tag('c:tools/fishing_rod'),
    'minecraft:shears': tag('c:tools/shear'),
    // gear rule: the Iesnium Butcher Knife ritual took the finished butcher knife; a steel knife blade instead
    'occultism:butcher_knife': item('tfc:metal/knife_blade/steel'),
    'minecraft:shield': item('tfc:metal/double_sheet/wrought_iron'),
    'minecraft:leather_boots': item('minecraft:leather'),
    'minecraft:stone_pickaxe': item('tfc:metal/pickaxe_head/copper'),
    'minecraft:stone_shovel': item('tfc:metal/shovel_head/copper'),
    'minecraft:stone_axe': item('tfc:metal/axe_head/copper'),
    'minecraft:stone_hoe': item('tfc:metal/hoe_head/copper'),
    'minecraft:stone_sword': item('tfc:metal/sword_blade/copper'),
    'minecraft:iron_pickaxe': item('tfc:metal/pickaxe_head/wrought_iron'),
    'minecraft:iron_shovel': item('tfc:metal/shovel_head/wrought_iron'),
    'minecraft:iron_axe': item('tfc:metal/axe_head/wrought_iron'),
    'minecraft:iron_hoe': item('tfc:metal/hoe_head/wrought_iron'),
    'minecraft:iron_sword': item('tfc:metal/sword_blade/wrought_iron'),
    'minecraft:golden_pickaxe': item('tfc:metal/double_ingot/gold'),
    'minecraft:golden_sword': item('tfc:metal/double_ingot/gold'),
    'minecraft:diamond_pickaxe': item('tfc:metal/pickaxe_head/steel'),
    'minecraft:diamond_shovel': item('tfc:metal/shovel_head/steel'),
    'minecraft:diamond_axe': item('tfc:metal/axe_head/steel'),
    'minecraft:diamond_sword': item('tfc:metal/sword_blade/steel'),
    'minecraft:netherite_pickaxe': item('tfc:metal/pickaxe_head/black_steel'),
    'minecraft:netherite_sword': item('tfc:metal/sword_blade/black_steel'),
    'minecraft:iron_helmet': item('tfc:metal/unfinished_helmet/wrought_iron'),
    'minecraft:iron_chestplate': item('tfc:metal/unfinished_chestplate/wrought_iron'),
    'minecraft:iron_leggings': item('tfc:metal/unfinished_greaves/wrought_iron'),
    'minecraft:iron_boots': item('tfc:metal/unfinished_boots/wrought_iron'),
    'minecraft:chainmail_helmet': item('tfc:metal/unfinished_helmet/wrought_iron'),
    'minecraft:chainmail_chestplate': item('tfc:metal/unfinished_chestplate/wrought_iron'),
    'minecraft:chainmail_leggings': item('tfc:metal/unfinished_greaves/wrought_iron'),
    'minecraft:chainmail_boots': item('tfc:metal/unfinished_boots/wrought_iron'),
    'minecraft:golden_helmet': item('tfc:metal/double_sheet/gold'),
    'minecraft:golden_chestplate': item('tfc:metal/double_sheet/gold'),
    'minecraft:golden_leggings': item('tfc:metal/double_sheet/gold'),
    'minecraft:golden_boots': item('tfc:metal/double_sheet/gold'),
    'minecraft:diamond_helmet': item('tfc:metal/unfinished_helmet/steel'),
    'minecraft:diamond_chestplate': item('tfc:metal/unfinished_chestplate/steel'),
    'minecraft:diamond_leggings': item('tfc:metal/unfinished_greaves/steel'),
    'minecraft:diamond_boots': item('tfc:metal/unfinished_boots/steel')
  }

  // Diamond opens with age_4 (kimberlite, Doc 08 section 3.2). Recipes of the magic mods whose result is an Arcane
  // Age item take an Arcane Age gem instead (tag firmages:gems/arcane) and a source gem block for a diamond block.
  // List from the result Ages in kubejs/data/firmages/tags/item/age_items/ (2026-09-30); enchantment recipes
  // have no result item and keep their diamonds, so higher enchantment levels come with the Industrial Age.
  const DIAMOND_TO_ARCANE = [
    'ars_nouveau:amulet_of_mana_boost', 'ars_nouveau:amulet_of_mana_regen', 'ars_nouveau:apprentice_book_upgrade',
    'ars_nouveau:enchanters_gauntlet', 'ars_nouveau:enchanters_sword', 'ars_nouveau:enchanting_apparatus',
    'ars_nouveau:glyph_glide', 'ars_nouveau:glyph_linger', 'ars_nouveau:glyph_wall', 'ars_nouveau:imbuement_amplify_arrow',
    'ars_nouveau:planarium', 'ars_nouveau:ring_of_greater_discount', 'ars_nouveau:ring_of_lesser_discount',
    'ars_nouveau:ritual_flight', 'ars_nouveau:scryers_oculus', 'ars_nouveau:shapers_focus', 'ars_nouveau:whirlisprig_charm',
    'occultism:ritual/craft_soul_gem', 'occultism:spirit_fire/spirit_attuned_gem',
    'theurgy:crafting/shaped/divination_rod_t3', 'theurgy:crafting/shaped/sulfur_attuned_divination_rod_precious'
  ]

  // Gear rule for Arcane gear (review 2026-10-01): these apparatus recipes upgrade a finished tool (fishing rod, bow,
  // crossbow) as the reagent. The reagent becomes a material of the tier (archwood log, TFC steel rod) and the
  // string the old tool carried moves onto a pedestal. dev/poc_analyze.py check C-3g guards it.
  const GEAR_FIX = {
    'ars_nouveau:enchanters_fishing_rod': { reagent: item('tfc:metal/rod/steel'), add: [tag('c:strings')] },
    'ars_nouveau:spell_bow': { reagent: tag('c:logs/archwood'), add: [tag('c:strings'), tag('c:strings')] },
    'ars_nouveau:spell_crossbow': { reagent: tag('c:logs/archwood'),
      add: [tag('c:strings'), tag('c:strings'), item('tfc:metal/rod/steel')] }
  }

  // One regex pass per recipe text finds every '"item":"<mapped id>"' (the closing quote makes it an exact id match).
  // It replaces one split/join per MAP key: 80 string passes over each of the ~2,500 magic recipes cost about 0.4 s
  // of every Age reload (dev/poc-results.md, "Reload performance").
  // Item ids are [a-z0-9_:/] only, so they need no escaping inside the alternation.
  const ITEM_RE = new RegExp(`"item":"(${Object.keys(MAP).join('|')})"`, 'g')
  const TO_ARCANE = {}
  DIAMOND_TO_ARCANE.forEach((id) => { TO_ARCANE[id] = true })
  const rewrite = (text, id) => {
    let out = text.replace(ITEM_RE, (m, from) => JSON.stringify(MAP[from]).slice(1, -1))
    if (TO_ARCANE[id]) {
      out = out.split('"tag":"c:gems/diamond"').join('"tag":"firmages:gems/arcane"')
        .split('"item":"minecraft:diamond"').join('"tag":"firmages:gems/arcane"')
        .split('"tag":"c:storage_blocks/diamond"').join('"item":"ars_nouveau:source_gem_block"')
    }
    const fix = GEAR_FIX[id]
    if (fix) {
      // let, not const: Rhino rejects a block-scoped const on the second call ("redeclaration of var j")
      let j = JSON.parse(out)
      j.reagent = fix.reagent
      j.pedestalItems = (j.pedestalItems || []).concat(fix.add)
      j.keepNbtOfReagent = false
      out = JSON.stringify(j)
    }
    return out
  }

  // The magic recipes that name a MAP item, an Arcane diamond or a GEAR_FIX reagent, as fixed ids: 167 of the 2,714
  // recipes of the magic mods (Occultism, Ars Nouveau and Additions, Theurgy; read from the original recipes on
  // 2026-10-01). A plain id list is a map lookup; reading every magic recipe as JSON cost about 55 ms of every reload.
  // dev/poc_analyze.py check C-3m reports a magic recipe that still names a MAP item (add its id here).
  const REWRITE_IDS = [
    'ars_additions:apparatus/dispel_protection_charm', 'ars_additions:apparatus/ender_mask_charm',
    'ars_additions:apparatus/fall_prevention_charm', 'ars_additions:apparatus/fire_resistance_charm',
    'ars_additions:apparatus/golden_charm', 'ars_additions:apparatus/night_vision_charm',
    'ars_additions:apparatus/powdered_snow_walk_charm', 'ars_additions:apparatus/sonic_boom_protection_charm',
    'ars_additions:apparatus/undying_charm', 'ars_additions:apparatus/void_protection_charm',
    'ars_additions:apparatus/water_breathing_charm', 'ars_additions:apparatus/wither_protection_charm',
    'ars_additions:apparatus/xp_jar', 'ars_additions:glyph_retaliate', 'ars_nouveau:agronomic_sourcelink',
    'ars_nouveau:air_essence_to_snow_bucket', 'ars_nouveau:alchemists_crown', 'ars_nouveau:amulet_of_mana_boost',
    'ars_nouveau:amulet_of_mana_regen', 'ars_nouveau:apprentice_book_upgrade', 'ars_nouveau:arcanist_boots',
    'ars_nouveau:arcanist_hood', 'ars_nouveau:arcanist_leggings', 'ars_nouveau:arcanist_robes',
    'ars_nouveau:battlemage_boots', 'ars_nouveau:battlemage_hood', 'ars_nouveau:battlemage_leggings',
    'ars_nouveau:battlemage_robes', 'ars_nouveau:drygmy_charm', 'ars_nouveau:efficiency_1',
    'ars_nouveau:efficiency_2', 'ars_nouveau:efficiency_3', 'ars_nouveau:efficiency_4', 'ars_nouveau:efficiency_5',
    'ars_nouveau:enchanters_fishing_rod', 'ars_nouveau:enchanters_gauntlet', 'ars_nouveau:enchanters_shield',
    'ars_nouveau:enchanters_sword', 'ars_nouveau:enchanting_apparatus', 'ars_nouveau:glyph_amplify',
    'ars_nouveau:glyph_break', 'ars_nouveau:glyph_burst', 'ars_nouveau:glyph_craft', 'ars_nouveau:glyph_cut',
    'ars_nouveau:glyph_dispel', 'ars_nouveau:glyph_explosion', 'ars_nouveau:glyph_fell', 'ars_nouveau:glyph_glide',
    'ars_nouveau:glyph_gravity', 'ars_nouveau:glyph_harm', 'ars_nouveau:glyph_harvest', 'ars_nouveau:glyph_infuse',
    'ars_nouveau:glyph_light', 'ars_nouveau:glyph_linger', 'ars_nouveau:glyph_projectile', 'ars_nouveau:glyph_pull',
    'ars_nouveau:glyph_self', 'ars_nouveau:glyph_smelt', 'ars_nouveau:glyph_underfoot', 'ars_nouveau:glyph_wall',
    'ars_nouveau:glyph_wind_shear', 'ars_nouveau:imbuement_abjuration_essence', 'ars_nouveau:imbuement_amplify_arrow',
    'ars_nouveau:jar_of_light', 'ars_nouveau:manipulation_essence_to_andesite',
    'ars_nouveau:manipulation_essence_to_calcite', 'ars_nouveau:manipulation_essence_to_deepslate',
    'ars_nouveau:manipulation_essence_to_diorite', 'ars_nouveau:manipulation_essence_to_granite',
    'ars_nouveau:manipulation_essence_to_tuff', 'ars_nouveau:mycelial_sourcelink', 'ars_nouveau:novice_spell_book',
    'ars_nouveau:novice_spellbook_alt', 'ars_nouveau:planarium', 'ars_nouveau:potion_diffuser',
    'ars_nouveau:potion_flask', 'ars_nouveau:ring_of_greater_discount', 'ars_nouveau:ring_of_lesser_discount',
    'ars_nouveau:ritual_burrowing', 'ars_nouveau:ritual_conjure_island_desert', 'ars_nouveau:ritual_containment',
    'ars_nouveau:ritual_disintegration', 'ars_nouveau:ritual_fertility', 'ars_nouveau:ritual_flight',
    'ars_nouveau:ritual_gravity', 'ars_nouveau:ritual_harvest', 'ars_nouveau:scryers_oculus',
    'ars_nouveau:shapers_focus', 'ars_nouveau:sorcerer_boots', 'ars_nouveau:sorcerer_hood',
    'ars_nouveau:sorcerer_leggings', 'ars_nouveau:sorcerer_robes', 'ars_nouveau:source_berry_roll',
    'ars_nouveau:spell_bow', 'ars_nouveau:spell_crossbow', 'ars_nouveau:storage_lectern',
    'ars_nouveau:thread_repairing', 'ars_nouveau:void_jar', 'ars_nouveau:water_essence_to_bucket',
    'ars_nouveau:whirlisprig_charm', 'ars_nouveau:wilden_summon_alt', 'ars_nouveau:wixie_charm',
    'occultism:crafting/book_of_calling_djinni_manage_machine', 'occultism:crafting/chalk_red_impure',
    'occultism:crafting/nature_paste_mossy_cobblestone', 'occultism:crushing/calcite_dust',
    'occultism:ritual/craft_iesnium_anvil', 'occultism:ritual/craft_iesnium_butcher_knife',
    'occultism:ritual/craft_miner_marid_master', 'occultism:ritual/craft_soul_gem',
    'occultism:ritual/familiar_blacksmith', 'occultism:ritual/familiar_chimera',
    'occultism:ritual/misc_reinforced_deepslate', 'occultism:ritual/misc_wild_trim',
    'occultism:ritual/possess_hoglin', 'occultism:ritual/possess_random_animal_rideable',
    'occultism:ritual/possess_villager', 'occultism:ritual/possess_witch',
    'occultism:ritual/summon_afrit_crystallizer', 'occultism:ritual/summon_afrit_smelter',
    'occultism:ritual/summon_demonic_husband', 'occultism:ritual/summon_demonic_wife',
    'occultism:ritual/summon_djinni_day_time', 'occultism:ritual/summon_djinni_manage_machine',
    'occultism:ritual/summon_djinni_smelter', 'occultism:ritual/summon_foliot_farmer',
    'occultism:ritual/summon_foliot_lumberjack', 'occultism:ritual/summon_foliot_otherrock_trader',
    'occultism:ritual/summon_foliot_otherstone_trader', 'occultism:ritual/summon_foliot_sapling_trader',
    'occultism:ritual/summon_foliot_smelter', 'occultism:ritual/wild_creeper', 'occultism:ritual/wild_drowned',
    'occultism:ritual/wild_random_animal_rideable', 'occultism:ritual/wild_silverfish',
    'occultism:ritual/wild_villager', 'occultism:spirit_fire/otherrock', 'occultism:spirit_fire/otherstone',
    'occultism:spirit_fire/otherworld_sapling_natural', 'occultism:spirit_fire/spirit_attuned_gem',
    'theurgy:calcination/alchemical_salt_creature_from_beef',
    'theurgy:calcination/alchemical_salt_creature_from_chicken',
    'theurgy:calcination/alchemical_salt_creature_from_mutton',
    'theurgy:calcination/alchemical_salt_creature_from_porkchop',
    'theurgy:calcination/alchemical_salt_strata_from_gravel',
    'theurgy:crafting/shaped/caloric_flux_emitter_from_campfire', 'theurgy:crafting/shaped/divination_rod_t3',
    'theurgy:crafting/shaped/sulfur_attuned_divination_rod_precious', 'theurgy:crafting/shapeless/lava_bucket',
    'theurgy:crafting/shapeless/water_bucket', 'theurgy:distillation/beef', 'theurgy:distillation/chicken',
    'theurgy:distillation/porkchop', 'theurgy:liquefaction/alchemical_sulfur_andesite_from_andesite',
    'theurgy:liquefaction/alchemical_sulfur_apple_from_apple',
    'theurgy:liquefaction/alchemical_sulfur_beef_from_beef',
    'theurgy:liquefaction/alchemical_sulfur_carrot_from_carrot',
    'theurgy:liquefaction/alchemical_sulfur_chicken_from_chicken',
    'theurgy:liquefaction/alchemical_sulfur_deepslate_from_deepslate',
    'theurgy:liquefaction/alchemical_sulfur_diorite_from_diorite',
    'theurgy:liquefaction/alchemical_sulfur_granite_from_granite',
    'theurgy:liquefaction/alchemical_sulfur_gravel_from_gravel',
    'theurgy:liquefaction/alchemical_sulfur_mutton_from_mutton',
    'theurgy:liquefaction/alchemical_sulfur_porkchop_from_porkchop',
    'theurgy:liquefaction/alchemical_sulfur_potato_from_potato',
    'theurgy:liquefaction/alchemical_sulfur_stone_from_stone',
    'theurgy:liquefaction/alchemical_sulfur_wheat_from_wheat'
  ]
  const todo = []
  event.forEachRecipe(REWRITE_IDS.map((id) => ({ id: id })), (r) => {
    const id = String(r.getId())
    const text = String(r.json)
    const next = rewrite(text, id)
    if (next !== text) todo.push([id, next])
  })
  // A rewritten machine recipe (ritual, apparatus, Theurgy) is re-added only once its station's Age is open:
  // recipes/station_ages.js removes the mods' originals, but not recipes re-added in this event. Grid recipes keep
  // their ProgressiveStages namespace lock and are always re-added.
  const age3 = FirmAges.isUnlocked('age_3')
  todo.forEach((t) => {
    event.remove({ id: t[0] })
    const j = JSON.parse(t[1])
    if (!age3 && !/^(minecraft:crafting|kubejs:)/.test(String(j.type))) return
    event.custom(j).id(t[0])
  })
  console.info(`[firmages] arcane TFC inputs: ${todo.length} magic recipes rewritten`)

  // ---- Targeted fixes the generic map cannot express ----------------------------------------------------------
  // Purple chalk (Djinni pentacles, the Arcane Keystone) asks for End stone dust; the End opens with age_6.
  // TFC amethyst powder takes its place (amethyst veins open with age_3).
  event.replaceInput({ id: 'occultism:crafting/chalk_purple_impure' }, '#c:dusts/end_stone', 'tfc:powder/amethyst')
  // The first golden apple, carrot and glistering melon need TFC produce (vanilla crops do not grow here).
  event.replaceInput({ id: 'minecraft:golden_apple' }, 'minecraft:apple', 'tfc:food/red_apple')
  event.replaceInput({ id: 'minecraft:golden_carrot' }, 'minecraft:carrot', 'tfc:food/carrot')
  event.replaceInput({ id: 'minecraft:glistering_melon_slice' }, 'minecraft:melon_slice', 'tfc:food/melon_slice')
})
