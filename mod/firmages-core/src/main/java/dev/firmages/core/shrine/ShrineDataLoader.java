package dev.firmages.core.shrine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.firmages.core.FirmagesCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Loads {@code data/<ns>/firmages_shrine/**.json} on every datapack load (SPEC §2.6), and resolves the
 * consecration roles of every ring's pattern keys from the ring multiblock JSON ({@code modonomicon/multiblocks/})
 * and {@code consecration.json} (SPEC §17).
 */
public final class ShrineDataLoader extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "firmages_shrine";
    private static volatile ShrineData current = ShrineData.EMPTY;
    /** Multiblock id -> pattern key -> role. */
    private static volatile Map<String, Map<Character, Consecration.Role>> ringRoles = Map.of();

    public ShrineDataLoader() {
        super(new Gson(), FOLDER);
    }

    public static ShrineData current() {
        return current;
    }

    /** Roles of the pattern keys of ring multiblock {@code id} (empty when the ring is not consecrated). */
    public static Map<Character, Consecration.Role> ringRoles(ResourceLocation id) {
        return ringRoles.getOrDefault(id.toString(), Map.of());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, JsonElement> byId = new TreeMap<>();
        files.forEach((id, json) -> byId.put(id.toString(), json));
        ShrineData data = ShrineData.parse(byId);
        data.errors().forEach(e -> FirmagesCore.LOGGER.error("Shrine data rejected: {}", e));
        data.warnings().forEach(w -> FirmagesCore.LOGGER.warn("Shrine data: {}", w));
        FirmagesCore.LOGGER.info("Shrine data: tiers {}, fallback {}, rings up to {}, {} offerings, blessings {}",
            data.tiers().keySet(), data.fallback().isPresent(), data.highestRing(), data.offerings().size(), data.blessings().keySet());
        Map<String, Map<Character, Consecration.Role>> roles = new HashMap<>();
        for (ShrineTier t : data.tiers().values()) {
            if (t.multiblock().isEmpty()) continue;
            ResourceLocation mb = ResourceLocation.parse(t.multiblock().get());
            Optional<JsonObject> json = readMultiblock(rm, mb);
            if (json.isEmpty()) {
                FirmagesCore.LOGGER.warn("Shrine consecration: ring multiblock {} of tier {} not found; its blocks stay Age material", mb, t.tier());
                continue;
            }
            Set<Character> keep = Set.of(ShrineTier.HEART_KEY, t.plinthKey());
            Map<Character, Consecration.Role> r = data.consecration().rolesFor(mb.toString(), json.get(), keep);
            List<Character> unmapped = data.consecration().unmapped(mb.toString(), json.get(), keep);
            if (!unmapped.isEmpty()) {
                FirmagesCore.LOGGER.warn("Shrine consecration: {} keys {} have no role in consecration.json; they stay Age material", mb, unmapped);
            }
            roles.put(mb.toString(), r);
        }
        FirmagesCore.LOGGER.info("Shrine consecration: roles for {} rings", roles.size());
        ringRoles = Map.copyOf(roles);
        current = data;
    }

    private static Optional<JsonObject> readMultiblock(ResourceManager rm, ResourceLocation id) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "modonomicon/multiblocks/" + id.getPath() + ".json");
        Optional<Resource> res = rm.getResource(file);
        if (res.isEmpty()) return Optional.empty();
        try (Reader r = res.get().openAsReader()) {
            JsonElement e = JsonParser.parseReader(r);
            return e.isJsonObject() ? Optional.of(e.getAsJsonObject()) : Optional.empty();
        } catch (Exception e) {
            FirmagesCore.LOGGER.error("Shrine consecration: cannot read {}", file, e);
            return Optional.empty();
        }
    }
}
