// Gamerules the pack forces on every server start, existing worlds included.
// announceAdvancements false: no "<player> has made the advancement [...]" chat. Advancements stay server-side
// (FTB Quests advancement tasks need them); the client hides toasts and the screen in firmages-core.
// playersSleepingPercentage 50 (Felix 2026-10-13, H1): half of the online players in bed skip the night. Beds that
// can skip the night exist from the Iron Age (dev/age_map.toml); the TFC thatch bed never skips it.
// reducedDebugInfo true (Felix 2026-10-13, H5): F3 hides the coordinates. Set on every start so a new world starts
// hidden; stages/grants.js reconcile() lifts it when a player of the team logs in with FA.COORDS_FROM_AGE (age_1).
// decorative_blocks:disableThatch true: shears no longer turn hay into Decorative Blocks' thatch fluid (a vanilla
// hay mechanic beside TFC's own thatch block; research n2-deco-mods P3).
ServerEvents.loaded(event => {
  event.server.runCommandSilent('gamerule announceAdvancements false')
  event.server.runCommandSilent('gamerule playersSleepingPercentage 50')
  event.server.runCommandSilent('gamerule reducedDebugInfo true')
  event.server.runCommandSilent('gamerule decorative_blocks:disableThatch true')
})
