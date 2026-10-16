package dev.firmages.core.compat.progressivestages;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.IngredientGateHelper;
import com.enviouse.progressivestages.server.enforcement.RecipeEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * ProgressiveStages' crafting-grid rule for a craft outside a menu (ground crafting, SPEC §19.3). PS 3.0.6 checks a
 * crafting grid in its {@code CraftingMenuMixin} [verified, javap]: crafting enforcement on
 * ({@code StageConfig.isBlockCrafting}), creative bypass, the recipe id lock, the result's recipe-output lock, the
 * result item itself when locked outputs are hidden, and the ingredient gate. The same checks through its public
 * classes: {@code RecipeEnforcer.canCraftRecipe} (enforcement switch, creative bypass, recipe id),
 * {@code RecipeEnforcer.isOutputItemRecipeLocked}, {@code LockRegistry.isItemBlockedFor} and
 * {@code IngredientGateHelper.checkStacks}. These are not in PS's {@code api} package; the GameTest
 * {@code HardshipGameTests.groundCraft} keeps them honest.
 */
public final class CraftLocks {
    private CraftLocks() {}

    /** True if {@code player} may craft {@code recipe} with result {@code result} from {@code parts}. */
    public static boolean allowed(ServerPlayer player, RecipeHolder<?> recipe, ItemStack result, List<ItemStack> parts) {
        if (!StageConfig.isBlockCrafting()) return true;
        if (!RecipeEnforcer.canCraftRecipe(player, recipe)) return false;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return true;
        if (!result.isEmpty() && RecipeEnforcer.isOutputItemRecipeLocked(player, result.getItem())) return false;
        if (!result.isEmpty() && StageConfig.isHideLockRecipeOutput() && LockRegistry.getInstance().isItemBlockedFor(player, result.getItem())) return false;
        return IngredientGateHelper.checkStacks(player, parts).isEmpty();
    }
}
