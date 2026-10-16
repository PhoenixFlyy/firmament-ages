package dev.firmages.core.mixin.ae2;

import appeng.items.storage.BasicStorageCell;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * AE2 cell types (SPEC §34.2): item cells are built with 63 types (AE2's 1k to 256k item cells and the MEGA item cells,
 * AE2 19.2.18 and MEGA Cells 4.11.0 [verified, javap]); those report {@code balance.ae2CellTypes}. Fluid cells (18) and
 * any other limit stay as they are.
 */
@Mixin(value = BasicStorageCell.class, remap = false)
public abstract class BasicStorageCellMixin {
    @ModifyReturnValue(method = "getTotalTypes", at = @At("RETURN"))
    private int firmages$totalTypes(int types, ItemStack stack) {
        return types == 63 ? Math.max(63, ServerConfig.ae2CellTypes()) : types;
    }
}
