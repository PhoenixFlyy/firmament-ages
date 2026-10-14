package dev.firmages.core.client.immersion;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.immersion.AgeStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * One EMI reload per Age change (SPEC §3.3). {@code firmages:age_status} carries {@code reloadPending} from the stage
 * change until the Age swap (or reload) is done. Meanwhile:
 * <ul>
 *   <li>ProgressiveStages' own EMI reload on the stage change is held back ({@code mixin.psemi.EmiReloadHoldMixin});</li>
 *   <li>when the swap is done: an Age swap sends recipes without tags, and EMI 1.1.24 waits for both
 *       ({@code EmiReloadManager.reloadRecipes}: "Recipes synchronized, waiting for tags" [verified, javap]), so the
 *       client marks the tags as synchronized ({@code reloadTags()}), which reloads EMI once with the new recipes and the
 *       new stages. A full reload sends both and EMI reloads by itself. If no recipes came (nothing reloaded), the held
 *       back reload runs.</li>
 * </ul>
 * JEI restarts on the recipe sync by itself (19.64 {@code StartEventObserver.onRecipesUpdatedEvent}), before this runs,
 * so EMI's JEI-backed categories (JEMI) read the new recipes. Physical client only.
 */
@EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
public final class EmiReloadGate {
    private static boolean pending;
    private static boolean heldBack;
    private static boolean recipesSeen;
    private static boolean tagsSeen;

    private EmiReloadGate() {}

    /** Mixin hook: true holds back ProgressiveStages' EMI reload (a swap is pending and reloads EMI afterwards). */
    public static boolean holdBack() {
        if (!pending) return false;
        heldBack = true;
        return true;
    }

    /** {@code firmages:age_status} arrived (before it is stored). */
    static void onStatus(AgeStatus next) {
        if (next.reloadPending() && !pending) {
            pending = true;
            heldBack = false;
            recipesSeen = false;
            tagsSeen = false;
        } else if (!next.reloadPending() && pending) {
            pending = false;
            if (recipesSeen && !tagsSeen) {
                if (EmiBridge.reloadTags()) FirmagesCore.LOGGER.info("EMI reloads once after the Age swap (recipes synced, tags unchanged)");
            } else if (heldBack && !recipesSeen) {
                EmiBridge.reload();
            }
            heldBack = false;
        }
    }

    @SubscribeEvent
    public static void recipes(RecipesUpdatedEvent event) {
        if (pending) recipesSeen = true;
    }

    @SubscribeEvent
    public static void tags(TagsUpdatedEvent event) {
        if (pending && event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) tagsSeen = true;
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = false;
        heldBack = false;
        recipesSeen = false;
        tagsSeen = false;
    }
}
