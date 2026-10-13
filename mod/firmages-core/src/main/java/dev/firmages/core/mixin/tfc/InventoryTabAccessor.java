package dev.firmages.core.mixin.tfc;

import net.dries007.tfc.client.screen.button.PlayerInventoryTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * TFC's inventory tab buttons (Inventory, Calendar, Nutrition, Climate, Field Guide) keep their tab and icon position
 * in private fields [verified, javap, TFC 4.2.11]; tabs whose Age is not open are removed and the rest move up
 * (SPEC §18.6, {@code compat/tfc/TfcClient}).
 */
@Mixin(value = PlayerInventoryTabButton.class, remap = false)
public interface InventoryTabAccessor {
    @Accessor("tab")
    PlayerInventoryTabButton.Tab firmages$tab();

    @Accessor("iconY")
    int firmages$iconY();

    @Accessor("iconY")
    void firmages$setIconY(int y);
}
