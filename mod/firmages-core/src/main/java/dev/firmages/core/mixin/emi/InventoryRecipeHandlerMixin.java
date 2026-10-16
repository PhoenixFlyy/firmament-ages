package dev.firmages.core.mixin.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.handler.InventoryRecipeHandler;
import dev.firmages.core.client.immersion.GridLockClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EMI's "+" (fill the grid) and shift-craft in the player inventory are off while the Dawn grid lock holds (SPEC
 * §19.3): EMI 1.1.24 {@code InventoryRecipeHandler.supportsRecipe} [verified, javap] answers false, so the button
 * greys out as in any screen without a handler. The server refuses an EMI fill anyway (its fill packet checks
 * {@code Slot.mayPlace}). Only operators see EMI in Dawn (§18.3). Client only; fails soft.
 */
@Mixin(value = InventoryRecipeHandler.class, remap = false)
public abstract class InventoryRecipeHandlerMixin {
    @Inject(method = "supportsRecipe", at = @At("HEAD"), cancellable = true, require = 0)
    private void firmages$gridLocked(EmiRecipe recipe, CallbackInfoReturnable<Boolean> cir) {
        if (GridLockClient.locked()) cir.setReturnValue(false);
    }
}
