package dev.firmages.core.mixin.ps;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.server.ServerEventHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.compat.progressivestages.LockSyncDedupe;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

/**
 * Marks the first lock sync of {@code ServerEventHandler.onPlayerJoin} (login, dimension change, respawn) and ends
 * the dedupe scope after its {@code sendStageSync}; see {@link LockSyncDedupe}. All injections fail soft.
 */
@Mixin(value = ServerEventHandler.class, remap = false)
public abstract class PlayerJoinMixin {
    @WrapOperation(method = "onPlayerJoin", require = 0, at = @At(value = "INVOKE", ordinal = 0,
        target = "Lcom/enviouse/progressivestages/common/network/NetworkHandler;sendLockSync(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void firmages$firstLockSync(ServerPlayer player, Operation<Void> original) {
        original.call(player);
        LockSyncDedupe.begin(player);
        LockSyncDedupe.markSent(player, LockSyncDedupe.Site.JOIN);
    }

    @WrapOperation(method = "onPlayerJoin", require = 0, at = @At(value = "INVOKE", ordinal = 0,
        target = "Lcom/enviouse/progressivestages/common/network/NetworkHandler;sendStageSync(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/Set;)V"))
    private static void firmages$stageSync(ServerPlayer player, Set<StageId> stages, Operation<Void> original) {
        try {
            original.call(player, stages);
        } finally {
            LockSyncDedupe.end();
        }
    }
}
