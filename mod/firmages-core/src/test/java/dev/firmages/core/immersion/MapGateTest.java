package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §18.2: JourneyMap by Age (Felix M1A, 2026-10-17). */
class MapGateTest {
    @Test
    void defaultOpensWithTheIronAge() {
        assertEquals(AgeId.AGE_2, MapGate.DEFAULT_FROM);
        for (AgeId age : AgeId.all()) {
            boolean iron = age.ordinal() >= AgeId.AGE_2.ordinal();
            assertEquals(iron, MapGate.open(age, MapGate.DEFAULT_FROM, false), "survival in " + age + ", operator or not");
            assertTrue(MapGate.open(age, MapGate.DEFAULT_FROM, true), "creative in " + age);
        }
        assertFalse(MapGate.open(AgeId.AGE_3, AgeId.AGE_4, false), "a later configured Age");
        assertTrue(MapGate.open(AgeId.DAWN, AgeId.DAWN, false), "dawn: always there");
    }

    @Test
    void screensByClassName() {
        for (String s : MapGate.MAP_SCREENS) {
            assertTrue(MapGate.screenBlocked(s, false), s + " while closed");
            assertFalse(MapGate.screenBlocked(s, true), s + " while open");
        }
        assertTrue(MapGate.screenBlocked("journeymap.client.ui.fullscreen.Fullscreen", false));
        assertTrue(MapGate.screenBlocked("journeymap.client.ui.waypointmanager.waypoint.WaypointEditor", false));
        assertTrue(MapGate.screenBlocked(MapGate.SPLASH, true), "the splash never opens");
        assertTrue(MapGate.screenBlocked(MapGate.SPLASH, false));
        assertFalse(MapGate.screenBlocked("journeymap.client.ui.option.ClientOptionsManager", false), "the options stay");
        assertFalse(MapGate.screenBlocked("Fullscreen", false), "no simple names");
        assertFalse(MapGate.screenBlocked("net.minecraft.client.gui.screens.inventory.InventoryScreen", false));
        assertFalse(MapGate.screenBlocked(null, false));
    }

    @Test
    void keysByName() {
        assertTrue(MapGate.isMapKey("key.journeymap.map_toggle_alt"));
        assertTrue(MapGate.isMapKey("key.journeymap.create_waypoint"));
        assertTrue(MapGate.isMapKey("key.journeymap.minimap_preset"));
        assertFalse(MapGate.isMapKey("key.firmages.show_age"));
        assertFalse(MapGate.isMapKey("key.inventory"));
        assertFalse(MapGate.isMapKey(null));
    }

    @Test
    void minimapFlagOffWhileClosedAndBackOnlyIfTheGateTookIt() {
        assertEquals(Boolean.FALSE, MapGate.minimapTarget(false, true, false), "closed: switch off");
        assertNull(MapGate.minimapTarget(false, false, true), "closed and already off");
        assertEquals(Boolean.TRUE, MapGate.minimapTarget(true, false, true), "opened: back on once");
        assertNull(MapGate.minimapTarget(true, true, true), "already on again (new world state)");
        assertNull(MapGate.minimapTarget(true, false, false), "another mod switched it off: left alone");
        assertNull(MapGate.minimapTarget(true, true, false), "open and on");
    }
}
