// Firmament Ages - announce a new Age to everyone online (one team = one progression).
// Chat line: [stage].unlock_message in the stage TOML. Here: title, subtitle, sound, firework.
// ProgressiveStages.onGranted fires for every engine grant (quest reward, command, trigger, script).

(() => {
  const FA = global.FA
  const short = (stage) => String(stage).replace(/^progressivestages:/, '')
  const json = (obj) => JSON.stringify(obj)

  ProgressiveStages.onGranted((player, stage) => {
    const s = short(stage)
    const title = FA.AGE_TITLES[s]
    if (!title || s === 'dawn') return // dawn is the starting stage, nothing to celebrate
    const server = player.server
    const sub = FA.AGE_SUBTITLES[s] || ''

    server.runCommandSilent('title @a times 10 80 20')
    server.runCommandSilent(`title @a subtitle ${json({ text: sub, color: 'gray' })}`)
    server.runCommandSilent(`title @a title ${json({ text: title, color: 'gold', bold: true })}`)
    server.runCommandSilent('playsound minecraft:ui.toast.challenge_complete master @a')
    // POC: 1.21 firework entity NBT (FireworksItem + minecraft:fireworks component); verify in game.
    server.players.forEach((p) => {
      server.runCommandSilent(`execute at ${p.username} run summon minecraft:firework_rocket ~ ~1 ~ ` +
        '{LifeTime:30,FireworksItem:{id:"minecraft:firework_rocket",count:1,components:{"minecraft:fireworks":' +
        '{flight_duration:1,explosions:[{shape:"large_ball",colors:[I;16755200],has_twinkle:true}]}}}}')
    })
  })
})()
