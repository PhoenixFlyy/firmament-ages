package dev.firmages.core.ark;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.TreeMap;

/**
 * Loads {@code data/<ns>/firmages_ark/**.json} on every datapack load (SPEC §31.8). A bad file is rejected with an
 * ERROR; a crown entry whose {@code neoforge:conditions} fail is left out (INFO line). The pack can override any island
 * from {@code kubejs/data/firmages/firmages_ark/}.
 */
public final class ArkDataLoader extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "firmages_ark";
    private static volatile ArkData current = ArkData.EMPTY;

    public ArkDataLoader() {
        super(new Gson(), FOLDER);
    }

    public static ArkData current() {
        return current;
    }

    /** GameTests install their own islands (vanilla ids) and put the loaded data back afterwards. @return the old data */
    public static ArkData replaceForTests(ArkData data) {
        ArkData old = current;
        current = data;
        return old;
    }

    /** The conditions the island files use: {@code neoforge:item_exists} and {@code neoforge:mod_loaded}; others fail. */
    public static boolean conditionsMet(JsonArray conditions) {
        for (JsonElement e : conditions) {
            JsonObject c = e.getAsJsonObject();
            String type = c.has("type") ? c.get("type").getAsString() : "";
            boolean ok = switch (type) {
                case "neoforge:item_exists" -> {
                    ResourceLocation id = ResourceLocation.tryParse(c.get("item").getAsString());
                    yield id != null && BuiltInRegistries.ITEM.containsKey(id);
                }
                case "neoforge:mod_loaded" -> ModList.get().isLoaded(c.get("modid").getAsString());
                default -> false;
            };
            if (!ok) return false;
        }
        return true;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, JsonElement> byId = new TreeMap<>();
        files.forEach((id, json) -> byId.put(id.toString(), json));
        ArkData data = ArkData.parse(byId, ArkDataLoader::conditionsMet);
        data.errors().forEach(e -> FirmagesCore.LOGGER.error("Archipelago data rejected: {}", e));
        data.warnings().forEach(w -> FirmagesCore.LOGGER.warn("Archipelago data: {}", w));
        // An item of a later sky milestone that is not in this build yet: expected, so INFO, not WARN.
        data.skipped().forEach(w -> FirmagesCore.LOGGER.info("Archipelago data: {}", w));
        if (data.islands().size() < ArkData.ISLANDS) {
            FirmagesCore.LOGGER.warn("Archipelago data: rules for {} of {} islands; the others take nothing", data.islands().size(), ArkData.ISLANDS);
        }
        FirmagesCore.LOGGER.info("Archipelago data: islands {}", data.islands().keySet());
        current = data;
    }
}
