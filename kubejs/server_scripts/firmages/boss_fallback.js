// Firmament Ages - boss fallback gateways (Doc 08 section 5.1): a missing or bugged boss never blocks an Age.
// Each boss checkpoint whose drop goes into a signature item has a sigil (startup_scripts/items.js, global.FA_SIGILS)
// that opens a Gateways to Eternity gate of stand-in waves (kubejs/data/firmages/gateways/<age>_trial.json). The gate
// pays out the boss drop itself, so every recipe slot (#firmages:boss_token/<age>), quest and second use of the drop
// accepts it unchanged. The sigil recipes are in recipes/boss_sigils.js; the firmages-core gate keeps each one closed
// until its Age (age_items tags).
//
// Opening follows GatePearlItem#useOn of Gateways 5.1.0: the gate's own canOpen check, the gate stands on top of the
// clicked block's collision shape, it keeps the gate's spacing to other gates, rises up to 4 blocks to find room, then
// the entity is added and onGateCreated() starts it. The summoner is the player (rewards drop at the gate).

const FA_GATE_REGISTRY = Java.loadClass('dev.shadowsoffire.gateways.gate.GatewayRegistry')
const FA_GATE_ENTITY = Java.loadClass('dev.shadowsoffire.gateways.entity.GatewayEntity')
const FA_RL = Java.loadClass('net.minecraft.resources.ResourceLocation')
const FA_AXIS_Y = Java.loadClass('net.minecraft.core.Direction$Axis').Y

// Opens gate `gateId` on top of `pos` for `player`. Returns null on success, else the reason as a string.
function faOpenFallbackGate(level, player, pos, gateId) {
  const holder = FA_GATE_REGISTRY.INSTANCE.holder(FA_RL.parse(gateId))
  if (!holder.isBound()) return `unknown gateway ${gateId}`
  const gate = holder.get()
  const denied = gate.canOpen(player)
  if (denied != null) return String(denied.getString())
  const entity = gate.createEntity(level, player)
  const shape = level.getBlockState(pos).getCollisionShape(level, pos)
  const x = pos.getX() + 0.5
  let y = pos.getY() + (shape.isEmpty() ? 0 : shape.max(FA_AXIS_Y))
  const z = pos.getZ() + 0.5
  entity.setPos(x, y, z)
  const spacing = Math.max(0, gate.rules().spacing())
  if (!level.getEntitiesOfClass(FA_GATE_ENTITY, entity.getBoundingBox().inflate(spacing)).isEmpty()) {
    return 'another Gateway is too close'
  }
  // noCollision is overloaded (Entity, AABB); Rhino needs the signature spelled out.
  const free = () => level['noCollision(net.minecraft.world.entity.Entity)'](entity)
  for (let i = 0; i < 4 && !free(); i++) {
    y += 1
    entity.setPos(x, y, z)
  }
  if (!free()) return 'not enough space to open this Gateway'
  level.addFreshEntity(entity)
  entity.onGateCreated()
  return null
}

BlockEvents.rightClicked((event) => {
  const sigil = (global.FA_SIGILS || {})[String(event.item.id)]
  if (!sigil) return
  // Rhino rejects a const inside try ("redeclaration of var"), so the locals are declared before it.
  const player = event.player
  let failed = null
  try {
    failed = faOpenFallbackGate(event.level, player, event.block.pos, sigil.gate)
    if (failed) {
      player.tell(Text.red(`The ${sigil.name} fails: ${failed}.`))
    } else {
      if (!player.isCreative()) event.item.shrink(1)
      console.info(`[firmages] boss fallback: ${player.username} opened ${sigil.gate} with ${sigil.name} at ${event.block.pos}`)
    }
  } catch (e) {
    console.error(`[firmages] boss fallback: ${sigil.gate} failed: ${e}`)
  }
  // A sigil never uses the block it is clicked on (no chest or machine GUI opens). cancel() ends the handler, so last.
  event.cancel()
})

// The Ender Dragon has a second drop that later Ages need: DE adds its heart to the dragon loot (Space Age item, the
// Dragon Heart quest, awakened draconium, the Chaos Sigil). The heart is locked until age_7 (a player could not pick it
// up), so the End Trial cannot pay it out in its gateway file; from the Space Age on this hook adds one heart per gate.
const FA_END_HEART = { gate: 'firmages:end_trial', item: 'draconicevolution:dragon_heart', age: 'age_7' }

NativeEvents.onEvent(Java.loadClass('dev.shadowsoffire.gateways.event.GateEvent$Completed'), (event) => {
  // As above: no const inside try (Rhino), so the gate is declared first.
  let gate = null
  try {
    gate = event.getEntity()
    if (String(FA_GATE_REGISTRY.INSTANCE.getKey(gate.getGateway())) != FA_END_HEART.gate) return
    if (!FirmAges.isUnlocked(FA_END_HEART.age)) return
    gate.spawnAtLocation(Item.of(FA_END_HEART.item))
    console.info(`[firmages] boss fallback: ${FA_END_HEART.gate} completed in ${FA_END_HEART.age}: one ${FA_END_HEART.item} added`)
  } catch (e) {
    console.error(`[firmages] boss fallback: End Trial heart failed: ${e}`)
  }
})
