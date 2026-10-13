package dev.firmages.core.mixin.tfc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.firmages.core.compat.tfc.TfcTweaks;
import net.dries007.tfc.ForgeEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nutrition as a bonus only (SPEC §18.6). TFC 4.2.11 [verified, javap]: {@code ForgeEventHandler.onLivingHurt}
 * divides a player's incoming damage by {@code IPlayerInfo.getHealthModifier()}, which is how nutrition changes
 * effective max health. The modifier is raised to the starting value ({@link TfcTweaks#healthModifier}).
 */
@Mixin(value = ForgeEventHandler.class, remap = false)
public abstract class TfcDamageMixin {
    @ModifyExpressionValue(method = "onLivingHurt", at = @At(value = "INVOKE",
        target = "Lnet/dries007/tfc/common/player/IPlayerInfo;getHealthModifier()F"))
    private static float firmages$nutritionFloor(float modifier) {
        return TfcTweaks.healthModifier(modifier);
    }
}
