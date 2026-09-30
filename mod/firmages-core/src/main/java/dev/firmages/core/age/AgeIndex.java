package dev.firmages.core.age;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.firmages.core.FirmagesCore;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Item/block/fluid → Age lookup (SPEC §2.2), built from the {@code firmages:age_items|age_blocks|age_fluids/<age>}
 * tag JSON of a resource manager. Immutable; {@link #current()} is swapped atomically at the end of each
 * datapack load. Untagged entries count as always unlocked.
 */
public record AgeIndex(Map<Item, AgeId> items, Map<Block, AgeId> blocks, Map<Fluid, AgeId> fluids, int generation, Report report) {

    public static final String ITEM_PREFIX = "age_items";
    public static final String BLOCK_PREFIX = "age_blocks";
    public static final String FLUID_PREFIX = "age_fluids";

    /** Diagnostics of one build. */
    public record Report(AgeAssignment itemTags, AgeAssignment blockTags, AgeAssignment fluidTags,
                         List<String> unknownIds, List<String> warnings, long buildMillis) {}

    private static final AtomicInteger GENERATIONS = new AtomicInteger();
    private static final AtomicInteger LOADS_BEGUN = new AtomicInteger();
    private static final AgeIndex EMPTY = new AgeIndex(Map.of(), Map.of(), Map.of(), 0, null);

    private static volatile AgeIndex current = EMPTY;
    /** Resource manager of the datapack load in progress (captured at {@code ReloadableServerResources#loadResources}). */
    private static volatile WeakReference<ResourceManager> loadingManager = new WeakReference<>(null);
    private static WeakReference<ResourceManager> memoManager = new WeakReference<>(null);
    private static AgeIndex memoIndex;

    public static AgeIndex current() {
        return current;
    }

    /**
     * The index of the datapack load in progress, else {@link #current()} (the previous load's). Used on the reload
     * path: KubeJS 2101 runs tag handlers as a pre-capture, on a reload at the start of {@code loadResources}, on the
     * initial load before the resource manager even exists (then only an empty index is available;
     * {@link AgeService#checkLoadAnswers} detects that and reloads once after start).
     */
    public static AgeIndex loading() {
        ResourceManager rm = loadingManager.get();
        return rm != null ? forResources(rm) : current;
    }

    /** True between the capture at {@code loadResources} and the apply phase of the same load. */
    public static boolean loadInProgress() {
        return loadingManager.get() != null;
    }

    /** Called from the mixin at the start of every server datapack load. */
    public static void beginLoad(ResourceManager rm) {
        loadingManager = new WeakReference<>(rm);
        LOADS_BEGUN.incrementAndGet();
    }

    /** Number of datapack loads the mixin captured since start (diagnostics: proves the mixin is applied). */
    public static int loadsBegun() {
        return LOADS_BEGUN.get();
    }

    /** Called by {@link AgeReloadListener} in its apply phase: the load's index becomes current. */
    static AgeIndex finishLoad(ResourceManager rm) {
        AgeIndex idx = forResources(rm);
        current = idx;
        if (loadingManager.get() == rm) loadingManager = new WeakReference<>(null);
        logSummary(idx);
        return idx;
    }

    /** Built once per resource manager (memoized), thread-safe. */
    public static synchronized AgeIndex forResources(ResourceManager rm) {
        if (memoManager.get() == rm && memoIndex != null) return memoIndex;
        AgeIndex idx = build(rm);
        memoManager = new WeakReference<>(rm);
        memoIndex = idx;
        return idx;
    }

    public boolean isEmpty() {
        return items.isEmpty() && blocks.isEmpty() && fluids.isEmpty();
    }

    /** True when no Age tag holds anything: the gate cannot know what to lock (SPEC §2.1). */
    public boolean misconfigured() {
        return isEmpty();
    }

    public AgeId ageOf(Item item) {
        return items.get(item);
    }

    public AgeId ageOf(Block block) {
        return blocks.get(block);
    }

    public AgeId ageOf(Fluid fluid) {
        return fluids.get(fluid);
    }

    private static AgeIndex build(ResourceManager rm) {
        long t0 = System.nanoTime();
        List<String> unknown = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        AgeAssignment itemTags = assign(rm, Registries.ITEM, ITEM_PREFIX, warnings);
        AgeAssignment blockTags = assign(rm, Registries.BLOCK, BLOCK_PREFIX, warnings);
        AgeAssignment fluidTags = assign(rm, Registries.FLUID, FLUID_PREFIX, warnings);
        Map<Item, AgeId> items = toObjects(itemTags, BuiltInRegistries.ITEM, unknown);
        Map<Block, AgeId> blocks = toObjects(blockTags, BuiltInRegistries.BLOCK, unknown);
        Map<Fluid, AgeId> fluids = toObjects(fluidTags, BuiltInRegistries.FLUID, unknown);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        return new AgeIndex(items, blocks, fluids, GENERATIONS.incrementAndGet(),
            new Report(itemTags, blockTags, fluidTags, List.copyOf(unknown), List.copyOf(warnings), ms));
    }

    private static AgeAssignment assign(ResourceManager rm, ResourceKey<? extends Registry<?>> registry, String prefix, List<String> warnings) {
        String dir = Registries.tagsDirPath(registry);
        AgeTagResolver resolver = new AgeTagResolver(tagId -> layers(rm, dir, tagId, warnings));
        AgeAssignment a = AgeAssignment.assign(resolver, prefix);
        resolver.warnings().forEach(w -> warnings.add(dir + ": " + w));
        a.duplicates().forEach(d -> warnings.add(dir + ": duplicate " + d));
        return a;
    }

    private static List<JsonElement> layers(ResourceManager rm, String dir, String tagId, List<String> warnings) {
        ResourceLocation tag = ResourceLocation.tryParse(tagId);
        if (tag == null) {
            warnings.add(dir + ": invalid tag id " + tagId);
            return List.of();
        }
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(tag.getNamespace(), dir + "/" + tag.getPath() + ".json");
        List<JsonElement> out = new ArrayList<>();
        for (Resource res : rm.getResourceStack(file)) {
            try (Reader reader = res.openAsReader()) {
                out.add(JsonParser.parseReader(reader));
            } catch (Exception e) {
                warnings.add(dir + ": cannot read " + file + " from pack " + res.sourcePackId() + ": " + e.getMessage());
            }
        }
        return out;
    }

    private static <T> Map<T, AgeId> toObjects(AgeAssignment a, Registry<T> registry, List<String> unknown) {
        Map<T, AgeId> out = new IdentityHashMap<>();
        a.byId().forEach((id, age) -> {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && registry.containsKey(rl)) out.put(registry.get(rl), age);
            else unknown.add(registry.key().location().getPath() + " " + id);
        });
        return Collections.unmodifiableMap(out);
    }

    private static void logSummary(AgeIndex idx) {
        Report r = idx.report();
        if (r == null) return;
        FirmagesCore.LOGGER.info("AgeIndex gen {}: {} items, {} blocks, {} fluids in {} ms (items per Age {}, blocks per Age {})",
            idx.generation(), idx.items().size(), idx.blocks().size(), idx.fluids().size(), r.buildMillis(),
            r.itemTags().perAge(), r.blockTags().perAge());
        if (!r.itemTags().missingTags().isEmpty()) {
            FirmagesCore.LOGGER.warn("AgeIndex: no item tag firmages:age_items/<age> for {}", r.itemTags().missingTags());
        }
        if (!r.blockTags().missingTags().isEmpty()) {
            FirmagesCore.LOGGER.warn("AgeIndex: no block tag firmages:age_blocks/<age> for {}", r.blockTags().missingTags());
        }
        int shown = 0;
        for (String w : r.warnings()) {
            if (shown++ >= 50) {
                FirmagesCore.LOGGER.warn("AgeIndex: {} more warnings not shown", r.warnings().size() - 50);
                break;
            }
            FirmagesCore.LOGGER.warn("AgeIndex: {}", w);
        }
        if (!r.unknownIds().isEmpty()) {
            FirmagesCore.LOGGER.warn("AgeIndex: {} tagged ids are not registered (ignored), first: {}",
                r.unknownIds().size(), r.unknownIds().subList(0, Math.min(20, r.unknownIds().size())));
        }
        if (idx.misconfigured()) {
            FirmagesCore.LOGGER.error("AgeIndex: all firmages:age_* tags are empty. Nothing is known to belong to a locked Age; "
                + "the Age gate cannot lock anything. Generate the tags with dev/gen_stage_locks.py.");
        }
    }
}
