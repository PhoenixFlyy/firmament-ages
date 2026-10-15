package dev.firmages.core.sky;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Overworld storage {@code firmages_sky} (SPEC §24.4, §25.8): the Starfall sites, the night counters and the aurora
 * state (seed and override, read by the Sky Harvest of M11). The chunk index (chunk -> sites whose core lies within
 * {@link StarfallRules#HOT_RADIUS} of it) is rebuilt on load and serves the hot-core loot condition without a world scan.
 */
public final class SkySavedData extends SavedData {
    public static final String DATA_NAME = "firmages_sky";
    /** Index lookups of the hot-core condition since start (the GameTest counts them). */
    public static final AtomicLong INDEX_READS = new AtomicLong();

    private final Map<Long, StarfallSite> sites = new LinkedHashMap<>();
    private final Long2ObjectMap<LongList> coreIndex = new Long2ObjectOpenHashMap<>();
    private long nextId = 1;
    private int nightsSinceFall;
    private String lastMetal = "";
    private boolean guaranteedTonight;
    /** Overworld day ({@code dayTime / 24000}) of the last night roll; -1 before the first. */
    private long lastRollDay = -1;
    private long auroraSeed = new Random().nextLong();
    private String auroraOverride = "auto";

    public static SkySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SkySavedData::new, SkySavedData::load, null), DATA_NAME);
    }

    public Collection<StarfallSite> sites() {
        return Collections.unmodifiableCollection(sites.values());
    }

    public Optional<StarfallSite> site(long id) {
        return Optional.ofNullable(sites.get(id));
    }

    public StarfallSite newSite(int x, int z, String metal, String size, StarfallSite.Origin origin, long seed) {
        StarfallSite s = new StarfallSite(nextId++, x, z, metal, size, origin, seed);
        sites.put(s.id, s);
        setDirty();
        return s;
    }

    /** Puts a site's core into the chunk index (call when the core position is known). */
    public void indexCore(StarfallSite s) {
        if (s.core == null) return;
        int r = StarfallRules.HOT_RADIUS;
        for (int cx = (s.core.getX() - r) >> 4; cx <= (s.core.getX() + r) >> 4; cx++) {
            for (int cz = (s.core.getZ() - r) >> 4; cz <= (s.core.getZ() + r) >> 4; cz++) {
                LongList l = coreIndex.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new LongArrayList(1));
                if (!l.contains(s.id)) l.add(s.id);
            }
        }
    }

    /**
     * The site whose core lies within {@link StarfallRules#HOT_RADIUS} of {@code pos}, by one map lookup of the
     * position's chunk.
     */
    public Optional<StarfallSite> siteNear(BlockPos pos) {
        INDEX_READS.incrementAndGet();
        LongList ids = coreIndex.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (ids == null) return Optional.empty();
        long r2 = (long) StarfallRules.HOT_RADIUS * StarfallRules.HOT_RADIUS;
        for (int i = 0; i < ids.size(); i++) {
            StarfallSite s = sites.get(ids.getLong(i));
            if (s != null && s.core != null && s.core.distSqr(pos) <= r2) return Optional.of(s);
        }
        return Optional.empty();
    }

    public int nightsSinceFall() {
        return nightsSinceFall;
    }

    public void setNightsSinceFall(int n) {
        nightsSinceFall = n;
        setDirty();
    }

    public String lastMetal() {
        return lastMetal;
    }

    public void setLastMetal(String metal) {
        lastMetal = metal;
        setDirty();
    }

    public boolean guaranteedTonight() {
        return guaranteedTonight;
    }

    public void setGuaranteedTonight(boolean g) {
        guaranteedTonight = g;
        setDirty();
    }

    public long lastRollDay() {
        return lastRollDay;
    }

    public void setLastRollDay(long day) {
        lastRollDay = day;
        setDirty();
    }

    public long auroraSeed() {
        return auroraSeed;
    }

    public String auroraOverride() {
        return auroraOverride;
    }

    public void setAuroraOverride(String o) {
        auroraOverride = o;
        setDirty();
    }

    /** Sites that are not carved yet (PENDING or CARVING). */
    public long openCount() {
        return sites.values().stream().filter(StarfallSite::open).count();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (StarfallSite s : sites.values()) list.add(s.save());
        tag.put("sites", list);
        tag.putLong("nextId", nextId);
        tag.putInt("nightsSinceFall", nightsSinceFall);
        tag.putString("lastMetal", lastMetal);
        tag.putBoolean("guaranteedTonight", guaranteedTonight);
        tag.putLong("lastRollDay", lastRollDay);
        tag.putLong("auroraSeed", auroraSeed);
        tag.putString("auroraOverride", auroraOverride);
        return tag;
    }

    public static SkySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SkySavedData d = new SkySavedData();
        for (Tag t : tag.getList("sites", Tag.TAG_COMPOUND)) {
            StarfallSite s = StarfallSite.load((CompoundTag) t);
            d.sites.put(s.id, s);
            d.indexCore(s);
        }
        d.nextId = Math.max(tag.getLong("nextId"), d.sites.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1);
        d.nightsSinceFall = tag.getInt("nightsSinceFall");
        d.lastMetal = tag.getString("lastMetal");
        d.guaranteedTonight = tag.getBoolean("guaranteedTonight");
        d.lastRollDay = tag.contains("lastRollDay") ? tag.getLong("lastRollDay") : -1;
        if (tag.contains("auroraSeed")) d.auroraSeed = tag.getLong("auroraSeed");
        if (tag.contains("auroraOverride")) d.auroraOverride = tag.getString("auroraOverride");
        return d;
    }

    /** A copy through save and load (the GameTest round trip). */
    public static SkySavedData roundTrip(SkySavedData d, HolderLookup.Provider registries) {
        return load(d.save(new CompoundTag(), registries), registries);
    }
}
