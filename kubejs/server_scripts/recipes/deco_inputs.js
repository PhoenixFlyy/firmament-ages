// Firmament Ages - the n7 deco set in TFC materials (research n7-build, Felix 2026-10-14).
// Macaw's Stairs, Fences, Trapdoors, Roofs and Furniture, Storage Drawers, Another Furniture, Dusty Decorations, Joy
// of Painting, Create: Interiors, Exposure, Immersive Paintings, the Rechiseled chisel and the Every Compat copies of
// the furniture mods in TFC and AFC woods (short ids mws, mcf, mct, mcr, mcfur, sd, af).
// A TFC world makes no vanilla iron, copper or gold ingot, iron nugget, torch, dirt, bucket, barrel or iron trapdoor, so
// these recipes take the TFC item (MAP; the vanilla copper and gold ingots as in recipes/dead_inputs.js). Torches stay TFC
// torches: a lamp of Another Furniture or Dusty Decorations is an Iron Age item (dev/age_map.toml), like every light
// that does not burn out. Refurbished Furniture (age_5) and Laser Bridges (age_7) have their own Age materials in
// recipes/age_N/; Exposure's camera, film and developing recipes are rebuilt in recipes/exposure.js (OWNED, skipped
// here), this file only maps the vanilla metals of its other recipes.
// Ages of the outputs: dev/age_map.toml [mods] and [items]; the vanilla-wood originals are in [hidden] (patterns).
// Ids from the mod jars (no fa_dump with these mods yet). POC: Every Compat's recipes come from Moonlight's runtime
// pack; check one TFC kitchen sink and one TFC drawer in EMI.

ServerEvents.recipes((event) => {
  const MAP = {
    'minecraft:iron_ingot': { tag: 'c:ingots/iron' },
    'minecraft:copper_ingot': { tag: 'c:ingots/copper' },
    'minecraft:gold_ingot': { tag: 'c:ingots/gold' },
    'minecraft:iron_nugget': { item: 'tfc:metal/rod/wrought_iron' },
    'minecraft:torch': { item: 'tfc:torch' },
    'minecraft:dirt': { tag: 'tfc:dirt' },
    'minecraft:bucket': { item: 'tfc:wooden_bucket' },
    'minecraft:water_bucket': { item: 'tfc:wooden_bucket' },
    'minecraft:iron_trapdoor': { item: 'tfc:metal/trapdoor/wrought_iron' },
    'minecraft:barrel': { tag: 'tfc:barrels' },
    'minecraft:honeycomb': { item: 'firmalife:beeswax' },
    // Macaw's dyed couches and chaises (never in an Every Compat recipe: there the vanilla wood is Moonlight's swap key)
    'minecraft:oak_planks': { tag: 'minecraft:planks' }
  }
  // Bronze Age pieces: copper and bronze instead of wrought iron (the kitchen cabinets, gutters and the roofing hammer
  // belong to the first home).
  const BY_ID = {
    'mcwfurnitures:cabinet_door': { 'minecraft:iron_nugget': { item: 'tfc:metal/rod/copper' } },
    'mcwfurnitures:cabinet_drawer': { 'minecraft:iron_nugget': { item: 'tfc:metal/rod/copper' } },
    'mcwroofs:gutter': { 'minecraft:iron_nugget': { item: 'tfc:metal/rod/copper' } },
    'mcwroofs:roofing_hammer': {
      'minecraft:iron_ingot': { item: 'tfc:metal/ingot/bronze' },
      'minecraft:iron_nugget': { item: 'tfc:metal/rod/bronze' }
    }
  }
  const NS = ['mcwstairs', 'mcwfences', 'mcwtrpdoors', 'mcwroofs', 'mcwfurnitures', 'storagedrawers', 'another_furniture',
    'dustydecorations', 'xercapaint', 'interiors', 'exposure', 'immersive_paintings']
  const EC = /^everycomp:(mws|mcf|mct|mcr|mcfur|sd|af)\//
  const OWNED = /^exposure:(camera|camera_stand|lightroom|album|(high_sensitivity_)?(black_and_white|color)_film(_developing)?)$/
  const re = new RegExp(`"item":"(${Object.keys(MAP).join('|')})"`, 'g')
  const todo = []
  event.forEachRecipe(NS.map((ns) => ({ mod: ns })).concat([{ mod: 'everycomp' }]), (r) => {
    const id = String(r.getId())
    if (id.startsWith('everycomp:') && !EC.test(id)) return
    if (OWNED.test(id)) return
    const text = String(r.json)
    const map = Object.assign({}, MAP, BY_ID[id] || {})
    if (id.startsWith('everycomp:')) delete map['minecraft:oak_planks']
    const next = text.replace(re, (m, from) => {
      const to = map[from]
      if (!to) return m
      return to.tag ? `"tag":"${to.tag}"` : `"item":"${to.item}"`
    })
    if (next !== text) todo.push([id, next])
  })
  todo.forEach((t) => {
    event.remove({ id: t[0] })
    event.custom(JSON.parse(t[1])).id(t[0])
  })
  console.info(`[firmages] deco inputs: ${todo.length} recipes take TFC materials`)

  // ---- Macaw's Trapdoors: the three prints the TFC trapdoors use (barred, cottage, four panel; Every Compat makes
  // no others, config/everycomp-hazardous.toml). Macaw's draws them from vanilla trapdoors; the centre of the paper
  // ring is now a TFC part, each a different one (Macaw's Doors' prints take the TFC doors and trapdoors).
  event.replaceInput({ id: 'mcwtrpdoors:print_barred' }, 'minecraft:crimson_trapdoor', 'tfc:metal/bars/wrought_iron')
  event.replaceInput({ id: 'mcwtrpdoors:print_cottage' }, 'minecraft:spruce_trapdoor', '#tfc:lumber')
  event.replaceInput({ id: 'mcwtrpdoors:print_four_panel' }, 'minecraft:dark_oak_trapdoor', '#minecraft:wooden_slabs')

  // ---- The chisel screen (Rechiseled) comes with bronze, like TFC's own chisel ladder.
  event.replaceInput({ id: 'rechiseled:chisel' }, '#c:ingots/iron', 'tfc:metal/ingot/bronze')

  // ---- Joy of Painting: canvas on burlap, a feather for the palette's brush. The palette is filled with any dye
  // (TFC dyes from the quern).
  event.replaceInput({ id: 'xercapaint:item_canvas' }, 'minecraft:paper', 'tfc:burlap_cloth')
  event.replaceInput({ id: 'xercapaint:item_palette' }, 'minecraft:brush', 'minecraft:feather')

  // ---- Storage Drawers: one upgrade per Age (dev/age_map.toml [items]). Metal upgrades take the TFC sheet of their
  // metal; the later ones keep their material and add the Age's rod or fibre instead of sticks.
  event.replaceInput({ id: 'storagedrawers:copper_storage_upgrade' }, '#c:ingots/copper', 'tfc:metal/sheet/copper')
  event.replaceInput({ id: 'storagedrawers:iron_storage_upgrade' }, '#c:ingots/iron', 'tfc:metal/sheet/wrought_iron')
  event.replaceInput({ id: 'storagedrawers:gold_storage_upgrade' }, '#c:ingots/gold', 'tfc:metal/sheet/gold')
  event.replaceInput({ id: 'storagedrawers:obsidian_storage_upgrade' }, '#c:rods/wooden', 'immersiveengineering:stick_steel')
  event.replaceInput({ id: 'storagedrawers:diamond_storage_upgrade' }, '#c:rods/wooden', 'immersiveengineering:stick_aluminum')
  ;['emerald', 'netherite'].forEach((m) =>
    event.replaceInput({ id: `storagedrawers:${m}_storage_upgrade` }, '#c:rods/wooden', 'ae2:quartz_fiber'))
})
