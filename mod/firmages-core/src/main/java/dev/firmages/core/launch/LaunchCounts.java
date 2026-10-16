package dev.firmages.core.launch;

import java.util.Map;

/** Probe counts of one team by module item id (SPEC §31.6). Pure Java. */
public final class LaunchCounts {
    private LaunchCounts() {}

    /** Flights of {@code module}, or of every module when it is null. */
    public static long sum(Map<String, Long> counts, String module) {
        if (module != null) return counts.getOrDefault(module, 0L);
        long n = 0;
        for (long v : counts.values()) n += v;
        return n;
    }
}
