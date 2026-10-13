package dev.firmages.core.compat.tfc;

import dev.firmages.core.immersion.ImmersionService;
import dev.firmages.core.immersion.InventoryTab;
import net.dries007.tfc.client.screen.button.PlayerInventoryTabButton;
import net.minecraft.server.level.ServerPlayer;

/**
 * TerraFirmaCraft's inventory tabs by Age (SPEC §18.6), both sides. TFC 4.2.11 [verified, javap]: the tabs are
 * {@code PlayerInventoryTabButton.Tab} INVENTORY, CALENDAR, NUTRITION, CLIMATE, BOOK (the Field Guide, added only with
 * Patchouli); a click sends {@code SwitchInventoryTabPacket}, whose server {@code handle} is the only place that opens
 * the calendar, nutrition and climate menus and the Field Guide. Loaded only when TFC is installed.
 */
public final class TfcTabs {
    private TfcTabs() {}

    /** The Age-gated tab for TFC's tab, {@code null} for the Inventory tab (always open). */
    public static InventoryTab of(PlayerInventoryTabButton.Tab tab) {
        return switch (tab) {
            case INVENTORY -> null;
            case CALENDAR -> InventoryTab.CALENDAR;
            case NUTRITION -> InventoryTab.NUTRITION;
            case CLIMATE -> InventoryTab.CLIMATE;
            case BOOK -> InventoryTab.FIELD_GUIDE;
        };
    }

    /** Server: may {@code player} switch to {@code tab} now? Asked by {@code mixin/tfc/SwitchInventoryTabMixin}. */
    public static boolean allowed(ServerPlayer player, PlayerInventoryTabButton.Tab tab) {
        InventoryTab gated = of(tab);
        return gated == null || ImmersionService.status(player.server).tabs().contains(gated);
    }
}
