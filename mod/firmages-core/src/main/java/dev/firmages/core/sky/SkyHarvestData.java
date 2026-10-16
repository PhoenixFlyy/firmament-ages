package dev.firmages.core.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The balance values of the Sky Harvest, {@code data/<ns>/firmages_sky/sky_harvest.json} (SPEC §26.6). Pure: parsed
 * from Gson, no Minecraft types. A file with a value out of range is rejected as a whole ({@link #errors()} not empty);
 * the loader then keeps {@link #DEFAULT} (the shipped values) and logs an ERROR, so nets and rods never stop because of
 * a bad override.
 */
public record SkyHarvestData(Aurora aurora, Storm storm, List<String> errors) {
    /**
     * The aurora (SPEC §26.3, §26.4).
     *
     * @param minY           a net catches only at or above this height
     * @param referenceY     both temperatures are read at this height, never at the net's or the camera's
     * @param maxAverageTemp the cold band: yearly average at {@code referenceY} at or below this (°C)
     * @param chanceCold     night chance in the cold half of the year there (instant at or below the average)
     * @param chanceWarm     night chance in the warm half
     * @param mbPerHour      mB per in-game hour (1,000 ticks) at full intensity, in the warm half of the year
     * @param coldHalfFactor the fill is multiplied by this in the cold half of the year (S3A: double in winter)
     * @param netCapacity    tank of one net (mB)
     * @param shareRadius    nets within this many blocks (cube) share the light
     * @param colors         ribbon colours (RGB), kept for a renderer and the data contract
     */
    public record Aurora(int minY, int referenceY, double maxAverageTemp, double chanceCold, double chanceWarm, double mbPerHour,
                         double coldHalfFactor, int netCapacity, int shareRadius, List<Integer> colors) {}

    /**
     * The Storm Rod (SPEC §26.5).
     *
     * @param meanStrikeTicksRain mean ticks between strikes on one rod in rain
     * @param thunderFactor       the strike rate is multiplied by this during thunder
     * @param sandPerStrike       sand fused into storm glass by one strike
     * @param input               {@code #tag} or item id the rod takes
     * @param result              item id of the fused result
     */
    public record Storm(int meanStrikeTicksRain, double thunderFactor, int sandPerStrike, String input, String result) {}

    public static final SkyHarvestData DEFAULT = new SkyHarvestData(
        new Aurora(140, 140, -10.0, 1.0, 1.0, 100, 2.0, 4000, 1, List.of(0x3CFF9E, 0x7A5CFF)),
        new Storm(4800, 4, 4, "#c:sands", "firmages:storm_glass"), List.of());

    public boolean valid() {
        return errors.isEmpty();
    }

    /** Parses one file. Every problem is collected; any problem rejects the file. */
    public static SkyHarvestData parse(JsonObject root) {
        List<String> errors = new ArrayList<>();
        JsonObject a = object(root, "aurora", errors);
        int minY = intOr(a, "min_y", errors);
        int refY = intOr(a, "reference_y", errors);
        double maxTemp = doubleOr(a, "max_average_temp", errors);
        JsonObject nc = object(a, "night_chance", errors);
        double cold = doubleOr(nc, "cold", errors);
        double warm = doubleOr(nc, "warm", errors);
        double mbPerHour = doubleOr(a, "mb_per_hour", errors);
        double coldFactor = a.has("cold_half_factor") ? doubleOr(a, "cold_half_factor", errors) : 2.0;
        int capacity = intOr(a, "net_capacity", errors);
        int share = intOr(a, "share_radius", errors);
        List<Integer> colors = new ArrayList<>();
        if (a.has("colors") && a.get("colors").isJsonArray()) {
            for (JsonElement e : (JsonArray) a.get("colors")) {
                String s = e.isJsonPrimitive() ? e.getAsString() : "";
                if (!s.matches("#[0-9A-Fa-f]{6}")) errors.add("aurora.colors: " + e + " is not #RRGGBB");
                else colors.add(Integer.parseInt(s.substring(1), 16));
            }
        }
        if (minY < -64 || minY > 320) errors.add("aurora.min_y " + minY + " outside -64..320");
        if (refY < -64 || refY > 320) errors.add("aurora.reference_y " + refY + " outside -64..320");
        if (maxTemp < -60 || maxTemp > 60) errors.add("aurora.max_average_temp " + maxTemp + " outside -60..60");
        if (cold < 0 || cold > 1) errors.add("aurora.night_chance.cold " + cold + " outside 0..1");
        if (warm < 0 || warm > 1) errors.add("aurora.night_chance.warm " + warm + " outside 0..1");
        if (mbPerHour <= 0 || mbPerHour > 100_000) errors.add("aurora.mb_per_hour " + mbPerHour + " outside 0..100000");
        if (coldFactor < 1 || coldFactor > 16) errors.add("aurora.cold_half_factor " + coldFactor + " outside 1..16");
        if (capacity < 1000 || capacity > 1_000_000) errors.add("aurora.net_capacity " + capacity + " outside 1000..1000000");
        if (share < 0 || share > 8) errors.add("aurora.share_radius " + share + " outside 0..8");

        JsonObject s = object(root, "storm", errors);
        int mean = intOr(s, "mean_strike_ticks_rain", errors);
        double factor = doubleOr(s, "thunder_factor", errors);
        int sand = intOr(s, "sand_per_strike", errors);
        String input = string(s, "input", errors);
        String result = string(s, "result", errors);
        if (mean < 20 || mean > 10_000_000) errors.add("storm.mean_strike_ticks_rain " + mean + " outside 20..10000000");
        if (factor < 1 || factor > 100) errors.add("storm.thunder_factor " + factor + " outside 1..100");
        if (sand < 1 || sand > 64) errors.add("storm.sand_per_strike " + sand + " outside 1..64");
        if (!input.replaceFirst("^#", "").contains(":")) errors.add("storm.input " + input + " is not an id or #tag");
        if (!result.contains(":") || result.startsWith("#")) errors.add("storm.result " + result + " is not an item id");

        if (!errors.isEmpty()) return new SkyHarvestData(DEFAULT.aurora, DEFAULT.storm, List.copyOf(errors));
        return new SkyHarvestData(new Aurora(minY, refY, maxTemp, cold, warm, mbPerHour, coldFactor, capacity, share, List.copyOf(colors)),
            new Storm(mean, factor, sand, input, result), List.of());
    }

    private static JsonObject object(JsonObject o, String key, List<String> errors) {
        if (o.has(key) && o.get(key).isJsonObject()) return o.getAsJsonObject(key);
        errors.add(key + " is missing or not an object");
        return new JsonObject();
    }

    private static int intOr(JsonObject o, String key, List<String> errors) {
        try {
            if (o.has(key)) return o.get(key).getAsInt();
        } catch (RuntimeException e) {
            errors.add(key + " is not a number");
            return 0;
        }
        errors.add(key + " is missing");
        return 0;
    }

    private static double doubleOr(JsonObject o, String key, List<String> errors) {
        try {
            if (o.has(key)) return o.get(key).getAsDouble();
        } catch (RuntimeException e) {
            errors.add(key + " is not a number");
            return 0;
        }
        errors.add(key + " is missing");
        return 0;
    }

    private static String string(JsonObject o, String key, List<String> errors) {
        if (o.has(key) && o.get(key).isJsonPrimitive()) return o.get(key).getAsString();
        errors.add(key + " is missing");
        return "";
    }
}
