package dev.firmages.core.command;

import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SPEC §10.2: the order rules of /firmages stage (Ages stay contiguous). */
class StageRulesTest {

    @Test
    void grantFillsEarlierAgesInOrder() {
        Set<AgeId> held = EnumSet.of(AgeId.DAWN, AgeId.AGE_0);
        assertEquals(List.of(AgeId.AGE_1, AgeId.AGE_2, AgeId.AGE_3), StageRules.agesToGrant(held, AgeId.AGE_3));
        assertEquals(List.of(AgeId.AGE_1), StageRules.agesToGrant(held, AgeId.AGE_1), "the next Age alone");
        assertEquals(List.of(), StageRules.agesToGrant(held, AgeId.AGE_0), "held: nothing to do");
        assertEquals(List.of(AgeId.DAWN, AgeId.AGE_0), StageRules.agesToGrant(EnumSet.noneOf(AgeId.class), AgeId.AGE_0), "Dawn first");
        assertEquals(List.of(AgeId.AGE_1), StageRules.agesToGrant(EnumSet.of(AgeId.DAWN, AgeId.AGE_0, AgeId.AGE_2), AgeId.AGE_2),
            "a gap below a held Age is filled");
    }

    @Test
    void revokeTakesLaterAgesLatestFirst() {
        Set<AgeId> held = EnumSet.of(AgeId.DAWN, AgeId.AGE_0, AgeId.AGE_1, AgeId.AGE_2, AgeId.AGE_3);
        assertEquals(List.of(AgeId.AGE_3, AgeId.AGE_2, AgeId.AGE_1), StageRules.agesToRevoke(held, AgeId.AGE_1));
        assertEquals(List.of(AgeId.AGE_3), StageRules.agesToRevoke(held, AgeId.AGE_3));
        assertEquals(List.of(), StageRules.agesToRevoke(held, AgeId.AGE_4), "not held: nothing to do");
        assertEquals(List.of(AgeId.AGE_3, AgeId.AGE_2, AgeId.AGE_1, AgeId.AGE_0, AgeId.DAWN), StageRules.agesToRevoke(held, AgeId.DAWN));
    }

    @Test
    void clearRevokesAgesLatestFirstThenTheRest() {
        assertEquals(List.of("age_1", "age_0", "dawn", "ftbchunks_mapping", "mob_0", "mob_1"),
            StageRules.clearOrder(List.of("mob_1", "dawn", "age_0", "ftbchunks_mapping", "age_1", "mob_0")));
        assertEquals(List.of(), StageRules.clearOrder(List.of()));
    }

    @Test
    void listShowsAgesFirst() {
        assertEquals(List.of("dawn", "age_0", "age_9", "finale_won", "mob_0", "tool_toms_storage"),
            StageRules.listOrder(List.of("tool_toms_storage", "age_9", "mob_0", "dawn", "finale_won", "age_0", "dawn")));
    }
}
