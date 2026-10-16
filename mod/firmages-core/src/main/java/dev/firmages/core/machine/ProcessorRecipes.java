package dev.firmages.core.machine;

import dev.firmages.core.age.CacheGeneration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The live {@code firmages:ore_processing} recipes (SPEC §40.3), sorted by id and rebuilt after every datapack load and
 * every Age swap ({@link CacheGeneration}); the Age gate removes them until the Singularity Age (station Age in
 * {@code recipes/station_ages.js}).
 */
public final class ProcessorRecipes {
    private static RecipeManager cachedManager;
    private static int cachedGeneration = -1;
    private static List<RecipeHolder<OreProcessingRecipe>> cached = List.of();
    private static int generation;

    private ProcessorRecipes() {}

    public static synchronized List<RecipeHolder<OreProcessingRecipe>> all(Level level) {
        RecipeManager rm = level.getRecipeManager();
        int gen = CacheGeneration.get();
        if (rm == cachedManager && gen == cachedGeneration) return cached;
        List<RecipeHolder<OreProcessingRecipe>> list = new ArrayList<>(rm.getAllRecipesFor(MachineRegistry.ORE_PROCESSING_TYPE.get()));
        list.sort(Comparator.comparing(h -> h.id().toString()));
        cached = List.copyOf(list);
        cachedManager = rm;
        cachedGeneration = gen;
        generation++;
        return cached;
    }

    /** Changes whenever {@link #all} was rebuilt. */
    public static synchronized int generation(Level level) {
        all(level);
        return generation;
    }

    /** The first recipe that takes this item (any count). */
    public static Optional<RecipeHolder<OreProcessingRecipe>> forInput(Level level, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        for (RecipeHolder<OreProcessingRecipe> h : all(level)) if (h.value().takes(stack)) return Optional.of(h);
        return Optional.empty();
    }

    public static boolean chemicalUsed(Level level, ResourceLocation id) {
        for (RecipeHolder<OreProcessingRecipe> h : all(level)) if (h.value().chemical().id().equals(id)) return true;
        return false;
    }

    public static boolean fluidUsed(Level level, FluidStack fluid) {
        for (RecipeHolder<OreProcessingRecipe> h : all(level)) if (h.value().takesFluid(fluid)) return true;
        return false;
    }

    public static boolean lubricantUsed(Level level, FluidStack fluid) {
        for (RecipeHolder<OreProcessingRecipe> h : all(level)) if (h.value().takesLubricant(fluid)) return true;
        return false;
    }
}
