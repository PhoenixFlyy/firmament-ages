// Firmament Ages - tooltip lines of the signature items (text in startup_scripts/items.js, global.FA_SIGNATURE_TOOLTIPS).
// KubeJS 2101: ItemEvents.modifyTooltips, add(ingredient, [components]). Reload in game: F3+T.

ItemEvents.modifyTooltips((event) => {
  const lines = global.FA_SIGNATURE_TOOLTIPS || {}
  Object.keys(lines).forEach((id) => event.add(id, [Text.gray(lines[id])]))
})
