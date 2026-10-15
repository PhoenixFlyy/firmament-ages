// Firmament Ages - Goblin Traders trade by Age (vorauswahl M251, dev/decisions-while-away.md 2026-10-15).
// The trade pools (kubejs/data/goblintraders/goblin_trades/) hold TFC goods of Dawn to the Arcane Age. A goblin draws
// its offers when it first spawns; this script then drops every offer whose result or price belongs to an Age the
// world has not reached (or to age_items/disabled), so a goblin met in the Stone Age only trades Stone Age goods.
// A goblin that is already in the world keeps its offers (an Age never goes back).

(() => {
  const GOBLINS = ['goblintraders:goblin_trader', 'goblintraders:vein_goblin_trader']

  const lockedIngredients = () => {
    const tags = ['disabled']
    FirmAges.lockedAges().forEach((s) => tags.push(String(s)))
    return tags.map((st) => Ingredient.of(`#firmages:age_items/${st}`))
  }

  const isLocked = (stack, locked) => !stack.isEmpty() && locked.some((ing) => ing.test(stack))

  GOBLINS.forEach((type) => {
    EntityEvents.spawned(type, (event) => {
      const goblin = event.entity
      if (goblin.level.isClientSide()) return
      const offers = goblin.getOffers() // draws the offers on the first call (AbstractGoblinEntity.populateTradeData)
      const locked = lockedIngredients()
      for (let i = offers.size() - 1; i >= 0; i--) {
        const offer = offers.get(i)
        if (isLocked(offer.getResult(), locked) || isLocked(offer.getCostA(), locked) || isLocked(offer.getCostB(), locked)) {
          offers.remove(i)
        }
      }
    })
  })
})()
