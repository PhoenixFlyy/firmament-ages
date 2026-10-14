package dev.firmages.core.hardship;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.immersion.AgeStatus;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hand-breaking rule of {@link ToolRule} (SPEC §19.2), on both sides:
 * <ul>
 * <li>{@code PlayerEvent.BreakSpeed} (LOWEST, client and server) sets the speed to 0, so the destroy progress stays 0:
 * no crack overlay, no break. The client knows the rule from {@code firmages:age_status} ({@code toolRule}), so it
 * predicts the same and nothing snaps back.</li>
 * <li>{@code BlockEvent.BreakEvent} is cancelled on the server as a safety net (modified clients, lag).</li>
 * <li>{@code PlayerInteractEvent.LeftClickBlock} (server, start of a break) sends an action-bar hint, at most once per
 * {@link ToolRule#HINT_INTERVAL_MS} per player.</li>
 * </ul>
 * "Correct tool" is the held item's {@code isCorrectToolForDrops} for the block (its tool component rules: a
 * TerraFirmaCraft stone axe or hammer has the wooden tier and is correct for logs). Creative and spectator players
 * and fake players (machines) are exempt. The server's Age is the highest unlocked Age of the global Age state.
 */
public final class ToolRuleService {
    public static final TagKey<Block> NEEDS_TOOL_EARLY =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "needs_tool_early"));
    private static final Map<UUID, Long> LAST_HINT = new ConcurrentHashMap<>();

    private ToolRuleService() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(EventPriority.LOWEST, ToolRuleService::onBreakSpeed);
        gameBus.addListener(EventPriority.HIGH, ToolRuleService::onBreak);
        gameBus.addListener(ToolRuleService::onLeftClick);
        gameBus.addListener(ToolRuleService::onLoggedOut);
    }

    /** True while the world's Age is below {@code hardship.toolRuleUntilAge}. */
    public static boolean active(MinecraftServer s) {
        return ToolRule.active(AgeStatus.current(AgeService.state(s).snapshot()), ServerConfig.toolRuleUntil());
    }

    /** The rule on the side of {@code player}: the server decides from its Age state, the client from the synced status. */
    static boolean active(Player player) {
        if (player.level().isClientSide()) {
            return FMLEnvironment.dist.isClient() && dev.firmages.core.client.immersion.ClientAgeStatus.toolRule();
        }
        MinecraftServer s = player.level().getServer();
        return s != null && active(s);
    }

    /** True if {@code player} may not break {@code state} with the held item while the rule is {@code ruleActive}. */
    public static boolean blocks(Player player, BlockState state, boolean ruleActive) {
        return ToolRule.blocks(ruleActive, state.is(NEEDS_TOOL_EARLY), exempt(player), player.getMainHandItem().isCorrectToolForDrops(state));
    }

    private static boolean exempt(Player player) {
        return player.isCreative() || player.isSpectator() || player instanceof FakePlayer;
    }

    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (event.getState().is(NEEDS_TOOL_EARLY) && blocks(player, event.getState(), active(player))) event.setNewSpeed(0f);
    }

    static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide() || event.isCanceled()) return;
        Player player = event.getPlayer();
        if (event.getState().is(NEEDS_TOOL_EARLY) && blocks(player, event.getState(), active(player))) {
            event.setCanceled(true);
            FirmagesCore.LOGGER.debug("Break of {} by {} refused (hardship.toolRuleUntilAge)", event.getState().getBlock(), player.getGameProfile().getName());
        }
    }

    static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        BlockState state = player.level().getBlockState(event.getPos());
        if (!state.is(NEEDS_TOOL_EARLY) || !blocks(player, state, active(player.server))) return;
        long now = Util.getMillis();
        if (!ToolRule.hintDue(LAST_HINT.get(player.getUUID()), now)) return;
        LAST_HINT.put(player.getUUID(), now);
        ToolRule.Hint hint = ToolRule.hintFor(state.is(BlockTags.MINEABLE_WITH_AXE), state.is(BlockTags.MINEABLE_WITH_PICKAXE));
        player.displayClientMessage(Component.translatable(hint.key()), true);
    }

    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_HINT.remove(event.getEntity().getUUID());
    }
}
