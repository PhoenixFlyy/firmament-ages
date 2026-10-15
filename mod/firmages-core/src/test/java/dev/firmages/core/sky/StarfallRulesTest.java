package dev.firmages.core.sky;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.text.Numbers;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U (SPEC §25.11): the Age table with inheritance, pity, the first-fall guarantee, the site rejection rules with
 * a fake world (isle box included), deterministic old-crater regions, the shipped {@code starfall.json} and rejected
 * files.
 */
class StarfallRulesTest {
    private static final Path FILE = Path.of("src/main/resources/data/firmages/firmages_sky/starfall.json");

    private static JsonObject shipped() throws IOException {
        return JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
    }

    @Test
    void shippedFileParsesWithInheritance() throws IOException {
        StarfallData d = StarfallData.parse(shipped());
        assertTrue(d.valid(), "errors: " + d.errors());
        assertEquals(8, d.pityNights());
        for (AgeId a : List.of(AgeId.DAWN, AgeId.AGE_0, AgeId.AGE_1)) assertTrue(d.entryFor(a).isEmpty(), a + " has no live falls");
        assertEquals("meteoric_iron", d.entryFor(AgeId.AGE_2).orElseThrow().metal());
        assertEquals("small", d.entryFor(AgeId.AGE_3).orElseThrow().size());
        assertEquals("starsteel", d.entryFor(AgeId.AGE_5).orElseThrow().metal());
        assertEquals(AgeId.AGE_7, d.entryFor(AgeId.AGE_8).orElseThrow().age(), "age_8 inherits age_7");
        assertEquals(AgeId.AGE_7, d.entryFor(AgeId.AGE_9).orElseThrow().age(), "age_9 inherits age_7");
        assertEquals(4, d.size("old").orElseThrow().radius());
        assertEquals("firmages:mineral/starfall", d.metal("starsteel").orElseThrow().mix());
        assertEquals(0.35, d.oldCraterChance(), 1e-9);
    }

    @Test
    void badFilesAreRejected() throws IOException {
        JsonObject unknownMetal = shipped();
        unknownMetal.getAsJsonObject("ages").getAsJsonObject("age_2").addProperty("metal", "mithril");
        assertFalse(StarfallData.parse(unknownMetal).errors().isEmpty());
        JsonObject unknownSize = shipped();
        unknownSize.getAsJsonObject("ages").getAsJsonObject("age_4").addProperty("size", "huge");
        assertFalse(StarfallData.parse(unknownSize).errors().isEmpty());
        JsonObject unknownAge = shipped();
        unknownAge.getAsJsonObject("ages").add("age_12", unknownAge.getAsJsonObject("ages").get("age_2"));
        assertFalse(StarfallData.parse(unknownAge).errors().isEmpty());
        JsonObject noOld = shipped();
        noOld.getAsJsonObject("sizes").remove("old");
        assertFalse(StarfallData.parse(noOld).errors().isEmpty());
        JsonObject badMetalKey = shipped();
        badMetalKey.getAsJsonObject("metals").add("bronze", badMetalKey.getAsJsonObject("metals").get("starsteel"));
        StarfallData rejected = StarfallData.parse(badMetalKey);
        assertFalse(rejected.errors().isEmpty());
        assertFalse(rejected.valid(), "a rejected file gives no data");
        assertTrue(rejected.ages().isEmpty());
    }

    @Test
    void rollOrder() {
        // a guarantee beats everything
        assertEquals(StarfallRules.Reason.GUARANTEED, StarfallRules.roll(true, "meteoric_iron", "meteoric_iron", 0, 8, 0.0, 0.99));
        // the first fall of a new metal (age_2: nothing fell yet; age_5: starsteel after meteoric iron)
        assertEquals(StarfallRules.Reason.FIRST_FALL, StarfallRules.roll(false, "", "meteoric_iron", 0, 8, 0.0, 0.99));
        assertEquals(StarfallRules.Reason.FIRST_FALL, StarfallRules.roll(false, "meteoric_iron", "starsteel", 0, 8, 0.0, 0.99));
        // pity after eight clear nights
        assertEquals(StarfallRules.Reason.NONE, StarfallRules.roll(false, "meteoric_iron", "meteoric_iron", 7, 8, 0.12, 0.99));
        assertEquals(StarfallRules.Reason.PITY, StarfallRules.roll(false, "meteoric_iron", "meteoric_iron", 8, 8, 0.12, 0.99));
        // the chance
        assertEquals(StarfallRules.Reason.CHANCE, StarfallRules.roll(false, "meteoric_iron", "meteoric_iron", 0, 8, 0.12, 0.05));
        assertEquals(StarfallRules.Reason.NONE, StarfallRules.roll(false, "meteoric_iron", "meteoric_iron", 0, 8, 0.12, 0.12));
    }

    /** A fake world: water west of x = -500, a claim around (600, 0), an isle box around (0, 600). */
    private static final StarfallRules.WorldView WORLD = new StarfallRules.WorldView() {
        @Override
        public boolean noImpactBiome(int x, int z) {
            return x < -500;
        }

        @Override
        public boolean claimedNear(int x, int z, int marginChunks) {
            int m = marginChunks * 16;
            return Math.abs(x - 600) <= 16 + m && Math.abs(z) <= 16 + m;
        }

        @Override
        public boolean insideIsle(int x, int z) {
            return Math.abs(x) <= 30 && Math.abs(z - 600) <= 30;
        }
    };

    @Test
    void rejectionRules() {
        StarfallRules.Limits lim = new StarfallRules.Limits(Optional.of(new int[] {0, 0}), 256, List.of(new int[] {1000, 1000}), 128, 2);
        assertEquals("shrine", StarfallRules.reject(200, 0, lim, WORLD));
        assertEquals(null, StarfallRules.reject(0, 300, lim, WORLD));
        assertEquals("spawn", StarfallRules.reject(1050, 1000, lim, WORLD));
        assertEquals("biome", StarfallRules.reject(-600, 0, lim, WORLD));
        assertEquals("isle", StarfallRules.reject(10, 600, lim, WORLD));
        assertEquals("claim", StarfallRules.reject(640, 0, lim, WORLD), "within two chunks of the claim");
        assertEquals(null, StarfallRules.reject(700, 0, lim, WORLD), "beyond the margin");
        StarfallRules.Limits noClaims = new StarfallRules.Limits(Optional.empty(), 256, List.of(), 128, -1);
        assertEquals(null, StarfallRules.reject(600, 0, noClaims, WORLD), "without FTB Chunks the claim rule is off");
        assertEquals(null, StarfallRules.reject(100, 0, noClaims, WORLD), "no heart recorded: no shrine clearance");
    }

    @Test
    void siteChoiceStaysInRangeAndAvoidsRejections() {
        StarfallRules.Limits lim = new StarfallRules.Limits(Optional.of(new int[] {0, 0}), 256, List.of(), 128, 2);
        Random rng = new Random(42);
        for (int i = 0; i < 200; i++) {
            Optional<int[]> s = StarfallRules.chooseSite(0, 0, 300, 800, lim, WORLD, rng);
            assertTrue(s.isPresent());
            double d = Math.hypot(s.get()[0], s.get()[1]);
            assertTrue(d >= 299 && d <= 801, "distance " + d);
            assertEquals(null, StarfallRules.reject(s.get()[0], s.get()[1], lim, WORLD));
        }
        // every column refused: no site after sixteen attempts
        StarfallRules.WorldView allWater = new StarfallRules.WorldView() {
            public boolean noImpactBiome(int x, int z) { return true; }
            public boolean claimedNear(int x, int z, int m) { return false; }
            public boolean insideIsle(int x, int z) { return false; }
        };
        assertTrue(StarfallRules.chooseSite(0, 0, 300, 800, lim, allWater, rng).isEmpty());
    }

    @Test
    void oldCraterRegionsAreDeterministic() {
        int kept = 0;
        for (int rx = -20; rx < 20; rx++) {
            for (int rz = -20; rz < 20; rz++) {
                Optional<int[]> a = StarfallRules.oldCrater(12345L, rx, rz, 1024, 0.35);
                Optional<int[]> b = StarfallRules.oldCrater(12345L, rx, rz, 1024, 0.35);
                assertEquals(a.isPresent(), b.isPresent());
                if (a.isPresent()) {
                    kept++;
                    assertEquals(a.get()[0], b.get()[0]);
                    assertEquals(a.get()[1], b.get()[1]);
                    assertEquals(rx, StarfallRules.region(a.get()[0], 1024), "inside its region");
                    assertEquals(rz, StarfallRules.region(a.get()[1], 1024));
                    assertTrue(Math.floorMod(a.get()[0], 1024) >= 64 && Math.floorMod(a.get()[0], 1024) < 1024 - 64 + 1);
                }
            }
        }
        double share = kept / 1600.0;
        assertTrue(share > 0.28 && share < 0.42, "about 35 % of the regions keep a crater: " + share);
        assertTrue(StarfallRules.oldCrater(12345L, 0, 0, 1024, 0.0).isEmpty(), "chance 0 keeps none");
        int differ = 0;
        for (int rx = 0; rx < 40; rx++) {
            if (StarfallRules.oldCrater(1L, rx, 0, 1024, 0.35).isPresent() != StarfallRules.oldCrater(2L, rx, 0, 1024, 0.35).isPresent()) differ++;
        }
        assertTrue(differ > 0, "another seed gives other regions");
    }

    @Test
    void movesAreSeededAndWithin128() {
        for (int m = 1; m <= 3; m++) {
            int[] a = StarfallRules.moveTarget(100, 200, 77L, m);
            int[] b = StarfallRules.moveTarget(100, 200, 77L, m);
            assertEquals(a[0], b[0]);
            double d = Math.hypot(a[0] - 100, a[1] - 200);
            assertTrue(d >= 47 && d <= 129, "move distance " + d);
        }
    }

    @Test
    void bearingsVolumeAndTimes() {
        assertEquals("north", StarfallRules.bearing(0, 0, 0, -100));
        assertEquals("north_east", StarfallRules.bearing(0, 0, 100, -100));
        assertEquals("east", StarfallRules.bearing(0, 0, 100, 0));
        assertEquals("south", StarfallRules.bearing(0, 0, 0, 100));
        assertEquals("south_west", StarfallRules.bearing(0, 0, -100, 100));
        assertEquals("west", StarfallRules.bearing(0, 0, -100, 0));
        assertEquals("north_west", StarfallRules.bearing(0, 0, -100, -100));
        assertEquals(1.0F, StarfallRules.distantVolume(100), 1e-6);
        assertEquals(1.0F, StarfallRules.distantVolume(256), 1e-6);
        assertEquals(0.25F, StarfallRules.distantVolume(1500), 1e-6);
        assertEquals(0.25F, StarfallRules.distantVolume(5000), 1e-6);
        float mid = StarfallRules.distantVolume(878);
        assertTrue(mid > 0.6 && mid < 0.65, "halfway " + mid);
        assertTrue(StarfallRules.cold(100, 100));
        assertFalse(StarfallRules.cold(99, 100));
        assertEquals(List.of(1, 6), List.of(StarfallRules.daysHours(30000)[0], StarfallRules.daysHours(30000)[1]));
        assertEquals(List.of(0, 1), List.of(StarfallRules.daysHours(10)[0], StarfallRules.daysHours(10)[1]));
        assertEquals(List.of(2, 0), List.of(StarfallRules.daysHours(48000)[0], StarfallRules.daysHours(48000)[1]));
    }

    @Test
    void numbersInPlayerText() {
        assertEquals("zero", Numbers.words(0));
        assertEquals("three", Numbers.words(3));
        assertEquals("twelve", Numbers.words(12));
        assertEquals("13", Numbers.words(13));
        assertEquals("Seven", Numbers.capitalized(7));
        assertEquals("-1", Numbers.words(-1));
    }
}
