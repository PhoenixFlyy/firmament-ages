package dev.firmages.core.mixin.tfc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.firmages.core.compat.tfc.TfcTweaks;
import net.dries007.tfc.client.IngameOverlays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client half of the nutrition floor (SPEC §18.6): TFC's health bar shows health and max health times the health
 * modifier ({@code IngameOverlays.renderHealthBar}, TFC 4.2.11 [verified, javap]), so it shows the same floored value
 * the damage handler uses.
 */
@Mixin(value = IngameOverlays.class, remap = false)
public abstract class TfcHealthBarMixin {
    @ModifyExpressionValue(method = "renderHealthBar", at = @At(value = "INVOKE",
        target = "Lnet/dries007/tfc/common/player/IPlayerInfo;getHealthModifier()F"))
    private static float firmages$nutritionFloor(float modifier) {
        return TfcTweaks.healthModifier(modifier);
    }
}
