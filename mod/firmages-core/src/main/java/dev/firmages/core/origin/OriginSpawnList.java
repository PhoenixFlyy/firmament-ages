package dev.firmages.core.origin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The Origin's own spawn list ({@code mob_9}, Doc 08 §5 and §10.3), parsed from {@code data/firmages/origin/spawns.json}.
 * Pure Java (Gson only), so the parser is unit-tested. Format, like a biome's {@code spawners}:
 * <pre>{"spawners": {"monster": [{"type": "minecraft:enderman", "weight": 10, "minCount": 1, "maxCount": 2}]}}</pre>
 * The list replaces whatever biome modifiers would add in The Origin: every category that is not listed spawns
 * nothing there (no passive mobs).
 *
 * @param spawners category name ({@code monster}, {@code creature}, ...) to entries
 */
public record OriginSpawnList(Map<String, List<Entry>> spawners, List<String> errors) {
    public static final OriginSpawnList EMPTY = new OriginSpawnList(Map.of(), List.of());
    /** {@code MobCategory} serialized names of 1.21.1. */
    public static final Set<String> CATEGORIES = Set.of("monster", "creature", "ambient", "axolotls", "underground_water_creature",
        "water_creature", "water_ambient", "misc");

    public record Entry(String type, int weight, int minCount, int maxCount) {}

    public List<Entry> of(String category) {
        return spawners.getOrDefault(category, List.of());
    }

    /** Parses the file; bad entries are skipped one by one and listed in {@link #errors()}. */
    public static OriginSpawnList parse(JsonElement json) {
        List<String> errors = new ArrayList<>();
        Map<String, List<Entry>> out = new TreeMap<>();
        if (!json.isJsonObject() || !json.getAsJsonObject().has("spawners") || !json.getAsJsonObject().get("spawners").isJsonObject()) {
            return new OriginSpawnList(Map.of(), List.of("no 'spawners' object"));
        }
        for (Map.Entry<String, JsonElement> c : json.getAsJsonObject().getAsJsonObject("spawners").entrySet()) {
            if (!CATEGORIES.contains(c.getKey())) {
                errors.add("unknown mob category '" + c.getKey() + "'");
                continue;
            }
            List<Entry> list = new ArrayList<>();
            if (!c.getValue().isJsonArray()) {
                errors.add(c.getKey() + ": not a list");
                continue;
            }
            for (JsonElement e : c.getValue().getAsJsonArray()) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    String type = o.get("type").getAsString().trim();
                    if (!type.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("'" + type + "' is not an entity id");
                    int weight = o.has("weight") ? o.get("weight").getAsInt() : 1;
                    int min = o.has("minCount") ? o.get("minCount").getAsInt() : 1;
                    int max = o.has("maxCount") ? o.get("maxCount").getAsInt() : min;
                    if (weight < 1) throw new IllegalArgumentException(type + ": weight must be >= 1");
                    if (min < 1 || max < min || max > 16) throw new IllegalArgumentException(type + ": counts must be 1 <= minCount <= maxCount <= 16");
                    list.add(new Entry(type, weight, min, max));
                } catch (RuntimeException ex) {
                    errors.add(c.getKey() + ": " + ex.getMessage());
                }
            }
            out.put(c.getKey(), List.copyOf(list));
        }
        return new OriginSpawnList(Map.copyOf(out), List.copyOf(errors));
    }
}
