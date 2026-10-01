// Firmament Ages - announce a new Age to everyone online (one team = one progression).
// Chat line: [stage].unlock_message in the stage TOML. Here: title, subtitle, sound, firework.
// ProgressiveStages.onGranted fires for every engine grant (quest reward, command, trigger, script).
//
// firmages-core 0.3.0 and later owns the Age transition (SPEC section 8): the shrine plays the full ceremony and
// every other Age grant gets the short one (title, sting, beam). This script then stays silent for the Age stages,
// otherwise players would see two titles. It still announces finale_won, which is not an Age. The version comes
// from the mod list (Platform.getInfo), because the FirmAges binding has no version or ceremony flag.

(() => {
  const FA = global.FA
  const short = (stage) => String(stage).replace(/^progressivestages:/, '')
  const json = (obj) => JSON.stringify(obj)

  // true when the loaded firmages-core is 0.3.0 or newer ("0.3.0", "0.3.1-beta" ...)
  const modOwnsCeremony = (() => {
    try {
      if (!Platform.isLoaded('firmages')) return false
      // let, not const: Rhino rejects a const inside this try block ("redeclaration of var m")
      let v = String(Platform.getInfo('firmages').getVersion()).match(/^(\d+)\.(\d+)/)
      if (!v) return false
      return parseInt(v[1], 10) > 0 || parseInt(v[2], 10) >= 3
    } catch (e) {
      console.warn(`[firmages] on_stage_added: cannot read the firmages-core version (${e}); KubeJS titles stay on`)
      return false
    }
  })()
  console.info(`[firmages] on_stage_added: Age titles by ${modOwnsCeremony ? 'firmages-core ceremony' : 'KubeJS'}`)

  ProgressiveStages.onGranted((player, stage) => {
    const s = short(stage)
    const title = FA.AGE_TITLES[s]
    if (!title || s === 'dawn') return // dawn is the starting stage, nothing to celebrate
    if (modOwnsCeremony && FA.AGES.indexOf(s) >= 0) return // the shrine ceremony shows this Age
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
