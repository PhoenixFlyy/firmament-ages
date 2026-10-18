package dev.firmages.core.origin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round G3 (SPEC §16.5): the Java plan of the grand arena and its Python mirror in dev/gen_ark.py agree. */
class GrandArenaPlanTest {
    @Test
    void countsMatchGenArk() throws Exception {
        JsonObject o = JsonParser.parseString(Files.readString(Path.of("../../dev/data/origin_arena.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, Integer> counts = GrandArenaPlan.counts();
        JsonObject want = o.getAsJsonObject("counts");
        assertEquals(want.size(), counts.size(), "material keys: " + counts.keySet());
        for (var e : want.entrySet()) assertEquals(e.getValue().getAsInt(), counts.getOrDefault(e.getKey(), -1), e.getKey());
        assertEquals(o.get("cells").getAsInt(), GrandArenaPlan.cells().size());
        assertEquals(3 * o.get("classic_radius").getAsInt(), GrandArenaPlan.RADIUS, "three times the old diameter");
    }

    @Test
    void layoutFacts() {
        Map<Long, String> m = GrandArenaPlan.cells();
        assertEquals("brick_8", m.get(GrandArenaPlan.pack(8, 64, 0)), "the sink floor");
        assertEquals("dais", m.get(GrandArenaPlan.pack(0, 64, 0)));
        assertEquals("stone_8", m.get(GrandArenaPlan.pack(20, 67, 0)), "the fighting ring three above the sink");
        assertEquals("stone_0", m.get(GrandArenaPlan.pack(0, 70, -53)), "the outermost terrace is the Stone Age");
        assertEquals("trim_8", m.get(GrandArenaPlan.pack(0, 68, -36)), "the innermost terrace is the Quantum Age");
        assertEquals("brick_8", m.get(GrandArenaPlan.pack(-58, 71, 0)), "the rim");
        assertEquals("brick_8", m.get(GrandArenaPlan.pack(-59, 72, 0)), "the parapet");
        assertTrue(!m.containsKey(GrandArenaPlan.pack(0, 72, 59)), "the parapet is cut at the south gate");
        assertEquals(4, m.values().stream().filter("arena_altar"::equals).count());
        assertEquals("arena_altar", m.get(GrandArenaPlan.pack(0, GrandArenaPlan.ALTAR_Y, -16)), "the altar on its podium");
        assertEquals("brick_0", m.get(GrandArenaPlan.pack(2, 68, -16)), "the podium's lower step");
        assertEquals(9, m.values().stream().filter(v -> v.startsWith("glass_")).count());
        // trench and bridge on the north-east diagonal
        assertEquals("brick_8", m.get(GrandArenaPlan.pack(18, 64, -10)), "the trench floor");
        assertTrue(!m.containsKey(GrandArenaPlan.pack(18, 67, -10)), "the trench is open");
        assertEquals("trim_8", m.get(GrandArenaPlan.pack(15, 67, -15)), "the bridge over it");
        // the platform far below and the ascent's first and last cells
        assertEquals("brick_8", m.get(GrandArenaPlan.pack(0, GrandArenaPlan.PLATFORM_Y, 81)), "the platform under the arrival");
        assertEquals("brick_0", m.get(GrandArenaPlan.pack(-1, 35, 76)), "the first step of the ascent");
        assertEquals("trim_0", m.get(GrandArenaPlan.pack(-20, 42, 76)), "the first bridge");
        assertTrue(!m.containsKey(GrandArenaPlan.pack(-20, 41, 76)), "nothing under the bridge");
        assertEquals("brick_6", m.get(GrandArenaPlan.pack(0, 71, 61)), "the approach ends level with the rim");
        for (int i = 0; i < 9; i++) {
            int[] v = GrandArenaPlan.vitrine(i);
            assertEquals("glass_" + i, m.get(GrandArenaPlan.pack(v[0], v[1], v[2])), "vitrine " + i);
            assertEquals("pillar_" + i, m.get(GrandArenaPlan.pack(v[0], v[1] - 1, v[2])), "vitrine pillar " + i);
        }
        for (long p : new long[] {GrandArenaPlan.pack(-60, 64, 0), GrandArenaPlan.pack(3, -7, -60)}) {
            assertEquals(p, GrandArenaPlan.pack(GrandArenaPlan.unpackX(p), GrandArenaPlan.unpackY(p), GrandArenaPlan.unpackZ(p)));
        }
    }

    /** From the arrival point the rim is reached on foot: a step of at most one up or down, never a jump of two. */
    @Test
    void ascentIsWalkable() {
        int steps = GrandArenaPlan.planWalkToRim();
        assertTrue(steps > 80 && steps < 140, "a path from the platform to the rim, " + steps + " steps");
    }

    /** The boss's and the sky's reading of the arena: the Gathering altar's floor and the podium heights. */
    @Test
    void heights() {
        assertEquals(64, GrandArenaPlan.FLOOR_Y);
        assertEquals(70, GrandArenaPlan.ALTAR_Y);
        assertEquals(71, GrandArenaPlan.RIM_Y);
        assertEquals(34, GrandArenaPlan.PLATFORM_Y);
        assertEquals(37, GrandArenaPlan.RIM_Y - GrandArenaPlan.PLATFORM_Y, "the climb");
    }

    /** An Origin built before round G3 keeps the classic arena; a new one is grand; a stored choice stays. */
    @Test
    void oldWorldsKeepTheClassicArena() {
        assertTrue(!GrandArenaPlan.isGrand("", 3), "an arena built before round G3 stays classic");
        assertTrue(GrandArenaPlan.isGrand("", 0), "a new Origin is grand");
        assertTrue(!GrandArenaPlan.isGrand("classic", 0));
        assertTrue(GrandArenaPlan.isGrand("grand", 3));
    }
}
