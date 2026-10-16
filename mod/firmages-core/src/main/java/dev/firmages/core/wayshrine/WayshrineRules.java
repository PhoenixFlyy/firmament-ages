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
    /** The bearings of one search pass, a 32nd of a turn apart. */
    public static final int TUTORIAL_BEARINGS = 32;
    /** The distances tried on each bearing, from 300 to 800 blocks. */
    public static final int[] TUTORIAL_DISTANCES = {300, 425, 550, 675, 800};
    /** The sites of one search pass: every distance on every bearing (160). */
    public static final int TUTORIAL_SITES = TUTORIAL_BEARINGS * TUTORIAL_DISTANCES.length;
    /**
     * The bearings are visited 13 steps of 32 apart (13 and 32 are coprime, so each bearing comes once): two sites tried
     * one after the other lie on opposite sides of the spawn, not on the same coast or slope.
     */
    public static final int TUTORIAL_BEARING_STRIDE = 13;
    /** A pass that found no site is followed by another one, at a new start bearing, this many ticks later (a quarter day). */
    public static final long TUTORIAL_RETRY_TICKS = 6000L;
    /** At most this many blocks of height spread over the footprint of the tutorial site. */
    public static final int TUTORIAL_MAX_SLOPE = 3;
    /**
     * The tutorial Wayshrine places itself only on a world younger than this many ticks (three days, an hour of play):
     * an older world has a history, and its players may have built near the spawn.
     */
    public static final long TUTORIAL_MAX_WORLD_TICKS = 3 * 24000L;
    /** Blocks around the footprint of a tutorial site that must be free of player blocks and block entities. */
    public static final int TUTORIAL_SITE_MARGIN = 8;

    private WayshrineRules() {}

    /** True while the world is young enough for the tutorial Wayshrine to place itself ({@link #TUTORIAL_MAX_WORLD_TICKS}). */
    public static boolean tutorialWorldFresh(long gameTime) {
        return gameTime <= TUTORIAL_MAX_WORLD_TICKS;
    }

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

    /**
     * The tutorial site {@code i} of a pass ({@code 0 <= i <} {@link #TUTORIAL_SITES}): every distance of one bearing in
     * turn, then the next bearing ({@link #TUTORIAL_BEARING_STRIDE}). The pass starts at {@code startAngle} with the
     * distance {@code firstDistance}; each bearing starts one distance further out, so the first fit site is not always the
     * nearest one. Bearing 0 is north (-z), east is +x.
     */
    public static int[] tutorialPoint(int spawnX, int spawnZ, double startAngle, int firstDistance, int i) {
        int n = TUTORIAL_DISTANCES.length;
        int b = i / n;
        int bearing = (int) ((long) b * TUTORIAL_BEARING_STRIDE % TUTORIAL_BEARINGS);
        double a = startAngle + bearing * (Math.PI * 2 / TUTORIAL_BEARINGS);
        int d = TUTORIAL_DISTANCES[Math.floorMod(firstDistance + b + i % n, n)];
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
