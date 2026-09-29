// Firmament Ages - static recipe-viewer hiding (same for every player).
// KubeJS 2101 RecipeViewerEvents.removeEntriesCompletely('item', ...) removes stacks and their recipes from
// EMI (KubeJS EMI integration) and JEI. Only for items that never exist in the pack; per-Age hiding is done
// per team by ProgressiveStages ([emi]/[jei] in progressivestages.toml).
// Reload in game: F3+T or /kubejs reload client-scripts.

RecipeViewerEvents.removeEntriesCompletely('item', (event) => {
  global.FA.HIDDEN_ITEMS.forEach((id) => event.remove(id))
})
