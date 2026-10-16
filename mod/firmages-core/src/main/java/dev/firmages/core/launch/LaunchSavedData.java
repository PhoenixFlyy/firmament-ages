package dev.firmages.core.launch;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code firmages_launch} (overworld storage, SPEC §31.6): the probes each team brought home, keyed by the
 * FTB team id (the player id without FTB Teams). Layout: {@code teams.<team uuid>.<module item id> = long}.
 */
public final class LaunchSavedData extends SavedData {
    public static final String NAME = "firmages_launch";

    private final Map<UUID, Map<String, Long>> teams = new HashMap<>();

    public static LaunchSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(LaunchSavedData::new, LaunchSavedData::load, null), NAME);
    }

    public static LaunchSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        LaunchSavedData d = new LaunchSavedData();
        CompoundTag teams = tag.getCompound("teams");
        for (String key : teams.getAllKeys()) {
            UUID team;
            try {
                team = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag mods = teams.getCompound(key);
            Map<String, Long> m = new LinkedHashMap<>();
            for (String module : mods.getAllKeys()) if (mods.getLong(module) > 0) m.put(module, mods.getLong(module));
            if (!m.isEmpty()) d.teams.put(team, m);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag teams = new CompoundTag();
        this.teams.forEach((team, mods) -> {
            CompoundTag t = new CompoundTag();
            mods.forEach(t::putLong);
            teams.put(team.toString(), t);
        });
        tag.put("teams", teams);
        return tag;
    }

    /** The counts of {@code team} (read-only; empty when it never flew). */
    public Map<String, Long> of(UUID team) {
        Map<String, Long> m = teams.get(team);
        return m == null ? Map.of() : Collections.unmodifiableMap(m);
    }

    /** Every team (status command). */
    public Map<UUID, Map<String, Long>> all() {
        return Collections.unmodifiableMap(teams);
    }

    void add(UUID team, String module, long n) {
        if (n <= 0) return;
        teams.computeIfAbsent(team, t -> new LinkedHashMap<>()).merge(module, n, Long::sum);
        setDirty();
    }
}
