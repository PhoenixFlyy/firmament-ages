package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §18.2: the Curios and Apothic Attributes buttons of the inventory by Age. */
class InventoryButtonsTest {
    @Test
    void defaultHidesThemInDawnOnly() {
        assertEquals(AgeId.AGE_0, InventoryButtons.DEFAULT_FROM);
        for (AgeId age : AgeId.all()) {
            assertEquals(age != AgeId.DAWN, InventoryButtons.shown(age, InventoryButtons.DEFAULT_FROM, false, false), "survival in " + age);
            assertTrue(InventoryButtons.shown(age, InventoryButtons.DEFAULT_FROM, true, false), "operator in " + age);
            assertTrue(InventoryButtons.shown(age, InventoryButtons.DEFAULT_FROM, false, true), "creative in " + age);
        }
        assertFalse(InventoryButtons.shown(AgeId.AGE_2, AgeId.AGE_3, false, false), "a later configured Age");
        assertTrue(InventoryButtons.shown(AgeId.DAWN, AgeId.DAWN, false, false), "dawn: always there");
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
