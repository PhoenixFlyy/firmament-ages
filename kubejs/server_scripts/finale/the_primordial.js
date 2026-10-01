// Firmament Ages - the end boss, version 1 (Doc 08 section 10.4, r10 A "script first"; firmages-core SPEC section 16).
// firmages-core owns the place and the result: it fires OriginEvent$Gathering when the whole team stands at the altar
// of The Origin, and grants finale_won plus the FINALE ceremony when an entity with FirmAges.finalBossTag() dies.
// This script owns the fight in between:
//   1. Gathering -> a Gateways to Eternity gate (data/firmages/gateways/the_origin.json) above the altar:
//      Maledictus, then Ignis, then both.
//   2. Gate completed -> "The Primordial": an Ender Guardian of Cataclysm with more health and armour and the final-boss
//      tag, which is all firmages-core needs.
//   3. Phases by health: at two thirds it calls Ender Golems and darkens the arena, at one third Endermapteras, lightning
//      and resistance for itself.
// The script never grants finale_won. A new Gathering while a gate or the boss is alive is ignored (after a wipe the
// gate fails and the team can gather again once the mod's cooldown has passed).

const FA_FINALE = {
  dim: 'firmages:origin',
  gate: 'firmages:the_origin',
  boss: 'cataclysm:ender_guardian',
  health: 1024, // the vanilla cap of generic.max_health
  armor: 20,
  name: 'The Primordial'
}

// KubeJS adds bean getters that shadow the vanilla methods of the same name (entity.level, level.dimension), so the
// script reads the level with getCommandSenderWorld() and the dimension id through either form.
function faDimId(level) {
  try {
    return String(level.dimension().location())
  } catch (e) {
    return String(level.dimension)
  }
}

// A script error inside a NeoForge event would crash the server tick (an entity tick fires GateEvent): log it instead.
function faGuard(what, fn) {
  return (event) => {
    try {
      fn(event)
    } catch (e) {
      console.error(`[finale] ${what} failed: ${e}`)
    }
  }
}

function faFightRunning(level) {
  const tag = FirmAges.finalBossTag()
  for (const e of level.getAllEntities()) {
    if (!e.isAlive()) continue
    const id = String(e.getEncodeId())
    if (id.startsWith('gateways:')) return true
    if (e.getTags().contains(tag)) return true
  }
  return false
}

function faRun(server, cmd) {
  server.runCommandSilent(`execute in ${FA_FINALE.dim} run ${cmd}`)
}

NativeEvents.onEvent(Java.loadClass('dev.firmages.core.origin.OriginEvent$Gathering'), faGuard('Gathering', (event) => {
  const level = event.getLevel()
  const server = level.getServer()
  if (FirmAges.isFinaleWon() || faFightRunning(level)) return
  const a = event.getAltar()
  faRun(server, `open_gateway ${a.getX() + 0.5} ${a.getY() + 3} ${a.getZ() + 0.5} ${FA_FINALE.gate}`)
  faRun(server, `tellraw @a[distance=..48,x=${a.getX()},y=${a.getY()},z=${a.getZ()}] {"text":"The Firmament answers. Something stirs beyond the gate.","color":"dark_purple","italic":true}`)
  console.info(`[finale] Gathering ${event.getCount()}: gate ${FA_FINALE.gate} opened at the altar`)
}))

NativeEvents.onEvent(Java.loadClass('dev.shadowsoffire.gateways.event.GateEvent$Completed'), faGuard('gate completed', (event) => {
  const level = event.getEntity().getCommandSenderWorld()
  if (faDimId(level) != FA_FINALE.dim || FirmAges.isFinaleWon()) return
  const alt = FirmAges.originAltar()
  const a = { x: alt[0], y: alt[1], z: alt[2] }
  const nbt = `{Tags:["${FirmAges.finalBossTag()}"],PersistenceRequired:1b,CustomNameVisible:1b,` +
    `CustomName:'{"text":"${FA_FINALE.name}","color":"dark_purple","bold":true}',Health:${FA_FINALE.health}f,` +
    `attributes:[{id:"minecraft:generic.max_health",base:${FA_FINALE.health}d},{id:"minecraft:generic.armor",base:${FA_FINALE.armor}d},` +
    `{id:"minecraft:generic.knockback_resistance",base:1.0d}]}`
  faRun(level.getServer(), `summon ${FA_FINALE.boss} ${a.x + 0.5} ${a.y + 2} ${a.z + 8.5} ${nbt}`)
  faRun(level.getServer(), `title @a[distance=..64,x=${a.x},y=${a.y},z=${a.z}] title {"text":"${FA_FINALE.name}","color":"dark_purple"}`)
  console.info(`[finale] gate completed: ${FA_FINALE.name} (${FA_FINALE.boss}) summoned`)
}))

// KubeJS 2101.7 EntityEvents.afterHurt did not fire on the test server (not even for a zombie), so the phases listen to
// NeoForge's LivingDamageEvent.Post directly; the tag check comes first, so any other damage costs one set lookup.
NativeEvents.onEvent(Java.loadClass('net.neoforged.neoforge.event.entity.living.LivingDamageEvent$Post'), faGuard('boss phase', (event) => {
  const boss = event.getEntity()
  if (!boss.getTags().contains(FirmAges.finalBossTag()) || !boss.isAlive()) return
  const data = boss.getPersistentData()
  const phase = data.getInt('fa_phase')
  const frac = boss.getHealth() / boss.getMaxHealth()
  const server = boss.getServer()
  const at = (cmd) => server.runCommandSilent(`execute in ${FA_FINALE.dim} positioned ${boss.getX()} ${boss.getY()} ${boss.getZ()} run ${cmd}`)
  if (phase < 1 && frac <= 2 / 3) {
    data.putInt('fa_phase', 1)
    at(`summon cataclysm:ender_golem ~4 ~ ~`)
    at(`summon cataclysm:ender_golem ~-4 ~ ~`)
    at(`effect give @a[distance=..48] minecraft:darkness 8 0`)
    console.info('[finale] phase 2')
  } else if (phase < 2 && frac <= 1 / 3) {
    data.putInt('fa_phase', 2)
    for (const [dx, dz] of [[5, 0], [-5, 0], [0, 5], [0, -5]]) {
      at(`summon cataclysm:endermaptera ~${dx} ~1 ~${dz}`)
      at(`summon minecraft:lightning_bolt ~${dx * 2} ~ ~${dz * 2}`)
    }
    faRun(server, `effect give @e[tag=${FirmAges.finalBossTag()},distance=..64,x=${boss.getX()},y=${boss.getY()},z=${boss.getZ()}] minecraft:resistance 30 1`)
    console.info('[finale] phase 3')
  }
}))
