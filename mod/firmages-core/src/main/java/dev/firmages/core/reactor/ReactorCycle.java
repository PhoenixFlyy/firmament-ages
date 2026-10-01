package dev.firmages.core.reactor;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The reactor controller's state machine (SPEC §6.2, Doc 11 §6 decision 2): MONITOR, SHUTDOWN, COOLING, SWAP,
 * READY. The controller shuts the reactor down at the configured conversion, waits until DE reports COLD, swaps
 * chaos for fuel and then signals READY by redstone. It never charges or activates the reactor: starting stays a
 * player action (Felix, 2026-09-30). Pure Java; the block entity feeds it a {@link ReactorView} every few ticks.
 *
 * <pre>
 * MONITOR  --RUNNING and conversion >= shutdownAtConversion: shutdown()-->  SHUTDOWN
 * MONITOR  --COLD-->                                                        SWAP
 * SHUTDOWN --no longer WARMING_UP/RUNNING (STOPPING, COOLING, COLD)-->      COOLING   (else asks again)
 * COOLING  --COLD-->  SWAP;   --WARMING_UP/RUNNING (a player restarted it)--> MONITOR
 * SWAP     --swap(); fuel and chaos within the targets-->                   READY
 * READY    --WARMING_UP/RUNNING-->  MONITOR;  --COLD but targets missed-->  SWAP
 * any      --no reactor, INVALID, BEYOND_HOPE-->                            MONITOR
 * </pre>
 */
public final class ReactorCycle {
    public enum State { MONITOR, SHUTDOWN, COOLING, SWAP, READY }

    /**
     * @param shutdownAtConversion converted share (0..1) at which a running reactor is shut down
     * @param minFuel              fuel the swap tops up to; also met once nothing more fits
     * @param maxChaos             chaos that may stay in the reactor; a rest below 16 always stays (no fragment holds it)
     */
    public record Settings(double shutdownAtConversion, int minFuel, int maxChaos) {
        public static final Settings DEFAULT = new Settings(0.80, 10368, 0);
    }

    /** Moves chaos out of and fuel into a COLD reactor (the block entity's inventories). */
    @FunctionalInterface
    public interface Swapper {
        void swap(ReactorView reactor, Settings settings);
    }

    private State state = State.MONITOR;
    private String detail = "no reactor";

    public State state() {
        return state;
    }

    /** Short English reason for the current state, for the status command and the use message. */
    public String detail() {
        return detail;
    }

    public void restore(State s) {
        state = s;
        detail = "restored";
    }

    /** True while the controller emits its redstone signal. */
    public boolean ready() {
        return state == State.READY;
    }

    /** True when fuel and chaos meet the targets of {@code s} (the READY condition). */
    public static boolean targetsMet(ReactorView r, Settings s) {
        boolean fuelOk = r.fuel() >= s.minFuel() || ReactorFuel.full(r.fuel(), r.chaos());
        boolean chaosOk = r.chaos() - s.maxChaos() < ReactorFuel.NUGGET;
        return fuelOk && chaosOk;
    }

    /** One step. {@code reactor} is null when no core is bound to a touching stabilizer or injector. */
    public State tick(@Nullable ReactorView reactor, Settings s, Swapper swapper) {
        if (reactor == null) return set(State.MONITOR, "no reactor: place the controller against a stabilizer or injector of a formed reactor");
        ReactorView.Phase p = reactor.phase();
        if (p == ReactorView.Phase.INVALID || p == ReactorView.Phase.UNKNOWN) return set(State.MONITOR, "reactor structure invalid");
        if (p == ReactorView.Phase.BEYOND_HOPE) return set(State.MONITOR, "reactor beyond hope");
        double conversion = ReactorFuel.conversion(reactor.fuel(), reactor.chaos());
        switch (state) {
            case MONITOR -> {
                if (p == ReactorView.Phase.RUNNING && conversion >= s.shutdownAtConversion() - 1e-9) {
                    reactor.shutdown();
                    return set(State.SHUTDOWN, String.format(Locale.ROOT, "conversion %.0f%%: shutting down", conversion * 100));
                }
                if (p == ReactorView.Phase.COLD) return swap(reactor, s, swapper);
                return set(State.MONITOR, String.format(Locale.ROOT, "%s, conversion %.0f%% (shutdown at %.0f%%)",
                    p.name().toLowerCase(Locale.ROOT), conversion * 100, s.shutdownAtConversion() * 100));
            }
            case SHUTDOWN -> {
                if (p.online()) {
                    reactor.shutdown();
                    return set(State.SHUTDOWN, "waiting for the reactor to stop");
                }
                if (p == ReactorView.Phase.COLD) return swap(reactor, s, swapper);
                return set(State.COOLING, "cooling down");
            }
            case COOLING -> {
                if (p == ReactorView.Phase.COLD) return swap(reactor, s, swapper);
                if (p.online()) return set(State.MONITOR, "restarted while cooling");
                return set(State.COOLING, "cooling down");
            }
            case SWAP -> {
                if (p == ReactorView.Phase.COLD) return swap(reactor, s, swapper);
                if (p.online()) return set(State.MONITOR, "started before the swap finished");
                return set(State.SWAP, "waiting for COLD");
            }
            case READY -> {
                if (p.online()) return set(State.MONITOR, "started by a player");
                if (p == ReactorView.Phase.COLD && !targetsMet(reactor, s)) return swap(reactor, s, swapper);
                return set(State.READY, "ready: start the reactor by hand");
            }
        }
        return state;
    }

    private State swap(ReactorView r, Settings s, Swapper swapper) {
        if (!targetsMet(r, s)) swapper.swap(r, s);
        if (targetsMet(r, s)) return set(State.READY, "ready: start the reactor by hand");
        boolean chaosLeft = r.chaos() - s.maxChaos() >= ReactorFuel.NUGGET;
        return set(State.SWAP, chaosLeft ? String.format(Locale.ROOT, "removing chaos (%.0f left; output full?)", r.chaos())
            : String.format(Locale.ROOT, "waiting for fuel (%.0f of %d)", r.fuel(), s.minFuel()));
    }

    private State set(State next, String why) {
        state = next;
        detail = why;
        return next;
    }
}
