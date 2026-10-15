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
 * Loads {@code data/firmages/firmages_sky/starfall.json} on every datapack load (SPEC §25.7); the pack overrides it from
 * {@code kubejs/data/firmages/firmages_sky/}. A rejected file leaves Starfall without data (no falls) and logs an ERROR.
 */
public final class StarfallDataLoader extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "firmages_sky";
    public static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "starfall");
    private static volatile StarfallData current = StarfallData.EMPTY;

    public StarfallDataLoader() {
        super(new Gson(), FOLDER);
    }

    public static StarfallData current() {
        return current;
    }

    /** Test hook: replace the loaded data until the next datapack load. */
    public static void set(StarfallData data) {
        current = data;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        JsonElement json = files.get(FILE);
        if (json == null || !json.isJsonObject()) {
            FirmagesCore.LOGGER.error("Starfall data: {} missing or not an object; no stars fall", FILE);
            current = StarfallData.EMPTY;
            return;
        }
        StarfallData data = StarfallData.parse(json.getAsJsonObject());
        if (!data.errors().isEmpty()) {
            data.errors().forEach(e -> FirmagesCore.LOGGER.error("Starfall data rejected: {}", e));
            current = StarfallData.EMPTY;
            return;
        }
        FirmagesCore.LOGGER.info("Starfall data: Ages {}, sizes {}, metals {}, pity after {} nights, old craters {}",
            data.ages().keySet(), data.sizes().keySet(), data.metals().keySet(), data.pityNights(), data.oldCraterChance());
        current = data;
    }
}
