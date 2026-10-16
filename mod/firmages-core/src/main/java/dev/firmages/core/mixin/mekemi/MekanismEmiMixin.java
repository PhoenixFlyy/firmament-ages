package dev.firmages.core.mixin.mekemi;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import mekanism.client.recipe_viewer.emi.MekanismEmi;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The Fusion Neutron Activator in EMI's activating category (SPEC §21.13, round F1). Mekanism: Neutron Activator 1.0.0
 * runs Mekanism's {@code mekanism:activating} recipes but names itself as a catalyst for JEI only
 * ({@code client/recipe_viewer/jei/MNAJEI}); Mekanism's EMI plugin takes the workstations of a category from its own
 * recipe-viewer type (the Solar Neutron Activator) [verified, javap]. This adds the pack's open one, the fusion block
 * (age_8, {@code recipes/age_8/quantum_age.js}), to the same category. The fission block has no recipe in the pack and
 * stays out. The EMI Age view (§21) hides the workstation before its Age like any item.
 */
@Mixin(value = MekanismEmi.class, remap = false)
public abstract class MekanismEmiMixin {
    @Unique
    private static final ResourceLocation FUSION_ACTIVATOR =
        ResourceLocation.fromNamespaceAndPath("mekanismneutronactivator", "fusion_neutron_activator");

    @Inject(method = "addWorkstations", at = @At("HEAD"))
    private static void firmages$fusionActivator(EmiRegistry registry, EmiRecipeCategory category, List<ItemLike> workstations,
                                                 CallbackInfo ci) {
        if (!RecipeViewerRecipeType.ACTIVATING.id().equals(category.getId())) return;
        BuiltInRegistries.ITEM.getOptional(FUSION_ACTIVATOR).ifPresent(item -> registry.addWorkstation(category, EmiStack.of(item)));
    }
}
