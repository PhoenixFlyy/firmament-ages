package dev.firmages.core.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §40.4: the Ore Processor's operation. */
class ProcessorRulesTest {
    private static final long FE = 50_000;

    private static ProcessorRules.View view(boolean chem, boolean fluid, boolean room, boolean lub, long energy, int time) {
        return new ProcessorRules.View(false, true, chem, fluid, room, lub, energy, FE, 2.0, time);
    }

    /** Runs ticks until done; returns {ticks, FE used}. */
    private static long[] run(boolean lub, int time) {
        ProcessorRules.State s = ProcessorRules.State.IDLE;
        long fe = 0;
        for (int t = 1; t <= 1000; t++) {
            ProcessorRules.Step st = ProcessorRules.tick(s, view(true, true, true, lub, Long.MAX_VALUE, time));
            if (t == 1) assertEquals(lub, st.takeLubricant(), "the lubricant is taken at the start only when it is there");
            else assertFalse(st.takeLubricant(), "the lubricant is taken once");
            fe += st.energyUsed();
            s = st.next();
            if (st.done()) return new long[] {t, fe};
        }
        throw new AssertionError("never done");
    }

    @Test
    void lubricantBuysTimeNotPower() {
        long[] plain = run(false, 210);
        long[] fast = run(true, 210);
        assertEquals(210, plain[0]);
        assertEquals(105, fast[0], "twice as fast with lubricant");
        assertEquals(FE * 210, plain[1], "fePerTick x time");
        assertEquals(plain[1], fast[1], "the same FE per operation");
    }

    @Test
    void waitsInOrderAndKeepsProgress() {
        ProcessorRules.State s = new ProcessorRules.State(50, true, false);
        assertEquals(ProcessorRules.Reason.NEEDS_CHEMICAL, ProcessorRules.tick(s, view(false, true, true, false, FE, 100)).reason());
        assertEquals(ProcessorRules.Reason.NEEDS_FLUID, ProcessorRules.tick(s, view(true, false, true, false, FE, 100)).reason());
        assertEquals(ProcessorRules.Reason.OUTPUT_FULL, ProcessorRules.tick(s, view(true, true, false, false, FE, 100)).reason());
        ProcessorRules.Step noPower = ProcessorRules.tick(s, view(true, true, true, false, FE - 1, 100));
        assertEquals(ProcessorRules.Reason.NO_POWER, noPower.reason());
        assertEquals(50, noPower.next().progress(), "progress kept while waiting");
        assertEquals(0, noPower.energyUsed());
        ProcessorRules.Step go = ProcessorRules.tick(s, view(true, true, true, false, FE, 100));
        assertEquals(51, go.next().progress());
        assertEquals(FE, go.energyUsed());
    }

    @Test
    void noInputOrNoRecipeResets() {
        ProcessorRules.State s = new ProcessorRules.State(50, true, true);
        ProcessorRules.Step empty = ProcessorRules.tick(s, new ProcessorRules.View(true, false, true, true, true, true, FE, FE, 2.0, 100));
        assertEquals(ProcessorRules.Reason.IDLE, empty.reason());
        assertEquals(ProcessorRules.State.IDLE, empty.next());
        ProcessorRules.Step none = ProcessorRules.tick(s, new ProcessorRules.View(false, false, true, true, true, true, FE, FE, 2.0, 100));
        assertEquals(ProcessorRules.Reason.NO_RECIPE, none.reason());
        assertTrue(none.next().progress() == 0 && !none.next().started());
    }

    @Test
    void percent() {
        assertEquals(0, ProcessorRules.percent(0, 210));
        assertEquals(50, ProcessorRules.percent(105, 210));
        assertEquals(100, ProcessorRules.percent(300, 210));
    }
}
