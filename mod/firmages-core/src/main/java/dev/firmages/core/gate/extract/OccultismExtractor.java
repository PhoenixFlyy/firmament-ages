package dev.firmages.core.gate.extract;

import com.klikli_dev.occultism.crafting.recipe.MinerRecipe;
import com.klikli_dev.occultism.crafting.recipe.result.ItemRecipeResult;
import com.klikli_dev.occultism.crafting.recipe.result.RecipeResult;
import com.klikli_dev.occultism.crafting.recipe.result.TagRecipeResult;
import com.klikli_dev.occultism.crafting.recipe.result.WeightedItemRecipeResult;
import com.klikli_dev.occultism.crafting.recipe.result.WeightedTagRecipeResult;
import dev.firmages.core.gate.OutputExtractor;
import dev.firmages.core.gate.OutputSink;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Occultism 1.224.4 [verified, javap]: {@code MinerRecipe#getWeightedResult} (the Dimensional Mineshaft), and for
 * every other Occultism recipe (rituals, spirit fire, crushing, ...) its {@code RecipeResult} fields and
 * {@link ItemStack} fields named like a result or output. Tag results are recorded as tags through
 * {@code tag()}: {@code getStack()} on a tag result resolves the tag and caches the stack
 * ({@code cachedOutputStack}), so neither it nor {@code getResultItem} is ever called for Occultism recipes.
 */
public final class OccultismExtractor implements OutputExtractor {
    private static final String PACKAGE = "com.klikli_dev.occultism.";

    @Override
    public boolean extract(Recipe<?> recipe, OutputSink sink, HolderLookup.Provider registries) throws IllegalAccessException {
        if (recipe instanceof MinerRecipe m) {
            result(m.getWeightedResult(), sink);
            return true;
        }
        if (!recipe.getClass().getName().startsWith(PACKAGE)) return false;
        for (Field f : ExtractSupport.outputFields(recipe.getClass(), Object.class)) {
            Object v = f.get(recipe);
            String n = f.getName().toLowerCase(Locale.ROOT);
            for (Object e : ExtractSupport.elements(v)) {
                if (e instanceof RecipeResult r) result(r, sink);
                else if (e instanceof ItemStack s && (n.contains("result") || n.contains("output"))) ExtractSupport.item(sink, s);
            }
        }
        return true;
    }

    private static void result(RecipeResult r, OutputSink sink) {
        if (r instanceof WeightedTagRecipeResult t) sink.tag(OutputSink.Kind.ITEM, t.tag().location().toString());
        else if (r instanceof TagRecipeResult t) sink.tag(OutputSink.Kind.ITEM, t.tag().location().toString());
        else if (r instanceof WeightedItemRecipeResult || r instanceof ItemRecipeResult) ExtractSupport.item(sink, r.getStack());
    }

    @Override
    public String name() {
        return "occultism";
    }
}
