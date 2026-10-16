package dev.firmages.core.mixin.tfc;

import net.dries007.tfc.common.blockentities.PlacedItemBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** TFC 4.2.11 {@code PlacedItemBlockEntity.updateBlock()} (protected) [verified, javap]: ground crafting (SPEC §19.3). */
@Mixin(value = PlacedItemBlockEntity.class, remap = false)
public interface PlacedItemBlockEntityAccessor {
    @Invoker("updateBlock")
    void firmages$updateBlock();
}
