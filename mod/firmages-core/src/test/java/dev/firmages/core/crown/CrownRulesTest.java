package dev.firmages.core.crown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §27.13 U: chord matching, swing, the forge cycle with the night factor, slot assignment. */
class CrownRulesTest {
    private static final int TRIAD = 0b111;

    private static CrownRules.View view(String recipe, int lenses, boolean sky, boolean night) {
        return new CrownRules.View(true, true, recipe, TRIAD, 100, lenses, true, true, sky, night, 0.5);
    }

    /** Ticks until the forging completes (or -1 after 1000). */
    private static int run(CrownRules.View v) {
        CrownRules.Forge f = CrownRules.Forge.IDLE;
        for (int t = 1; t <= 1000; t++) {
            CrownRules.Step s = CrownRules.tick(f, v);
            if (s.done()) return t;
            f = s.next();
        }
        return -1;
    }

    @Test
    void exactChordOnly() {
        assertTrue(CrownRules.chordMatches(TRIAD, TRIAD));
        assertFalse(CrownRules.chordMatches(0b011, TRIAD), "two of three");
        assertFalse(CrownRules.chordMatches(0b1111, TRIAD), "a fourth lens makes another chord");
        assertFalse(CrownRules.chordMatches(0, 0), "no recipe");
        assertEquals(2, CrownRules.firstMissing(0b011, TRIAD));
        assertEquals(3, CrownRules.firstExtra(0b1111, TRIAD));
        assertEquals(-1, CrownRules.firstMissing(TRIAD, TRIAD));
    }

    @Test
    void duplicatesCountOnceAndOnlyFullyIn() {
        int[] lenses = {0, 1, 1, 2, -1};
        assertEquals(TRIAD, CrownRules.lensesIn(lenses, new boolean[] {true, true, true, true, true}));
        assertEquals(0b011, CrownRules.lensesIn(lenses, new boolean[] {true, false, true, false, true}));
    }

    @Test
    void swingCompletes() {
        int p = 0;
        for (int i = 0; i < 9; i++) p = CrownRules.swing(p, true, 10);
        assertFalse(CrownRules.fullyIn(p, 10), "nine ticks of ten");
        p = CrownRules.swing(p, true, 10);
        assertTrue(CrownRules.fullyIn(p, 10));
        assertEquals(10, CrownRules.swing(p, true, 10), "stays in");
        p = CrownRules.swing(p, false, 10);
        assertFalse(CrownRules.fullyIn(p, 10), "leaving at once stops counting");
        for (int i = 0; i < 20; i++) p = CrownRules.swing(p, false, 10);
        assertEquals(0, p);
    }

    @Test
    void nightFactorAndRounding() {
        assertEquals(100, run(view("r", TRIAD, true, false)));
        assertEquals(50, run(view("r", TRIAD, true, true)), "night halves the time");
        assertEquals(50, CrownRules.ticksFor(100, true, 0.5));
        assertEquals(30, CrownRules.ticksFor(100, true, 0.3));
        assertEquals(34, CrownRules.ticksFor(101, true, 0.333));
        assertEquals(100, CrownRules.ticksFor(100, false, 0.1), "day ignores the factor");
        assertEquals(10, CrownRules.ticksFor(100, true, 0.01), "the factor is clamped to 0.1");
        assertEquals(45, CrownRules.percent(45.9, 100));
    }

    @Test
    void chordChangeResetsAndSkyPauses() {
        CrownRules.Forge f = CrownRules.Forge.IDLE;
        for (int i = 0; i < 40; i++) f = CrownRules.tick(f, view("r", TRIAD, true, false)).next();
        assertEquals(40, f.progress(), 1e-9);
        CrownRules.Step covered = CrownRules.tick(f, view("r", TRIAD, false, false));
        assertEquals(CrownRules.Reason.SKY_HIDDEN, covered.reason());
        assertEquals(40, covered.next().progress(), 1e-9, "a covered sky pauses and keeps the progress");
        CrownRules.Step changed = CrownRules.tick(covered.next(), view("r", 0b1111, true, false));
        assertEquals(CrownRules.Reason.WRONG_CHORD, changed.reason());
        assertEquals(0, changed.next().progress(), 1e-9, "a changed chord resets");
        CrownRules.Step back = CrownRules.tick(changed.next(), view("r", TRIAD, true, false));
        assertEquals(1, back.next().progress(), 1e-9, "the chord back starts from zero");
        assertEquals(1, CrownRules.tick(back.next(), view("other", TRIAD, true, false)).next().progress(), 1e-9,
            "another recipe starts from zero");
        assertEquals(CrownRules.Reason.NO_RECIPE, CrownRules.tick(f, view(null, TRIAD, true, false)).reason());
    }

    @Test
    void reasonsInOrder() {
        CrownRules.Forge f = CrownRules.Forge.IDLE;
        assertEquals(CrownRules.Reason.DISABLED,
            CrownRules.tick(f, new CrownRules.View(false, false, "r", TRIAD, 100, TRIAD, false, false, false, false, 0.5)).reason());
        assertEquals(CrownRules.Reason.UNFORMED,
            CrownRules.tick(f, new CrownRules.View(true, false, "r", TRIAD, 100, TRIAD, true, true, true, false, 0.5)).reason());
        assertEquals(CrownRules.Reason.NO_FLUID,
            CrownRules.tick(f, new CrownRules.View(true, true, "r", TRIAD, 100, TRIAD, false, true, true, false, 0.5)).reason());
        assertEquals(CrownRules.Reason.OUTPUT_FULL,
            CrownRules.tick(f, new CrownRules.View(true, true, "r", TRIAD, 100, TRIAD, true, false, true, false, 0.5)).reason());
    }

    @Test
    void slotsAssignToDistinctIngredients() {
        // slots 0 and 2 filled; ingredient 0 fits slot 2, ingredient 1 fits slot 0
        CrownRules.Fits fits = (s, g) -> (s == 0 && g == 1) || (s == 2 && g == 0);
        assertArrayEquals(new int[] {1, 0}, CrownRules.assign(new int[] {0, 2}, 2, true, fits));
        assertNull(CrownRules.assign(new int[] {0, 2}, 3, true, fits), "complete needs every ingredient");
        assertArrayEquals(new int[] {1, 0}, CrownRules.assign(new int[] {0, 2}, 3, false, fits), "partial");
        assertNull(CrownRules.assign(new int[] {0, 2}, 2, true, (s, g) -> g == 0), "two slots cannot share an ingredient");
        assertArrayEquals(new int[] {0, 2}, CrownRules.filled(new boolean[] {false, true, false, true}));
    }
}
