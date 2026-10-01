// priority: 100
// Firmament Ages - stage bookkeeping around the age stages.
//
// Age stages (dawn, age_0 ... age_9) are granted by the FTB Quests goal quest of each Age (team reward,
// reward type "gamestage", Doc 08 section 9.1). Everything that hangs on an Age is derived here, so the
// quest book only ever grants ONE stage per Age:
//   - mob ladder mob_N                           (Doc 08 section 5)
//   - helper stages ftbchunks_mapping, tool_<station> windows (Doc 08 section 2.3, Doc 10 v3 section 6.2)
//   - FTB Chunks claim/force-load quota with the Industrial Age (decision r8 A)
// reconcile() is idempotent: it runs on every ProgressiveStages grant/revoke and on login, so admin
// repairs (/stage grant ... age_N) and late joiners converge to the same state.
//
// API: ProgressiveStages KubeJS binding (PSKubeBindings, PS 3.0.5): has/grant/revoke(player, stage),
// onGranted/onRevoked((player, stage) => ...). Stage strings arrive normalized ("progressivestages:age_2").

(() => {
  const FA = global.FA
  const short = (stage) => String(stage).replace(/^progressivestages:/, '')
  const ageIndex = (stage) => FA.AGES.indexOf(stage)
  const mobIndex = (stage) => parseInt(String(stage).split('_')[1], 10)

  let busy = false

  const highestAge = (player) => {
    let best = -1
    FA.AGES.forEach((age, i) => {
      if (ProgressiveStages.has(player, age)) best = i
    })
    return best
  }

  const setStage = (player, stage, wanted) => {
    const has = ProgressiveStages.has(player, stage)
    if (wanted && !has) {
      if (!ProgressiveStages.grant(player, stage)) console.warn(`[firmages] could not grant ${stage} to ${player.username}`)
    } else if (!wanted && has) {
      ProgressiveStages.revoke(player, stage)
    }
  }

  // Rhino throws "redeclaration of var" for const/let declared directly inside a try block,
  // so the body lives in its own function and reconcile() only guards re-entry.
  const reconcileNow = (player) => {
    const idx = highestAge(player)
    if (idx < 0) return
    const age = FA.AGES[idx]

    // Mob ladder. No const inside the loop body: Rhino reports "redeclaration of var" on the second pass.
    const top = mobIndex(FA.MOB_STAGE_FOR[age])
    for (let k = 0; k <= 9; k++) {
      setStage(player, `mob_${k}`, FA.MOB_LADDER_CUMULATIVE ? k <= top : k === top)
    }

    // Helper windows [from, to).
    Object.keys(FA.HELPER_WINDOWS).forEach((helper) => {
      const from = FA.HELPER_WINDOWS[helper][0]
      const to = FA.HELPER_WINDOWS[helper][1]
      const wanted = idx >= ageIndex(from) && (to === null || idx < ageIndex(to))
      setStage(player, helper, wanted)
    })
  }

  const reconcile = (player) => {
    if (busy) return
    busy = true
    try {
      reconcileNow(player)
    } finally {
      busy = false
    }
  }

  // FTB Chunks quota: per-player extras; party_limit_mode "largest" (config/ftbchunks-world.snbt) turns
  // them into a team quota. Command syntax verified in FTBChunksCommands.java (tag v2101.1.22).
  const grantChunkQuota = (player) => {
    const q = FA.CHUNK_QUOTA
    player.server.players.forEach((p) => {
      player.server.runCommandSilent(`ftbchunks admin extra_claim_chunks ${p.username} set ${q.claims}`)
      player.server.runCommandSilent(`ftbchunks admin extra_force_load_chunks ${p.username} set ${q.forceLoads}`)
    })
  }

  ProgressiveStages.onGranted((player, stage) => {
    const s = short(stage)
    if (FA.AGES.indexOf(s) >= 0) {
      reconcile(player)
      if (s === FA.CHUNK_QUOTA.stage) grantChunkQuota(player)
      // POC: Apotheosis world tier per Age (Haven/Frontier/Ascent/Summit/Pinnacle, Doc 08 section 8).
      // The command syntax is unverified, see dev/config-todo.md. Example shape only:
      // player.server.runCommandSilent(`apotheosis world_tier set ${player.username} frontier`)
    }
  })

  ProgressiveStages.onRevoked((player, stage) => {
    if (FA.AGES.indexOf(short(stage)) >= 0) reconcile(player)
  })

  PlayerEvents.loggedIn((event) => {
    reconcile(event.player)
    // Late joiners: the quota is per player; give it to the newcomer once the team has the Industrial Age.
    // POC: with party_limit_mode "largest" this is redundant but keeps "owner"/"sum" modes working.
    if (ProgressiveStages.has(event.player, FA.CHUNK_QUOTA.stage)) grantChunkQuota(event.player)
  })

  // Optional advancement -> stage grants (Triumph pattern, Doc 03 section 3). Empty on purpose: every
  // Age is granted by its goal quest. Entries look like  'tfc:story/firepit': 'age_0'.
  const ADVANCEMENT_GRANTS = {}
  PlayerEvents.advancement((event) => {
    // event.advancement is null for some advancements a new player gets on first join (seen with mock players)
    if (!event.advancement) return
    const stage = ADVANCEMENT_GRANTS[String(event.advancement.id)]
    if (stage) ProgressiveStages.grant(event.player, stage)
  })

  // Admin repair: /fa_reconcile  (op only; KubeJS basicCommand, verified in KubeJSCommands.java 2101).
  ServerEvents.basicCommand('fa_reconcile', (event) => {
    const player = event.player
    if (!player) {
      event.respond(Text.red('Run this as a player.'))
      return
    }
    reconcile(player)
    event.respond(Text.green(`Stages: ${String(ProgressiveStages.list(player))}`))
  })

  // PoC self-test without a player (op only, runs from the console): a FakePlayer walks dawn -> age_6 and back,
  // reconcile() runs after every step and the stage list is printed. PS fires onGranted/onRevoked only for
  // players in the server's player list, so reconcile() is called directly here; the event wiring itself
  // needs a client (dev/poc-checklist.md C21). All stages of the fake player are removed at the end.
  // Each line carries the wall time of the ProgressiveStages call ("ps") and of reconcile() ("rc") in ms.
  ServerEvents.basicCommand('fa_selftest', (event) => {
    const FakePlayerFactory = Java.loadClass('net.neoforged.neoforge.common.util.FakePlayerFactory')
    const GameProfile = Java.loadClass('com.mojang.authlib.GameProfile')
    const UUID = Java.loadClass('java.util.UUID')
    const level = event.server.overworld()
    const fp = FakePlayerFactory.get(level, new GameProfile(UUID.fromString('fa000000-0000-4000-8000-00000000f1a0'), '[FA_Selftest]'))
    const say = (msg) => {
      event.respond(Text.gray(msg))
      console.info(`[fa_selftest] ${msg}`)
    }
    const list = () => {
      const l = []
      ProgressiveStages.list(fp).forEach((s) => l.push(String(s).replace(/^progressivestages:/, '')))
      return l.sort().join(',')
    }
    ProgressiveStages.revokeAll(fp)
    say(`start: [${list()}]`)
    ;['dawn', 'age_0', 'age_1', 'age_2', 'age_3', 'age_4', 'age_5', 'age_6'].forEach((age) => {
      const t0 = Date.now()
      const ok = ProgressiveStages.grant(fp, age)
      const t1 = Date.now()
      reconcile(fp)
      say(`grant ${age} -> ${ok} (ps ${t1 - t0} ms, rc ${Date.now() - t1} ms): [${list()}]`)
    })
    ;['age_6', 'age_5', 'age_4', 'age_3', 'age_2'].forEach((age) => {
      const t0 = Date.now()
      const ok = ProgressiveStages.revoke(fp, age)
      const t1 = Date.now()
      reconcile(fp)
      say(`revoke ${age} -> ${ok} (ps ${t1 - t0} ms, rc ${Date.now() - t1} ms): [${list()}]`)
    })
    ProgressiveStages.revokeAll(fp)
    say(`cleanup: [${list()}]`)
  })
})()
