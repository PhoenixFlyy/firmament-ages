package dev.firmages.core.anchor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level U (SPEC-himmel §29.3, §29.5): the shard mask of a Sky Anchor (one shard per tier, the highest taken back first)
 * and when it acts (from the Space Age, with the shrine intact, only the shards of awakened tiers).
 */
class AnchorRulesTest {
    @Test
    void oneShardPerTier() {
        int t = AnchorRules.with(0, 0);
        t = AnchorRules.with(t, 2);
        assertEquals(0b101, t);
        assertEquals(t, AnchorRules.with(t, 2), "a second shard of tier 2 changes nothing");
        assertTrue(AnchorRules.holds(t, 2));
        assertFalse(AnchorRules.holds(t, 1));
        assertEquals(t, AnchorRules.with(t, 9), "no tier 9");
        assertEquals(t, AnchorRules.with(t, -1), "no tier -1");
        assertEquals(2, AnchorRules.count(t));
    }

    @Test
    void highestComesBackFirst() {
        assertEquals(-1, AnchorRules.highest(0));
        assertEquals(2, AnchorRules.highest(0b101));
        assertEquals(8, AnchorRules.highest(0b1_1111_1111));
        assertEquals(0, AnchorRules.highest(0b1));
    }

    @Test
    void activeOnlyFromTheSpaceAgeWithAnIntactShrine() {
        int shards = 0b101; // tiers 0 and 2
        assertEquals(0b101, AnchorRules.activeMask(true, true, 9, shards));
        assertEquals(0, AnchorRules.activeMask(false, true, 9, shards), "quiet below age_7");
        assertEquals(0, AnchorRules.activeMask(true, false, 9, shards), "quiet while a ring is broken");
        assertEquals(0b001, AnchorRules.activeMask(true, true, 2, shards), "tier 2 not awakened: its shard is inert");
    }

    @Test
    void statusNamesTheReason() {
        assertEquals(AnchorRules.Status.BEFORE_AGE, AnchorRules.status(false, true, 9, 0b1));
        assertEquals(AnchorRules.Status.BROKEN, AnchorRules.status(true, false, 9, 0b1));
        assertEquals(AnchorRules.Status.BROKEN, AnchorRules.status(true, true, 0, 0b1));
        assertEquals(AnchorRules.Status.EMPTY, AnchorRules.status(true, true, 9, 0));
        assertEquals(AnchorRules.Status.SLEEPING, AnchorRules.status(true, true, 3, 0b1_0000_0000));
        assertEquals(AnchorRules.Status.ACTIVE, AnchorRules.status(true, true, 3, 0b1_0000_0100));
    }
}
