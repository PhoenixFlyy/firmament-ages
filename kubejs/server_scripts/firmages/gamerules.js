// Gamerules the pack forces on every server start, existing worlds included.
// announceAdvancements false: no "<player> has made the advancement [...]" chat. Advancements stay server-side
// (FTB Quests advancement tasks need them); the client hides toasts and the screen in firmages-core.
ServerEvents.loaded(event => {
  event.server.runCommandSilent('gamerule announceAdvancements false')
})
