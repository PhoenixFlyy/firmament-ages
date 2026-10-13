package dev.firmages.core.mixin.tfc;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.tfc.TfcTabs;
import net.dries007.tfc.network.SwitchInventoryTabPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server guard of the TFC tabs (SPEC §18.6): a tab whose Age is not open ({@code ui.*TabFromAge}) cannot be switched
 * to, so a client that still shows the button (another mod, an older firmages-core) cannot open the page either.
 * TFC 4.2.11 {@code SwitchInventoryTabPacket.handle(ServerPlayer)} [verified, javap].
 */
@Mixin(value = SwitchInventoryTabPacket.class, remap = false)
public abstract class SwitchInventoryTabMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void firmages$gateByAge(ServerPlayer player, CallbackInfo ci) {
        if (player == null) return;
        SwitchInventoryTabPacket self = (SwitchInventoryTabPacket) (Object) this;
        if (!TfcTabs.allowed(player, self.tab())) {
            FirmagesCore.LOGGER.debug("TFC tab {} refused for {}: its Age is not open", self.tab(), player.getGameProfile().getName());
            ci.cancel();
        }
    }
}
