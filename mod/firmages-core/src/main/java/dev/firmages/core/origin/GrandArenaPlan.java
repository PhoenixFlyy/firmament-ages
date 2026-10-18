package dev.firmages.core.origin;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongPredicate;

/**
 * The grand arena of The Origin (rounds G3 and G4, SPEC §16.5) as a pure block plan: every cell with a material key, no
 * Minecraft types, so a JUnit test, the GameTest and {@code dev/origin_arena_plan.py} (its mirror; {@code dev/gen_ark.py}
 * writes {@code dev/data/origin_arena.json} from it) agree on the same counts. {@link OriginArena#grandState} maps the
 * keys to block states.
 *
 * <p>Keys: {@code <role>_<accent>} is a block of the consecrated family (role stone, brick, pillar, lamp, metal, glass or
 * trim; accent the shrine Age 0..8), {@code rock_0..4} the mixed rock of the undersides, {@code dais} the crying-obsidian
 * dais, {@code lantern} a sea lantern, {@code arena_altar} a fight altar. Every walkable block is consecrated (unbreakable).
 *
 * <p>Frame (round G4): the Gathering altar stays at (0, 65, 0) on the sink floor (y 64). Sink (radius 13), two steps (14,
 * 15), the fighting ring (16..35, y 67) with two trenches on the diagonals and bridges over them, three Age terraces
 * (36..41 y 68, 42..47 y 69, 48..53 y 70; Age 8 innermost, Age 0 outermost), the rim (54..60, y 71) with a parapet,
 * merlons, a wreath of 18 pillars and four towers on the diagonals; four altar podiums 16 blocks out (altar at y 70),
 * six open domes on the ring (walls with doorways and slits, a cap with an oculus, 3 high); the arrival platform
 * 37 blocks below in the south (y 34) with the return gate, and the winding ascent (three flights climbing one block per
 * two, two bridges over the void, nine vitrines on the edge rows) up to the arch over the south gate of the rim. All
 * arithmetic is integer, so Python and Java match.
 */
public final class GrandArenaPlan {
    public static final int RADIUS = 60;
    /** The sink floor; the Gathering altar stands one above it. */
    public static final int FLOOR_Y = 64;
    public static final int RING_Y = FLOOR_Y + 3;
    public static final int RIM_Y = FLOOR_Y + 7;
    /** The fight altars stand on their podiums at this height. */
    public static final int ALTAR_Y = RING_Y + 3;
    public static final int PLATFORM_Y = FLOOR_Y - 30;
    /** Platform footprint (x from -PLATFORM_HALF to +PLATFORM_HALF, z from PLATFORM_Z0 to PLATFORM_Z1). */
    public static final int PLATFORM_HALF = 10, PLATFORM_Z0 = 78, PLATFORM_Z1 = 98;
    public static final int SINK_R = 13, RING_R = 35;
    public static final int TRENCH_R0 = 20, TRENCH_R1 = 22;
    /** Altars: distance from the centre and the Age accent of each podium, in the order north, east, south, west. */
    public static final int ALTAR_DISTANCE = 16;
    public static final int[][] ALTARS = {{0, -ALTAR_DISTANCE}, {ALTAR_DISTANCE, 0}, {0, ALTAR_DISTANCE}, {-ALTAR_DISTANCE, 0}};
    public static final int[] ALTAR_AGES = {0, 3, 6, 8};
    /** Six domes at distance 29, every 60 degrees from 30 degrees on. */
    static final int[][] DOMES = {{25, 14}, {0, 29}, {-25, 14}, {-25, -14}, {0, -29}, {25, -14}};
    static final int[] DOME_AGES = {1, 2, 4, 5, 7, 3};
    static final int DOME_R2 = 12;
    /** The pillar wreath at distance 56, every 20 degrees, the south pillar left out for the gate. */
    static final int[][] PILLARS = {{56, 0}, {53, 19}, {43, 36}, {28, 48}, {10, 55}, {-10, 55}, {-28, 48}, {-43, 36}, {-53, 19},
        {-56, 0}, {-53, -19}, {-43, -36}, {-28, -48}, {-10, -55}, {10, -55}, {28, -48}, {43, -36}, {53, -19}};
    static final int PILLAR_HEIGHT = 5;
    static final int[][] TOWERS = {{40, 40}, {-40, 40}, {-40, -40}, {40, -40}};
    static final int TOWER_HEIGHT = 8;
    public static final int ARCH_Z = 58, ARCH_HALF = 4, ARCH_HEIGHT = 8;
    static final int GATE_HALF = 1;

    /** A segment of the ascent: direction, start, length, accent, climbing (one up per two cells) and bridge (no support). */
    record Segment(char dir, int x, int z, int n, int accent, boolean climb, boolean bridge) {
        int dx() {
            return dir == 'W' ? -1 : dir == 'E' ? 1 : 0;
        }

        int dz() {
            return dir == 'N' ? -1 : 0;
        }
    }

    static final Segment[] ASCENT = {
        new Segment('W', -1, 76, 16, 0, true, false),
        new Segment('W', -17, 76, 6, 0, false, true),
        new Segment('W', -23, 76, 16, 1, true, false),
        new Segment('N', -40, 77, 10, 2, true, false),
        new Segment('E', -39, 66, 16, 3, true, false),
        new Segment('E', -23, 66, 6, 4, false, true),
        new Segment('E', -17, 66, 16, 5, true, false),
        new Segment('N', 0, 67, 7, 6, false, false)};
    /** Vitrine i: segment, cell and side of the edge row its pillar stands on. */
    static final int[][] VITRINES = {{0, 3, 1}, {0, 11, 1}, {2, 3, 1}, {2, 11, 1}, {3, 3, -1}, {3, 7, -1}, {4, 5, 1}, {6, 4, 1}, {6, 11, 1}};

    private GrandArenaPlan() {}

    /** The arrival point of {@code origin tp}: on the platform, north of the DHD. */
    public static int[] arrival() {
        return new int[] {0, PLATFORM_Y + 1, 81};
    }

    /** Origin of Stargate Journey's pedestal template on the platform. */
    public static int[] gateOrigin() {
        return new int[] {-3, PLATFORM_Y, 84};
    }

    /**
     * True when a world's Origin has the grand arena ({@link OriginLayout#decide}): the stored layout name, else (a world
     * from before round G3) classic when its arena was already built (arena version above 0), grand for a new Origin.
     */
    public static boolean isGrand(String stored, int arenaVersion) {
        if (stored == null || stored.isEmpty()) return arenaVersion <= 0;
        return !stored.equalsIgnoreCase("classic");
    }

    /** Integer square root (floor). */
    static int isqrt(int n) {
        int r = (int) Math.sqrt(n);
        while (r * r > n) r--;
        while ((r + 1) * (r + 1) <= n) r++;
        return r;
    }

    static boolean inDisc(int x, int z) {
        return x * x + z * z <= RADIUS * RADIUS + RADIUS;
    }

    static String rock(int x, int y, int z) {
        int h = Math.floorMod(x * 31 + y * 17 + z * 13, 97);
        return "rock_" + (h < 50 ? 0 : h < 70 ? 1 : h < 85 ? 2 : h < 93 ? 3 : 4);
    }

    /** True on the north-east and south-west diagonal wedges (the trenches). */
    static boolean wedge(int x, int z) {
        if (!((x > 0 && z < 0) || (x < 0 && z > 0))) return false;
        int ax = Math.abs(x), az = Math.abs(z);
        return 2 * ax >= az && 2 * az >= ax;
    }

    static int bandAge(int k) {
        return 8 - (k - 36) / 2;
    }

    static boolean inDome(int x, int z) {
        for (int[] d : DOMES) if ((x - d[0]) * (x - d[0]) + (z - d[1]) * (z - d[1]) <= DOME_R2) return true;
        return false;
    }

    /** The y of the top block of disc column (x, z). */
    static int topY(int x, int z) {
        int k = isqrt(x * x + z * z);
        if (k <= SINK_R) return FLOOR_Y;
        if (k < 16) return FLOOR_Y + (k - SINK_R);
        if (k <= RING_R) {
            if (k >= TRENCH_R0 && k <= TRENCH_R1 && wedge(x, z) && Math.abs(Math.abs(x) - Math.abs(z)) > 1) return FLOOR_Y;
            return RING_Y;
        }
        if (k <= 41) return RING_Y + 1;
        if (k <= 47) return RING_Y + 2;
        if (k <= 53) return RING_Y + 3;
        return RIM_Y;
    }

    /** The material key of the top block of disc column (x, z). */
    static String topKey(int x, int z) {
        int d2 = x * x + z * z;
        int k = isqrt(d2);
        if (d2 <= 5) return "dais";
        boolean spoke = x == 0 || z == 0 || Math.abs(x) == Math.abs(z);
        if (k <= SINK_R) {
            if (k == 4 || k == 12) return k == 12 && spoke ? "lantern" : "metal_8";
            return "brick_8";
        }
        if (k < 16) return "stone_8";
        if (k <= RING_R) {
            if (k >= TRENCH_R0 && k <= TRENCH_R1 && wedge(x, z)) return Math.abs(Math.abs(x) - Math.abs(z)) <= 1 ? "trim_8" : "brick_8";
            if (k == 24) return "metal_8";
            if (k == 30 && spoke && !inDome(x, z)) return "lantern";
            return "stone_8";
        }
        if (k <= 53) return ((k - 36) % 2 == 0 ? "trim_" : "stone_") + bandAge(k);
        return "brick_8";
    }

    static boolean onPlatform(int x, int z) {
        return Math.abs(x) <= PLATFORM_HALF && z >= PLATFORM_Z0 && z <= PLATFORM_Z1;
    }

    /** The walkway y of cell j of segment s: each segment starts on the y the one before reached (plus one when climbing). */
    static int ascentY(int s, int j) {
        int y = PLATFORM_Y;
        for (int i = 0; i < ASCENT.length; i++) {
            Segment seg = ASCENT[i];
            int y0 = seg.climb ? y + 1 : y;
            if (i == s) return y0 + (seg.climb ? j / 2 : 0);
            y = seg.climb ? y0 + (seg.n - 1) / 2 : y0;
        }
        throw new IllegalArgumentException("segment " + s);
    }

    /** A cell of the ascent: walkway y, accent, bridge, edge row. */
    record Way(int y, int accent, boolean bridge, boolean edge) {}

    /** The ascent's walkway and edge cells by packed (x, 0, z); a walkway or the platform keeps its cell over an edge. */
    static Map<Long, Way> ascentCells() {
        Map<Long, Way> out = new LinkedHashMap<>();
        for (int s = 0; s < ASCENT.length; s++) {
            Segment seg = ASCENT[s];
            int px = -seg.dz(), pz = seg.dx();
            for (int j = 0; j < seg.n; j++) {
                int x = seg.x + j * seg.dx(), z = seg.z + j * seg.dz();
                int y = ascentY(s, j);
                for (int side = -2; side <= 2; side++) {
                    int cx = x + side * px, cz = z + side * pz;
                    boolean edge = Math.abs(side) == 2;
                    long key = pack(cx, 0, cz);
                    if (edge && ((out.containsKey(key) && !out.get(key).edge) || onPlatform(cx, cz))) continue;
                    out.put(key, new Way(y, seg.accent, seg.bridge, edge));
                }
            }
        }
        return out;
    }

    /** The glass cell (x, y, z) of vitrine {@code i}; its pillar stands one below, on the ascent's edge row. */
    public static int[] vitrine(int i) {
        int[] v = VITRINES[i];
        Segment seg = ASCENT[v[0]];
        int px = -seg.dz(), pz = seg.dx();
        return new int[] {seg.x + v[1] * seg.dx() + v[2] * 2 * px, ascentY(v[0], v[1]) + 1, seg.z + v[1] * seg.dz() + v[2] * 2 * pz};
    }

    /** Every cell of the plan, keyed by packed position (x, y, z), later entries overriding earlier ones. */
    public static Map<Long, String> cells() {
        Map<Long, String> m = new LinkedHashMap<>();
        // 1. the disc: top, fill under it, the rock cone below the sink level
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (!inDisc(x, z)) continue;
                int t = topY(x, z);
                put(m, x, t, z, topKey(x, z));
                for (int y = FLOOR_Y; y < t; y++) put(m, x, y, z, "stone_8");
                int k = isqrt(x * x + z * z);
                int depth = 3 + (RADIUS - k) * 15 / RADIUS + Math.floorMod(x * 31 + z * 17, 3);
                for (int dy = 1; dy <= depth; dy++) put(m, x, FLOOR_Y - dy, z, rock(x, FLOOR_Y - dy, z));
            }
        }
        // 2. the parapet on the rim with merlons, cut at the south gate
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (!inDisc(x, z) || isqrt(x * x + z * z) < 59) continue;
                if (z > 0 && Math.abs(x) <= GATE_HALF) continue;
                put(m, x, RIM_Y + 1, z, "brick_8");
                if (isqrt(x * x + z * z) == RADIUS && Math.floorMod(x + z, 2) == 0) put(m, x, RIM_Y + 2, z, "brick_8");
            }
        }
        // 3. the pillar wreath
        for (int i = 0; i < PILLARS.length; i++) {
            int a = i % 9;
            for (int dy = 1; dy <= PILLAR_HEIGHT; dy++) put(m, PILLARS[i][0], RIM_Y + dy, PILLARS[i][1], "pillar_" + a);
            put(m, PILLARS[i][0], RIM_Y + PILLAR_HEIGHT + 1, PILLARS[i][1], "lamp_" + a);
        }
        // 3b. the four towers
        for (int[] t : TOWERS) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 1; dy <= TOWER_HEIGHT; dy++) put(m, t[0] + dx, RIM_Y + dy, t[1] + dz, "pillar_8");
                    put(m, t[0] + dx, RIM_Y + TOWER_HEIGHT + 1, t[1] + dz, "trim_8");
                }
            }
            put(m, t[0], RIM_Y + TOWER_HEIGHT + 2, t[1], "lamp_8");
        }
        // 4. the four altar podiums
        for (int a = 0; a < ALTARS.length; a++) {
            int ax = ALTARS[a][0], az = ALTARS[a][1];
            String brick = "brick_" + ALTAR_AGES[a];
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    put(m, ax + dx, RING_Y + 1, az + dz, brick);
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) put(m, ax + dx, RING_Y + 2, az + dz, brick);
                }
            }
            put(m, ax, ALTAR_Y, az, "arena_altar");
        }
        // 5. six open domes: a two-high wall ring with four doorways and slits, a cap ring with an oculus
        for (int i = 0; i < DOMES.length; i++) {
            int cx = DOMES[i][0], cz = DOMES[i][1];
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 >= 7 && d2 <= DOME_R2) {
                        if (dx != 0 && dz != 0) put(m, cx + dx, RING_Y + 1, cz + dz, "brick_" + DOME_AGES[i]);
                        if (Math.floorMod(dx + dz, 2) == 0) put(m, cx + dx, RING_Y + 2, cz + dz, "brick_" + DOME_AGES[i]);
                    }
                    if (d2 >= 1 && d2 <= 8) put(m, cx + dx, RING_Y + 3, cz + dz, "trim_" + DOME_AGES[i]);
                }
            }
        }
        // 6. the arch over the south gate
        for (int y = RIM_Y + 1; y < RIM_Y + ARCH_HEIGHT; y++) {
            put(m, -ARCH_HALF, y, ARCH_Z, "pillar_8");
            put(m, ARCH_HALF, y, ARCH_Z, "pillar_8");
        }
        for (int x = -ARCH_HALF; x <= ARCH_HALF; x++) put(m, x, RIM_Y + ARCH_HEIGHT, ARCH_Z, x == 0 ? "lamp_8" : "trim_8");
        // 7. the arrival platform with its rim, lamp posts and rock below
        for (int x = -PLATFORM_HALF; x <= PLATFORM_HALF; x++) {
            for (int z = PLATFORM_Z0; z <= PLATFORM_Z1; z++) {
                put(m, x, PLATFORM_Y, z, "brick_8");
                int depth = 2 + Math.min(PLATFORM_HALF - Math.abs(x), Math.min(z - PLATFORM_Z0, PLATFORM_Z1 - z)) / 2;
                for (int dy = 1; dy <= depth; dy++) put(m, x, PLATFORM_Y - dy, z, rock(x, PLATFORM_Y - dy, z));
                boolean edge = Math.abs(x) == PLATFORM_HALF || z == PLATFORM_Z1 || z == PLATFORM_Z0;
                if (edge && !(z == PLATFORM_Z0 && x >= -2 && x <= 0)) put(m, x, PLATFORM_Y + 1, z, "stone_8");
                if (Math.abs(x) == PLATFORM_HALF && (z == PLATFORM_Z0 || z == PLATFORM_Z1)) {
                    put(m, x, PLATFORM_Y + 2, z, "pillar_8");
                    put(m, x, PLATFORM_Y + 3, z, "pillar_8");
                    put(m, x, PLATFORM_Y + 4, z, "lamp_8");
                }
            }
        }
        // 8. the ascent: walkway, edge rows with rails, the core and rock under the flights (bridges float)
        for (var e : ascentCells().entrySet()) {
            int x = unpackX(e.getKey()), z = unpackZ(e.getKey());
            Way w = e.getValue();
            if (w.edge) {
                put(m, x, w.y, z, "stone_" + w.accent);
                put(m, x, w.y + 1, z, "trim_" + w.accent);
            } else {
                put(m, x, w.y, z, (w.bridge ? "trim_" : "brick_") + w.accent);
            }
            if (!w.bridge) {
                put(m, x, w.y - 1, z, "stone_8");
                for (int dy = 2; dy < (w.edge ? 3 : 5); dy++) put(m, x, w.y - dy, z, rock(x, w.y - dy, z));
            }
        }
        // 9. the nine vitrines on the edge rows (the rail there gives way to the pillar)
        for (int i = 0; i < 9; i++) {
            int[] v = vitrine(i);
            put(m, v[0], v[1] - 1, v[2], "pillar_" + i);
            put(m, v[0], v[1], v[2], "glass_" + i);
        }
        return m;
    }

    /** Count per material key, sorted. */
    public static Map<String, Integer> counts() {
        Map<String, Integer> c = new TreeMap<>();
        for (String k : cells().values()) c.merge(k, 1, Integer::sum);
        return c;
    }

    /**
     * Breadth-first walk from {@code start} (x, y, z: the feet) over four neighbours with a step of at most one up or
     * down; a cell is walkable when the block below is solid and the two above are not. @return the path length to the
     * first cell on the rim (y above the rim, inside the disc), -1 if none
     */
    public static int walkToRim(LongPredicate solid, int[] start) {
        return walk(solid, start, (x, y, z) -> y == RIM_Y + 1 && inDisc(x, z));
    }

    interface Goal {
        boolean at(int x, int y, int z);
    }

    static int walk(LongPredicate solid, int[] start, Goal goal) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<long[]> q = new ArrayDeque<>();
        long s = pack(start[0], start[1], start[2]);
        seen.add(s);
        q.add(new long[] {s, 0});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            long[] cur = q.poll();
            int x = unpackX(cur[0]), y = unpackY(cur[0]), z = unpackZ(cur[0]);
            if (goal.at(x, y, z)) return (int) cur[1];
            for (int[] d : steps) {
                for (int dy : new int[] {0, 1, -1}) {
                    int nx = x + d[0], ny = y + dy, nz = z + d[1];
                    long p = pack(nx, ny, nz);
                    if (seen.contains(p)) continue;
                    if (solid.test(pack(nx, ny - 1, nz)) && !solid.test(p) && !solid.test(pack(nx, ny + 1, nz))) {
                        seen.add(p);
                        q.add(new long[] {p, cur[1] + 1});
                    }
                }
            }
        }
        return -1;
    }

    /** {@link #walkToRim} over the plan itself (every cell is a full block). */
    public static int planWalkToRim() {
        Map<Long, String> m = cells();
        Map<Long, Boolean> solid = new HashMap<>();
        return walkToRim(p -> solid.computeIfAbsent(p, m::containsKey), arrival());
    }

    private static void put(Map<Long, String> m, int x, int y, int z, String key) {
        m.put(pack(x, y, z), key);
    }

    /** Same packing as {@code BlockPos.asLong}. */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FFFFFFL) << 12;
    }

    public static int unpackX(long p) {
        return (int) (p << 0 >> 38);
    }

    public static int unpackY(long p) {
        return (int) (p << 52 >> 52);
    }

    public static int unpackZ(long p) {
        return (int) (p << 26 >> 38);
    }
}
