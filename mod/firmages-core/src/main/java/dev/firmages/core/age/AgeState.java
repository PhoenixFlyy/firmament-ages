package dev.firmages.core.age;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumSet;

/**
 * SavedData {@code firmages_ages} in the overworld data storage (SPEC §2.3): the union of the Age stages of all
 * teams. With the pack's single team this is the team's set. {@code dawn} is always unlocked.
 */
public final class AgeState extends SavedData {
    public static final String DATA_NAME = "firmages_ages";

    private final AgeLedger ledger;
    /** False when the SavedData file did not exist yet (fresh world or first start with the mod). */
    private final boolean loadedFromDisk;

    private AgeState(AgeLedger ledger, boolean loadedFromDisk) {
        this.ledger = ledger;
        this.loadedFromDisk = loadedFromDisk;
    }

    public static AgeState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(() -> new AgeState(new AgeLedger(), false), AgeState::load, null), DATA_NAME);
    }

    private static AgeState load(CompoundTag tag, HolderLookup.Provider registries) {
        EnumSet<AgeId> unlocked = EnumSet.noneOf(AgeId.class);
        ListTag list = tag.getList("unlocked", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            AgeId.byId(list.getString(i)).ifPresent(unlocked::add);
        }
        return new AgeState(new AgeLedger(new AgeSnapshot(unlocked, tag.getLong("version"), tag.getLong("lastReloadGameTime"))), true);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        AgeSnapshot s = ledger.snapshot();
        ListTag list = new ListTag();
        s.unlockedIds().forEach(id -> list.add(StringTag.valueOf(id)));
        tag.put("unlocked", list);
        tag.putLong("version", s.version());
        tag.putLong("lastReloadGameTime", s.lastReloadGameTime());
        return tag;
    }

    public AgeLedger ledger() {
        return ledger;
    }

    public AgeSnapshot snapshot() {
        return ledger.snapshot();
    }

    public boolean loadedFromDisk() {
        return loadedFromDisk;
    }
}
