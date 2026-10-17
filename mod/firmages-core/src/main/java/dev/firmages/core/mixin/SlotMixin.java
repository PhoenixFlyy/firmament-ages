package dev.firmages.core.mixin;

import dev.firmages.core.hardship.InventoryRowsService;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A menu slot that shows a locked inventory row (SPEC §19.4) is inactive (the screen neither draws nor hovers it, so
 * no tooltip) and takes nothing. Base {@code Slot} only: slot classes of other mods that override these without
 * calling {@code super} are covered by {@code AbstractContainerMenuMixin} (clicks, shift-clicks) and painted over by
 * {@code InventoryRowsClient}.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void firmages$lockedRowInactive(CallbackInfoReturnable<Boolean> cir) {
        if (InventoryRowsService.lockedSlot((Slot) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void firmages$lockedRowTakesNothing(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (InventoryRowsService.lockedSlot((Slot) (Object) this)) cir.setReturnValue(false);
    }
}
