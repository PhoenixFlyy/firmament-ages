package dev.firmages.core.gametest;

import dev.firmages.core.sky.ClimateSource;
import net.minecraft.world.level.Level;

/** The GameTest run's climate (no TFC there, SPEC §24.4): fixed temperatures the test sets. */
public final class TestClimateSource implements ClimateSource {
    private volatile float average;
    private volatile float instant;

    public TestClimateSource(float average, float instant) {
        this.average = average;
        this.instant = instant;
    }

    public void set(float average, float instant) {
        this.average = average;
        this.instant = instant;
    }

    @Override
    public float averageTemp(Level level, int x, int y, int z) {
        return average;
    }

    @Override
    public float instantTemp(Level level, int x, int y, int z) {
        return instant;
    }
}
