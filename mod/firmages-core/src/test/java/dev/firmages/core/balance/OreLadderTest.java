package dev.firmages.core.balance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ore ladder of {@code /firmages ore audit} (SPEC §38) on the real metal table, with the L- check's arithmetic. */
class OreLadderTest {
    private static OreLadder ladder;

    @BeforeAll
    static void table() throws IOException {
        Path p = Path.of("../../kubejs/data/firmages/balance/metals.json");
        ladder = new OreLadder(JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    private static JsonObject j(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static double only(Map<String, JsonObject> recipes) {
        List<OreLadder.Measure> m = ladder.measure(recipes);
        assertEquals(1, m.size(), m.toString());
        return m.get(0).factor();
    }

    @Test
    void tfcMeltingOfANormalPieceIsOne() {
        double f = only(Map.of("tfc:heating/normal_hematite", j("""
            {"type":"tfc:heating","ingredient":{"item":"tfc:ore/normal_hematite"},"result_fluid":{"id":"tfc:metal/cast_iron","amount":25},"temperature":1535}""")));
        assertEquals(1.0, f, 1e-9);
    }

    @Test
    void purificationCountsTheInputCountAndTheClumps() {
        double rich = only(Map.of("a", j("""
            {"type":"mekanism:purifying","item_input":{"item":"tfc:ore/rich_hematite","count":1},"chemical_input":{"chemical":"mekanism:oxygen","amount":1},
             "output":{"id":"mekanism:clump_iron","count":1}}""")));
        assertEquals(100.0 / 35, rich, 1e-9);
        double poor = only(Map.of("b", j("""
            {"type":"mekanism:purifying","item_input":{"item":"tfc:ore/poor_hematite","count":7},"chemical_input":{"chemical":"mekanism:oxygen","amount":1},
             "output":{"id":"mekanism:clump_iron","count":3}}""")));
        assertEquals(300.0 / (7 * 15), poor, 1e-9);
    }

    @Test
    void ieCrusherWrapperAndChanceMultiply() {
        double f = only(Map.of("c", j("""
            {"type":"immersiveengineering:crusher","input":{"item":"tfc:ore/normal_hematite"},"energy":3000,
             "result":{"basePredicate":{"item":"firmages:dust/iron"},"count":1},
             "secondaries":[{"output":{"item":"firmages:nugget/iron"},"chance":0.5}]}""")));
        assertEquals((100 + 0.5 * 11.11) / 25, f, 1e-9);
    }

    @Test
    void perAgeTakesTheMetalsFirstAgeAndTheBestTechStation() {
        Map<String, JsonObject> r = new LinkedHashMap<>();
        r.put("heat", j("""
            {"type":"tfc:heating","ingredient":{"item":"tfc:ore/normal_hematite"},"result_fluid":{"id":"tfc:metal/cast_iron","amount":25}}"""));
        r.put("purify", j("""
            {"type":"mekanism:purifying","item_input":{"item":"tfc:ore/normal_hematite","count":1},"output":{"id":"mekanism:clump_iron","count":1}}"""));
        List<OreLadder.Entry> e = ladder.entries(ladder.measure(r));
        assertEquals(2, e.size());
        OreLadder.Entry heat = e.stream().filter(x -> x.station().equals("tfc_melting")).findFirst().orElseThrow();
        assertEquals("age_2", heat.age(), "the crucible is age_0, iron is first processed in age_2");
        Map<String, double[]> perAge = ladder.perAge(e);
        assertFalse(perAge.containsKey("age_1"), "no iron before its first Age");
        assertEquals(1.0, perAge.get("age_5")[1], 1e-9);
        assertEquals(4.0, perAge.get("age_6")[1], 1e-9, "purification of a normal piece: 100 / 25");
        assertTrue(ladder.stepsBack(e).isEmpty());
    }

    @Test
    void aLaterStationThatYieldsLessIsAStepBack() {
        Map<String, JsonObject> r = new LinkedHashMap<>();
        r.put("crush", j("""
            {"type":"create:crushing","ingredients":[{"item":"tfc:ore/normal_hematite"}],"results":[{"id":"firmages:dust/iron","count":1}]}"""));
        r.put("purify", j("""
            {"type":"mekanism:purifying","item_input":{"item":"tfc:ore/normal_hematite","count":2},"output":{"id":"mekanism:clump_iron","count":1}}"""));
        List<String> back = ladder.stepsBack(ladder.entries(ladder.measure(r)));
        assertEquals(1, back.size(), back.toString());
        assertTrue(back.get(0).contains("mek_purifying"));
    }

    @Test
    void anUnlistedTypeThatMultipliesOreIsReported() {
        Map<String, JsonObject> r = Map.of("grid", j("""
            {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"tfc:ore/normal_hematite"}],"result":{"id":"firmages:dust/iron","count":1}}"""));
        List<OreLadder.Measure> u = ladder.unlisted(ladder.measure(r));
        assertEquals(1, u.size());
        assertEquals(4.0, u.get(0).factor(), 1e-9);
    }

    @Test
    void worldDataIsNoProcessing() {
        Map<String, JsonObject> r = Map.of("mix", j("""
            {"type":"immersiveengineering:mineral_mix","ores":[{"output":{"item":"tfc:ore/normal_hematite"},"chance":1}],"spoils":[]}"""));
        assertTrue(ladder.measure(r).isEmpty());
    }
}
