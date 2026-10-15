package dev.firmages.core.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A consecrated shard, shed by The Primordial in phase 3 (Firmament Shatter). Use: ten seconds of Firmament Ward
 * (star-fall passes over you). Sneak + use: throw it; a hit on the boss opens its veil. Held in the off hand, a melee
 * hit on the boss opens the veil and spends the shard. Never crafted; the last ones are keepsakes (Firmament Glass).
 */
public class ConsecratedShardItem extends Item {
    public static final int WARD_TICKS = 200;

    public ConsecratedShardItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ThrownShard shard = new ThrownShard(level, player);
                shard.setItem(stack.copyWithCount(1));
                shard.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
                level.addFreshEntity(shard);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 1.4F);
            player.getCooldowns().addCooldown(this, 10);
        } else {
            if (!level.isClientSide) player.addEffect(new MobEffectInstance(BossRegistry.FIRMAMENT_WARD, WARD_TICKS, 0));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), BossRegistry.SHARD.get(), SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.firmages.consecrated_shard.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
