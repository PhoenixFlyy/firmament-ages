package dev.firmages.core.origin;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level U: The Origin's own spawn list (mob_9, {@code data/firmages/origin/spawns.json}). */
class OriginSpawnListTest {

    @Test
    void shippedListHasOnlyLowWeightMonsters() throws IOException {
        OriginSpawnList l = OriginSpawnList.parse(JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/firmages/origin/spawns.json"))));
        assertEquals(List.of(), l.errors());
        assertEquals(java.util.Set.of("monster"), l.spawners().keySet(), "no passive, ambient or water mobs");
        List<OriginSpawnList.Entry> m = l.of("monster");
        assertEquals(List.of("minecraft:enderman", "cataclysm:endermaptera", "cataclysm:ignited_revenant", "cataclysm:ender_golem"),
            m.stream().map(OriginSpawnList.Entry::type).toList());
        int total = m.stream().mapToInt(OriginSpawnList.Entry::weight).sum();
        OriginSpawnList.Entry golem = m.get(3);
        assertTrue(golem.weight() * 10 < total && golem.maxCount() == 1, "the elite golem is rare and alone");
        assertTrue(l.of("creature").isEmpty());
    }

    @Test
    void badEntriesAreSkippedOneByOne() {
        OriginSpawnList l = OriginSpawnList.parse(JsonParser.parseString("{\"spawners\":{\"monster\":["
            + "{\"type\":\"minecraft:zombie\",\"weight\":5,\"minCount\":1,\"maxCount\":4},"
            + "{\"type\":\"Not An Id\"},{\"type\":\"minecraft:husk\",\"weight\":0},{\"type\":\"minecraft:stray\",\"minCount\":3,\"maxCount\":2}],"
            + "\"dragons\":[]}}"));
        assertEquals(1, l.of("monster").size());
        assertEquals(new OriginSpawnList.Entry("minecraft:zombie", 5, 1, 4), l.of("monster").get(0));
        assertEquals(4, l.errors().size(), "three bad entries and one unknown category: " + l.errors());
        assertFalse(OriginSpawnList.parse(JsonParser.parseString("{}")).errors().isEmpty(), "no spawners object");
        assertEquals(new OriginSpawnList.Entry("minecraft:vex", 1, 1, 1),
            OriginSpawnList.parse(JsonParser.parseString("{\"spawners\":{\"monster\":[{\"type\":\"minecraft:vex\"}]}}")).of("monster").get(0), "defaults");
    }
}
