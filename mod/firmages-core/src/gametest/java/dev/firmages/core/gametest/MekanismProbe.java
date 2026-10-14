package dev.firmages.core.gametest;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Mekanism helpers for the GameTests; only loaded when Mekanism is in the run. */
final class MekanismProbe {
    private MekanismProbe() {}

    /** The enrichment chamber's input cache (what the machine asks every tick) finds a recipe for this input. */
    static boolean enrichingFinds(Level level, ItemStack input) {
        return mekanism.common.recipe.MekanismRecipeType.ENRICHING.getRecipeType().getInputCache().findFirstRecipe(level, input) != null;
    }
}
