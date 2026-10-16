package dev.firmages.core.anchor;

import dev.firmages.core.shrine.BlessingSources;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * {@code firmages:blessing_shard} (SPEC-himmel §29.3, Felix S1A): "Shard of &lt;blessing&gt;", one item with the
 * component {@code firmages:tier} (0..8). It goes into a Sky Anchor's slot of its tier. A shard is never a relic: no
 * plinth takes it and the Gathering does not count it.
 */
public final class BlessingShardItem extends Item {
    public BlessingShardItem(Properties properties) {
        super(properties);
    }

    /** A shard of {@code tier} (0..8). */
    public static ItemStack of(int tier) {
        ItemStack s = new ItemStack(AnchorRegistry.BLESSING_SHARD.get());
        s.set(AnchorRegistry.TIER.get(), Math.max(0, Math.min(BlessingSources.TIERS - 1, tier)));
        return s;
    }

    /** The tier of a shard stack, -1 when it is no shard. */
    public static int tier(ItemStack stack) {
        if (!(stack.getItem() instanceof BlessingShardItem)) return -1;
        Integer t = stack.get(AnchorRegistry.TIER.get());
        return t == null ? 0 : t;
    }

    /** The lang key of the shard's name, one per tier (the blessing of that tier, {@code tier/ring_N.json}). */
    public static String nameKey(int tier) {
        return "item.firmages.blessing_shard.tier_" + tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(nameKey(tier(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.firmages.blessing_shard.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
