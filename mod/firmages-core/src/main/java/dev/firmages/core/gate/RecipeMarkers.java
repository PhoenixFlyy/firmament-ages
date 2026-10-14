package dev.firmages.core.gate;

import dev.firmages.core.age.AgeId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Age markers the pack's recipe scripts declare during a datapack load (SPEC §3.4): {@code FirmAges.stationAge},
 * {@code requireAge} and {@code untilAge}. The scripts add every recipe at every Age and only say which Age it needs;
 * the gate turns the markers into station clauses ({@link GateRules.Stations}). KubeJS runs the scripts at the HEAD of
 * {@code RecipeManager#apply}, the gate's main pass at its TAIL {@link #drain drains} what they declared, so each load
 * starts empty. Thread-safe; pure Java.
 */
public final class RecipeMarkers {

    /** What one load declared. Maps are immutable. */
    public record Snapshot(Map<String, AgeId> stationAges, Map<String, Set<AgeId>> requireAges, Map<String, Set<AgeId>> untilAges,
                           List<String> errors) {
        public static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), List.of());

        public int size() {
            return stationAges.size() + requireAges.size() + untilAges.size();
        }
    }

    private static final Map<String, AgeId> STATIONS = new HashMap<>();
    private static final Map<String, Set<AgeId>> REQUIRE = new HashMap<>();
    private static final Map<String, Set<AgeId>> UNTIL = new HashMap<>();
    private static final List<String> ERRORS = new ArrayList<>();

    private RecipeMarkers() {}

    /** Every recipe whose type or serializer is {@code type} needs {@code age} (the Age of the machine). */
    public static synchronized void stationAge(String type, String age) {
        Optional<AgeId> a = parse("stationAge", type, age);
        if (a.isEmpty()) return;
        AgeId old = STATIONS.put(normalize(type), a.get());
        if (old != null && old != a.get()) ERRORS.add("stationAge(" + type + "): " + old.id() + " replaced by " + a.get().id());
    }

    /** The recipe {@code recipeId} needs {@code age} as well as its outputs' Ages. Several calls add up. */
    public static synchronized void requireAge(String recipeId, String age) {
        parse("requireAge", recipeId, age).ifPresent(a -> REQUIRE.computeIfAbsent(normalize(recipeId), k -> EnumSet.noneOf(AgeId.class)).add(a));
    }

    /** The recipe {@code recipeId} exists only while {@code age} is locked (a later variant replaces it). Several calls: any of them. */
    public static synchronized void untilAge(String recipeId, String age) {
        parse("untilAge", recipeId, age).ifPresent(a -> UNTIL.computeIfAbsent(normalize(recipeId), k -> EnumSet.noneOf(AgeId.class)).add(a));
    }

    /** Hands out what was declared since the last drain and starts empty. */
    public static synchronized Snapshot drain() {
        Snapshot s = new Snapshot(Map.copyOf(STATIONS), freeze(REQUIRE), freeze(UNTIL), List.copyOf(ERRORS));
        STATIONS.clear();
        REQUIRE.clear();
        UNTIL.clear();
        ERRORS.clear();
        return s;
    }

    private static Map<String, Set<AgeId>> freeze(Map<String, Set<AgeId>> m) {
        Map<String, Set<AgeId>> out = new HashMap<>();
        m.forEach((k, v) -> out.put(k, Collections.unmodifiableSet(EnumSet.copyOf(v))));
        return Map.copyOf(out);
    }

    private static Optional<AgeId> parse(String what, String id, String age) {
        if (id == null || OutputSink.normalize(id) == null) {
            ERRORS.add(what + ": invalid id '" + id + "'");
            return Optional.empty();
        }
        Optional<AgeId> a = AgeId.byId(age);
        if (a.isEmpty()) ERRORS.add(what + "(" + id + "): unknown Age '" + age + "'");
        return a;
    }

    private static String normalize(String id) {
        return OutputSink.normalize(id);
    }
}
