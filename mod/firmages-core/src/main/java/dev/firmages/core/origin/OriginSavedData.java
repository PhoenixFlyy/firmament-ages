package dev.firmages.core.origin;

import dev.firmages.core.ark.ArkState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * SavedData {@code firmages_origin} (overworld storage): which parts of The Origin's arena are built, whether the
 * final boss fell, the Gathering bookkeeping, and the Archipelago's progress per island ({@code ark.<age>}, SPEC §31.8).
 */
public final class OriginSavedData extends SavedData {
    private static final String NAME = "firmages_origin";

    private int arenaVersion;
    /** {@link OriginLayout} name, empty until {@link OriginService#ensureBuilt} decides it (round G3). */
    private String layout = "";
    private boolean gateBuilt;
    private boolean dhdCoreTaken;
    private boolean won;
    private int gatherings;
    private final ArkState ark = new ArkState(this::setDirty);

    public static OriginSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(OriginSavedData::new, OriginSavedData::load, null), NAME);
    }

    private static OriginSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        OriginSavedData d = new OriginSavedData();
        d.arenaVersion = tag.getInt("arena_version");
        d.layout = tag.getString("layout");
        d.gateBuilt = tag.getBoolean("gate_built");
        d.dhdCoreTaken = tag.getBoolean("dhd_core_taken");
        d.won = tag.getBoolean("won");
        d.gatherings = tag.getInt("gatherings");
        d.ark.load(tag.getCompound("ark"));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("arena_version", arenaVersion);
        tag.putString("layout", layout);
        tag.putBoolean("gate_built", gateBuilt);
        tag.putBoolean("dhd_core_taken", dhdCoreTaken);
        tag.putBoolean("won", won);
        tag.putInt("gatherings", gatherings);
        tag.put("ark", ark.save());
        return tag;
    }

    public int arenaVersion() { return arenaVersion; }
    /** The arena layout of this world: an Origin built before round G3 (arena version above 0, no layout) is classic. */
    public OriginLayout layout() {
        return OriginLayout.decide(layout, arenaVersion);
    }
    /** Public for the GameTests of the old-world path. */
    public void setLayout(OriginLayout l) { layout = l.name().toLowerCase(java.util.Locale.ROOT); setDirty(); }
    public boolean gateBuilt() { return gateBuilt; }
    /** The return gate's DHD holds no energy core (taken when the gate was built, or once by the 1.0 migration). */
    public boolean dhdCoreTaken() { return dhdCoreTaken; }
    public boolean won() { return won; }
    public int gatherings() { return gatherings; }
    /** The Archipelago's progress (SPEC §31.8). */
    public ArkState ark() { return ark; }

    /** Set by {@link OriginService#ensureBuilt}; public for the GameTests of the arena migration (SPEC §31.10). */
    public void setArenaVersion(int v) { arenaVersion = v; setDirty(); }
    void setGateBuilt(boolean b) { gateBuilt = b; setDirty(); }
    /** Set with the gate and by {@link OriginService#migrateDhdCore}; public for the GameTest of that migration. */
    public void setDhdCoreTaken(boolean b) { dhdCoreTaken = b; setDirty(); }
    public void setWon(boolean b) { won = b; setDirty(); }

    void gathered() {
        gatherings++;
        setDirty();
    }

    /** {@code /firmages origin reset}: the fight can be won again, the arena stays. */
    void resetFight() {
        won = false;
        gatherings = 0;
        setDirty();
    }
}
