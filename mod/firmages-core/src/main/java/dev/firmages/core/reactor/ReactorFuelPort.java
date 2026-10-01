package dev.firmages.core.reactor;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * m4 (SPEC §6.1): the item port that Draconic reactor stabilizers and injectors get, so hoppers and pipes can
 * refuel a cold reactor. Slot 0 takes awakened draconium (block, ingot, nugget) while the reactor is COLD, up to
 * DE's cap; slot 1 gives the chaos as fragments, largest first, while it is COLD. Nothing happens in any other
 * reactor state, and with {@code reactor.itemHandler = false}.
 */
public final class ReactorFuelPort implements IItemHandler {
    private final Supplier<@Nullable ReactorView> reactor;

    public ReactorFuelPort(Supplier<@Nullable ReactorView> reactor) {
        this.reactor = reactor;
    }

    @Nullable
    private ReactorView cold() {
        if (ServerConfig.loaded() && !ServerConfig.REACTOR_ITEM_HANDLER.get()) return null;
        ReactorView r = reactor.get();
        return r != null && r.phase() == ReactorView.Phase.COLD ? r : null;
    }

    @Override
    public int getSlots() {
        return 2;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot != 1) return ItemStack.EMPTY;
        ReactorView r = cold();
        if (r == null) return ItemStack.EMPTY;
        int[] split = ReactorFuel.chaosSplit(r.chaos());
        for (int i = 0; i < 3; i++) {
            if (split[i] > 0) return ReactorItems.chaos(i, Math.min(64, split[i]));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot != 0 || stack.isEmpty()) return stack;
        int value = ReactorItems.fuelValue(stack);
        if (value <= 0) return stack;
        ReactorView r = cold();
        if (r == null) return stack;
        int n = ReactorFuel.fits(value, stack.getCount(), r.fuel(), r.chaos());
        if (n <= 0) return stack;
        if (!simulate) r.addFuel(n * value);
        return stack.copyWithCount(stack.getCount() - n);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != 1 || amount <= 0) return ItemStack.EMPTY;
        ReactorView r = cold();
        if (r == null) return ItemStack.EMPTY;
        int[] split = ReactorFuel.chaosSplit(r.chaos());
        for (int i = 0; i < 3; i++) {
            if (split[i] <= 0) continue;
            int n = Math.min(Math.min(amount, 64), split[i]);
            ItemStack out = ReactorItems.chaos(i, n);
            if (out.isEmpty()) return ItemStack.EMPTY;
            if (!simulate) r.removeChaos(n * ReactorFuel.UNITS[i]);
            return out;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0 && ReactorItems.fuelValue(stack) > 0;
    }
}
