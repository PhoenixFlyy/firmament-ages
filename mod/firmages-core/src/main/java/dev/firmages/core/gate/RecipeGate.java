package dev.firmages.core.gate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.config.EarlyServerConfig;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.gate.extract.CreateExtractor;
import dev.firmages.core.gate.extract.ExtractSupport;
import dev.firmages.core.gate.extract.IEExtractor;
import dev.firmages.core.gate.extract.MekanismExtractor;
import dev.firmages.core.gate.extract.OccultismExtractor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * m2, the machine-recipe Age gate (SPEC §4): called from {@code RecipeManagerMixin} at the TAIL of
 * {@code RecipeManager#apply} with the post-KubeJS recipe JSON. Classifies every recipe once per load
 * ({@link GateRules#classify}: outputs from {@link OutputExtractor}s, {@code getResultItem} and {@link JsonOutputWalker};
 * stations from the scripts' {@link RecipeMarkers} and the Create heat levels), keeps all of them in a
 * {@link RecipeMaster} and replaces the recipe list with the ones the Age snapshot of the load keeps (§3.2,
 * fail-strict at boot), which rebuilds {@code byName} and {@code byType}; every machine, cache and the client sync then
 * follow. On an Age change {@link #apply} re-filters the master in place (the Age swap, {@code age/AgeSwap}).
 * <p>Some mods add recipes after {@code RecipeManager#apply}: Create Dragons Plus 1.11.9 generates its sandpaper
 * polishing recipes at the TAIL of {@code ReloadableServerResources#updateRegistryTags} and writes them straight into
 * {@code byType}/{@code byName}. {@link #lateFilter} runs after that ({@code ReloadableServerResourcesLateMixin}),
 * classifies every recipe the main pass did not see with the same rules (their JSON comes from {@code Recipe.CODEC})
 * and completes the master.
 * <p>The late pass also resolves the result of the IE and Occultism recipes. Their tag outputs are only read as tags
 * during the load (their getResultItem caches its first answer, and tags are not bound yet), and a tag with an unlocked
 * member keeps the recipe. But the mod makes the one item its tag resolution picks: Occultism's crusher turns
 * {@code #c:dusts/charcoal} into {@code mekanism:dust_charcoal}. Once the tags are bound the cached answer is the real
 * one, so the resolved result becomes one more output the recipe needs.
 */
public final class RecipeGate {
    /** Recipe managers of live {@code ReloadableServerResources}, mapped to the tag manager of the same load. */
    private static final Map<RecipeManager, TagManager> LINKS = Collections.synchronizedMap(new WeakHashMap<>());
    /** Recipe classes whose {@code getResultItem} resolves a tag and caches it: never call it during the load. */
    private static final List<String> LAZY_RESULT_PACKAGES = List.of("blusunrize.immersiveengineering.", "com.klikli_dev.occultism.");

    private static volatile GateReport last;
    private static volatile String lastFailure;
    private static volatile List<OutputExtractor> extractors;

    /** Context of a main pass, kept until the late pass of the same load. */
    private record Pass(RecipeMaster master, GateRules.Lookup lookup, AgeSnapshot snap, Set<ResourceLocation> kept,
                        HolderLookup.Provider registries, GateRules.Stations stations, RecipeMarkers.Snapshot markers, long millis) {}

    private static final Map<RecipeManager, Pass> PASSES = Collections.synchronizedMap(new WeakHashMap<>());
    /** The completed master of each live recipe manager (after its late pass). */
    private static final Map<RecipeManager, RecipeMaster> MASTERS = Collections.synchronizedMap(new WeakHashMap<>());

    private RecipeGate() {}

    /** Called from {@code ReloadableServerResourcesMixin} when the resources of a datapack load are created. */
    public static void link(RecipeManager recipes, TagManager tags) {
        LINKS.put(recipes, tags);
    }

    /** Report of the last successful filter run or Age swap, or null before the first. */
    public static GateReport lastReport() {
        return last;
    }

    /** Why the last filter run failed (its load kept every recipe), or null if it succeeded. */
    public static String lastFailure() {
        return lastFailure;
    }

    /** The master list of this recipe manager's load, or null (gate failed, or the load is not complete yet). */
    public static RecipeMaster master(RecipeManager manager) {
        return MASTERS.get(manager);
    }

    public static void filter(RecipeManager manager, Map<ResourceLocation, JsonElement> json, ResourceManager resources,
                              HolderLookup.Provider registries) {
        MASTERS.remove(manager);
        try {
            run(manager, json, resources, registries);
            lastFailure = null;
        } catch (RuntimeException | LinkageError e) {
            lastFailure = e.toString();
            FirmagesCore.LOGGER.error("Recipe gate failed; recipes of this load are NOT Age-filtered", e);
        }
    }

    private static void run(RecipeManager manager, Map<ResourceLocation, JsonElement> json, ResourceManager resources,
                            HolderLookup.Provider registries) {
        long t0 = System.nanoTime();
        GateRules.Settings settings = settings();
        AgeIndex idx = AgeIndex.forResources(resources);
        AgeSnapshot snap = AgeService.snapshotForReload();
        TagView tags = TagView.of(LINKS.get(manager), resources);
        List<String> notes = new ArrayList<>();
        if (idx.report() != null && !idx.report().itemTags().missingTags().isEmpty()) {
            notes.add("no firmages:age_items tag for " + idx.report().itemTags().missingTags() + ": their items count as unlocked");
        }
        RecipeMarkers.Snapshot markers = RecipeMarkers.drain();
        markers.errors().forEach(e -> notes.add("FirmAges marker: " + e));
        GateRules.Lookup lookup = lookup(idx, tags);
        GateRules.Stations stations = GateRules.Stations.of(markers, lookup);
        RecipeMaster master = new RecipeMaster(settings, tags.source(), idx.misconfigured(), notes);
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            master.add(classify(holder, json.get(holder.id()), settings, lookup, stations, master, registries, false));
        }
        List<RecipeHolder<?>> kept = master.live(snap);
        if (kept.size() != master.size()) manager.replaceRecipes(kept);
        Set<ResourceLocation> keptIds = new HashSet<>(kept.size() * 2);
        for (RecipeHolder<?> h : kept) keptIds.add(h.id());
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        GateReport report = master.report(snap, ms);
        PASSES.put(manager, new Pass(master, lookup, snap, keptIds, registries, stations, markers, ms));
        last = report;
        FirmagesCore.LOGGER.info("{}; {} recipe markers from the scripts ({} station types, {} requireAge, {} untilAge)", report.summary(),
            markers.size(), markers.stationAges().size(), markers.requireAges().size(), markers.untilAges().size());
        report.notes().forEach(n -> FirmagesCore.LOGGER.error("Recipe gate: {}", n));
        if (report.misconfigured()) {
            FirmagesCore.LOGGER.error("Recipe gate: all firmages:age_* tags are empty, so no recipe is known to belong to a locked Age");
        }
    }

    /** Classifies one recipe (its outputs, its stations). */
    private static RecipeMaster.Entry classify(RecipeHolder<?> holder, JsonElement j, GateRules.Settings settings, GateRules.Lookup lookup,
                                               GateRules.Stations stations, RecipeMaster master, HolderLookup.Provider registries, boolean late) {
        Recipe<?> recipe = holder.value();
        String id = holder.id().toString();
        String type = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
        String serializer = String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()));
        OutputSink sink = new OutputSink();
        String outputs = "(not examined)";
        if (GateRules.byConfig(id, type, serializer, settings) == null) {
            extract(recipe, sink, registries, master, id);
            if (j != null) JsonOutputWalker.walk(j, sink);
            outputs = sink.describe();
        }
        GateRules.Classification cls = GateRules.classify(id, type, serializer, heat(j), sink, settings, lookup, stations);
        return new RecipeMaster.Entry(holder, type, serializer, outputs, cls, late);
    }

    /** {@code heat_requirement} of a Create basin recipe, else null. */
    private static String heat(JsonElement j) {
        if (j instanceof JsonObject o && o.get("heat_requirement") instanceof JsonElement h && h.isJsonPrimitive()) return h.getAsString();
        return null;
    }

    /**
     * The late pass: classifies the recipes that code added after the main pass of this load (see the class comment),
     * resolves the IE and Occultism results and publishes the master. Called at the TAIL of
     * {@code ReloadableServerResources#updateRegistryTags}, after the tags are bound.
     */
    public static void lateFilter(RecipeManager manager) {
        Pass p = PASSES.remove(manager);
        if (p == null) return;
        try {
            long t0 = System.nanoTime();
            RecipeMaster master = p.master();
            Map<ResourceLocation, RecipeHolder<?>> current = new HashMap<>();
            for (RecipeHolder<?> h : manager.getRecipes()) current.put(h.id(), h);
            // Main-pass recipes: a kept one that is gone now was removed by a mod after the apply; one still there may
            // be a new holder object (a mod rewrote it), which the master keeps.
            List<RecipeMaster.Entry> entries = master.entriesMutable();
            Set<ResourceLocation> known = new HashSet<>(entries.size() * 2);
            int removed = 0;
            for (int i = entries.size() - 1; i >= 0; i--) {
                RecipeMaster.Entry e = entries.get(i);
                known.add(e.id());
                RecipeHolder<?> now = current.get(e.id());
                if (now == null && p.kept().contains(e.id())) {
                    entries.remove(i);
                    removed++;
                } else if (now != null && now != e.holder()) {
                    entries.set(i, new RecipeMaster.Entry(now, e.type(), e.serializer(), e.outputs(), e.cls(), e.late()));
                }
            }
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, p.registries());
            GateRules.Settings settings = master.settings();
            int late = 0;
            for (RecipeHolder<?> holder : manager.getRecipes()) {
                if (known.contains(holder.id())) continue;
                late++;
                JsonElement j = null;
                try {
                    j = Recipe.CODEC.encodeStart(ops, holder.value()).result().orElse(null);
                } catch (RuntimeException e) {
                    master.error(holder.id().toString(), "encode for the late pass: " + e);
                }
                master.add(classify(holder, j, settings, p.lookup(), p.stations(), master, p.registries(), true));
            }
            int resolved = resolveResults(master, p);
            MASTERS.put(manager, master);
            List<RecipeHolder<?>> live = master.live(p.snap());
            if (!sameRecipes(manager, live)) manager.replaceRecipes(live);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            GateReport report = master.report(p.snap(), p.millis() + ms);
            last = report;
            List<String> unmatched = unmatchedMarkers(p.markers(), master);
            FirmagesCore.LOGGER.info("Recipe gate late pass: {} recipes added after the recipe load ({} dropped), {} dropped by their resolved "
                    + "result ({} IE/Occultism results resolved), {} removed by other code after the load; master {} recipes, {} live. {}",
                late, report.lateDropped(), report.resolvedDropped(), resolved, removed, master.size(), live.size(), report.summary());
            if (!unmatched.isEmpty()) {
                FirmagesCore.LOGGER.warn("Recipe gate: {} requireAge/untilAge markers name no recipe: {}", unmatched.size(),
                    unmatched.subList(0, Math.min(20, unmatched.size())));
            }
        } catch (RuntimeException | LinkageError e) {
            lastFailure = "late pass: " + e;
            MASTERS.remove(manager);
            FirmagesCore.LOGGER.error("Recipe gate late pass failed; recipes added after the recipe load are NOT Age-filtered and the "
                + "Age swap falls back to a datapack reload", e);
        }
    }

    /** Late pass: the IE and Occultism results, resolved now that the tags are bound, become one more output clause. */
    private static int resolveResults(RecipeMaster master, Pass p) {
        int n = 0;
        List<RecipeMaster.Entry> entries = master.entriesMutable();
        for (int i = 0; i < entries.size(); i++) {
            RecipeMaster.Entry e = entries.get(i);
            if (e.late() || e.cls().fixed() != null || !master.settings().enabled()) continue;
            Recipe<?> recipe = e.holder().value();
            String cls = recipe.getClass().getName();
            if (LAZY_RESULT_PACKAGES.stream().noneMatch(cls::startsWith)) continue;
            ItemStack stack;
            try {
                stack = recipe.getResultItem(p.registries());
            } catch (RuntimeException | LinkageError ex) {
                master.error(e.id().toString(), "getResultItem in the late pass: " + ex);
                continue;
            }
            if (stack == null || stack.isEmpty()) continue;
            n++;
            OutputSink sink = new OutputSink();
            ExtractSupport.item(sink, stack);
            GateRules.Clause clause = null;
            for (String item : sink.items()) clause = GateRules.itemClause(item, p.lookup());
            if (clause == null) continue;
            entries.set(i, new RecipeMaster.Entry(e.holder(), e.type(), e.serializer(), e.outputs() + "; resolved result " + sink.describe(),
                e.cls().withResolved(clause), false));
        }
        return n;
    }

    private static List<String> unmatchedMarkers(RecipeMarkers.Snapshot markers, RecipeMaster master) {
        if (markers.requireAges().isEmpty() && markers.untilAges().isEmpty()) return List.of();
        Set<String> ids = new HashSet<>(master.size() * 2);
        for (RecipeMaster.Entry e : master.entries()) ids.add(e.id().toString());
        List<String> out = new ArrayList<>();
        for (String id : markers.requireAges().keySet()) if (!ids.contains(id)) out.add("requireAge " + id);
        for (String id : markers.untilAges().keySet()) if (!ids.contains(id)) out.add("untilAge " + id);
        out.sort(null);
        return out;
    }

    // ---------------------------------------------------------------- Age swap

    /**
     * What one {@link #apply} changed. {@code foreign}: recipes in the manager that the master does not know (code added
     * them after the load); the swap keeps them as they are. {@code live} is the new recipe list.
     */
    public record Applied(int master, int before, int after, int added, int removed, int foreign, long millis, GateReport report,
                          List<RecipeHolder<?>> live) {}

    /**
     * Re-filters the master of {@code manager} for {@code snap} and replaces the live recipes (the Age swap). Server
     * thread. Null when this load has no master (the gate failed): the caller reloads the datapacks instead.
     */
    public static Applied apply(RecipeManager manager, AgeSnapshot snap) {
        RecipeMaster master = MASTERS.get(manager);
        if (master == null) return null;
        long t0 = System.nanoTime();
        Collection<RecipeHolder<?>> before = manager.getRecipes();
        Set<ResourceLocation> beforeIds = new HashSet<>(before.size() * 2);
        for (RecipeHolder<?> h : before) beforeIds.add(h.id());
        List<RecipeHolder<?>> live = master.live(snap);
        int added = 0;
        for (RecipeHolder<?> h : live) if (!beforeIds.contains(h.id())) added++;
        int kept = live.size() - added;
        Set<ResourceLocation> known = master.ids();
        int foreign = 0;
        for (RecipeHolder<?> h : before) {
            if (!known.contains(h.id())) {
                live.add(h);
                foreign++;
            }
        }
        int removed = before.size() - foreign - kept;
        manager.replaceRecipes(live);
        long filterMs = (System.nanoTime() - t0) / 1_000_000L;
        GateReport report = master.report(snap, filterMs);
        last = report;
        return new Applied(master.size(), before.size(), live.size(), added, removed, foreign, filterMs, report, live);
    }

    /** True when the manager holds exactly these holder objects (an Age swap's result after other code ran). */
    public static boolean sameRecipes(RecipeManager manager, List<RecipeHolder<?>> live) {
        Collection<RecipeHolder<?>> now = manager.getRecipes();
        if (now.size() != live.size()) return false;
        for (RecipeHolder<?> h : live) {
            if (manager.byKey(h.id()).orElse(null) != h) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- extraction

    private static void extract(Recipe<?> recipe, OutputSink sink, HolderLookup.Provider registries, RecipeMaster master, String id) {
        for (OutputExtractor e : extractors()) {
            try {
                if (e.extract(recipe, sink, registries)) return;
            } catch (Exception | LinkageError ex) {
                master.error(id, e.name() + " extractor: " + ex);
                return;
            }
        }
        String cls = recipe.getClass().getName();
        if (LAZY_RESULT_PACKAGES.stream().anyMatch(cls::startsWith)) return;
        try {
            ExtractSupport.item(sink, recipe.getResultItem(registries));
        } catch (RuntimeException | LinkageError ex) {
            master.error(id, "getResultItem: " + ex);
        }
    }

    private static List<OutputExtractor> extractors() {
        List<OutputExtractor> list = extractors;
        if (list == null) {
            // Each extractor class is only touched inside its isLoaded branch: a constructor reference
            // (CreateExtractor::new) would link, and so verify, the class even when the mod is absent.
            List<OutputExtractor> out = new ArrayList<>();
            ModList mods = ModList.get();
            try {
                if (mods.isLoaded("create")) out.add(new CreateExtractor());
            } catch (LinkageError e) {
                extractorMismatch("create", e);
            }
            try {
                if (mods.isLoaded("immersiveengineering")) out.add(new IEExtractor());
            } catch (LinkageError e) {
                extractorMismatch("immersiveengineering", e);
            }
            try {
                if (mods.isLoaded("mekanism")) out.add(new MekanismExtractor());
            } catch (LinkageError e) {
                extractorMismatch("mekanism", e);
            }
            try {
                if (mods.isLoaded("occultism")) out.add(new OccultismExtractor());
            } catch (LinkageError e) {
                extractorMismatch("occultism", e);
            }
            list = List.copyOf(out);
            extractors = list;
            FirmagesCore.LOGGER.info("Recipe gate extractors: {}", list.stream().map(OutputExtractor::name).toList());
        }
        return list;
    }

    private static void extractorMismatch(String modId, LinkageError e) {
        FirmagesCore.LOGGER.error("Recipe gate: the {} extractor does not match the loaded mod; its recipes use the JSON walk only", modId, e);
    }

    private static GateRules.Lookup lookup(AgeIndex idx, TagView tags) {
        Map<String, AgeId> items = idx.itemIds();
        Map<String, AgeId> fluids = idx.fluidIds();
        Set<String> disabledItems = idx.disabledItemIds();
        Set<String> disabledFluids = idx.disabledFluidIds();
        return new GateRules.Lookup() {
            public AgeId itemAge(String id) { return items.get(id); }
            public AgeId fluidAge(String id) { return fluids.get(id); }
            public boolean itemDisabled(String id) { return disabledItems.contains(id); }
            public boolean fluidDisabled(String id) { return disabledFluids.contains(id); }
            public Collection<String> itemTag(String tagId) { return tags.itemTag(tagId); }
            public Collection<String> fluidTag(String tagId) { return tags.fluidTag(tagId); }
        };
    }

    /** gate.* settings; read from the file during the initial load, before NeoForge loads the server config. */
    static GateRules.Settings settings() {
        if (ServerConfig.loaded()) {
            return new GateRules.Settings(ServerConfig.GATE_ENABLED.get(), Set.copyOf(ServerConfig.ALLOW_RECIPES.get()),
                Set.copyOf(ServerConfig.DENY_RECIPES.get()), Set.copyOf(ServerConfig.EXEMPT_RECIPE_TYPES.get()));
        }
        Path w = EarlyServerConfig.dedicatedWorldDir();
        return new GateRules.Settings(
            EarlyServerConfig.bool(w, "gate.enabled").orElse(true),
            Set.copyOf(EarlyServerConfig.stringList(w, "gate.allowRecipes").orElse(List.of())),
            Set.copyOf(EarlyServerConfig.stringList(w, "gate.denyRecipes").orElse(List.of())),
            Set.copyOf(EarlyServerConfig.stringList(w, "gate.exemptRecipeTypes").orElse(ServerConfig.DEFAULT_EXEMPT_TYPES)));
    }
}
