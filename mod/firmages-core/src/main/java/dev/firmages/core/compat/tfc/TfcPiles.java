package dev.firmages.core.compat.tfc;

import dev.firmages.core.mixin.tfc.PlacedItemBlockEntityAccessor;
import net.dries007.tfc.common.blockentities.PlacedItemBlockEntity;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * TerraFirmaCraft's placed-item piles for ground crafting (SPEC §19.3). TFC 4.2.11 [verified, javap]: block
 * {@code tfc:placed_item}, block entity {@code PlacedItemBlockEntity} with a four-slot {@code ItemStackHandler}
 * (slot = quarter of the block: +1 west half, +2 north half; one item per slot; a large item lies alone in slot 0),
 * {@code updateBlock()} removes the block when the pile is empty and syncs it otherwise. The pit kiln's block entity
 * extends it and is left out (type check). Loaded only when TFC is installed.
 */
public final class TfcPiles {
    private TfcPiles() {}

    /** The pile's inventory at {@code pos}, or null if no placed-item pile is there. */
    public static @Nullable IItemHandlerModifiable pile(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PlacedItemBlockEntity pile) || be.getType() != TFCBlockEntities.PLACED_ITEM.get()) return null;
        return pile.getInventory();
    }

    /** Selftest: {@code tfc:placed_item} is registered with its block entity, and the accessor mixin is applied. */
    public static boolean hooked() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.parse("tfc:placed_item"))
            && TFCBlockEntities.PLACED_ITEM.get() != null
            && PlacedItemBlockEntityAccessor.class.isAssignableFrom(PlacedItemBlockEntity.class);
    }

    /** After the pile's contents changed: TFC removes an empty pile, else syncs it to clients. */
    public static void update(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PlacedItemBlockEntity pile) ((PlacedItemBlockEntityAccessor) pile).firmages$updateBlock();
    }
}
