// Firmament Ages - the Frontier Compass (Iron Age, dev/decisions-while-away.md 2026-10-14): used, it names the
// direction and the distance of the nearest Cold Forge, the ruin that keeps the Frontier Shard (structure
// firmages:cold_forge, template from dev/gen_cold_forge.py). The search is vanilla's map search
// (ServerLevel#findNearestMapStructure on the structure tag firmages:cold_forge, 100 chunks around the player, the
// radius of /locate); it does not skip a forge that was found before, so the needle always points to the nearest one.
// Overworld only: the Cold Forge generates in the TFC hills. A short cooldown keeps the search off the server tick.

(() => {
  const TagKey = Java.loadClass('net.minecraft.tags.TagKey')
  const Registries = Java.loadClass('net.minecraft.core.registries.Registries')
  const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')
  const COLD_FORGE = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath('firmages', 'cold_forge'))
  const RADIUS = 100 // chunks
  const COOLDOWN = 100 // ticks
  // atan2 with x east and z south: 0 = east, a quarter turn = south
  const DIRS = ['east', 'south-east', 'south', 'south-west', 'west', 'north-west', 'north', 'north-east']

  // KubeJS adds bean getters that shadow vanilla methods (level.dimension), so the id is read either way.
  const dimId = (level) => {
    try {
      return String(level.dimension().location())
    } catch (e) {
      return String(level.dimension)
    }
  }

  const distanceText = (d) => {
    const n = d >= 1000 ? Math.round(d / 100) * 100 : Math.max(50, Math.round(d / 50) * 50)
    return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  }

  ItemEvents.rightClicked('firmages:frontier_compass', (event) => {
    const player = event.player
    const level = event.level
    if (level.isClientSide()) return
    player.getCooldowns().addCooldown(event.item.getItem(), COOLDOWN)
    if (dimId(level) !== 'minecraft:overworld') {
      player.setStatusMessage(Text.gray('The needle wanders. Cold iron lies under the sky of home.'))
      return
    }
    let pos = null
    try {
      pos = level.findNearestMapStructure(COLD_FORGE, player.blockPosition(), RADIUS, false)
    } catch (e) {
      console.error(`[firmages] frontier_compass: search failed: ${e}`)
    }
    if (pos == null) {
      player.setStatusMessage(Text.gray('The needle wanders. No cold forge within reach.'))
      return
    }
    const dx = pos.getX() - player.getX()
    const dz = pos.getZ() - player.getZ()
    const d = Math.sqrt(dx * dx + dz * dz)
    if (d < 24) {
      player.setStatusMessage(Text.gray('Cold iron, here.'))
      return
    }
    const dir = DIRS[(((Math.round(Math.atan2(dz, dx) / (Math.PI / 4))) % 8) + 8) % 8]
    player.setStatusMessage(Text.gray(`Cold iron, ${dir}. About ${distanceText(d)} blocks.`))
  })
})()
