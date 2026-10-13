package dev.firmages.core.mixin.ars;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Join silence (SPEC §18.1). Ars Nouveau 5.13.3 [verified, javap]: {@code Rewards.init()} fetches a remote JSON that
 * can switch on a one-time Starbuncle Plush gift with a Makeship chat ad ({@code SEND_ONE_TIME_MESSAGE},
 * {@code STARBUNCLE_PLUSH_MESSAGE}) and the contributor list. Skipping it leaves both off; only the cosmetic
 * contributor Starbuncles are lost. {@code require = 0}: a renamed method only loses this hook, the chat blocklist
 * still drops the ad. Targets by name, applied only when Ars Nouveau is loaded.
 */
@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.setup.reward.Rewards", remap = false)
public abstract class RewardsMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true, require = 0)
    private static void firmages$noRemoteRewards(CallbackInfo ci) {
        ci.cancel();
    }
}
