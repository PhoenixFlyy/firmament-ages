package dev.firmages.core.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.firmages.core.age.AgeId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The balance values of Starfall, {@code data/<ns>/firmages_sky/starfall.json} (SPEC §25.7). Pure: parsed from Gson,
 * no Minecraft types. A file with an unknown metal, size or Age, or a value out of range, is rejected as a whole
 * ({@link #errors()} not empty); the loader then keeps no Starfall data (no falls) and logs an ERROR, as the shrine
 * data loader does.
 */
public record StarfallData(int pityNights, Map<AgeId, AgeEntry> ages, Map<String, Size> sizes, Map<String, Metal> metals,
                           double oldCraterChance, List<String> errors) {
    public static final String OLD_SIZE = "old";
    public static final String METEORIC_IRON = "meteoric_iron";
    public static final String STARSTEEL = "starsteel";
    public static final StarfallData EMPTY = new StarfallData(0, Map.of(), Map.of(), Map.of(), 0, List.of());

    /** One Age row: the chance of a fall on a clear night, the distance range from the anchor, the size and the metal. */
    public record AgeEntry(AgeId age, double chance, int minDistance, int maxDistance, String size, String metal) {}

    /** A crater size: bowl radius and depth, and the meteorite count range of its cluster. */
    public record Size(String id, int radius, int depth, int minMeteorites, int maxMeteorites) {}

    /** A metal: the weight of each meteorite block in its cluster (block id -> weight) and its IE mineral mix. */
    public record Metal(String id, Map<String, Double> mass, String mix) {}

    public boolean valid() {
        return errors.isEmpty() && !ages.isEmpty();
    }

    /**
     * The row that holds in {@code current}: the Age's own row or the latest earlier one (age_8 and age_9 use age_7);
     * empty before the first row (Dawn to age_1 have no live falls).
     */
    public Optional<AgeEntry> entryFor(AgeId current) {
        AgeEntry best = null;
        for (AgeId a : AgeId.all()) {
            if (a.ordinal() > current.ordinal()) break;
            AgeEntry e = ages.get(a);
            if (e != null) best = e;
        }
        return Optional.ofNullable(best);
    }

    public Optional<Size> size(String id) {
        return Optional.ofNullable(sizes.get(id));
    }

    public Optional<Metal> metal(String id) {
        return Optional.ofNullable(metals.get(id));
    }

    /** Parses one file. Every problem is collected; any problem rejects the file. */
    public static StarfallData parse(JsonObject root) {
        List<String> errors = new ArrayList<>();
        int pity = intOr(root, "pity_nights", 8, errors);
        if (pity < 1) errors.add("pity_nights must be at least 1");

        Map<String, Size> sizes = new LinkedHashMap<>();
        JsonObject so = objectOr(root, "sizes", errors);
        for (Map.Entry<String, JsonElement> e : so.entrySet()) {
            if (!e.getValue().isJsonObject()) {
                errors.add("size " + e.getKey() + " is not an object");
                continue;
            }
            JsonObject s = e.getValue().getAsJsonObject();
            int radius = intOr(s, "radius", -1, errors);
            int depth = intOr(s, "depth", -1, errors);
            int[] met = range(s, "meteorites", errors);
            if (radius < 2 || radius > 16) errors.add("size " + e.getKey() + ": radius " + radius + " outside 2..16");
            if (depth < 1 || depth > radius) errors.add("size " + e.getKey() + ": depth " + depth + " outside 1..radius");
            if (met[0] < 1 || met[1] < met[0] || met[1] > 64) errors.add("size " + e.getKey() + ": meteorites " + met[0] + ".." + met[1] + " outside 1..64");
            sizes.put(e.getKey(), new Size(e.getKey(), radius, depth, met[0], met[1]));
        }
        if (!sizes.containsKey(OLD_SIZE)) errors.add("size \"old\" is missing (old craters use it)");

        Map<String, Metal> metals = new LinkedHashMap<>();
        JsonObject mo = objectOr(root, "metals", errors);
        for (Map.Entry<String, JsonElement> e : mo.entrySet()) {
            if (!e.getKey().equals(METEORIC_IRON) && !e.getKey().equals(STARSTEEL)) {
                errors.add("unknown metal " + e.getKey() + " (meteoric_iron, starsteel)");
                continue;
            }
            if (!e.getValue().isJsonObject()) {
                errors.add("metal " + e.getKey() + " is not an object");
                continue;
            }
            JsonObject m = e.getValue().getAsJsonObject();
            Map<String, Double> mass = new LinkedHashMap<>();
            JsonObject massObj = objectOr(m, "mass", errors);
            double total = 0;
            for (Map.Entry<String, JsonElement> me : massObj.entrySet()) {
                double w = me.getValue().getAsDouble();
                if (!(w > 0)) errors.add("metal " + e.getKey() + ": weight of " + me.getKey() + " must be positive");
                if (!me.getKey().contains(":")) errors.add("metal " + e.getKey() + ": " + me.getKey() + " is not a block id");
                mass.put(me.getKey(), w);
                total += w;
            }
            if (mass.isEmpty() || !(total > 0)) errors.add("metal " + e.getKey() + " has no meteorite blocks");
            String mix = m.has("mix") ? m.get("mix").getAsString() : "";
            if (!mix.contains(":")) errors.add("metal " + e.getKey() + ": mix " + mix + " is not an id");
            metals.put(e.getKey(), new Metal(e.getKey(), Collections.unmodifiableMap(mass), mix));
        }
        if (!metals.containsKey(METEORIC_IRON)) errors.add("metal meteoric_iron is missing (old craters use it)");

        Map<AgeId, AgeEntry> ages = new LinkedHashMap<>();
        JsonObject ao = objectOr(root, "ages", errors);
        for (Map.Entry<String, JsonElement> e : ao.entrySet()) {
            Optional<AgeId> age = AgeId.byId(e.getKey());
            if (age.isEmpty() || !e.getKey().equals(age.get().id())) {
                errors.add("unknown Age " + e.getKey());
                continue;
            }
            if (!e.getValue().isJsonObject()) {
                errors.add("Age " + e.getKey() + " is not an object");
                continue;
            }
            JsonObject a = e.getValue().getAsJsonObject();
            double chance = a.has("chance") ? a.get("chance").getAsDouble() : -1;
            int[] dist = range(a, "distance", errors);
            String size = a.has("size") ? a.get("size").getAsString() : "";
            String metal = a.has("metal") ? a.get("metal").getAsString() : "";
            if (chance < 0 || chance > 1) errors.add("Age " + e.getKey() + ": chance " + chance + " outside 0..1");
            if (dist[0] < 16 || dist[1] < dist[0] || dist[1] > 10000) errors.add("Age " + e.getKey() + ": distance " + dist[0] + ".." + dist[1] + " outside 16..10000");
            if (!sizes.containsKey(size)) errors.add("Age " + e.getKey() + ": unknown size " + size);
            if (!metals.containsKey(metal)) errors.add("Age " + e.getKey() + ": unknown metal " + metal);
            ages.put(age.get(), new AgeEntry(age.get(), chance, dist[0], dist[1], size, metal));
        }
        if (ages.isEmpty()) errors.add("no Age has a fall");

        double oldChance = 0;
        if (root.has("old_craters")) {
            JsonObject oc = root.getAsJsonObject("old_craters");
            oldChance = oc.has("chance") ? oc.get("chance").getAsDouble() : 0;
            if (oldChance < 0 || oldChance > 1) errors.add("old_craters.chance " + oldChance + " outside 0..1");
        }
        if (!errors.isEmpty()) return new StarfallData(0, Map.of(), Map.of(), Map.of(), 0, List.copyOf(errors));
        return new StarfallData(pity, Collections.unmodifiableMap(ages), Collections.unmodifiableMap(sizes), Collections.unmodifiableMap(metals),
            oldChance, List.of());
    }

    private static int intOr(JsonObject o, String key, int fallback, List<String> errors) {
        if (!o.has(key)) {
            errors.add(key + " is missing");
            return fallback;
        }
        try {
            return o.get(key).getAsInt();
        } catch (RuntimeException e) {
            errors.add(key + " is not a number");
            return fallback;
        }
    }

    private static JsonObject objectOr(JsonObject o, String key, List<String> errors) {
        if (o.has(key) && o.get(key).isJsonObject()) return o.getAsJsonObject(key);
        errors.add(key + " is missing or not an object");
        return new JsonObject();
    }

    private static int[] range(JsonObject o, String key, List<String> errors) {
        if (!o.has(key) || !o.get(key).isJsonArray() || o.getAsJsonArray(key).size() != 2) {
            errors.add(key + " must be a pair [min, max]");
            return new int[] {-1, -1};
        }
        JsonArray arr = o.getAsJsonArray(key);
        return new int[] {arr.get(0).getAsInt(), arr.get(1).getAsInt()};
    }
}
