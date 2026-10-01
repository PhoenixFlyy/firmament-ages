package dev.firmages.core.shrine;

import com.enviouse.progressivestages.common.api.StageChangeEvent;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.ceremony.CeremonyService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus wiring of the shrine and the ceremony. */
public final class ShrineEvents {
    private ShrineEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(ShrineEvents::onReloadListeners);
        bus.addListener(ShrineEvents::onRightClickBlock);
        bus.addListener(ShrineEvents::onBreak);
        bus.addListener(ShrineEvents::onPlace);
        bus.addListener(ShrineEvents::onExplosion);
        bus.addListener(ShrineEvents::onLogin);
        bus.addListener(ShrineEvents::onStageChange);
        bus.addListener(Blessings::onSpawnCheck);
        bus.addListener(Blessings::onPickupXp);
        bus.addListener(Blessings::onLogout);
        bus.addListener(ShrineEvents::onServerTick);
        bus.addListener(ShrineEvents::onServerStarted);
        bus.addListener(ShrineEvents::onServerStopped);
    }

    private static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ShrineDataLoader());
    }

    /**
     * Sneak plus use with an empty main hand on the heart is a prayer, whatever the off hand holds (vanilla skips
     * the block use while sneaking with any item, and an off-hand torch would be placed). Every other use inside
     * the shrine may complete an {@code interact} rite (the bell).
     */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (state.is(ShrineRegistry.SHRINE_HEART.get()) && event.getEntity().isShiftKeyDown() && event.getEntity().getMainHandItem().isEmpty()) {
            if (event.getLevel() instanceof ServerLevel sl && event.getEntity() instanceof ServerPlayer sp) ShrineService.pray(sl, pos, sp);
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            return;
        }
        if (event.getLevel() instanceof ServerLevel sl && event.getEntity() instanceof ServerPlayer sp) ShrineService.onBlockUsed(sl, pos, state, sp);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel sl) ShrineService.onBlockChanged(sl, event.getPos());
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel sl) ShrineService.onBlockChanged(sl, event.getPos());
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        for (BlockPos p : event.getAffectedBlocks()) {
            ShrineService.onBlockChanged(sl, p);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) ShrineService.onLogin(sp);
    }

    private static void onStageChange(StageChangeEvent event) {
        if (event.wasRevoked() && event.getPlayer() != null) ShrineService.onRevoked(event.getPlayer().server, event.getStageId().getPath());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        CeremonyService.tick(event.getServer());
        Blessings.tick(event.getServer());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer s = event.getServer();
        ShrineSavedData sd = ShrineSavedData.get(s);
        sd.heart().ifPresent(gp -> ShrineState.set(gp.dimension(), gp.pos(), sd.intact(),
            ShrineRules.awakened(AgeService.state(s).snapshot().unlocked())));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ShrineState.clear();
        CeremonyService.onServerStopped();
        Blessings.clear();
    }
}
