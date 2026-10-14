package dev.firmages.core.mixin.ponder;

import dev.firmages.core.client.immersion.ClientAgeView;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Ponder tag screens show no item of a locked Age (SPEC §21.6). Ponder 1.0.82 {@code PonderTagScreen.init} [verified,
 * javap] fills {@code items} from the tag registry, drops the tag's main item, then lays the list out from its size;
 * index exclusions are not read there. The locked entries are removed before the layout (first {@code List.size()}).
 * Client only, applied only with Ponder; fails soft.
 */
@Mixin(value = PonderTagScreen.class, remap = false)
public abstract class PonderTagScreenMixin {
    @Shadow
    @Final
    protected List<PonderTagScreen.ItemEntry> items;

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 0), require = 0)
    private void firmages$hideLocked(CallbackInfo ci) {
        items.removeIf(e -> ClientAgeView.itemLocked(e.item().asItem()));
    }
}
