// Firmament Ages - the gear rule for the tech mods of the Electric to the Singularity Age (Doc 08 section 8,
// Felix's condition 15): no tool and no armour piece is an ingredient of a better one; a later tier gets a DIRECT
// recipe from the materials of its own Age. Finished vanilla tools and armour that a TFC world cannot make become the
// TFC part of the same slot (the MAP rule of recipes/age_3/arcane_tfc_inputs.js: iron -> wrought iron, diamond ->
// steel, netherite -> black steel; gold -> gold double sheet, a vanilla shield -> steel double sheet; shears stay a
// tool via #c:tools/shear).
// Exceptions kept (Doc 08 section 8, r9 A / s6 A): MekaSuit and Meka-Tool (trophies after finale_won), modules of the
// MekaSuit and of Draconic gear (removable upgrades; only their vanilla-gear inputs change), backpack upgrades.
// Disabled instead (stages/disabled.toml): every paxel, the Mekanism Tools bronze/steel/osmium/lapis sets, Mystical
// Agriculture tools, armour and augments, AdvancedAE Quantum Armor, the DE mob grinder.
// The list of every recipe changed here is in dev/decisions-while-away.md (gear rule, 2026-10-01).

ServerEvents.recipes((event) => {
  const part = (metal, slot) => ({
    helmet: `tfc:metal/unfinished_helmet/${metal}`, chestplate: `tfc:metal/unfinished_chestplate/${metal}`,
    leggings: `tfc:metal/unfinished_greaves/${metal}`, boots: `tfc:metal/unfinished_boots/${metal}`,
    pickaxe: `tfc:metal/pickaxe_head/${metal}`, axe: `tfc:metal/axe_head/${metal}`, shovel: `tfc:metal/shovel_head/${metal}`,
    hoe: `tfc:metal/hoe_head/${metal}`, sword: `tfc:metal/sword_blade/${metal}`
  })[slot]
  const swap = (id, from, to) => event.replaceInput({ id: id }, from, to)

  // ======================================================================================== Draconic Evolution
  // Doc 08 section 8 / Doc 10 v3 7.3: about 25 tool, armour, staff and capacitor recipes. The catalyst (the previous
  // tier's piece, or a vanilla diamond tool for wyvern) becomes the core of the tier; an ingredient equal to the new
  // catalyst steps down one core (chaotic core -> large chaos fragment, awakened core -> wyvern core); the tools inside
  // the staffs become awakened draconium ingots (draconic) or large chaos fragments (chaotic); the wyvern basic relay
  // crystals (the crystal network is disabled) become desh plates, the Space Age metal of the wyvern tier.
  const CORE = { wyvern: 'draconicevolution:wyvern_core', draconic: 'draconicevolution:awakened_core', chaotic: 'draconicevolution:chaotic_core' }
  const STEP_DOWN = { 'draconicevolution:chaotic_core': { item: 'draconicevolution:large_chaos_frag' },
    'draconicevolution:awakened_core': { item: 'draconicevolution:wyvern_core' } }
  const GEAR = /^(minecraft:(diamond_[a-z]+|bow)|draconicevolution:(wyvern|draconic|chaotic)_(axe|hoe|pickaxe|shovel|sword|chestpiece|bow|capacitor|staff))$/
  const IN_STAFF = { draconic: { tag: 'c:ingots/draconium_awakened' }, chaotic: { item: 'draconicevolution:large_chaos_frag' } }
  const deFix = []
  event.forEachRecipe({ type: 'draconicevolution:fusion_crafting' }, (r) => {
    const id = String(r.getId())
    if (!/^draconicevolution:tools\/(wyvern|draconic|chaotic)_/.test(id)) return
    deFix.push([id, JSON.parse(String(r.json))])
  })
  deFix.forEach((row) => {
    const id = row[0]
    const j = row[1]
    event.remove({ id: id })
    if (id === 'draconicevolution:tools/chaotic_staff_alt') return // a second chaotic staff recipe, from the draconic staff
    const tier = String(j.techLevel).toLowerCase()
    const core = CORE[tier]
    if (j.catalyst && j.catalyst.item && GEAR.test(j.catalyst.item)) j.catalyst = { item: core }
    j.ingredients = j.ingredients.map((e) => {
      const ing = e.ingredient || e
      const it = ing.item
      if (it && it === core && STEP_DOWN[it]) return { ingredient: STEP_DOWN[it], consume: true }
      if (it && GEAR.test(it)) return { ingredient: IN_STAFF[tier] || { tag: 'c:plates/desh' }, consume: true }
      if (it === 'draconicevolution:basic_relay_crystal') return { ingredient: { tag: 'c:plates/desh' }, consume: true }
      return e
    })
    event.custom(j).id(id)
  })
  // Draconic modules (exception: their own chains stay) and the vanilla gear they eat.
  swap('draconicevolution:modules/item_wyvern_aqua_adapt', 'minecraft:iron_pickaxe', part('wrought_iron', 'pickaxe'))
  swap('draconicevolution:modules/item_wyvern_hill_step', 'minecraft:golden_boots', 'tfc:metal/double_sheet/gold')
  swap('draconicevolution:modules/item_wyvern_mining_stability', 'minecraft:golden_pickaxe', 'tfc:metal/double_sheet/gold')
  swap('draconicevolution:modules/item_wyvern_proj_penetration', 'minecraft:shield', 'tfc:metal/double_sheet/steel')
  swap('draconicevolution:modules/item_wyvern_tree_harvest', 'minecraft:diamond_axe', part('steel', 'axe'))

  // ======================================================================================== Mekanism
  // Armored Jetpack and Armored Free Runners: direct (Doc 10 v3 3.4); the base piece becomes the part it is built around.
  ;[['mekanism:jetpack_armored', 'mekanism:jetpack', 'mekanism:advanced_chemical_tank'],
    ['mekanism:free_runners_armored', 'mekanism:free_runners', 'mekanism:energy_tablet']].forEach((row) => {
    const found = []
    event.forEachRecipe({ id: row[0] }, (r) => found.push(JSON.parse(String(r.json))))
    if (found.length !== 1) return
    const j = found[0]
    Object.keys(j.key).forEach((k) => { if (j.key[k].item === row[1]) j.key[k] = { item: row[2] } })
    j.type = 'minecraft:crafting_shaped'
    event.remove({ id: row[0] })
    event.custom(j).id(row[0])
  })
  // MekaSuit modules (exception) with vanilla tools; the flamethrower eats flint and steel (a tool).
  swap('mekanism:module_attack_amplification_unit', 'minecraft:iron_sword', part('wrought_iron', 'sword'))
  swap('mekanism:module_excavation_escalation_unit', 'minecraft:iron_pickaxe', part('wrought_iron', 'pickaxe'))
  swap('mekanism:module_farming_unit', 'minecraft:iron_hoe', part('wrought_iron', 'hoe'))
  swap('mekanism:module_locomotive_boosting_unit', 'minecraft:diamond_leggings', part('steel', 'leggings'))
  swap('mekanism:module_shearing_unit', 'minecraft:shears', '#c:tools/shear')
  swap('mekanism:module_silk_touch_unit', 'minecraft:diamond_pickaxe', part('steel', 'pickaxe'))
  ;['axe', 'pickaxe', 'shovel'].forEach((s) => swap('mekanism:module_vein_mining_unit', `minecraft:diamond_${s}`, part('steel', s)))
  swap('mekanism:flamethrower', 'minecraft:flint_and_steel', 'minecraft:blaze_powder')
  // The HDPE Elytra is direct: a propeller takes the place of the vanilla elytra, as in the Create Jetpack
  // (recipes/age_4/industrial_age.js; no End cities in this pack).
  swap('mekanism:hdpe_elytra', 'minecraft:elytra', 'create:propeller')
  // Antimatter turning a bow into a crossbow and a diamond sword into a trident: gear from gear.
  event.remove({ id: 'mekanism:nucleosynthesizing/crossbow' })
  event.remove({ id: 'mekanism:nucleosynthesizing/trident' })
  // Mekanism Tools: the refined shields eat a vanilla shield; the other sets and every paxel are disabled.
  ;['refined_obsidian', 'refined_glowstone'].forEach((m) => swap(`mekanismtools:${m}/shield`, 'minecraft:shield', 'tfc:metal/double_sheet/steel'))
  // Evolved Mekanism (Quantum Age): shields from blue steel, the capturing module from a black steel blade.
  ;['better_gold', 'plaslitherite', 'refined_redstone']
    .forEach((m) => swap(`evolvedmekanism:tools/${m}/shield`, 'minecraft:shield', 'tfc:metal/double_sheet/blue_steel'))
  swap('evolvedmekanism:module_capturing_unit', 'minecraft:netherite_sword', part('black_steel', 'sword'))

  // ======================================================================================== Mystical Agriculture
  // The seed reprocessor asked for two vanilla iron hoes (no recipe in a TFC world): wrought iron hoe heads.
  swap('mysticalagriculture:seed_reprocessor', 'minecraft:iron_hoe', part('wrought_iron', 'hoe'))

  // ======================================================================================== backpacks and quests
  // Wooden and stone vanilla tools have no recipe in a TFC world; the copper part of the slot takes their place (the
  // stone/copper rule of the Arcane MAP). The tool swapper upgrade is an exception to the gear rule (backpack upgrade).
  ;['axe', 'pickaxe', 'shovel', 'sword'].forEach((t) =>
    swap('sophisticatedbackpacks:tool_swapper_upgrade', `minecraft:wooden_${t}`, part('copper', t)))
  swap('ftbquests:loot_crate_opener', 'minecraft:stone_pickaxe', part('copper', 'pickaxe'))

  // ======================================================================================== AE2 and AdvancedAE
  // Fluix tools from certus quartz instead of the certus tool of the same slot; the network tool from certus instead
  // of the certus wrench.
  ;['axe', 'hoe', 'pickaxe', 'shovel', 'sword'].forEach((s) => swap(`ae2:tools/fluix_${s}`, `#ae2:quartz_${s}`, '#c:gems/certus_quartz'))
  swap('ae2:tools/network_tool', '#ae2:quartz_wrench', '#c:gems/certus_quartz')
  swap('advanced_ae:strength_card', 'minecraft:diamond_sword', part('steel', 'sword'))

  // ======================================================================================== Ad Astra
  // Doc 08 section 8: the Netherite Space Suit and the Jet Suit are direct, from the plates of their tier and
  // netherite ingots instead of the netherite armour or the previous suit.
  ;['suit', 'helmet', 'pants', 'boots'].forEach((p) => {
    const vanilla = { suit: 'chestplate', helmet: 'helmet', pants: 'leggings', boots: 'boots' }[p]
    swap(`ad_astra:netherite_space_${p}`, `minecraft:netherite_${vanilla}`, '#c:ingots/netherite')
    swap(`ad_astra:jet_suit${p === 'suit' ? '' : '_' + p}`, `ad_astra:netherite_space_${p}`, '#c:ingots/netherite')
  })

  // ======================================================================================== Stargate Journey
  // Naquadah tools and armour: smithing on the TFC steel part of the slot, not on the diamond piece; the gravers on
  // a diamond, not on the diamond graver; the System Lord armour on a gold double sheet, not on the Jaffa piece.
  ;['axe', 'hoe', 'pickaxe', 'shovel', 'sword', 'helmet', 'chestplate', 'leggings', 'boots']
    .forEach((s) => swap(`sgjourney:naquadah_${s}`, `minecraft:diamond_${s}`, part('steel', s)))
  ;['naquadah_graver', 'netherite_graver'].forEach((g) => swap(`sgjourney:${g}`, 'sgjourney:diamond_graver', '#c:gems/diamond'))
  ;['helmet', 'chestplate', 'leggings', 'boots'].forEach((s) => swap(`sgjourney:system_lord_${s}`, `sgjourney:jaffa_${s}`, 'tfc:metal/double_sheet/gold'))

  // ======================================================================================== Apotheosis
  // Smithing upgrades stone -> iron -> gold and chain -> iron are gear chains on vanilla gear (golden -> diamond is
  // removed with the Industrial Age). Salvaging stays (affix mobs drop vanilla gear); the two tables eat tools.
  event.remove({ id: /^apotheosis:smithing\/upgrade_(stone|iron|chainmail)_.+_to_.+$/ })
  swap('apotheosis:salvaging_table', 'minecraft:iron_axe', part('wrought_iron', 'axe'))
  swap('apotheosis:salvaging_table', 'minecraft:iron_pickaxe', part('wrought_iron', 'pickaxe'))
  swap('apotheosis:gem_cutting_table', 'minecraft:shears', '#c:tools/shear')
})
