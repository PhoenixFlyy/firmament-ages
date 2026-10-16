package dev.firmages.core.launch;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code data/<ns>/firmages_launch/fuel.json} and {@code module/*.json} on every datapack load (SPEC §31.4); the
 * pack overrides them from {@code kubejs/data/}. The files are parsed and checked once the load's tags are bound
 * ({@code TagsUpdatedEvent}, server data load), because the checks ask {@code #c:ores}, {@code #c:raw_materials} and the
 * {@code firmages:age_blocks/*} tags. Rejected files log an ERROR each.
 */
public final class LaunchDataLoader extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "firmages_launch";
    private static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores"));
    private static final TagKey<Item> RAW = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "raw_materials"));

    private static volatile LaunchData current = LaunchData.EMPTY;
    private static volatile Map<ResourceLocation, JsonElement> pending;

    public LaunchDataLoader() {
        super(new Gson(), FOLDER);
    }

    public static LaunchData current() {
        return current;
    }

    /** Test hook: replace the loaded data until the next datapack load. */
    public static void set(LaunchData data) {
        current = data;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        pending = Map.copyOf(files);
    }

    /** Server data load: the tags of this load are bound now; parse and check the files read by {@link #apply}. */
    static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        Map<ResourceLocation, JsonElement> files = pending;
        if (files == null) return;
        pending = null;
        current = build(files);
    }

    /** Parses {@code files} (ids as the reload listener reports them: {@code ns:fuel}, {@code ns:module/x}). */
    public static LaunchData build(Map<ResourceLocation, JsonElement> files) {
        Map<String, JsonElement> fuel = new HashMap<>();
        Map<String, JsonElement> modules = new HashMap<>();
        files.forEach((id, json) -> {
            if (id.getPath().equals("fuel")) fuel.put(id.getNamespace() + ":fuel", json);
            else if (id.getPath().startsWith("module/")) modules.put(id.toString(), json);
        });
        LaunchData data = LaunchData.parse(fuel, modules, LOOKUP);
        data.errors().forEach(e -> FirmagesCore.LOGGER.error("Launch data rejected: {}", e));
        data.skipped().forEach(s -> FirmagesCore.LOGGER.info("Launch data skipped: {}", s));
        FirmagesCore.LOGGER.info("Launch data: fuel {}, modules {}", data.fuel().map(f -> "#" + f.fluidTag() + " " + f.launchMb() + " mB, "
            + f.throttle().size() + " steps").orElse("missing"), data.modules().keySet());
        return data;
    }

    private static Optional<Item> item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl);
    }

    /** Registry and bound tags of this load. */
    static final LaunchData.Lookup LOOKUP = new LaunchData.Lookup() {
        @Override
        public boolean registered(String id) {
            return item(id).isPresent();
        }

        @Override
        public String forbidden(String id) {
            String ns = LaunchData.forbiddenNamespace(id);
            if (ns != null) return ns;
            Optional<Item> it = item(id);
            if (it.isEmpty()) return null;
            Holder<Item> h = it.get().builtInRegistryHolder();
            if (h.is(ORES)) return "in #c:ores";
            if (h.is(RAW)) return "in #c:raw_materials";
            if (it.get() instanceof net.minecraft.world.item.BlockItem bi) {
                var bh = bi.getBlock().builtInRegistryHolder();
                for (var tag : bh.tags().toList()) {
                    ResourceLocation t = tag.location();
                    if (t.getNamespace().equals(FirmagesCore.MOD_ID) && t.getPath().startsWith(AgeIndex.BLOCK_PREFIX + "/")) return "in #" + t;
                }
            }
            return null;
        }

        @Override
        public AgeId age(String id) {
            return item(id).map(i -> AgeIndex.current().ageOf(i)).orElse(null);
        }
    };
}
