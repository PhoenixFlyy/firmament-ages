package dev.firmages.core.age;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global counter bumped after every Age reload (SPEC §4.6). Block entities with private recipe caches compare
 * their stored value against it and drop the cache when it changed.
 */
public final class CacheGeneration {
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private CacheGeneration() {}

    public static int get() {
        return GENERATION.get();
    }

    public static int bump() {
        return GENERATION.incrementAndGet();
    }
}
