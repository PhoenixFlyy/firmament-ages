// Dev gametest probe only (copied into run/gametest by prepareGametestRun, never part of the pack).
// Exposes FirmAges.lockedOreBlocks() as a block tag, so CoreGameTests can check the binding on the datapack load path.
// Reading a tag's members first makes KubeJS run the block tag handlers inside the tag loader (the pre-capture cannot
// read tags), where the binding answers for the load in progress, on the initial load too.
ServerEvents.tags('block', event => {
  event.get('minecraft:mineable/pickaxe').getObjectIds()
  const locked = FirmAges.lockedOreBlocks()
  console.info(`firmages probe: lockedOreBlocks = ${locked}, unlocked = ${FirmAges.unlockedAges()}`)
  locked.forEach(id => event.add('firmages:test/locked_ores', id))
  event.add('firmages:test/probe_ran', 'minecraft:bedrock')
})
