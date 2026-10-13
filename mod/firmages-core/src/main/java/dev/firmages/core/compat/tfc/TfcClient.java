package dev.firmages.core.compat.tfc;

import dev.firmages.core.immersion.InventoryTab;
import dev.firmages.core.mixin.tfc.InventoryTabAccessor;
import net.dries007.tfc.client.screen.button.PlayerInventoryTabButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * TFC's inventory tabs by Age on the client (SPEC §18.6). TFC 4.2.11 [verified, javap]: {@code
 * ClientForgeEventHandler.onInitGuiPost} adds the tab buttons (Inventory, Calendar, Nutrition, Climate, and the
 * Field Guide when Patchouli is present) to the survival inventory and, with {@code enableTabsInCreative}, to the
 * creative inventory; the calendar, nutrition and climate screens add the same buttons in their own {@code init}. A tab
 * whose Age is not open is removed and the tabs below move up, so the column has no gap. Client only; call only when
 * TFC is loaded.
 */
public final class TfcClient {
    private TfcClient() {}

    public static boolean available() {
        return ModList.get().isLoaded("tfc");
    }

    /** Run after TFC's own {@code ScreenEvent.Init.Post}. @param open the tabs the server's status allows */
    public static void applyTabs(ScreenEvent.Init.Post event, Set<InventoryTab> open) {
        Screen screen = event.getScreen();
        List<PlayerInventoryTabButton> kept = new ArrayList<>();
        for (GuiEventListener l : List.copyOf(screen.children())) {
            if (!(l instanceof PlayerInventoryTabButton b)) continue;
            InventoryTab gated = TfcTabs.of(tab(b));
            if (gated == null || open.contains(gated)) kept.add(b);
            else event.removeListener(b);
        }
        // the n-th remaining tab takes the n-th slot (TFC places each tab at its Tab.yIn, 23 px apart)
        kept.sort(Comparator.comparingInt(b -> tab(b).ordinal()));
        for (int slot = 0; slot < kept.size(); slot++) {
            PlayerInventoryTabButton b = kept.get(slot);
            int dy = PlayerInventoryTabButton.Tab.VALUES[slot].yIn - tab(b).yIn;
            if (dy == 0) continue;
            b.setY(b.getY() + dy);
            InventoryTabAccessor acc = (InventoryTabAccessor) b;
            acc.firmages$setIconY(acc.firmages$iconY() + dy);
        }
    }

    private static PlayerInventoryTabButton.Tab tab(PlayerInventoryTabButton b) {
        return ((InventoryTabAccessor) b).firmages$tab();
    }
}
