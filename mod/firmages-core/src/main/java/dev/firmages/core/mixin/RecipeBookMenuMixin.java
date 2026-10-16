package dev.firmages.core.mixin;

import dev.firmages.core.hardship.GridLockMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The recipe book cannot fill a locked crafting grid (SPEC §19.3): {@code RecipeBookMenu.handlePlacement} moves the
 * ingredients itself ({@code ServerPlaceRecipe}), past the slots' {@code mayPlace}. Refused for a menu of
 * {@link GridLockMenu} whose grid is locked, so nothing moves back and forth.
 */
@Mixin(RecipeBookMenu.class)
public abstract class RecipeBookMenuMixin {
    @Inject(method = "handlePlacement", at = @At("HEAD"), cancellable = true)
    private void firmages$gridLocked(boolean placeAll, RecipeHolder<?> recipe, ServerPlayer player, CallbackInfo ci) {
        if (this instanceof GridLockMenu menu && menu.firmages$gridLocked()) ci.cancel();
    }
}
