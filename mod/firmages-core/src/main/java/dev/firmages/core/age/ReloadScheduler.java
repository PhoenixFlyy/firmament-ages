package dev.firmages.core.age;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Coalescing datapack-reload scheduler (SPEC §3.3). Pure Java: the clock, the delays and the reload
 * action are injected, so the timing rules are unit-tested with a fake clock.
 * <ul>
 *   <li>{@link #request} schedules a reload {@code delay} ticks later; further requests extend the due tick,
 *       but never beyond {@code coalesce} ticks after the first pending request.</li>
 *   <li>A request while a reload runs queues exactly one follow-up.</li>
 * </ul>
 * All methods are called on the server thread.
 */
public final class ReloadScheduler {

    /** Starts one reload; the future completes when the reload has finished (or failed). */
    public interface Reloader {
        CompletableFuture<?> start(String reason);
    }

    /** Called after each reload, successful or not. */
    public interface FinishListener {
        void onFinish(String reason, long durationMillis, Throwable error);
    }

    public enum Phase { IDLE, PENDING, RUNNING }

    private final LongSupplier clock;
    private final IntSupplier delayTicks;
    private final IntSupplier coalesceTicks;
    private final LongSupplier nanoTime;
    private final Reloader reloader;
    private final FinishListener listener;

    private long dueTick = -1;
    private long firstRequestTick = -1;
    private boolean running;
    private boolean followUp;
    private final Set<String> pendingReasons = new LinkedHashSet<>();
    private final Set<String> followUpReasons = new LinkedHashSet<>();
    private int reloadsStarted;
    private long lastDurationMillis = -1;

    public ReloadScheduler(LongSupplier clock, IntSupplier delayTicks, IntSupplier coalesceTicks, LongSupplier nanoTime,
                           Reloader reloader, FinishListener listener) {
        this.clock = clock;
        this.delayTicks = delayTicks;
        this.coalesceTicks = coalesceTicks;
        this.nanoTime = nanoTime;
        this.reloader = reloader;
        this.listener = listener;
    }

    /** Requests a reload after the configured delay, coalescing with a pending one. */
    public void request(String reason) {
        if (running) {
            followUp = true;
            followUpReasons.add(reason);
            return;
        }
        long now = clock.getAsLong();
        long delay = Math.max(0, delayTicks.getAsInt());
        pendingReasons.add(reason);
        if (dueTick < 0) {
            firstRequestTick = now;
            dueTick = now + delay;
        } else {
            long cap = firstRequestTick + Math.max(delay, coalesceTicks.getAsInt());
            dueTick = Math.max(dueTick, Math.min(now + delay, cap));
        }
    }

    /** Requests a reload on the next {@link #tick} (operator {@code /firmages reload}, boot reconcile). */
    public void requestNow(String reason) {
        if (running) {
            followUp = true;
            followUpReasons.add(reason);
            return;
        }
        long now = clock.getAsLong();
        pendingReasons.add(reason);
        if (dueTick < 0) firstRequestTick = now;
        dueTick = now;
    }

    /** Runs the due reload, if any. */
    public void tick() {
        if (running || dueTick < 0 || clock.getAsLong() < dueTick) return;
        String reason = String.join(", ", pendingReasons);
        pendingReasons.clear();
        dueTick = -1;
        firstRequestTick = -1;
        running = true;
        reloadsStarted++;
        long t0 = nanoTime.getAsLong();
        CompletableFuture<?> future;
        try {
            future = reloader.start(reason);
        } catch (Throwable t) {
            finish(reason, t0, t);
            return;
        }
        future.whenComplete((r, err) -> finish(reason, t0, err));
    }

    private void finish(String reason, long t0, Throwable err) {
        running = false;
        lastDurationMillis = (nanoTime.getAsLong() - t0) / 1_000_000L;
        listener.onFinish(reason, lastDurationMillis, err);
        if (followUp) {
            followUp = false;
            String next = "follow-up: " + String.join(", ", followUpReasons);
            followUpReasons.clear();
            request(next);
        }
    }

    public Phase phase() {
        if (running) return Phase.RUNNING;
        return dueTick >= 0 ? Phase.PENDING : Phase.IDLE;
    }

    /** Ticks until the pending reload runs, or -1 when none is pending. */
    public long ticksUntilDue() {
        return dueTick < 0 ? -1 : Math.max(0, dueTick - clock.getAsLong());
    }

    public boolean followUpQueued() {
        return followUp;
    }

    public int reloadsStarted() {
        return reloadsStarted;
    }

    public long lastDurationMillis() {
        return lastDurationMillis;
    }
}
