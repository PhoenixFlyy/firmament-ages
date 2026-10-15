package dev.firmages.core.wayshrine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.shrine.BlessingIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads {@code data/<ns>/firmages_wayshrine/**.json} on every datapack load (SPEC-himmel §28.6). A bad file is rejected
 * with an ERROR; an offering item that is not registered is reported once per load with a WARN (that Age's offering is
 * refused until the item exists).
 */
public final class WayshrineDataLoader extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "firmages_wayshrine";
    private static volatile WayshrineData current = WayshrineData.EMPTY;

    public WayshrineDataLoader() {
        super(new Gson(), FOLDER);
    }

    public static WayshrineData current() {
        return current;
    }

    /** Offering items of the loaded data that are not registered: Age -> item id. */
    public static Map<String, String> unknownOfferings(WayshrineData d) {
        Map<String, String> out = new TreeMap<>();
        d.offerings().forEach((age, item) -> {
            ResourceLocation rl = ResourceLocation.tryParse(item);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) out.put(age, item);
        });
        return out;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, JsonElement> byId = new TreeMap<>();
        files.forEach((id, json) -> byId.put(id.toString(), json));
        WayshrineData data = WayshrineData.parse(byId);
        data.errors().forEach(e -> FirmagesCore.LOGGER.error("Wayshrine data rejected: {}", e));
        data.warnings().forEach(w -> FirmagesCore.LOGGER.warn("Wayshrine data: {}", w));
        unknownOfferings(data).forEach((age, item) -> FirmagesCore.LOGGER.warn(
            "Wayshrine offering of {} names {}, which is not registered; that offering is refused until it is", age, item));
        List<String> ages = List.copyOf(data.rites().keySet());
        FirmagesCore.LOGGER.info("Wayshrine data: types {}, offerings for {}, rites for {}", data.types().keySet(), data.offerings().keySet(), ages);
        current = data;
        BlessingIndex.markDirty();
    }
}
