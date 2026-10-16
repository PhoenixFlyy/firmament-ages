package dev.firmages.core.compat.emi;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.firmages.core.machine.MachineRegistry;
import dev.firmages.core.machine.MachineText;
import dev.firmages.core.machine.OreProcessingRecipe;
import dev.firmages.core.machine.ProcessorRules;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.Arrays;
import java.util.List;

/**
 * EMI category {@code firmages:ore_processing} ("Ore Processing", SPEC §40.7) with the Ore Processor as workstation:
 * the ore pieces, the working fluid, the lubricant (optional, faster), the result, and as text the Mekanism chemical
 * (its name from Mekanism's lang), the time and the FE of one operation. The Age view hides the recipes until the
 * Singularity Age like any other. Called from {@link FirmagesEmiPlugin#register}.
 */
final class ProcessorEmi {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("firmages", "ore_processing");

    private ProcessorEmi() {}

    static void register(EmiRegistry registry) {
        EmiRecipeCategory cat = new EmiRecipeCategory(ID, EmiStack.of(MachineRegistry.ORE_PROCESSOR_ITEM.get())) {
            @Override
            public Component getName() {
                return Component.translatable("emi.category.firmages.ore_processing");
            }
        };
        registry.addCategory(cat);
        registry.addWorkstation(cat, EmiStack.of(MachineRegistry.ORE_PROCESSOR_ITEM.get()));
        for (RecipeHolder<OreProcessingRecipe> h : registry.getRecipeManager().getAllRecipesFor(MachineRegistry.ORE_PROCESSING_TYPE.get())) {
            registry.addRecipe(new Processing(cat, h));
        }
    }

    private static EmiIngredient fluid(SizedFluidIngredient f) {
        List<EmiStack> stacks = Arrays.stream(f.getFluids()).map((FluidStack s) -> EmiStack.of(s.getFluid(), s.getAmount())).toList();
        return EmiIngredient.of(stacks, f.amount());
    }

    private static final class Processing extends BasicEmiRecipe {
        private static final int W = 140;
        private static final int H = 58;
        private final OreProcessingRecipe recipe;

        Processing(EmiRecipeCategory cat, RecipeHolder<OreProcessingRecipe> h) {
            super(cat, h.id(), W, H);
            this.recipe = h.value();
            inputs.add(EmiIngredient.of(recipe.input().ingredient(), recipe.input().count()));
            inputs.add(fluid(recipe.fluid()));
            recipe.lubricant().ifPresent(l -> catalysts.add(fluid(l)));
            outputs.add(EmiStack.of(recipe.result()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(EmiIngredient.of(recipe.input().ingredient(), recipe.input().count()), 0, 0);
            widgets.addSlot(fluid(recipe.fluid()), 20, 0);
            recipe.lubricant().ifPresent(l -> widgets.addSlot(fluid(l), 40, 0).catalyst(true));
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 64, 1);
            widgets.addSlot(EmiStack.of(recipe.result()), 96, 0).recipeContext(this);
            widgets.addText(Component.translatable("firmages.emi.processor.chemical", MachineText.n(recipe.chemical().amount()),
                MachineText.chemicalName(recipe.chemical().id())), 0, 22, 0xFF404040, false);
            long fe = ProcessorRules.energyPerTick(ServerConfig.processorFePerTick(), 1.0) * recipe.time();
            String seconds = String.format(java.util.Locale.ROOT, "%.1f", recipe.time() / 20F).replace(".0", "");
            widgets.addText(Component.translatable("firmages.emi.processor.cost", seconds, MachineText.n(fe)), 0, 34, 0xFF404040, false);
            if (recipe.lubricant().isPresent()) {
                String speed = String.format(java.util.Locale.ROOT, "%.1f", ServerConfig.processorLubricantSpeed()).replace(".0", "");
                widgets.addText(Component.translatable("firmages.emi.processor.lubricant", speed), 0, 46, 0xFF404090, false);
            }
        }
    }
}
