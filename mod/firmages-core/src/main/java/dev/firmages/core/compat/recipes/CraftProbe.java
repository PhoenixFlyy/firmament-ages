package dev.firmages.core.compat.recipes;

import dev.firmages.core.gate.RecipeGate;
import dev.firmages.core.gate.RecipeMaster;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The crafting table's lookup after an Age swap (SPEC §3.3): lays the ingredients of a shaped or shapeless recipe
 * into a crafting input and asks the live recipe manager the way the crafting table does ({@code getRecipeFor} with
 * four arguments, the overload FastSuite caches). Used by {@code /firmages recipes craft <id>} and by the selftest case
 * {@code crafting_table_follows_ages}.
 */
public final class CraftProbe {
    private CraftProbe() {}

    /**
     * @param live  the recipe is in the live manager (else it is kept in the gate's master list: locked by its Age)
     * @param found the recipe the lookup returns
     */
    public record Result(ResourceLocation id, boolean live, Optional<RecipeHolder<CraftingRecipe>> found) {}

    /** Probes {@code id}; empty when it is neither live nor in the master list, or not a probeable crafting recipe. */
    public static Optional<Result> probe(MinecraftServer server, ResourceLocation id) {
        RecipeManager rm = server.getRecipeManager();
        RecipeHolder<?> holder = rm.byKey(id).orElse(null);
        boolean live = holder != null;
        if (holder == null) {
            RecipeMaster master = RecipeGate.master(rm);
            if (master != null) {
                holder = master.entries().stream().map(RecipeMaster.Entry::holder).filter(h -> h.id().equals(id)).findFirst().orElse(null);
            }
        }
        if (holder == null || !(holder.value() instanceof CraftingRecipe cr)) return Optional.empty();
        CraftingInput input = craftingInput(cr);
        if (input == null) return Optional.empty();
        return Optional.of(new Result(id, live, rm.getRecipeFor(RecipeType.CRAFTING, input, server.overworld(), (RecipeHolder<CraftingRecipe>) null)));
    }

    /**
     * Up to {@code limit} probeable recipes, evenly spread over the id order: live ones ({@code live = true}) or the
     * master list's crafting recipes the Ages keep out of the manager. Only plain {@link ShapedRecipe} and
     * {@link ShapelessRecipe}: subclasses may match on more than their items.
     */
    public static List<ResourceLocation> sample(MinecraftServer server, boolean live, int limit) {
        RecipeManager rm = server.getRecipeManager();
        List<RecipeHolder<?>> pool = new ArrayList<>();
        if (live) {
            pool.addAll(rm.getAllRecipesFor(RecipeType.CRAFTING));
        } else {
            RecipeMaster master = RecipeGate.master(rm);
            if (master != null) {
                master.entries().stream().map(RecipeMaster.Entry::holder).filter(h -> rm.byKey(h.id()).isEmpty()).forEach(pool::add);
            }
        }
        List<ResourceLocation> ids = pool.stream()
            .filter(h -> h.value().getClass() == ShapedRecipe.class || h.value().getClass() == ShapelessRecipe.class)
            .filter(h -> craftingInput((CraftingRecipe) h.value()) != null)
            .map(RecipeHolder::id).sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        if (ids.size() <= limit) return ids;
        List<ResourceLocation> out = new ArrayList<>();
        for (int i = 0; i < limit; i++) out.add(ids.get((int) ((long) i * ids.size() / limit)));
        return out;
    }

    /** The crafting grid for a shaped or shapeless recipe: the first item of each ingredient; null for other recipes. */
    static CraftingInput craftingInput(CraftingRecipe recipe) {
        int width;
        int height;
        if (recipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
        } else if (recipe instanceof ShapelessRecipe) {
            int n = recipe.getIngredients().size();
            if (n == 0 || n > 9) return null;
            width = Math.min(n, 3);
            height = (n + 2) / 3;
        } else {
            return null;
        }
        List<ItemStack> items = new ArrayList<>();
        for (Ingredient i : recipe.getIngredients()) {
            if (i.isEmpty()) {
                items.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack[] options = i.getItems();
            if (options.length == 0) return null;
            items.add(options[0].copyWithCount(1));
        }
        while (items.size() < width * height) items.add(ItemStack.EMPTY);
        return CraftingInput.of(width, height, items);
    }
}
