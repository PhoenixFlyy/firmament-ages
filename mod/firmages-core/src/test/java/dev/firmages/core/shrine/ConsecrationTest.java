package dev.firmages.core.shrine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U checks of the consecration (SPEC §17): the shipped {@code consecration.json} gives every block key of every
 * ring a role (heart and plinth stay), the parser rejects bad entries and honours pattern overrides, and the stored
 * original block states survive the string round trip that {@link ShrineSavedData} writes to NBT.
 */
class ConsecrationTest {
    private static final Path RES = Path.of("src/main/resources/data/firmages");

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    @Test
    void everyShippedRingKeyHasARole() throws IOException {
        Consecration c = Consecration.parse(read(RES.resolve("firmages_shrine/consecration.json")));
        assertTrue(c.errors().isEmpty(), "errors: " + c.errors());
        Set<Consecration.Role> used = EnumSet.noneOf(Consecration.Role.class);
        int blocks = 0;
        for (int n = 0; n <= ShrineData.MAX_TIER; n++) {
            String id = "firmages:shrine_ring_" + n;
            JsonObject mb = read(RES.resolve("modonomicon/multiblocks/shrine_ring_" + n + ".json"));
            Set<Character> keep = Set.of(ShrineTier.HEART_KEY, ShrineTier.DEFAULT_PLINTH_KEY);
            Map<Character, Consecration.Role> roles = c.rolesFor(id, mb, keep);
            assertEquals(List.of(), c.unmapped(id, mb, keep), id + " keys without a role");
            assertFalse(roles.containsKey('0') || roles.containsKey('P'), id + ": heart and plinth keep their own blocks");
            assertEquals(mb.getAsJsonObject("mapping").size() - 2, roles.size(), id + " roles " + roles);
            used.addAll(roles.values());
            for (var layer : mb.getAsJsonArray("pattern")) {
                for (var row : layer.getAsJsonArray()) {
                    for (char ch : row.getAsString().toCharArray()) if (roles.containsKey(ch)) blocks++;
                }
            }
        }
        assertEquals(EnumSet.allOf(Consecration.Role.class), used, "every role of the family is used by some ring");
        assertEquals(537, blocks, "consecrated positions over all nine rings");
        // the shipped data loads through ShrineData as well (no unknown-file warning)
        ShrineData d = ShrineData.parse(Map.of("firmages:consecration", read(RES.resolve("firmages_shrine/consecration.json"))));
        assertTrue(d.errors().isEmpty() && d.warnings().isEmpty(), d.errors() + " " + d.warnings());
        assertEquals(c.roles(), d.consecration().roles());
    }

    @Test
    void parserRejectsBadEntriesAndAppliesOverrides() {
        JsonObject o = JsonParser.parseString("""
            {
              "roles": {
                "#firmages:shrine/hearth_stones": "stone",
                "minecraft:bell": "metal",
                "#Bad Tag": "stone",
                "minecraft:hay_block": "marble"
              },
              "patterns": { "firmages:ring": { "B": "lamp", "XY": "stone" } },
              "comment": "x"
            }
            """).getAsJsonObject();
        Consecration c = Consecration.parse(o);
        assertEquals(Map.of("#firmages:shrine/hearth_stones", Consecration.Role.STONE, "minecraft:bell", Consecration.Role.METAL), c.roles());
        assertEquals(4, c.errors().size(), c.errors().toString()); // bad id, unknown role, two-character key, unknown top-level key
        JsonObject mb = JsonParser.parseString("""
            { "mapping": {
                "0": { "type": "modonomicon:block", "block": "firmages:shrine_heart" },
                "C": { "type": "modonomicon:tag", "tag": "#firmages:shrine/hearth_stones" },
                "B": { "type": "modonomicon:block", "block": "minecraft:bell" },
                "G": { "type": "modonomicon:blockstate", "block": "minecraft:bell[attachment=floor]" },
                "H": { "type": "modonomicon:tag", "tag": "firmages:shrine/unknown" } } }
            """).getAsJsonObject();
        Map<Character, Consecration.Role> roles = c.rolesFor("firmages:ring", mb, Set.of('0'));
        assertEquals(Map.of('C', Consecration.Role.STONE, 'B', Consecration.Role.LAMP, 'G', Consecration.Role.METAL), new TreeMap<>(roles));
        assertEquals(List.of('H'), c.unmapped("firmages:ring", mb, Set.of('0')));
        assertEquals(Map.of('C', Consecration.Role.STONE, 'B', Consecration.Role.METAL, 'G', Consecration.Role.METAL),
            new TreeMap<>(c.rolesFor("firmages:other", mb, Set.of('0'))), "overrides apply to their multiblock only");
    }

    @Test
    void originalStatesRoundTrip() {
        Map<String, String> props = new TreeMap<>(Map.of("axis", "y", "lit", "true", "candles", "4"));
        Consecration.StateSpec s = new Consecration.StateSpec("tfc:candle/white", props);
        String enc = Consecration.encodeState(s);
        assertEquals("tfc:candle/white[axis=y,candles=4,lit=true]", enc, "sorted like BlockStateParser");
        assertEquals(Optional.of(s), Consecration.decodeState(enc));
        Consecration.StateSpec plain = new Consecration.StateSpec("minecraft:cobblestone", Map.of());
        assertEquals("minecraft:cobblestone", Consecration.encodeState(plain));
        assertEquals(Optional.of(plain), Consecration.decodeState("minecraft:cobblestone"));
        assertEquals(Optional.of(plain), Consecration.decodeState("minecraft:cobblestone[]"));
        for (String bad : new String[] {"", "Minecraft:Stone", "stone", "a:b[", "a:b[x]", "a:b[x=1,x=2]", "a:b[x=1]]", "a:b[=1]", "a:b[X=1]"}) {
            assertTrue(Consecration.decodeState(bad).isEmpty(), "rejected: '" + bad + "'");
        }
        for (Consecration.Role r : Consecration.Role.values()) assertEquals(Optional.of(r), Consecration.Role.byId(r.id()));
        assertTrue(Consecration.Role.byId("marble").isEmpty());
    }
}
