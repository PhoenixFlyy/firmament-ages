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
        assertEquals("floor", m.get(GrandArenaPlan.pack(20, 64, 0)), "fighting floor");
        assertEquals("age_0_stone", m.get(GrandArenaPlan.pack(0, 64, -53)), "the outermost band is the Stone Age");
        assertEquals("age_8_trim", m.get(GrandArenaPlan.pack(0, 64, -36)), "the innermost band is the Quantum Age");
        assertEquals("rim", m.get(GrandArenaPlan.pack(-58, 64, 0)));
        assertEquals(4, m.values().stream().filter("arena_altar"::equals).count());
        assertEquals(9, m.values().stream().filter(v -> v.startsWith("vitrine_glass_")).count());
        // the stair: the last step sits one above the floor next to it, the platform 8 above the floor
        assertEquals("stair", m.get(GrandArenaPlan.pack(0, 65, 50)));
        assertEquals("stair", m.get(GrandArenaPlan.pack(0, 71, 56)));
        assertEquals("platform", m.get(GrandArenaPlan.pack(0, 72, 62)));
        assertTrue(!m.containsKey(GrandArenaPlan.pack(0, 65, 49)), "the floor in front of the stair is open");
        for (long p : new long[] {GrandArenaPlan.pack(-60, 64, 0), GrandArenaPlan.pack(3, -7, -60)}) {
            assertEquals(p, GrandArenaPlan.pack(GrandArenaPlan.unpackX(p), GrandArenaPlan.unpackY(p), GrandArenaPlan.unpackZ(p)));
        }
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
