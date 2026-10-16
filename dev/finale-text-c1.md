# Finale text pieces (round C1, boss)

Player-facing English text for the integrator: quests, walkthrough and the server README. Everything here matches
firmages-core on branch `c1/boss` (The Primordial, `docs/primordial.md`; numbers at the `[boss]` defaults). The
goal quest, the bridge quest and the WALKTHROUGH finale on this branch already use these texts; the pieces marked
"new" are not in the pack yet.

## 1. Quests (`dev/gen_quests.py`, chapter Singularity)

**Goal `singularity/goal` "Beyond the Firmament"** (task: stage `finale_won`, "Defeat the final boss in The Origin";
in the branch):

> Dial The Origin and step through the gate. Gather at the altar, all of you, and The Primordial wakes. Its veil
> opens only when you answer its attacks.
>
> Its fall completes the Firmament. The MekaSuit and the Meka-Tool can then be made.

**Bridge `singularity/side/origin` "Into The Origin"** (unchanged, still correct):

> Step through the gate. Gather, all of you, at the central altar to call the final battle.
> A gate on the arena dials home.

**New, optional explanation quest "The Primordial"** (kind explanation, after "Into The Origin", no task or a
checkmark; text only):

> It never speaks. Its veil swallows most of your blows; answer what it does and the veil opens.
>
> The Dark Before: strike the keel while it rests on the floor after a slam, or shoot the node that glows white
> before its bolts fly.
>
> Nine Lights, from two thirds: the lanterns go out. Shoot the node whose beam sweeps the floor, or strike up at
> the core while it pulls you in.
>
> Firmament Shatter, from one third: stars fall where the gold marks glow. Pick up the shards it sheds. Use one
> and the stars pass you; hold one in the off hand and strike; or sneak and throw one at it.

**New, optional side quest "A Piece of the Sky"** (task: item `firmages:consecrated_shard`, kind side, after the
goal or parallel to it):

> The shards it sheds in the Firmament Shatter are the only ones there will ever be. Keep one. Four glass and a
> shard make four Firmament Glass.

(Quest rewards: real items, per Felix's note for a later round. Fitting ones: Firmament Glass for the side quest;
nothing for the goal, the finale ceremony is the reward.)

## 2. WALKTHROUGH (`docs/WALKTHROUGH.md`, section Finale; in the branch)

Ages table, row Finale, column "Goal": "Defeat The Primordial, the final boss in The Origin".

What you must reach: "Defeat The Primordial, the final boss in The Origin. Its fall grants `finale_won` to all of
you."

The way:

1. You step out of the gate south of a floating arena and walk north past the DHD. Nine islands float around it,
   one per Age, each with its relic turning above it.
2. Everyone online stands within 6 blocks of the altar in the middle. The altar answers.
3. The Primordial wakes above the altar: dark glass, a keel below, a halo of nine lights. Its veil takes most of
   your damage; it opens only when you answer an attack.
   - The Dark Before: strike the keel while it rests on the floor after a slam, or shoot the node that glows white.
   - Nine Lights (from two thirds): the lanterns go out. Shoot the node whose beam sweeps the floor, or strike the
     core from below while it pulls you in.
   - Firmament Shatter (from one third): stars fall. Pick up the shards it sheds: use one to let the stars pass
     you, hold one in the off hand and strike, or throw one at it (sneak and use) to open the veil.
4. Expect six to ten minutes. After ten minutes the veil opens for less time, after twelve the stars fall
   everywhere.
5. It falls: "Beyond the Firmament". The MekaSuit and the Meka-Tool can now be made. The shrine's beam turns gold.
6. The gate at the arena dials home.

Pitfalls:

- Every online player who is not a spectator must stand at the altar. One friend elsewhere and nothing starts.
  A player who has not moved or acted for five minutes is not counted.
- After a wipe it withdraws once nobody is near it for 30 s, and the lanterns return. Gather again after 30 s.
- The altar cannot be broken, not even by the boss.
- Nothing else spawns in the arena while The Primordial lives.
- Monsters spawn in The Origin only in darkness; the arena is lit. Beds and respawn anchors do not work there.
- The victory counts once. A friend who logs in later receives it silently.

## 3. What the player sees, step by step (for quest art, the client brief and Felix's client test)

| Moment | Seen / heard | What the player must do |
|---|---|---|
| Whole team at the altar | Chat "The altar answers: all of you stand before it. Something wakes in the dark." | Nothing; stay near. |
| Wake | Title "The Primordial", subtitle "The dark before the first light"; boss bar; it rises to hover 2.5 blocks above the altar | Spread out around the altar. |
| Phase 1 keel slam (every 12 s) | It glides over the nearest player, rises half a block, drops; ring shock radius 5 (18 damage) | Step out of the ring, then strike the keel while it rests on the floor (2 s): veil open 6 s. |
| Phase 1 node volley (every 8 s) | One node glows white for 1 s, then three bolts (10 damage, Darkness 2 s) | Shoot the white node before the bolts leave: volley cancelled, veil open 4 s. |
| Phase 2 entry (two thirds) | Title "Nine Lights"; Caelum: "Nine lights. Keep one burning."; all sea lanterns go out | Bring light of your own. |
| Phase 2 beam sweep (every 15 s) | A node washes in its Age colour, then a beacon beam from it to the floor sweeps half a circle in 6 s (8 damage per half second) | Shoot the burning node: beam stops, veil open 4 s. |
| Phase 2 pull (every 20 s) | Everyone within 24 blocks is drawn under the core for 2 s, then a slam | Strike up at it from below during the pull: no slam, veil open 6 s; or hit the grounded keel after the slam. |
| Phase 3 entry (one third) | Title "Firmament Shatter"; Caelum: "The Firmament breaks. Gather what falls."; the core splits, the eye lights | Watch the floor. |
| Phase 3 star-fall (every 2 s) | Gold mark on the floor 1.5 s ahead, then a star (14 damage, radius 2) | Leave the mark, or use a shard (Firmament Ward 10 s). |
| Phase 3 shatter (every 25 s) | It rises 3 blocks and cannot be hurt for 3 s, nine stars fall at once, six Consecrated Shards drop | Pick up shards. Off-hand shard + melee hit, or a thrown shard (sneak + use): veil open 8 s. |
| Death | Die animation 3 s, the halo collapses, a last star rises; lanterns return | Title "Beyond the Firmament", Caelum: "The first light is yours now. No sky stands above you any more." |

## 4. Phase answers in one line each (tooltip or Chronicle sidebar size)

- The Dark Before: hit the keel on the floor, or shoot the white node.
- Nine Lights: shoot the burning node, or strike up while it pulls.
- Firmament Shatter: shard in the off hand, or throw one; use one to let the stars pass.

## 5. Server README (`README-SERVERPACK.md`, operator commands)

- `/firmages origin fight`: summons The Primordial above the altar at once (the Gathering skipped). Refused while
  it lives, while nobody is in The Origin to keep the altar loaded, and once the finale is won.
- `/firmages origin kill boss`: kills it; the finale is won the normal way.
- `[boss]` in `serverconfig/firmages-server.toml`: `effectiveHealth` (13000, one player), `perPlayerFactor` (0.7),
  `veilFactor` (0.25), `hitCap` (40), `enrageMinutes` (10), `starfallEnrageMinutes` (12), `wipeSeconds` (30) (round C2,
  the fight model of SPEC 39).
