package dev.firmages.core.compat.eft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §20.2: trunk and branches of one TFC, AFC or Beneath tree fall together. */
class TreeFamilyTest {
    @Test
    void trunkAndBranchesOfOneSpecies() {
        assertTrue(TreeFamily.sameTree("tfc:wood/log/oak", "tfc:wood/wood/oak"));
        assertTrue(TreeFamily.sameTree("tfc:wood/wood/kapok", "tfc:wood/log/kapok"), "starting at a branch");
        assertTrue(TreeFamily.sameTree("afc:wood/log/kauri", "afc:wood/wood/kauri"));
        assertTrue(TreeFamily.sameTree("beneath:wood/log/crimson", "beneath:wood/wood/crimson"));
        assertTrue(TreeFamily.sameTree("tfc:wood/log/oak", "tfc:wood/log/oak"));
    }

    @Test
    void otherBlocksStayApart() {
        assertFalse(TreeFamily.sameTree("tfc:wood/log/oak", "tfc:wood/wood/birch"), "other species");
        assertFalse(TreeFamily.sameTree("tfc:wood/log/oak", "afc:wood/wood/oak"), "other namespace");
        assertFalse(TreeFamily.sameTree("tfc:wood/stripped_log/oak", "tfc:wood/log/oak"), "stripped");
        assertFalse(TreeFamily.sameTree("minecraft:oak_log", "minecraft:oak_wood"), "vanilla trees have no branch blocks");
        assertFalse(TreeFamily.sameTree("minecraft:oak_log", "tfc:wood/log/oak"));
        assertFalse(TreeFamily.sameTree(null, "tfc:wood/log/oak"));
        assertFalse(TreeFamily.sameTree("tfc:wood/log/oak", null));
    }

    @Test
    void speciesOfAnId() {
        assertEquals("tfc:oak", TreeFamily.species("tfc:wood/log/oak"));
        assertEquals("afc:kauri", TreeFamily.species("afc:wood/wood/kauri"));
        assertNull(TreeFamily.species("tfc:wood/log/"));
        assertNull(TreeFamily.species("tfc:wood/log/oak/extra"));
        assertNull(TreeFamily.species("wood/log/oak"), "no namespace");
        assertNull(TreeFamily.species("tfc:wood/leaves/oak"));
        assertTrue(TreeFamily.MAX_HEIGHT >= 43, "the tallest tree of the pack");
    }
}
