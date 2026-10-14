package dev.firmages.core.compat.progressivestages;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.util.TextUtil;
import dev.firmages.core.immersion.TooltipTrim;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Locked items show nothing but ProgressiveStages' lines (SPEC §18.2, Felix 2026-10-14). ProgressiveStages 3.0.6
 * {@code client.ClientEventHandler.onItemTooltip} [verified, javap] masks line 0 and appends its block, but leaves
 * every other mod's lines (TFC "Melts into ...", heat, size). Run at LOWEST priority, after every mod. The lock test
 * is PS's own: not creative-bypassed, and the item's or its recipe's required stages (client lock cache, else the
 * local {@code LockRegistry}) contain one the player lacks. Client only; call only when PS is loaded.
 */
public final class LockedTooltip {
    private LockedTooltip() {}

    /** Drops every line of a locked item except its name and PS's block. @return true if the item is locked */
    public static boolean trim(ItemStack stack, List<Component> lines) {
        if (lines.isEmpty() || ClientLockCache.isCreativeBypass()) return false;
        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        Set<StageId> itemReq = ClientLockCache.getRequiredStagesForItem(id);
        if (itemReq.isEmpty()) itemReq = LockRegistry.getInstance().getRequiredStages(item);
        Set<StageId> recipeReq = ClientLockCache.getRequiredStagesForRecipeByOutput(id);
        if (recipeReq.isEmpty()) recipeReq = LockRegistry.getInstance().getRequiredStagesForRecipeByOutput(item);
        Set<StageId> itemMissing = missing(itemReq);
        Set<StageId> recipeMissing = missing(recipeReq);
        if (itemMissing.isEmpty() && recipeMissing.isEmpty()) return false;

        StageId stage = (itemMissing.isEmpty() ? recipeMissing : itemMissing).iterator().next(); // as PS picks it
        List<String> plain = new ArrayList<>(lines.size());
        for (Component c : lines) plain.add(plain(c));
        List<Integer> keep = TooltipTrim.lockedKeep(plain, lockedMessages(), descriptionLine(stage));
        List<Component> kept = new ArrayList<>(keep.size());
        for (int i : keep) kept.add(lines.get(i));
        lines.clear();
        lines.addAll(kept);
        return true;
    }

    private static Set<StageId> missing(Set<StageId> required) {
        Set<StageId> out = new LinkedHashSet<>();
        for (StageId s : required) if (!ClientStageCache.hasStage(s)) out.add(s);
        return out;
    }

    private static Set<String> lockedMessages() {
        return Set.of(plain(StageConfig.getMsgTooltipItemLocked()), plain(StageConfig.getMsgTooltipRecipeLocked()),
            plain(StageConfig.getMsgTooltipItemAndRecipeLocked()));
    }

    /** PS's description line for {@code stage}, or null; PS adds it only when the stage is set to show it, the
     *  comparison in {@link TooltipTrim#lockedKeep} keeps it only if it is really there. */
    private static String descriptionLine(StageId stage) {
        String d = ClientStageCache.getDescription(stage);
        if (d == null || d.isEmpty()) return null;
        return plain(StageConfig.getMsgTooltipStageDescription().replace("{description}", d));
    }

    private static String plain(String colorCoded) {
        return plain(TextUtil.parseColorCodes(colorCoded));
    }

    private static String plain(Component c) {
        String s = ChatFormatting.stripFormatting(c.getString());
        return s == null ? "" : s.trim();
    }
}
