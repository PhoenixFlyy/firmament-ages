package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §21.14: Jade names a block of a locked Age "Unknown", by the recipe viewer's Age index. */
class UnknownBlocksTest {
    private static final Map<String, AgeId> NS = Map.of("minecraft", AgeId.DAWN, "tfc", AgeId.DAWN, "create", AgeId.AGE_1,
        "immersivepetroleum", AgeId.AGE_5);
    // opaque item keys, compared by identity like registry objects
    private static final String GRANITE = new String("tfc:rock/raw/granite");
    private static final String SHAFT = new String("create:shaft"); // untagged: its namespace decides
    private static final String IRON_BLOCK = new String("minecraft:iron_block"); // a Dawn namespace, tagged age_2
    private static final String BOOKSHELF = new String("minecraft:bookshelf"); // disabled
    private static final List<String> ITEMS = List.of(GRANITE, SHAFT, IRON_BLOCK, BOOKSHELF);
    private static final Map<String, String> TAGS = Map.of(GRANITE, "dawn", IRON_BLOCK, "age_2", BOOKSHELF, "disabled");

    private static EmiAgeView view(String... stages) {
        return EmiAgeView.build(List.of(stages), NS, ITEMS, s -> s, TAGS::get, List.<String>of(), s -> s, s -> null);
    }

    @Test
    void blocksByTheirItem() {
        EmiAgeView dawn = view();
        assertFalse(UnknownBlocks.unknown(dawn, GRANITE, "tfc:rock/raw/granite", false), "a Dawn block keeps its name");
        assertTrue(UnknownBlocks.unknown(dawn, SHAFT, "create:shaft", false), "Create before the Bronze Age");
        assertTrue(UnknownBlocks.unknown(dawn, IRON_BLOCK, "minecraft:iron_block", false), "the Age tag beats the namespace");
        assertTrue(UnknownBlocks.unknown(dawn, BOOKSHELF, "minecraft:bookshelf", false), "disabled");
        EmiAgeView bronze = view("age_0", "age_1");
        assertFalse(UnknownBlocks.unknown(bronze, SHAFT, "create:shaft", false), "Create from the Bronze Age");
        assertTrue(UnknownBlocks.unknown(bronze, IRON_BLOCK, "minecraft:iron_block", false), "age_2 still locked");
        assertFalse(UnknownBlocks.unknown(view("age_0", "age_1", "age_2"), IRON_BLOCK, "minecraft:iron_block", false));
        assertTrue(UnknownBlocks.unknown(view("age_0", "age_1", "age_2", "age_3", "age_4", "age_5", "age_6", "age_7", "age_8", "age_9"),
            BOOKSHELF, "minecraft:bookshelf", false), "disabled in every Age");
    }

    @Test
    void blocksWithoutItemByNamespace() {
        assertTrue(UnknownBlocks.unknown(view("age_0", "age_1"), null, "immersivepetroleum:oil", false), "a fluid block of a later mod");
        assertFalse(UnknownBlocks.unknown(view("age_0", "age_1", "age_2", "age_3", "age_4", "age_5"), null, "immersivepetroleum:oil", false));
        assertFalse(UnknownBlocks.unknown(view(), null, "minecraft:water", false), "water");
        assertFalse(UnknownBlocks.unknown(view(), null, "jade:unknown_namespace", false), "unknown namespaces are never locked");
    }

    @Test
    void creativeSeesEverything() {
        assertFalse(UnknownBlocks.unknown(view(), SHAFT, "create:shaft", true));
        assertFalse(UnknownBlocks.unknown(view(), null, "immersivepetroleum:oil", true));
        assertFalse(UnknownBlocks.unknown(null, SHAFT, "create:shaft", false), "no view yet: nothing hidden");
        assertEquals("firmages.jade.unknown", UnknownBlocks.NAME_KEY);
    }
}
