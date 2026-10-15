package dev.firmages.core.proof;

import dev.firmages.core.age.AgeId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * One team's tally (SPEC §22.1): how many items of each id passed its Tally Chutes, split by the world's Age at the
 * moment they passed. The split lets a later proof ask only for what passed since its Age dawned ({@code age} in the
 * {@code firmages:tally} criterion), so dust counted in the Iron Age does not complete an Industrial proof at once.
 * Pure Java (unit-tested).
 */
public final class TallyCounts {
    private final EnumMap<AgeId, Map<String, Long>> byAge = new EnumMap<>(AgeId.class);

    /** Adds {@code n} items of {@code item} counted while the world stood in {@code age}. */
    public void add(AgeId age, String item, long n) {
        if (n <= 0) return;
        byAge.computeIfAbsent(age, a -> new LinkedHashMap<>()).merge(item, n, Long::sum);
    }

    /** Items whose id matches {@code item}, counted in {@code from} or a later Age; {@code from} null for every Age. */
    public long sum(Predicate<String> item, AgeId from) {
        long total = 0;
        for (Map.Entry<AgeId, Map<String, Long>> e : byAge.entrySet()) {
            if (from != null && e.getKey().compareTo(from) < 0) continue;
            for (Map.Entry<String, Long> c : e.getValue().entrySet()) {
                if (item.test(c.getKey())) total += c.getValue();
            }
        }
        return total;
    }

    /** Every item of this id, all Ages. */
    public long total(String item) {
        return sum(item::equals, null);
    }

    public boolean isEmpty() {
        return byAge.isEmpty();
    }

    /** Read-only view, Age order (for saving and the status command). */
    public Map<AgeId, Map<String, Long>> view() {
        return Collections.unmodifiableMap(byAge);
    }
}
