// Dev gametest probe only (copied into run/gametest by prepareGametestRun, never part of the pack).
// Exposes FirmAges.lockedOreBlocks() as a block tag, so CoreGameTests can check the binding on the reload path.
ServerEvents.tags('block', event => {
  const locked = FirmAges.lockedOreBlocks()
  console.info(`firmages probe: lockedOreBlocks = ${locked}, unlocked = ${FirmAges.unlockedAges()}`)
  locked.forEach(id => event.add('firmages:test/locked_ores', id))
  event.add('firmages:test/probe_ran', 'minecraft:bedrock')
})
