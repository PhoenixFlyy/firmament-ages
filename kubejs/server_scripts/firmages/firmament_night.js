// Firmament Ages - the Firmament Night: the first night after a new Age glows in that Age's colour (n2-creative P4).
//
// Data: kubejs/data/firmages/enhancedcelestials2core/lunar/ (dev/gen_firmament_night.py): one Enhanced Celestials 2
// lunar event firmages:firmament_age_<N> per Age with sky and moon colour, a larger moon, a hot-bar line at dusk,
// half the monster spawns and 1.5x experience. None of them has an event_probability file, so EC2 never rolls them.
//
// Start: the first grant of age_N in this world schedules the night of age_N. ProgressiveStages fires onGranted once
// per team member; the world counter "seen" (highest Age that got its night) lets only the first call through and
// skips an Age that is granted again after a revoke. The night is tonight if the Age arrives before midnight, else
// the next night; the first 400 ticks after the grant belong to the shrine ceremony and the Age reload.
// The script calls LunarForecast.setLunarEventTonight / insertLunarEvent (EC2 Core 2.0.3.3, javap) and not the
// command /ec setLunarEvent: the command jumps the clock to night when it runs by day and refuses in rain.
// setLunarEventTonight by day starts the event at dusk (night_start_time 12500) and ends it at the next dawn, so
// the clock stays untouched. EC2 shows no lunar event while it rains (dimension setting requires_clear_skies), so the
// script keeps the weather clear until that dawn.
// The pending night is kept in server persistentData and checked every 10 s until it has passed: a forecast that
// is rolled again (blood_moon.js at mob_2 in the same grant wave, /ec lunarForecast recompute) loses the inserted
// event, and the next pass puts it back. Sleeping through it ends it like any night.
// Worlds from before this script: the first login records the team's current Age as seen, so an update never
// replays old Ages. Admin: /stage grant <player> age_N on a test world plays the night of a new N once.
// Starfall (firmages-core SPEC 25.3): on the Firmament Night itself the script calls FirmAges.starfallTonight() once,
// so a star falls that night from the Iron Age on (no Enhanced Celestials code in the mod).

(() => {
  if (!Platform.isLoaded('enhancedcelestials2core')) {
    console.warn('[firmages] firmament_night: Enhanced Celestials 2 Core not loaded, Firmament Nights off')
    return
  }
  const EC = Java.loadClass('dev.corgitaco.enhancedcelestials2core.EnhancedCelestials')
  const ECRegistry = Java.loadClass('dev.corgitaco.enhancedcelestials2core.api.EnhancedCelestialsRegistry')
  const ResourceKey = Java.loadClass('net.minecraft.resources.ResourceKey')
  const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')

  const DAY = 24000
  const MIDNIGHT = 18000 // day time of midnight; after it the night is too short and the next one is taken
  const DELAY_TICKS = 400
  const PERIOD_TICKS = 200
  const SEEN = 'firmages_night_seen' // server persistentData: highest Age number (0..9) that got its night
  const INIT = 'firmages_night_init' // SEEN was initialised for this world
  const PENDING_AGE = 'firmages_night_age' // the night to play: Age number,
  const PENDING_DAY = 'firmages_night_day' // overworld day (day time / 24000) of that night,
  const PENDING_AFTER = 'firmages_night_after' // overworld day time before which nothing is scheduled
  const STAR_DAY = 'firmages_night_star' // overworld day + 1 of the night that already asked for a falling star

  const short = (stage) => String(stage).replace(/^progressivestages:/, '')
  const eventId = (n) => `firmages:firmament_age_${n}`
  const clearPending = (data) => [PENDING_AGE, PENDING_DAY, PENDING_AFTER].forEach((k) => data.remove(k))

  const highestAge = (player) => {
    let best = -1
    for (let k = 0; k <= 9; k++) {
      if (ProgressiveStages.has(player, `age_${k}`)) best = k
    }
    return best
  }

  const holderFor = (server, n) => {
    const registry = server.registryAccess().registryOrThrow(ECRegistry.LUNAR_EVENT_KEY)
    const key = ResourceKey.create(ECRegistry.LUNAR_EVENT_KEY, ResourceLocation.fromNamespaceAndPath('firmages', `firmament_age_${n}`))
    // getHolder has a ResourceKey and a ResourceLocation overload, which Rhino calls ambiguous; getHolderOrThrow has one
    try {
      return registry.getHolderOrThrow(key)
    } catch (e) {
      return null
    }
  }

  // true when the forecast holds the event of Age n on the night that is `offset` days from today
  const scheduled = (forecast, n, offset) => {
    let found = false
    forecast.upcomingEventsByDaysInAdvance().forEach((days, holder) => {
      if (Number(days) === offset && String(holder.getRegisteredName()) === eventId(n)) found = true
    })
    return found
  }

  const ensure = (server) => {
    const data = server.persistentData
    if (!data.contains(PENDING_AGE)) return
    const n = data.getInt(PENDING_AGE)
    const target = Number(data.getLong(PENDING_DAY))
    const level = server.overworld()
    const now = Number(level.getDayTime())
    if (now < Number(data.getLong(PENDING_AFTER))) return
    const offset = target - Math.floor(now / DAY)
    if (offset < 0) {
      clearPending(data) // the night has passed
      return
    }
    const holder = holderFor(server, n)
    if (holder === null) {
      console.warn(`[firmages] firmament_night: lunar event ${eventId(n)} missing, night of age_${n} dropped`)
      clearPending(data)
      return
    }
    if (offset === 0 && Number(data.getLong(STAR_DAY)) !== target + 1) {
      data.putLong(STAR_DAY, target + 1)
      if (typeof FirmAges !== 'undefined' && FirmAges.starfallTonight) FirmAges.starfallTonight()
    }
    // clear sky until the dawn that ends the night (EC2 hides lunar events in rain)
    const clear = (target + 1) * DAY - now
    EC.lunarForecastWorldData(level).ifPresent((forecast) => {
      if (scheduled(forecast, n, offset)) return
      if (offset === 0) forecast.setLunarEventTonight(holder)
      else forecast.insertLunarEvent(holder, offset)
      level.setWeatherParameters(clear, 0, false, false)
      console.info(`[firmages] firmament_night: ${eventId(n)} scheduled ${offset === 0 ? 'tonight' : 'for the next night'}`)
    })
    if (level.isRaining()) level.setWeatherParameters(clear, 0, false, false)
  }

  const schedule = (server, n) => {
    const level = server.overworld()
    const now = Number(level.getDayTime())
    const today = Math.floor(now / DAY)
    const data = server.persistentData
    data.putInt(PENDING_AGE, n)
    data.putLong(PENDING_DAY, now % DAY >= MIDNIGHT ? today + 1 : today)
    data.putLong(PENDING_AFTER, now + DELAY_TICKS) // day time: Rhino finds no getGameTime on ServerLevel (KubeJS 2101.7.2)
    console.info(`[firmages] firmament_night: age_${n} reached, night pending`)
  }

  PlayerEvents.loggedIn((event) => {
    const data = event.server.persistentData
    if (data.getBoolean(INIT)) return
    data.putBoolean(INIT, true)
    data.putInt(SEEN, highestAge(event.player))
  })

  ProgressiveStages.onGranted((player, stage) => {
    const m = short(stage).match(/^age_([0-9])$/)
    if (!m) return
    const n = parseInt(m[1], 10)
    const server = player.server
    const data = server.persistentData
    if (!data.getBoolean(INIT)) {
      // granted before any login since this script exists (e.g. /reload in a running session)
      data.putBoolean(INIT, true)
      data.putInt(SEEN, n - 1)
    }
    if (n <= data.getInt(SEEN)) return
    data.putInt(SEEN, n)
    schedule(server, n)
  })

  ServerEvents.tick((event) => {
    if (event.server.tickCount % PERIOD_TICKS !== 0) return
    ensure(event.server)
  })
})()
