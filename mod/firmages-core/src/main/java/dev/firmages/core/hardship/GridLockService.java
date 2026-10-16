package dev.firmages.core.hardship;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.immersion.AgeStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Dawn grid lock of {@link GridLock} (SPEC §19.3), on both sides:
 * <ul>
 * <li>The grid menus (player inventory, Curios screen) use {@link GridSlots}: while {@link #locked} their grid and
 * result slots are inactive, take nothing and give nothing.</li>
 * <li>Their {@code slotsChanged} asks {@link #refuseGridChange} first: while locked the result stays empty and
 * anything that still reached the grid (another mod, a modified client) goes back to the player, so neither the
 * vanilla recipe lookup nor ProgressiveStages or Polymorph run.</li>
 * <li>The recipe book's placement is refused for a locked grid ({@code mixin/RecipeBookMenuMixin}).</li>
 * <li>When an Age change lifts the lock, every player online reads "the crafting grid is open".</li>
 * </ul>
 * The server decides from the global Age state, the client from {@code firmages:age_status} ({@code gridLock}; false
 * before the first status, so the server alone decides until then). Creative and spectator players and fake players
 * (machines) are exempt. Ground crafting, the Dawn replacement, is {@link GroundCraft}.
 */
public final class GridLockService {
    private static final Map<UUID, Long> LAST_HINT = new ConcurrentHashMap<>();
    /** The lock after the last Age change (null before the server started). */
    private static volatile Boolean lastActive;

    private GridLockService() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(EventPriority.LOW, GridLockService::onServerStarted); // after AgeService loaded the state
        gameBus.addListener(GridLockService::onServerStopped);
        gameBus.addListener(GridLockService::onLoggedOut);
        GroundCraft.register(gameBus);
    }

    /** True while the world's Age is below {@code hardship.gridLockUntilAge}. */
    public static boolean active(MinecraftServer s) {
        return GridLock.active(AgeStatus.current(AgeService.state(s).snapshot()), ServerConfig.gridLockUntil());
    }

    /** The lock for {@code player} on its side: the server decides from its Age state, the client from the synced status. */
    public static boolean locked(Player player) {
        if (player == null) return false;
        boolean exempt = player.isCreative() || player.isSpectator() || player instanceof FakePlayer;
        boolean active;
        if (player.level().isClientSide()) {
            active = FMLEnvironment.dist.isClient() && dev.firmages.core.client.immersion.ClientAgeStatus.gridLock();
        } else {
            MinecraftServer s = player.level().getServer();
            active = s != null && active(s);
        }
        return GridLock.locks(active, exempt);
    }

    /**
     * The head of a grid menu's {@code slotsChanged}. While the grid is locked for {@code owner} the result is
     * cleared and, on the server, every item in the grid goes back to the owner's inventory (or drops at their feet
     * when it is full). @return true if locked: the menu must skip its own recipe lookup.
     */
    public static boolean refuseGridChange(Player owner, Container grid, Container result) {
        if (!locked(owner)) return false;
        result.clearContent();
        if (!owner.level().isClientSide()) {
            int returned = 0;
            for (int i = 0; i < grid.getContainerSize(); i++) {
                ItemStack stack = grid.removeItemNoUpdate(i);
                if (stack.isEmpty()) continue;
                owner.getInventory().placeItemBackInInventory(stack);
                returned++;
            }
            if (returned > 0) {
                FirmagesCore.LOGGER.debug("Locked crafting grid of {}: {} stacks returned (hardship.gridLockUntilAge)", owner.getGameProfile().getName(), returned);
            }
        }
        return true;
    }

    /** At most one ground-craft hint per player per {@link GridLock#HINT_INTERVAL_MS}; true (and recorded) if one may go out now. */
    static boolean takeHint(Player player) {
        long now = Util.getMillis();
        if (!GridLock.hintDue(LAST_HINT.get(player.getUUID()), now)) return false;
        LAST_HINT.put(player.getUUID(), now);
        return true;
    }

    /**
     * Called by {@code AgeService} after every change of the global Age state: when the change lifted the lock,
     * every player online reads one line naming their inventory key.
     */
    public static void onAgeChanged(MinecraftServer s) {
        boolean now = active(s);
        Boolean was = lastActive;
        lastActive = now;
        if (!Boolean.TRUE.equals(was) || now || s.getPlayerList() == null) return;
        Component line = Component.translatable("firmages.gridlock.open", Component.keybind("key.inventory")).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) p.sendSystemMessage(line);
        FirmagesCore.LOGGER.info("The crafting grid is open (hardship.gridLockUntilAge reached)");
    }

    static void onServerStarted(ServerStartedEvent event) {
        lastActive = active(event.getServer());
    }

    static void onServerStopped(ServerStoppedEvent event) {
        lastActive = null;
        LAST_HINT.clear();
    }

    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_HINT.remove(event.getEntity().getUUID());
    }
}
