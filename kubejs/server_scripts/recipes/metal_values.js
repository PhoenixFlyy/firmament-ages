// Firmament Ages - one value per metal form (round F1, 2026-10-16; dev/poc_analyze.py check L-8).
// TFC prices its metal forms in mB: an ingot is 100, a sheet 200 (two ingots on the anvil), and a metal block 100
// (4 sheets and a plank make 8 blocks, a block melts back to 100 mB). The same TFC and TFC-IE blocks are also the
// members of c:storage_blocks/<metal> (TFC's own tags; Almost Unified turns every mod's storage block into them), and
// the c:plates/<metal> target is the TFC sheet (tags/unification.js). Recipes of other mods that assume the vanilla
// prices then create metal: a "block -> 9 ingots" recipe turned 4 sheets (8 ingots) into 8 blocks and 72 ingots, a
// "1 ingot -> 1 plate" recipe made a 200 mB TFC sheet from one ingot. The TFC price is the one the whole metal chain
// (ore ladder, crucible, casting) is built on, so the recipes with the other price go; nothing else changes.
// The blocks stay building blocks (from sheets, 1 ingot each) and still fill every recipe that asks for a block.
// Plain ids (a map lookup each), found on the gate-off dump of 0.14.0 (scratchpad b2int/d1) with the metal graph of
// poc_analyze.py L-8.

ServerEvents.recipes((event) => {
  // ---- "block -> 9 ingots" and the CBC melt of a block to 810 mB: a TFC block is one ingot ---------------------
  ;[
    'minecraft:copper_ingot', 'minecraft:gold_ingot_from_gold_block',
    'createbigcannons:bronze_ingot_from_block', 'createbigcannons:cast_iron_ingot_from_block',
    'createbigcannons:melting/melt_bronze_block', 'createbigcannons:melting/melt_cast_iron_block',
    'createbigcannons:melting/melt_steel_block',
    'immersiveengineering:crafting/storage_aluminum_to_ingot_aluminum',
    'immersiveengineering:crafting/storage_constantan_to_ingot_constantan',
    'immersiveengineering:crafting/storage_lead_to_ingot_lead', 'immersiveengineering:crafting/storage_nickel_to_ingot_nickel',
    'mekanism:processing/steel/ingot/from_block', 'mekanism:processing/tin/ingot/from_block',
    'mekanism:processing/uranium/ingot/from_block',
    'occultengineering:crafting/items/sterling_silver_ingot_from_decompacting', 'occultism:crafting/silver_ingot_from_block'
  ].forEach((id) => event.remove({ id: id }))

  // ---- "9 ingots (or 810 mB) -> 1 block": the same block for nine times its price --------------------------------
  ;[
    'minecraft:copper_block', 'minecraft:gold_block', 'minecraft:iron_block', 'ad_astra:steel_block',
    'createbigcannons:bronze_block', 'createbigcannons:cast_iron_block', 'createbigcannons:steel_block',
    'createbigcannons:compacting/forge_bronze_block', 'createbigcannons:compacting/forge_cast_iron_block',
    'createbigcannons:compacting/forge_steel_block',
    'mekanism:processing/lead/storage_blocks/from_ingots', 'mekanism:processing/tin/storage_blocks/from_ingots',
    'mekanism:processing/uranium/storage_blocks/from_ingots',
    'occultengineering:crafting/items/sterling_silver_block_from_compacting', 'occultism:crafting/silver_block',
    'tfc_ie_addon:metalpress/block_steel', 'tfc_ie_addon:metalpress/block_uranium'
  ].forEach((id) => event.remove({ id: id }))

  // ---- "1 ingot -> 1 plate" whose plate is the TFC sheet (two ingots) ---------------------------------------------
  // TFC-IE's anvil plates of the TFC metals and Create Casing's zinc pressing. The TFC anvil, the Create and WoodenCog
  // presses and the IE Metal Press make the sheet from two ingots (poc_analyze.py C-4e).
  ;['copper', 'gold', 'iron', 'nickel', 'silver', 'steel'].forEach((m) => event.remove({ id: `tfc_ie_addon:anvil/plate_${m}` }))
  event.remove({ id: 'createcasing:pressing/zinc' })

  // ---- One zinc nugget: Create's (tags/unification.js keeps c:nuggets/zinc to it) -------------------------------
  // More Mekanism Processing cut an ingot into 9 nuggets of its own and took 8 Create nuggets around one of its own
  // back; firmages:crafting/zinc_nugget and Create's compacting recipe already do both with Create's nugget.
  event.remove({ id: 'moremekanismprocessing:processing/zinc/nugget/from_ingot' })
  event.remove({ id: 'moremekanismprocessing:processing/zinc/ingot/from_nugget' })
})
