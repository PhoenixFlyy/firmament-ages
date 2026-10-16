package dev.firmages.core.isles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Overworld storage {@code firmages_isles} (SPEC §30.3, §30.5): the clusters placed by code (the fallback cluster of an
 * existing world and operator clusters), each with its resolved isles and which of them stand; the lode positions that
 * wait to re-form; and the last isle start the fallback check found near the heart.
 */
public final class IsleSavedData extends SavedData {
    public static final String DATA_NAME = "firmages_isles";

    public enum Origin { FALLBACK, MANUAL }

    /** A cluster placed by code. {@code placed[i]} is true once isle {@code i} stands (or was skipped as obstructed). */
    public static final class Cluster {
        public final long id;
        public final Origin origin;
        public final int x;
        public final int z;
        public final List<IsleSpec> isles;
        public final boolean[] placed;
        public final boolean[] skipped;

        Cluster(long id, Origin origin, int x, int z, List<IsleSpec> isles, boolean[] placed, boolean[] skipped) {
            this.id = id;
            this.origin = origin;
            this.x = x;
            this.z = z;
            this.isles = List.copyOf(isles);
            this.placed = placed;
            this.skipped = skipped;
        }

        public boolean done() {
            for (boolean b : placed) if (!b) return false;
            return true;
        }

        /** The core of the Wind Shrine this cluster carries, if any (on the main isle). */
        public Optional<BlockPos> shrineCore() {
            for (int i = 0; i < isles.size(); i++) {
                if (isles.get(i).shrine() && !skipped[i]) return Optional.of(IsleWriter.shrineCore(isles.get(i)));
            }
            return Optional.empty();
        }
    }

    private final Map<Long, Cluster> clusters = new LinkedHashMap<>();
    private final Set<GlobalPos> reform = new LinkedHashSet<>();
    private long nextId = 1;
    @Nullable
    private BlockPos knownStart;

    public static IsleSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(IsleSavedData::new, IsleSavedData::load, null), DATA_NAME);
    }

    public Collection<Cluster> clusters() {
        return Collections.unmodifiableCollection(clusters.values());
    }

    public Optional<Cluster> cluster(long id) {
        return Optional.ofNullable(clusters.get(id));
    }

    public Optional<Cluster> fallback() {
        return clusters.values().stream().filter(c -> c.origin == Origin.FALLBACK).findFirst();
    }

    public Cluster add(Origin origin, int x, int z, List<IsleSpec> isles) {
        if (origin == Origin.FALLBACK) clusters.values().removeIf(c -> c.origin == Origin.FALLBACK);
        Cluster c = new Cluster(nextId++, origin, x, z, isles, new boolean[isles.size()], new boolean[isles.size()]);
        clusters.put(c.id, c);
        setDirty();
        return c;
    }

    public void remove(long id) {
        if (clusters.remove(id) != null) setDirty();
    }

    public void markPlaced(long id, int isle, boolean skipped) {
        Cluster c = clusters.get(id);
        if (c == null || isle < 0 || isle >= c.placed.length) return;
        c.placed[isle] = true;
        c.skipped[isle] = skipped;
        setDirty();
    }

    public Set<GlobalPos> reform() {
        return Collections.unmodifiableSet(reform);
    }

    public void queueReform(GlobalPos pos) {
        if (reform.add(pos)) setDirty();
    }

    public boolean unqueueReform(GlobalPos pos) {
        boolean r = reform.remove(pos);
        if (r) setDirty();
        return r;
    }

    public Optional<BlockPos> knownStart() {
        return Optional.ofNullable(knownStart);
    }

    public void setKnownStart(@Nullable BlockPos pos) {
        knownStart = pos == null ? null : pos.immutable();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextId", nextId);
        ListTag list = new ListTag();
        for (Cluster c : clusters.values()) {
            CompoundTag ct = new CompoundTag();
            ct.putLong("Id", c.id);
            ct.putString("Origin", c.origin.name());
            ct.putInt("X", c.x);
            ct.putInt("Z", c.z);
            ListTag isles = new ListTag();
            for (int i = 0; i < c.isles.size(); i++) {
                CompoundTag it = new CompoundTag();
                IslePiece.write(c.isles.get(i), it);
                it.putBoolean("Placed", c.placed[i]);
                it.putBoolean("Skipped", c.skipped[i]);
                isles.add(it);
            }
            ct.put("Isles", isles);
            list.add(ct);
        }
        tag.put("Clusters", list);
        ListTag rl = new ListTag();
        for (GlobalPos g : reform) {
            CompoundTag gt = new CompoundTag();
            gt.putString("Dim", g.dimension().location().toString());
            gt.put("Pos", NbtUtils.writeBlockPos(g.pos()));
            rl.add(gt);
        }
        tag.put("Reform", rl);
        if (knownStart != null) tag.put("KnownStart", NbtUtils.writeBlockPos(knownStart));
        return tag;
    }

    public static IsleSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        IsleSavedData d = new IsleSavedData();
        d.nextId = Math.max(1, tag.getLong("NextId"));
        for (Tag t : tag.getList("Clusters", Tag.TAG_COMPOUND)) {
            CompoundTag ct = (CompoundTag) t;
            List<IsleSpec> isles = new ArrayList<>();
            ListTag il = ct.getList("Isles", Tag.TAG_COMPOUND);
            boolean[] placed = new boolean[il.size()];
            boolean[] skipped = new boolean[il.size()];
            for (int i = 0; i < il.size(); i++) {
                CompoundTag it = il.getCompound(i);
                isles.add(IslePiece.read(it));
                placed[i] = it.getBoolean("Placed");
                skipped[i] = it.getBoolean("Skipped");
            }
            Origin origin;
            try {
                origin = Origin.valueOf(ct.getString("Origin"));
            } catch (IllegalArgumentException e) {
                origin = Origin.MANUAL;
            }
            Cluster c = new Cluster(ct.getLong("Id"), origin, ct.getInt("X"), ct.getInt("Z"), isles, placed, skipped);
            d.clusters.put(c.id, c);
            d.nextId = Math.max(d.nextId, c.id + 1);
        }
        for (Tag t : tag.getList("Reform", Tag.TAG_COMPOUND)) {
            CompoundTag gt = (CompoundTag) t;
            ResourceLocation dim = ResourceLocation.tryParse(gt.getString("Dim"));
            Optional<BlockPos> pos = NbtUtils.readBlockPos(gt, "Pos");
            if (dim != null && pos.isPresent()) d.reform.add(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), pos.get()));
        }
        d.knownStart = NbtUtils.readBlockPos(tag, "KnownStart").orElse(null);
        return d;
    }
}
