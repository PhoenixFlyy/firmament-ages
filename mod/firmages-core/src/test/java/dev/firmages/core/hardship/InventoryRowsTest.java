package dev.firmages.core.hardship;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.immersion.AgeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §19.4: the open rows by Age, the locked inventory indices, the exemptions and the {@code inventoryRows} status field. */
class InventoryRowsTest {

    @Test
    void defaultsByAge() {
        // Felix 2026-10-17: Dawn the hotbar only, the Stone Age two rows, the Bronze Age all three
        assertEquals(0, InventoryRows.defaultRows(AgeId.DAWN));
        assertEquals(2, InventoryRows.defaultRows(AgeId.AGE_0));
        for (AgeId age : AgeId.all()) {
            if (age.ordinal() >= AgeId.AGE_1.ordinal()) assertEquals(InventoryRows.ALL, InventoryRows.defaultRows(age), "default in " + age);
        }
    }

    @Test
    void configValues() {
        Map<AgeId, Integer> cfg = Map.of(AgeId.DAWN, 1, AgeId.AGE_0, 7, AgeId.AGE_1, -2);
        assertEquals(1, InventoryRows.rows(AgeId.DAWN, cfg::get));
        assertEquals(3, InventoryRows.rows(AgeId.AGE_0, cfg::get), "clamped to 3");
        assertEquals(0, InventoryRows.rows(AgeId.AGE_1, cfg::get), "clamped to 0");
        assertEquals(3, InventoryRows.rows(AgeId.AGE_2, cfg::get), "a missing Age opens every row");
        assertEquals(3, InventoryRows.rowsFor(0, true), "creative, spectator and machines are exempt");
        assertEquals(0, InventoryRows.rowsFor(0, false));
    }

    @Test
    void lockedIndices() {
        // Inventory.items: hotbar 0..8, main rows top to bottom 9..17, 18..26, 27..35, armour 36..39, offhand 40
        for (int i = 0; i <= 40; i++) {
            assertEquals(i >= 9 && i <= 35, InventoryRows.locked(i, 0), "no open row, index " + i);
            assertEquals(i >= 9 && i <= 26, InventoryRows.locked(i, 1), "one open row (27..35, above the hotbar), index " + i);
            assertEquals(i >= 9 && i <= 17, InventoryRows.locked(i, 2), "two open rows (18..35), index " + i);
            assertFalse(InventoryRows.locked(i, 3), "every row open, index " + i);
        }
        assertEquals(36, InventoryRows.firstOpen(0));
        assertEquals(18, InventoryRows.firstOpen(2));
        assertEquals(9, InventoryRows.firstOpen(3));
        assertTrue(InventoryRows.locked(9, -1) && !InventoryRows.locked(9, 4), "out-of-range rows are clamped");
    }

    @Test
    void statusCarriesTheRows() {
        AgeStatus dawn = AgeStatus.of(AgeSnapshot.DAWN_ONLY, t -> Optional.empty(), AgeId.AGE_1, AgeId.AGE_1, t -> Optional.empty(),
            Optional.of(AgeId.AGE_2), Optional.of(AgeId.AGE_0));
        assertEquals(0, dawn.inventoryRows(), "Dawn by default: the hotbar only");
        AgeStatus stone = AgeStatus.of(AgeSnapshot.of(List.of(AgeId.AGE_0), 1, 0), t -> Optional.empty(), AgeId.AGE_1, AgeId.AGE_1,
            t -> Optional.empty(), Optional.of(AgeId.AGE_2), Optional.of(AgeId.AGE_0));
        assertEquals(2, stone.inventoryRows(), "Stone Age by default: two rows");
        assertEquals(1, stone.withInventoryRows(a -> a == AgeId.AGE_0 ? 1 : 3).inventoryRows(), "the config decides");
        assertEquals(2, stone.withGridLock(Optional.empty()).withReloadPending(true).withGathered(true).withUiGates(AgeId.AGE_0, AgeId.AGE_2)
            .inventoryRows(), "the other withers keep it");
        assertEquals(InventoryRows.ALL, AgeStatus.UNKNOWN.inventoryRows(), "before the first status the client locks nothing");
    }
}
