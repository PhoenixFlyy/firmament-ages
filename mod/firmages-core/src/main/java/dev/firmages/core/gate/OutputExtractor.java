package dev.firmages.core.gate;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Typed output extraction for one mod's recipe classes (SPEC §4.2 step 1). Implementations live in
 * {@code gate.extract} and are only instantiated when their mod is loaded.
 * <p>
 * Extractors must read the recipe's structure only. They must never resolve tags (no {@code Ingredient#getItems},
 * no IE {@code TagOutput#get}, no Occultism {@code RecipeResult#getStack} on tag results): the tags of the load in
 * progress are not bound yet, and those calls cache their first answer in the recipe for the rest of the session.
 */
public interface OutputExtractor {
    /**
     * @return true if this extractor handles the recipe (first match wins; the vanilla {@code getResultItem} step is
     * then skipped). The JSON walk runs in addition in every case.
     */
    boolean extract(Recipe<?> recipe, OutputSink sink, HolderLookup.Provider registries) throws Exception;

    String name();
}
