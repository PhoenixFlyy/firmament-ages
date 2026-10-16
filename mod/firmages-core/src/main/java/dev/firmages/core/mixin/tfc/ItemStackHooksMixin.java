package dev.firmages.core.mixin.tfc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.compat.tfc.LookupGuard;
import net.dries007.tfc.common.component.ItemStackHooks;
import net.dries007.tfc.common.component.food.FoodDefinition;
import net.dries007.tfc.common.component.heat.HeatDefinition;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No recursive heat or food lookup while a stack is built (SPEC §42, {@link LookupGuard}). TFC 4.2.11 [verified, javap]:
 * {@code ItemStackHooks.onModifyItemStackComponents(ItemStack)} calls {@code HeatCapability.getDefinition(ItemStack)}
 * and {@code FoodCapability.getDefinition(ItemStack)} for a stack without the component; both calls run through the
 * guard, so a stack that a definition's ingredient builds while it is being matched does not start the lookup again.
 */
@Mixin(value = ItemStackHooks.class, remap = false)
public abstract class ItemStackHooksMixin {
    @WrapOperation(method = "onModifyItemStackComponents", at = @At(value = "INVOKE",
        target = "Lnet/dries007/tfc/common/component/heat/HeatCapability;getDefinition(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/common/component/heat/HeatDefinition;"))
    private static HeatDefinition firmages$heatOnce(ItemStack stack, Operation<HeatDefinition> original) {
        return LookupGuard.lookup(() -> original.call(stack));
    }

    @WrapOperation(method = "onModifyItemStackComponents", at = @At(value = "INVOKE",
        target = "Lnet/dries007/tfc/common/component/food/FoodCapability;getDefinition(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/common/component/food/FoodDefinition;"))
    private static FoodDefinition firmages$foodOnce(ItemStack stack, Operation<FoodDefinition> original) {
        return LookupGuard.lookup(() -> original.call(stack));
    }
}
