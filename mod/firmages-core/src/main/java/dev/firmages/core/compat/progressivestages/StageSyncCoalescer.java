package dev.firmages.core.compat.progressivestages;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One ProgressiveStages stage sync per player and tick ({@code sync.coalesce}, SPEC §3.5; state for
 * {@code mixin.ps.StageSyncCoalesceMixin}).
 *
 * <p>ProgressiveStages 3.0.6 [verified, javap] answers every stage grant or revoke with
 * {@code NetworkHandler.sendStageSync(member, getStages(member))} for every online team member
 * ({@code StageManager.syncToTeamMembers}, {@code syncToPlayer}, {@code syncAllPlayers},
 * {@code TeamStageSync.syncStageGrantToTeam}). {@code sendStageSync} sends the lock sync
 * ({@code sendLockSync}: walks every item, about 0.27 s per player in the pack), then the stage payload, the reveal
 * policy and the entity visibility. An Age grant changes several stages in one tick (the Age, the mob ladder, the
 * helper windows in {@code kubejs/server_scripts/stages/grants.js}), so the tick froze for about 0.3 s per change
 * and member (2.6 s with 2 players).
 *
 * <p>While coalescing is on, a {@code sendStageSync} on the server thread is not sent but recorded per player (the
 * stage set of the latest call wins) and sent once at the end of the tick ({@code ServerTickEvent.Post}) through the
 * original method, so each player gets one lock sync followed by one stage sync, the same order as before, with
 * the state of the end of the tick. Not deferred: calls off the server thread, calls inside a full sync
 * ({@code syncPlayer} after a reload, {@code onPlayerJoin}; {@link LockSyncDedupe#inScope}), which also drop a
 * pending sync of that player because they send the newer state anyway, and the sends of the flush itself.
 * A player who left before the flush gets nothing; one who respawned (new {@code ServerPlayer}, same UUID) gets it.
 */
public final class StageSyncCoalescer {
    private static final SyncBatch<UUID, Set<StageId>> BATCH = new SyncBatch<>();
    private static boolean flushing;
    private static Boolean override;
    private static long requestedTotal;
    private static long sentTotal;
    private static long lastFlushNanos;

    private StageSyncCoalescer() {}

    public static boolean enabled() {
        return override != null ? override : ServerConfig.syncCoalesce();
    }

    /** GameTests only: forces coalescing on or off; {@code null} goes back to {@code sync.coalesce}. */
    public static void overrideEnabled(Boolean value) {
        override = value;
    }

    /** From the mixin at the head of {@code sendStageSync}; @return true when the call is deferred (cancel it). */
    public static boolean defer(ServerPlayer player, Set<StageId> stages) {
        if (flushing || player == null || !player.server.isSameThread()) return false;
        if (LockSyncDedupe.inScope(player)) {
            BATCH.supersede(player.getUUID());
            return false;
        }
        if (!enabled()) return false;
        BATCH.add(player.getUUID(), stages);
        requestedTotal++;
        return true;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        flush(event.getServer());
    }

    /** Drops what a stopped server left pending (an integrated server can start again in the same JVM). */
    public static void onServerStopped(ServerStoppedEvent event) {
        BATCH.drain();
        flushing = false;
    }

    /** Sends the pending syncs now: one {@code sendStageSync} per player. */
    public static void flush(MinecraftServer server) {
        if ((BATCH.isEmpty() && BATCH.requests() == 0) || flushing) return;
        SyncBatch.Drained<UUID, Set<StageId>> d = BATCH.drain();
        long t0 = System.nanoTime();
        int sent = 0;
        int gone = 0;
        flushing = true;
        try {
            for (Map.Entry<UUID, Set<StageId>> e : d.syncs()) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null || p.hasDisconnected()) {
                    gone++;
                    continue;
                }
                try {
                    NetworkHandler.sendStageSync(p, e.getValue());
                    sent++;
                } catch (RuntimeException ex) {
                    FirmagesCore.LOGGER.error("Deferred ProgressiveStages stage sync to {} failed", p.getGameProfile().getName(), ex);
                }
            }
        } finally {
            flushing = false;
        }
        sentTotal += sent;
        lastFlushNanos = System.nanoTime() - t0;
        if (d.requests() > d.syncs().size()) {
            FirmagesCore.LOGGER.info("ProgressiveStages stage syncs coalesced: {} request(s) sent as {} sync(s) in {} ms{} (sync.coalesce)",
                d.requests(), sent, lastFlushNanos / 1_000_000, gone > 0 ? "; " + gone + " player(s) left before the end of the tick" : "");
        } else if (gone > 0) {
            FirmagesCore.LOGGER.debug("Deferred ProgressiveStages stage sync dropped: {} player(s) left before the end of the tick", gone);
        }
    }

    /** Deferred {@code sendStageSync} calls since the server started. */
    public static long requested() {
        return requestedTotal;
    }

    /** Syncs the flushes sent since the server started. */
    public static long sent() {
        return sentTotal;
    }

    /** Wall time of the last flush in ms. */
    public static double lastFlushMillis() {
        return lastFlushNanos / 1e6;
    }
}
