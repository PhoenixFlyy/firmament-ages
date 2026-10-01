package dev.firmages.core.mixin.ps;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.compat.progressivestages.LockSyncDedupe;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the first lock sync of {@code syncPlayer}; see {@link LockSyncDedupe}. All injections fail soft. */
@Mixin(value = ProgressiveStagesAPI.class, remap = false)
public abstract class SyncPlayerMixin {
    @Inject(method = "syncPlayer", at = @At("HEAD"), require = 0)
    private static void firmages$begin(ServerPlayer player, CallbackInfo ci) {
        LockSyncDedupe.begin(player);
    }

    @WrapOperation(method = "syncPlayer", require = 0, at = @At(value = "INVOKE", ordinal = 0,
        target = "Lcom/enviouse/progressivestages/common/network/NetworkHandler;sendLockSync(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void firmages$firstLockSync(ServerPlayer player, Operation<Void> original) {
        original.call(player);
        LockSyncDedupe.markSent(player);
    }

    @Inject(method = "syncPlayer", at = @At("RETURN"), require = 0)
    private static void firmages$end(ServerPlayer player, CallbackInfo ci) {
        LockSyncDedupe.end();
    }
}
