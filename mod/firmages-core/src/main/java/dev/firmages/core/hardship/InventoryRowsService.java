package dev.firmages.core.hardship;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.immersion.AgeStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The inventory rows of {@link InventoryRows} (SPEC §19.4), on both sides. A locked slot is index 9 and up of the
 * player's own {@code Inventory} below {@link InventoryRows#firstOpen}; it takes nothing:
 * <ul>
 * <li>{@code mixin/InventoryMixin}: {@code getFreeSlot} and {@code getSlotWithRemainingSpace} skip locked slots, so
 * {@code Inventory.add} (pickups, {@code /give}, quest rewards, {@code placeItemBackInInventory}) fills only the open
 * ones and leaves the rest to the caller (it drops it or leaves it on the ground); {@code canPlaceItem} is false, so
 * NeoForge's {@code InvWrapper} (the player item-handler capability, {@code ItemHandlerHelper.giveItemToPlayer})
 * inserts nothing there.</li>
 * <li>{@code mixin/SlotMixin}: a menu slot of a locked index is inactive (not drawn, not hovered, no tooltip) and its
 * {@code mayPlace} is false.</li>
 * <li>{@code mixin/AbstractContainerMenuMixin}: every click whose slot is locked is dropped ({@code clicked}), and
 * {@code moveItemStackTo} (every shift-click into the player's slots) skips locked slots, also for mod slot classes
 * that override {@code mayPlace}.</li>
 * <li>Items that are already in a locked slot (worlds from before, an Age taken back, a creative player switching to
 * survival, a lowered config) move to open slots, the rest drops at the player's feet: on login, respawn, after every
 * change of the global Age state and, as a safety net, once a second.</li>
 * </ul>
 * The server decides from the global Age state, the client from {@code firmages:age_status} ({@code inventoryRows};
 * every row before the first status). Creative and spectator players and fake players (machines) are exempt. No hint:
 * the rows are simply grey (Felix 2026-10-17, SPEC §18).
 */
public final class InventoryRowsService {
    /** The safety-net sweep runs every this many player ticks. */
    static final int SWEEP_TICKS = 20;

    private InventoryRowsService() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(InventoryRowsService::onLoggedIn);
        gameBus.addListener(InventoryRowsService::onRespawn);
        gameBus.addListener(InventoryRowsService::onPlayerTick);
    }

    /** The open rows of the world's Age ({@code hardship.inventoryRows}). */
    public static int worldRows(MinecraftServer s) {
        return InventoryRows.rows(AgeStatus.current(AgeService.state(s).snapshot()), ServerConfig::inventoryRows);
    }

    /** The open rows for {@code player} on its side: the server decides from its Age state, the client from the synced status. */
    public static int openRows(Player player) {
        if (player == null) return InventoryRows.ALL;
        boolean exempt = player.isCreative() || player.isSpectator() || player instanceof FakePlayer;
        if (exempt) return InventoryRows.ALL;
        if (player.level().isClientSide()) {
            return FMLEnvironment.dist.isClient() ? dev.firmages.core.client.immersion.ClientAgeStatus.inventoryRows() : InventoryRows.ALL;
        }
        MinecraftServer s = player.level().getServer();
        return s == null ? InventoryRows.ALL : worldRows(s);
    }

    /** True if index {@code index} of {@code player}'s inventory is locked. */
    public static boolean locked(Player player, int index) {
        return index >= InventoryRows.MAIN_START && index < InventoryRows.firstOpen(0) && InventoryRows.locked(index, openRows(player));
    }

    /** True if {@code slot} shows a locked slot of a player inventory. */
    public static boolean lockedSlot(Slot slot) {
        return slot.container instanceof Inventory inv && locked(inv.player, slot.getContainerSlot());
    }

    /**
     * Moves every item out of the locked rows of {@code player}: into open slots ({@code placeItemBackInInventory},
     * which now skips locked ones), whatever does not fit drops at the player's feet. @return the stacks moved
     */
    public static int sweep(ServerPlayer player) {
        int open = openRows(player);
        if (open >= InventoryRows.ALL) return 0;
        Inventory inv = player.getInventory();
        int moved = 0;
        for (int i = InventoryRows.MAIN_START; i < InventoryRows.firstOpen(open); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            inv.setItem(i, ItemStack.EMPTY);
            inv.placeItemBackInInventory(stack);
            moved++;
        }
        if (moved > 0) {
            FirmagesCore.LOGGER.debug("{}: {} stacks moved out of locked inventory rows (hardship.inventoryRows)", player.getGameProfile().getName(), moved);
        }
        return moved;
    }

    /** After every change of the global Age state (AgeService): sweeps every online player. */
    public static void sweepAll(MinecraftServer s) {
        if (s.getPlayerList() == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) sweep(p);
    }

    static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sweep(p);
    }

    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sweep(p);
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % SWEEP_TICKS == 0) sweep(p);
    }
}
