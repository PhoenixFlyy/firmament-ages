package dev.firmages.core.wayshrine;

import dev.firmages.core.text.Numbers;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level U: the pointer's bearing and distance, the tutorial search and site test, and the number words. */
class WayshrineRulesTest {
    @Test
    void bearings() {
        assertEquals("north", WayshrineRules.bearing(0, 0, 0, -100));
        assertEquals("east", WayshrineRules.bearing(0, 0, 100, 0));
        assertEquals("south", WayshrineRules.bearing(0, 0, 0, 100));
        assertEquals("west", WayshrineRules.bearing(0, 0, -100, 0));
        assertEquals("north_east", WayshrineRules.bearing(0, 0, 100, -100));
        assertEquals("south_west", WayshrineRules.bearing(10, 10, -90, 110));
        assertEquals("north", WayshrineRules.bearing(0, 0, 20, -100), "within 22.5 degrees of north");
        assertEquals("north_east", WayshrineRules.bearing(0, 0, 50, -100));
    }

    @Test
    void distances() {
        assertEquals(1200, WayshrineRules.roundedDistance(1234));
        assertEquals(450, WayshrineRules.roundedDistance(460));
        assertEquals(40, WayshrineRules.roundedDistance(37));
        assertEquals(10, WayshrineRules.roundedDistance(2));
        assertEquals("1,200", Numbers.words(WayshrineRules.roundedDistance(1234)));
    }

    @Test
    void tutorialSearchTriesEveryDistanceOnEveryBearing() {
        assertEquals(160, WayshrineRules.TUTORIAL_SITES, "five distances on 32 bearings");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < WayshrineRules.TUTORIAL_SITES; i++) {
            int[] p = WayshrineRules.tutorialPoint(3456, 1408, 1.0, 2, i);
            double d = Math.hypot(p[0] - 3456, p[1] - 1408);
            assertTrue(d >= 299 && d <= 801, "site " + i + " at " + d);
            long distance = Math.round(d);
            assertTrue(Arrays.stream(WayshrineRules.TUTORIAL_DISTANCES).anyMatch(x -> Math.abs(x - distance) <= 1), "site " + i + " on a ring: " + d);
            double deg = Math.toDegrees(Math.atan2(p[0] - 3456, -(p[1] - 1408)) - 1.0);
            long bearing = Math.floorMod(Math.round(deg / (360.0 / WayshrineRules.TUTORIAL_BEARINGS)), WayshrineRules.TUTORIAL_BEARINGS);
            assertTrue(seen.add(bearing + "@" + (Math.round(d / 25.0) * 25)), "site " + i + " is new: bearing " + bearing + ", " + d);
        }
        assertEquals(160, seen.size(), "every distance on every bearing, each once");
    }

    @Test
    void tutorialSearchWalksOutwardOnOneBearing() {
        // first distance 300, start bearing north: the first five sites are 300, 425, 550, 675 and 800 blocks north
        for (int i = 0; i < 5; i++) {
            int[] p = WayshrineRules.tutorialPoint(0, 0, 0, 0, i);
            assertEquals(0, p[0], "site " + i + " stays on the north bearing");
            assertEquals(-WayshrineRules.TUTORIAL_DISTANCES[i], p[1], "site " + i);
        }
        // the sixth site is the next bearing, 13 of 32 steps on (146.25 degrees: south-east), one distance further out
        int[] next = WayshrineRules.tutorialPoint(0, 0, 0, 0, 5);
        assertEquals("south_east", WayshrineRules.bearing(0, 0, next[0], next[1]));
        assertEquals(425, Math.round(Math.hypot(next[0], next[1])));
        // the first distance of a pass is its own: index 3 starts at 675 and wraps to 300
        assertEquals(-675, WayshrineRules.tutorialPoint(0, 0, 0, 3, 0)[1]);
        assertEquals(-300, WayshrineRules.tutorialPoint(0, 0, 0, 3, 2)[1]);
        assertEquals(-800, WayshrineRules.tutorialPoint(0, 0, 0, 0, 4)[1], "the outer ring is 800 (bearing north)");
    }

    @Test
    void sites() {
        assertTrue(WayshrineRules.fitSite(new int[] {70, 71, 72, 73, 70}, false));
        assertFalse(WayshrineRules.fitSite(new int[] {70, 74}, false), "four blocks of spread is too steep");
        assertFalse(WayshrineRules.fitSite(new int[] {70, 70}, true), "water is no land");
        assertEquals(71, WayshrineRules.floorY(new int[] {70, 71, 72, 73}), "the floor sits one below the rounded mean surface");
    }

    @Test
    void tutorialOnlyOnNewWorlds() {
        assertTrue(WayshrineRules.tutorialWorldFresh(0), "the first start of a new world");
        assertTrue(WayshrineRules.tutorialWorldFresh(3 * 24000L), "still within three days");
        assertFalse(WayshrineRules.tutorialWorldFresh(3 * 24000L + 1), "an older world has a history");
        assertFalse(WayshrineRules.tutorialWorldFresh(40_000_000L), "an existing world updating to 0.11.0");
    }

    @Test
    void numberWords() {
        assertEquals("seven", Numbers.words(7));
        assertEquals("twelve", Numbers.words(12));
        assertEquals("13", Numbers.words(13));
        assertEquals("Seven", Numbers.capitalized(7));
        assertEquals("24", Numbers.capitalized(24));
    }
}
