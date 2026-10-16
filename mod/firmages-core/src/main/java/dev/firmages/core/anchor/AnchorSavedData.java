package dev.firmages.core.anchor;

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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * SavedData {@code firmages_anchors} in the overworld storage (SPEC-himmel §29.3): {@code dimension}, {@code pos} and
 * {@code tiers} (bit mask) of every Sky Anchor, so the blessing index knows the anchors of unloaded chunks and other
 * dimensions. Written on place, change and break; an anchor's block entity puts its record back when it loads, and a
 * record whose loaded chunk holds no anchor is dropped ({@link AnchorService#sources}).
 */
public final class AnchorSavedData extends SavedData {
    public static final String DATA_NAME = "firmages_anchors";

    private final Map<GlobalPos, Integer> anchors = new LinkedHashMap<>();

    public static AnchorSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(AnchorSavedData::new, AnchorSavedData::load, null), DATA_NAME);
    }

    /** Every anchor and its shards (a copy). */
    public Map<GlobalPos, Integer> anchors() {
        return Map.copyOf(anchors);
    }

    public Optional<Integer> tiers(GlobalPos where) {
        return Optional.ofNullable(anchors.get(where));
    }

    /** @return true when the record changed */
    public boolean put(GlobalPos where, int tiers) {
        Integer old = anchors.put(where, tiers);
        if (old != null && old == tiers) return false;
        setDirty();
        return true;
    }

    /** @return true when a record was removed */
    public boolean remove(GlobalPos where) {
        if (anchors.remove(where) == null) return false;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        anchors.forEach((where, tiers) -> {
            CompoundTag t = new CompoundTag();
            t.putString("dimension", where.dimension().location().toString());
            t.putLong("pos", where.pos().asLong());
            t.putInt("tiers", tiers);
            list.add(t);
        });
        tag.put("anchors", list);
        return tag;
    }

    public static AnchorSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AnchorSavedData d = new AnchorSavedData();
        ListTag list = tag.getList("anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dimension"));
            if (dim == null) continue;
            d.anchors.put(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLong("pos"))), t.getInt("tiers"));
        }
        return d;
    }

    /** NBT round trip (GameTests). */
    public static AnchorSavedData reload(AnchorSavedData d, HolderLookup.Provider registries) {
        return load(d.save(new CompoundTag(), registries), registries);
    }
}
