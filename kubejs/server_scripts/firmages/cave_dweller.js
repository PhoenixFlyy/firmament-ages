// Firmament Ages - Cave Dweller ReEvolved 1.4-rc2 hunts only until the Iron Age (Felix 2026-10-13, H12).
// From age_2 on (the server-wide Age state of firmages-core, FirmAges.isUnlocked) the entity never joins a level:
// EntityEvents.spawned is NeoForge's EntityJoinLevelEvent, so this covers the mod's own spawn timer
// (Utils.trySpawnMob -> addFreshEntityWithPassengers), /summon and dwellers saved in chunks (the mod has no spawn egg).
// ProgressiveStages cannot express "only before a stage" (it locks while a stage is missing), hence this script.
// The mod still sends its cave noises (packet cave_dweller:cave_sound) on its timer and after every spawn attempt;
// firmages-core mutes the cave_dweller sounds on the client from age_2 (dev/decisions-while-away.md, H12).
// Config: config/cave_dweller/server_config.toml (one dweller at a time, Overworld only).

EntityEvents.spawned('cave_dweller:cave_dweller', (event) => {
  if (FirmAges.isUnlocked('age_2')) event.cancel()
})
