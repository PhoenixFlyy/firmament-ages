package dev.firmages.core.gate.extract;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.firmages.core.gate.OutputExtractor;
import dev.firmages.core.gate.OutputSink;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Create 6.0.10 [verified, javap]: {@code ProcessingRecipe#getRollableResults} and {@code #getFluidResults}
 * (mixing, compacting, pressing, crushing, milling, deploying, fan processing, ...), and the result pool of
 * {@code SequencedAssemblyRecipe} (the transitional item is not an output).
 */
public final class CreateExtractor implements OutputExtractor {
    @Override
    public boolean extract(Recipe<?> recipe, OutputSink sink, HolderLookup.Provider registries) {
        if (recipe instanceof ProcessingRecipe<?, ?> p) {
            for (ProcessingOutput o : p.getRollableResults()) ExtractSupport.item(sink, o.getStack());
            for (FluidStack f : p.getFluidResults()) ExtractSupport.fluid(sink, f);
            return true;
        }
        if (recipe instanceof SequencedAssemblyRecipe s) {
            for (ProcessingOutput o : s.resultPool) ExtractSupport.item(sink, o.getStack());
            return true;
        }
        return false;
    }

    @Override
    public String name() {
        return "create";
    }
}
