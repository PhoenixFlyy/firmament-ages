package dev.firmages.core.isles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The layout of a Drifting Isle cluster (SPEC §30.3), pure: the horizontal plan follows from the world seed and the
 * start chunk alone (so the Starfall probe, the pointer and {@code locate} know it without generating anything), the
 * heights from a terrain sampler (the chunk generator's {@code getBaseHeight} for worldgen and for the fallback cluster).
 * <ul>
 *   <li>The main isle sits on the start chunk's middle; its size is drawn by the weights. One to four isles in all; the
 *   satellites are small and lie around it with a gap of {@link #GAP_MIN} to {@link #GAP_MAX} blocks.</li>
 *   <li>The depth of the underside cone grows with the radius ({@link #depth}). The top lies in {@code top_y}, the tip at
 *   or above {@link IsleSettings#TIP_MIN}, the tip at least {@code min_clearance} above the terrain at the centre and the
 *   underside at least that much above the terrain sampled under the body. A main isle with no room ends the start (high
 *   TFC mountains); a satellite with no room is left out.</li>
 *   <li>Isles from {@code lode_from} up carry the lode. The main isle carries the Wind Shrine with
 *   {@code wind_shrine_chance} (a hash of seed and chunk, S6B); the fallback cluster always does.</li>
 * </ul>
 */
public final class IsleLayout {
    public static final int GAP_MIN = 6;
    public static final int GAP_MAX = 16;
    /** Satellite tops lie within this many blocks of the main isle's top. */
    public static final int SATELLITE_TOP_SPREAD = 14;
    /** On the shrine's isle no tree grows within this distance of the centre (the ruin is at most 11 by 11). */
    public static final int SHRINE_CLEAR = IsleShape.SHRINE_FLAT + 2;

    private IsleLayout() {}

    /** Terrain height (first free block above the surface) at a column. */
    @FunctionalInterface
    public interface HeightSampler {
        int height(int x, int z);
    }

    /** One isle of the horizontal plan. */
    public record Plan(int x, int z, int radius, IsleSize size, long seed, boolean lode, boolean shrine, int trees, int topOffset) {}

    /** The depth of the underside tip below the top for a radius. */
    public static int depth(int radius) {
        return Math.round(radius * 1.15F) + 4;
    }

    public static long mix(long a, long b) {
        long z = a * 0x9E3779B97F4A7C15L + b;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The seed of the cluster that starts in chunk ({@code chunkX}, {@code chunkZ}). */
    public static long clusterSeed(long worldSeed, int chunkX, int chunkZ) {
        return mix(mix(worldSeed ^ 0x1E5D151E5L, chunkX), chunkZ);
    }

    /** Whether the cluster of that chunk carries the Wind Shrine (S6B): a pure hash, the same for locate and generation. */
    public static boolean shrineRoll(long worldSeed, int chunkX, int chunkZ, float chance) {
        if (chance <= 0) return false;
        long h = mix(clusterSeed(worldSeed, chunkX, chunkZ), 0x5A1E5L);
        return (h >>> 11) * 0x1.0p-53 < chance;
    }

    /**
     * The horizontal plan of a cluster around ({@code centreX}, {@code centreZ}).
     *
     * @param shrine true when the main isle carries the Wind Shrine
     */
    public static List<Plan> plan(long clusterSeed, int centreX, int centreZ, IsleSettings s, boolean shrine) {
        Random r = new Random(clusterSeed);
        int total = s.cluster().min() + r.nextInt(s.cluster().max() - s.cluster().min() + 1);
        IsleSize mainSize = pickSize(r, s);
        List<Plan> out = new ArrayList<>();
        out.add(isle(r, s, centreX, centreZ, mainSize, shrine));
        int tries = 0;
        while (out.size() < total && tries++ < 12) {
            IsleSettings.Range range = s.radius(IsleSize.SMALL);
            int rad = range.min() + r.nextInt(range.max() - range.min() + 1);
            double angle = r.nextDouble() * Math.PI * 2;
            Plan main = out.get(0);
            int dist = main.radius() + rad + GAP_MIN + r.nextInt(GAP_MAX - GAP_MIN + 1);
            int x = centreX + (int) Math.round(Math.cos(angle) * dist);
            int z = centreZ + (int) Math.round(Math.sin(angle) * dist);
            int topOffset = r.nextInt(2 * SATELLITE_TOP_SPREAD + 1) - SATELLITE_TOP_SPREAD;
            long seed = r.nextLong();
            int trees = Math.min(r.nextInt(2), s.treesMax()); // a satellite holds at most one tree
            boolean free = true;
            for (Plan p : out) {
                if (Math.hypot(p.x() - x, p.z() - z) < p.radius() + rad + GAP_MIN) free = false;
            }
            if (!free) continue;
            out.add(new Plan(x, z, rad, IsleSize.SMALL, seed, s.lodeFrom() == IsleSize.SMALL, false, trees, topOffset));
        }
        return out;
    }

    private static Plan isle(Random r, IsleSettings s, int x, int z, IsleSize size, boolean shrine) {
        IsleSettings.Range range = s.radius(size);
        int rad = range.min() + r.nextInt(range.max() - range.min() + 1);
        if (shrine) rad = Math.max(rad, IsleShape.SHRINE_MIN_RADIUS); // the ruin needs a flat top of 15 blocks
        long seed = r.nextLong();
        int trees = r.nextInt(s.treesMax() + 1);
        if (shrine && rad < SHRINE_CLEAR + 4) trees = 0; // the ruin takes the whole top of a small isle
        return new Plan(x, z, rad, size, seed, size.ordinal() >= s.lodeFrom().ordinal(), shrine, trees, 0);
    }

    private static IsleSize pickSize(Random r, IsleSettings s) {
        int total = s.weightSmall() + s.weightMedium() + s.weightLarge();
        int roll = r.nextInt(total);
        if (roll < s.weightSmall()) return IsleSize.SMALL;
        if (roll < s.weightSmall() + s.weightMedium()) return IsleSize.MEDIUM;
        return IsleSize.LARGE;
    }

    /**
     * The isles of a plan with their heights, the main isle first. Empty when the main isle has no room in the band
     * above its terrain.
     */
    public static List<IsleSpec> resolve(List<Plan> plans, IsleSettings s, HeightSampler terrain) {
        List<IsleSpec> out = new ArrayList<>();
        if (plans.isEmpty()) return out;
        int mainTop = 0;
        for (int i = 0; i < plans.size(); i++) {
            Plan p = plans.get(i);
            int depth = depth(p.radius());
            int lo = lowestTop(p, depth, s, terrain);
            int hi = s.topY().max();
            if (lo > hi) {
                if (i == 0) return out;
                continue;
            }
            int top;
            if (i == 0) {
                Random r = new Random(mix(p.seed(), 0x70F));
                top = lo + r.nextInt(hi - lo + 1);
                mainTop = top;
            } else {
                top = Math.max(lo, Math.min(hi, mainTop + p.topOffset()));
            }
            out.add(new IsleSpec(p.x(), p.z(), p.radius(), p.size(), top, depth, p.seed(), p.lode(), p.shrine(), p.trees(),
                IsleSpec.crystalsFor(p.radius(), s.crystalsPer100Top()), false));
        }
        return new ArrayList<>(IsleRuinSite.mark(out));
    }

    /** The lowest top surface the band, the cloud margin and the terrain allow for an isle. */
    static int lowestTop(Plan p, int depth, IsleSettings s, HeightSampler terrain) {
        int lo = Math.max(s.topY().min(), IsleSettings.TIP_MIN + depth);
        lo = Math.max(lo, terrain.height(p.x(), p.z()) + s.minClearance() + depth);
        int under = Integer.MIN_VALUE;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            for (double f : new double[] {0.55, 1.0}) {
                int x = p.x() + (int) Math.round(Math.cos(a) * p.radius() * f);
                int z = p.z() + (int) Math.round(Math.sin(a) * p.radius() * f);
                // the underside at that distance lies IsleShape.underside(depth, f) below the top
                under = Math.max(under, terrain.height(x, z) + IsleShape.underside(depth, f));
            }
        }
        return Math.max(lo, under + s.minClearance() + 2);
    }

    /** True when the column lies within the reach of the cluster whose centre is ({@code cx}, {@code cz}). */
    public static boolean withinReach(int cx, int cz, int x, int z, IsleSettings s) {
        long dx = x - cx;
        long dz = z - cz;
        long r = s.maxExtent();
        return dx * dx + dz * dz <= r * r;
    }
}
