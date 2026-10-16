package dev.firmages.core.mixin.ae2;

import appeng.me.cells.BasicCellInventory;
import dev.firmages.core.config.ServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * AE2 cell types (SPEC §34.2; Felix: freer than 63, like other expert packs). AE2 19.2.18 [verified, javap] clamps a
 * cell's {@code maxItemTypes} to the inlined constant {@code MAX_ITEM_TYPES = 63} in the constructor of
 * {@code BasicCellInventory} (two {@code bipush 63}: compare and assign). Both become {@code balance.ae2CellTypes}.
 */
@Mixin(value = BasicCellInventory.class, remap = false)
public abstract class BasicCellInventoryMixin {
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 63))
    private int firmages$cellTypeCap(int original) {
        return Math.max(original, ServerConfig.ae2CellTypes());
    }
}
