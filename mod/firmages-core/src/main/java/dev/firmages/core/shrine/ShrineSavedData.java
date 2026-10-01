package dev.firmages.core.shrine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * SavedData {@code firmages_shrine} in the overworld storage (SPEC §2.5): the one shrine of the server, its last
 * validation, the enshrined relics (plinth positions in the heart's dimension) and the Ages the shrine granted
 * (catch-up for players who were offline when ProgressiveStages runs per player).
 */
public final class ShrineSavedData extends SavedData {
    public static final String DATA_NAME = "firmages_shrine";

    /**
     * One enshrined relic.
     *
     * @param item the relic's item id (after a return: what came back, e.g. the Awakened Keystone)
     * @param lent true while the shrine has lent it out (SPEC §7.4); the record stays, so ring and blessings stay valid
     */
    public record Relic(BlockPos plinth, int tier, String item, boolean lent) {
        public Relic(BlockPos plinth, int tier, String item) {
            this(plinth, tier, item, false);
        }
    }

    @Nullable private GlobalPos heart;
    private boolean intact;
    private int lastValidRing = -1;
    private long lastValidated;
    private final Map<Long, Relic> relics = new TreeMap<>();
    private final Set<String> grantedStages = new LinkedHashSet<>();

    public static ShrineSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(ShrineSavedData::new, ShrineSavedData::load, null), DATA_NAME);
    }

    public Optional<GlobalPos> heart() {
        return Optional.ofNullable(heart);
    }

    public void setHeart(@Nullable GlobalPos pos) {
        heart = pos;
        if (pos == null) {
            intact = false;
            lastValidRing = -1;
        }
        setDirty();
    }

    public boolean intact() {
        return intact;
    }

    public int lastValidRing() {
        return lastValidRing;
    }

    public long lastValidated() {
        return lastValidated;
    }

    public void setValidation(boolean intact, int validRing, long gameTime) {
        if (this.intact != intact || this.lastValidRing != validRing) setDirty();
        this.intact = intact;
        this.lastValidRing = validRing;
        this.lastValidated = gameTime;
    }

    public Map<Long, Relic> relics() {
        return Map.copyOf(relics);
    }

    public void putRelic(Relic r) {
        relics.put(r.plinth().asLong(), r);
        setDirty();
    }

    public Optional<Relic> removeRelic(BlockPos plinth) {
        Relic r = relics.remove(plinth.asLong());
        if (r != null) setDirty();
        return Optional.ofNullable(r);
    }

    public Optional<Relic> relicForTier(int tier) {
        return relics.values().stream().filter(r -> r.tier() == tier).findFirst();
    }

    /** Relics the shrine has lent out now. */
    public java.util.List<Relic> lentRelics() {
        return relics.values().stream().filter(Relic::lent).toList();
    }

    public Set<String> grantedStages() {
        return Set.copyOf(grantedStages);
    }

    public void addGranted(String stage) {
        if (grantedStages.add(stage)) setDirty();
    }

    public void removeGranted(String stage) {
        if (grantedStages.remove(stage)) setDirty();
    }

    static ShrineSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ShrineSavedData d = new ShrineSavedData();
        if (tag.contains("heartDim") && tag.contains("heartPos")) {
            ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("heartDim")));
            NbtUtils.readBlockPos(tag, "heartPos").ifPresent(p -> d.heart = GlobalPos.of(dim, p));
        }
        d.intact = tag.getBoolean("intact");
        d.lastValidRing = tag.contains("lastValidRing") ? tag.getInt("lastValidRing") : -1;
        d.lastValidated = tag.getLong("lastValidated");
        ListTag rl = tag.getList("relics", Tag.TAG_COMPOUND);
        for (int i = 0; i < rl.size(); i++) {
            CompoundTag r = rl.getCompound(i);
            BlockPos p = BlockPos.of(r.getLong("pos"));
            d.relics.put(p.asLong(), new Relic(p, r.getInt("tier"), r.getString("item"), r.getBoolean("lent")));
        }
        ListTag gl = tag.getList("granted", Tag.TAG_STRING);
        for (int i = 0; i < gl.size(); i++) d.grantedStages.add(gl.getString(i));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (heart != null) {
            tag.putString("heartDim", heart.dimension().location().toString());
            tag.put("heartPos", NbtUtils.writeBlockPos(heart.pos()));
        }
        tag.putBoolean("intact", intact);
        tag.putInt("lastValidRing", lastValidRing);
        tag.putLong("lastValidated", lastValidated);
        ListTag rl = new ListTag();
        for (Relic r : relics.values()) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", r.plinth().asLong());
            c.putInt("tier", r.tier());
            c.putString("item", r.item());
            if (r.lent()) c.putBoolean("lent", true);
            rl.add(c);
        }
        tag.put("relics", rl);
        ListTag gl = new ListTag();
        grantedStages.forEach(s -> gl.add(StringTag.valueOf(s)));
        tag.put("granted", gl);
        return tag;
    }

    /** NBT round trip, used by the GameTest for save/load. */
    public static ShrineSavedData reload(ShrineSavedData d, HolderLookup.Provider registries) {
        return load(d.save(new CompoundTag(), registries), registries);
    }
}
