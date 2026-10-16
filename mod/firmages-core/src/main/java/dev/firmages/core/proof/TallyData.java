package dev.firmages.core.proof;

import dev.firmages.core.age.AgeId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code firmages_tally} (overworld storage, SPEC §22.1): the tally of every team, keyed by the FTB team id
 * (the player id without FTB Teams). Layout: {@code teams.<team uuid>.<age id>.<item id> = long}.
 */
public final class TallyData extends SavedData {
    public static final String NAME = "firmages_tally";

    private final Map<UUID, TallyCounts> teams = new HashMap<>();

    public static TallyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TallyData::new, TallyData::load, null), NAME);
    }

    public static TallyData load(CompoundTag tag, HolderLookup.Provider registries) {
        TallyData d = new TallyData();
        CompoundTag teams = tag.getCompound("teams");
        for (String key : teams.getAllKeys()) {
            UUID team;
            try {
                team = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag ages = teams.getCompound(key);
            TallyCounts counts = new TallyCounts();
            for (String ageKey : ages.getAllKeys()) {
                AgeId age = AgeId.byId(ageKey).orElse(null);
                if (age == null) continue;
                CompoundTag items = ages.getCompound(ageKey);
                for (String item : items.getAllKeys()) counts.add(age, item, items.getLong(item));
            }
            if (!counts.isEmpty()) d.teams.put(team, counts);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag teams = new CompoundTag();
        this.teams.forEach((team, counts) -> {
            CompoundTag ages = new CompoundTag();
            counts.view().forEach((age, items) -> {
                CompoundTag t = new CompoundTag();
                items.forEach(t::putLong);
                ages.put(age.id(), t);
            });
            teams.put(team.toString(), ages);
        });
        tag.put("teams", teams);
        return tag;
    }

    /** The tally of {@code team}; empty (and not stored) when it never counted. */
    public TallyCounts of(UUID team) {
        TallyCounts c = teams.get(team);
        return c != null ? c : new TallyCounts();
    }

    /** The highest sum over all teams (one fellowship per server: its tally), see {@link TallyCounts#sum}. */
    public long best(java.util.function.Predicate<String> item, AgeId from) {
        long best = 0;
        for (TallyCounts c : teams.values()) best = Math.max(best, c.sum(item, from));
        return best;
    }

    void add(UUID team, AgeId age, String item, long n) {
        teams.computeIfAbsent(team, t -> new TallyCounts()).add(age, item, n);
        setDirty();
    }
}
