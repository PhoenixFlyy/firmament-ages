package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §18.2: the Curios and Apothic Attributes buttons of the inventory by Age. */
class InventoryButtonsTest {
    private static AgeStatus status(AgeId age) {
        List<AgeId> unlocked = new ArrayList<>();
        for (AgeId a : AgeId.all()) if (a.ordinal() <= age.ordinal()) unlocked.add(a);
        return AgeStatus.of(AgeSnapshot.of(unlocked, 1, 0), t -> Optional.empty(), AgeId.AGE_1, AgeId.AGE_1, t -> Optional.empty(),
            Optional.empty(), Optional.empty());
    }

    @Test
    void defaultHidesThemInDawnOnly() {
        assertEquals(AgeId.AGE_0, InventoryButtons.DEFAULT_FROM);
        for (AgeId age : AgeId.all()) {
            AgeStatus s = status(age).withUiGates(InventoryButtons.DEFAULT_FROM, MapGate.DEFAULT_FROM);
            assertEquals(age != AgeId.DAWN, s.inventoryButtons(), "the server's button flag in " + age);
            assertEquals(age != AgeId.DAWN, InventoryButtons.shown(s.inventoryButtons(), false), "survival in " + age + ", operator or not");
            assertTrue(InventoryButtons.shown(s.inventoryButtons(), true), "creative in " + age);
        }
        assertFalse(status(AgeId.AGE_2).withUiGates(AgeId.AGE_3, AgeId.AGE_2).inventoryButtons(), "a later configured Age");
        assertTrue(status(AgeId.DAWN).withUiGates(AgeId.DAWN, AgeId.AGE_2).inventoryButtons(), "dawn: always there");
        assertFalse(AgeStatus.UNKNOWN.inventoryButtons(), "before the first status: hidden");
        AgeStatus s = status(AgeId.AGE_4);
        assertEquals(s.withGridLock(Optional.of(AgeId.AGE_0)).withReloadPending(true).withGathered(true).inventoryButtons(), s.inventoryButtons(),
            "the other with-methods keep the flags");
    }

    @Test
    void widgetsAndScreensByClassName() {
        assertTrue(InventoryButtons.removed("top.theillusivec4.curios.client.gui.CuriosButton"));
        assertTrue(InventoryButtons.removed("dev.shadowsoffire.apothic_attributes.client.AttributesGui"));
        assertTrue(InventoryButtons.removed("dev.shadowsoffire.apothic_attributes.client.AttributesGui$1"), "the toggle button");
        assertTrue(InventoryButtons.removed("dev.shadowsoffire.apothic_attributes.client.AttributesGui$HideUnchangedButton"));
        assertFalse(InventoryButtons.removed("net.minecraft.client.gui.components.ImageButton"), "the recipe-book button stays");
        assertFalse(InventoryButtons.removed("dev.shadowsoffire.apothic_attributes.client.CuriosClientCompat$1"), "only the inventory's widgets");
        assertFalse(InventoryButtons.removed("CuriosButton"), "no simple names");
        assertFalse(InventoryButtons.removed(null));
        assertTrue(InventoryButtons.screenBlocked("top.theillusivec4.curios.client.gui.CuriosScreen"));
        assertFalse(InventoryButtons.screenBlocked("net.minecraft.client.gui.screens.inventory.InventoryScreen"));
        assertFalse(InventoryButtons.screenBlocked(null));
    }
}
