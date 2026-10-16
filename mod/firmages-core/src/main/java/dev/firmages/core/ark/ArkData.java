package dev.firmages.core.ark;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * The Archipelago's sample rules (SPEC §31.8), one file per island under {@code data/<ns>/firmages_ark/island/}.
 * Pure Java (Gson only), so the parser is unit-tested without a server. A bad file is rejected with an error and its
 * island stays without rules; an entry whose {@code neoforge:conditions} fail is left out and listed in {@code skipped}
 * (the crown entries carry
 * {@code neoforge:item_exists} guards, so the branch works before the other sky milestones ship their items).
 *
 * <pre>
 * { "island": 2,
 *   "flora": { "tag": "firmages:ark/fruit_saplings", "distinct": 4 },          // or a list of options (any one)
 *   "fauna": { "entities": ["tfc:cow", "tfc:goat"] },                          // [] = the island has no animal
 *   "work":  { "type": "kinetic", "block": "create:water_wheel", "rpm": 16 },  // or a list of rules (any one)
 *   "crown": [ { "tag": "firmages:celestial_alloy/age_3", "count": 4 } ],
 *   "stages": ["firmages:ark/age_2/stage_1", "firmages:ark/age_2/stage_2", "firmages:ark/age_2/stage_3"] }
 * </pre>
 */
public record ArkData(Map<Integer, Island> islands, List<String> errors, List<String> warnings, List<String> skipped) {
    public static final ArkData EMPTY = new ArkData(Map.of(), List.of(), List.of(), List.of());
    /** The nine islands, one per shrine Age 0..8. */
    public static final int ISLANDS = 9;
    /** The three groups; each completed one grows the next stage. */
    public static final List<String> GROUPS = List.of("flora", "fauna", "work");
    public static final String CROWN = "crown";
    /** Every name {@code ark grant} accepts. */
    public static final List<String> ALL_GROUPS = List.of("flora", "fauna", "work", CROWN);

    /** Items by tag ({@code tag} set) or by id ({@code ids}). */
    public record Selector(String tag, List<String> ids) {
        public boolean byTag() {
            return !tag.isEmpty();
        }

        @Override
        public String toString() {
            return byTag() ? "#" + tag : ids.toString();
        }
    }

    /** One flora option: {@code distinct} different item ids, or {@code count} items in all, of {@code what}. */
    public record Flora(Selector what, int distinct, int count) {
        public int needed() {
            return distinct > 0 ? distinct : count;
        }
    }

    public enum WorkType { BLOCKSTATE, ENERGY, KINETIC }

    /**
     * One work rule: a block ({@code block} or {@code tag}) on the island that satisfies it. {@code blockstate}: its
     * property {@code property} has one of {@code values}, or (int properties) at least {@code min}. {@code energy}: it
     * holds at least {@code amount} FE. {@code kinetic}: it turns at {@code rpm} or faster.
     */
    public record Work(WorkType type, String block, String tag, String property, List<String> values, int min, long amount, int rpm) {
        @Override
        public String toString() {
            String what = !block.isEmpty() ? block : "#" + tag;
            return switch (type) {
                case BLOCKSTATE -> what + "[" + property + (values.isEmpty() ? ">=" + min : "=" + String.join("|", values)) + "]";
                case ENERGY -> what + " holding " + amount + " FE";
                case KINETIC -> what + " at " + rpm + " RPM";
            };
        }
    }

    /** One crown entry: {@code count} items of {@code what}. */
    public record CrownEntry(Selector what, int count) {}

    /**
     * The rules of one island. {@code fauna} empty: the island takes no animal, and its fauna group counts as done once
     * the finale is won (the Space and Quantum islands).
     */
    public record Island(int island, List<Flora> flora, List<String> fauna, List<Work> work, List<CrownEntry> crown, List<String> stages) {
        public String age() {
            return "age_" + island;
        }

        public boolean hasFauna() {
            return !fauna.isEmpty();
        }

        public boolean hasCrown() {
            return !crown.isEmpty();
        }
    }

    public Optional<Island> island(int i) {
        return Optional.ofNullable(islands.get(i));
    }

    /**
     * Parses every file (id -> json). {@code conditionsMet} answers an entry's {@code neoforge:conditions} array (the
     * loader checks {@code neoforge:item_exists} and {@code neoforge:mod_loaded}; tests pass their own).
     */
    public static ArkData parse(Map<String, JsonElement> files, Predicate<JsonArray> conditionsMet) {
        Map<Integer, Island> islands = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        new TreeMap<>(files).forEach((id, json) -> {
            try {
                Island isl = parseIsland(json.getAsJsonObject(), conditionsMet, skipped, id);
                if (islands.containsKey(isl.island())) {
                    warnings.add(id + ": island " + isl.island() + " is defined again; this file replaces the earlier one");
                }
                islands.put(isl.island(), isl);
            } catch (RuntimeException e) {
                errors.add(id + ": " + e.getMessage());
            }
        });
        return new ArkData(Map.copyOf(islands), List.copyOf(errors), List.copyOf(warnings), List.copyOf(skipped));
    }

    static Island parseIsland(JsonObject o, Predicate<JsonArray> conditionsMet, List<String> skipped, String id) {
        if (!o.has("island")) throw new IllegalArgumentException("'island' (0.." + (ISLANDS - 1) + ") is missing");
        int island = o.get("island").getAsInt();
        if (island < 0 || island >= ISLANDS) throw new IllegalArgumentException("'island' must be 0.." + (ISLANDS - 1) + ", was " + island);
        List<Flora> flora = new ArrayList<>();
        for (JsonObject f : objects(o.get("flora"), "flora")) {
            Selector s = selector(f, "flora");
            int distinct = f.has("distinct") ? f.get("distinct").getAsInt() : 0;
            int count = f.has("count") ? f.get("count").getAsInt() : 0;
            if ((distinct > 0) == (count > 0)) throw new IllegalArgumentException("a flora option needs either 'distinct' or 'count' (>= 1)");
            flora.add(new Flora(s, distinct, count));
        }
        if (flora.isEmpty()) throw new IllegalArgumentException("'flora' needs at least one option");
        List<String> fauna = new ArrayList<>();
        if (o.has("fauna")) {
            JsonObject fo = o.getAsJsonObject("fauna");
            if (fo.has("entities")) for (JsonElement e : fo.getAsJsonArray("entities")) fauna.add(e.getAsString());
        }
        List<Work> work = new ArrayList<>();
        for (JsonObject w : objects(o.get("work"), "work")) work.add(parseWork(w));
        if (work.isEmpty()) throw new IllegalArgumentException("'work' needs at least one rule");
        List<CrownEntry> crown = new ArrayList<>();
        if (o.has("crown")) {
            for (JsonElement e : o.getAsJsonArray("crown")) {
                JsonObject c = e.getAsJsonObject();
                if (c.has("neoforge:conditions") && !conditionsMet.test(c.getAsJsonArray("neoforge:conditions"))) {
                    skipped.add(id + ": crown entry " + c.get(c.has("tag") ? "tag" : "item") + " left out (its conditions are not met)");
                    continue;
                }
                int count = c.has("count") ? c.get("count").getAsInt() : 1;
                if (count < 1) throw new IllegalArgumentException("a crown entry needs 'count' >= 1");
                crown.add(new CrownEntry(selector(c, "crown"), count));
            }
        }
        List<String> stages = new ArrayList<>();
        if (o.has("stages")) for (JsonElement e : o.getAsJsonArray("stages")) stages.add(e.getAsString());
        if (stages.size() != GROUPS.size()) throw new IllegalArgumentException("'stages' needs " + GROUPS.size() + " templates, one per group, had " + stages.size());
        return new Island(island, List.copyOf(flora), List.copyOf(fauna), List.copyOf(work), List.copyOf(crown), List.copyOf(stages));
    }

    private static Work parseWork(JsonObject w) {
        String typeName = w.has("type") ? w.get("type").getAsString() : "";
        WorkType type;
        try {
            type = WorkType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown work type '" + typeName + "' (blockstate, energy, kinetic)");
        }
        String block = w.has("block") ? w.get("block").getAsString() : "";
        String tag = w.has("tag") ? stripHash(w.get("tag").getAsString()) : "";
        if (block.isEmpty() == tag.isEmpty()) throw new IllegalArgumentException("a work rule needs either 'block' or 'tag'");
        String property = w.has("property") ? w.get("property").getAsString() : "";
        List<String> values = new ArrayList<>();
        if (w.has("value")) values.add(w.get("value").getAsString());
        if (w.has("values")) for (JsonElement e : w.getAsJsonArray("values")) values.add(e.getAsString());
        int min = w.has("min") ? w.get("min").getAsInt() : 0;
        long amount = w.has("amount") ? w.get("amount").getAsLong() : 1;
        int rpm = w.has("rpm") ? w.get("rpm").getAsInt() : 0;
        switch (type) {
            case BLOCKSTATE -> {
                if (property.isEmpty() || (values.isEmpty() && min < 1)) {
                    throw new IllegalArgumentException("a blockstate rule needs 'property' and 'value', 'values' or 'min' >= 1");
                }
            }
            case ENERGY -> {
                if (amount < 1) throw new IllegalArgumentException("an energy rule needs 'amount' >= 1");
            }
            case KINETIC -> {
                if (rpm < 1) throw new IllegalArgumentException("a kinetic rule needs 'rpm' >= 1");
            }
        }
        return new Work(type, block, tag, property, List.copyOf(values), min, amount, rpm);
    }

    private static Selector selector(JsonObject o, String what) {
        String tag = o.has("tag") ? stripHash(o.get("tag").getAsString()) : "";
        List<String> ids = new ArrayList<>();
        if (o.has("item")) ids.add(o.get("item").getAsString());
        if (o.has("items")) for (JsonElement e : o.getAsJsonArray("items")) ids.add(e.getAsString());
        if (tag.isEmpty() == ids.isEmpty()) throw new IllegalArgumentException("a " + what + " entry needs either 'tag' or 'item'/'items'");
        return new Selector(tag, List.copyOf(ids));
    }

    private static List<JsonObject> objects(JsonElement e, String what) {
        if (e == null || e.isJsonNull()) return List.of();
        List<JsonObject> out = new ArrayList<>();
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsJsonObject());
        } else if (e.isJsonObject()) {
            out.add(e.getAsJsonObject());
        } else {
            throw new IllegalArgumentException("'" + what + "' must be an object or a list of objects");
        }
        return out;
    }

    private static String stripHash(String s) {
        return s.startsWith("#") ? s.substring(1) : s;
    }
}
