// Firmament Ages - m3 miner filter, Mekanism Digital Miner (firmages-core SPEC section 5).
// Every ore block of a locked Age (firmages:age_blocks/<age>, FirmAges.lockedOreBlocks()) joins
// mekanism:miner_blacklist [tag name verified in Mekanism 10.7.19]. ThreadMinerSearch and
// TileEntityDigitalMiner#tryMineBlock both honour the tag, so disguised ores are neither found nor counted.
// Runs on every datapack load; the Age unlock reload takes the ores back out. Idempotent.
// FirmAges answers for the load in progress; call it only inside ServerEvents.tags.

ServerEvents.tags('block', (event) => {
  FirmAges.lockedOreBlocks().forEach((id) => event.add('mekanism:miner_blacklist', id))
})
