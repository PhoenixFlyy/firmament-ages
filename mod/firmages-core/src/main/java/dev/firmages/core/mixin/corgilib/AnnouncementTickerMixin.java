package dev.firmages.core.mixin.corgilib;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Join silence (SPEC §18.1). Corgilib 5.0.0.10 [verified, javap]: {@code AnnouncementInfoClientTicker
 * .checkedAnnouncementTicker(Player)} drives a remote announcement (chat after 5 minutes or a widget on every
 * screen); its config only chooses CHAT or WIDGET. The ticker is skipped. Client only, applied only when Corgilib
 * is loaded.
 */
@Pseudo
@Mixin(targets = "corgitaco.corgilib.client.AnnouncementInfoClientTicker", remap = false)
public abstract class AnnouncementTickerMixin {
    @Inject(method = "checkedAnnouncementTicker", at = @At("HEAD"), cancellable = true, require = 0)
    private static void firmages$noAnnouncements(Player player, CallbackInfo ci) {
        ci.cancel();
    }
}
