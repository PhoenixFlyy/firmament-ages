package dev.firmages.core.sky;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U (SPEC §26.9): the night window, the band at the reference height (a net at Y 250 above a warm column stays
 * off), the cold and the warm chance, rain, the override, the intensity range, the net's fill and the Storm Rod's
 * rates, the shipped {@code sky_harvest.json} and rejected files. The temperatures come from a fake climate column
 * with TFC's elevation rule (-0.16225 °C per block above Y 63).
 */
class AuroraRulesTest {
    private static final Path FILE = Path.of("src/main/resources/data/firmages/firmages_sky/sky_harvest.json");
    private static final SkyHarvestData DATA = shipped();
    private static final SkyHarvestData.Aurora A = DATA.aurora();
    private static final long NIGHT = 5 * 24000L + 18000;

    private static SkyHarvestData shipped() {
        try {
            return SkyHarvestData.parse(JsonParser.parseString(Files.readString(FILE)).getAsJsonObject());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A fake TFC column: sea-level average {@code seaAvg}, TFC's lapse rate above Y 63 (capped at 17.82 °C). */
    private static float column(float seaAvg, int y) {
        return seaAvg - (float) Math.min(17.82, Math.max(0, y - 63) * 0.16225);
    }

    private static AuroraRules.Result eval(AuroraRules.Override o, long dayTime, boolean rain, float avg, float inst, int x, int z) {
        return AuroraRules.evaluate(A, new AuroraRules.Input(42L, o, dayTime, rain, avg, inst, x, z));
    }

    @Test
    void shippedFileParses() {
        assertTrue(DATA.valid(), "errors: " + DATA.errors());
        assertEquals(SkyHarvestData.DEFAULT.aurora(), A, "the built-in fallback equals the shipped file");
        assertEquals(SkyHarvestData.DEFAULT.storm(), DATA.storm());
        assertEquals(140, A.minY());
        assertEquals(140, A.referenceY());
        assertEquals(2, A.colors().size());
    }

    @Test
    void badFilesAreRejected() throws IOException {
        JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
        o.getAsJsonObject("aurora").getAsJsonObject("night_chance").addProperty("cold", 1.5);
        o.getAsJsonObject("storm").addProperty("thunder_factor", 0);
        o.getAsJsonObject("storm").remove("result");
        SkyHarvestData d = SkyHarvestData.parse(o);
        assertFalse(d.valid());
        assertTrue(d.errors().size() >= 3, "errors: " + d.errors());
        assertEquals(SkyHarvestData.DEFAULT.aurora(), d.aurora(), "a rejected file keeps the built-in values");
        assertFalse(SkyHarvestData.parse(new JsonObject()).valid());
    }

    @Test
    void nightWindow() {
        assertFalse(AuroraRules.night(12999));
        assertTrue(AuroraRules.night(13000));
        assertTrue(AuroraRules.night(22999));
        assertFalse(AuroraRules.night(23000));
        assertTrue(AuroraRules.night(7 * 24000L + 18000));
        assertFalse(AuroraRules.night(7 * 24000L + 6000));
    }

    @Test
    void bandIsReadAtTheReferenceHeight() {
        float seaAvg = 5.0F; // the spawn latitude: -7.5 °C at Y 140, -12.8 °C at Y 250
        assertTrue(column(seaAvg, 250) <= A.maxAverageTemp(), "the isle top alone would count as polar");
        float atRef = column(seaAvg, A.referenceY());
        AuroraRules.Result r = eval(AuroraRules.Override.ON, NIGHT, false, atRef, atRef - 5, 0, 0);
        assertFalse(r.inBand() || r.active(), "a net at Y 250 above a warm column stays off: " + r);
        float cold = column(0.0F, A.referenceY()); // -12.5 °C at Y 140, about 2,000 blocks poleward of spawn
        assertTrue(eval(AuroraRules.Override.ON, NIGHT, false, cold, cold - 5, 0, 0).active(), "a cold column at the reference height");
    }

    @Test
    void chanceColdAndWarmHalf() {
        int coldHits = 0;
        int warmHits = 0;
        int n = 0;
        for (int day = 0; day < 40; day++) {
            for (int cell = 0; cell < 100; cell++) {
                long t = day * 24000L + 18000;
                int x = (cell % 10) * 512;
                int z = (cell / 10) * 512;
                if (eval(AuroraRules.Override.AUTO, t, false, -13F, -18F, x, z).active()) coldHits++;
                if (eval(AuroraRules.Override.AUTO, t, false, -13F, -8F, x, z).active()) warmHits++;
                n++;
            }
        }
        double cold = coldHits / (double) n;
        double warm = warmHits / (double) n;
        assertEquals(0.5, cold, 0.04, "cold half yields every other clear night");
        assertEquals(0.25, warm, 0.04, "warm half yields half as often");
        // one cell agrees across its 512 blocks and the roll is stable for a night
        AuroraRules.Result a = eval(AuroraRules.Override.AUTO, NIGHT, false, -13F, -18F, 1, 1);
        AuroraRules.Result b = eval(AuroraRules.Override.AUTO, NIGHT + 3000, false, -13F, -18F, 511, 300);
        assertEquals(a.roll(), b.roll());
        assertEquals(a.intensity(), b.intensity());
    }

    @Test
    void rainDayAndOverride() {
        // find a cell that rolls under the cold chance tonight
        int x = 0;
        while (!eval(AuroraRules.Override.AUTO, NIGHT, false, -13F, -18F, x, 0).active()) x += 512;
        assertFalse(eval(AuroraRules.Override.AUTO, NIGHT, true, -13F, -18F, x, 0).active(), "rain hides it");
        assertFalse(eval(AuroraRules.Override.AUTO, NIGHT - 12000, false, -13F, -18F, x, 0).active(), "day hides it");
        assertFalse(eval(AuroraRules.Override.AUTO, NIGHT, false, 1F, -8F, x, 0).active(), "too warm");
        assertFalse(eval(AuroraRules.Override.OFF, NIGHT, false, -13F, -18F, x, 0).active(), "off beats everything");
        assertTrue(eval(AuroraRules.Override.ON, NIGHT - 12000, true, -13F, 5F, x + 512, 0).active(), "on beats night, rain and roll");
        assertFalse(eval(AuroraRules.Override.ON, NIGHT, false, 1F, -8F, x, 0).active(), "but not the band");
        assertEquals(AuroraRules.Override.ON, AuroraRules.Override.parse("on"));
        assertEquals(AuroraRules.Override.AUTO, AuroraRules.Override.parse("bogus"));
    }

    @Test
    void intensityRangeAndFill() {
        double min = 1;
        double max = 0;
        for (int i = 0; i < 2000; i++) {
            double v = eval(AuroraRules.Override.ON, i * 24000L + 18000, false, -3F, -8F, i * 37, -i * 11).intensity();
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        assertTrue(min >= 0.5 && max <= 1.0 && min < 0.55 && max > 0.95, "intensity 0.5..1.0: " + min + ".." + max);
        assertEquals(2.0, AuroraRules.fillPerStep(A, 1.0, 0), 1e-9, "100 mB per hour = 2 mB per 20 ticks");
        assertEquals(1.0, AuroraRules.fillPerStep(A, 1.0, 1), 1e-9, "two adjacent nets share");
        // a full aurora night (10,000 ticks = 500 steps) at average intensity 0.75: about 750 mB (SPEC §26.4)
        assertEquals(750, 500 * AuroraRules.fillPerStep(A, 0.75, 0), 1e-6);
    }

    @Test
    void stormRates() {
        SkyHarvestData.Storm s = DATA.storm();
        double rain = StormRules.strikeChance(s, true, false, true);
        double thunder = StormRules.strikeChance(s, true, true, true);
        assertEquals(20.0 / 4800, rain, 1e-12);
        assertEquals(4 * rain, thunder, 1e-12);
        assertEquals(0, StormRules.strikeChance(s, false, false, true));
        assertEquals(0, StormRules.strikeChance(s, true, true, false), "no strike under a roof");
        assertEquals(4, StormRules.fused(s, 10, 64));
        assertEquals(3, StormRules.fused(s, 3, 64));
        assertEquals(2, StormRules.fused(s, 10, 2));
        assertEquals(0, StormRules.fused(s, 0, 64));
        // SPEC §26.5: one rain window of about 27 % of a real hour (72,000 ticks) gives about 16 storm glass
        double perHour = 72000 * 0.27 / StormRules.ROLL_TICKS * rain * s.sandPerStrike();
        assertEquals(16.2, perHour, 0.1);
    }
}
