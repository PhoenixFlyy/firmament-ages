package dev.firmages.core.wayshrine;

import dev.firmages.core.shrine.BlessingIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus wiring of the Wayshrines (SPEC-himmel §28) and their blessing source provider. */
public final class WayshrineEvents {
    private WayshrineEvents() {}

    public static void register(IEventBus bus) {
        BlessingIndex.registerProvider(WayshrineService::sources);
        bus.addListener(WayshrineEvents::onReloadListeners);
        bus.addListener(EventPriority.HIGH, WayshrineEvents::onRightClickBlock);
        bus.addListener(WayshrineEvents::onLeftClickBlock);
        bus.addListener(WayshrineEvents::onBreak);
        bus.addListener(WayshrineEvents::onPlace);
        bus.addListener(WayshrineEvents::onExplosion);
        bus.addListener(WayshrineEvents::onServerStarted);
        bus.addListener(WayshrineEvents::onServerStopped);
        bus.addListener((ServerTickEvent.Post e) -> WayshrineTutorial.tick(e.getServer()));
    }

    private static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new WayshrineDataLoader());
    }

    /**
     * Sneak plus right-click with an empty main hand on a core is the prayer, whatever the off hand holds (vanilla skips
     * the block use while sneaking with any item, and an off-hand torch would be placed).
     */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown() || !event.getEntity().getMainHandItem().isEmpty()) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(WayshrineRegistry.CORE.get())) return;
        if (event.getLevel() instanceof ServerLevel sl && event.getEntity() instanceof ServerPlayer sp) WayshrineService.pray(sl, event.getPos(), sp);
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    /** Sneak and punch a core with an empty hand: the offering comes back. The punch never breaks the core. */
    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!(event.getLevel() instanceof ServerLevel sl) || !(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty()) return;
        BlockPos pos = event.getPos();
        if (!sl.getBlockState(pos).is(WayshrineRegistry.CORE.get())) return;
        if (WayshrineService.takeBack(sl, pos, sp)) event.setCanceled(true);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel sl) WayshrineService.onBlockChanged(sl, event.getPos());
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel sl) WayshrineService.onBlockChanged(sl, event.getPos());
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        event.getAffectedBlocks().removeIf(p -> sl.getBlockState(p).is(WayshrineRegistry.CORE.get())); // the core never goes
        for (BlockPos p : event.getAffectedBlocks()) WayshrineService.onBlockChanged(sl, p);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        WayshrineTutorial.onServerStarted(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        WayshrinePointer.clear();
        WayshrineTutorial.onServerStopped();
    }
}
