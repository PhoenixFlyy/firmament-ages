package dev.firmages.core.hardship;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.firmages.core.age.AgeId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Creeping Darkness (SPEC §19.7, S1f): the night table {@code data/firmages/balance/nights.json}, one row per Age. Pure
 * Java (Gson and {@link AgeId} only), so JUnit, the selftest and {@code dev/gen_nights.py} check the same rules N-1 to N-8.
 */
public final class Nights {
    public static final List<String> TIERS = List.of("haven", "frontier", "ascent", "summit", "pinnacle");
    /** Slot name to item id pattern of a TFC metal ({@code %s} = metal). */
    private static final Map<String, String> TFC = slots("tfc:metal/helmet/%s", "tfc:metal/chestplate/%s", "tfc:metal/greaves/%s", "tfc:metal/boots/%s", "tfc:metal/sword/%s");
    private static final Map<String, String> VANILLA = slots("minecraft:%s_helmet", "minecraft:%s_chestplate", "minecraft:%s_leggings", "minecraft:%s_boots", "minecraft:%s_sword");

    public record Row(AgeId age, String gear, double gearChance, double health, double damage, double nightSpawn,
                      double bloodMoonKeep, boolean superBloodMoon, double caveDwellerHunt, String apothTier, String why) {
        /** Slot ({@code head, chest, legs, feet, mainhand}) to item id of the row's gear; empty without gear. */
        public Map<String, String> gearItems() {
            if (gear.isEmpty()) return Map.of();
            Map<String, String> pattern = gear.equals("diamond") || gear.equals("netherite") ? VANILLA : TFC;
            Map<String, String> out = new LinkedHashMap<>();
            pattern.forEach((slot, p) -> out.put(slot, p.formatted(gear)));
            return out;
        }
    }

    private final Map<AgeId, Row> rows;

    private Nights(Map<AgeId, Row> rows) {
        this.rows = rows;
    }

    /** The row of {@code age}; a table without it gives the neutral Dawn values. */
    public Row row(AgeId age) {
        Row r = rows.get(age);
        return r != null ? r : neutral(age);
    }

    public List<Row> rows() {
        return List.copyOf(rows.values());
    }

    public static Row neutral(AgeId age) {
        return new Row(age, "", 0, 1, 1, 1, 0, false, 1, "haven", "");
    }

    public static Nights parse(JsonObject table) {
        Map<AgeId, Row> rows = new EnumMap<>(AgeId.class);
        JsonArray ages = table.has("ages") ? table.getAsJsonArray("ages") : new JsonArray();
        for (JsonElement e : ages) {
            JsonObject o = e.getAsJsonObject();
            Optional<AgeId> age = AgeId.byId(str(o, "age"));
            if (age.isEmpty()) continue;
            rows.put(age.get(), new Row(age.get(), str(o, "gear"), num(o, "gear_chance", 0), num(o, "health", 1), num(o, "damage", 1),
                num(o, "night_spawn", 1), num(o, "blood_moon_keep", 0), o.has("super_blood_moon") && o.get("super_blood_moon").getAsBoolean(),
                num(o, "cave_dweller_hunt", 1), str(o, "apoth_tier"), str(o, "why")));
        }
        return new Nights(rows);
    }

    /** Rules N-1 to N-5 and N-8 of {@code dev/gen_nights.py} (N-6 and N-7 need the pack config and the Age map). Empty = fine. */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        for (AgeId a : AgeId.all()) if (!rows.containsKey(a)) out.add("N-1 no row for " + a.id());
        Row prev = null;
        for (Row r : rows.values()) {
            String a = r.age().id();
            if (r.why().isBlank()) out.add("N-1 " + a + ": no why");
            if (!in(r.gearChance(), 0, 1) || !in(r.bloodMoonKeep(), 0, 1) || !in(r.caveDwellerHunt(), 0, 1)) out.add("N-2 " + a + ": chance outside [0, 1]");
            if (!in(r.health(), 1, 2.5) || !in(r.damage(), 1, 2.5) || !in(r.nightSpawn(), 1, 2)) out.add("N-2 " + a + ": multiplier out of range");
            if (!TIERS.contains(r.apothTier())) out.add("N-8 " + a + ": apoth_tier " + r.apothTier());
            if (r.gear().isEmpty() != (r.gearChance() == 0)) out.add("N-7 " + a + ": gear and gear_chance disagree");
            if (r.age().ordinal() < AgeId.AGE_2.ordinal() && (r.bloodMoonKeep() > 0 || r.superBloodMoon())) out.add("N-5 " + a + ": blood moon before mob_2");
            if (prev != null) {
                if (r.gearChance() < prev.gearChance() || r.health() < prev.health() || r.damage() < prev.damage()
                    || r.nightSpawn() < prev.nightSpawn() || r.bloodMoonKeep() < prev.bloodMoonKeep()
                    || TIERS.indexOf(r.apothTier()) < TIERS.indexOf(prev.apothTier()) || (prev.superBloodMoon() && !r.superBloodMoon())) {
                    out.add("N-3 " + a + ": easier than " + prev.age().id());
                }
            }
            prev = r;
        }
        Row d = rows.get(AgeId.DAWN), s = rows.get(AgeId.AGE_9);
        if (d != null && (d.health() != 1 || d.damage() != 1 || d.nightSpawn() != 1 || !d.gear().isEmpty())) out.add("N-4 dawn is not neutral");
        if (s != null && (s.health() != 2.5 || s.damage() != 2.5 || s.nightSpawn() != 2)) out.add("N-4 age_9 is not 2.5 / 2.5 / 2.0");
        return out;
    }

    /** The night decision: dark is no sky light at all (caves, the Nether) or the open sky at night. */
    public static boolean dark(int skyLight, boolean hasSkyLight, boolean night) {
        return skyLight == 0 || (hasSkyLight && night);
    }

    /** Extra mobs for one natural dark spawn: {@code floor(nightSpawn - 1)} plus one with the remaining chance ({@code roll} in [0, 1)). */
    public static int extraSpawns(double nightSpawn, double roll) {
        double extra = Math.max(0, nightSpawn - 1);
        int whole = (int) Math.floor(extra);
        return whole + (roll < extra - whole ? 1 : 0);
    }

    private static boolean in(double v, double lo, double hi) {
        return v >= lo && v <= hi;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static double num(JsonObject o, String k, double fallback) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : fallback;
    }

    private static Map<String, String> slots(String head, String chest, String legs, String feet, String hand) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("head", head);
        m.put("chest", chest);
        m.put("legs", legs);
        m.put("feet", feet);
        m.put("mainhand", hand);
        return m;
    }
}
