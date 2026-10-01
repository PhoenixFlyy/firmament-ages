package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for the mods that add recipes after {@code RecipeManager#apply} (Create Dragons Plus writes its sandpaper
 * recipes at the TAIL of {@code ReloadableServerResources#updateRegistryTags}): on every server data load it adds
 * {@link #LATE_IRON} (age_2 output) and {@link #LATE_STONE} (no Age) to that load's recipe manager from
 * {@code TagsUpdatedEvent}, which runs inside {@code updateRegistryTags} before the firmages late pass.
 */
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class LateRecipeInjector {
    static final String LATE_IRON = "firmages:test/late_iron_from_dirt";
    static final String LATE_STONE = "firmages:test/late_stone_from_gravel";

    private static volatile RecipeManager pending;

    private LateRecipeInjector() {}

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent event) {
        pending = event.getServerResources().getRecipeManager();
    }

    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        RecipeManager manager = pending;
        pending = null;
        if (manager == null) return;
        List<RecipeHolder<?>> all = new ArrayList<>(manager.getRecipes());
        all.add(smelting(LATE_IRON, Items.DIRT, Items.IRON_INGOT));
        all.add(smelting(LATE_STONE, Items.GRAVEL, Items.STONE));
        manager.replaceRecipes(all);
    }

    private static RecipeHolder<?> smelting(String id, net.minecraft.world.item.Item in, net.minecraft.world.item.Item out) {
        return new RecipeHolder<>(ResourceLocation.parse(id),
            new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(in), new ItemStack(out), 0.1f, 200));
    }
}
