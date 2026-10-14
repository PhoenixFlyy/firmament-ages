package dev.firmages.core.hardship;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.immersion.AgeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §19.2: {@code [hardship] toolRuleUntilAge}, the hand-breaking predicate, the hint choice and its rate limit. */
class ToolRuleTest {

    @Test
    void defaultIsDawnOnly() {
        // Felix 2026-10-14, option A; the key itself is checked in HardshipGameTests (ModConfigSpec is not on the unit-test classpath)
        assertEquals(AgeId.AGE_0, ToolRule.DEFAULT_UNTIL);
        assertTrue(ToolRule.isValid(ToolRule.DEFAULT_UNTIL.id()));
        Optional<AgeId> until = ToolRule.until(ToolRule.DEFAULT_UNTIL.id());
        for (AgeId age : AgeId.all()) assertEquals(age == AgeId.DAWN, ToolRule.active(age, until), "default in " + age);
    }

    @Test
    void configValues() {
        assertEquals(Optional.of(AgeId.AGE_1), ToolRule.until("age_1"));
        assertEquals(Optional.of(AgeId.AGE_1), ToolRule.until("progressivestages:age_1"));
        assertEquals(Optional.empty(), ToolRule.until("never"));
        assertEquals(Optional.empty(), ToolRule.until(" NEVER "));
        assertEquals(Optional.of(AgeId.AGE_0), ToolRule.until("stone"), "unknown -> default");
        assertEquals(Optional.of(AgeId.AGE_0), ToolRule.until(null));
        assertTrue(ToolRule.isValid("never"));
        assertTrue(ToolRule.isValid("dawn"));
        assertFalse(ToolRule.isValid("stone"));
        assertFalse(ToolRule.isValid(0));
        for (AgeId age : AgeId.all()) {
            assertFalse(ToolRule.active(age, Optional.empty()), "never: off in " + age);
            assertFalse(ToolRule.active(age, Optional.of(AgeId.DAWN)), "dawn: off in " + age);
            assertEquals(age.ordinal() < AgeId.AGE_1.ordinal(), ToolRule.active(age, Optional.of(AgeId.AGE_1)), "age_1 in " + age);
        }
    }

    @Test
    void predicate() {
        assertTrue(ToolRule.blocks(true, true, false, false), "rule on, tagged block, bare hand");
        assertFalse(ToolRule.blocks(true, true, false, true), "correct tool");
        assertFalse(ToolRule.blocks(true, false, false, false), "block outside the tag");
        assertFalse(ToolRule.blocks(true, true, true, false), "creative, spectator or machine");
        assertFalse(ToolRule.blocks(false, true, false, false), "rule off after its Age");
    }

    @Test
    void hints() {
        assertEquals(ToolRule.Hint.AXE, ToolRule.hintFor(true, false));
        assertEquals(ToolRule.Hint.AXE, ToolRule.hintFor(true, true));
        assertEquals(ToolRule.Hint.PICKAXE, ToolRule.hintFor(false, true));
        assertEquals(ToolRule.Hint.TOOL, ToolRule.hintFor(false, false));
        assertEquals("firmages.hint.needs_axe", ToolRule.Hint.AXE.key());
        assertTrue(ToolRule.hintDue(null, 0));
        assertFalse(ToolRule.hintDue(1_000L, 60_999L));
        assertTrue(ToolRule.hintDue(1_000L, 61_000L));
    }

    @Test
    void statusCarriesTheRule() {
        Optional<AgeId> stone = Optional.of(AgeId.AGE_0);
        AgeStatus dawn = AgeStatus.of(AgeSnapshot.DAWN_ONLY, t -> Optional.empty(), AgeId.AGE_1, AgeId.AGE_1, t -> Optional.empty(),
            Optional.of(AgeId.AGE_2), stone);
        assertTrue(dawn.toolRule(), "Dawn: the rule holds");
        AgeStatus stoneAge = AgeStatus.of(AgeSnapshot.of(List.of(AgeId.AGE_0), 1, 0), t -> Optional.empty(), AgeId.AGE_1, AgeId.AGE_1,
            t -> Optional.empty(), Optional.of(AgeId.AGE_2), stone);
        assertFalse(stoneAge.toolRule(), "Stone Age: off");
        assertFalse(AgeStatus.UNKNOWN.toolRule(), "before the first status the client predicts nothing");
    }
}
