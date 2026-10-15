package dev.firmages.core.wayshrine;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** The Wayshrine Stone item: the hover line names its Wayshrine ("From the Forest Ring"). */
public final class WayshrineStoneItem extends BlockItem {
    public WayshrineStoneItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** The stone of a Wayshrine of {@code type} at {@code pos}: kind in the block state, origin in the component. */
    public static ItemStack of(String type, BlockPos pos) {
        ItemStack s = new ItemStack(WayshrineRegistry.STONE_ITEM.get());
        s.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(WayshrineStoneBlock.KIND, WayshrineKind.byType(type)));
        s.set(WayshrineRegistry.ORIGIN.get(), new WayshrineOrigin(type, pos.immutable()));
        return s;
    }

    /** The type the stone came from: the component, else the block state's kind, else the Forest Ring. */
    public static String typeOf(ItemStack stack) {
        WayshrineOrigin o = stack.get(WayshrineRegistry.ORIGIN.get());
        if (o != null) return o.type();
        BlockItemStateProperties st = stack.get(DataComponents.BLOCK_STATE);
        WayshrineKind k = st == null ? null : st.get(WayshrineStoneBlock.KIND);
        return (k == null ? WayshrineKind.FOREST_RING : k).id();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.firmages.wayshrine_stone.from", WayshrineService.typeName(typeOf(stack)))
            .withStyle(ChatFormatting.GRAY));
    }
}
