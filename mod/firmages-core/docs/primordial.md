# The Primordial

The end boss of Firmament Ages: the dark before the first light, the shrine inverted. Where the shrine is white
marble, gold trim and a beam going up, The Primordial is dark glass with gold edges, a dark needle (the spire) above,
a keel below, a cage of four veil plates around the keel and a tilted halo of nine shards, one per Age 0..8, each with
a node in that Age's ceremony beam colour. It never speaks; Caelum does, twice.

One entity (`firmages:primordial`, package `dev.firmages.core.boss`), no adds, no waves. It carries the scoreboard tag
`firmages.final_boss` from birth, so its death in The Origin wins the finale through the existing `OriginService` path
(`finale_won` plus the FINALE ceremony). The core summons it itself (`BossEvents`, no pack script): on the Gathering
(party size = the players at the altar) and for `/firmages origin fight` (party size = the players near it when it
wakes), 2.5 blocks above the altar.

Concept and research: `Modpack-Planung/research-raw/n7-boss.md`.

## Phases

| Phase | Health | Title | Attacks | Opens the veil (the damage window) |
|---|---|---|---|---|
| 1 | 100 to 67 % | (wake: "The Primordial", "The dark before the first light") | **Keel slam** every 12 s: it glides over the nearest player and rises half a block for 1.1 s, drops in 0.4 s, ring shock radius 5 (18 damage, knockback), the keel rests on the floor 2 s, rises 1.5 s. **Node volley** every 8 s: one node glows white for 1 s, then three bolts at the nearest players (10 damage, 2 s Darkness). | Melee hit on the keel while it rests on the floor: veil open 6 s. Ranged hit on the charging node (before the bolts leave): volley cancelled, veil open 4 s. |
| 2 | 67 to 33 % | Nine Lights; Caelum: "Nine lights. Keep one burning." | On entry the arena's sea lanterns go out (polished blackstone until the fight ends), nothing spawns near it. **Beam sweep** every 15 s: one node washes in its Age colour 1 s, then its ceremony beam burns from the node to the floor (9 blocks out) and sweeps 180 degrees in 6 s, 8 damage every half second plus Darkness. **The pull** every 20 s: every player within 24 blocks is drawn under the core for 2 s, then a keel slam. | Melee hit from below (within 2.5 blocks of its axis) during the pull: the slam never lands, veil open 6 s. A melee hit on the grounded keel after the slam: veil open 6 s. Ranged hit on the burning node: the beam stops, veil open 4 s. |
| 3 | 33 to 0 % | Firmament Shatter; Caelum: "The Firmament breaks. Gather what falls." | The core splits, the eye lights, the halo scatters. **Star-fall** every 2 s near a random player (gold mark on the floor 1.5 s ahead, 14 damage, radius 2). **Firmament shatter** every 25 s: it rises 3 blocks (untouchable for those 3 s), nine stars fall at once (the eight spoke marks and the nearest player), six **consecrated shards** drop on the floor. | A melee hit with a consecrated shard in the off hand (spends it): veil open 8 s. A thrown shard (sneak + use) that hits it: veil open 8 s. Use (no sneak): 10 s of Firmament Ward, star-fall passes you. |

Entering phase 2 or 3 locks it for 1 s or 2 s (no damage, no attacks). A single hit never skips a phase: the damage of
a hit stops at the phase floor. Death: the die animation (3 s), the halo collapses into the core, a last star rises;
its star-falls vanish, the lanterns burn again. The loot table is empty: the win is `finale_won` (the trophies).

Soft enrage: after `enrageMinutes` (10) the veil stays open half as long; after `starfallEnrageMinutes` (12) star-fall
rains in every phase, every second.

Wipe: with no living player within 48 blocks of the altar for `wipeSeconds` (30), it withdraws: the lanterns return,
its star-falls go. The next Gathering (after `origin.gatherCooldownSeconds`) summons it anew.

## Numbers

| What | Value | Where |
|---|---|---|
| Health | 1024 (the attribute cap) | `PrimordialEntity.MAX_HEALTH` |
| Armour / toughness | 12 / 8 | `createAttributes` |
| Effective pool | `effectiveHealth * ((1 - f) + f * players)`: 2400 (1), 3480 (2), 4560 (3), 5640 (4) | `[boss] effectiveHealth`, `perPlayerFactor` |
| Damage taken | after armour, times `1024 / pool`, times `veilFactor` (0.25) while the veil is closed | `scaleDamage`, `BossEvents` (`LivingDamageEvent.Pre`) |
| Hurt cooldown | 10 ticks per attacker (each player's hits count, not two hits a second for everyone) | `IFRAMES` |
| Hover | 2.5 blocks above the floor (the hitbox starts there; melee reaches the keel and the underside) | `PrimordialGeometry.HOVER` |
| Hitbox | 6 x 7 blocks | `BossRegistry.PRIMORDIAL` |
| Leash | 24 blocks horizontal or 12 vertical from home: back at once | `LEASH`, `leash()` |
| Slam | 18 damage, radius 5, impact at 1.5 s, keel rests 2 s | `SLAM_*` |
| Volley | 3 bolts, 10 damage, 2 s Darkness | `VOLLEY_*` |
| Beam | 8 damage per 0.5 s, 180 degrees in 6 s, floor point 9 blocks out | `BEAM_*`, `BEAM_RADIUS` |
| Pull | 24 blocks, up to 0.5 blocks per tick, 2 s | `PULL_*` |
| Star-fall | 14 damage, radius 2, every 2 s (phase 3) | `STAR_DAMAGE`, `stepStarfall` |
| Shatter | 9 stars, 6 shards (60 s on the floor), every 25 s | `SHATTER_PEAK`, `SHARDS_PER_SHATTER`, `SHARD_LIFESPAN` |
| Veil windows | melee 6 s, ranged 4 s, shard 8 s | `VEIL_MELEE`, `VEIL_RANGED`, `VEIL_SHARD` |
| Cooldowns | slam 12 s, volley 8 s, beam 15 s, pull 20 s, shatter 25 s; 1.5 s between two attacks | `COOLDOWN`, `GAP` |

Expected length (first estimate, before tuning): a Chaotic sword (17.5 per hit, about 12.2 after armour, at most two
hits a second that count per player) deals about 24 per second to the pool with the veil open and 6 with it closed.
With the veil open a third of the time that is about 12 per second per player: one player needs about 200 s of hitting
(about 6 minutes with dodging and the phase locks), two players about 145 s each, four players about 120 s each. The
target is 6 to 10 minutes; if the larger groups finish too fast, raise `perPlayerFactor` (0.6: four players 6720).

## Tuning knobs

Per world, without a rebuild (`serverconfig/firmages-server.toml`, section `[boss]`):

- `effectiveHealth` (2400): the fight's length for one player.
- `perPlayerFactor` (0.45): how much each further player adds.
- `veilFactor` (0.25): how much the closed veil protects; 1 makes the counters pointless, 0 makes them mandatory.
- `enrageMinutes` (10), `starfallEnrageMinutes` (12): when the soft enrage starts.
- `wipeSeconds` (30): how long it waits for players before it withdraws.

In code (`PrimordialEntity` constants, a rebuild): damage per attack, cooldowns, veil windows, the slam and pull
timelines (each must stay in step with its animation in `dev/gen_primordial.py`: `PrimordialAbility.length` equals the
animation length in ticks).

Open tuning, to be measured on the test server with 1, 2 and 4 players in Chaotic gear: the fight length, the slam's
2 s keel window for melee players, whether the beam is readable from the rim, and the player damage numbers against
TFC's damage resistance.

## Assets

All generated by `python dev/gen_primordial.py` (`--check` verifies the files match, `--preview OUT.png` renders the
three phases with a small projector through the GeckoLib transform chain):

- `geo/entity/primordial.geo.json`: 28 bones, 30 cubes, per-face UVs on a 128 x 128 atlas. The core is two halves
  (`core_a`, `core_b`) so phase 3 can split it; each veil plate hangs on a hinge bone; the halo has a tilt bone
  (`halo`) and a spin bone (`halo_spin`) that `PrimordialModel` sets in code from `PrimordialGeometry`, the closed form
  the server uses for node hits (the generator checks both agree to 1e-6 blocks).
- `animations/entity/primordial.animation.json`: idle, hurt, veil_open, veil_close, keel_slam, volley_charge,
  beam_sweep, pull, split, shatter, die.
- `textures/entity/primordial.png` (dark glass, gold edges, void, nine Age nodes; palette of `dev/gen_textures.py`),
  `primordial_glowmask.png` (nodes, gold edges, void specks), `primordial_glowmask_eye.png` (the eye, drawn from
  phase 3 on), `primordial_flare.png` (node flare and star-fall), `item/consecrated_shard.png`,
  `block/firmament_glass.png`, `mob_effect/firmament_ward.png`.

Client: `PrimordialRenderer` (GeckoLib, translucent), two emissive layers, a flare on the lit node, the beam via
`BeaconRenderer.renderBeaconBeam` from the node bone as rendered; `StarFallRenderer`; the thrown shard uses the item
renderer. Sounds are vanilla events (`sounds.json`, `primordial.*`).

Server sync: phase, ability, veil, lit node and the beam's angle are synched entity data; the poses follow from them,
so a player who joins mid-fight sees the right state.

## Items and blocks

- **Consecrated Shard** (`firmages:consecrated_shard`, stacks to 16, epic): only from the shatter. Use: Firmament Ward
  10 s. Sneak + use: throw. Off hand: a melee hit opens the veil. Shards that stay are keepsakes.
- **Firmament Glass** (`firmages:firmament_glass`): cosmetic, one shard and four glass give four.
- **Firmament Ward** (`firmages:firmament_ward`): star-fall passes the bearer.

## Tests

GameTests `BossGameTests`: phase thresholds and the lantern swap (and relight on death), veil and keel window,
per-attacker damage and the effective pool, the shatter's six shards and nine stars, death grants `finale_won`
through the tag, a fight summoned by `/firmages origin fight` and won by scaled player hits
(`summonedFightIsWonByScaledDamage`), return home, nothing stays behind. `OriginGameTests`: the Gathering and
`origin fight` wake it, no second one while it lives. Unit: `OriginSpawnListTest` (endermen only in The Origin).
