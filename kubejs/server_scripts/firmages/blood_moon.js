// Firmament Ages - no blood moon before the Iron Age mob tier mob_2 (Felix 2026-10-13, H8).
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
//
// Age blood moons (n7-world): from mob_2 on, the same 10 s pass swaps every blood and super blood moon in the forecast
// for the variant of the team's Age, firmages:blood_moon_age_<N> / super_blood_moon_age_<N> (N = 2..9, highest Age of
// the players online; dev/gen_blood_moon.py). The variants keep EC2's modifiers and add the Age: armour and weapons of
// the metal one Age behind, from the Electric Age on speed, resistance and strength. LunarForecast.insertLunarEvent
// (EC2 Core 2.0.3.3, javap) replaces the event of that night; a moon that is already up is left as it is.
//
// Creeping Darkness (S1f, SPEC 19.7): the night table kubejs/data/firmages/balance/nights.json sets per Age how many of
// EC2's blood moons stay ('blood_moon_keep', share of the rolled nights; the rest is dropped with
// LunarForecast.removeLunarEvent(days)) and whether super blood moons stay ('super_blood_moon'; else the night becomes
// the plain Age blood moon). The keep decision hangs on the world day of the moon, so every pass decides the same.

(() => {
  if (!Platform.isLoaded('enhancedcelestials2core')) {
    console.warn('[firmages] blood_moon: Enhanced Celestials 2 Core not loaded, blood moon gate off')
    return
  }
  const EC = Java.loadClass('dev.corgitaco.enhancedcelestials2core.EnhancedCelestials')
  const ECRegistry = Java.loadClass('dev.corgitaco.enhancedcelestials2core.api.EnhancedCelestialsRegistry')
  const ResourceKey = Java.loadClass('net.minecraft.resources.ResourceKey')
  const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')
  // default blood moons and the Age variants: id -> kind
  const BLOOD_MOON = /^(?:enhancedcelestials2defaultlunarevents:(blood_moon|super_blood_moon)|firmages:(blood_moon|super_blood_moon)_age_[2-9])$/
  const GATE_STAGE = 'mob_2'
  const FIRST_AGE = 2 // the Age of mob_2
  const REMOVED_FLAG = 'firmages_blood_moons_removed' // server persistentData: the gate changed the forecast
  const PERIOD_TICKS = 200
  // read once per game start, like metal_table.js
  const NIGHTS = {}
  try {
    JSON.parse(String(JsonIO.readString('kubejs/data/firmages/balance/nights.json'))).ages.forEach((r) => (NIGHTS[r.age] = r))
  } catch (e) {
    console.warn(`[firmages] blood_moon: nights.json not read (${e}), every blood moon stays`)
  }
  const nightRow = (n) => NIGHTS[`age_${n}`] || { blood_moon_keep: 1, super_blood_moon: true }
  // stable share in [0, 1) for a world day
  const dayShare = (day) => ((day * 2654435761) % 1000003) / 1000003

  const short = (stage) => String(stage).replace(/^progressivestages:/, '')
  const bloodMoonKind = (holder) => {
    const m = String(holder.getRegisteredName()).match(BLOOD_MOON)
    return m ? m[1] || m[2] : null
  }
  const isBloodMoon = (holder) => bloodMoonKind(holder) !== null

  const highestAge = (server) => {
    let best = -1
    server.players.forEach((p) => {
      for (let k = best + 1; k <= 9; k++) {
        if (ProgressiveStages.has(p, `age_${k}`)) best = k
      }
    })
    return best
  }

  const holderFor = (server, path) => {
    const registry = server.registryAccess().registryOrThrow(ECRegistry.LUNAR_EVENT_KEY)
    const key = ResourceKey.create(ECRegistry.LUNAR_EVENT_KEY, ResourceLocation.fromNamespaceAndPath('firmages', path))
    // getHolder has a ResourceKey and a ResourceLocation overload, which Rhino calls ambiguous; getHolderOrThrow has one
    try {
      return registry.getHolderOrThrow(key)
    } catch (e) {
      return null
    }
  }

  // swap every blood moon of the forecast that is not yet the team's Age variant (a running one stays)
  const reskin = (server) => {
    const n = Math.max(FIRST_AGE, highestAge(server))
    const row = nightRow(n)
    const today = Math.floor(Number(server.overworld().getDayTime()) / 24000)
    EC.lunarForecastWorldData(server.overworld()).ifPresent((forecast) => {
      const swaps = []
      const drops = []
      const active = forecast.isEventActive()
      forecast.upcomingEventsByDaysInAdvance().forEach((days, holder) => {
        let kind = bloodMoonKind(holder)
        const d = Number(days)
        if (kind === null || (d === 0 && active)) return
        if (dayShare(today + d) >= row.blood_moon_keep) {
          drops.push(d)
          return
        }
        if (kind === 'super_blood_moon' && !row.super_blood_moon) kind = 'blood_moon'
        if (String(holder.getRegisteredName()) !== `firmages:${kind}_age_${n}`) swaps.push([d, kind])
      })
      drops.forEach((d) => {
        if (forecast.removeLunarEvent(d)) console.info(`[firmages] blood_moon: blood moon in ${d} day(s) dropped (nights.json, Age ${n})`)
      })
      swaps.forEach((swap) => {
        const target = holderFor(server, `${swap[1]}_age_${n}`)
        if (target === null) {
          console.warn(`[firmages] blood_moon: lunar event firmages:${swap[1]}_age_${n} missing, EC2 default kept`)
          return
        }
        forecast.insertLunarEvent(target, swap[0])
        console.info(`[firmages] blood_moon: ${swap[1]} in ${swap[0]} day(s) is now the Age ${n} variant`)
      })
    })
  }

  const anyoneHasGate = (server) => {
    let ok = false
    server.players.forEach((p) => {
      if (ProgressiveStages.has(p, GATE_STAGE)) ok = true
    })
    return ok
  }

  ServerEvents.tick((event) => {
    const server = event.server
    if (server.tickCount % PERIOD_TICKS !== 0 || server.players.isEmpty()) return
    if (anyoneHasGate(server)) {
      reskin(server)
      return
    }
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
