package dev.firmages.core.origin;

import net.minecraft.core.BlockPos;

/**
 * Which arena a world's Origin has (round G3, SPEC §16.5). A world whose Origin was generated before round G3
 * keeps the {@link #CLASSIC} arena (radius 20, gate on a pedestal south of it, islands 45 to 75 blocks out); an
 * Origin generated since gets the {@link #GRAND} arena (radius 60, gate on the raised arrival platform, islands moved
 * out by 50 blocks). The choice is stored in {@link OriginSavedData} ({@code layout}) and never changes for a world.
 */
public enum OriginLayout {
    CLASSIC(20, 0, new BlockPos(0, OriginArena.FLOOR_Y + 1, 14), new BlockPos(-3, OriginArena.FLOOR_Y, 22)),
    GRAND(GrandArenaPlan.RADIUS, 50, new BlockPos(0, GrandArenaPlan.PLATFORM_Y + 1, 62),
        new BlockPos(-3, GrandArenaPlan.PLATFORM_Y, 68));

    public final int radius;
    /** Added to every island's distance from the centre. */
    public final int islandOffset;
    /** Where {@code /firmages origin tp} puts players. */
    public final BlockPos arrival;
    /** Origin of Stargate Journey's pedestal template (gate base at (3, 2, 9), DHD at (3, 1, 3) from here). */
    public final BlockPos gateOrigin;

    OriginLayout(int radius, int islandOffset, BlockPos arrival, BlockPos gateOrigin) {
        this.radius = radius;
        this.islandOffset = islandOffset;
        this.arrival = arrival;
        this.gateOrigin = gateOrigin;
    }

    public BlockPos gateBase() {
        return gateOrigin.offset(3, 2, 9);
    }

    public BlockPos dhd() {
        return gateOrigin.offset(3, 1, 3);
    }

    /** True when the arena has the four fight altars (SPEC §39.1). */
    public boolean hasAltars() {
        return this == GRAND;
    }

    /**
     * The layout of a world from what {@link OriginSavedData} holds: the stored name, else (a world from before round G3)
     * classic when its arena was already built, grand for an Origin not generated yet.
     */
    public static OriginLayout decide(String stored, int arenaVersion) {
        return GrandArenaPlan.isGrand(stored, arenaVersion) ? GRAND : CLASSIC;
    }

    public static OriginLayout byName(String s) {
        for (OriginLayout l : values()) if (l.name().equalsIgnoreCase(s)) return l;
        return GRAND;
    }
}
