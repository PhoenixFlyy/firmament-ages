package dev.firmages.core.mixin.trainutilities;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Join silence (SPEC §18.1). Create Train Utilities 3.1.0 [verified, javap]: {@code net.adeptstack.Main.init()}
 * registers an Architectury {@code PLAYER_JOIN} listener, {@code lambda$init$0(ServerPlayer)}, that sends a red
 * "ATTENTION (Create Train Utilities): ..." rename notice on every join, with no config. The lambda is skipped.
 * The lambda name is version-fragile, so {@code require = 0}; the client chat blocklist ships the same text as the
 * fallback. Applied only when the mod is loaded.
 */
@Pseudo
@Mixin(targets = "net.adeptstack.Main", remap = false)
public abstract class JoinNoticeMixin {
    @Inject(method = "lambda$init$0(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void firmages$noJoinNotice(ServerPlayer player, CallbackInfo ci) {
        ci.cancel();
    }
}
