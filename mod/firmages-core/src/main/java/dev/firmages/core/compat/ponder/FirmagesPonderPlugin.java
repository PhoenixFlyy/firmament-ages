package dev.firmages.core.compat.ponder;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.client.immersion.ClientAgeView;
import net.createmod.ponder.api.registration.IndexExclusionHelper;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;

/**
 * Ponder by Age (SPEC §21.6). Ponder 1.0.82 (in Create 6.0.10) collects the index exclusions of every plugin each
 * time the index screen is built ({@code PonderIndexScreen}, [verified, javap]), so a predicate that follows the
 * player's Ages hides the scenes of locked items from the index (opened from Create's config menu). The tag screens
 * ignore exclusions; {@code mixin.ponder.PonderTagScreenMixin} filters those. Client only, registered only with Ponder.
 */
public final class FirmagesPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return FirmagesCore.MOD_ID;
    }

    @Override
    public void indexExclusions(IndexExclusionHelper helper) {
        helper.exclude(il -> ClientAgeView.itemLocked(il.asItem()));
    }

    /** Client setup, when Ponder is loaded. */
    public static void register() {
        PonderIndex.addPlugin(new FirmagesPonderPlugin());
    }
}
