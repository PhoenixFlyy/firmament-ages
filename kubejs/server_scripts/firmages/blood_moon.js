// Firmament Ages - no blood moon before the Iron Age mob tier mob_2 (Felix 2026-10-13, H8; dev/config-todo.md 17).
//
// Enhanced Celestials 2 (Core 2.0.3.3, Default Lunar Events 2.0.1.3, javap): the spawn rules of a lunar event
// (data/<ns>/enhancedcelestials2core/lunar/event_probability/*.json, "spawn_requirements.rules") know only
// min_nights_between and valid_moon_phase (LunarEventSpawnRuleTypes), and their context holds only day and moon phase
// (SpawnRuleContext), so a stage cannot be a data condition. The gate edits the overworld forecast instead:
// every 10 s, while players are online and none of them has mob_2, LunarForecast.removeLunarEvents() drops every
// blood and super blood moon from today on (all scheduled years) and ends one that is already running; EC2 then saves
// the forecast and syncs it to the clients. A forecast that rolls a new year, /ec lunarForecast recompute and
// /ec setLunarEvent are caught by the next pass. With nobody online the gate waits: TFC stops time on an empty
// server (enableTimeStopWhenServerEmpty), so no night passes. One team = one progression (ProgressiveStages team mode).
// When mob_2 arrives (grants.js, Iron Age), the forecast is rolled once more if the gate removed anything, so blood
// moons come back at once instead of after the two 100-day years EC2 keeps scheduled.

(() => {
  if (!Platform.isLoaded('enhancedcelestials2core')) {
    console.warn('[firmages] blood_moon: Enhanced Celestials 2 Core not loaded, blood moon gate off')
    return
  }
  const EC = Java.loadClass('dev.corgitaco.enhancedcelestials2core.EnhancedCelestials')
  const BLOOD_MOONS = [
    'enhancedcelestials2defaultlunarevents:blood_moon',
    'enhancedcelestials2defaultlunarevents:super_blood_moon'
  ]
  const GATE_STAGE = 'mob_2'
  const REMOVED_FLAG = 'firmages_blood_moons_removed' // server persistentData: the gate changed the forecast
  const PERIOD_TICKS = 200

  const short = (stage) => String(stage).replace(/^progressivestages:/, '')
  const isBloodMoon = (holder) => BLOOD_MOONS.indexOf(String(holder.getRegisteredName())) >= 0

  const anyoneHasGate = (server) => {
    let ok = false
    server.players.forEach((p) => {
      if (ProgressiveStages.has(p, GATE_STAGE)) ok = true
    })
    return ok
  }

  ServerEvents.tick((event) => {
    const server = event.server
    if (server.tickCount % PERIOD_TICKS !== 0 || server.players.isEmpty() || anyoneHasGate(server)) return
    EC.lunarForecastWorldData(server.overworld()).ifPresent((forecast) => {
      const removed = forecast.removeLunarEvents((holder) => isBloodMoon(holder))
      if (removed > 0) {
        server.persistentData.putBoolean(REMOVED_FLAG, true)
        console.info(`[firmages] blood_moon: removed ${removed} blood moon(s) from the forecast (no ${GATE_STAGE} yet)`)
      }
    })
  })

  ProgressiveStages.onGranted((player, stage) => {
    if (short(stage) !== GATE_STAGE) return
    const server = player.server
    if (!server.persistentData.getBoolean(REMOVED_FLAG)) return
    server.persistentData.remove(REMOVED_FLAG)
    EC.lunarForecastWorldData(server.overworld()).ifPresent((forecast) => {
      forecast.recomputeForecast()
      console.info(`[firmages] blood_moon: ${GATE_STAGE} reached, lunar forecast rolled again with blood moons`)
    })
  })
})()
