// Firmament Ages - tooltip lines of the signature items (text in startup_scripts/items.js, global.FA_SIGNATURE_TOOLTIPS)
// and of the boss fallback sigils (global.FA_SIGILS). KubeJS 2101: ItemEvents.modifyTooltips, add(ingredient, [components]).
// Reload in game: F3+T.

ItemEvents.modifyTooltips((event) => {
  const lines = global.FA_SIGNATURE_TOOLTIPS || {}
  Object.keys(lines).forEach((id) => event.add(id, [Text.gray(lines[id])]))
  const sigils = global.FA_SIGILS || {}
  Object.keys(sigils).forEach((id) => {
    const s = sigils[id]
    event.add(id, [
      Text.gray(`Use it on a block to open the ${s.gateName}, a Gateway of stand-ins for ${s.boss}.`),
      Text.gray(`Clear every wave and the Gateway gives ${s.reward}.`)
    ])
  })
})

// Gateway names: Gateways to Eternity names a gate by the translation key "<namespace>.<path>" of its id
// (GatewayEntity, id.toString() with ':' -> '.'), shown on the boss bar while the gate is open.
ClientEvents.lang('en_us', (event) => {
  const sigils = global.FA_SIGILS || {}
  Object.keys(sigils).forEach((id) => event.add('firmages', String(sigils[id].gate).replace(':', '.'), sigils[id].gateName))
  event.add('firmages', 'firmages.the_origin', 'The Origin')
  event.add('firmages', 'firmages.the_origin_fallback', 'The Origin')
})
