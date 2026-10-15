package dev.firmages.core.sky;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The block changes of one crater (SPEC §25.3), pure and deterministic from the site seed. Coordinates are relative to
 * the centre column: {@code dy} of a bowl op is relative to the ground height at the centre, {@code dy} of a
 * {@code surface} op (the lining over the lip, the ejecta rim) relative to the ground of its own column, which the
 * carver reads when it gets there. Order: air from the top down, the lining, the lip, the rim, the meteorites, the
 * core last.
 * <ul>
 * <li>The bowl has the size's radius and depth; the depth falls off with the square of the distance, with ±1 noise.</li>
 * <li>Everything inside the bowl and up to {@link #AIR_ABOVE} blocks above it becomes air (trees included).</li>
 * <li>A one-block lining of sky rock covers the bowl and extends one block outward over the lip.</li>
 * <li>The meteorites sit in an ellipsoid cluster at the bowl floor around the core.</li>
 * <li>An ejecta rim of sky rock runs from the radius to the radius + 2, one block high with chance {@link #RIM_CHANCE}.</li>
 * </ul>
 */
public final class CraterPlan {
    public static final int AIR_ABOVE = 12;
    public static final double RIM_CHANCE = 0.4;
    /** Nothing of a crater lies further than this from its centre column (the box of its chunks). */
    public static final int MARGIN = 3;

    public enum Kind { AIR, SKY_ROCK, METEORITE, CORE }

    /**
     * One change. {@code block} is the meteorite block id for {@link Kind#METEORITE} and empty otherwise;
     * {@code surface} ops are placed relative to their own column's ground.
     */
    public record Op(int dx, int dy, int dz, Kind kind, String block, boolean surface) {}

    private final List<Op> ops;
    private final int radius;

    private CraterPlan(List<Op> ops, int radius) {
        this.ops = Collections.unmodifiableList(ops);
        this.radius = radius;
    }

    public List<Op> ops() {
        return ops;
    }

    public int radius() {
        return radius;
    }

    /** The reach of the crater from its centre column: radius + {@link #MARGIN}. */
    public int reach() {
        return radius + MARGIN;
    }

    public long count(Kind kind) {
        return ops.stream().filter(o -> o.kind() == kind).count();
    }

    /** The floor height (relative to the centre ground) of a bowl column, the same function the plan used. */
    public static int floor(long seed, int radius, int depth, int dx, int dz) {
        int d2 = dx * dx + dz * dz;
        int base = -(int) Math.round(depth * (1.0 - (double) d2 / ((double) radius * radius)));
        if (dx == 0 && dz == 0) return -depth;
        long h = StarfallRules.mix(seed, ((long) dx << 32) ^ (dz & 0xFFFFFFFFL));
        int noise = (int) Math.floorMod(h, 5) - 2; // -2..2; only the ends move the floor: ±1 with chance 0.2 each
        int n = noise == -2 ? -1 : noise == 2 ? 1 : 0;
        return Math.max(-depth - 1, Math.min(0, base + n));
    }

    /**
     * The plan of a crater.
     *
     * @param mass meteorite block id -> weight (the metal's {@code mass}); iteration order fixes the draw
     */
    public static CraterPlan of(long seed, StarfallData.Size size, Map<String, Double> mass) {
        int r = size.radius();
        int depth = size.depth();
        Random rng = new Random(seed);
        List<Op> air = new ArrayList<>();
        Map<Long, Op> lining = new LinkedHashMap<>();
        List<Op> lip = new ArrayList<>();
        List<Op> rim = new ArrayList<>();
        int r2 = r * r;
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= r2) {
                    int f = floor(seed, r, depth, dx, dz);
                    lining.put(key(dx, f, dz), new Op(dx, f, dz, Kind.SKY_ROCK, "", false));
                } else if (d2 <= (r + 1) * (r + 1)) {
                    lip.add(new Op(dx, 0, dz, Kind.SKY_ROCK, "", true));
                }
                if (d2 >= r2 && d2 <= (r + 2) * (r + 2)) {
                    long h = StarfallRules.mix(seed ^ 0x7217L, ((long) dx << 32) ^ (dz & 0xFFFFFFFFL));
                    if ((h >>> 11) * 0x1.0p-53 < RIM_CHANCE) rim.add(new Op(dx, 1, dz, Kind.SKY_ROCK, "", true));
                }
            }
        }
        // air: top down over every bowl column, from AIR_ABOVE to just above the floor
        for (int y = AIR_ABOVE; y >= -depth; y--) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r2) continue;
                    if (y > floor(seed, r, depth, dx, dz)) air.add(new Op(dx, y, dz, Kind.AIR, "", false));
                }
            }
        }
        // the cluster: the n positions nearest the core in an ellipsoid (flat and wide), at or below the floor
        int coreY = -depth;
        int n = size.minMeteorites() + rng.nextInt(size.maxMeteorites() - size.minMeteorites() + 1);
        double a = Math.max(1.5, Math.cbrt(n) * 1.1);
        double b = Math.max(1.0, a * 0.6);
        List<double[]> cand = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                int f = floor(seed, r, depth, dx, dz);
                for (int y = f; y >= f - 3; y--) {
                    if (dx == 0 && dz == 0 && y == coreY) continue;
                    double e = (dx * dx) / (a * a) + ((y - coreY) * (y - coreY)) / (b * b) + (dz * dz) / (a * a);
                    cand.add(new double[] {e + rng.nextDouble() * 0.35, dx, y, dz});
                }
            }
        }
        cand.sort((p, q) -> Double.compare(p[0], q[0]));
        double total = mass.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Op> meteorites = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int i = 0; i < cand.size() && meteorites.size() < n; i++) {
            int dx = (int) cand.get(i)[1];
            int y = (int) cand.get(i)[2];
            int dz = (int) cand.get(i)[3];
            if (!taken.add(key(dx, y, dz))) continue;
            meteorites.add(new Op(dx, y, dz, Kind.METEORITE, pick(mass, total, rng.nextDouble()), false));
        }
        taken.add(key(0, coreY, 0));
        List<Op> out = new ArrayList<>(air);
        for (Op l : lining.values()) if (!taken.contains(key(l.dx(), l.dy(), l.dz()))) out.add(l);
        out.addAll(lip);
        out.addAll(rim);
        out.addAll(meteorites);
        out.add(new Op(0, coreY, 0, Kind.CORE, "", false));
        return new CraterPlan(out, r);
    }

    private static String pick(Map<String, Double> mass, double total, double roll) {
        double x = roll * total;
        String last = "";
        for (Map.Entry<String, Double> e : mass.entrySet()) {
            last = e.getKey();
            x -= e.getValue();
            if (x < 0) return e.getKey();
        }
        return last;
    }

    private static long key(int dx, int dy, int dz) {
        return ((long) (dx & 0xFFFF) << 32) | ((long) (dy & 0xFFFF) << 16) | (dz & 0xFFFF);
    }
}
