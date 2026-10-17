package dev.firmages.core.loot;

import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The loot Age rule (SPEC §19.5): which generated stacks stay for which Ages. */
class LootAgeRuleTest {
    private static final Map<String, AgeId> AGES = Map.of("dawn_item", AgeId.DAWN, "copper", AgeId.AGE_0, "iron", AgeId.AGE_2,
        "star", AgeId.AGE_9);
    private static final Set<AgeId> DAWN = EnumSet.of(AgeId.DAWN);

    private static List<String> roll(List<String> ids, Set<AgeId> unlocked) {
        List<String> loot = new ArrayList<>(ids);
        LootAgeRule.filter(loot, AGES::get, "hidden"::equals, unlocked);
        return loot;
    }

    @Test
    void keepDecision() {
        assertTrue(LootAgeRule.keep(null, false, DAWN), "untagged stays");
        assertTrue(LootAgeRule.keep(AgeId.DAWN, false, Set.of()), "Dawn stays even for an empty set");
        assertFalse(LootAgeRule.keep(AgeId.AGE_9, false, DAWN), "age_9 goes in Dawn");
        assertTrue(LootAgeRule.keep(AgeId.AGE_9, false, EnumSet.of(AgeId.DAWN, AgeId.AGE_9)), "age_9 stays once held");
        assertFalse(LootAgeRule.keep(null, true, EnumSet.allOf(AgeId.class)), "disabled goes with every Age");
        assertFalse(LootAgeRule.keep(AgeId.AGE_2, true, EnumSet.allOf(AgeId.class)), "disabled wins over a held Age");
    }

    @Test
    void filterPerAge() {
        List<String> table = List.of("bread", "dawn_item", "copper", "iron", "star", "hidden");
        assertEquals(List.of("bread", "dawn_item"), roll(table, DAWN));
        assertEquals(List.of("bread", "dawn_item", "copper"), roll(table, EnumSet.of(AgeId.DAWN, AgeId.AGE_0, AgeId.AGE_1)));
        assertEquals(List.of("bread", "dawn_item", "copper", "iron", "star"), roll(table, EnumSet.allOf(AgeId.class)));
        assertEquals(List.of("bread", "dawn_item", "star"), roll(table, EnumSet.of(AgeId.DAWN, AgeId.AGE_9)), "a set, not a rank");
        List<String> loot = new ArrayList<>(table);
        assertEquals(4, LootAgeRule.filter(loot, AGES::get, "hidden"::equals, DAWN), "removed count");
        assertEquals(List.of(), roll(List.of("star"), DAWN), "a table of one age_9 item is empty in Dawn");
    }

    @Test
    void stageIds() {
        assertEquals(EnumSet.of(AgeId.DAWN, AgeId.AGE_3), LootAgeRule.ages(List.of("progressivestages:age_3", "mob_3", "finale_won")));
        assertEquals(EnumSet.of(AgeId.DAWN), LootAgeRule.ages(List.of()));
        assertEquals(EnumSet.allOf(AgeId.class), LootAgeRule.ages(AgeId.all().stream().map(AgeId::id).toList()));
    }
}
