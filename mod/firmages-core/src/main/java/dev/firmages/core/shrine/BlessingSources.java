package dev.firmages.core.shrine;

import java.util.List;

/**
 * The blessing sources (SPEC-himmel §29.5), pure Java so the rules are unit-tested. A source carries a set of awakened
 * tiers (a bit mask, bit k = tier k) into a sphere around a block. The home heart is one source; every lit and intact
 * Wayshrine is one; every Sky Anchor with a shard is one from the Space Age (each registers through
 * {@link BlessingIndex#registerProvider}). A player gets the blessings of the OR of every source in reach.
 * <p>
 * Deviation from the spec's record: the dimension is the level id as a string and the position three ints, so this
 * class needs no Minecraft classes; {@link BlessingIndex} converts.
 */
public final class BlessingSources {
    /** Tiers 0..8 (age_0 .. age_8 rings), as in {@link ShrineData#MAX_TIER}. */
    public static final int TIERS = ShrineData.MAX_TIER + 1;

    private BlessingSources() {}

    public enum Kind { HEART, WAYSHRINE, ANCHOR }

    /**
     * One source.
     *
     * @param dimension level id, e.g. {@code minecraft:overworld}
     * @param radius    blocks from the centre of the source block (inclusive)
     * @param tierMask  the tiers it carries (bit k = tier k); 0 carries nothing
     * @param sanctuary Hearthward: natural monster spawns are refused within the radius
     */
    public record Source(Kind kind, String dimension, int x, int y, int z, int radius, int tierMask, boolean sanctuary) {
        /** True when the point lies within {@link #radius} of the centre of the source block, in the same dimension. */
        public boolean reaches(String dim, double px, double py, double pz) {
            if (!dimension.equals(dim)) return false;
            double dx = px - (x + 0.5);
            double dy = py - (y + 0.5);
            double dz = pz - (z + 0.5);
            return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
        }
    }

    /** The mask of the tiers 0..awakened-1 (awakened = the number of awakened tiers, 0..9). */
    public static int allTiers(int awakened) {
        int n = Math.max(0, Math.min(awakened, TIERS));
        return n == 0 ? 0 : (1 << n) - 1;
    }

    /** The tiers in force at a point: the OR of every source that reaches it. */
    public static int mask(List<Source> sources, String dim, double x, double y, double z) {
        int m = 0;
        for (Source s : sources) {
            if (s.tierMask() != 0 && s.reaches(dim, x, y, z)) m |= s.tierMask();
        }
        return m;
    }

    /** True when a source with Hearthward reaches the point. */
    public static boolean sanctuaryAt(List<Source> sources, String dim, double x, double y, double z) {
        for (Source s : sources) {
            if (s.sanctuary() && s.reaches(dim, x, y, z)) return true;
        }
        return false;
    }

    /**
     * The sources in force: none while the home shrine is not intact or nothing is awakened (a broken ring silences the
     * heart, the Wayshrines and the anchors alike, §29.5), else the candidates with their masks cut to the awakened tiers
     * (an anchor's shard of a sleeping tier does nothing) and without the empty ones.
     */
    public static List<Source> inForce(boolean shrineIntact, int awakened, List<Source> candidates) {
        int awake = allTiers(awakened);
        if (!shrineIntact || awake == 0) return List.of();
        return candidates.stream()
            .map(s -> new Source(s.kind(), s.dimension(), s.x(), s.y(), s.z(), s.radius(), s.tierMask() & awake, s.sanctuary()))
            .filter(s -> s.tierMask() != 0)
            .toList();
    }
}
