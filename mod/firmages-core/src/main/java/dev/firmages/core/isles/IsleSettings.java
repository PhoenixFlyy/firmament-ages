package dev.firmages.core.isles;

/**
 * The isle fields of {@code data/firmages/worldgen/structure/drifting_isle.json} (SPEC §30.3): the band of the top
 * surface, the clearance above the terrain, the cluster size, the radius range and weight of each size, the crystal
 * density, the size from which an isle carries a lode, the trees and the Wind Shrine chance (S6B). The fallback cluster
 * of existing worlds reads the same values from the registered structure. Plain values (the unit tests run without the
 * codec library); the JSON codec is {@link IsleCodecs#SETTINGS}.
 */
public record IsleSettings(Range topY, int minClearance, Range cluster, Range small, Range medium, Range large,
                           int weightSmall, int weightMedium, int weightLarge, float crystalsPer100Top, IsleSize lodeFrom,
                           int treesMax, float windShrineChance) {
    /** The underside tip stays at or above the cloud layer (Y 192) plus a margin. */
    public static final int TIP_MIN = 200;
    /** Crowns and the shrine stay below this height (19 blocks of headroom for an airship envelope). */
    public static final int CROWN_MAX = 300;

    public record Range(int min, int max) {}

    /** The shipped values (SPEC §30.3), for tests and when the structure is missing from the registry. */
    public static final IsleSettings DEFAULT = new IsleSettings(new Range(228, 272), 40, new Range(1, 4), new Range(8, 12), new Range(13, 18),
        new Range(19, 24), 6, 3, 1, 3.0F, IsleSize.MEDIUM, 2, 0.25F);

    /** Why these values cannot shape an isle, or null when they can (the codec of {@link IsleCodecs} reports it). */
    public String problem() {
        for (Range r : new Range[] {topY, cluster, small, medium, large}) if (r.min > r.max) return "min " + r.min + " above max " + r.max;
        if (cluster.min < 1 || cluster.max > 8) return "cluster must lie within 1..8";
        if (small.min < 4 || large.max > 32) return "isle radii must lie within 4..32";
        if (topY.min < TIP_MIN + 8 || topY.max > CROWN_MAX - IslePiece.HEADROOM) {
            return "top_y must lie within " + (TIP_MIN + 8) + ".." + (CROWN_MAX - IslePiece.HEADROOM);
        }
        if (weightSmall + weightMedium + weightLarge <= 0) return "all size weights are zero";
        return null;
    }

    public Range radius(IsleSize size) {
        return switch (size) {
            case SMALL -> small;
            case MEDIUM -> medium;
            case LARGE -> large;
        };
    }

    /** The widest reach of a cluster from its centre, in blocks (the Starfall probe and the searches use it). */
    public int maxExtent() {
        return large.max + 2 * small.max + IsleLayout.GAP_MAX + 4;
    }
}
