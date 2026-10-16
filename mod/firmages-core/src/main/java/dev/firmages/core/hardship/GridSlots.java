package dev.firmages.core.hardship;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The slots of a 2x2 crafting grid under the Dawn grid lock (SPEC §19.3), put in place of vanilla's by the menu
 * mixins. While the grid is locked for the owner they are inactive (the screen neither draws nor hovers them, so no
 * click reaches them), a grid slot takes nothing ({@code mayPlace}: the server refuses clicks, drags and EMI fills)
 * and the result cannot be taken. A grid slot can always be emptied, so nothing sticks in it. They are still
 * {@code Slot} and {@code ResultSlot}, so every {@code instanceof} check of other mods holds.
 */
public final class GridSlots {
    private GridSlots() {}

    /**
     * Replaces slot 0 (the grid's result) and slots 1 to 4 (the grid) of a menu built like the player inventory by
     * grid-lock slots at the same index and position. Leaves the list alone if those slots do not belong to
     * {@code grid} and {@code result} (another mod changed the layout).
     */
    public static void replaceGrid(List<Slot> slots, Player owner, CraftingContainer grid, Container result) {
        if (slots.size() < 5 || slots.get(0).container != result) return;
        for (int i = 1; i <= 4; i++) if (slots.get(i).container != grid) return;
        Slot r = slots.get(0);
        Result rs = new Result(owner, grid, result, r.getContainerSlot(), r.x, r.y);
        rs.index = 0;
        slots.set(0, rs);
        for (int i = 1; i <= 4; i++) {
            Slot g = slots.get(i);
            Input in = new Input(owner, grid, g.getContainerSlot(), g.x, g.y);
            in.index = i;
            slots.set(i, in);
        }
    }

    /** One of the four grid slots. */
    public static final class Input extends Slot {
        private final Player owner;

        public Input(Player owner, Container grid, int slot, int x, int y) {
            super(grid, slot, x, y);
            this.owner = owner;
        }

        @Override
        public boolean isActive() {
            return !GridLockService.locked(owner) && super.isActive();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !GridLockService.locked(owner) && super.mayPlace(stack);
        }
    }

    /** The result slot of the grid. */
    public static final class Result extends ResultSlot {
        private final Player owner;

        public Result(Player owner, CraftingContainer grid, Container result, int slot, int x, int y) {
            super(owner, grid, result, slot, x, y);
            this.owner = owner;
        }

        @Override
        public boolean isActive() {
            return !GridLockService.locked(owner) && super.isActive();
        }

        @Override
        public boolean mayPickup(Player player) {
            return !GridLockService.locked(owner) && super.mayPickup(player);
        }
    }
}
