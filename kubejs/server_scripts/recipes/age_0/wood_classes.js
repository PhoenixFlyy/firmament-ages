// Firmament Ages - wood classes (HW1A, Felix 2026-10-15): vanilla wood does what the trees' own wood does.
// Every naturally grown TFC, AFC or Beneath trunk drops the vanilla log of its class (loot tables from
// dev/gen_wood_classes.py); the classes, their lead species and the species come from global.FA.WOOD_CLASSES
// (dev/wood_classes.toml). Recipe ids are firmages:wood/..., so the Dawn lock (mod:firmages, age_0.toml) keeps them
// for the Stone Age. Checks: dev/poc_analyze.py W-4 (one recipe per grid), W-6 (no class-bound function recipe),
// W-7 (the lead recipes take the class tags).
//   R1  the vanilla planks recipes from logs go (no planks without a saw)
//   R2  saw + vanilla log, wood or stripped -> 2 vanilla planks (as TFC: one log is two planks)
//   R3  saw + vanilla planks, slab or stairs -> 4, 2 or 3 lumber of the lead species
//   R4  4 vanilla planks -> crafting table (TFC removed the vanilla recipe; the TFC workbenches take species planks)
//   R5  8 class logs around a species sapling -> 8 logs of that species; R6 the same with planks
//   R7  1 species log or wood -> 1 class log (fallen trunks, ruins, torn-down builds)
//   R8  golden bamboo <-> bamboo, golden bamboo block <-> bamboo block
//   R9  the lead species' TFC function blocks (support, axle, crate, clutch, encased axle, sewing table, water wheel,
//       scribing table, shelf) take the class tags firmages:wood_class/<class>/...
//   R10 buttons, plates, slabs, stairs, fences, gates, doors, trapdoors, signs and boats stay species-only: vanilla has
//       its own recipes for them, an opened TFC recipe would match the same grid twice
//   S   a species sapling -> the vanilla sapling of its class (vanilla trees grow by vanilla rules, not TFC climate)

ServerEvents.recipes((event) => {
  const W = global.FA.WOOD_CLASSES
  const SAW = { tag: 'c:tools/saw' }
  const REMAINDER = { modifiers: [{ type: 'tfc:damage_crafting_remainder' }] }
  const sawn = (input, out, count, id) => event.custom({
    type: 'tfc:advanced_shapeless_crafting',
    ingredients: [SAW, input],
    primary_ingredient: SAW,
    remainder: REMAINDER,
    result: { count: count, id: out }
  }).id(id)
  const leaf = (s) => s.split(':').pop().split('/').pop()
  let n = 0

  Object.keys(W.classes).forEach((k) => {
    const c = W.classes[k]
    const v = c.vanilla
    const lead = c.lead
    // R1, R2
    event.remove({ id: v.planks })
    sawn({ tag: v.logs_tag }, v.planks, 2, `firmages:wood/planks/${k}`)
    // R3
    sawn({ item: v.planks }, lead.lumber, 4, `firmages:wood/lumber/${k}_from_planks`)
    sawn({ item: v.slab }, lead.lumber, 2, `firmages:wood/lumber/${k}_from_slab`)
    sawn({ item: v.stairs }, lead.lumber, 3, `firmages:wood/lumber/${k}_from_stairs`)
    n += 4
    c.species.forEach((s) => {
      const sp = s.id.replace(':', '_')
      // R5, R6
      event.shaped(Item.of(s.log, 8), ['LLL', 'LSL', 'LLL'], { L: v.log, S: s.sapling })
        .id(`firmages:wood/species/${sp}_logs`)
      n++
      if (s.planks) {
        event.shaped(Item.of(s.planks, 8), ['PPP', 'PSP', 'PPP'], { P: v.planks, S: s.sapling })
          .id(`firmages:wood/species/${sp}_planks`)
        n++
      }
      // R7
      event.shapeless(v.log, [Ingredient.of([s.log, s.wood])]).id(`firmages:wood/class_log/${sp}`)
      n++
      // S
      if (v.sapling) {
        event.shapeless(v.sapling, [s.sapling]).id(`firmages:wood/class_sapling/${sp}`)
        n++
      }
    })
  })

  // R4
  event.shaped('minecraft:crafting_table', ['PP', 'PP'], { P: '#firmages:vanilla_planks' })
    .id('firmages:wood/crafting_table')
  n++

  // R8
  W.bamboo.forEach((pair) => {
    event.shapeless(pair[1], [pair[0]]).id(`firmages:wood/bamboo/${leaf(pair[1])}_from_golden`)
    event.shapeless(pair[0], [pair[1]]).id(`firmages:wood/bamboo/golden_${leaf(pair[1])}`)
    n += 2
  })

  // R9: JSON rewrite (the pack's pattern for recipe types without a KubeJS schema, recipes/deco_inputs.js)
  const KINDS = ['support', 'axle', 'crate', 'clutch', 'encased_axle', 'sewing_table', 'water_wheel', 'scribing_table',
    'shelf']
  const todo = []
  Object.keys(W.classes).forEach((k) => {
    const lead = W.classes[k].lead
    const ns = lead.id.split(':')[0]
    const sp = lead.id.split(':')[1]
    const map = {}
    map[`"tag":"${lead.logs_tag}"`] = `"tag":"firmages:wood_class/${k}/logs"`
    map[`"item":"${lead.log}"`] = `"tag":"firmages:wood_class/${k}/logs"`
    map[`"item":"${lead.stripped_log}"`] = `"tag":"firmages:wood_class/${k}/stripped_logs"`
    map[`"item":"${lead.slab}"`] = `"tag":"firmages:wood_class/${k}/slabs"`
    map[`"item":"${lead.planks}"`] = `"tag":"firmages:wood_class/${k}/planks"`
    KINDS.forEach((kind) => {
      const id = `${ns}:crafting/wood/${kind}/${sp}`
      event.forEachRecipe({ id: id }, (r) => {
        let text = String(r.json).replace(/"(tag|item)"\s*:\s*"/g, '"$1":"')
        Object.keys(map).forEach((from) => { text = text.split(from).join(map[from]) })
        todo.push([id, text])
      })
    })
  })
  todo.forEach((t) => {
    event.remove({ id: t[0] })
    event.custom(JSON.parse(t[1])).id(t[0])
  })
  console.info(`[firmages] wood classes: ${n} recipes, ${todo.length} lead-species recipes take the class tags`)
})
