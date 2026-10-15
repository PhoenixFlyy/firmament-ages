package dev.firmages.core.wayshrine;

import dev.firmages.core.text.Numbers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level U: the pointer's bearing and distance, the tutorial ring and site test, and the number words. */
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
    void tutorialRing() {
        for (int i = 0; i < WayshrineRules.TUTORIAL_TRIES; i++) {
            int[] p = WayshrineRules.tutorialPoint(3456, 1408, 1.0, 500, i);
            double d = Math.hypot(p[0] - 3456, p[1] - 1408);
            assertTrue(d >= 299 && d <= 801, "try " + i + " at " + d);
        }
        int[] far = WayshrineRules.tutorialPoint(0, 0, 0, 5000, 0);
        assertEquals(-800, far[1], "the distance is clamped to 800 (bearing north)");
        int[] a = WayshrineRules.tutorialPoint(0, 0, 0, 400, 0);
        int[] b = WayshrineRules.tutorialPoint(0, 0, 0, 400, 4);
        assertEquals(0, a[0]);
        assertEquals(400, b[0], "four tries later the bearing turned by a quarter");
    }

    @Test
    void sites() {
        assertTrue(WayshrineRules.fitSite(new int[] {70, 71, 72, 73, 70}, false));
        assertFalse(WayshrineRules.fitSite(new int[] {70, 74}, false), "four blocks of spread is too steep");
        assertFalse(WayshrineRules.fitSite(new int[] {70, 70}, true), "water is no land");
        assertEquals(71, WayshrineRules.floorY(new int[] {70, 71, 72, 73}), "the floor sits one below the rounded mean surface");
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
