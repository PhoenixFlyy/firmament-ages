package dev.firmages.core.hardship;

import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creeping Darkness (SPEC §19.7): the real night table {@code kubejs/data/firmages/balance/nights.json} and the night rules. */
class NightsTableTest {
    private static Nights table;

    @BeforeAll
    static void load() throws IOException {
        Path p = Path.of("../../kubejs/data/firmages/balance/nights.json");
        table = Nights.parse(JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    @Test
    void tableKeepsTheRules() {
        assertEquals(List.of(), table.problems());
        assertEquals(AgeId.all().size(), table.rows().size());
    }

    @Test
    void anchors() {
        Nights.Row dawn = table.row(AgeId.DAWN), last = table.row(AgeId.AGE_9);
        assertEquals(1.0, dawn.health());
        assertEquals(1.0, dawn.damage());
        assertEquals(Map.of(), dawn.gearItems());
        assertEquals(2.5, last.health());
        assertEquals(2.5, last.damage());
        assertEquals(2.0, last.nightSpawn());
        assertEquals("pinnacle", last.apothTier());
        assertEquals("minecraft:netherite_helmet", last.gearItems().get("head"));
        assertEquals("tfc:metal/greaves/black_steel", table.row(AgeId.AGE_5).gearItems().get("legs"));
        assertEquals("tfc:metal/sword/black_steel", table.row(AgeId.AGE_5).gearItems().get("mainhand"));
    }

    @Test
    void noBloodMoonBeforeTheIronAgeAndNoDwellerAfterIt() {
        for (AgeId a : AgeId.all()) {
            Nights.Row r = table.row(a);
            if (a.ordinal() < AgeId.AGE_2.ordinal()) assertEquals(0.0, r.bloodMoonKeep(), a.id());
            // H12: hardship.caveDwellerUntilAge = age_2
            if (a.ordinal() >= AgeId.AGE_2.ordinal()) assertEquals(0.0, r.caveDwellerHunt(), a.id());
        }
    }

    @Test
    void brokenTablesAreCaught() {
        String rows = "{\"ages\":[{\"age\":\"dawn\",\"gear\":\"\",\"gear_chance\":0,\"health\":1.2,\"damage\":1,\"night_spawn\":1,"
            + "\"blood_moon_keep\":0.5,\"super_blood_moon\":false,\"cave_dweller_hunt\":1,\"apoth_tier\":\"haven\",\"why\":\"x\"},"
            + "{\"age\":\"age_0\",\"gear\":\"copper\",\"gear_chance\":0,\"health\":1.1,\"damage\":1,\"night_spawn\":1,"
            + "\"blood_moon_keep\":0,\"super_blood_moon\":false,\"cave_dweller_hunt\":1,\"apoth_tier\":\"mythic\",\"why\":\"y\"}]}";
        List<String> p = Nights.parse(JsonParser.parseString(rows).getAsJsonObject()).problems();
        assertTrue(p.stream().anyMatch(s -> s.startsWith("N-1 no row for age_1")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("N-3 age_0")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("N-4 dawn")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("N-5 dawn")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("N-7 age_0")), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("N-8 age_0")), p.toString());
    }

    @Test
    void darkAndExtras() {
        assertTrue(Nights.dark(0, true, false), "cave by day");
        assertTrue(Nights.dark(15, true, true), "open sky at night");
        assertFalse(Nights.dark(15, true, false), "open sky by day");
        assertFalse(Nights.dark(4, true, false), "under a tree by day");
        assertTrue(Nights.dark(0, false, false), "Nether");
        assertEquals(0, Nights.extraSpawns(1.0, 0.0));
        assertEquals(1, Nights.extraSpawns(1.5, 0.49));
        assertEquals(0, Nights.extraSpawns(1.5, 0.5));
        assertEquals(1, Nights.extraSpawns(2.0, 0.99));
    }

    @Test
    void missingAgeIsNeutral() {
        Nights empty = Nights.parse(new com.google.gson.JsonObject());
        assertEquals(1.0, empty.row(AgeId.AGE_7).health());
        assertEquals(1.0, empty.row(AgeId.AGE_7).caveDwellerHunt());
        assertFalse(empty.problems().isEmpty());
    }
}
