package dev.firmages.core.ark;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U (SPEC §31.10): the Archipelago's rules without a world. The shipped island files parse into nine islands
 * with a crown from the Arcane island on; distinct flora ids count once, counts and crowns take only the rest; an
 * island without an animal gets its fauna group only after the finale; three groups grow three stages, the crown none;
 * the nine sockets are distinct cells on the ring; growth runs from the plinth outward; and {@code dev/gen_ark.py --check} is clean.
 */
class ArkRulesTest {
    private static final Path ISLANDS = Path.of("src/main/resources/data/firmages/firmages_ark/island");
    private static final Map<String, Set<String>> TAGS = Map.of(
        "test:saplings", Set.of("minecraft:oak_sapling", "minecraft:birch_sapling", "minecraft:spruce_sapling"),
        "test:alloy", Set.of("test:alloy_ingot"));
    private static final ArkRules.Matcher M = (what, id) -> what.byTag() ? TAGS.getOrDefault(what.tag(), Set.of()).contains(id) : what.ids().contains(id);
    private static final String STAGES = "\"stages\": [\"a:s1\", \"a:s2\", \"a:s3\"]";
    private static final String KINETIC = "\"work\": {\"type\": \"kinetic\", \"block\": \"a:w\", \"rpm\": 1}, ";
    private static final String FLORA = "\"flora\": {\"items\": [\"a:b\"], \"count\": 1}, ";

    private static Map<String, JsonElement> shipped() throws IOException {
        Map<String, JsonElement> files = new TreeMap<>();
        try (Stream<Path> s = Files.list(ISLANDS)) {
            for (Path p : s.sorted().toList()) files.put("firmages:island/" + p.getFileName(), JsonParser.parseString(Files.readString(p)));
        }
        return files;
    }

    private static ArkData.Island island(String json) {
        ArkData d = ArkData.parse(Map.of("test:x", JsonParser.parseString(json)), c -> true);
        assertTrue(d.errors().isEmpty(), d.errors().toString());
        return d.island(1).orElseThrow();
    }

    @Test
    void shippedFilesGiveNineIslandsWithCrownsFromTheArcaneIsland() throws IOException {
        ArkData d = ArkData.parse(shipped(), c -> true);
        assertTrue(d.errors().isEmpty(), d.errors().toString());
        assertEquals(ArkData.ISLANDS, d.islands().size());
        for (int i = 0; i < ArkData.ISLANDS; i++) {
            ArkData.Island isl = d.island(i).orElseThrow();
            assertEquals(i >= 3, isl.hasCrown(), "age_" + i + " crown");
            assertEquals(i < 7, isl.hasFauna(), "age_" + i + " fauna (the Space and Quantum islands take none)");
            assertEquals(3, isl.stages().size());
            for (int k = 0; k < 3; k++) assertEquals("firmages:ark/age_" + i + "/stage_" + (k + 1), isl.stages().get(k));
            for (ArkData.CrownEntry c : isl.crown()) {
                if (c.what().byTag()) assertEquals("firmages:celestial_alloy/age_" + i, c.what().tag());
                assertTrue(c.count() >= 2);
            }
        }
        assertEquals(2, d.island(7).orElseThrow().crown().size(), "the Space crown: Void Steel and deep-space samples");
        // Magic stays optional: every Arcane group has a non-magic choice.
        ArkData.Island arcane = d.island(3).orElseThrow();
        assertTrue(arcane.flora().size() >= 2, "a second flora choice");
        assertTrue(arcane.fauna().contains("tfc:cat"), "a cat");
        assertTrue(arcane.work().stream().anyMatch(w -> w.block().startsWith("create:")), "a Create work");
    }

    @Test
    void crownEntriesOfMissingItemsAreLeftOutNotFailed() throws IOException {
        ArkData d = ArkData.parse(shipped(), c -> false);
        assertTrue(d.errors().isEmpty(), d.errors().toString());
        assertEquals(ArkData.ISLANDS, d.islands().size());
        for (int i = 0; i < ArkData.ISLANDS; i++) assertFalse(d.island(i).orElseThrow().hasCrown());
        assertEquals(7, d.skipped().size(), d.skipped().toString());
    }

    @Test
    void badFilesAreRejected() {
        Map<String, String> bad = Map.of(
            "no_island", "{" + FLORA + KINETIC + STAGES + "}",
            "island_range", "{\"island\": 9, " + FLORA + KINETIC + STAGES + "}",
            "both_counts", "{\"island\": 1, \"flora\": {\"items\": [\"a:b\"], \"count\": 1, \"distinct\": 1}, " + KINETIC + STAGES + "}",
            "work_type", "{\"island\": 1, " + FLORA + "\"work\": {\"type\": \"magic\", \"block\": \"a:w\"}, " + STAGES + "}",
            "no_rpm", "{\"island\": 1, " + FLORA + "\"work\": {\"type\": \"kinetic\", \"block\": \"a:w\"}, " + STAGES + "}",
            "two_stages", "{\"island\": 1, " + FLORA + KINETIC + "\"stages\": [\"a:s1\", \"a:s2\"]}",
            "no_work", "{\"island\": 1, " + FLORA + STAGES + "}");
        bad.forEach((name, json) -> {
            ArkData d = ArkData.parse(Map.of(name, JsonParser.parseString(json)), c -> true);
            assertEquals(1, d.errors().size(), name + ": " + d.errors());
            assertTrue(d.islands().isEmpty(), name);
        });
    }

    @Test
    void distinctFloraCountsEachIdOnce() {
        ArkData.Island isl = island("{\"island\": 1, \"flora\": {\"tag\": \"#test:saplings\", \"distinct\": 2}, \"fauna\": {\"entities\": [\"minecraft:cow\"]}, "
            + KINETIC + "\"crown\": [{\"tag\": \"test:alloy\", \"count\": 4}], " + STAGES + "}");
        ArkProgress p = new ArkProgress();
        ArkRules.Take t = ArkRules.accept(isl, p, "minecraft:oak_sapling", 5, M).orElseThrow();
        assertEquals(new ArkRules.Take("flora", -1, 1), t, "one item per new kind");
        assertTrue(ArkRules.apply(isl, p, "minecraft:oak_sapling", t, M).isEmpty());
        assertTrue(ArkRules.accept(isl, p, "minecraft:oak_sapling", 5, M).isEmpty(), "the same kind again counts nothing");
        assertTrue(ArkRules.accept(isl, p, "minecraft:dirt", 1, M).isEmpty(), "an item it does not ask for");
        t = ArkRules.accept(isl, p, "minecraft:birch_sapling", 1, M).orElseThrow();
        assertEquals(List.of("flora"), ArkRules.apply(isl, p, "minecraft:birch_sapling", t, M));
        assertTrue(ArkRules.accept(isl, p, "minecraft:spruce_sapling", 1, M).isEmpty(), "flora is done: a third kind is not taken");

        // the crown takes only the rest
        t = ArkRules.accept(isl, p, "test:alloy_ingot", 3, M).orElseThrow();
        assertEquals(new ArkRules.Take(ArkData.CROWN, 0, 3), t);
        assertTrue(ArkRules.apply(isl, p, "test:alloy_ingot", t, M).isEmpty());
        t = ArkRules.accept(isl, p, "test:alloy_ingot", 64, M).orElseThrow();
        assertEquals(1, t.count(), "one more of 64");
        assertEquals(List.of(ArkData.CROWN), ArkRules.apply(isl, p, "test:alloy_ingot", t, M));
        assertTrue(ArkRules.accept(isl, p, "test:alloy_ingot", 1, M).isEmpty());
        assertEquals(1, ArkRules.targetStage(p), "the crown grows no stage");
        assertFalse(ArkRules.isComplete(isl, p));
        p.groups.add("fauna");
        p.groups.add("work");
        assertEquals(3, ArkRules.targetStage(p));
        assertTrue(ArkRules.isComplete(isl, p));
    }

    @Test
    void countFloraTakesTheRest() {
        ArkData.Island isl = island("{\"island\": 1, \"flora\": {\"items\": [\"a:seed\"], \"count\": 8}, \"work\": {\"type\": \"energy\", \"block\": \"a:w\"}, " + STAGES + "}");
        ArkProgress p = new ArkProgress();
        ArkRules.Take t = ArkRules.accept(isl, p, "a:seed", 5, M).orElseThrow();
        assertEquals(5, t.count());
        ArkRules.apply(isl, p, "a:seed", t, M);
        t = ArkRules.accept(isl, p, "a:seed", 64, M).orElseThrow();
        assertEquals(3, t.count());
        assertEquals(List.of("flora"), ArkRules.apply(isl, p, "a:seed", t, M));
    }

    @Test
    void anIslandWithoutAnimalGetsItsFaunaOnlyAfterTheFinale() {
        ArkData.Island isl = island("{\"island\": 1, " + FLORA + "\"fauna\": {\"entities\": []}, \"work\": {\"type\": \"energy\", \"block\": \"a:w\"}, " + STAGES + "}");
        ArkProgress p = new ArkProgress();
        assertTrue(ArkRules.settle(isl, p, M, false).isEmpty());
        assertEquals(List.of("fauna"), ArkRules.settle(isl, p, M, true));
        assertTrue(ArkRules.settle(isl, p, M, true).isEmpty(), "once");
        assertFalse(isl.hasCrown());
        assertFalse(ArkRules.isComplete(isl, p));
        p.groups.add("flora");
        p.groups.add("work");
        assertTrue(ArkRules.isComplete(isl, p), "no crown to give");
    }

    @Test
    void ninthIslandCompletesTheArchipelago() {
        List<ArkProgress> all = new ArrayList<>();
        for (int i = 0; i < ArkData.ISLANDS; i++) all.add(new ArkProgress());
        assertEquals(0, ArkRules.completeMask(all));
        for (int i = 0; i < ArkData.ISLANDS - 1; i++) all.get(i).complete = true;
        assertFalse(ArkRules.allComplete(ArkRules.completeMask(all)));
        all.get(8).complete = true;
        assertEquals(0x1FF, ArkRules.completeMask(all));
        assertTrue(ArkRules.allComplete(ArkRules.completeMask(all)));
    }

    @Test
    void socketsAreDistinctCellsOnTheRing() {
        Set<String> cells = new HashSet<>();
        for (int i = 0; i < ArkData.ISLANDS; i++) {
            int[] s = ArkRules.socket(i);
            assertTrue(cells.add(s[0] + "," + s[1]), "socket " + i + " shares a cell");
            double d = Math.hypot(s[0], s[1]);
            assertTrue(d >= 2.5 && d <= 3.7, "socket " + i + " at " + d);
        }
    }

    @Test
    void growthRunsFromThePlinthOutward() {
        assertEquals(0, ArkRules.growthTick(0, 0, 10, 200));
        assertEquals(200, ArkRules.growthTick(10, 0, 10, 200));
        assertEquals(100, ArkRules.growthTick(0, 5, 10, 200));
        assertEquals(0, ArkRules.growthTick(7, 7, 10, 0), "growthTicks 0: at once");
        int last = -1;
        for (int d = 0; d <= 12; d++) {
            int t = ArkRules.growthTick(d, 0, 12, 200);
            assertTrue(t >= last);
            last = t;
        }
    }

    @Test
    void generatorCheckIsClean() throws IOException, InterruptedException {
        Path script = Path.of("../../dev/gen_ark.py");
        Assumptions.assumeTrue(Files.exists(script), "dev/gen_ark.py not found");
        Process p;
        try {
            p = new ProcessBuilder("python", script.toString(), "--check").redirectErrorStream(true).start();
        } catch (IOException e) {
            Assumptions.abort("no python on PATH: " + e.getMessage());
            return;
        }
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "gen_ark.py --check timed out");
        assertEquals(0, p.exitValue(), "gen_ark.py --check: " + out);
    }
}
