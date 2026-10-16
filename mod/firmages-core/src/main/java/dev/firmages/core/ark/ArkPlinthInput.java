package dev.firmages.core.ark;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The plinth's item input for pipes, hoppers and AE2 (SPEC §31.8): one always-empty slot that takes exactly what the
 * island still asks for (a new flora id, the rest of a count, the rest of a crown entry) and refuses everything else,
 * so a pipe never loses items into it. Nothing can be extracted. Before the finale it takes nothing.
 */
public final class ArkPlinthInput implements IItemHandler {
    private final ServerLevel level;
    private final BlockPos pos;

    private ArkPlinthInput(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    /** The handler of the plinth at {@code pos}, or null on the client. */
    @Nullable
    public static IItemHandler at(Level level, BlockPos pos) {
        return level instanceof ServerLevel sl ? new ArkPlinthInput(sl, pos) : null;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return stack;
        int taken = ArkService.offer(level, pos, stack, simulate, null);
        if (taken <= 0) return stack;
        return stack.copyWithCount(stack.getCount() - taken);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return ArkService.offer(level, pos, stack, true, null) > 0;
    }
}
