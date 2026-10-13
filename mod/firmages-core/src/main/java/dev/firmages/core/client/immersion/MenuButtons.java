package dev.firmages.core.client.immersion;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * No mod list and no advancements entry (SPEC §18.2). NeoForge 21.1.252 [verified, sources]: the title screen adds a
 * {@code ModsButton} and the pause screen a full-width button, both labelled {@code fml.menu.mods}; the pause screen's
 * advancements button is {@code gui.advancements} (half width, next to Statistics). Both are hidden. On the pause
 * screen the gaps close: Statistics takes the advancements button's place at full width, and the rows below the mods
 * button move up one row. The title screen is only hidden, its layout belongs to FancyMenu.
 * <p>
 * The title screen's Language and Accessibility icon buttons are hidden here too: vanilla 1.21.1 labels them
 * {@code options.language} / {@code options.accessibility} ({@code CommonButtons}, icon-only), while FancyMenu 3.9.14's
 * {@code TitleScreenWidgetIdentificationContext} still maps the pre-1.20.5 keys {@code narrator.button.*}, so its
 * {@code mc_titlescreen_language_button} / {@code _accessibility_button} ids never resolve [verified, javap].
 */
final class MenuButtons {
    static final String MODS = "fml.menu.mods";
    static final String ADVANCEMENTS = "gui.advancements";
    static final String STATS = "gui.stats";
    static final String LANGUAGE = "options.language";
    static final String ACCESSIBILITY = "options.accessibility";
    /** Row pitch of the pause menu grid (20 px button, 4 px padding). */
    private static final int PAUSE_ROW = 24;

    private MenuButtons() {}

    static void apply(ScreenEvent.Init.Post event) {
        boolean pause = event.getScreen() instanceof PauseScreen;
        if (!pause && !(event.getScreen() instanceof TitleScreen)) return;
        List<GuiEventListener> widgets = List.copyOf(event.getListenersList());
        AbstractWidget mods = find(widgets, MODS);
        AbstractWidget advancements = find(widgets, ADVANCEMENTS);
        hide(mods);
        hide(advancements);
        if (!pause) {
            hide(find(widgets, LANGUAGE));
            hide(find(widgets, ACCESSIBILITY));
            return;
        }
        if (advancements != null) {
            AbstractWidget stats = find(widgets, STATS);
            if (stats != null && stats.getY() == advancements.getY()) {
                int right = stats.getX() + stats.getWidth();
                stats.setX(advancements.getX());
                stats.setWidth(right - advancements.getX());
            }
        }
        if (mods != null) {
            for (GuiEventListener l : widgets) {
                if (l instanceof AbstractWidget w && w != mods && w.getY() > mods.getY()) w.setY(w.getY() - PAUSE_ROW);
            }
        }
    }

    private static AbstractWidget find(List<GuiEventListener> widgets, String key) {
        for (GuiEventListener l : widgets) {
            if (l instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents tc && tc.getKey().equals(key)) return w;
        }
        return null;
    }

    private static void hide(AbstractWidget w) {
        if (w == null) return;
        w.visible = false;
        w.active = false;
    }
}
