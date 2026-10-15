package dev.firmages.core.wayshrine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SavedData {@code firmages_wayshrines} in the overworld storage (SPEC-himmel §28.6): one record per known core (it
 * registers itself when its chunk loads), whether the tutorial Wayshrine stands, and the pointer cache (structure
 * positions the pointer found, so the next player is pointed there without a search).
 */
public final class WayshrineSavedData extends SavedData {
    public static final String DATA_NAME = "firmages_wayshrines";

    /**
     * One Wayshrine.
     *
     * @param litAge     the Age it was lit in ("" before; information only)
     * @param litAt      the overworld game time of its lighting (0 before)
     * @param stoneGiven its Wayshrine Stone dropped (once per Wayshrine)
     */
    public record Record(GlobalPos where, String type, WayshrinePhase phase, boolean intact, String litAge, long litAt, boolean stoneGiven) {
        public boolean lit() {
            return phase == WayshrinePhase.LIT;
        }

        /** Lit and its form standing: it carries the blessings. */
        public boolean blessing() {
            return lit() && intact;
        }

        Record withState(WayshrinePhase p, boolean i) {
            return new Record(where, type, p, i, litAge, litAt, stoneGiven);
        }
    }

    /** A structure the pointer found (or the tutorial site), in the overworld. */
    public record Found(BlockPos pos, String type) {}

    private final Map<GlobalPos, Record> records = new LinkedHashMap<>();
    private final List<Found> found = new ArrayList<>();
    private boolean tutorialPlaced;
    @Nullable private BlockPos tutorialPos;

    public static WayshrineSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(WayshrineSavedData::new, WayshrineSavedData::load, null), DATA_NAME);
    }

    public Collection<Record> records() {
        return List.copyOf(records.values());
    }

    public Optional<Record> record(GlobalPos where) {
        return Optional.ofNullable(records.get(where));
    }

    public void put(Record r) {
        if (!r.equals(records.put(r.where(), r))) setDirty();
    }

    public Optional<Record> remove(GlobalPos where) {
        Record r = records.remove(where);
        if (r != null) setDirty();
        return Optional.ofNullable(r);
    }

    public List<Found> found() {
        return List.copyOf(found);
    }

    /** Adds a found structure position (once per position). */
    public void addFound(Found f) {
        if (found.stream().noneMatch(x -> x.pos().equals(f.pos()))) {
            found.add(f);
            setDirty();
        }
    }

    public boolean tutorialPlaced() {
        return tutorialPlaced;
    }

    public Optional<BlockPos> tutorialPos() {
        return Optional.ofNullable(tutorialPos);
    }

    public void setTutorial(@Nullable BlockPos pos) {
        tutorialPlaced = pos != null;
        tutorialPos = pos;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Record r : records.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("dimension", r.where().dimension().location().toString());
            t.putLong("pos", r.where().pos().asLong());
            t.putString("type", r.type());
            t.putString("phase", r.phase().getSerializedName());
            t.putBoolean("intact", r.intact());
            t.putString("litAge", r.litAge());
            t.putLong("litAt", r.litAt());
            t.putBoolean("stoneGiven", r.stoneGiven());
            list.add(t);
        }
        tag.put("records", list);
        ListTag fl = new ListTag();
        for (Found f : found) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", f.pos().asLong());
            t.putString("type", f.type());
            fl.add(t);
        }
        tag.put("found", fl);
        tag.putBoolean("tutorialPlaced", tutorialPlaced);
        if (tutorialPos != null) tag.putLong("tutorialPos", tutorialPos.asLong());
        return tag;
    }

    public static WayshrineSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WayshrineSavedData d = new WayshrineSavedData();
        ListTag list = tag.getList("records", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dimension"));
            if (dim == null) continue;
            GlobalPos where = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLong("pos")));
            d.records.put(where, new Record(where, t.getString("type"), WayshrinePhase.byId(t.getString("phase")), t.getBoolean("intact"),
                t.getString("litAge"), t.getLong("litAt"), t.getBoolean("stoneGiven")));
        }
        ListTag fl = tag.getList("found", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            d.found.add(new Found(BlockPos.of(fl.getCompound(i).getLong("pos")), fl.getCompound(i).getString("type")));
        }
        d.tutorialPlaced = tag.getBoolean("tutorialPlaced");
        d.tutorialPos = tag.contains("tutorialPos") ? BlockPos.of(tag.getLong("tutorialPos")) : null;
        return d;
    }

    /** NBT round trip (GameTests). */
    public static WayshrineSavedData reload(WayshrineSavedData d, HolderLookup.Provider registries) {
        return load(d.save(new CompoundTag(), registries), registries);
    }
}
