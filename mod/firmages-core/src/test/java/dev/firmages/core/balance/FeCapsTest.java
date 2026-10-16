package dev.firmages.core.balance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code balance.feCap} entries (SPEC §37). */
class FeCapsTest {
    @Test
    void readsBlockIdsAndCaps() {
        FeCaps c = FeCaps.parse(List.of("createappliedkinetics:energy_provider=0", " mekanismgenerators:wind_generator = 120 "));
        assertTrue(c.errors().isEmpty(), c.errors().toString());
        assertEquals(0, c.caps().get("createappliedkinetics:energy_provider"));
        assertEquals(120, c.caps().get("mekanismgenerators:wind_generator"));
    }

    @Test
    void badEntriesAreReportedAndIgnored() {
        FeCaps c = FeCaps.parse(List.of("no_namespace=5", "a:b=-1", "a:b=99999999999", "a:c=7", "a:c=9"));
        assertEquals(4, c.errors().size(), c.errors().toString());
        assertEquals(1, c.caps().size());
        assertEquals(9, c.caps().get("a:c"), "the last entry of a block counts");
    }

    @Test
    void theBudgetIsPerTick() {
        assertEquals(100, FeCaps.room(100, 5, 4, 100), "a new tick starts a fresh budget");
        assertEquals(40, FeCaps.room(100, 5, 5, 60));
        assertEquals(0, FeCaps.room(100, 5, 5, 160));
    }
}
