package dev.firmages.core.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/**
 * The four fight altars of the grand arena as a state machine (round G3, SPEC §39.1), pure Java for the unit tests.
 * Each altar is idle, channelled by one player, or active. A channel completes after {@link #CHANNEL_TICKS}; a player
 * channels one altar at a time; an active altar cannot be channelled. The boss's strike turns one active altar back to
 * idle. {@link ArenaAltars} keeps the world's blocks in step and checks that a channeller stays at his altar.
 */
public final class AltarCircuit {
    public static final int ALTARS = 4;
    /** A channel takes 3 s. */
    public static final int CHANNEL_TICKS = 60;
    /** While the shield stands, the boss strikes one active altar every 20 s. */
    public static final int STRIKE_TICKS = 400;

    private final boolean[] active = new boolean[ALTARS];
    private final UUID[] channeler = new UUID[ALTARS];
    private final long[] since = new long[ALTARS];

    public boolean active(int i) {
        return active[i];
    }

    public int activeCount() {
        int n = 0;
        for (boolean a : active) if (a) n++;
        return n;
    }

    public boolean allActive() {
        return activeCount() == ALTARS;
    }

    public UUID channeler(int i) {
        return channeler[i];
    }

    public boolean anyChannel() {
        for (UUID u : channeler) if (u != null) return true;
        return false;
    }

    /**
     * {@code who} starts (or keeps) channelling altar {@code i} at tick {@code now}; any other channel of his ends.
     * @return false when the altar is active or another player channels it
     */
    public boolean start(int i, UUID who, long now) {
        if (active[i]) return false;
        if (channeler[i] != null && !channeler[i].equals(who)) return false;
        for (int j = 0; j < ALTARS; j++) if (j != i && who.equals(channeler[j])) channeler[j] = null;
        if (channeler[i] == null) {
            channeler[i] = who;
            since[i] = now;
        }
        return true;
    }

    public void cancel(int i) {
        channeler[i] = null;
    }

    /** Share of the channel of altar {@code i} done at {@code now}, 0 without a channel. */
    public float progress(int i, long now) {
        if (channeler[i] == null) return 0;
        return Math.min(1F, (now - since[i]) / (float) CHANNEL_TICKS);
    }

    /** Completes every channel that has run {@link #CHANNEL_TICKS}. @return the altars that became active now */
    public List<Integer> tick(long now) {
        List<Integer> done = new ArrayList<>();
        for (int i = 0; i < ALTARS; i++) {
            if (channeler[i] != null && now - since[i] >= CHANNEL_TICKS) {
                set(i, true);
                done.add(i);
            }
        }
        return done;
    }

    /** Sets altar {@code i} active or idle; ends its channel. */
    public void set(int i, boolean on) {
        active[i] = on;
        channeler[i] = null;
    }

    public void resetAll() {
        for (int i = 0; i < ALTARS; i++) set(i, false);
    }

    public void completeAll() {
        for (int i = 0; i < ALTARS; i++) set(i, true);
    }

    /**
     * The boss strikes: one active altar, chosen by {@code pick} (given the number of active altars, returns an index
     * below it), turns idle. @return the altar struck, -1 when none is active
     */
    public int strike(IntUnaryOperator pick) {
        List<Integer> on = new ArrayList<>();
        for (int i = 0; i < ALTARS; i++) if (active[i]) on.add(i);
        if (on.isEmpty()) return -1;
        int i = on.get(Math.floorMod(pick.applyAsInt(on.size()), on.size()));
        set(i, false);
        return i;
    }
}
