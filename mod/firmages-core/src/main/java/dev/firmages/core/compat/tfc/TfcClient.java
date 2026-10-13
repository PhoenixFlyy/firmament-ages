package dev.firmages.core.compat.tfc;

import dev.firmages.core.mixin.tfc.InventoryTabAccessor;
import net.dries007.tfc.client.screen.button.PlayerInventoryTabButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * Hides TFC's nutrition tab (SPEC §18.6). TFC 4.2.11 [verified, javap]: {@code ClientForgeEventHandler.onInitGuiPost}
 * adds four {@code PlayerInventoryTabButton}s (Inventory, Calendar, Nutrition, Climate) to the inventory, and the
 * calendar, nutrition and climate screens add the same four in their own {@code init}. The nutrition button is hidden
 * and the climate button moves up into its slot, so the column has no gap. Client only; call only when TFC is loaded.
 */
public final class TfcClient {
    private TfcClient() {}

    public static boolean available() {
        return ModList.get().isLoaded("tfc");
    }

    /** Run after TFC's own {@code ScreenEvent.Init.Post}. */
    public static void hideNutritionTab(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        AbstractWidget nutrition = null;
        AbstractWidget climate = null;
        for (GuiEventListener l : List.copyOf(screen.children())) {
            if (!(l instanceof PlayerInventoryTabButton b)) continue;
            PlayerInventoryTabButton.Tab tab = ((InventoryTabAccessor) b).firmages$tab();
            if (tab == PlayerInventoryTabButton.Tab.NUTRITION) nutrition = b;
            else if (tab == PlayerInventoryTabButton.Tab.CLIMATE) climate = b;
        }
        if (nutrition == null) return;
        nutrition.visible = false;
        nutrition.active = false;
        if (climate != null && climate.visible) {
            int dy = nutrition.getY() - climate.getY();
            climate.setY(climate.getY() + dy);
            InventoryTabAccessor acc = (InventoryTabAccessor) climate;
            acc.firmages$setIconY(acc.firmages$iconY() + dy);
        }
    }
}
