package dev.firmages.core.shrine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U checks of the shipped ring multiblocks ({@code data/firmages/modonomicon/multiblocks/shrine_ring_N.json})
 * against the shape rules the shrine code relies on (SPEC §7.8): dense pattern anchored at one heart in the centre
 * of the bottom layer (never the exact middle layer), one plinth on the ring's own border north of the heart at
 * distance N + 2, so rings nest without sharing a block; every pattern character mapped, every pack tag file
 * present; the rite keys of the tier exist in the ring; rings 3..8 are 4-fold symmetric apart from the plinth; the
 * block count stays small.
 */
class ShrineRingFilesTest {
    private static final Path RES = Path.of("src/main/resources/data/firmages");

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    /** layers.get(y) = bottom-up layer y; each row is z (north first), each char x (west first). */
    private static List<List<String>> layersBottomUp(JsonObject mb) {
        JsonArray pattern = mb.getAsJsonArray("pattern");
        List<List<String>> out = new java.util.ArrayList<>();
        for (int i = pattern.size() - 1; i >= 0; i--) {
            List<String> rows = new java.util.ArrayList<>();
            for (JsonElement r : pattern.get(i).getAsJsonArray()) rows.add(r.getAsString());
            out.add(rows);
        }
        return out;
    }

    @Test
    void ringsHaveTheShapeTheShrineExpects() throws IOException {
        for (int n = 0; n <= ShrineData.MAX_TIER; n++) {
            String where = "shrine_ring_" + n;
            JsonObject mb = read(RES.resolve("modonomicon/multiblocks/" + where + ".json"));
            JsonObject tier = read(RES.resolve("firmages_shrine/tier/ring_" + n + ".json"));
            assertEquals("modonomicon:dense", mb.get("type").getAsString(), where);
            List<List<String>> layers = layersBottomUp(mb);
            int h = n + 2;
            int size = 2 * h + 1;
            assertTrue(layers.size() >= 2, where + ": the heart must not sit in the exact middle layer");
            Set<Character> used = new HashSet<>();
            int hearts = 0;
            int plinths = 0;
            int blocks = 0;
            for (int y = 0; y < layers.size(); y++) {
                List<String> rows = layers.get(y);
                assertEquals(size, rows.size(), where + " layer " + y + " rows");
                for (int z = 0; z < size; z++) {
                    assertEquals(size, rows.get(z).length(), where + " layer " + y + " row " + z);
                    for (int x = 0; x < size; x++) {
                        char c = rows.get(z).charAt(x);
                        if (c == '_') continue;
                        used.add(c);
                        blocks++;
                        if (c == '0') {
                            hearts++;
                            assertTrue(y == 0 && x == h && z == h, where + ": heart in the centre of the bottom layer");
                        }
                        if (c == 'P') {
                            plinths++;
                            assertTrue(y == 0 && x == h && z == 0, where + ": plinth north of the heart at distance " + h);
                        }
                    }
                }
            }
            assertEquals(1, hearts, where + " hearts");
            assertEquals(1, plinths, where + " plinths");
            assertTrue(blocks <= 200, where + ": " + blocks + " blocks");
            JsonObject mapping = mb.getAsJsonObject("mapping");
            assertEquals(used, charSet(mapping.keySet()), where + ": mapping keys = pattern characters");
            for (String k : mapping.keySet()) {
                JsonObject m = mapping.getAsJsonObject(k);
                if (m.get("type").getAsString().equals("modonomicon:tag")) {
                    String tag = m.get("tag").getAsString();
                    assertTrue(tag.startsWith("#firmages:shrine/"), where + " " + k + ": pack shrine tag " + tag);
                    Path file = RES.resolve("tags/block/shrine/" + tag.substring("#firmages:shrine/".length()) + ".json");
                    assertTrue(Files.isRegularFile(file), where + ": tag file " + file);
                    assertNotEquals(0, read(file).getAsJsonArray("values").size(), where + ": tag " + tag + " has entries");
                }
            }
            if (tier.has("rites")) {
                for (JsonElement r : tier.getAsJsonArray("rites")) {
                    JsonObject rite = r.getAsJsonObject();
                    String type = rite.get("type").getAsString();
                    if (type.equals("blockstate") || type.equals("energy")) {
                        assertTrue(used.contains(rite.get("key").getAsString().charAt(0)), where + ": rite key " + rite.get("key"));
                    }
                }
            }
            if (n >= 3) assertFourFold(where, layers, h);
        }
    }

    private static Set<Character> charSet(Set<String> keys) {
        Set<Character> out = new HashSet<>();
        for (String k : keys) {
            assertEquals(1, k.length(), "mapping key " + k);
            out.add(k.charAt(0));
        }
        return out;
    }

    /** Every block equals its 90-degree rotation about the heart; the plinth stands in for a border block. */
    private static void assertFourFold(String where, List<List<String>> layers, int h) {
        for (int y = 0; y < layers.size(); y++) {
            List<String> rows = layers.get(y);
            for (int z = -h; z <= h; z++) {
                for (int x = -h; x <= h; x++) {
                    char a = rows.get(z + h).charAt(x + h);
                    int rx = -z;
                    int rz = x;
                    char b = rows.get(rz + h).charAt(rx + h);
                    if (a == 'P' || b == 'P') continue;
                    assertEquals(a, b, where + " layer " + y + ": (" + x + "," + z + ") vs (" + rx + "," + rz + ")");
                }
            }
        }
    }
}
