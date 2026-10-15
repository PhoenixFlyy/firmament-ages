package dev.firmages.core.wayshrine;

/**
 * Pure rules of the Wayshrines (SPEC-himmel §28), unit-tested: the pointer's bearing and rounded distance, the tutorial
 * site's ring around the spawn and its terrain test.
 */
public final class WayshrineRules {
    /** The eight bearings, clockwise from north (lang keys {@code firmages.bearing.<id>}). */
    public static final String[] BEARINGS = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
    public static final int TUTORIAL_MIN = 300;
    public static final int TUTORIAL_MAX = 800;
    public static final int TUTORIAL_TRIES = 16;
    /** At most this many blocks of height spread over the footprint of the tutorial site. */
    public static final int TUTORIAL_MAX_SLOPE = 3;

    private WayshrineRules() {}

    /** The bearing from (fx, fz) to (tx, tz); Minecraft's north is -z, east is +x. */
    public static String bearing(double fx, double fz, double tx, double tz) {
        double dx = tx - fx;
        double dz = tz - fz;
        if (dx == 0 && dz == 0) return BEARINGS[0];
        double deg = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        if (deg < 0) deg += 360;
        return BEARINGS[(int) Math.round(deg / 45.0) % 8];
    }

    /** "about N blocks": tens below 100 (at least ten), fifties below 1,000, hundreds above. */
    public static long roundedDistance(double d) {
        if (d < 100) return Math.max(10, Math.round(d / 10.0) * 10);
        if (d < 1000) return Math.round(d / 50.0) * 50;
        return Math.round(d / 100.0) * 100;
    }

    /** The tutorial candidate {@code i} of {@link #TUTORIAL_TRIES}: a bearing that turns by 1/16 each try. */
    public static int[] tutorialPoint(int spawnX, int spawnZ, double startAngle, double distance, int i) {
        double a = startAngle + i * (Math.PI * 2 / TUTORIAL_TRIES);
        double d = Math.max(TUTORIAL_MIN, Math.min(TUTORIAL_MAX, distance));
        return new int[] {spawnX + (int) Math.round(Math.sin(a) * d), spawnZ - (int) Math.round(Math.cos(a) * d)};
    }

    /** A site is fit when no column of its footprint is under fluid and the height spread is at most three blocks. */
    public static boolean fitSite(int[] heights, boolean anyFluid) {
        if (anyFluid || heights.length == 0) return false;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int h : heights) {
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return max - min <= TUTORIAL_MAX_SLOPE;
    }

    /** The floor height of a fit site: the rounded mean of its surface heights, minus one (start_height -1). */
    public static int floorY(int[] heights) {
        long sum = 0;
        for (int h : heights) sum += h;
        return (int) Math.round((double) sum / heights.length) - 1;
    }
}
