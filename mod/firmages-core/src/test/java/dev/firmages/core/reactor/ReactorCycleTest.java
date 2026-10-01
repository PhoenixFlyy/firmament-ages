package dev.firmages.core.reactor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level U: the controller state machine and the fuel arithmetic (SPEC §6.2, §12 m4). */
class ReactorCycleTest {
    /** A reactor that only changes when the test says so; shutdown() moves RUNNING to STOPPING like DE. */
    static final class Fake implements ReactorView {
        Phase phase = Phase.COLD;
        double fuel;
        double chaos;
        int shutdowns;

        Fake(Phase phase, double fuel, double chaos) {
            this.phase = phase;
            this.fuel = fuel;
            this.chaos = chaos;
        }

        @Override public Phase phase() { return phase; }
        @Override public double fuel() { return fuel; }
        @Override public double chaos() { return chaos; }

        @Override
        public void shutdown() {
            shutdowns++;
            if (phase.online()) phase = Phase.STOPPING;
        }

        @Override public void addFuel(int amount) { fuel += amount; }
        @Override public void removeChaos(int amount) { chaos -= amount; }
    }

    /** Swapper with a fixed stock of fuel items and unlimited chaos output, like the block entity with full input. */
    static final class Stock implements ReactorCycle.Swapper {
        int blocks;
        int nuggets;
        int chaosOut;
        int calls;

        Stock(int blocks, int nuggets) {
            this.blocks = blocks;
            this.nuggets = nuggets;
        }

        @Override
        public void swap(ReactorView r, ReactorCycle.Settings s) {
            calls++;
            int[] split = ReactorFuel.chaosSplit(r.chaos() - s.maxChaos());
            for (int i = 0; i < 3; i++) {
                r.removeChaos(split[i] * ReactorFuel.UNITS[i]);
                chaosOut += split[i] * ReactorFuel.UNITS[i];
            }
            int b = Math.min(ReactorFuel.fits(ReactorFuel.BLOCK, blocks, r.fuel(), r.chaos()),
                (int) Math.ceil(Math.max(0, s.minFuel() - r.fuel()) / ReactorFuel.BLOCK));
            r.addFuel(b * ReactorFuel.BLOCK);
            blocks -= b;
            int n = Math.min(ReactorFuel.fits(ReactorFuel.NUGGET, nuggets, r.fuel(), r.chaos()),
                (int) Math.ceil(Math.max(0, s.minFuel() - r.fuel()) / ReactorFuel.NUGGET));
            r.addFuel(n * ReactorFuel.NUGGET);
            nuggets -= n;
        }
    }

    private static final ReactorCycle.Settings S = ReactorCycle.Settings.DEFAULT;

    @Test
    void fullCycleShutsDownSwapsAndSignalsReadyButNeverStarts() {
        // A running reactor at 79 % conversion keeps running.
        Fake r = new Fake(ReactorView.Phase.RUNNING, 10368 * 0.21, 10368 * 0.79);
        Stock stock = new Stock(64, 64);
        ReactorCycle c = new ReactorCycle();
        assertEquals(ReactorCycle.State.MONITOR, c.tick(r, S, stock));
        assertEquals(0, r.shutdowns);
        // 80 %: shutdown requested once, DE goes STOPPING.
        r.fuel = 10368 * 0.20;
        r.chaos = 10368 * 0.80;
        assertEquals(ReactorCycle.State.SHUTDOWN, c.tick(r, S, stock));
        assertEquals(1, r.shutdowns);
        assertEquals(ReactorView.Phase.STOPPING, r.phase);
        assertEquals(ReactorCycle.State.COOLING, c.tick(r, S, stock));
        r.phase = ReactorView.Phase.COOLING;
        assertEquals(ReactorCycle.State.COOLING, c.tick(r, S, stock));
        assertEquals(0, stock.calls, "nothing is swapped before COLD");
        assertFalse(c.ready());
        // COLD: chaos out (down to the rest below 16), fuel in up to 10368 or until nothing fits.
        r.phase = ReactorView.Phase.COLD;
        assertEquals(ReactorCycle.State.READY, c.tick(r, S, stock));
        assertTrue(c.ready());
        assertTrue(r.chaos < ReactorFuel.NUGGET, "chaos left: " + r.chaos);
        assertTrue(r.fuel >= S.minFuel() || ReactorFuel.full(r.fuel, r.chaos), "fuel " + r.fuel);
        assertTrue(r.fuel + r.chaos <= ReactorFuel.CAP);
        // READY holds while cold and is never followed by a start from the controller.
        for (int i = 0; i < 10; i++) assertEquals(ReactorCycle.State.READY, c.tick(r, S, stock));
        assertEquals(ReactorView.Phase.COLD, r.phase);
        // A player starts it: back to MONITOR, no signal.
        r.phase = ReactorView.Phase.WARMING_UP;
        assertEquals(ReactorCycle.State.MONITOR, c.tick(r, S, stock));
        assertFalse(c.ready());
        assertEquals(1, r.shutdowns, "no shutdown while warming up below the threshold");
    }

    @Test
    void swapWaitsForFuelAndForChaosRoom() {
        Fake r = new Fake(ReactorView.Phase.COLD, 0, 0);
        Stock empty = new Stock(0, 0);
        ReactorCycle c = new ReactorCycle();
        assertEquals(ReactorCycle.State.SWAP, c.tick(r, S, empty));
        assertTrue(c.detail().startsWith("waiting for fuel"), c.detail());
        empty.blocks = 3;
        assertEquals(ReactorCycle.State.SWAP, c.tick(r, S, empty));
        assertEquals(3 * ReactorFuel.BLOCK, r.fuel);
        empty.blocks = 10;
        assertEquals(ReactorCycle.State.READY, c.tick(r, S, empty));
        assertEquals(10368, r.fuel);
        assertEquals(5, empty.blocks, "fills to minFuel, not beyond");
        // Someone takes fuel out while READY: the controller swaps again.
        empty.nuggets = 64;
        r.fuel = 5000;
        assertEquals(ReactorCycle.State.READY, c.tick(r, S, empty));
        assertTrue(r.fuel >= 10368 || ReactorFuel.full(r.fuel, r.chaos));
        // Chaos that cannot leave (no output room) keeps it in SWAP.
        r.chaos = 2000;
        r.fuel = 1000;
        ReactorCycle.Swapper noRoom = (re, s) -> {};
        assertEquals(ReactorCycle.State.SWAP, c.tick(r, S, noRoom));
        assertTrue(c.detail().startsWith("removing chaos"), c.detail());
    }

    @Test
    void leftoverFuelThatCannotReachMinFuelStillCountsAsFull() {
        // 2073.6 unconverted + 7.3 chaos: nuggets fill to 10361.6, one more nugget would exceed the cap.
        Fake r = new Fake(ReactorView.Phase.COLD, 2073.6, 7.3);
        ReactorCycle c = new ReactorCycle();
        assertEquals(ReactorCycle.State.READY, c.tick(r, S, new Stock(64, 64)));
        assertTrue(r.fuel < S.minFuel());
        assertTrue(ReactorFuel.full(r.fuel, r.chaos));
        assertEquals(7.3, r.chaos, 1e-9, "a chaos rest below 16 stays");
    }

    @Test
    void manualShutdownAndRestartsAreFollowed() {
        Fake r = new Fake(ReactorView.Phase.RUNNING, 2 * 1296, 6 * 1296);
        Stock stock = new Stock(64, 0);
        ReactorCycle c = new ReactorCycle();
        assertEquals(ReactorCycle.State.MONITOR, c.tick(r, S, stock));
        // A player stops it at 75 %: the controller follows to COLD and refuels.
        r.phase = ReactorView.Phase.STOPPING;
        assertEquals(ReactorCycle.State.MONITOR, c.tick(r, S, stock));
        r.phase = ReactorView.Phase.COLD;
        assertEquals(ReactorCycle.State.READY, c.tick(r, S, stock));
        // Restarted while the controller waited for cooling.
        r.phase = ReactorView.Phase.RUNNING;
        r.chaos = 9000;
        r.fuel = 1368;
        assertEquals(ReactorCycle.State.MONITOR, c.tick(r, S, stock));
        assertEquals(ReactorCycle.State.SHUTDOWN, c.tick(r, S, stock));
        r.phase = ReactorView.Phase.COOLING;
        assertEquals(ReactorCycle.State.COOLING, c.tick(r, S, stock));
        r.phase = ReactorView.Phase.WARMING_UP;
        assertEquals(ReactorCycle.State.MONITOR, c.tick(r, S, stock));
        // No reactor and broken structures drop to MONITOR without touching anything.
        assertEquals(ReactorCycle.State.MONITOR, c.tick(null, S, stock));
        r.phase = ReactorView.Phase.INVALID;
        assertEquals(ReactorCycle.State.MONITOR, c.tick(r, S, stock));
        r.phase = ReactorView.Phase.BEYOND_HOPE;
        assertEquals(ReactorCycle.State.MONITOR, c.tick(r, S, stock));
    }

    @Test
    void thresholdIsConfigurable() {
        ReactorCycle.Settings half = new ReactorCycle.Settings(0.5, 5000, 0);
        Fake r = new Fake(ReactorView.Phase.RUNNING, 5000, 5000);
        ReactorCycle c = new ReactorCycle();
        assertEquals(ReactorCycle.State.SHUTDOWN, c.tick(r, half, new Stock(0, 0)));
        Fake r2 = new Fake(ReactorView.Phase.RUNNING, 5000, 5000);
        assertEquals(ReactorCycle.State.MONITOR, new ReactorCycle().tick(r2, S, new Stock(0, 0)));
        // maxChaos lets some chaos stay.
        Fake cold = new Fake(ReactorView.Phase.COLD, 5000, 500);
        assertTrue(ReactorCycle.targetsMet(cold, new ReactorCycle.Settings(0.8, 5000, 500)));
        assertFalse(ReactorCycle.targetsMet(cold, new ReactorCycle.Settings(0.8, 5000, 0)));
    }

    @Test
    void fuelArithmeticMatchesTheDraconicGui() {
        assertEquals(1296, ReactorFuel.fuelValue(ReactorFuel.FUEL_BLOCK));
        assertEquals(144, ReactorFuel.fuelValue(ReactorFuel.FUEL_INGOT));
        assertEquals(16, ReactorFuel.fuelValue(ReactorFuel.FUEL_NUGGET));
        assertEquals(0, ReactorFuel.fuelValue("draconicevolution:draconium_block"));
        assertEquals(1296, ReactorFuel.chaosValue(ReactorFuel.CHAOS_LARGE));
        assertEquals(0, ReactorFuel.chaosValue("draconicevolution:chaos_shard"));
        assertEquals(10383, ReactorFuel.room(0, 0));
        assertEquals(8, ReactorFuel.fits(ReactorFuel.BLOCK, 64, 0, 0));
        assertEquals(0, ReactorFuel.fits(ReactorFuel.BLOCK, 64, 9100, 0));
        assertEquals(80, ReactorFuel.fits(ReactorFuel.NUGGET, 80, 9100, 0));
        assertArrayEquals(new int[] {6, 3, 2}, ReactorFuel.chaosSplit(6 * 1296 + 3 * 144 + 2 * 16 + 15.9));
        assertArrayEquals(new int[] {0, 0, 0}, ReactorFuel.chaosSplit(15.99));
        assertTrue(ReactorFuel.full(10368, 0));
        assertFalse(ReactorFuel.full(10367 - 16, 0));
        assertEquals(15, ReactorFuel.comparator(10368, 0));
        assertEquals(3, ReactorFuel.comparator(2000, 8000));
        assertEquals(0, ReactorFuel.comparator(0, 0));
        assertEquals(0.8, ReactorFuel.conversion(2000, 8000), 1e-9);
    }
}
