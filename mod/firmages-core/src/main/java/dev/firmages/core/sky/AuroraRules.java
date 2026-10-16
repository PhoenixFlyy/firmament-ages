package dev.firmages.core.sky;

import java.util.Locale;

/**
 * Where and when the aurora shines (SPEC §26.3, Felix S3A). Pure: the caller reads both temperatures through
 * {@link ClimateSource} at {@link SkyHarvestData.Aurora#referenceY()}, never at the net's or the camera's own height,
 * so an isle top does not count as polar and the answer is the same everywhere in a column.
 *
 * <p>Active when it is night ({@code dayTime % 24000} in 13000..23000), the column lies in the cold band (yearly
 * average at the reference height at or below {@code max_average_temp}), the overworld is not raining, and the night
 * roll of the column's 512-block cell is below the chance. The chance is {@code night_chance.cold} in the cold half of
 * the year there (instant temperature at or below the average), else {@code night_chance.warm}; both ship as 1.0, so
 * every clear cold-band night has an aurora, and a net catches {@code cold_half_factor} (2) times as much in the cold
 * half (S3A: "every clear night in the cold band, double in winter"). The operator override {@code on}/{@code off} beats everything but the band.
 */
public final class AuroraRules {
    public static final int NIGHT_START = 13000;
    public static final int NIGHT_END = 23000;
    /** The roll and the intensity are the same for every column of one 512-block cell (x >> 9, z >> 9). */
    public static final int CELL_SHIFT = 9;

    private AuroraRules() {}

    /** {@code /firmages sky aurora on|off|auto}: forced on (band still applies), forced off, or the rules. */
    public enum Override {
        AUTO, ON, OFF;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Override parse(String s) {
            for (Override o : values()) if (o.id().equals(s)) return o;
            return AUTO;
        }
    }

    /**
     * Everything the rule reads.
     *
     * @param dayTime     overworld day time (not mod 24000)
     * @param raining     the overworld's rain flag
     * @param averageTemp yearly average at the reference height
     * @param instantTemp temperature now at the reference height
     */
    public record Input(long seed, Override override, long dayTime, boolean raining, float averageTemp, float instantTemp, int x, int z) {}

    /** The verdict with its parts (the status command prints them). */
    public record Result(boolean active, boolean night, boolean inBand, boolean coldHalf, double chance, double roll, double intensity,
                         boolean raining, Override override) {}

    public static boolean night(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= NIGHT_START && t < NIGHT_END;
    }

    public static Result evaluate(SkyHarvestData.Aurora data, Input in) {
        boolean night = night(in.dayTime());
        boolean inBand = in.averageTemp() <= data.maxAverageTemp();
        boolean coldHalf = in.instantTemp() <= in.averageTemp();
        double chance = coldHalf ? data.chanceCold() : data.chanceWarm();
        long day = Math.floorDiv(in.dayTime(), 24000L);
        long h = hash(in.seed(), day, in.x() >> CELL_SHIFT, in.z() >> CELL_SHIFT);
        double roll = unit(h);
        double intensity = 0.5 + 0.5 * unit(mix(h ^ 0x9E3779B97F4A7C15L));
        boolean active = switch (in.override()) {
            case OFF -> false;
            case ON -> inBand;
            case AUTO -> night && inBand && !in.raining() && roll < chance;
        };
        return new Result(active, night, inBand, coldHalf, chance, roll, intensity, in.raining(), in.override());
    }

    /** The night roll's hash of the seed, the overworld day and the 512-block cell. */
    public static long hash(long seed, long day, int cellX, int cellZ) {
        long h = mix(seed ^ 0xA0761D6478BD642FL);
        h = mix(h ^ day * 0xE7037ED1A0B428DBL);
        h = mix(h ^ (long) cellX * 0x8EBC6AF09C88C6E3L);
        return mix(h ^ (long) cellZ * 0x589965CC75374CC3L);
    }

    /** SplitMix64 finaliser. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The top 53 bits as a double in [0, 1). */
    static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * mB one net adds in one 20-tick step (SPEC §26.4): {@code mb_per_hour * intensity / 50}, times
     * {@code cold_half_factor} in the cold half of the year, divided by 1 + the other nets within {@code share_radius}.
     */
    public static double fillPerStep(SkyHarvestData.Aurora data, double intensity, int otherNets, boolean coldHalf) {
        double season = coldHalf ? data.coldHalfFactor() : 1.0;
        return data.mbPerHour() * season * intensity / 50.0 / (1 + Math.max(0, otherNets));
    }
}
