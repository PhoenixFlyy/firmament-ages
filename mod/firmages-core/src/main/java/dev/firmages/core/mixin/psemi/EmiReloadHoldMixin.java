package dev.firmages.core.mixin.psemi;

import dev.firmages.core.client.immersion.EmiReloadGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ProgressiveStages 3.0.6 reloads EMI on every client stage change
 * ({@code ClientStageCache.triggerEmiReload} → {@code ProgressiveStagesEMIPlugin.triggerEmiReload()}, public static
 * [verified, javap]). On an Age change that reload runs with the old recipes, seconds before the Age swap sends the new
 * ones, and a second reload follows. While the server reports a pending swap, {@link EmiReloadGate} holds it back and
 * reloads EMI once after the swap. Client only; targets by name (no EMI on the compile path); fails soft.
 */
@Mixin(targets = "com.enviouse.progressivestages.client.emi.ProgressiveStagesEMIPlugin", remap = false)
public abstract class EmiReloadHoldMixin {
    @Inject(method = "triggerEmiReload()V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void firmages$holdBack(CallbackInfo ci) {
        if (EmiReloadGate.holdBack()) ci.cancel();
    }
}
