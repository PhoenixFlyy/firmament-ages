package dev.firmages.core.compat.fancymenu;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SPEC §18.7: the RAM placeholders' numbers and their 250 ms refresh. */
class RamGaugeTest {
    private static final long MIB = 1024L * 1024L;

    @Test
    void usedMaxAndPercentInMib() {
        RamGauge g = new RamGauge(() -> 0, () -> 6144 * MIB, () -> 2048 * MIB, () -> 8192 * MIB);
        assertEquals(new RamGauge.Sample(4096, 8192, 50), g.get());
    }

    @Test
    void refreshesAtMostEveryPeriod() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicLong free = new AtomicLong(3000 * MIB);
        RamGauge g = new RamGauge(clock::get, () -> 4000 * MIB, free::get, () -> 4000 * MIB);
        assertEquals(1000, g.get().usedMb());
        free.set(1000 * MIB);
        clock.addAndGet(RamGauge.PERIOD_MILLIS - 1);
        assertEquals(1000, g.get().usedMb(), "within the period the old sample stays");
        clock.addAndGet(1);
        assertEquals(3000, g.get().usedMb(), "after the period a new sample");
        assertEquals(75, g.get().usedPct());
    }

    @Test
    void noLimitUsesTheReservedHeap() {
        RamGauge g = new RamGauge(() -> 0, () -> 2048 * MIB, () -> 1024 * MIB, () -> Long.MAX_VALUE);
        assertEquals(new RamGauge.Sample(1024, 2048, 50), g.get());
    }
}
