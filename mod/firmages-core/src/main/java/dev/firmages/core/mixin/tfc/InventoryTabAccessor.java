package dev.firmages.core.mixin.tfc;

import net.dries007.tfc.client.screen.button.PlayerInventoryTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * TFC's inventory tab buttons (Inventory, Calendar, Nutrition, Climate) keep their tab and icon position in private
 * fields [verified, javap, TFC 4.2.11]; the nutrition tab is hidden and the climate tab moved up into its place
 * (SPEC §18.6).
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
