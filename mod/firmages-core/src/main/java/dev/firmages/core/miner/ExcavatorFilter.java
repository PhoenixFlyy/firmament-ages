package dev.firmages.core.miner;

import dev.firmages.core.age.AgeGate;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * m3, IE excavator (SPEC §5): a rolled ore of a locked Age is replaced by a roll of the mix's spoils. The mineral
 * mixes themselves are never removed (they are rolled into the world at worldgen). Called from
 * {@code mixin.ie.MineralMixMixin} at the return of {@code MineralMix#getRandomOre(Random)}.
 */
public final class ExcavatorFilter {
    private ExcavatorFilter() {}

    /** The excavator's roll: {@code rolled}, or a spoil when the rolled item (or the block it places) is locked. */
    public static ItemStack filter(ItemStack rolled, Supplier<ItemStack> spoil) {
        if (!ServerConfig.minerIeExcavator()) return rolled;
        return OreRoll.pick(rolled, ExcavatorFilter::locked, spoil);
    }

    public static boolean locked(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (AgeGate.isLocked(stack.getItem())) return true;
        return stack.getItem() instanceof BlockItem b && AgeGate.isLocked(b.getBlock());
    }
}
