package dev.firmages.core.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §41.4: the Arcane Catalyst's extra ticks and their FE. */
class CatalystRulesTest {
    @Test
    void halfATickPerTickIsOneExtraTickEverySecondTick() {
        double acc = 0;
        long total = 0;
        for (int t = 0; t < 100; t++) {
            long[] r = CatalystRules.extraTicks(acc, 0.5);
            total += r[0];
            acc = r[1] / 1_000_000.0;
            assertTrue(r[0] <= 1, "never more than one extra tick at 0.5");
        }
        assertEquals(50, total);
    }

    @Test
    void surchargeIsWhatTheMachineDrewTimesTheFactorOrTheFlatRate() {
        assertEquals(200, CatalystRules.surcharge(200, 1.0, 512), "an extra operation costs twice");
        assertEquals(100, CatalystRules.surcharge(200, 0.5, 512));
        assertEquals(0, CatalystRules.surcharge(0, 1.0, 512), "an idle machine costs nothing");
        assertEquals(512, CatalystRules.surcharge(-1, 1.0, 512), "unreadable: the flat rate");
    }

    @Test
    void chargedNeedsSourceAndRotation() {
        assertTrue(CatalystRules.charged(true, 64, 64));
        assertFalse(CatalystRules.charged(false, 256, 64), "no Source");
        assertFalse(CatalystRules.charged(true, 32, 64), "too slow");
    }
}
