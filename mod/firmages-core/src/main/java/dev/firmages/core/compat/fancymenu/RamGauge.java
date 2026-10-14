package dev.firmages.core.compat.fancymenu;

import java.util.function.LongSupplier;

/**
 * The numbers behind the loading screen's RAM line (SPEC §18.7): used heap ({@code totalMemory - freeMemory}), the
 * heap limit ({@code maxMemory}, {@code -Xmx}) and their ratio, in MiB. FancyMenu asks a placeholder every frame;
 * the gauge samples at most every {@link #PERIOD_MILLIS} ms, so the line stays readable while the heap churns.
 */
public final class RamGauge {
    public static final long PERIOD_MILLIS = 250;
    private static final long MIB = 1024L * 1024L;

    /** One sample: used and max in MiB, used in whole percent of max. */
    public record Sample(long usedMb, long maxMb, int usedPct) {}

    private final LongSupplier clock;
    private final LongSupplier total;
    private final LongSupplier free;
    private final LongSupplier max;
    private volatile Sample sample;
    private volatile long sampledAt;

    public RamGauge(LongSupplier clock, LongSupplier total, LongSupplier free, LongSupplier max) {
        this.clock = clock;
        this.total = total;
        this.free = free;
        this.max = max;
    }

    /** The JVM's own heap and the wall clock. */
    public static RamGauge runtime() {
        Runtime rt = Runtime.getRuntime();
        return new RamGauge(System::currentTimeMillis, rt::totalMemory, rt::freeMemory, rt::maxMemory);
    }

    /** The current sample, taken anew when the last one is {@link #PERIOD_MILLIS} ms old or older. */
    public Sample get() {
        long now = clock.getAsLong();
        Sample s = sample;
        if (s == null || now - sampledAt >= PERIOD_MILLIS) {
            s = measure();
            sample = s;
            sampledAt = now;
        }
        return s;
    }

    private Sample measure() {
        long t = total.getAsLong();
        long used = Math.max(0, t - free.getAsLong());
        long m = max.getAsLong();
        if (m <= 0 || m == Long.MAX_VALUE) m = t; // no -Xmx limit: the heap reserved so far is the ceiling
        int pct = m <= 0 ? 0 : (int) Math.min(100, Math.round(used * 100.0 / m));
        return new Sample(used / MIB, m / MIB, pct);
    }
}
