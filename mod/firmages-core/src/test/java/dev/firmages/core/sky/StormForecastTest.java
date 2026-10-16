package dev.firmages.core.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level U (SPEC §26.10, round H3): the replay of TFC's weather cycle behind {@code sky storm forecast}. */
class StormForecastTest {
    private static final SkyHarvestData.Storm STORM = SkyHarvestData.DEFAULT.storm();

    @Test
    void noRainNoGlass() {
        StormForecast f = StormForecast.replay(STORM, 0, 72_000, 0F, 0F, t -> -1, t -> false);
        assertEquals(0, f.rainTicks());
        assertEquals(0, f.glass(), 1e-9);
    }

    @Test
    void steadyRainGivesTheBaseRate() {
        // a full hour of strong rain: the level passes 0.2 after 21 ticks, then a roll every 20 ticks at 20 / 4,800
        StormForecast f = StormForecast.replay(STORM, 0, 72_000, 0F, 0F, t -> 1.0, t -> false);
        assertEquals(72_000 - 20, f.rainTicks(), 1);
        assertEquals(72_000 / 20.0 * 20 / 4800, f.strikes(), 0.05);
        assertEquals(f.strikes() * 4, f.glass(), 1e-9);
        assertEquals(f.glass(), f.glassPerRealHour(), 1e-9);
    }

    @Test
    void thunderMultiplies() {
        StormForecast rain = StormForecast.replay(STORM, 0, 72_000, 1F, 0F, t -> 1.0, t -> false);
        StormForecast storm = StormForecast.replay(STORM, 0, 72_000, 1F, 1F, t -> 1.0, t -> true);
        assertEquals(4 * rain.strikes(), storm.strikes(), 0.01);
        assertTrue(storm.thunderTicks() > 71_000);
    }

    @Test
    void lightRainBelowTheThresholdIsNotRain() {
        StormForecast f = StormForecast.replay(STORM, 0, 72_000, 0F, 0F, t -> 0.15, t -> false);
        assertEquals(0, f.rainTicks());
    }
}
