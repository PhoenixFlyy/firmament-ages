package dev.firmages.core.mixin.ps;

import com.enviouse.progressivestages.server.enforcement.DimensionEnforcer;
import dev.firmages.core.origin.OriginAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code /firmages origin tp <players> force} (operators, SPEC §16.1): ProgressiveStages' dimension lock lets a player
 * with a forced visit stay in The Origin. Every check of the lock goes through this one method [verified, javap
 * 3.0.6]: the travel event ({@code canTravelToDimension}), the bounce after arrival ({@code handlePostTravelSafetyNet})
 * and the once-a-second check of the player's current dimension ({@code ServerEventHandler.onPlayerTick}). Fails soft.
 */
@Mixin(value = DimensionEnforcer.class, remap = false)
public abstract class DimensionLockBypassMixin {
    @Inject(method = "isDimensionLockedForPlayer(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;)Z",
        at = @At("HEAD"), cancellable = true, require = 0)
    private static void firmages$forcedVisit(ServerPlayer player, ResourceLocation dimension, String action, CallbackInfoReturnable<Boolean> cir) {
        if (OriginAccess.forcedVisit(player, dimension)) cir.setReturnValue(false);
    }
}
