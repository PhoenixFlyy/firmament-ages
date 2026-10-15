// Firmament Ages - Age 9: chaos made, not looted (dev/decisions-while-away.md, 2026-10-14; research n7-adventure 4).
// The Chaos Guardian is an optional shortcut. The team's own Draconic reactor turns awakened draconium into chaos
// fragments (DE ReactorMenu hands the converted fuel out as large, medium and small fragments; the firmages-core
// Reactor Controller stops a cycle at 80 %, about 6.4 large fragments). So the reactor itself must be buildable
// without chaos:
//   - reactor core, stabilizer and injector at draconic level instead of chaotic (DE takes an injector of at least the
//     recipe's level for every ingredient, so a chaotic reactor recipe needed chaotic injectors, which need chaos);
//   - reactor core: an antimatter pellet as the catalyst instead of a chaos shard, two subatomic alloys (the age_8
//     token) instead of two large fragments;
//   - stabilizer: an awakened core instead of a chaotic core, an antimatter pellet instead of a large fragment.
// Budget after the bootstrap, about four reactor cycles: twelve chaotic injectors for the Ultimate Singularity at
// 1 large + 3 medium fragments each (16 large), the chaotic core (5), the Singularity's two token slots (2) and the
// coordinates (1), 24 large fragments. The chaotic injector takes an antimatter pellet where DE asks for a dragon egg,
// so the End stays optional.
// Same recipe ids as Draconic Evolution's, so its guide and EMI show the pack version.

ServerEvents.recipes((event) => {
  const consume = (ing) => ({ ingredient: ing, consume: true })
  const fusion = (id, json) => {
    event.remove({ id: id })
    json.type = 'draconicevolution:fusion_crafting'
    event.custom(json).id(id)
  }
  const AWAKENED = { tag: 'c:ingots/draconium_awakened' }
  const DRACONIUM = { tag: 'c:ingots/draconium' }

  fusion('draconicevolution:machines/reactor_core', {
    result: { id: 'draconicevolution:reactor_core', count: 1 },
    catalyst: { item: 'mekanism:pellet_antimatter' },
    totalEnergy: 64000000,
    techLevel: 'draconic',
    ingredients: [AWAKENED, DRACONIUM, AWAKENED, DRACONIUM, AWAKENED, DRACONIUM, AWAKENED,
      { item: 'evolvedmekanism:alloy_subatomic' }, { item: 'evolvedmekanism:alloy_subatomic' }].map(consume)
  })
  fusion('draconicevolution:machines/reactor_stabilizer', {
    result: { id: 'draconicevolution:reactor_stabilizer', count: 1 },
    catalyst: { item: 'draconicevolution:reactor_prt_stab_frame' },
    totalEnergy: 16000000,
    techLevel: 'draconic',
    ingredients: [{ item: 'draconicevolution:draconic_energy_core' }, { item: 'draconicevolution:reactor_prt_rotor_full' },
      { item: 'draconicevolution:reactor_prt_focus_ring' }, AWAKENED, AWAKENED, { item: 'draconicevolution:awakened_core' },
      AWAKENED, { item: 'mekanism:pellet_antimatter' }].map(consume)
  })
  const ROTOR = { item: 'draconicevolution:reactor_prt_in_rotor' }
  fusion('draconicevolution:machines/reactor_injector', {
    result: { id: 'draconicevolution:reactor_injector', count: 1 },
    catalyst: { item: 'draconicevolution:wyvern_core' },
    totalEnergy: 16000000,
    techLevel: 'draconic',
    ingredients: [DRACONIUM, ROTOR, ROTOR, ROTOR, DRACONIUM, DRACONIUM, { tag: 'c:ingots/iron' }, ROTOR,
      { tag: 'c:ingots/iron' }, DRACONIUM].map(consume)
  })
  const DIAMOND = { tag: 'c:gems/diamond' }
  const MEDIUM = { item: 'draconicevolution:medium_chaos_frag' }
  fusion('draconicevolution:machines/chaotic_crafting_injector', {
    result: { id: 'draconicevolution:chaotic_crafting_injector', count: 1 },
    catalyst: { item: 'draconicevolution:awakened_crafting_injector' },
    totalEnergy: 8000000,
    techLevel: 'draconic',
    ingredients: [DIAMOND, DIAMOND, { item: 'draconicevolution:large_chaos_frag' }, MEDIUM, MEDIUM, MEDIUM,
      { item: 'mekanism:pellet_antimatter' }, DIAMOND, DIAMOND].map(consume)
  })
})
