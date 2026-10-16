package dev.firmages.core.sky;

import java.util.function.LongPredicate;
import java.util.function.LongToDoubleFunction;

/**
 * The Storm Rod's yield over a stretch of TerraFirmaCraft's own weather (SPEC §26.10 acceptance 3, round H3). Pure:
 * the caller hands in TFC's rain intensity and thunder flag per calendar tick ({@code ClimateModel.getRain},
 * {@code getThunder}); this replays TFC's weather cycle [verified, javap {@code WeatherHelpers.advanceWeatherCycle},
 * TFC 4.2.11]: the rain and thunder levels move at most 0.01 a tick towards the target (rain: the intensity, below 0
 * for none; thunder: 1 or 0), the world rains while the rain level is above 0.2 and thunders while the thunder level is
 * above 0.9 in rain (vanilla {@code Level.isRaining}, {@code isThundering}). Every {@link StormRules#ROLL_TICKS} ticks a
 * rod under open sky adds its strike chance, so the result is the expected number of strikes, not a roll.
 *
 * @param ticks        ticks replayed
 * @param rainTicks    ticks the world rained
 * @param thunderTicks ticks it thundered
 * @param strikes      expected strikes of one rod under open sky
 * @param glass        expected storm glass of that rod (strikes times {@code sand_per_strike})
 */
public record StormForecast(long ticks, long rainTicks, long thunderTicks, double strikes, double glass) {
    /** Ticks in one real hour at 20 ticks a second. */
    public static final long REAL_HOUR = 72_000L;

    public static StormForecast replay(SkyHarvestData.Storm data, long fromTick, long ticks, float rainLevel, float thunderLevel,
                                       LongToDoubleFunction rain, LongPredicate thunder) {
        long rainT = 0;
        long thunderT = 0;
        double strikes = 0;
        float r = rainLevel;
        float th = thunderLevel;
        for (long i = 0; i < ticks; i++) {
            long t = fromTick + i;
            float rainTarget = (float) rain.applyAsDouble(t);
            float thunderTarget = thunder.test(t) ? 1F : 0F;
            r = unit(r + step(rainTarget - r));
            th = unit(th + step(thunderTarget - th));
            boolean raining = r > 0.2F;
            boolean thundering = raining && th > 0.9F;
            if (raining) rainT++;
            if (thundering) thunderT++;
            if (i % StormRules.ROLL_TICKS == 0) strikes += StormRules.strikeChance(data, raining, thundering, true);
        }
        return new StormForecast(ticks, rainT, thunderT, strikes, strikes * data.sandPerStrike());
    }

    private static float unit(float v) {
        return Math.max(0F, Math.min(1F, v));
    }

    private static float step(float v) {
        return Math.max(-0.01F, Math.min(0.01F, v));
    }

    /** Storm glass per real hour of one rod. */
    public double glassPerRealHour() {
        return ticks == 0 ? 0 : glass * REAL_HOUR / ticks;
    }
}
