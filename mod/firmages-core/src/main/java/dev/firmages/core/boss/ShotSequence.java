package dev.firmages.core.boss;

import org.jetbrains.annotations.Nullable;

/**
 * The order the halo fires in (round G4, SPEC §39.2), pure Java: segment after segment from Stone to Quantum, then
 * the Singularity segment when the phase allows it and the cycle is its turn ({@code phase_from}, {@code every_cycles}
 * in the table), then Stone again. A timer counts the phase's interval between two shots; a phase change restarts the
 * timer but keeps the ring's position, so no segment is skipped.
 */
public final class ShotSequence {
    private final BossShots table;
    private int index;
    private int cycle;
    private int timer;

    public ShotSequence(BossShots table) {
        this.table = table;
    }

    public int index() {
        return index;
    }

    public int cycle() {
        return cycle;
    }

    public int timer() {
        return timer;
    }

    /** The segment that fires next in {@code phase} (without advancing). */
    public ShotAge peek(int phase) {
        int i = index, c = cycle;
        for (int guard = 0; guard < ShotAge.COUNT * 2; guard++) {
            ShotAge a = ShotAge.byIndex(i);
            if (fires(a, phase, c)) return a;
            i++;
            if (i >= ShotAge.COUNT) {
                i = 0;
                c++;
            }
        }
        return ShotAge.STONE;
    }

    private boolean fires(ShotAge a, int phase, int c) {
        BossShots.Shot s = table.shot(a);
        return s.phaseFrom() <= phase && (c + 1) % s.everyCycles() == 0;
    }

    /**
     * One tick in {@code phase}: counts the interval down and, when it runs out, hands out the segment that fires now
     * and moves the ring on. @return the segment to fire, or null while the timer runs
     */
    @Nullable
    public ShotAge tick(int phase) {
        if (++timer < table.intervalTicks(phase)) return null;
        timer = 0;
        return advance(phase);
    }

    /** The segment at the ring's position (skipping those that do not fire in this phase or cycle), then the next one. */
    public ShotAge advance(int phase) {
        for (int guard = 0; guard < ShotAge.COUNT * 2; guard++) {
            ShotAge a = ShotAge.byIndex(index);
            boolean fires = fires(a, phase, cycle);
            step();
            if (fires) return a;
        }
        return ShotAge.STONE;
    }

    private void step() {
        if (++index >= ShotAge.COUNT) {
            index = 0;
            cycle++;
        }
    }

    /** A new phase: the next shot comes after a full interval; the ring keeps its place. */
    public void restart() {
        timer = 0;
    }

    public void load(int index, int cycle, int timer) {
        this.index = Math.floorMod(index, ShotAge.COUNT);
        this.cycle = Math.max(0, cycle);
        this.timer = Math.max(0, timer);
    }
}
