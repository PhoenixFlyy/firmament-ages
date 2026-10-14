package dev.firmages.core.immersion;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * {@code ui.hiddenMenuWidgets} (SPEC §18.2): widgets other mods add to the title and pause screens, hidden without
 * code. An entry names a widget by its fully qualified class name ({@code getClass().getName()}, inner classes with
 * {@code $}) or by the translation key of its label; blank entries and surrounding spaces are ignored.
 */
public final class MenuWidgetFilter {
    /**
     * Supplementaries 3.9.9 adds a Quark-style gear button ({@code client.screens.ConfigButton}, label the literal
     * "s") next to the Realms button of the title screen and next to a pause-screen button; it opens Moonlight's
     * list of every installed mod's config (verified, javap). FancyMenu moves the Realms button, the gear stays.
     */
    public static final List<String> DEFAULT_HIDDEN = List.of("net.mehvahdjukaar.supplementaries.client.screens.ConfigButton");

    private MenuWidgetFilter() {}

    /** True when one of {@code entries} names the widget's class or its label's translation key. */
    public static boolean hidden(Collection<? extends String> entries, String className, @Nullable String labelKey) {
        for (String raw : entries) {
            if (raw == null) continue;
            String entry = raw.strip();
            if (entry.isEmpty()) continue;
            if (entry.equals(className) || entry.equals(labelKey)) return true;
        }
        return false;
    }
}
