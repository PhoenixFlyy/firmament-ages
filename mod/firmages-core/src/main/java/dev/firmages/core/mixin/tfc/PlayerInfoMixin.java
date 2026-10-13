package dev.firmages.core.mixin.tfc;

import dev.firmages.core.compat.tfc.TfcTweaks;
import net.dries007.tfc.common.player.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No thirst (SPEC §18.6, Felix 2026-10-13). TFC 4.2.11 [verified, javap]: {@code PlayerInfo.thirst} starts at
 * {@code MAX_THIRST}; {@code tick} lowers it through {@code addThirst} -> {@code setThirst} (exhaustion times
 * {@code thirstModifier}, plus heat), drinking raises it the same way, and below 20 or 10 the tick adds slowness,
 * mining fatigue and dehydration damage. With {@code tfc.disableThirst} every write is pinned to the maximum and the
 * tick first refills a value loaded from an older save, so none of the thirst effects can trigger. Hunger, food
 * decay and the nutrition bonus are untouched.
 */
@Mixin(value = PlayerInfo.class, remap = false)
public abstract class PlayerInfoMixin {
    @Shadow
    private float thirst;

    @Shadow
    public abstract void setThirst(float value);

    @ModifyVariable(method = "setThirst", at = @At("HEAD"), argsOnly = true)
    private float firmages$pinThirst(float value) {
        return TfcTweaks.thirstDisabled() ? TfcTweaks.maxThirst() : value;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void firmages$refillThirst(Player player, CallbackInfo ci) {
        if (thirst < TfcTweaks.maxThirst() && TfcTweaks.thirstDisabled()) setThirst(TfcTweaks.maxThirst());
    }
}
