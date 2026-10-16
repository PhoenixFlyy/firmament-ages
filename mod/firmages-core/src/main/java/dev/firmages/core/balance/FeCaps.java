package dev.firmages.core.balance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The entries of {@code balance.feCap} (SPEC §37): {@code "<block id>=<FE per tick>"}, the most FE one block of that
 * type may give per game tick through the energy capability. Pure Java, unit-tested.
 *
 * @param caps   block id to FE per tick (0 = the block gives nothing)
 * @param errors one line per entry that could not be read (it is ignored)
 */
public record FeCaps(Map<String, Integer> caps, List<String> errors) {
    public static final FeCaps NONE = new FeCaps(Map.of(), List.of());
    private static final Pattern ENTRY = Pattern.compile("\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)\\s*=\\s*(\\d{1,10})\\s*");

    public static FeCaps parse(List<? extends String> entries) {
        Map<String, Integer> caps = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (String e : entries) {
            Matcher m = ENTRY.matcher(e);
            if (!m.matches()) {
                errors.add("'" + e + "' is not <block id>=<FE per tick>");
                continue;
            }
            long n = Long.parseLong(m.group(2));
            if (n > Integer.MAX_VALUE) {
                errors.add("'" + e + "': at most " + Integer.MAX_VALUE + " FE per tick");
                continue;
            }
            if (caps.put(m.group(1), (int) n) != null) errors.add("'" + e + "': " + m.group(1) + " is listed twice, the last entry counts");
        }
        return new FeCaps(Collections.unmodifiableMap(caps), List.copyOf(errors));
    }

    /** True for an entry list the config accepts (each entry a string; bad entries are reported, not refused). */
    public static boolean validEntry(Object o) {
        return o instanceof String;
    }

    /**
     * FE a capped block may still give in {@code tick}: the cap minus what it gave in that tick already.
     *
     * @param usedTick the tick of {@code used}; another tick starts a fresh budget
     */
    public static long room(int cap, long tick, long usedTick, long used) {
        return usedTick == tick ? Math.max(0, cap - used) : cap;
    }
}
