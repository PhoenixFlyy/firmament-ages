package dev.firmages.core.mixin.ps;

import dev.firmages.core.compat.emi.EmiReloadDefer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips ProgressiveStages' EMI reload at a stage change while an Age reload is pending (SPEC §21.5,
 * {@link EmiReloadDefer}). ProgressiveStages 3.0.6 [verified, javap]: {@code ProgressiveStagesEMIPlugin.triggerEmiReload()}
 * is public static and schedules {@code EmiReloadManager.reload()} on the client thread. Client only, applied only with
 * ProgressiveStages; the target class loads only when EMI is installed. Fails soft ({@code require = 0}).
 */
@Pseudo
@Mixin(targets = "com.enviouse.progressivestages.client.emi.ProgressiveStagesEMIPlugin", remap = false)
public abstract class EmiReloadDeferMixin {
    @Inject(method = "triggerEmiReload", at = @At("HEAD"), cancellable = true, require = 0)
    private static void firmages$deferToAgeReload(CallbackInfo ci) {
        if (EmiReloadDefer.skip()) ci.cancel();
    }
}
