package dev.firmages.core.sky;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.firmages.core.FirmagesCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * Loads {@code data/firmages/firmages_sky/sky_harvest.json} on every datapack load (SPEC §26.6); the pack overrides it
 * from {@code kubejs/data/firmages/firmages_sky/}. A missing or rejected file logs an ERROR and keeps the shipped
 * values ({@link SkyHarvestData#DEFAULT}).
 */
public final class SkyHarvestDataLoader extends SimpleJsonResourceReloadListener {
    public static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "sky_harvest");
    private static volatile SkyHarvestData current = SkyHarvestData.DEFAULT;
    private static volatile boolean loaded;

    public SkyHarvestDataLoader() {
        super(new Gson(), StarfallDataLoader.FOLDER);
    }

    public static SkyHarvestData current() {
        return current;
    }

    /** True once a valid file was read (the self-test checks it). */
    public static boolean loaded() {
        return loaded;
    }

    /** Test hook: replace the data until the next datapack load. */
    public static void set(SkyHarvestData data) {
        current = data;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        JsonElement json = files.get(FILE);
        if (json == null || !json.isJsonObject()) {
            FirmagesCore.LOGGER.error("Sky Harvest data: {} missing or not an object; using the built-in values", FILE);
            current = SkyHarvestData.DEFAULT;
            loaded = false;
            return;
        }
        SkyHarvestData data = SkyHarvestData.parse(json.getAsJsonObject());
        if (!data.valid()) {
            data.errors().forEach(e -> FirmagesCore.LOGGER.error("Sky Harvest data rejected: {}", e));
            current = SkyHarvestData.DEFAULT;
            loaded = false;
            return;
        }
        SkyHarvestData.Aurora a = data.aurora();
        FirmagesCore.LOGGER.info("Sky Harvest data: aurora at Y {} at or below {} C (chance {}/{}), {} mB/h; storm every {} ticks, thunder x{}",
            a.referenceY(), a.maxAverageTemp(), a.chanceCold(), a.chanceWarm(), a.mbPerHour(), data.storm().meanStrikeTicksRain(),
            data.storm().thunderFactor());
        current = data;
        loaded = true;
    }
}
