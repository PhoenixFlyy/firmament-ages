// Firmament Ages - PoC helper commands (op only, KubeJS ServerEvents.basicCommand, KubeJS 2101).
//   /fa_scan <radius> <id prefix>   count blocks whose id starts with the prefix around the caller
//                                   (loaded chunks only, y = caller +-64). Example: /fa_scan 48 create:zinc_ore
//   /fa_stages                      list the caller's ProgressiveStages stages
// Used by dev/poc-checklist.md to prove that disabled worldgen really is gone. It is slow:
// a radius of 48 blocks means about 1.2 million block lookups, so keep the radius small.

(() => {
  ServerEvents.basicCommand('fa_scan', (event) => {
    const player = event.player
    if (!player) {
      event.respond(Text.red('Run this as a player.'))
      return
    }
    const args = String(event.input).trim().split(/\s+/)
    const radius = Math.min(64, parseInt(args[0], 10) || 16)
    const prefix = args[1] || 'tfc:ore/'
    const level = event.level
    const px = Math.floor(player.x)
    const py = Math.floor(player.y)
    const pz = Math.floor(player.z)
    const counts = {}
    let total = 0
    // No const inside the loop bodies: Rhino reports "redeclaration of var" on the second pass.
    const tally = (id) => {
      if (id.startsWith(prefix)) {
        counts[id] = (counts[id] || 0) + 1
        total++
      }
    }
    for (let x = px - radius; x <= px + radius; x++) {
      for (let z = pz - radius; z <= pz + radius; z++) {
        for (let y = Math.max(level.minBuildHeight, py - 64); y <= Math.min(level.maxBuildHeight - 1, py + 64); y++) {
          tally(String(level.getBlock(x, y, z).id))
        }
      }
    }
    const top = Object.keys(counts).sort((a, b) => counts[b] - counts[a]).slice(0, 10)
    event.respond(Text.gold(`[fa_scan] ${total} block(s) matching "${prefix}" within ${radius}`))
    top.forEach((id) => event.respond(Text.gray(`  ${counts[id]} x ${id}`)))
  })

  ServerEvents.basicCommand('fa_stages', (event) => {
    if (!event.player) return
    event.respond(Text.gold(`Stages: ${String(ProgressiveStages.list(event.player))}`))
  })
})()
