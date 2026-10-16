package dev.firmages.core.compat.ae2;

import appeng.items.tools.powered.WirelessTerminalItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The wireless crafting terminal as a portable crafting grid (AE2 package, review 0.11.0; Felix: the terminal may come
 * earlier with a very expensive recipe). It is made from the Industrial Age on, but AE2 opens it only when it is linked
 * to an ME network through a wireless access point, which is Information Age. Right-click with a terminal that is not
 * linked opens a 3 by 3 crafting grid instead of AE2's "not linked" message; a linked terminal opens AE2's terminal as
 * before (out of range included). Only the right-click from the hand: ae2wtlib's hotkey keeps AE2's behaviour. With
 * ae2wtlib 19.5.1 the item under {@code ae2:wireless_crafting_terminal} is ae2wtlib's {@code ItemWCT} (an AE2
 * {@code WirelessTerminalItem}, not a {@code WirelessCraftingTerminalItem}) [verified, javap and a copy boot], so the
 * terminal is found by its id.
 * The grid closes like a crafting table, its items go back to the player. Loaded only when AE2 is present
 * ({@link #MOD_ID}).
 */
public final class PortableCrafting {
    public static final String MOD_ID = "ae2";
    public static final ResourceLocation TERMINAL = ResourceLocation.fromNamespaceAndPath("ae2", "wireless_crafting_terminal");

    private PortableCrafting() {}

    public static void register(IEventBus bus) {
        bus.addListener(PortableCrafting::onRightClickItem);
    }

    /** True for a wireless crafting terminal that is not linked to any ME network. */
    public static boolean unlinkedTerminal(ItemStack stack) {
        return stack.getItem() instanceof WirelessTerminalItem t && TERMINAL.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
            && t.getLinkedPosition(stack) == null;
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!unlinkedTerminal(stack)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (event.getEntity() instanceof ServerPlayer player) open(player, stack);
    }

    /** Opens the portable grid for {@code player}, titled with the terminal's name. */
    public static void open(ServerPlayer player, ItemStack terminal) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new Grid(id, inv, ContainerLevelAccess.create(p.level(), p.blockPosition())),
            terminal.getHoverName()));
    }

    /**
     * Vanilla's crafting menu with a real level access (the result slot and the item return on close need one) that stays
     * valid away from a crafting table. The client opens it as the vanilla crafting screen ({@code MenuType.CRAFTING}).
     */
    public static final class Grid extends CraftingMenu {
        Grid(int id, Inventory inventory, ContainerLevelAccess access) {
            super(id, inventory, access);
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isAlive();
        }
    }
}
