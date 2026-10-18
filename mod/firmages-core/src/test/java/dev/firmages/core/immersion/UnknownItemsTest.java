package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §21.15 (G3, Felix S1a): items of a locked Age are "Something Unknown" in tooltips, chat, name tags and Jade. */
class UnknownItemsTest {
    private static final Map<String, AgeId> NS = Map.of("minecraft", AgeId.DAWN, "create", AgeId.AGE_1);
    private static final String STICK = new String("minecraft:stick");
    private static final String SHAFT = new String("create:shaft");
    private static final String STEEL = new String("minecraft:iron_ingot");
    private static final List<String> ITEMS = List.of(STICK, SHAFT, STEEL);
    private static final Map<String, String> TAGS = Map.of(STICK, "dawn", STEEL, "age_2");

    private static EmiAgeView view(String... stages) {
        return EmiAgeView.build(List.of(stages), NS, ITEMS, s -> s, TAGS::get, List.<String>of(), s -> s, s -> null);
    }

    @Test
    void lockedItemsAreMaskedByAgeIndex() {
        assertFalse(UnknownItems.masked(view(), STICK, false), "a Dawn item keeps its name");
        assertTrue(UnknownItems.masked(view(), SHAFT, false), "Create before the Bronze Age");
        assertFalse(UnknownItems.masked(view("age_0", "age_1"), SHAFT, false), "Create from the Bronze Age");
        assertTrue(UnknownItems.masked(view("age_0", "age_1"), STEEL, false), "the Age tag beats the namespace");
        assertFalse(UnknownItems.masked(view("age_0", "age_1", "age_2"), STEEL, false));
    }

    @Test
    void creativeEmptyAndNoViewKeepNames() {
        assertFalse(UnknownItems.masked(view(), SHAFT, true), "creative sees every name");
        assertFalse(UnknownItems.masked(null, SHAFT, false), "no view yet");
        assertFalse(UnknownItems.masked(view(), null, false), "empty stack");
        assertEquals("firmages.unknown_item", UnknownItems.NAME_KEY);
    }
}
