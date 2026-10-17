package dev.firmages.core.immersion;

import java.util.Set;

/**
 * EMI gating per Age (SPEC §18.3). Pure Java (unit-tested). EMI is forced off while the world's Age is before
 * {@code ui.emiFromAge}, its own toggle key included (the client re-applies every tick). When the Age opens it, EMI is
 * switched on once; after that the player's own toggle is left alone. Operators (permission level 2) and players in
 * creative mode have the viewer in every Age (SPEC §21.3).
 */
public final class ViewerGate {
    /**
     * Recipe screens that stay shut while the viewer is closed for the player (SPEC §21.3), by class name. EMI's recipe
     * and recipe-tree screens: {@code EmiApi.displayRecipes} and the JEI API through JEMI open them without checking
     * {@code EmiConfig.enabled} [verified, javap, EMI 1.1.24]. JEI's own recipe screen {@code RecipesGui} (the only
     * {@code Screen} of JEI 19.64.0.453, opened through {@code Minecraft.setScreen} [verified, javap]) is never built
     * while EMI runs (EMI's {@code PluginCallerMixin} skips JEI's GUI plugins); it is listed in case it ever is.
     */
    public static final Set<String> VIEWER_SCREENS = Set.of("dev.emi.emi.screen.RecipeScreen", "dev.emi.emi.screen.BoMScreen",
        "mezz.jei.gui.recipes.RecipesGui");

    private ViewerGate() {}

    /** A screen of {@link #VIEWER_SCREENS} does not open while the viewer is closed ({@link #open} false). */
    public static boolean screenBlocked(String screenClass, boolean viewerOpen) {
        return !viewerOpen && screenClass != null && VIEWER_SCREENS.contains(screenClass);
    }

    /**
     * @param ageAllows the world's Age opens the viewer ({@code ui.emiFromAge})
     * @param operator  the player has permission level 2
     * @param creative  the player is in creative mode
     * @return EMI and its recipe screens are open for this player
     */
    public static boolean open(boolean ageAllows, boolean operator, boolean creative) {
        return ageAllows || operator || creative;
    }

    /**
     * @param allowed     the current status allows the viewer
     * @param wasAllowed  the decision of the previous tick, {@code null} right after joining a world
     * @param enabledNow  EMI's current {@code enabled} flag
     * @return the value to set, or {@code null} to leave EMI as it is
     */
    public static Boolean target(boolean allowed, Boolean wasAllowed, boolean enabledNow) {
        if (!allowed) return enabledNow ? Boolean.FALSE : null;
        if (wasAllowed == null || !wasAllowed) return enabledNow ? null : Boolean.TRUE;
        return null;
    }

    /** TFC nutrition lines of food tooltips (TFC 4.2.11 {@code IFood.addTooltipInfo} [verified, javap]). */
    public static boolean isNutritionKey(String translationKey) {
        return translationKey.equals("tfc.tooltip.nutrition") || translationKey.startsWith("tfc.tooltip.nutrition_")
            || translationKey.equals("tfc.tooltip.hold_shift_for_nutrition_info") || translationKey.startsWith("tfc.enum.nutrient.");
    }
}
