package dev.firmages.core.origin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code mob_9}: The Origin spawns from its own list, {@code data/firmages/origin/spawns.json} (overridable by the
 * pack under {@code kubejs/data/firmages/origin/spawns.json}, reloaded with every datapack load). The biome has no
 * spawners; {@link LevelEvent.PotentialSpawns} (NeoForge, asked by {@code NaturalSpawner} for every spawn attempt)
 * gets exactly this list in {@code firmages:origin}, per category, so biome modifiers of other mods add nothing there
 * and categories without entries (passive mobs) stay empty. Entity ids of mods that are not loaded are skipped with
 * one WARN. Light rules stay the dimension type's (monsters only on block light 0).
 */
public final class OriginSpawns extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "origin";
    public static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "spawns");

    private static volatile OriginSpawnList list = OriginSpawnList.EMPTY;
    private static volatile Map<MobCategory, List<MobSpawnSettings.SpawnerData>> resolved = Map.of();

    public OriginSpawns() {
        super(new Gson(), FOLDER);
    }

    public static void register(IEventBus bus) {
        bus.addListener((AddReloadListenerEvent e) -> e.addListener(new OriginSpawns()));
        bus.addListener(EventPriority.LOWEST, OriginSpawns::onPotentialSpawns);
    }

    public static OriginSpawnList list() {
        return list;
    }

    /** The spawner entries The Origin uses for {@code category} (unregistered entity ids already dropped). */
    public static List<MobSpawnSettings.SpawnerData> spawns(MobCategory category) {
        return resolved.getOrDefault(category, List.of());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        JsonElement json = files.get(FILE);
        OriginSpawnList parsed = json == null ? OriginSpawnList.EMPTY : OriginSpawnList.parse(json);
        if (json == null) FirmagesCore.LOGGER.warn("The Origin: no {} found; nothing spawns there", FILE);
        parsed.errors().forEach(e -> FirmagesCore.LOGGER.error("The Origin spawn list: {}", e));
        Map<MobCategory, List<MobSpawnSettings.SpawnerData>> out = new EnumMap<>(MobCategory.class);
        List<String> used = new ArrayList<>();
        for (MobCategory c : MobCategory.values()) {
            List<MobSpawnSettings.SpawnerData> l = new ArrayList<>();
            for (OriginSpawnList.Entry e : parsed.of(c.getSerializedName())) {
                Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(e.type()));
                if (type.isEmpty()) {
                    FirmagesCore.LOGGER.warn("The Origin spawn list: {} is not registered (mod missing?); skipped", e.type());
                    continue;
                }
                l.add(new MobSpawnSettings.SpawnerData(type.get(), e.weight(), e.minCount(), e.maxCount()));
                used.add(c.getSerializedName() + " " + e.type() + " w" + e.weight());
            }
            out.put(c, List.copyOf(l));
        }
        list = parsed;
        resolved = out;
        FirmagesCore.LOGGER.info("The Origin spawn list: {}", used.isEmpty() ? "nothing" : used);
    }

    /** Replaces the spawn candidates in The Origin by the list (LOWEST: after every other mod's additions). */
    static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != OriginRegistry.ORIGIN) return;
        List<MobSpawnSettings.SpawnerData> want = spawns(event.getMobCategory());
        if (event.getSpawnerDataList().equals(want)) return;
        for (MobSpawnSettings.SpawnerData d : List.copyOf(event.getSpawnerDataList())) event.removeSpawnerData(d);
        want.forEach(event::addSpawnerData);
    }
}
