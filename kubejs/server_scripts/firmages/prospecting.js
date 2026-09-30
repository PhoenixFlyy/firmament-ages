// Firmament Ages - m1 stage-aware prospecting (firmages-core SPEC section 5, config prospecting.enabled).
// Ore blocks of a locked Age (FirmAges.lockedOreBlocks()) leave the prospecting tags, so a disguised vein gives
// no hit. Tag names verified in the pinned jars:
//  - tfc:prospectable (TFC 4.2.11, data/tfc/tags/block/prospectable.json = ["#c:ores"]): TFC propick, and the
//    Precision Prospecting prospector hammer and drill.
//  - precisionprospecting:prospectable_mineral (Precision Prospecting 2.1): its mineral prospector.
// KubeJS 2101 cannot remove a member that a tag only holds through a nested tag (#c:ores), so a tag that
// contains a locked ore is flattened: its resolved members minus the locked ores (registered ids only).
// Runs on every datapack load; the Age unlock reload restores the ores. Idempotent.
// Reading the members first matters: during the KubeJS pre-capture getObjectIds() returns nothing and makes KubeJS
// run all block tag handlers inside the tag loader instead, where FirmAges answers for the load in progress, on
// the initial load too.

const FA_M1_PROSPECT_TAGS = ['tfc:prospectable', 'precisionprospecting:prospectable_mineral']

ServerEvents.tags('block', (event) => {
  const members = FA_M1_PROSPECT_TAGS.map((tag) => event.get(tag).getObjectIds())
  if (!FirmAges.prospectingEnabled()) return
  const locked = {}
  let lockedCount = 0
  FirmAges.lockedOreBlocks().forEach((id) => {
    locked[String(id)] = true
    lockedCount++
  })
  if (lockedCount === 0) return
  FA_M1_PROSPECT_TAGS.forEach((tag, i) => {
    const keep = []
    let removed = 0
    members[i].forEach((id) => {
      const s = String(id)
      if (locked[s]) removed++
      else keep.push(s)
    })
    if (removed === 0) return
    event.removeAll(tag)
    keep.forEach((id) => event.add(tag, id))
    console.info(`firmages m1: ${tag}: ${removed} ore blocks of locked Ages removed, ${keep.length} kept`)
  })
})
