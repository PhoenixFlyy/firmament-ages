package dev.firmages.core.shrine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.firmages.core.FirmagesCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;
import java.util.TreeMap;

/** Loads {@code data/<ns>/firmages_shrine/**.json} on every datapack load (SPEC §2.6). */
public final class ShrineDataLoader extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "firmages_shrine";
    private static volatile ShrineData current = ShrineData.EMPTY;

    public ShrineDataLoader() {
        super(new Gson(), FOLDER);
    }

    public static ShrineData current() {
        return current;
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
        current = data;
    }
}
