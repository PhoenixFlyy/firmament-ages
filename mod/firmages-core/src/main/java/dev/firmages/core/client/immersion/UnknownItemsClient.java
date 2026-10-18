package dev.firmages.core.client.immersion;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.immersion.UnknownItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/**
 * Client half of {@link UnknownItems} (SPEC §21.15): the name tag of an item entity or an item frame holding an item of
 * a locked Age and the {@code [item]} text of a chat line read "Something Unknown"; the tooltip goes through
 * {@code ImmersionClient.tooltip}, Jade through {@code FirmagesJadePlugin}.
 */
@EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
public final class UnknownItemsClient {
    private UnknownItemsClient() {}

    /** The stack is named "Something Unknown" for the local player. */
    public static boolean masked(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        LocalPlayer p = Minecraft.getInstance().player;
        return UnknownItems.masked(ClientAgeView.current(), stack.getItem(), p != null && p.isCreative());
    }

    public static MutableComponent name() {
        return Component.translatable(UnknownItems.NAME_KEY).withStyle(ChatFormatting.GRAY);
    }

    /** The item an item entity or item frame shows, else empty. */
    public static ItemStack shown(Entity e) {
        if (e instanceof ItemEntity item) return item.getItem();
        if (e instanceof ItemFrame frame) return frame.getItem();
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void nameTag(RenderNameTagEvent event) {
        if (masked(shown(event.getEntity()))) event.setContent(name());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void chat(ClientChatReceivedEvent event) {
        if (Minecraft.getInstance().player == null) return;
        Component in = event.getMessage();
        Component out = mask(in);
        if (out != in) event.setMessage(out);
    }

    /** The component with every part that shows a locked item ({@code show_item} hover) renamed; the same object if none. */
    static Component mask(Component c) {
        HoverEvent hover = c.getStyle().getHoverEvent();
        if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_ITEM) {
            HoverEvent.ItemStackInfo info = hover.getValue(HoverEvent.Action.SHOW_ITEM);
            if (info != null && masked(info.getItemStack())) {
                return Component.translatable("chat.square_brackets", name()).setStyle(c.getStyle());
            }
        }
        boolean changed = false;
        MutableComponent copy = c.plainCopy().setStyle(c.getStyle());
        for (Component s : c.getSiblings()) {
            Component m = mask(s);
            changed |= m != s;
            copy.append(m);
        }
        return changed ? copy : c;
    }
}
