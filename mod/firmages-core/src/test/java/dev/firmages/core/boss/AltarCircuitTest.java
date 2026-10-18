package dev.firmages.core.boss;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round G3 (SPEC §39.1): the altar state machine. */
class AltarCircuitTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

    @Test
    void aChannelLightsAnAltarAfterThreeSeconds() {
        AltarCircuit c = new AltarCircuit();
        assertTrue(c.start(0, A, 100));
        assertTrue(c.tick(100 + AltarCircuit.CHANNEL_TICKS - 1).isEmpty());
        assertEquals(List.of(0), c.tick(100 + AltarCircuit.CHANNEL_TICKS));
        assertTrue(c.active(0));
        assertFalse(c.start(0, B, 200), "a lit altar cannot be channelled");
    }

    @Test
    void oneChannelPerAltarAndPerPlayer() {
        AltarCircuit c = new AltarCircuit();
        assertTrue(c.start(1, A, 0));
        assertFalse(c.start(1, B, 5), "another player channels it");
        assertTrue(c.start(1, A, 10), "the same player keeps it");
        assertEquals(1.0F / 6, c.progress(1, 10), 1e-6F, "the channel keeps its start");
        assertTrue(c.start(2, A, 20));
        assertNull(c.channeler(1), "starting a second altar ends the first channel");
        c.cancel(2);
        assertFalse(c.anyChannel());
    }

    @Test
    void anyOrderLightsAllFour() {
        AltarCircuit c = new AltarCircuit();
        int[] order = {3, 0, 2, 1};
        long t = 0;
        for (int i : order) {
            c.start(i, A, t);
            t += AltarCircuit.CHANNEL_TICKS;
            c.tick(t);
        }
        assertTrue(c.allActive());
        c.resetAll();
        assertEquals(0, c.activeCount());
        c.completeAll();
        assertTrue(c.allActive());
    }

    @Test
    void theStrikeTurnsOneLitAltarDark() {
        AltarCircuit c = new AltarCircuit();
        assertEquals(-1, c.strike(n -> 0), "nothing to strike");
        c.set(1, true);
        c.set(3, true);
        assertEquals(3, c.strike(n -> 1), "the second lit altar");
        assertFalse(c.active(3));
        assertEquals(1, c.activeCount());
    }
}
