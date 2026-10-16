package dev.firmages.core.sky;

/**
 * The Storm Rod's weather roll (SPEC §26.5). Pure. Every {@link #ROLL_TICKS} ticks while the overworld rains (the
 * global flag; local climate is ignored, so an arid base still gets dry storms) and the sky above the tip is open, a
 * rod is struck with probability {@code ROLL_TICKS / mean_strike_ticks_rain}, times {@code thunder_factor} in thunder.
 * It uses its own roll: vanilla lightning and lightning rods stay as they are.
 */
public final class StormRules {
    public static final int ROLL_TICKS = 20;

    private StormRules() {}

    /** Strike probability of one roll; 0 without rain or without open sky. */
    public static double strikeChance(SkyHarvestData.Storm data, boolean raining, boolean thundering, boolean skyOpen) {
        if (!raining || !skyOpen) return 0;
        double p = (double) ROLL_TICKS / data.meanStrikeTicksRain();
        if (thundering) p *= data.thunderFactor();
        return Math.min(1.0, p);
    }

    /** Sand fused by one strike: {@code sand_per_strike}, at most what the input holds and the output takes. */
    public static int fused(SkyHarvestData.Storm data, int sandAvailable, int outputRoom) {
        return Math.max(0, Math.min(data.sandPerStrike(), Math.min(sandAvailable, outputRoom)));
    }
}
