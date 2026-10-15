package dev.firmages.core.wayshrine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U (SPEC-himmel §28.10): the restored forms and the worldgen files the generator wrote. Per type one core in the
 * centre of the bottom layer and never in the exact middle layer (§7.8), one light {@code L}, at most 11 by 8 by 11,
 * every matcher tag and every nested firmages tag present, the structure, set, pool and template files of the seven
 * worldgen types (the Wind Shrine: pool and template only), and {@code dev/gen_wayshrines.py --check} clean.
 */
class WayshrineFilesTest {
    private static final Path RES = Path.of("src/main/resources/data/firmages");
    private static final List<String> TYPES = List.of("forest_ring", "steppe_stones", "swamp_altar", "desert_arch", "mountain_spire", "wind_shrine",
        "reef_altar", "volcanic_ring");

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    private static Path tagFile(String tag) {
        String id = tag.startsWith("#") ? tag.substring(1) : tag;
        String ns = id.substring(0, id.indexOf(':'));
        String path = id.substring(id.indexOf(':') + 1);
        return Path.of("src/main/resources/data/" + ns + "/tags/block/" + path + ".json");
    }

    @Test
    void restoredFormsHaveTheShapeTheCodeExpects() throws IOException {
        for (String t : TYPES) {
            JsonObject mb = read(RES.resolve("modonomicon/multiblocks/wayshrine_" + t + ".json"));
            assertEquals("modonomicon:dense", mb.get("type").getAsString(), t);
            JsonArray pattern = mb.getAsJsonArray("pattern");
            // Modonomicon reads pattern[layer][x].charAt(z): rows are x (west first), characters z (north first)
            int sy = pattern.size();
            int sx = pattern.get(0).getAsJsonArray().size();
            int sz = pattern.get(0).getAsJsonArray().get(0).getAsString().length();
            assertTrue(sx <= 11 && sy <= 8 && sz <= 11, t + " is " + sx + " by " + sy + " by " + sz);
            assertTrue(sy >= 2, t + ": the core must not sit in the exact middle layer");
            int cores = 0;
            int lights = 0;
            for (int i = 0; i < sy; i++) {
                int y = sy - 1 - i; // pattern lists the top layer first
                JsonArray rows = pattern.get(i).getAsJsonArray();
                assertEquals(sx, rows.size(), t + " layer " + y);
                for (int x = 0; x < sx; x++) {
                    String row = rows.get(x).getAsString();
                    assertEquals(sz, row.length(), t + " layer " + y + " row " + x);
                    for (int z = 0; z < sz; z++) {
                        char c = row.charAt(z);
                        if (c == '_') continue;
                        assertTrue(mb.getAsJsonObject("mapping").has(String.valueOf(c)), t + ": key " + c + " unmapped");
                        if (c == '0') {
                            cores++;
                            assertTrue(y == 0 && x == sx / 2 && z == sz / 2, t + ": the core in the centre of the bottom layer");
                        }
                        if (c == 'L') lights++;
                    }
                }
            }
            assertEquals(1, cores, t + " cores");
            assertEquals(1, lights, t + " lights");
            for (Map.Entry<String, JsonElement> m : mb.getAsJsonObject("mapping").entrySet()) {
                JsonObject matcher = m.getValue().getAsJsonObject();
                if (!matcher.get("type").getAsString().equals("modonomicon:tag")) continue;
                Path tag = tagFile(matcher.get("tag").getAsString());
                assertTrue(Files.isRegularFile(tag), t + ": tag file " + tag);
                for (JsonElement v : read(tag).getAsJsonArray("values")) {
                    String id = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                    if (id.startsWith("#firmages:")) assertTrue(Files.isRegularFile(tagFile(id)), t + ": nested tag " + id);
                }
            }
            assertTrue(Files.isRegularFile(RES.resolve("structure/wayshrine/" + t + ".nbt")), t + " template");
            assertTrue(Files.isRegularFile(RES.resolve("worldgen/template_pool/wayshrine/" + t + ".json")), t + " pool");
            boolean worldgen = !t.equals("wind_shrine");
            assertEquals(worldgen, Files.isRegularFile(RES.resolve("worldgen/structure/wayshrine/" + t + ".json")), t + " structure");
            if (worldgen) {
                JsonObject set = read(RES.resolve("worldgen/structure_set/wayshrine/" + t + ".json"));
                JsonObject placement = set.getAsJsonObject("placement");
                assertEquals("tfc:climate", placement.get("type").getAsString(), t);
                assertEquals(80, placement.get("spacing").getAsInt(), t);
                assertEquals(30, placement.get("separation").getAsInt(), t);
                assertTrue(set.has("neoforge:conditions"), t + ": the set needs TFC");
                assertTrue(Files.isRegularFile(RES.resolve("tags/worldgen/biome/has_structure/wayshrine/" + t + ".json")), t + " biomes");
            }
        }
        JsonObject tag = read(RES.resolve("tags/worldgen/structure/wayshrine.json"));
        assertEquals(7, tag.getAsJsonArray("values").size(), "#firmages:wayshrine holds the seven worldgen types");
        List<Integer> salts = new ArrayList<>();
        for (String t : TYPES) {
            if (t.equals("wind_shrine")) continue;
            int salt = read(RES.resolve("worldgen/structure_set/wayshrine/" + t + ".json")).getAsJsonObject("placement").get("salt").getAsInt();
            assertFalse(salts.contains(salt), t + ": salt shared");
            salts.add(salt);
        }
    }

    @Test
    void generatorIsClean() throws Exception {
        Path script = Path.of("../../dev/gen_wayshrines.py");
        Assumptions.assumeTrue(Files.isRegularFile(script), "the generator lives in the pack repository");
        Process p;
        try {
            p = new ProcessBuilder("python", script.toString(), "--check").redirectErrorStream(true).start();
        } catch (IOException e) {
            Assumptions.abort("no python on PATH: " + e.getMessage());
            return;
        }
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "gen_wayshrines.py --check timed out");
        assertEquals(0, p.exitValue(), "gen_wayshrines.py --check: " + out);
    }
}
