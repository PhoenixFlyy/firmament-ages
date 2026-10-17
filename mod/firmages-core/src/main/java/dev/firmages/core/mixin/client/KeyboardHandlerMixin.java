package dev.firmages.core.mixin.client;

import dev.firmages.core.client.immersion.F3VeilClient;
import dev.firmages.core.immersion.F3Veil;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The F3 veil's key combinations (SPEC §18.11): while the debug screen is veiled, F3+C (the position as a teleport
 * command) and F3+I (the targeted block's or entity's data) copy nothing to the clipboard. NeoForge 21.1.252
 * {@code KeyboardHandler.handleDebugKeys(int)} handles every F3 combination in one switch and answers whether the key
 * was used [verified, sources]; the head of it returns {@link F3Veil#vetoedDebugKey} for these two keys, the answers
 * vanilla gives with reduced debug info. Every other combination stays vanilla's.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void firmages$veilCopy(int key, CallbackInfoReturnable<Boolean> cir) {
        Boolean veto = F3Veil.vetoedDebugKey(key);
        if (veto != null && !F3VeilClient.open()) cir.setReturnValue(veto);
    }
}
