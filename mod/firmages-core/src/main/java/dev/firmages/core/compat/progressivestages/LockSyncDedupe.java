package dev.firmages.core.compat.progressivestages;

import net.minecraft.server.level.ServerPlayer;

/**
 * Skips the duplicate ProgressiveStages lock sync of one player sync (state for {@code mixin.ps.SyncPlayerMixin},
 * {@code mixin.ps.PlayerJoinMixin} and {@code mixin.ps.SendStageSyncMixin}).
 *
 * <p>ProgressiveStages 3.0.5 [verified, javap] sends the lock sync twice in two places, with nothing changed in
 * between, so the second one is the same packet again:
 * <ul>
 *   <li>{@code ProgressiveStagesAPI.syncPlayer(player)} calls {@code NetworkHandler.sendLockSync(player)} and then
 *       {@code NetworkHandler.sendStageSync(player, stages)}, which calls {@code sendLockSync(player)} again before
 *       the stage payload. PS syncs every player this way after every datapack reload.</li>
 *   <li>{@code ServerEventHandler.onPlayerJoin} ({@code EntityJoinLevelEvent}: login, every dimension change and
 *       respawn) calls {@code sendLockSync(player)}, {@code StageManager.getStages(player)} and then
 *       {@code sendStageSync(player, stages)} the same way.</li>
 * </ul>
 * The lock sync is the expensive part ({@code CompiledRuleEngine.resolveViewerItemLocks} walks every item, about
 * 0.27 s per player and call; {@code dev/poc-results.md}, "Reload performance").
 *
 * <p>The second call is skipped only when the first one was sent for the same player inside the same
 * {@code syncPlayer} or {@code onPlayerJoin} call on the same thread, and at most once per first call. If a mixin
 * does not apply (a changed PS), nothing is skipped. The packet order stays.
 */
public final class LockSyncDedupe {
    /** Where the first lock sync was sent. */
    public enum Site { RELOAD_SYNC, JOIN }

    private static final ThreadLocal<ServerPlayer> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Site> SENT = new ThreadLocal<>();
    private static long skippedSync;
    private static long skippedJoin;

    private LockSyncDedupe() {}

    public static void begin(ServerPlayer player) {
        ACTIVE.set(player);
        SENT.remove();
    }

    public static void end() {
        ACTIVE.remove();
        SENT.remove();
    }

    public static void markSent(ServerPlayer player, Site site) {
        if (player == ACTIVE.get()) SENT.set(site);
    }

    /** True when this call is the duplicate; counts it and clears the state, so one first call skips at most one. */
    public static boolean skip(ServerPlayer player) {
        Site site = SENT.get();
        if (site == null || player != ACTIVE.get()) return false;
        if (site == Site.JOIN) skippedJoin++;
        else skippedSync++;
        end();
        return true;
    }

    /** Duplicate lock syncs of {@code syncPlayer} (the reload path) skipped since the server started. */
    public static long skipped() {
        return skippedSync;
    }

    /** Duplicate lock syncs of {@code onPlayerJoin} (login, dimension change, respawn) skipped since the server started. */
    public static long skippedOnJoin() {
        return skippedJoin;
    }
}
