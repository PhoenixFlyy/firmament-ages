package dev.firmages.core.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.client.immersion.ClientAgeView;
import dev.firmages.core.immersion.EmiAgeView;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.material.Fluid;

import java.util.Optional;

/**
 * EMI by Age (SPEC §21). EMI 1.1.24 has three ways to hide a stack [verified, javap]: removing it from the index
 * (all ProgressiveStages and KubeJS do), which still shows it inside recipes, as a tag member and as a workstation;
 * removing recipes; and <b>disabling</b> it ({@code EmiInitRegistry.disableStacks}), which drops every recipe with a
 * fully disabled input, output or catalyst, every fully disabled workstation and every disabled tag member, and
 * hides it from the index. {@code initialize} runs on every EMI reload, and ProgressiveStages reloads EMI when the
 * player's stages change, so the predicate follows the Age.
 *
 * <p>Disabled: items and fluids of a locked Age ({@link EmiAgeView}), every other stack type (Mekanism chemicals) of a
 * locked mod, and items whose components name a locked mod: a Patchouli book ({@code patchouli:book}), a Modonomicon
 * book ({@code modonomicon:book_id}), enchantments and stored enchantments, potion contents. Removed: the recipes of
 * a locked mod's categories (IE world data, Mekanism's client-built Boiler, SPS and liquifier recipes, JEI-only
 * categories through JEMI) and EMI Loot tables of a locked mod.
 *
 * <p>EMI finds the class by its annotation ({@code EmiAgnosNeoForge} scans the mod files) and loads it only when EMI is
 * installed; it never loads on a server.
 */
@EmiEntrypoint
public final class FirmagesEmiPlugin implements EmiPlugin {
    private static final ResourceLocation PATCHOULI_BOOK = ResourceLocation.fromNamespaceAndPath("patchouli", "book");
    private static final ResourceLocation MODONOMICON_BOOK = ResourceLocation.fromNamespaceAndPath("modonomicon", "book_id");

    private static volatile EmiAgeView view = EmiAgeView.OPEN;
    private static DataComponentType<?> patchouliBook;
    private static DataComponentType<?> modonomiconBook;

    /** The view of the last EMI reload. */
    public static EmiAgeView view() {
        return view;
    }

    @Override
    public void initialize(EmiInitRegistry registry) {
        EmiAgeView v = ClientAgeView.current();
        view = v;
        patchouliBook = BuiltInRegistries.DATA_COMPONENT_TYPE.get(PATCHOULI_BOOK);
        modonomiconBook = BuiltInRegistries.DATA_COMPONENT_TYPE.get(MODONOMICON_BOOK);
        registry.disableStacks(s -> locked(v, s)); // this reload's view, also if another reload starts meanwhile
        EmiAudit.reloadStarted();
        FirmagesCore.LOGGER.info("EMI gate: {}", v.summary());
    }

    @Override
    public void register(EmiRegistry registry) {
        EmiAgeView v = view;
        registry.removeRecipes(r -> recipeLocked(v, r));
    }

    static boolean recipeLocked(EmiAgeView v, EmiRecipe r) {
        if (r.getCategory() == null) return false;
        ResourceLocation id = r.getId();
        return v.recipeLocked(r.getCategory().getId().toString(), id == null ? null : id.toString());
    }

    static boolean locked(EmiAgeView v, EmiStack s) {
        if (s == null || s.isEmpty()) return false;
        Object key = s.getKey();
        if (key instanceof Item item) return v.itemLocked(item) || componentsLocked(v, s.getComponentChanges());
        if (key instanceof Fluid fluid) return v.fluidLocked(fluid);
        ResourceLocation id = s.getId(); // chemicals and other stack types: by mod
        return id != null && v.namespaceLocked(id.getNamespace());
    }

    private static boolean componentsLocked(EmiAgeView v, DataComponentPatch patch) {
        if (patch == null || patch.isEmpty()) return false;
        if (idComponentLocked(v, patch, patchouliBook) || idComponentLocked(v, patch, modonomiconBook)) return true;
        if (enchantmentsLocked(v, patch.get(DataComponents.STORED_ENCHANTMENTS))) return true;
        if (enchantmentsLocked(v, patch.get(DataComponents.ENCHANTMENTS))) return true;
        Optional<? extends PotionContents> potion = patch.get(DataComponents.POTION_CONTENTS);
        if (potion != null && potion.isPresent()) {
            PotionContents pc = potion.get();
            if (pc.potion().isPresent() && holderLocked(v, pc.potion().get())) return true;
            for (MobEffectInstance e : pc.customEffects()) if (holderLocked(v, e.getEffect())) return true;
        }
        return false;
    }

    private static boolean idComponentLocked(EmiAgeView v, DataComponentPatch patch, DataComponentType<?> type) {
        if (type == null) return false;
        Optional<?> value = patch.get(type);
        return value != null && value.isPresent() && value.get() instanceof ResourceLocation rl && v.namespaceLocked(rl.getNamespace());
    }

    private static boolean enchantmentsLocked(EmiAgeView v, Optional<? extends ItemEnchantments> ench) {
        if (ench == null || ench.isEmpty()) return false;
        for (var holder : ench.get().keySet()) if (holderLocked(v, holder)) return true;
        return false;
    }

    private static boolean holderLocked(EmiAgeView v, Holder<?> holder) {
        return holder.unwrapKey().map(k -> v.namespaceLocked(k.location().getNamespace())).orElse(false);
    }
}
