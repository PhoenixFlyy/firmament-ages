package dev.firmages.core.isles;

/**
 * One resolved isle (SPEC §30.3): its centre column, radius and size, the height of its top surface and the depth of
 * its underside tip below it, its own seed (the shape, the clusters, the trees), whether it carries the firmament lode
 * and the Wind Shrine (S6B), how many trees grow on it and how many crystal clusters it holds. The shape follows from
 * these values alone ({@link IsleShape}), so a structure piece, the fallback placer and a GameTest draw the same isle.
 */
public record IsleSpec(int x, int z, int radius, IsleSize size, int top, int depth, long seed, boolean lode, boolean shrine, int trees,
                       int crystals, boolean ruin) {
    /** The same isle with or without the cluster's ruin (G3, {@link IsleRuin}). */
    public IsleSpec withRuin(boolean r) {
        return new IsleSpec(x, z, radius, size, top, depth, seed, lode, shrine, trees, crystals, r);
    }

    /** The underside tip (the lode sits there). */
    public int tip() {
        return top - depth;
    }

    /** Crystal clusters for an isle of this radius at {@code per100} clusters per 100 blocks of top surface (at least two). */
    public static int crystalsFor(int radius, float per100) {
        double area = Math.PI * Math.pow(radius * 0.9, 2);
        return Math.max(2, (int) Math.round(area * per100 / 100.0));
    }
}
