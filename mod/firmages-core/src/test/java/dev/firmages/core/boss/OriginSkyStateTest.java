package dev.firmages.core.boss;

import dev.firmages.core.boss.OriginSkyState.Mood;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Round G3 (SPEC §16.6): the sky's mood per phase and the 2 s fade. */
class OriginSkyStateTest {
    @Test
    void moodPerPhaseWithATwoSecondFade() {
        OriginSkyState s = new OriginSkyState();
        assertEquals(1, s.weight(Mood.CLEAR));
        for (int t = 0; t < OriginSkyState.FADE_TICKS / 2; t++) s.tick(2, false, t);
        assertEquals(Mood.AURORA, s.target());
        assertEquals(0.5F, s.weight(Mood.AURORA), 1e-4F, "halfway after one second");
        for (int t = 20; t < 40; t++) s.tick(2, false, t);
        assertEquals(1, s.weight(Mood.AURORA), 1e-4F);
        assertEquals(0, s.weight(Mood.CLEAR), 1e-4F);
        for (int t = 40; t < 80; t++) s.tick(3, false, t);
        assertEquals(1, s.weight(Mood.ECLIPSE), 1e-4F, "phase 3: eclipse");
    }

    @Test
    void dawnAfterTheFallThenClear() {
        OriginSkyState s = new OriginSkyState();
        s.tick(3, true, 1000);
        assertEquals(Mood.DAWN, s.aim(0, false, 1000 + OriginSkyState.DAWN_TICKS - 1), "dawn stays after the body is gone");
        assertEquals(Mood.CLEAR, s.aim(0, false, 1000 + OriginSkyState.DAWN_TICKS));
        s.clear();
        assertEquals(1, s.weight(Mood.CLEAR));
    }
}
