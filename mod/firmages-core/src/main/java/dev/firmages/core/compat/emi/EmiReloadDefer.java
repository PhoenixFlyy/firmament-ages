package dev.firmages.core.compat.emi;

import dev.emi.emi.runtime.EmiReloadManager;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.client.immersion.ClientAgeStatus;

/**
 * One EMI reload per Age grant instead of two (SPEC §21.5, research n2-perf item 2). ProgressiveStages 3.0.6 reloads
 * EMI as soon as the player's stages change ({@code ProgressiveStagesEMIPlugin.triggerEmiReload}), with the recipe
 * set that the Age reload replaces three seconds later; the recipe and tag sync after that reload reloads EMI again.
 * While {@code firmages:age_status} says an Age reload is pending, {@code mixin.ps.EmiReloadDeferMixin} skips the first
 * one. Stage changes without an Age reload (game mode, mob stages) keep theirs.
 *
 * <p>Safety net: if the pending reload ends without any EMI reload since the skipped one (a failed reload sends no
 * recipes), the skipped reload runs when the status clears. Client only; needs EMI and ProgressiveStages.
 */
public final class EmiReloadDefer {
    private static int reloadsAtSkip = -1;
    private static int skipped;

    private EmiReloadDefer() {}

    /** From the mixin: true cancels ProgressiveStages' reload. */
    public static boolean skip() {
        if (!ClientAgeStatus.known() || !ClientAgeStatus.get().reloadPending()) return false;
        if (reloadsAtSkip < 0) reloadsAtSkip = FirmagesEmiPlugin.reloads();
        skipped++;
        FirmagesCore.LOGGER.info("ProgressiveStages EMI reload skipped: an Age reload follows and reloads EMI ({} skipped so far)", skipped);
        return true;
    }

    /** The status no longer says pending: run the skipped reload if no EMI reload started since. */
    public static void pendingCleared() {
        if (reloadsAtSkip < 0) return;
        boolean none = FirmagesEmiPlugin.reloads() == reloadsAtSkip && EmiReloadManager.isLoaded();
        reloadsAtSkip = -1;
        if (none) {
            FirmagesCore.LOGGER.info("Age reload ended without an EMI reload: running the skipped ProgressiveStages reload");
            com.enviouse.progressivestages.client.emi.ProgressiveStagesEMIPlugin.triggerEmiReload();
        }
    }

    public static void reset() {
        reloadsAtSkip = -1;
    }
}
