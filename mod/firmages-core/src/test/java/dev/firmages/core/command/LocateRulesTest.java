package dev.firmages.core.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §10.3: radius, distance, order, names and the click command of /firmages locate. */
class LocateRulesTest {

    @Test
    void radiusIsClamped() {
        assertEquals(512, LocateRules.DEFAULT_RADIUS);
        assertEquals(2048, LocateRules.clampRadius(5000));
        assertEquals(1, LocateRules.clampRadius(0));
        assertEquals(300, LocateRules.clampRadius(300));
    }

    @Test
    void horizontalDistanceFromTheBlockCentre() {
        assertEquals(0, LocateRules.distance(10.5, 20.5, 10, 20));
        assertEquals(5, LocateRules.distance(0.5, 0.5, 3, 4));
        assertTrue(LocateRules.within(0.5, 0.5, 300, 400, 500));
        assertFalse(LocateRules.within(0.5, 0.5, 300, 401, 500), "just outside the circle");
        assertFalse(LocateRules.within(0.5, 0.5, 500, 500, 500), "the corner of the square lies outside the circle");
    }

    @Test
    void chunkRangeCoversTheRadius() {
        assertArrayEquals(new int[] {-1, 1, -1, 1}, LocateRules.chunkRange(0.5, 0.5, 16));
        assertArrayEquals(new int[] {-32, 32, 0, 64}, LocateRules.chunkRange(8, 520, 512));
    }

    @Test
    void nearestFirstAndStable() {
        List<LocateRules.Hit> hits = List.of(new LocateRules.Hit("b", 5, 64, 0, 40), new LocateRules.Hit("a", 1, 64, 0, 10),
            new LocateRules.Hit("c", 2, 64, 0, 40), new LocateRules.Hit("a", 1, 64, 1, 40));
        assertEquals(List.of(hits.get(1), hits.get(3), hits.get(0), hits.get(2)), LocateRules.sorted(hits, h -> h));
    }

    @Test
    void namesAndClicks() {
        assertEquals("Cold Forge", LocateRules.pretty("cold_forge"));
        assertEquals("Forest Ring", LocateRules.pretty("wayshrine/forest_ring"));
        assertEquals("Ruin Basement", LocateRules.pretty("ruin_basement"));
        assertEquals("/tp @s 10 70 -20", LocateRules.teleport(10, 70, -20));
        assertEquals("/spreadplayers 10 -20 0 1 false @s", LocateRules.teleport(10, LocateRules.UNKNOWN_Y, -20), "unknown height: the top block");
        assertEquals("10 ~ -20", LocateRules.coords(10, LocateRules.UNKNOWN_Y, -20));
        assertTrue(LocateRules.inBox(10, 10, 0, 0, 8, 8, 2));
        assertFalse(LocateRules.inBox(11, 10, 0, 0, 8, 8, 2));
    }
}
