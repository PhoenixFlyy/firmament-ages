package dev.firmages.core.compat.emi;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.firmages.core.crown.Chord;
import dev.firmages.core.crown.CrownRegistry;
import dev.firmages.core.crown.CrownText;
import dev.firmages.core.crown.StarforgeRecipe;
import dev.firmages.core.net.ShrineSyncPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.Arrays;
import java.util.List;

/**
 * EMI category {@code firmages:starforge} ("Starforging", SPEC §27.11) with the Star Anvil and the Lens Mount as
 * workstations. A recipe shows its inputs, its fluid, the result, nine dots (filled in the tier colour when the lens is
 * part of the chord, hollow otherwise), the chord name, the time and the night bonus. The Age view (SPEC §21) hides
 * locked lenses and alloys like any item. Called from {@link FirmagesEmiPlugin#register}.
 */
final class CrownEmi {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("firmages", "starforge");

    private CrownEmi() {}

    static void register(EmiRegistry registry) {
        EmiRecipeCategory cat = new EmiRecipeCategory(ID, EmiStack.of(CrownRegistry.STAR_ANVIL_ITEM.get())) {
            @Override
            public Component getName() {
                return Component.translatable("emi.category.firmages.starforge");
            }
        };
        registry.addCategory(cat);
        registry.addWorkstation(cat, EmiStack.of(CrownRegistry.STAR_ANVIL_ITEM.get()));
        registry.addWorkstation(cat, EmiStack.of(CrownRegistry.LENS_MOUNT_ITEM.get()));
        for (RecipeHolder<StarforgeRecipe> h : registry.getRecipeManager().getAllRecipesFor(CrownRegistry.STARFORGE_TYPE.get())) {
            registry.addRecipe(new Forging(cat, h));
        }
    }

    private static EmiIngredient fluid(SizedFluidIngredient f) {
        List<EmiStack> stacks = Arrays.stream(f.getFluids()).map((FluidStack s) -> EmiStack.of(s.getFluid(), s.getAmount())).toList();
        return EmiIngredient.of(stacks, f.amount());
    }

    private static final class Forging extends BasicEmiRecipe {
        private static final int W = 150;
        private static final int H = 73;
        private final StarforgeRecipe recipe;

        Forging(EmiRecipeCategory cat, RecipeHolder<StarforgeRecipe> h) {
            super(cat, h.id(), W, H);
            this.recipe = h.value();
            recipe.ingredients().forEach(i -> inputs.add(EmiIngredient.of(i.ingredient(), i.count())));
            recipe.fluid().ifPresent(f -> inputs.add(fluid(f)));
            outputs.add(EmiStack.of(recipe.result()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            int n = recipe.ingredients().size();
            for (int i = 0; i < 4; i++) {
                widgets.addSlot(i < n ? EmiIngredient.of(recipe.ingredients().get(i).ingredient(), recipe.ingredients().get(i).count()) : EmiStack.EMPTY,
                    (i % 2) * 18, (i / 2) * 18);
            }
            recipe.fluid().ifPresent(f -> widgets.addSlot(fluid(f), 40, 9));
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 64, 10);
            widgets.addSlot(EmiStack.of(recipe.result()), 96, 5).large(true).recipeContext(this);
            int mask = recipe.chordMask();
            widgets.addDrawable(0, 40, 9 * 8, 8, (g, mx, my, delta) -> {
                for (int lens = 0; lens < Chord.LENSES; lens++) {
                    int rgb = 0xFF000000 | ShrineSyncPayload.clientTierColor(lens).orElse(0xFFFFFF);
                    int x = lens * 8;
                    if ((mask & (1 << lens)) != 0) {
                        g.fill(x + 1, 1, x + 7, 7, rgb);
                    } else {
                        g.fill(x + 1, 1, x + 7, 2, rgb);
                        g.fill(x + 1, 6, x + 7, 7, rgb);
                        g.fill(x + 1, 2, x + 2, 6, rgb);
                        g.fill(x + 6, 2, x + 7, 6, rgb);
                    }
                }
            });
            List<Component> lensNames = Chord.lenses(mask).stream().map(l -> (Component) CrownText.lensName(l)).toList();
            widgets.addTooltipText(lensNames, 0, 40, 9 * 8, 8);
            widgets.addText(CrownText.chordName(recipe.chord().toString()), 76, 40, 0xFF404040, false);
            String seconds = String.format(java.util.Locale.ROOT, "%.1f", recipe.time() / 20F).replace(".0", "");
            widgets.addText(Component.translatable("firmages.emi.crown.time", seconds), 0, 52, 0xFF404040, false);
            widgets.addText(Component.translatable("firmages.emi.crown.night"), 0, 63, 0xFF404090, false);
        }
    }
}
