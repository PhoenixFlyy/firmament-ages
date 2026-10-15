package dev.firmages.core.wayshrine;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.shrine.ShrineTier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U (SPEC-himmel §28.10): the shipped types, offerings and rites parse without warnings; the offering and the rite
 * fall back to the highest Age at or below the current one (age_8 uses age_6); bad files are rejected one by one.
 */
class WayshrineDataTest {
    static Map<String, JsonElement> shipped() throws IOException {
        Path root = Path.of("src/main/resources/data/firmages/firmages_wayshrine");
        Map<String, JsonElement> files = new TreeMap<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json")).toList()) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                files.put("firmages:" + rel.substring(0, rel.length() - 5), JsonParser.parseString(Files.readString(p)));
            }
        }
        return files;
    }

    @Test
    void shippedDataParses() throws IOException {
        WayshrineData d = WayshrineData.parse(shipped());
        assertEquals(List.of(), d.errors());
        assertEquals(List.of(), d.warnings());
        assertEquals(Set.of("forest_ring", "steppe_stones", "swamp_altar", "desert_arch", "mountain_spire", "wind_shrine", "reef_altar", "volcanic_ring"),
            d.types().keySet(), "eight types, the Wind Shrine in place of the Glacier Cairn (S6B)");
        assertEquals(AgeId.AGE_1, d.type("forest_ring").orElseThrow().minAge());
        assertEquals(AgeId.AGE_4, d.type("wind_shrine").orElseThrow().minAge(), "the isle shrine opens with the Industrial Age");
        assertEquals(AgeId.AGE_5, d.type("volcanic_ring").orElseThrow().minAge());
        for (WayshrineData.Type t : d.types().values()) {
            assertEquals("firmages:wayshrine/" + t.id(), t.structure());
            assertEquals("firmages:wayshrine_" + t.id(), t.multiblock());
            assertEquals('L', t.lightKey());
            assertTrue(t.minAge().ordinal() >= AgeId.AGE_1.ordinal() && t.minAge().ordinal() <= AgeId.AGE_5.ordinal(), t.id() + " min_age " + t.minAge());
        }
        for (int a = 1; a <= 6; a++) {
            assertTrue(d.offerings().containsKey("age_" + a), "offering of age_" + a);
            assertTrue(d.rites().containsKey("age_" + a), "rite of age_" + a);
        }
        assertEquals(Optional.of("tfc:metal/double_ingot/bronze"), d.offeringFor(AgeId.AGE_1));
        assertEquals(Optional.of("firmages:meteoric_iron_ingot"), d.offeringFor(AgeId.AGE_2));
        assertEquals(Optional.of("firmages:halo_steel_ingot"), d.offeringFor(AgeId.AGE_6));
        assertEquals(Optional.of("firmages:halo_steel_ingot"), d.offeringFor(AgeId.AGE_8), "age_8 falls back to age_6");
        assertEquals(Optional.empty(), d.offeringFor(AgeId.AGE_0), "nothing before the Bronze Age");
        List<ShrineTier.Rite> r1 = d.ritesFor(AgeId.AGE_1);
        assertEquals(1, r1.size());
        assertEquals(ShrineTier.Rite.Type.BLOCKSTATE, r1.get(0).type());
        assertEquals('L', r1.get(0).key());
        assertEquals("lit", r1.get(0).property());
        assertEquals(List.of(ShrineTier.Rite.Type.BLOCKSTATE, ShrineTier.Rite.Type.SKY), d.ritesFor(AgeId.AGE_3).stream().map(ShrineTier.Rite::type).toList());
        assertEquals("active", d.ritesFor(AgeId.AGE_4).get(0).property(), "from the Industrial Age the light is powered");
        assertEquals(List.of(ShrineTier.Rite.Type.BLOCKSTATE, ShrineTier.Rite.Type.PLAYERS_PRAYING),
            d.ritesFor(AgeId.AGE_9).stream().map(ShrineTier.Rite::type).toList(), "age_9 falls back to the age_6 rite");
        assertEquals(2, d.ritesFor(AgeId.AGE_7).get(1).min());
    }

    private static JsonElement json(String s) {
        return JsonParser.parseString(s);
    }

    @Test
    void badFilesAreRejected() {
        Map<String, JsonElement> files = new TreeMap<>();
        files.put("a:offerings", json("{\"age_1\": \"minecraft:gold_ingot\"}"));
        files.put("b:offerings", json("{\"age_0\": \"minecraft:iron_ingot\", \"age_2\": \"minecraft:diamond\"}"));
        files.put("a:rite/age_1", json("{\"rites\": [{\"type\": \"blockstate\", \"key\": \"L\", \"property\": \"lit\", \"value\": \"true\"}]}"));
        files.put("a:rite/age_2", json("{\"rites\": [{\"type\": \"wobble\"}]}"));
        files.put("a:rite/age_3", json("{\"rites\": [{\"type\": \"interact\", \"tag\": \"minecraft:bells\"}]}"));
        files.put("a:rite/dawn", json("{\"rites\": []}"));
        files.put("a:type/good", json("{\"structure\": \"a:x\", \"multiblock\": \"a:y\", \"min_age\": \"age_2\"}"));
        files.put("a:type/early", json("{\"structure\": \"a:x\", \"multiblock\": \"a:y\", \"min_age\": \"age_0\"}"));
        files.put("a:type/nostructure", json("{\"multiblock\": \"a:y\", \"min_age\": \"age_1\"}"));
        files.put("a:type/badbeam", json("{\"structure\": \"a:x\", \"multiblock\": \"a:y\", \"min_age\": \"age_1\", \"ruin_beam\": \"red\"}"));
        WayshrineData d = WayshrineData.parse(files);
        assertEquals(Map.of("age_1", "minecraft:gold_ingot"), d.offerings(), "the file with an unknown Age key is rejected whole");
        assertEquals(Set.of("age_1"), d.rites().keySet());
        assertEquals(Set.of("good"), d.types().keySet());
        assertEquals(0xD8E6F0, d.type("good").orElseThrow().ruinBeam(), "default ruin beam");
        assertEquals(7, d.errors().size(), "errors: " + d.errors());
        assertTrue(d.errors().stream().anyMatch(e -> e.contains("b:offerings") && e.contains("age_0")));
        assertTrue(d.errors().stream().anyMatch(e -> e.contains("unknown rite type 'wobble'")));
        assertTrue(d.errors().stream().anyMatch(e -> e.contains("not used at Wayshrines")));
        assertTrue(d.errors().stream().anyMatch(e -> e.contains("a:rite/dawn")));
    }
}
