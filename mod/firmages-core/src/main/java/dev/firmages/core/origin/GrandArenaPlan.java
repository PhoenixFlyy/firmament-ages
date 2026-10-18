package dev.firmages.core.origin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The grand arena of The Origin (round G3, SPEC §16.5) as a pure block plan: every cell with a material key, no
 * Minecraft types, so a JUnit test, the GameTest and {@code dev/gen_ark.py} (which mirrors it and writes
 * {@code dev/data/origin_arena.json}) agree on the same counts. {@link OriginArena} maps the keys to block states.
 *
 * <p>Three times the old diameter (radius 60 at y 64): an inner fighting floor (radius 35, gilded rings at 4, 12 and
 * 24, eight sea lanterns on the spokes at 30), nine two-block bands of the consecrated family from radius 36 to 53, one
 * per shrine Age, Age 0 outermost (the Archipelago's materials from outside in), an open rim from 54 to 60 with lantern
 * posts and the abyss beyond. Four altars stand on sockets 16 blocks out (north, east, south, west). In the south a
 * raised arrival platform (y 72) carries the return gate, an arch of the Quantum accent over the head of an 11-wide
 * stair down to the floor, and nine vitrines with the Ages' relics. All arithmetic is integer, so Python and Java match.
 */
public final class GrandArenaPlan {
    public static final int RADIUS = 60;
    public static final int FLOOR_Y = 64;
    public static final int PLATFORM_Y = 72;
    /** Platform footprint (x from -PLATFORM_HALF to +PLATFORM_HALF, z from PLATFORM_Z0 to PLATFORM_Z1). */
    public static final int PLATFORM_HALF = 12, PLATFORM_Z0 = 57, PLATFORM_Z1 = 84;
    public static final int STAIR_HALF = 5, STAIR_STEPS = 7;
    public static final int ARCH_Z = 58, ARCH_HALF = 7, ARCH_HEIGHT = 8;
    /** Altars: distance from the centre and the Age accent of each socket, in the order north, east, south, west. */
    public static final int ALTAR_DISTANCE = 16;
    public static final int[][] ALTARS = {{0, -ALTAR_DISTANCE}, {ALTAR_DISTANCE, 0}, {0, ALTAR_DISTANCE}, {-ALTAR_DISTANCE, 0}};
    public static final int[] ALTAR_AGES = {0, 3, 6, 8};
    /** Lantern posts on the rim every 30 degrees at distance 59, the south one left out for the stair. */
    static final int[][] POSTS = {{59, 0}, {51, 30}, {30, 51}, {-30, 51}, {-51, 30}, {-59, 0}, {-51, -30}, {-30, -51}, {0, -59},
        {30, -51}, {51, -30}};
    /** Vitrine of Age i: x and z of its pillar on the platform. */
    public static int[] vitrine(int i) {
        return i < 5 ? new int[] {-10, 62 + 4 * i} : new int[] {10, 62 + 4 * (i - 5)};
    }

    private GrandArenaPlan() {}

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
        return (int) Math.sqrt(n);
    }

    static boolean inDisc(int x, int z) {
        return x * x + z * z <= RADIUS * RADIUS + RADIUS;
    }

    static String rock(int x, int y, int z) {
        int h = Math.floorMod(x * 31 + y * 17 + z * 13, 97);
        return "rock_" + (h < 50 ? 0 : h < 70 ? 1 : h < 85 ? 2 : h < 93 ? 3 : 4);
    }

    /** The floor key of a disc column at distance index {@code k}. */
    static String top(int x, int z) {
        int d2 = x * x + z * z;
        int k = isqrt(d2);
        if (d2 <= 5) return "dais";
        if (k <= 35) {
            boolean spoke = x == 0 || z == 0 || Math.abs(x) == Math.abs(z);
            if (k == 4 || k == 12 || k == 24) return "gilded";
            if (k == 30 && spoke) return "lantern";
            return "floor";
        }
        if (k <= 53) {
            int age = 8 - (k - 36) / 2;
            return "age_" + age + ((k - 36) % 2 == 0 ? "_trim" : "_stone");
        }
        return "rim";
    }

    /** Every cell of the plan, keyed by packed position (x, y, z), later entries overriding earlier ones. */
    public static Map<Long, String> cells() {
        Map<Long, String> m = new LinkedHashMap<>();
        // 1. the disc and its rock cone
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (!inDisc(x, z)) continue;
                put(m, x, FLOOR_Y, z, top(x, z));
                int k = isqrt(x * x + z * z);
                int depth = 3 + (RADIUS - k) * 15 / RADIUS + Math.floorMod(x * 31 + z * 17, 3);
                for (int dy = 1; dy <= depth; dy++) put(m, x, FLOOR_Y - dy, z, rock(x, FLOOR_Y - dy, z));
            }
        }
        for (int[] p : POSTS) {
            put(m, p[0], FLOOR_Y + 1, p[1], "post");
            put(m, p[0], FLOOR_Y + 2, p[1], "lantern");
        }
        // 2. the four altars on their sockets
        for (int a = 0; a < ALTARS.length; a++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) put(m, ALTARS[a][0] + dx, FLOOR_Y + 1, ALTARS[a][1] + dz, "socket_" + ALTAR_AGES[a]);
            }
            put(m, ALTARS[a][0], FLOOR_Y + 2, ALTARS[a][1], "arena_altar");
        }
        // 3. the arrival platform
        for (int x = -PLATFORM_HALF; x <= PLATFORM_HALF; x++) {
            for (int z = PLATFORM_Z0; z <= PLATFORM_Z1; z++) {
                boolean edge = Math.abs(x) == PLATFORM_HALF || z == PLATFORM_Z1;
                put(m, x, PLATFORM_Y, z, edge ? "platform_rim" : "platform");
                if (inDisc(x, z)) {
                    for (int y = FLOOR_Y + 1; y < PLATFORM_Y; y++) put(m, x, y, z, "wall");
                } else {
                    int depth = 2 + Math.min(PLATFORM_HALF - Math.abs(x), PLATFORM_Z1 - z) / 2;
                    for (int dy = 1; dy <= depth; dy++) put(m, x, PLATFORM_Y - dy, z, rock(x, PLATFORM_Y - dy, z));
                }
                if (z == PLATFORM_Z0 && Math.abs(x) > STAIR_HALF) put(m, x, PLATFORM_Y + 1, z, "platform_rim");
            }
        }
        // 4. the stair down to the floor, with a parapet on each side
        for (int j = 1; j <= STAIR_STEPS; j++) {
            int z = PLATFORM_Z0 - j, y = PLATFORM_Y - j;
            for (int x = -STAIR_HALF; x <= STAIR_HALF; x++) {
                put(m, x, y, z, "stair");
                for (int yy = FLOOR_Y + 1; yy < y; yy++) put(m, x, yy, z, "wall");
            }
            for (int s = -1; s <= 1; s += 2) {
                for (int yy = FLOOR_Y + 1; yy <= y + 1; yy++) put(m, s * (STAIR_HALF + 1), yy, z, "wall");
            }
        }
        // 5. the arch over the head of the stair
        for (int y = PLATFORM_Y + 1; y < PLATFORM_Y + ARCH_HEIGHT; y++) {
            put(m, -ARCH_HALF, y, ARCH_Z, "arch_pillar");
            put(m, ARCH_HALF, y, ARCH_Z, "arch_pillar");
        }
        for (int x = -ARCH_HALF; x <= ARCH_HALF; x++) put(m, x, PLATFORM_Y + ARCH_HEIGHT, ARCH_Z, x == 0 ? "arch_lamp" : "arch_trim");
        // 6. the nine vitrines
        for (int i = 0; i < 9; i++) {
            int[] v = vitrine(i);
            put(m, v[0], PLATFORM_Y + 1, v[1], "vitrine_base_" + i);
            put(m, v[0], PLATFORM_Y + 2, v[1], "vitrine_glass_" + i);
        }
        return m;
    }

    /** Count per material key, sorted. */
    public static Map<String, Integer> counts() {
        Map<String, Integer> c = new TreeMap<>();
        for (String k : cells().values()) c.merge(k, 1, Integer::sum);
        return c;
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
