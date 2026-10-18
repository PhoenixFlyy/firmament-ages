package dev.firmages.core.boss;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.EnumMap;
import java.util.Map;

/**
 * The Primordial's ring shots and its close strike, read from {@code data/firmages/balance/boss_shots.json} (round G4,
 * SPEC §39.2; the build copies {@code kubejs/data/firmages/balance/boss_shots.json} into the jar, the pack's datapack copy
 * wins). Pure Java: one {@link Shot} row per {@link ShotAge} with typed accessors over the row's numbers, the firing
 * interval per phase, the close strike and the storm's bolt interval. A missing row or key falls back to the built-in
 * defaults below, so a broken datapack never stops the fight. {@code BossShotsService} caches the server's copy.
 */
public final class BossShots {
    /** Ticks between two shots in phase 1, 2 and 3 (index 0..2). */
    private final int[] interval;
    private final int[] storm;
    private final Melee melee;
    private final Map<ShotAge, Shot> shots = new EnumMap<>(ShotAge.class);

    /** One row: the numbers of a segment's shot. Every accessor reads the row and falls back to the default. */
    public record Shot(ShotAge age, JsonObject raw) {
        public double num(String key, double def) {
            JsonElement e = raw.get(key);
            return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : def;
        }

        public int ticks(String secondsKey) {
            return (int) Math.round(num(secondsKey, 0) * 20);
        }

        public double speed() {
            return num("speed", 1.0);
        }

        public float damage() {
            return (float) num("damage", 6);
        }

        public float radius() {
            return (float) num("radius", 1.5);
        }

        public double knockback() {
            return num("knockback", 0);
        }

        public int count() {
            return (int) num("count", 1);
        }

        public double spreadDeg() {
            return num("spread_deg", 0);
        }

        public int pierce() {
            return (int) num("pierce", 0);
        }

        public int trail() {
            return (int) num("trail", 2);
        }

        public int wellTicks() {
            return (int) num("well_ticks", 0);
        }

        public double wellRadius() {
            return num("well_radius", 0);
        }

        public double pull() {
            return num("pull", 0);
        }

        public double push() {
            return num("push", 0);
        }

        public int chainHops() {
            return (int) num("chain_hops", 0);
        }

        public double chainRange() {
            return num("chain_range", 0);
        }

        public float chainDamage() {
            return (float) num("chain_damage", 0);
        }

        public float implosionDamage() {
            return (float) num("implosion_damage", 0);
        }

        public double implosionRadius() {
            return num("implosion_radius", 0);
        }

        /** The first phase this segment fires in (1..3). */
        public int phaseFrom() {
            return (int) num("phase_from", 1);
        }

        /** The segment fires in one ring cycle of this many (1 = every cycle). */
        public int everyCycles() {
            return Math.max(1, (int) num("every_cycles", 1));
        }

        /**
         * Damage over the hit's lifetime for a target that does not move: the burst, the fire (1 a second), the poison
         * cloud (1 per 25 ticks at amplifier 0, per 12 at 1), the wither (1 per 40 ticks) and the implosion, every
         * shot of a fan counted.
         */
        public double expectedDamage() {
            double d = damage() * count();
            d += num("fire_seconds", 0);
            double cloud = num("cloud_seconds", 0);
            if (cloud > 0 && num("poison", 0) > 0) d += cloud * 20 / (num("poison", 1) >= 2 ? 12 : 25);
            d += num("wither_seconds", 0) * 20 / 40;
            d += implosionDamage();
            return d;
        }
    }

    public record Melee(double range, float damage, double knockback, int cooldownTicks) {}

    private BossShots(int[] interval, int[] storm, Melee melee) {
        this.interval = interval;
        this.storm = storm;
        this.melee = melee;
    }

    /** The built-in table (every row at its defaults, SPEC §39.2); the jar's copy of the JSON overrides it in play. */
    public static BossShots defaults() {
        return parse(new JsonObject());
    }

    public static BossShots parse(Reader reader) {
        return parse(JsonParser.parseReader(reader).getAsJsonObject());
    }

    public static BossShots parse(JsonObject root) {
        int[] interval = phases(root.getAsJsonObject("interval_ticks"), 30, 20, 14);
        int[] storm = phases(root.getAsJsonObject("storm_interval_ticks"), 120, 60, 30);
        JsonObject m = root.has("melee") ? root.getAsJsonObject("melee") : new JsonObject();
        Melee melee = new Melee(number(m, "range", 4.0), (float) number(m, "damage", 10), number(m, "knockback", 2.2),
            (int) number(m, "cooldown_ticks", 40));
        BossShots t = new BossShots(interval, storm, melee);
        JsonObject rows = root.has("shots") ? root.getAsJsonObject("shots") : new JsonObject();
        for (ShotAge a : ShotAge.values()) {
            JsonObject row = rows.has(a.key) && rows.get(a.key).isJsonObject() ? rows.getAsJsonObject(a.key) : defaultRow(a);
            t.shots.put(a, new Shot(a, row));
        }
        return t;
    }

    private static int[] phases(JsonObject o, int p1, int p2, int p3) {
        int[] out = {p1, p2, p3};
        if (o == null) return out;
        for (int i = 0; i < 3; i++) out[i] = Math.max(1, (int) number(o, String.valueOf(i + 1), out[i]));
        return out;
    }

    private static double number(JsonObject o, String key, double def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : def;
    }

    /** The defaults mirror the pack's table, so a world without the datapack still fights the same way. */
    static JsonObject defaultRow(ShotAge a) {
        JsonObject o = new JsonObject();
        switch (a) {
            case STONE -> put(o, "speed", 0.9, "damage", 14, "radius", 2.5, "knockback", 1.6);
            case BRONZE -> put(o, "speed", 1.1, "damage", 9, "radius", 1.2, "fire_seconds", 6);
            case IRON -> put(o, "speed", 1.8, "damage", 20, "radius", 0.8, "pierce", 2);
            case ARCANE -> put(o, "speed", 0.8, "damage", 8, "radius", 1.5, "levitation_seconds", 3, "blindness_seconds", 1.5);
            case INDUSTRIAL -> put(o, "speed", 0.9, "damage", 6, "radius", 1.5, "cloud_seconds", 4, "cloud_radius", 2.5, "slowness", 2, "poison", 1);
            case ELECTRIC -> put(o, "speed", 1.3, "damage", 12, "radius", 1.2, "chain_hops", 2, "chain_range", 6.0, "chain_damage", 7);
            case INFORMATION -> put(o, "speed", 1.2, "damage", 6, "radius", 1.0, "count", 3, "spread_deg", 10, "hunger_seconds", 8, "wither_seconds", 3);
            case SPACE -> put(o, "speed", 1.0, "damage", 8, "radius", 1.5, "well_ticks", 60, "well_radius", 8.0, "pull", 0.35, "push", 1.6);
            case QUANTUM -> put(o, "speed", 1.0, "damage", 10, "radius", 2.0, "freeze_seconds", 2, "slowness_seconds", 3);
            case SINGULARITY -> put(o, "speed", 0.7, "damage", 10, "radius", 1.0, "well_ticks", 60, "well_radius", 10.0, "pull", 0.25,
                "implosion_damage", 40, "implosion_radius", 4.0, "phase_from", 3, "every_cycles", 3);
        }
        return o;
    }

    private static void put(JsonObject o, Object... kv) {
        for (int i = 0; i < kv.length; i += 2) o.addProperty((String) kv[i], ((Number) kv[i + 1]).doubleValue());
    }

    public Shot shot(ShotAge a) {
        return shots.get(a);
    }

    /** Ticks between two shots in {@code phase} (1..3). */
    public int intervalTicks(int phase) {
        return interval[Math.max(0, Math.min(2, phase - 1))];
    }

    /** Ticks between two of the storm's bolts in {@code phase} (1..3). */
    public int stormIntervalTicks(int phase) {
        return storm[Math.max(0, Math.min(2, phase - 1))];
    }

    public Melee melee() {
        return melee;
    }

    /**
     * Damage a second the ring deals in {@code phase} to a player who does not dodge: every segment that fires in the
     * phase, weighted by how often it fires, over the phase's interval.
     */
    public double shotDps(int phase) {
        double perCycle = 0;
        double slots = 0;
        for (ShotAge a : ShotAge.values()) {
            Shot s = shot(a);
            if (s.phaseFrom() > phase) continue;
            double share = 1.0 / s.everyCycles();
            perCycle += s.expectedDamage() * share;
            slots += share;
        }
        return perCycle / (slots * intervalTicks(phase) / 20.0);
    }

    /** The close strike's damage a second for a player within its range. */
    public double meleeDps() {
        return melee.damage() * 20.0 / melee.cooldownTicks();
    }
}
