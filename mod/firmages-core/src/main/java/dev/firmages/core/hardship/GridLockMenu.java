package dev.firmages.core.hardship;

import net.minecraft.world.entity.player.Player;

/**
 * A menu with a 2x2 crafting grid that follows the Dawn grid lock (SPEC §19.3): the player inventory
 * ({@code mixin/InventoryMenuMixin}) and the Curios screen ({@code mixin/curios/CuriosContainerMixin}). The recipe
 * book's placement ({@code mixin/RecipeBookMenuMixin}) asks it whether the grid is locked for its player.
 */
public interface GridLockMenu {
    /** The player whose grid this is. */
    Player firmages$gridOwner();

    /** True while the grid is locked for its player. */
    default boolean firmages$gridLocked() {
        return GridLockService.locked(firmages$gridOwner());
    }
}
