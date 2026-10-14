package dev.firmages.core.compat.emi;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.firmages.core.FirmagesCore;
import mekanism.api.recipes.ItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.client.recipe_viewer.emi.ChemicalEmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Evolved Mekanism's Chemixer and APT recipes in EMI (SPEC §21.12). Evolved Mekanism 1.2.1-fix3 shows them only through
 * its JEI categories ({@code jei/categories/ChemixerRecipeCategory}, {@code APTRecipeCategory}), which take Mekanism's
 * chemical ingredient type. Mekanism 10.7.19 registers that type with JEI only when EMI is absent, so with EMI JEI
 * throws "Unknown ingredient type: ChemicalStack" for all six recipes and JEMI has nothing to copy [verified, javap and
 * the 0.9.0 client log]. This class adds both categories to EMI with Mekanism's own chemical stack
 * ({@link ChemicalEmiStack}). The category ids are in Evolved Mekanism's namespace, so the Age view locks them with the
 * mod ({@code age_namespaces.json}).
 *
 * <p>The APT recipe is Mekanism API ({@link ItemStackChemicalToItemStackRecipe}); the Chemixer recipe
 * ({@code fr.iglee42.evolvedmekanism.recipes.ChemixerRecipe}) is read through its public getters by reflection, so
 * firmages-core needs no Evolved Mekanism jar to compile. Loaded only from {@link FirmagesEmiPlugin} when both mods are
 * present.
 */
final class EvolvedMekanismEmi {
    static final String MOD_ID = "evolvedmekanism";
    private static final ResourceLocation CHEMIXING = ResourceLocation.fromNamespaceAndPath(MOD_ID, "chemixing");
    private static final ResourceLocation APT = ResourceLocation.fromNamespaceAndPath(MOD_ID, "apt");

    private EvolvedMekanismEmi() {}

    static void register(EmiRegistry registry) {
        EmiRecipeCategory chemixing = category("firmages_chemixing", "chemixer", Component.translatable("block.evolvedmekanism.chemixer"));
        EmiRecipeCategory apt = category("firmages_apt", "apt_casing", Component.translatable("apt.evolvedmekanism.apt"));
        List<BasicEmiRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<?> h : registry.getRecipeManager().getRecipes()) {
            ResourceLocation serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(h.value().getSerializer());
            try {
                if (CHEMIXING.equals(serializer)) {
                    recipes.add(chemixer(chemixing, h.id(), h.value()));
                } else if (APT.equals(serializer) && h.value() instanceof ItemStackChemicalToItemStackRecipe r) {
                    recipes.add(new Recipe3(apt, h.id(), List.of(items(r.getItemInput()), chemicals(r.getChemicalInput())),
                        outputs(r.getOutputDefinition()), r.perTickUsage()));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                FirmagesCore.LOGGER.warn("EMI: Evolved Mekanism recipe {} not shown: {}", h.id(), e.toString());
            }
        }
        if (recipes.isEmpty()) return;
        registry.addCategory(chemixing);
        registry.addCategory(apt);
        registry.addWorkstation(chemixing, stack("chemixer"));
        for (String part : List.of("apt_casing", "apt_port", "supercharging_element")) registry.addWorkstation(apt, stack(part));
        recipes.forEach(registry::addRecipe);
        FirmagesCore.LOGGER.info("EMI: {} Evolved Mekanism Chemixer and APT recipes added (JEI-only categories)", recipes.size());
    }

    private static BasicEmiRecipe chemixer(EmiRecipeCategory cat, ResourceLocation id, Recipe<?> r) throws ReflectiveOperationException {
        ItemStackIngredient main = (ItemStackIngredient) getter(r, "getInputMain").invoke(r);
        ItemStackIngredient extra = (ItemStackIngredient) getter(r, "getInputExtra").invoke(r);
        ChemicalStackIngredient gas = (ChemicalStackIngredient) getter(r, "getInputGas").invoke(r);
        @SuppressWarnings("unchecked")
        List<ItemStack> out = (List<ItemStack>) getter(r, "getOutputDefinition").invoke(r);
        return new Recipe3(cat, id, List.of(items(main), items(extra), chemicals(gas)), outputs(out), false);
    }

    private static Method getter(Object o, String name) throws NoSuchMethodException {
        return o.getClass().getMethod(name);
    }

    private static EmiRecipeCategory category(String path, String icon, Component name) {
        return new EmiRecipeCategory(ResourceLocation.fromNamespaceAndPath(MOD_ID, path), stack(icon)) {
            @Override
            public Component getName() {
                return name;
            }
        };
    }

    private static EmiStack stack(String path) {
        ItemLike item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
        return EmiStack.of(item);
    }

    private static EmiIngredient items(ItemStackIngredient in) {
        return EmiIngredient.of(in.getRepresentations().stream().map(EmiStack::of).toList());
    }

    private static EmiIngredient chemicals(ChemicalStackIngredient in) {
        return EmiIngredient.of(in.getRepresentations().stream().map(ChemicalEmiStack::new).toList());
    }

    private static List<EmiStack> outputs(List<ItemStack> out) {
        return out.stream().map(EmiStack::of).toList();
    }

    /** Inputs in a row (items, then the chemical), an arrow, the output. */
    private static final class Recipe3 extends BasicEmiRecipe {
        private final boolean perTick;

        Recipe3(EmiRecipeCategory cat, ResourceLocation id, List<EmiIngredient> in, List<EmiStack> out, boolean perTick) {
            super(cat, id, in.size() * 20 + 60, 26);
            this.inputs.addAll(in);
            this.outputs.addAll(out);
            this.perTick = perTick;
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            int x = 0;
            for (int i = 0; i < inputs.size(); i++, x += 20) {
                var slot = widgets.addSlot(inputs.get(i), x, 4);
                if (perTick && i == inputs.size() - 1) slot.appendTooltip(Component.literal("Used per tick while it works"));
            }
            widgets.addTexture(EmiTexture.EMPTY_ARROW, x + 4, 5);
            widgets.addSlot(outputs.isEmpty() ? EmiStack.EMPTY : outputs.get(0), x + 34, 0).large(true).recipeContext(this);
        }
    }
}
