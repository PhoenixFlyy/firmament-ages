package dev.firmages.core.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The rules of {@code /firmages locate} (SPEC §10.3): the radius, the horizontal distance, the order and the cap of the
 * list, structure names and the teleport command. No Minecraft types, so JUnit tests them ({@code LocateRulesTest}).
 */
public final class LocateRules {
    public static final int DEFAULT_RADIUS = 512;
    public static final int MAX_RADIUS = 2048;
    /** Lines after this many are summarised in one "and N more" line. */
    public static final int MAX_LINES = 60;

    /** The y of a place whose height is unknown without loading its chunk. */
    public static final int UNKNOWN_Y = Integer.MIN_VALUE;

    /** One found place: what it is, where (a teleport target; y above the ground, or {@link #UNKNOWN_Y}) and how far. */
    public record Hit(String kind, int x, int y, int z, int distance) {}

    private LocateRules() {}

    public static int clampRadius(int radius) {
        return Math.max(1, Math.min(MAX_RADIUS, radius));
    }

    /** Horizontal distance, rounded. */
    public static int distance(double ox, double oz, int x, int z) {
        double dx = x + 0.5 - ox;
        double dz = z + 0.5 - oz;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    public static boolean within(double ox, double oz, int x, int z, int radius) {
        return distance(ox, oz, x, z) <= radius;
    }

    /** Chunk coordinates whose chunk can hold a point within {@code radius} of {@code (ox, oz)}: [min, max] per axis. */
    public static int[] chunkRange(double ox, double oz, int radius) {
        int x = (int) Math.floor(ox);
        int z = (int) Math.floor(oz);
        return new int[] {(x - radius) >> 4, (x + radius) >> 4, (z - radius) >> 4, (z + radius) >> 4};
    }

    /** Nearest first; equal distances by kind, then position, so the order is stable. */
    public static <T> List<T> sorted(List<T> hits, java.util.function.Function<T, Hit> key) {
        List<T> out = new ArrayList<>(hits);
        out.sort(Comparator.comparingInt((T t) -> key.apply(t).distance()).thenComparing(t -> key.apply(t).kind())
            .thenComparingInt(t -> key.apply(t).x()).thenComparingInt(t -> key.apply(t).z()));
        return out;
    }

    /** "ruin_small" and "wayshrine/forest_ring" become "Ruin Small" and "Forest Ring" (the last path segment). */
    public static String pretty(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder b = new StringBuilder();
        for (String w : last.split("_")) {
            if (w.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return b.toString();
    }

    /**
     * The click command of a line; {@code /tp} and {@code /spreadplayers} need permission 2, as {@code /firmages} itself.
     * Without a known y, {@code /spreadplayers} puts the player on the top block there.
     */
    public static String teleport(int x, int y, int z) {
        return y == UNKNOWN_Y ? "/spreadplayers " + x + " " + z + " 0 1 false @s" : "/tp @s " + x + " " + y + " " + z;
    }

    /** "x y z", or "x ~ z" without a known y. */
    public static String coords(int x, int y, int z) {
        return x + " " + (y == UNKNOWN_Y ? "~" : String.valueOf(y)) + " " + z;
    }

    /** True when {@code (x, z)} lies in the box {@code [minX, maxX] x [minZ, maxZ]} widened by {@code margin}. */
    public static boolean inBox(int x, int z, int minX, int minZ, int maxX, int maxZ, int margin) {
        return x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin;
    }
}
