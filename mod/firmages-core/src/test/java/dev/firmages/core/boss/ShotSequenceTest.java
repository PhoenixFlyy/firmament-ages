package dev.firmages.core.boss;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Round G4 (SPEC §39.2): the ring fires Stone to Quantum in order, the black hole only in phase 3 and one cycle in three. */
class ShotSequenceTest {
    private static final BossShots T = BossShotsTest.packTable();

    private static List<ShotAge> fired(ShotSequence s, int phase, int ticks) {
        List<ShotAge> out = new ArrayList<>();
        for (int t = 0; t < ticks; t++) {
            ShotAge a = s.tick(phase);
            if (a != null) out.add(a);
        }
        return out;
    }

    @Test
    void phaseOneFiresNineAgesInOrderEveryOneAndAHalfSeconds() {
        ShotSequence s = new ShotSequence(T);
        for (int t = 0; t < 29; t++) assertNull(s.tick(1), "nothing before the interval runs out, tick " + t);
        assertEquals(ShotAge.STONE, s.tick(1), "the first shot after 30 ticks");
        List<ShotAge> rest = fired(s, 1, 30 * 9);
        assertEquals(List.of(ShotAge.BRONZE, ShotAge.IRON, ShotAge.ARCANE, ShotAge.INDUSTRIAL, ShotAge.ELECTRIC, ShotAge.INFORMATION, ShotAge.SPACE,
            ShotAge.QUANTUM, ShotAge.STONE), rest, "no Singularity before phase 3, then Stone again");
    }

    @Test
    void phaseThreeAddsTheBlackHoleEveryThirdCycle() {
        ShotSequence s = new ShotSequence(T);
        List<ShotAge> shots = fired(s, 3, 14 * 29);
        assertEquals(29, shots.size(), "a shot every 0.7 s");
        long holes = shots.stream().filter(a -> a == ShotAge.SINGULARITY).count();
        assertEquals(1, holes, "cycles 1 and 2 skip it, cycle 3 fires it: " + shots);
        assertEquals(ShotAge.SINGULARITY, shots.get(27), "the tenth slot of the third cycle (9 + 9 + 9 shots before it)");
        assertEquals(ShotAge.STONE, shots.get(28), "then Stone again");
        for (int i = 0; i < 9; i++) assertEquals(ShotAge.byIndex(i), shots.get(i), "cycle 1 in Age order");
    }

    @Test
    void aPhaseChangeRestartsTheTimerAndKeepsThePlace() {
        ShotSequence s = new ShotSequence(T);
        fired(s, 1, 30 * 3); // Stone, Bronze, Iron fired
        assertEquals(3, s.index());
        for (int t = 0; t < 10; t++) s.tick(1);
        s.restart();
        assertEquals(0, s.timer());
        assertEquals(ShotAge.ARCANE, s.peek(2), "the ring stands at Arcane");
        assertEquals(List.of(ShotAge.ARCANE), fired(s, 2, 20), "phase 2: 1.0 s to the next shot");
    }

    @Test
    void loadRestoresThePosition() {
        ShotSequence s = new ShotSequence(T);
        s.load(8, 2, 13);
        assertEquals(ShotAge.QUANTUM, s.tick(3), "timer 13 of 14: fires on the next tick, Quantum");
        assertEquals(ShotAge.SINGULARITY, s.advance(3), "cycle 3 (index 2) has the black hole");
        assertEquals(0, s.index());
        assertEquals(3, s.cycle());
    }
}
