package dev.firmages.core.gate.extract;

import blusunrize.immersiveengineering.api.crafting.IESerializableRecipe;
import blusunrize.immersiveengineering.api.crafting.StackWithChance;
import blusunrize.immersiveengineering.api.crafting.TagOutput;
import blusunrize.immersiveengineering.api.crafting.TagOutputList;
import dev.firmages.core.gate.OutputExtractor;
import dev.firmages.core.gate.OutputSink;
import dev.firmages.core.mixin.ie.TagOutputAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;

import java.lang.reflect.Field;

/**
 * Immersive Engineering 12.4.2 [verified, javap]: every {@code IESerializableRecipe} (all multiblocks incl.
 * {@code MultiblockRecipe#outputList}, crusher secondaries, arc furnace slag, and the single-block machines).
 * Output fields of type {@link TagOutput}, {@link TagOutputList}, {@link StackWithChance}, {@link ItemStack} or
 * {@link FluidStack} (also inside lists) are read without resolving tags: a tag output is recorded as the tag
 * ({@code TagOutput.rawData} left side), so the gate's "all members locked" rule applies. Input fields
 * (names with input/ingredient/catalyst/reagent, and ingredient-typed values) are skipped.
 * {@code getResultItem} is never called for IE recipes, because it caches the resolved stack.
 */
public final class IEExtractor implements OutputExtractor {
    @Override
    public boolean extract(Recipe<?> recipe, OutputSink sink, HolderLookup.Provider registries) throws IllegalAccessException {
        if (!(recipe instanceof IESerializableRecipe)) return false;
        for (Field f : ExtractSupport.outputFields(recipe.getClass(), IESerializableRecipe.class)) {
            Object v = f.get(recipe);
            if (v == null) continue;
            for (Object e : ExtractSupport.elements(v)) value(e, sink);
        }
        return true;
    }

    private static void value(Object v, OutputSink sink) {
        if (v instanceof TagOutput t) tagOutput(t, sink);
        else if (v instanceof TagOutputList l) l.getLazyList().forEach(t -> tagOutput(t, sink));
        else if (v instanceof StackWithChance s) tagOutput(s.stack(), sink);
        else if (v instanceof ItemStack s) ExtractSupport.item(sink, s);
        else if (v instanceof FluidStack f) ExtractSupport.fluid(sink, f);
    }

    private static void tagOutput(TagOutput t, OutputSink sink) {
        if (t == null) return;
        ((TagOutputAccessor) t).firmages$rawData().ifLeft(i -> ExtractSupport.ingredient(sink, i.getBaseIngredient()))
            .ifRight(s -> ExtractSupport.item(sink, s));
    }

    @Override
    public String name() {
        return "immersiveengineering";
    }
}
