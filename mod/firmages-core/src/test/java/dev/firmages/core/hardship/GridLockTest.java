package dev.firmages.core.hardship;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.immersion.AgeStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC §19.3: {@code [hardship] gridLockUntilAge}, the lock predicate with its exemptions, the ground-craft hint's
 * rate limit, the orders a pile is tried in, and the {@code gridLock} flag of the Age status.
 */
class GridLockTest {

    @Test
    void defaultIsDawnOnly() {
        // Felix 2026-10-15, HW2A: the grid opens with the Stone Age, the same boundary as the hand-breaking rule
        assertEquals(AgeId.AGE_0, GridLock.DEFAULT_UNTIL);
        assertEquals(ToolRule.DEFAULT_UNTIL, GridLock.DEFAULT_UNTIL);
        assertTrue(GridLock.isValid(GridLock.DEFAULT_UNTIL.id()));
        Optional<AgeId> until = GridLock.until(GridLock.DEFAULT_UNTIL.id());
        for (AgeId age : AgeId.all()) assertEquals(age == AgeId.DAWN, GridLock.active(age, until), "default in " + age);
    }

    @Test
    void configValues() {
        assertEquals(Optional.of(AgeId.AGE_1), GridLock.until("age_1"));
        assertEquals(Optional.of(AgeId.AGE_1), GridLock.until("progressivestages:age_1"));
        assertEquals(Optional.empty(), GridLock.until("never"));
        assertEquals(Optional.empty(), GridLock.until(" Never "));
        assertEquals(Optional.of(AgeId.AGE_0), GridLock.until("stone"), "unknown -> default");
        assertEquals(Optional.of(AgeId.AGE_0), GridLock.until(null));
        assertTrue(GridLock.isValid("never"));
        assertTrue(GridLock.isValid("dawn"));
        assertFalse(GridLock.isValid("stone"));
        assertFalse(GridLock.isValid(1));
        for (AgeId age : AgeId.all()) {
            assertFalse(GridLock.active(age, Optional.empty()), "never: open in " + age);
            assertFalse(GridLock.active(age, Optional.of(AgeId.DAWN)), "dawn: open in " + age);
            assertEquals(age.ordinal() < AgeId.AGE_1.ordinal(), GridLock.active(age, Optional.of(AgeId.AGE_1)), "age_1 in " + age);
        }
    }

    @Test
    void predicateAndHint() {
        assertTrue(GridLock.locks(true, false), "locked for a survival player");
        assertFalse(GridLock.locks(true, true), "creative, spectator or machine");
        assertFalse(GridLock.locks(false, false), "open after its Age");
        assertTrue(GridLock.hintDue(null, 0));
        assertFalse(GridLock.hintDue(5_000L, 64_999L));
        assertTrue(GridLock.hintDue(5_000L, 65_000L));
    }

    @Test
    void pileOrders() {
        // Direction.get2DDataValue: 0 south, 1 west, 2 north, 3 east; TFC pile slots 0 SE, 1 SW, 2 NE, 3 NW
        assertArrayEquals(new int[] {3, 2, 1, 0}, GridLock.viewOrder(2), "north: far row NW NE, near row SW SE");
        assertArrayEquals(new int[] {0, 1, 2, 3}, GridLock.viewOrder(0), "south: far row SE SW, near row NE NW");
        assertArrayEquals(new int[] {2, 0, 3, 1}, GridLock.viewOrder(3), "east: far column NE SE, near column NW SW");
        assertArrayEquals(new int[] {1, 3, 0, 2}, GridLock.viewOrder(1), "west: far column SW NW, near column SE NE");
        for (int facing = -1; facing <= 4; facing++) {
            List<int[]> orders = GridLock.orders(facing);
            assertEquals(24, orders.size(), "all arrangements of four places, facing " + facing);
            assertArrayEquals(GridLock.viewOrder(facing), orders.get(0), "the player's view first, facing " + facing);
            Set<String> distinct = new HashSet<>();
            for (int[] o : orders) {
                int[] sorted = o.clone();
                Arrays.sort(sorted);
                assertArrayEquals(new int[] {0, 1, 2, 3}, sorted, "a permutation: " + Arrays.toString(o));
                distinct.add(Arrays.toString(o));
            }
            assertEquals(24, distinct.size(), "no duplicates, facing " + facing);
        }
    }

    @Test
    void statusCarriesTheLock() {
        Optional<AgeId> stone = Optional.of(AgeId.AGE_0);
        AgeStatus dawn = AgeStatus.of(AgeSnapshot.DAWN_ONLY, t -> Optional.empty(), AgeId.AGE_1, AgeId.AGE_1, t -> Optional.empty(),
            Optional.of(AgeId.AGE_2), stone);
        assertFalse(dawn.gridLock(), "of() alone decides nothing about the grid");
        assertTrue(dawn.withGridLock(stone).gridLock(), "Dawn: locked");
        assertTrue(dawn.withGridLock(stone).withReloadPending(true).withGathered(false).gridLock(), "the other withers keep it");
        assertFalse(dawn.withGridLock(Optional.empty()).gridLock(), "never: open");
        AgeStatus stoneAge = AgeStatus.of(AgeSnapshot.of(List.of(AgeId.AGE_0), 1, 0), t -> Optional.empty(), AgeId.AGE_1, AgeId.AGE_1,
            t -> Optional.empty(), Optional.of(AgeId.AGE_2), stone).withGridLock(stone);
        assertFalse(stoneAge.gridLock(), "Stone Age: open");
        assertFalse(AgeStatus.UNKNOWN.gridLock(), "before the first status the client shows the grid");
    }
}
