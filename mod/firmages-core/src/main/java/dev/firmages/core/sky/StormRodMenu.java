package dev.firmages.core.sky;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The Storm Rod's plain container screen (SPEC §26.5): a 4 by 4 grid of sand on the left, a 4 by 4 grid of storm glass
 * on the right (take only), the player inventory below.
 */
public final class StormRodMenu extends AbstractContainerMenu {
    public static final int GRID_Y = 18;
    public static final int INPUT_X = 8;
    public static final int OUTPUT_X = 96;
    public static final int INV_Y = 104;
    public static final int SLOTS = StormRodBlockEntity.INPUTS + StormRodBlockEntity.OUTPUTS;
    private final ContainerLevelAccess access;

    /** Client side: an empty copy the server fills through slot sync. */
    public StormRodMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new ItemStackHandler(SLOTS), ContainerLevelAccess.NULL);
    }

    public StormRodMenu(int id, Inventory inv, IItemHandler items, ContainerLevelAccess access) {
        super(HarvestRegistry.STORM_ROD_MENU.get(), id);
        this.access = access;
        for (int i = 0; i < StormRodBlockEntity.INPUTS; i++) {
            addSlot(new SlotItemHandler(items, i, INPUT_X + (i % 4) * 18, GRID_Y + (i / 4) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return StormRodBlockEntity.isInput(stack);
                }
            });
        }
        for (int i = 0; i < StormRodBlockEntity.OUTPUTS; i++) {
            addSlot(new SlotItemHandler(items, StormRodBlockEntity.INPUTS + i, OUTPUT_X + (i % 4) * 18, GRID_Y + (i / 4) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!StormRodBlockEntity.isInput(stack) || !moveItemStackTo(stack, 0, StormRodBlockEntity.INPUTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, HarvestRegistry.STORM_ROD.get());
    }
}
