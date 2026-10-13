package dev.firmages.core.mixin.ps;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import dev.firmages.core.compat.progressivestages.StageSyncCoalescer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Defers {@code NetworkHandler.sendStageSync} (lock sync, stage payload, reveal policy, entity visibility) to the
 * end of the tick, once per player; see {@link StageSyncCoalescer}. Fails soft: without it every call is sent.
 */
@Mixin(value = NetworkHandler.class, remap = false)
public abstract class StageSyncCoalesceMixin {
    @Inject(method = "sendStageSync", at = @At("HEAD"), cancellable = true, require = 0)
    private static void firmages$coalesce(ServerPlayer player, Set<StageId> stages, CallbackInfo ci) {
        if (StageSyncCoalescer.defer(player, stages)) ci.cancel();
    }
}
