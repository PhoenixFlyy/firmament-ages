package dev.firmages.core.isles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The body of one Drifting Isle (SPEC §30.4), pure and deterministic from its {@link IsleSpec}: a noise-edged inverted
 * cone of skystone with a gentle dome on top, the firmament lode at the underside tip (medium and large isles), crystal
 * clusters hanging from the underside, set into rim cracks and on the floor of a hollow that opens to the top (medium and
 * large isles), and up to two enclosed pockets of dirt under grass for the trees. The Wind Shrine's isle keeps a flat
 * top within {@link #SHRINE_FLAT} blocks of its centre and a rounder rim.
 * <p>
 * {@link #emit} writes the blocks of a column range (one chunk during worldgen, all of them for the fallback placer):
 * the body column by column from the bottom, then the special blocks in a fixed order, so a cluster always comes after
 * the block that carries it.
 */
public final class IsleShape {
    /** The Wind Shrine's ruin needs a flat top within this distance of the centre (at most 11 by 11 blocks). */
    public static final int SHRINE_FLAT = 7;
    /** The smallest radius of an isle that carries the Wind Shrine. */
    public static final int SHRINE_MIN_RADIUS = 10;

    public enum Kind {
        SKYSTONE, DIRT, GRASS, LODE, AIR, CLUSTER_UP, CLUSTER_DOWN, CLUSTER_NORTH, CLUSTER_SOUTH, CLUSTER_EAST, CLUSTER_WEST;

        public boolean cluster() {
            return ordinal() >= CLUSTER_UP.ordinal();
        }

        /** The offset (dx, dy, dz) from a cluster to the block that carries it. */
        public int[] support() {
            return switch (this) {
                case CLUSTER_UP -> new int[] {0, -1, 0};
                case CLUSTER_DOWN -> new int[] {0, 1, 0};
                case CLUSTER_NORTH -> new int[] {0, 0, 1};
                case CLUSTER_SOUTH -> new int[] {0, 0, -1};
                case CLUSTER_EAST -> new int[] {-1, 0, 0};
                case CLUSTER_WEST -> new int[] {1, 0, 0};
                default -> new int[] {0, 0, 0};
            };
        }
    }

    @FunctionalInterface
    public interface Sink {
        void put(int x, int y, int z, Kind kind);
    }

    /** A tree: its trunk column and the first free block above the pocket's grass. */
    public record Tree(int x, int y, int z) {}

    private final IsleSpec s;
    private final double p1;
    private final double p2;
    private final double p3;
    private final double a1;
    private final double a2;
    private final double a3;
    private final double base;
    private final Map<Long, Kind> specials = new LinkedHashMap<>();
    private final List<Tree> trees = new ArrayList<>();
    private int hollowR;

    private IsleShape(IsleSpec spec) {
        this.s = spec;
        Random r = new Random(IsleLayout.mix(spec.seed(), 0x5A4E));
        p1 = r.nextDouble() * Math.PI * 2;
        p2 = r.nextDouble() * Math.PI * 2;
        p3 = r.nextDouble() * Math.PI * 2;
        if (spec.shrine()) {
            base = 0.94;
            a1 = 0.03;
            a2 = 0.02;
            a3 = 0.01;
        } else {
            base = 0.88;
            a1 = 0.06;
            a2 = 0.04;
            a3 = 0.02;
        }
        build(r);
    }

    public static IsleShape of(IsleSpec spec) {
        return new IsleShape(spec);
    }

    public IsleSpec spec() {
        return s;
    }

    /** The underside below the top at a relative distance {@code t} (0 centre, 1 rim) of an isle of that depth. */
    public static int underside(int depth, double t) {
        return (int) Math.round(depth * Math.pow(Math.max(0.0, 1.0 - t), 1.6));
    }

    /** The edge radius at angle {@code theta}. */
    double edge(double theta) {
        return s.radius() * (base + a1 * Math.sin(3 * theta + p1) + a2 * Math.sin(5 * theta + p2) + a3 * Math.sin(7 * theta + p3));
    }

    /** The smallest edge radius over all angles (a safe bound for inner features). */
    double minEdge() {
        return s.radius() * (base - a1 - a2 - a3);
    }

    /** Relative distance of column (dx, dz) from the centre: 0 at the centre, 1 on the edge, above 1 outside. */
    double t(int dx, int dz) {
        if (dx == 0 && dz == 0) return 0;
        return Math.sqrt(dx * dx + dz * dz) / edge(Math.atan2(dz, dx));
    }

    public boolean inside(int dx, int dz) {
        return t(dx, dz) <= 1.0;
    }

    /** The top surface of column (dx, dz), relative to the centre. */
    public int topAt(int dx, int dz) {
        double t = t(dx, dz);
        if (s.shrine()) {
            if (dx * dx + dz * dz <= SHRINE_FLAT * SHRINE_FLAT) return s.top();
            return t > 0.85 ? s.top() - 1 : s.top();
        }
        if (t > 0.85) return s.top() - 1;
        return s.top() + (int) Math.round(1.2 * (1 - t * t));
    }

    /** The lowest block of column (dx, dz): the inverted cone with a little jitter; the rim is at least two blocks thick. */
    public int bottomAt(int dx, int dz) {
        double t = t(dx, dz);
        int jitter = t > 0.2 && t < 0.9 ? (int) (IsleLayout.mix(s.seed(), (long) dx * 31 + dz) & 1L) : 0;
        int b = s.top() - underside(s.depth(), t) - jitter;
        return Math.min(b, topAt(dx, dz) - 1);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private void special(int dx, int y, int dz, Kind k) {
        specials.putIfAbsent(key(s.x() + dx, y, s.z() + dz), k);
    }

    private boolean free(int dx, int y, int dz) {
        return !specials.containsKey(key(s.x() + dx, y, s.z() + dz));
    }

    private void build(Random r) {
        int rad = s.radius();
        double minEdge = minEdge();
        // the hollow (medium and large isles), clear of the shrine's flat top
        int hx = 0;
        int hz = 0;
        int hy = 0;
        if (s.size() != IsleSize.SMALL) {
            int hr = s.size() == IsleSize.LARGE ? 3 : 2;
            double angle = r.nextDouble() * Math.PI * 2;
            double dist = s.shrine() ? SHRINE_FLAT + hr + 2 : 0.4 * rad;
            if (dist + hr + 2 <= 0.85 * minEdge) {
                hollowR = hr;
                hx = (int) Math.round(Math.cos(angle) * dist);
                hz = (int) Math.round(Math.sin(angle) * dist);
                hy = s.top() - hr - 1;
            }
        }
        // trees and their dirt pockets
        Set<Long> pocketCols = new HashSet<>();
        for (int i = 0; i < s.trees(); i++) {
            double minD = s.shrine() ? IsleLayout.SHRINE_CLEAR : 2;
            double maxD = 0.55 * minEdge;
            for (int attempt = 0; attempt < 10 && maxD >= minD; attempt++) {
                double angle = r.nextDouble() * Math.PI * 2;
                double d = minD + r.nextDouble() * (maxD - minD);
                int tx = (int) Math.round(Math.cos(angle) * d);
                int tz = (int) Math.round(Math.sin(angle) * d);
                if (hollowR > 0 && Math.hypot(tx - hx, tz - hz) < hollowR + 3) continue;
                boolean near = false;
                for (Tree t : trees) if (Math.hypot(t.x() - s.x() - tx, t.z() - s.z() - tz) < 5) near = true;
                if (near || (tx == 0 && tz == 0)) continue;
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        int cx = tx + ox;
                        int cz = tz + oz;
                        int top = topAt(cx, cz);
                        special(cx, top, cz, Kind.GRASS);
                        for (int y = top - 2; y < top; y++) if (y > bottomAt(cx, cz)) special(cx, y, cz, Kind.DIRT);
                        pocketCols.add(((long) cx << 32) ^ (cz & 0xFFFFFFFFL));
                    }
                }
                trees.add(new Tree(s.x() + tx, topAt(tx, tz) + 1, s.z() + tz));
                break;
            }
        }
        // the hollow: a sphere of air below the top with a shaft to the surface
        List<int[]> floor = new ArrayList<>();
        if (hollowR > 0) {
            for (int dx = -hollowR; dx <= hollowR; dx++) {
                for (int dy = -hollowR; dy <= hollowR; dy++) {
                    for (int dz = -hollowR; dz <= hollowR; dz++) {
                        if (dx * dx + dy * dy + dz * dz > hollowR * hollowR + 0.5) continue;
                        int cx = hx + dx;
                        int cz = hz + dz;
                        int y = hy + dy;
                        if (y <= bottomAt(cx, cz) || y > topAt(cx, cz)) continue;
                        special(cx, y, cz, Kind.AIR);
                        boolean belowAir = (dx * dx + (dy - 1) * (dy - 1) + dz * dz) <= hollowR * hollowR + 0.5;
                        if (!belowAir && y - 1 > bottomAt(cx, cz)) floor.add(new int[] {cx, y, cz});
                    }
                }
            }
            for (int y = hy; y <= topAt(hx, hz); y++) special(hx, y, hz, Kind.AIR);
        }
        // the lode at the underside tip
        if (s.lode()) special(0, bottomAt(0, 0), 0, Kind.LODE);
        // the crystal clusters: the hollow floor, rim cracks, the underside
        int n = s.crystals();
        int inHollow = floor.isEmpty() ? 0 : Math.min(3, Math.min(floor.size(), n / 4 + 1));
        int rest = n - inHollow;
        int onRim = (int) Math.round(rest * 0.35);
        int under = rest - onRim;
        for (int i = 0; i < inHollow && !floor.isEmpty(); i++) {
            int[] f = floor.remove(r.nextInt(floor.size()));
            if (f[0] == hx && f[2] == hz) {
                i--;
                continue;
            }
            specials.put(key(s.x() + f[0], f[1], s.z() + f[2]), Kind.CLUSTER_UP); // replaces the hollow's air
        }
        List<int[]> rim = new ArrayList<>();
        for (int dx = -rad - 1; dx <= rad + 1; dx++) {
            for (int dz = -rad - 1; dz <= rad + 1; dz++) {
                if (!inside(dx, dz)) continue;
                if (!inside(dx + 1, dz)) rim.add(new int[] {dx, dz, Kind.CLUSTER_EAST.ordinal()});
                if (!inside(dx - 1, dz)) rim.add(new int[] {dx, dz, Kind.CLUSTER_WEST.ordinal()});
                if (!inside(dx, dz + 1)) rim.add(new int[] {dx, dz, Kind.CLUSTER_SOUTH.ordinal()});
                if (!inside(dx, dz - 1)) rim.add(new int[] {dx, dz, Kind.CLUSTER_NORTH.ordinal()});
            }
        }
        for (int i = 0; i < onRim && !rim.isEmpty(); i++) {
            int[] e = rim.remove(r.nextInt(rim.size()));
            Kind k = Kind.values()[e[2]];
            int[] sup = k.support();
            int cx = e[0] - sup[0];
            int cz = e[1] - sup[2];
            int lo = bottomAt(e[0], e[1]);
            int hi = topAt(e[0], e[1]) - 1;
            int y = hi > lo ? lo + r.nextInt(hi - lo + 1) : lo;
            if (inside(cx, cz) || !free(cx, y, cz)) continue;
            special(cx, y, cz, k);
        }
        for (int i = 0, tries = 0; i < under && tries < under * 8; tries++) {
            int dx = r.nextInt(2 * rad + 1) - rad;
            int dz = r.nextInt(2 * rad + 1) - rad;
            double t = t(dx, dz);
            if (t < 0.15 || t > 0.85 || pocketCols.contains(((long) dx << 32) ^ (dz & 0xFFFFFFFFL))) continue;
            int y = bottomAt(dx, dz) - 1;
            if (!free(dx, y, dz)) continue;
            special(dx, y, dz, Kind.CLUSTER_DOWN);
            i++;
        }
    }

    /** The trees with their trunk positions (world coordinates). */
    public List<Tree> trees() {
        return trees;
    }

    /** Radius of the hollow, 0 without one. */
    public int hollowRadius() {
        return hollowR;
    }

    /** The lowest y any block of this isle takes (an underside cluster below the tip). */
    public int minY() {
        return s.top() - s.depth() - 2;
    }

    /** The highest y of the body (trees and the shrine add their own height). */
    public int maxY() {
        return s.top() + 2;
    }

    /**
     * Writes the blocks whose columns lie within [minX, maxX] by [minZ, maxZ] (world coordinates, inclusive): first the
     * body, column by column from the bottom, then the special blocks.
     */
    public void emit(Sink sink, int minX, int minZ, int maxX, int maxZ) {
        int rad = s.radius() + 1;
        int x0 = Math.max(minX, s.x() - rad);
        int x1 = Math.min(maxX, s.x() + rad);
        int z0 = Math.max(minZ, s.z() - rad);
        int z1 = Math.min(maxZ, s.z() + rad);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - s.x();
                int dz = z - s.z();
                if (!inside(dx, dz)) continue;
                int top = topAt(dx, dz);
                for (int y = bottomAt(dx, dz); y <= top; y++) {
                    if (!specials.containsKey(key(x, y, z))) sink.put(x, y, z, Kind.SKYSTONE);
                }
            }
        }
        for (Map.Entry<Long, Kind> e : specials.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 38);
            int z = (int) (k << 26 >> 38);
            int y = (int) (k << 52 >> 52);
            if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
            sink.put(x, y, z, e.getValue());
        }
    }

    /** Every block of the isle, in placing order. */
    public void emitAll(Sink sink) {
        emit(sink, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
}
