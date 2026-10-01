// Firmament Ages - the end boss, version 1 (Doc 08 section 10.4, r10 A "script first"; firmages-core SPEC section 16).
// firmages-core owns the place and the result: it fires OriginEvent$Gathering when the whole team stands at the altar
// of The Origin, and grants finale_won plus the FINALE ceremony when an entity with FirmAges.finalBossTag() dies.
// This script owns the fight in between:
//   1. Gathering -> a Gateways to Eternity gate (data/firmages/gateways/the_origin.json) above the altar:
//      Maledictus, then Ignis, then both.
//   2. That gate completed (gateway id and dimension checked) -> "The Primordial": an Ender Guardian of Cataclysm with
//      more health, armour toughness and the final-boss tag, which is all firmages-core needs.
//   3. Phases by health, each announced to the arena: at two thirds it calls Ender Golems, darkens the arena and takes
//      Resistance I, at one third it calls Endermapteras and lightning and takes Resistance II, both until it dies.
//   4. Its death (the tag decides): the arena hears it, the summoned helpers vanish. firmages-core grants finale_won.
// The script never grants finale_won. A new Gathering while this gate or the boss is alive is ignored (after a wipe the
// gate fails and the team can gather again once the mod's cooldown, origin.gatherCooldownSeconds, has passed).
//
// Fight length (decision log, "Länge des Endkampfs"): Cataclysm caps the Ender Guardian at 22 damage per hit and 13
// damage per second (cataclysm-common.toml, shared with every Ender Guardian, unchanged), far below what two Chaotic
// swords deal (17.5 damage, 3.2 swings a second each), so the cap, the hurt cooldown and the armour set the length,
// not the gear or the team size. Measured on the test server (2026-10-01): 4.4 health per second whether the hits
// come 3 or 10 times a second or as 22.5 once a second, 3.6 with Resistance I, 3.1 with Resistance II; 1024 health
// fell in 4 min 46 s of uninterrupted hitting, so about 7 minutes with the dodging, teleports and helpers of a fight.

const FA_FINALE = {
  gate: 'firmages:the_origin', // the gateway of this fight (kubejs/data/firmages/gateways/the_origin.json)
  boss: 'cataclysm:ender_guardian',
  health: 1024, // the vanilla cap of generic.max_health
  armor: 20, // the Ender Guardian's own armour
  toughness: 20, // the cap of generic.armor_toughness: big and small hits lose about the same share
  name: 'The Primordial',
  addTag: 'firmages.primordial_add', // the helpers the phases summon
  arena: 48 // blocks around the altar that hear the fight
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

const FA_GATEWAYS = Java.loadClass('dev.shadowsoffire.gateways.gate.GatewayRegistry')

// The gateway id of a gate entity ("" when it cannot be read).
function faGateId(gate) {
  try {
    return String(FA_GATEWAYS.INSTANCE.getKey(gate.getGateway()))
  } catch (e) {
    return ''
  }
}

function faInOrigin(level) {
  return faDimId(level) == String(FirmAges.originDimension())
}

function faIsBoss(entity) {
  return entity.getTags().contains(FirmAges.finalBossTag())
}

// Our gate or a living final boss in The Origin.
function faFightRunning(level) {
  for (const e of level.getAllEntities()) {
    if (!e.isAlive()) continue
    if (String(e.getEncodeId()).startsWith('gateways:') && faGateId(e) == FA_FINALE.gate) return true
    if (faIsBoss(e)) return true
  }
  return false
}

function faAltar() {
  const a = FirmAges.originAltar()
  return { x: a[0], y: a[1], z: a[2] }
}

// Runs a command in The Origin.
function faRun(server, cmd) {
  server.runCommandSilent(`execute in ${FirmAges.originDimension()} run ${cmd}`)
}

function faArena() {
  const a = faAltar()
  return `@a[x=${a.x},y=${a.y},z=${a.z},distance=..${FA_FINALE.arena}]`
}

function faTell(server, text, color) {
  faRun(server, `tellraw ${faArena()} {"text":${JSON.stringify(text)},"color":"${color}","italic":true}`)
}

function faTitle(server, title, subtitle) {
  faRun(server, `title ${faArena()} subtitle {"text":${JSON.stringify(subtitle)},"color":"gray"}`)
  faRun(server, `title ${faArena()} title {"text":${JSON.stringify(title)},"color":"dark_purple"}`)
}

NativeEvents.onEvent(Java.loadClass('dev.firmages.core.origin.OriginEvent$Gathering'), faGuard('Gathering', (event) => {
  const level = event.getLevel()
  const server = level.getServer()
  if (!faInOrigin(level) || FirmAges.isFinaleWon() || faFightRunning(level)) return
  const a = event.getAltar()
  faRun(server, `open_gateway ${a.getX() + 0.5} ${a.getY() + 3} ${a.getZ() + 0.5} ${FA_FINALE.gate}`)
  faTell(server, 'The Firmament answers. Something stirs beyond the gate.', 'dark_purple')
  console.info(`[finale] Gathering ${event.getCount()}: gate ${FA_FINALE.gate} opened at the altar`)
}))

NativeEvents.onEvent(Java.loadClass('dev.shadowsoffire.gateways.event.GateEvent$Completed'), faGuard('gate completed', (event) => {
  const gate = event.getEntity()
  const level = gate.getCommandSenderWorld()
  if (!faInOrigin(level) || faGateId(gate) != FA_FINALE.gate || FirmAges.isFinaleWon()) return
  const server = level.getServer()
  const a = faAltar()
  const nbt = `{Tags:["${FirmAges.finalBossTag()}"],PersistenceRequired:1b,CustomNameVisible:1b,` +
    `CustomName:'{"text":"${FA_FINALE.name}","color":"dark_purple","bold":true}',Health:${FA_FINALE.health}f,` +
    `attributes:[{id:"minecraft:generic.max_health",base:${FA_FINALE.health}d},{id:"minecraft:generic.armor",base:${FA_FINALE.armor}d},` +
    `{id:"minecraft:generic.armor_toughness",base:${FA_FINALE.toughness}d},{id:"minecraft:generic.knockback_resistance",base:1.0d}]}`
  faRun(server, `summon ${FA_FINALE.boss} ${a.x + 0.5} ${a.y + 2} ${a.z + 8.5} ${nbt}`)
  faTitle(server, FA_FINALE.name, 'The first of all things wakes')
  console.info(`[finale] gate ${FA_FINALE.gate} completed: ${FA_FINALE.name} (${FA_FINALE.boss}) summoned`)
}))

// KubeJS 2101.7 EntityEvents.afterHurt did not fire on the test server (not even for a zombie), so the phases listen to
// NeoForge's LivingDamageEvent.Post directly; the tag check comes first, so any other damage costs one set lookup.
NativeEvents.onEvent(Java.loadClass('net.neoforged.neoforge.event.entity.living.LivingDamageEvent$Post'), faGuard('boss phase', (event) => {
  const boss = event.getEntity()
  if (!faIsBoss(boss) || !boss.isAlive()) return
  const data = boss.getPersistentData()
  const phase = data.getInt('fa_phase')
  const frac = boss.getHealth() / boss.getMaxHealth()
  const server = boss.getServer()
  const at = (cmd) => server.runCommandSilent(`execute in ${FirmAges.originDimension()} positioned ${boss.getX()} ${boss.getY()} ${boss.getZ()} run ${cmd}`)
  const add = `{Tags:["${FA_FINALE.addTag}"]}`
  if (phase < 1 && frac <= 2 / 3) {
    data.putInt('fa_phase', 1)
    faTitle(server, '', 'The Primordial calls the stone of the End')
    faTell(server, 'The Primordial: "You carried nine Ages here. The End remembers older ones."', 'dark_purple')
    at(`summon cataclysm:ender_golem ~4 ~ ~ ${add}`)
    at(`summon cataclysm:ender_golem ~-4 ~ ~ ${add}`)
    at(`effect give @a[distance=..${FA_FINALE.arena}] minecraft:darkness 8 0`)
    at(`effect give @e[tag=${FirmAges.finalBossTag()},distance=..4] minecraft:resistance infinite 0`)
    console.info('[finale] phase 2')
  } else if (phase < 2 && frac <= 1 / 3) {
    data.putInt('fa_phase', 2)
    faTitle(server, '', 'The Primordial hardens; the sky breaks')
    faTell(server, 'The Primordial: "Then fall with the Firmament."', 'dark_purple')
    for (const [dx, dz] of [[5, 0], [-5, 0], [0, 5], [0, -5]]) {
      at(`summon cataclysm:endermaptera ~${dx} ~1 ~${dz} ${add}`)
      at(`summon minecraft:lightning_bolt ~${dx * 2} ~ ~${dz * 2}`)
    }
    at(`effect give @e[tag=${FirmAges.finalBossTag()},distance=..4] minecraft:resistance infinite 1`)
    console.info('[finale] phase 3')
  }
}))

// The tag decides, as for firmages-core: only the death of the tagged boss ends the fight here.
NativeEvents.onEvent(Java.loadClass('net.neoforged.neoforge.event.entity.living.LivingDeathEvent'), faGuard('boss death', (event) => {
  const boss = event.getEntity()
  if (!faIsBoss(boss)) return
  const level = boss.getCommandSenderWorld()
  if (!faInOrigin(level)) return
  const server = level.getServer()
  faTell(server, 'The Primordial falls. The gate of the Firmament stands open.', 'light_purple')
  faRun(server, `kill @e[tag=${FA_FINALE.addTag}]`)
  console.info(`[finale] ${FA_FINALE.name} died (tag ${FirmAges.finalBossTag()}); firmages-core decides the finale`)
}))
