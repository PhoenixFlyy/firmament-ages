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
    private boolean gateBuilt;
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
        d.gateBuilt = tag.getBoolean("gate_built");
        d.won = tag.getBoolean("won");
        d.gatherings = tag.getInt("gatherings");
        d.ark.load(tag.getCompound("ark"));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("arena_version", arenaVersion);
        tag.putBoolean("gate_built", gateBuilt);
        tag.putBoolean("won", won);
        tag.putInt("gatherings", gatherings);
        tag.put("ark", ark.save());
        return tag;
    }

    public int arenaVersion() { return arenaVersion; }
    public boolean gateBuilt() { return gateBuilt; }
    public boolean won() { return won; }
    public int gatherings() { return gatherings; }
    /** The Archipelago's progress (SPEC §31.8). */
    public ArkState ark() { return ark; }

    /** Set by {@link OriginService#ensureBuilt}; public for the GameTests of the arena migration (SPEC §31.10). */
    public void setArenaVersion(int v) { arenaVersion = v; setDirty(); }
    void setGateBuilt(boolean b) { gateBuilt = b; setDirty(); }
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
