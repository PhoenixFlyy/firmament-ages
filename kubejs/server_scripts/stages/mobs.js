// Firmament Ages - KubeJS spawn-check FALLBACK for the mob ladder (Doc 03 section 3, "Mob-Gating" fallback).
// Primary mechanism: ProgressiveStages [mobs].locked_spawns / [[mobs.replacements]] in mob_N.toml.
// Enable this only if the PoC shows that PS does not catch TFC's surface spawns (PoC item 8).
// EntityEvents.checkSpawn (KubeJS 2101, CheckLivingEntitySpawnKubeEvent) fires only for spawner and
// world-generation spawns and supports cancel(); it does not cover commands or spawn eggs.

(() => {
  const ENABLED = false

  // entity id -> mob stage that must be owned by the nearest player (mirror of mob_N.toml).
  const SPAWN_STAGE = {
    'minecraft:skeleton': 'mob_1',
    'minecraft:stray': 'mob_1',
    'minecraft:creeper': 'mob_1',
    'minecraft:witch': 'mob_2',
    'minecraft:phantom': 'mob_2',
    'minecraft:slime': 'mob_2'
  }

  if (!ENABLED) return

  EntityEvents.checkSpawn((event) => {
    const stage = SPAWN_STAGE[String(event.entity.type)]
    if (!stage) return
    const player = event.level.getNearestPlayer(event.x, event.y, event.z, 128, false)
    // No player nearby: allow, like ProgressiveStages does.
    if (player && !ProgressiveStages.has(player, stage)) event.cancel()
  })
})()
