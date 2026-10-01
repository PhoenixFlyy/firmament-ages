package dev.firmages.core.mixin.ps;

import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.compat.progressivestages.LockSyncDedupe;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Skips the lock sync inside {@code sendStageSync} when {@code syncPlayer} just sent it; see {@link LockSyncDedupe}. */
@Mixin(value = NetworkHandler.class, remap = false)
public abstract class SendStageSyncMixin {
    @WrapOperation(method = "sendStageSync", require = 0, at = @At(value = "INVOKE",
        target = "Lcom/enviouse/progressivestages/common/network/NetworkHandler;sendLockSync(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void firmages$skipDuplicate(ServerPlayer player, Operation<Void> original) {
        if (!LockSyncDedupe.skip(player)) original.call(player);
    }
}
