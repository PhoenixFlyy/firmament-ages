// Firmament Ages - static hiding of items that never exist in the pack (Doc 10 v3 section 7.2).
// c:hidden_from_recipe_viewers is the NeoForge convention tag; JEI honours it and EMI 1.1.24 reads it
// (EmiTags.HIDDEN_FROM_RECIPE_VIEWERS). client_scripts/viewer_static.js removes the same list through
// KubeJS RecipeViewerEvents as a second layer. The list itself lives in startup_scripts/00_constants.js.
// Per-Age hiding is NOT done here: ProgressiveStages hides locked items per team in EMI and JEI.
// POC: confirm in EMI that tagged stacks disappear from the index, not only from tag displays.

ServerEvents.tags('item', (event) => {
  event.add('c:hidden_from_recipe_viewers', global.FA.HIDDEN_ITEMS)
})
