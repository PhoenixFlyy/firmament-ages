package dev.firmages.core.compat.progressivestages;

import net.minecraft.server.level.ServerPlayer;

/**
 * Skips the duplicate ProgressiveStages lock sync of one player sync (state for
 * {@code mixin.ps.SyncPlayerMixin} and {@code mixin.ps.SendStageSyncMixin}).
 *
 * <p>ProgressiveStages 3.0.5 [verified, javap] {@code ProgressiveStagesAPI.syncPlayer(player)} calls
 * {@code NetworkHandler.sendLockSync(player)} and then {@code NetworkHandler.sendStageSync(player, stages)}, which
 * calls {@code sendLockSync(player)} again before the stage payload. Nothing changes between the two calls, so the
 * second lock sync is the same packet again. PS syncs every player after every datapack reload, and the lock sync is
 * the expensive part ({@code CompiledRuleEngine.resolveViewerItemLocks} walks every item, about 0.27 s per player
 * and call; {@code dev/poc-results.md}, "Reload performance").
 *
 * <p>The second call is skipped only when the first one was sent for the same player inside the same
 * {@code syncPlayer} call on the same thread. If either mixin does not apply (a changed PS), nothing is skipped.
 */
public final class LockSyncDedupe {
    private static final ThreadLocal<ServerPlayer> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SENT = ThreadLocal.withInitial(() -> false);
    private static long skipped;

    private LockSyncDedupe() {}

    public static void begin(ServerPlayer player) {
        ACTIVE.set(player);
        SENT.set(false);
    }

    public static void end() {
        ACTIVE.remove();
        SENT.remove();
    }

    public static void markSent(ServerPlayer player) {
        if (player == ACTIVE.get()) SENT.set(true);
    }

    /** True when this call is the duplicate; counts it. */
    public static boolean skip(ServerPlayer player) {
        if (player != ACTIVE.get() || !SENT.get()) return false;
        skipped++;
        return true;
    }

    /** Duplicate lock syncs skipped since the server started (logged with every Age reload). */
    public static long skipped() {
        return skipped;
    }
}
