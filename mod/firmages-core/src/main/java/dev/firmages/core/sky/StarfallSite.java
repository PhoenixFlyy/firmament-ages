package dev.firmages.core.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

/**
 * One Starfall site of {@link SkySavedData} (SPEC §25.8). The centre is a column ({@code y} is resolved at carve time);
 * the core position is set once the carve starts. Mutable: the service changes it on the server thread only.
 */
public final class StarfallSite {
    public enum State { PENDING, CARVING, CARVED, BURNED }

    public enum Origin { LIVE, OLD }

    public final long id;
    public int x;
    public int z;
    public final String metal;
    public final String size;
    public final Origin origin;
    public final long seed;
    /** Game time of the impact (0 for old craters). */
    public long impactTime;
    /** Game time when the core cools: impact time + {@code starfall.coolTicks}; 0 = cold from the start. */
    public long coolsAt;
    /** Set when the carve starts; the plan is relative to {@code (x, centreY, z)}. */
    public int centreY = Integer.MIN_VALUE;
    public BlockPos core = null;
    public State state = State.PENDING;
    /** Index of the next plan op. */
    public int cursor;
    public int moves;
    public boolean veinAdded;
    /** Placed by an operator at an exact position: no base check, no moves. */
    public boolean forced;
    /** The impact effects ran (live sites only). */
    public boolean impacted;

    public StarfallSite(long id, int x, int z, String metal, String size, Origin origin, long seed) {
        this.id = id;
        this.x = x;
        this.z = z;
        this.metal = metal;
        this.size = size;
        this.origin = origin;
        this.seed = seed;
    }

    public ChunkPos chunk() {
        return new ChunkPos(x >> 4, z >> 4);
    }

    public boolean open() {
        return state == State.PENDING || state == State.CARVING;
    }

    /** The chunks of the box (centre ± {@code reach}), as packed longs. */
    public long[] chunks(int reach) {
        int x0 = (x - reach) >> 4;
        int x1 = (x + reach) >> 4;
        int z0 = (z - reach) >> 4;
        int z1 = (z + reach) >> 4;
        long[] out = new long[(x1 - x0 + 1) * (z1 - z0 + 1)];
        int i = 0;
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) out[i++] = ChunkPos.asLong(cx, cz);
        return out;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("id", id);
        t.putInt("x", x);
        t.putInt("z", z);
        t.putString("metal", metal);
        t.putString("size", size);
        t.putString("origin", origin.name());
        t.putLong("seed", seed);
        t.putLong("impact", impactTime);
        t.putLong("coolsAt", coolsAt);
        t.putInt("centreY", centreY);
        if (core != null) t.putLong("core", core.asLong());
        t.putString("state", state.name());
        t.putInt("cursor", cursor);
        t.putInt("moves", moves);
        t.putBoolean("vein", veinAdded);
        t.putBoolean("forced", forced);
        t.putBoolean("impacted", impacted);
        return t;
    }

    static StarfallSite load(CompoundTag t) {
        StarfallSite s = new StarfallSite(t.getLong("id"), t.getInt("x"), t.getInt("z"), t.getString("metal"), t.getString("size"),
            enumOr(Origin.class, t.getString("origin"), Origin.LIVE), t.getLong("seed"));
        s.impactTime = t.getLong("impact");
        s.coolsAt = t.getLong("coolsAt");
        s.centreY = t.contains("centreY") ? t.getInt("centreY") : Integer.MIN_VALUE;
        s.core = t.contains("core") ? BlockPos.of(t.getLong("core")) : null;
        s.state = enumOr(State.class, t.getString("state"), State.PENDING);
        s.cursor = t.getInt("cursor");
        s.moves = t.getInt("moves");
        s.veinAdded = t.getBoolean("vein");
        s.forced = t.getBoolean("forced");
        s.impacted = t.getBoolean("impacted");
        return s;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return "#" + id + " " + state + " " + metal + "/" + size + " " + origin + " at " + x + "," + z;
    }
}
