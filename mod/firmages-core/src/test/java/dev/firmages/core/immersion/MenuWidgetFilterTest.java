package dev.firmages.core.immersion;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §18.2: the matcher behind {@code ui.hiddenMenuWidgets}. */
class MenuWidgetFilterTest {
    private static final String GEAR = "net.mehvahdjukaar.supplementaries.client.screens.ConfigButton";

    @Test
    void defaultHidesSupplementariesGear() {
        assertTrue(MenuWidgetFilter.hidden(MenuWidgetFilter.DEFAULT_HIDDEN, GEAR, null));
        assertFalse(MenuWidgetFilter.hidden(MenuWidgetFilter.DEFAULT_HIDDEN, "net.minecraft.client.gui.components.Button", "menu.singleplayer"),
            "vanilla buttons stay");
        assertFalse(MenuWidgetFilter.hidden(MenuWidgetFilter.DEFAULT_HIDDEN, GEAR + "$Inner", null), "exact class names only");
        assertFalse(MenuWidgetFilter.hidden(MenuWidgetFilter.DEFAULT_HIDDEN, "ConfigButton", null), "no simple-name match");
    }

    @Test
    void matchesClassOrLabelKey() {
        List<String> entries = List.of("com.example.Gear$Icon", "  menu.online  ", "");
        assertTrue(MenuWidgetFilter.hidden(entries, "com.example.Gear$Icon", "s"), "inner class by its binary name");
        assertTrue(MenuWidgetFilter.hidden(entries, "net.minecraft.client.gui.components.Button", "menu.online"), "label key, spaces stripped");
        assertFalse(MenuWidgetFilter.hidden(entries, "net.minecraft.client.gui.components.Button", null), "no key, other class");
        assertFalse(MenuWidgetFilter.hidden(entries, "", ""), "a blank entry hides nothing");
    }

    @Test
    void emptyOrNullEntriesHideNothing() {
        assertFalse(MenuWidgetFilter.hidden(List.of(), GEAR, "menu.online"));
        assertFalse(MenuWidgetFilter.hidden(Arrays.asList((String) null), GEAR, null));
    }
}
