package dev.firmages.core.launch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §31.10 U: the throttle steps, the redstone mapping, the flight as work on fuel, the start order, the trench. */
class ThrottleMathTest {
    private static final List<LaunchRules.Step> STEPS = LaunchRules.defaultSteps();
    private static final int WORK = 12_000;

    @Test
    void specNumbers() {
        // SPEC §31.4: flight_work 12,000 takes 10, 5 or 2.5 minutes for 12, 18 or 24 buckets
        assertEquals(12_000, LaunchRules.flightTicks(WORK, STEPS.get(0)));
        assertEquals(6_000, LaunchRules.flightTicks(WORK, STEPS.get(1)));
        assertEquals(3_000, LaunchRules.flightTicks(WORK, STEPS.get(2)));
        assertEquals(12_000, LaunchRules.flightFuel(WORK, STEPS.get(0)));
        assertEquals(18_000, LaunchRules.flightFuel(WORK, STEPS.get(1)));
        assertEquals(24_000, LaunchRules.flightFuel(WORK, STEPS.get(2)));
    }

    @Test
    void redstoneSplitsEvenly() {
        assertEquals(0, LaunchRules.stepFromRedstone(0, 3));
        for (int s = 1; s <= 5; s++) assertEquals(1, LaunchRules.stepFromRedstone(s, 3), "strength " + s);
        for (int s = 6; s <= 10; s++) assertEquals(2, LaunchRules.stepFromRedstone(s, 3), "strength " + s);
        for (int s = 11; s <= 15; s++) assertEquals(3, LaunchRules.stepFromRedstone(s, 3), "strength " + s);
        assertEquals(1, LaunchRules.stepFromRedstone(15, 1));
        assertEquals(15, LaunchRules.stepFromRedstone(15, 15));
        assertEquals(0, LaunchRules.stepFromRedstone(7, 0));
    }

    @Test
    void handAndRedstone() {
        assertEquals(2, LaunchRules.nextStep(1, 3));
        assertEquals(3, LaunchRules.nextStep(2, 3));
        assertEquals(1, LaunchRules.nextStep(3, 3), "wraps");
        assertEquals(1, LaunchRules.nextStep(9, 3), "a step beyond a shorter file wraps");
        assertEquals(3, LaunchRules.effectiveStep(1, 3, 3), "redstone wins");
        assertEquals(2, LaunchRules.effectiveStep(2, 0, 3), "no signal: the hand setting");
        assertEquals(3, LaunchRules.effectiveStep(5, 0, 3), "clamped");
    }

    /** Runs a flight with {@code fuel} mB; returns {progress, fuel left, ticks}. */
    private static long[] fly(long fuel, LaunchRules.Step step, int maxTicks) {
        long progress = 0;
        int t = 0;
        while (t < maxTicks) {
            LaunchRules.FlightTick ft = LaunchRules.flight(progress, WORK, fuel, step);
            if (ft.paused()) break;
            fuel -= ft.burnt();
            progress = ft.progress();
            t++;
            if (ft.done()) break;
        }
        return new long[] {progress, fuel, t};
    }

    @Test
    void flightIsWorkOnFuel() {
        long[] full = fly(30_000, STEPS.get(2), 100_000);
        assertEquals(WORK, full[0]);
        assertEquals(30_000 - 24_000, full[1]);
        assertEquals(3_000, full[2]);
        long[] dry = fly(4_000, STEPS.get(2), 100_000);
        assertEquals(2_000, dry[0], "4000 mB at step 3 flies 500 ticks of 4 progress");
        assertEquals(0, dry[1]);
        LaunchRules.FlightTick paused = LaunchRules.flight(2_000, WORK, 7, STEPS.get(2));
        assertTrue(paused.paused(), "less fuel than one tick needs pauses");
        assertEquals(2_000, paused.progress(), "nothing is lost");
        assertEquals(0, paused.burnt());
        LaunchRules.FlightTick resumed = LaunchRules.flight(2_000, WORK, 8, STEPS.get(2));
        assertFalse(resumed.paused());
        assertEquals(2_004, resumed.progress());
    }

    @Test
    void throttleShortensFlight() {
        assertTrue(LaunchRules.flightTicks(WORK, STEPS.get(2)) < LaunchRules.flightTicks(WORK, STEPS.get(1)));
        assertTrue(LaunchRules.flightTicks(WORK, STEPS.get(1)) < LaunchRules.flightTicks(WORK, STEPS.get(0)));
        assertTrue(LaunchRules.flightFuel(WORK, STEPS.get(2)) > LaunchRules.flightFuel(WORK, STEPS.get(0)), "speed costs fuel");
    }

    @Test
    void startOrder() {
        LaunchRules.Start ok = new LaunchRules.Start(true, true, true, 2, true, true, true, true, true, true, false);
        assertEquals(LaunchRules.Wait.NONE, LaunchRules.startWait(ok));
        assertEquals(LaunchRules.Wait.SKY_CLOSED, LaunchRules.startWait(new LaunchRules.Start(true, true, true, 2, true, true, true, true, true, false, false)));
        assertEquals(LaunchRules.Wait.HELD, LaunchRules.startWait(new LaunchRules.Start(true, true, true, 2, true, true, true, true, true, true, true)));
        assertEquals(LaunchRules.Wait.PORTS, LaunchRules.startWait(new LaunchRules.Start(true, true, true, 0, true, true, true, true, true, true, false)));
        assertEquals(LaunchRules.Wait.PORTS, LaunchRules.startWait(new LaunchRules.Start(true, true, true, 5, true, true, true, true, true, true, false)));
        assertEquals(LaunchRules.Wait.DISABLED, LaunchRules.startWait(new LaunchRules.Start(false, false, false, 0, false, false, false, false, false, false, true)));
        assertEquals(LaunchRules.Wait.FUEL, LaunchRules.startWait(new LaunchRules.Start(true, true, true, 1, true, true, true, false, true, true, false)));
    }

    @Test
    void easing() {
        assertEquals(0, LaunchRules.ascentHeight(0));
        assertEquals(LaunchRules.RISE, LaunchRules.ascentHeight(LaunchRules.ASCENT_TICKS));
        assertTrue(LaunchRules.ascentHeight(LaunchRules.ASCENT_TICKS / 2.0) < LaunchRules.RISE / 2.0, "slow off the pad");
        assertEquals(LaunchRules.RISE, LaunchRules.descentHeight(0));
        assertEquals(0, LaunchRules.descentHeight(LaunchRules.DESCENT_TICKS));
        assertEquals(45, LaunchRules.percent(5_400, WORK));
    }

    @Test
    void trenchShape() {
        // three channel layers of 3 x 3, a 3 x 3 opening, walls: back 3 + sides 6 per layer, floor 9
        assertEquals(3 * 9, TrenchShape.CELLS.stream().filter(c -> c.kind() == TrenchShape.Kind.CHANNEL).count());
        assertEquals(9, TrenchShape.CELLS.stream().filter(c -> c.kind() == TrenchShape.Kind.OPENING).count());
        assertEquals(3 * 9 + 9, TrenchShape.wallCount());
        assertNull(TrenchShape.firstFailure(c -> true));
        TrenchShape.Cell f = TrenchShape.firstFailure(c -> !(c.kind() == TrenchShape.Kind.OPENING && c.depth() == 2));
        assertEquals(TrenchShape.Kind.OPENING, f.kind());
    }
}
