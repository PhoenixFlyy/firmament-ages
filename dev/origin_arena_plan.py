"""Firmament Ages - the grand arena of The Origin as a block plan (round G4, SPEC section 16.5).

Mirror of mod/firmages-core/src/main/java/dev/firmages/core/origin/GrandArenaPlan.java: the same integer geometry and
the same material keys, so dev/gen_ark.py writes dev/data/origin_arena.json with the counts that GrandArenaPlanTest and
the GameTest ArenaGameTests.grandArenaMatchesThePlan compare against. dev/gen_arena_preview.py draws it.

Keys: "<role>_<accent>" is a block of the consecrated family (role stone, brick, pillar, lamp, metal, glass, trim; accent
the shrine Age 0..8), "rock_0".."rock_4" the mixed rock of the undersides, "dais" the crying-obsidian dais,
"lantern" a sea lantern, "arena_altar" a fight altar. Every walkable block is consecrated (unbreakable).

Frame: the Gathering altar stays at (0, 65, 0) on the sink floor (y 64). Sink (radius 14, y 64), step ring (15, y 65),
fighting ring (16..35, y 66), three Age terraces (36..41 y 67, 42..47 y 68, 48..53 y 69), rim (54..60, y 69) with a
parapet and a wreath of 17 pillars, four altar podiums 16 blocks out (altar at y 69), six open domes on the ring,
two trenches with bridges on the diagonals; the arrival platform 35 blocks below in the south (y 34) with the return
gate, and the winding ascent (three flights, two bridges, nine vitrines) up to the south gate of the rim.
"""
import math

RADIUS = 60
FLOOR_Y = 64                 # the sink floor; the Gathering altar stands at y 65
RING_Y = FLOOR_Y + 3         # 67
RIM_Y = FLOOR_Y + 7          # 71
ALTAR_Y = RING_Y + 3         # 70, on a podium
PLATFORM_Y = FLOOR_Y - 30    # 34
PLATFORM_HALF, PLATFORM_Z0, PLATFORM_Z1 = 10, 78, 98
SINK_R, RING_R = 13, 35      # steps at 14 and 15
TRENCH_R0, TRENCH_R1 = 20, 22
ALTAR_DISTANCE = 16
ALTARS = [(0, -ALTAR_DISTANCE), (ALTAR_DISTANCE, 0), (0, ALTAR_DISTANCE), (-ALTAR_DISTANCE, 0)]
ALTAR_AGES = [0, 3, 6, 8]
# six domes at distance 29, every 60 degrees from 30 degrees on (x = round(29 cos a), z = round(29 sin a))
DOMES = [(25, 14), (0, 29), (-25, 14), (-25, -14), (0, -29), (25, -14)]
DOME_AGES = [1, 2, 4, 5, 7, 3]
DOME_R2 = 12                 # wall ring: 7 <= dx*dx + dz*dz <= 12
# the pillar wreath at distance 56, every 20 degrees, the south pillar (90 degrees) left out for the gate
PILLARS = [(56, 0), (53, 19), (43, 36), (28, 48), (10, 55), (-10, 55), (-28, 48), (-43, 36), (-53, 19), (-56, 0),
           (-53, -19), (-43, -36), (-28, -48), (-10, -55), (10, -55), (28, -48), (43, -36), (53, -19)]
PILLAR_HEIGHT = 5
# four towers on the diagonals of the rim (3 by 3, eight high, a trim cap and a lamp)
TOWERS = [(40, 40), (-40, 40), (-40, -40), (40, -40)]
TOWER_HEIGHT = 8
ARCH_Z, ARCH_HALF, ARCH_HEIGHT = 58, 4, 8
GATE_HALF = 1                # the parapet's cut and the approach: x -1..1

# The ascent: segments (direction, start x, start z, length, accent, climbing, bridge). A segment's cell j lies at
# start + j * direction with the walkway at y0 + j // 2 when climbing (y0 flat on a bridge); y0 is the y the previous
# segment ended on plus one when climbing. Three cells wide (perpendicular -1..1), an edge row at +-2 with a rail.
# Directions: W = (-1, 0), E = (1, 0), N = (0, -1).
ASCENT = [
    ("W", -1, 76, 16, 0, True, False),    # flight 1a west, y 35..42
    ("W", -17, 76, 6, 0, False, True),    # bridge 1 over the void, y 42
    ("W", -23, 76, 16, 1, True, False),   # flight 1b west, y 43..50
    ("N", -40, 77, 10, 2, True, False),   # flight 2 north, y 51..55
    ("E", -39, 66, 16, 3, True, False),   # flight 3a east, y 56..63
    ("E", -23, 66, 6, 4, False, True),    # bridge 2, y 63
    ("E", -17, 66, 16, 5, True, False),   # flight 3b east, y 64..71
    ("N", 0, 67, 7, 6, False, False),     # the approach to the south gate, y 71
]
DIRS = {"W": (-1, 0), "E": (1, 0), "N": (0, -1)}
# vitrine i: (segment, cell j, side): on the edge row of a flight, the glass cell one above the walkway
VITRINES = [(0, 3, 1), (0, 11, 1), (2, 3, 1), (2, 11, 1), (3, 3, -1), (3, 7, -1), (4, 5, 1), (6, 4, 1), (6, 11, 1)]


def isqrt(n):
    return math.isqrt(n)


def in_disc(x, z):
    return x * x + z * z <= RADIUS * RADIUS + RADIUS


def rock(x, y, z):
    h = (x * 31 + y * 17 + z * 13) % 97
    return "rock_%d" % (0 if h < 50 else 1 if h < 70 else 2 if h < 85 else 3 if h < 93 else 4)


def wedge(x, z):
    """True on the north-east and south-west diagonal wedges (the trenches)."""
    if not ((x > 0 and z < 0) or (x < 0 and z > 0)):
        return False
    ax, az = abs(x), abs(z)
    return 2 * ax >= az and 2 * az >= ax


def band_age(k):
    """The shrine Age of terrace ring k (36..53): Age 8 innermost, Age 0 outermost."""
    return 8 - (k - 36) // 2


def top_y(x, z):
    """The walkable height of disc column (x, z): the y of its top block."""
    k = isqrt(x * x + z * z)
    if k <= SINK_R:
        return FLOOR_Y
    if k < 16:
        return FLOOR_Y + (k - SINK_R)
    if k <= RING_R:
        if TRENCH_R0 <= k <= TRENCH_R1 and wedge(x, z) and abs(abs(x) - abs(z)) > 1:
            return FLOOR_Y
        return RING_Y
    if k <= 41:
        return RING_Y + 1
    if k <= 47:
        return RING_Y + 2
    if k <= 53:
        return RING_Y + 3
    return RIM_Y


def top_key(x, z):
    d2 = x * x + z * z
    k = isqrt(d2)
    if d2 <= 5:
        return "dais"
    spoke = x == 0 or z == 0 or abs(x) == abs(z)
    if k <= SINK_R:
        if k in (4, 12):
            return "lantern" if k == 12 and spoke else "metal_8"
        return "brick_8"
    if k < 16:
        return "stone_8"
    if k <= RING_R:
        if TRENCH_R0 <= k <= TRENCH_R1 and wedge(x, z):
            return "trim_8" if abs(abs(x) - abs(z)) <= 1 else "brick_8"
        if k == 24:
            return "metal_8"
        if k == 30 and spoke and not in_dome(x, z):
            return "lantern"
        return "stone_8"
    if k <= 53:
        return ("trim_%d" if (k - 36) % 2 == 0 else "stone_%d") % band_age(k)
    return "brick_8"


def in_dome(x, z):
    for cx, cz in DOMES:
        if (x - cx) ** 2 + (z - cz) ** 2 <= DOME_R2:
            return True
    return False


def ascent_cells():
    """The ascent's walkway and edge cells: {(x, z): (y, accent, bridge, edge)} in segment order."""
    out = {}
    for d, sx, sz, n, accent, climb, bridge in ASCENT:
        dx, dz = DIRS[d]
        px, pz = -dz, dx  # perpendicular
        for j in range(n):
            x, z = sx + j * dx, sz + j * dz
            y = ascent_y(d, sx, sz, n, climb, j)
            for s in (-2, -1, 0, 1, 2):
                c = (x + s * px, z + s * pz)
                edge = abs(s) == 2
                if edge and (c in out and not out[c][3] or on_platform(c[0], c[1])):
                    continue  # a walkway or the platform keeps its cell
                out[c] = (y, accent, bridge, edge)
    return out


def on_platform(x, z):
    return abs(x) <= PLATFORM_HALF and PLATFORM_Z0 <= z <= PLATFORM_Z1


def ascent_y(d, sx, sz, n, climb, j):
    """The walkway y of cell j of a segment: segments are listed in order, each starting on the y the last one reached."""
    y = PLATFORM_Y
    for dd, ssx, ssz, nn, _a, cc, _b in ASCENT:
        if cc:
            y0 = y + 1
            y_end = y0 + (nn - 1) // 2
        else:
            y0 = y
            y_end = y
        if (dd, ssx, ssz) == (d, sx, sz):
            return y0 + (j // 2 if cc else 0)
        y = y_end
    raise KeyError((d, sx, sz))


def segment_cell(seg, j, side):
    d, sx, sz, n, accent, climb, bridge = ASCENT[seg]
    dx, dz = DIRS[d]
    px, pz = -dz, dx
    return sx + j * dx + side * 2 * px, sz + j * dz + side * 2 * pz, ascent_y(d, sx, sz, n, climb, j), accent


def vitrine(i):
    """(x, y, z) of the glass cell of vitrine i (its pillar stands one below, on the ascent's edge row)."""
    seg, j, side = VITRINES[i]
    x, z, y, _accent = segment_cell(seg, j, side)
    return x, y + 1, z


def altar_pos(a):
    return ALTARS[a][0], ALTAR_Y, ALTARS[a][1]


def arrival():
    return 0, PLATFORM_Y + 1, 81


def gate_origin():
    return -3, PLATFORM_Y, 84


def cells():
    """{(x, y, z): key}, later entries overriding earlier ones (GrandArenaPlan.cells)."""
    m = {}
    way = ascent_cells()
    # 1. the disc: top, fill under it, the rock cone below the sink level
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            if not in_disc(x, z):
                continue
            t = top_y(x, z)
            m[(x, t, z)] = top_key(x, z)
            for y in range(FLOOR_Y, t):
                m[(x, y, z)] = "stone_8"
            k = isqrt(x * x + z * z)
            depth = 3 + (RADIUS - k) * 15 // RADIUS + (x * 31 + z * 17) % 3
            for dy in range(1, depth + 1):
                m[(x, FLOOR_Y - dy, z)] = rock(x, FLOOR_Y - dy, z)
    # 2. the parapet on the rim, cut at the south gate
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            if not in_disc(x, z) or isqrt(x * x + z * z) < 59:
                continue
            if z > 0 and abs(x) <= GATE_HALF:
                continue
            m[(x, RIM_Y + 1, z)] = "brick_8"
            if isqrt(x * x + z * z) == RADIUS and (x + z) % 2 == 0:
                m[(x, RIM_Y + 2, z)] = "brick_8"  # merlons
    # 3. the pillar wreath
    for i, (px, pz) in enumerate(PILLARS):
        a = i % 9
        for dy in range(1, PILLAR_HEIGHT + 1):
            m[(px, RIM_Y + dy, pz)] = "pillar_%d" % a
        m[(px, RIM_Y + PILLAR_HEIGHT + 1, pz)] = "lamp_%d" % a
    # 3b. the four towers and the lamp posts on the platform corners
    for tx, tz in TOWERS:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for dy in range(1, TOWER_HEIGHT + 1):
                    m[(tx + dx, RIM_Y + dy, tz + dz)] = "pillar_8"
                m[(tx + dx, RIM_Y + TOWER_HEIGHT + 1, tz + dz)] = "trim_8"
        m[(tx, RIM_Y + TOWER_HEIGHT + 2, tz)] = "lamp_8"
    # 4. the four altar podiums
    for (ax, az), age in zip(ALTARS, ALTAR_AGES):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                m[(ax + dx, RING_Y + 1, az + dz)] = "brick_%d" % age
                if abs(dx) <= 1 and abs(dz) <= 1:
                    m[(ax + dx, RING_Y + 2, az + dz)] = "brick_%d" % age
        m[(ax, ALTAR_Y, az)] = "arena_altar"
    # 5. six open domes: a two-high wall ring with four doorways and slits, a cap ring with an oculus
    for (cx, cz), age in zip(DOMES, DOME_AGES):
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                d2 = dx * dx + dz * dz
                if 7 <= d2 <= DOME_R2:
                    if dx != 0 and dz != 0:
                        m[(cx + dx, RING_Y + 1, cz + dz)] = "brick_%d" % age
                    if (dx + dz) % 2 == 0:
                        m[(cx + dx, RING_Y + 2, cz + dz)] = "brick_%d" % age
                if 1 <= d2 <= 8:
                    m[(cx + dx, RING_Y + 3, cz + dz)] = "trim_%d" % age
    # 6. the arch over the south gate
    for y in range(RIM_Y + 1, RIM_Y + ARCH_HEIGHT):
        m[(-ARCH_HALF, y, ARCH_Z)] = "pillar_8"
        m[(ARCH_HALF, y, ARCH_Z)] = "pillar_8"
    for x in range(-ARCH_HALF, ARCH_HALF + 1):
        m[(x, RIM_Y + ARCH_HEIGHT, ARCH_Z)] = "lamp_8" if x == 0 else "trim_8"
    # 7. the arrival platform with its rim and rock below
    for x in range(-PLATFORM_HALF, PLATFORM_HALF + 1):
        for z in range(PLATFORM_Z0, PLATFORM_Z1 + 1):
            m[(x, PLATFORM_Y, z)] = "brick_8"
            depth = 2 + min(PLATFORM_HALF - abs(x), min(z - PLATFORM_Z0, PLATFORM_Z1 - z)) // 2
            for dy in range(1, depth + 1):
                m[(x, PLATFORM_Y - dy, z)] = rock(x, PLATFORM_Y - dy, z)
            edge = abs(x) == PLATFORM_HALF or z == PLATFORM_Z1 or z == PLATFORM_Z0
            if edge and not (z == PLATFORM_Z0 and -2 <= x <= 0):
                m[(x, PLATFORM_Y + 1, z)] = "stone_8"
            if abs(x) == PLATFORM_HALF and z in (PLATFORM_Z0, PLATFORM_Z1):
                m[(x, PLATFORM_Y + 2, z)] = "pillar_8"
                m[(x, PLATFORM_Y + 3, z)] = "pillar_8"
                m[(x, PLATFORM_Y + 4, z)] = "lamp_8"
    # 8. the ascent: walkway, edge rows with rails, the core and rock under the flights (bridges float)
    for (x, z), (y, accent, bridge, edge) in way.items():
        if edge:
            m[(x, y, z)] = "stone_%d" % accent
            m[(x, y + 1, z)] = "trim_%d" % accent
        else:
            m[(x, y, z)] = ("trim_%d" if bridge else "brick_%d") % accent
        if not bridge:
            m[(x, y - 1, z)] = "stone_8"
            for dy in range(2, 5 if not edge else 3):
                m[(x, y - dy, z)] = rock(x, y - dy, z)
    # 9. the nine vitrines on the edge rows (the rail there gives way to the pillar)
    for i in range(9):
        x, y, z = vitrine(i)
        m[(x, y - 1, z)] = "pillar_%d" % i
        m[(x, y, z)] = "glass_%d" % i
    return m


def counts():
    c = {}
    for k in cells().values():
        c[k] = c.get(k, 0) + 1
    return dict(sorted(c.items()))


def solid(key):
    return key != "lantern" or True  # every key of the plan is a full block


def walkable_path(m, start, goal):
    """Breadth-first walk from start (x, y, z) to any cell goal(x, y, z) -> bool: four neighbours, a step of at most one
    up or down, a block below, two air above. Returns the path length or -1."""
    from collections import deque
    seen = {start}
    q = deque([(start, 0)])
    while q:
        (x, y, z), n = q.popleft()
        if goal(x, y, z):
            return n
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1):
                p = (x + dx, y + dy, z + dz)
                if p in seen:
                    continue
                if (p[0], p[1] - 1, p[2]) in m and (p[0], p[1], p[2]) not in m and (p[0], p[1] + 1, p[2]) not in m:
                    seen.add(p)
                    q.append((p, n + 1))
    return -1


def ascent_ok(m=None):
    m = m or cells()
    goal = lambda x, y, z: y == RIM_Y + 1 and in_disc(x, z) and isqrt(x * x + z * z) <= RADIUS
    return walkable_path(m, arrival(), goal)


if __name__ == "__main__":
    m = cells()
    print("cells", len(m))
    for k, v in counts().items():
        print("  %-12s %d" % (k, v))
    for seg in ASCENT:
        print(seg, "y", ascent_y(seg[0], seg[1], seg[2], seg[3], seg[5], 0), "..", ascent_y(seg[0], seg[1], seg[2], seg[3], seg[5], seg[3] - 1))
    print("ascent path length", ascent_ok(m))
    for i in range(9):
        print("vitrine", i, vitrine(i))
