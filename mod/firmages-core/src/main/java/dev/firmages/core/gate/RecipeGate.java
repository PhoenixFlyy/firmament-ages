package dev.firmages.core.gate;

import com.google.gson.JsonElement;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * m2, the machine-recipe Age gate (SPEC §4): called from {@code RecipeManagerMixin} at the TAIL of
 * {@code RecipeManager#apply} with the post-KubeJS recipe JSON. Classifies every recipe by its outputs
 * ({@link OutputExtractor}s, {@code getResultItem}, {@link JsonOutputWalker}), applies {@link GateRules} with the
 * Age snapshot of the load (§3.2, fail-strict at boot) and replaces the recipe list with the kept recipes, which
 * rebuilds {@code byName} and {@code byType}; every machine, cache and the client sync then follow.
 * <p>Some mods add recipes after {@code RecipeManager#apply}: Create Dragons Plus 1.11.9 generates its sandpaper
 * polishing recipes at the TAIL of {@code ReloadableServerResources#updateRegistryTags} and writes them straight into
 * {@code byType}/{@code byName}. {@link #lateFilter} runs after that ({@code ReloadableServerResourcesLateMixin}) and
 * gates every recipe the main pass did not see, with the same rules, snapshot and report; their JSON comes from
 * {@code Recipe.CODEC}.
 * <p>The late pass also resolves the result of the IE and Occultism recipes the main pass kept. Their tag outputs
 * are only read as tags during the load (their getResultItem caches its first answer, and tags are not bound
 * yet), and a tag with an unlocked member keeps the recipe. But the mod makes the one item its tag resolution
 * picks: Occultism's crusher turns {@code #c:dusts/charcoal} into {@code mekanism:dust_charcoal}. Once the tags are
 * bound the cached answer is the real one, so a recipe whose resolved result is locked is dropped as well.
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
    private record Pass(GateRules.Settings settings, GateRules.Lookup lookup, AgeSnapshot snap, GateReport report,
                        Set<ResourceLocation> seen, HolderLookup.Provider registries) {}

    private static final Map<RecipeManager, Pass> PASSES = Collections.synchronizedMap(new WeakHashMap<>());

    private RecipeGate() {}

    /** Called from {@code ReloadableServerResourcesMixin} when the resources of a datapack load are created. */
    public static void link(RecipeManager recipes, TagManager tags) {
        LINKS.put(recipes, tags);
    }

    /** Report of the last successful filter run, or null before the first. */
    public static GateReport lastReport() {
        return last;
    }

    /** Why the last filter run failed (its load kept every recipe), or null if it succeeded. */
    public static String lastFailure() {
        return lastFailure;
    }

    public static void filter(RecipeManager manager, Map<ResourceLocation, JsonElement> json, ResourceManager resources,
                              HolderLookup.Provider registries) {
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
        GateReport report = new GateReport(Instant.now(), snap.unlockedIds(), settings.enabled(), idx.misconfigured(), tags.source());
        if (idx.report() != null && !idx.report().itemTags().missingTags().isEmpty()) {
            report.note("no firmages:age_items tag for " + idx.report().itemTags().missingTags() + ": their items count as unlocked");
        }
        GateRules.Lookup lookup = lookup(idx, tags);
        Collection<RecipeHolder<?>> all = manager.getRecipes();
        List<RecipeHolder<?>> kept = new ArrayList<>(all.size());
        Set<ResourceLocation> seen = new HashSet<>(all.size() * 2);
        for (RecipeHolder<?> holder : all) {
            seen.add(holder.id());
            if (decide(holder, json.get(holder.id()), settings, lookup, snap, report, registries) || !settings.enabled()) kept.add(holder);
        }
        if (kept.size() != all.size()) manager.replaceRecipes(kept);
        report.finish((System.nanoTime() - t0) / 1_000_000L);
        PASSES.put(manager, new Pass(settings, lookup, snap, report, seen, registries));
        last = report;
        FirmagesCore.LOGGER.info(report.summary());
        report.notes().forEach(n -> FirmagesCore.LOGGER.error("Recipe gate: {}", n));
        if (report.misconfigured()) {
            FirmagesCore.LOGGER.error("Recipe gate: all firmages:age_* tags are empty, so no recipe is known to belong to a locked Age");
        }
    }

    /** Classifies one recipe, records it in the report and returns whether the rules keep it. */
    private static boolean decide(RecipeHolder<?> holder, JsonElement j, GateRules.Settings settings, GateRules.Lookup lookup,
                                  AgeSnapshot snap, GateReport report, HolderLookup.Provider registries) {
        Recipe<?> recipe = holder.value();
        String id = holder.id().toString();
        String type = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
        String serializer = String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()));
        GateRules.Verdict v = GateRules.byConfig(id, type, serializer, settings);
        String outputs = "(not examined)";
        if (v == null) {
            OutputSink sink = new OutputSink();
            extract(recipe, sink, registries, report, id);
            if (j != null) JsonOutputWalker.walk(j, sink);
            v = GateRules.decide(id, type, serializer, sink, settings, lookup, snap);
            outputs = sink.describe();
        }
        report.record(id, type, serializer, v, outputs);
        return v.keep();
    }

    /**
     * The late pass: gates the recipes that code added after the main pass of this load (see the class comment).
     * Called at the TAIL of {@code ReloadableServerResources#updateRegistryTags}, after the tags are bound.
     */
    public static void lateFilter(RecipeManager manager) {
        Pass p = PASSES.remove(manager);
        if (p == null) return;
        try {
            long t0 = System.nanoTime();
            Collection<RecipeHolder<?>> all = manager.getRecipes();
            List<RecipeHolder<?>> kept = new ArrayList<>(all.size());
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, p.registries());
            int late = 0;
            int resolved = 0;
            for (RecipeHolder<?> holder : all) {
                if (p.seen().contains(holder.id())) {
                    if (resolvedLocked(holder, p)) resolved++;
                    else kept.add(holder);
                    continue;
                }
                late++;
                JsonElement j = null;
                try {
                    j = Recipe.CODEC.encodeStart(ops, holder.value()).result().orElse(null);
                } catch (RuntimeException e) {
                    p.report().error(holder.id().toString(), "encode for the late pass: " + e);
                }
                if (decide(holder, j, p.settings(), p.lookup(), p.snap(), p.report(), p.registries()) || !p.settings().enabled()) {
                    kept.add(holder);
                }
            }
            if (late == 0 && resolved == 0) return;
            int dropped = all.size() - kept.size();
            if (dropped > 0) manager.replaceRecipes(kept);
            p.report().late(late, dropped - resolved, resolved, (System.nanoTime() - t0) / 1_000_000L);
            FirmagesCore.LOGGER.info("Recipe gate late pass: {} recipes added after the recipe load ({} dropped), {} dropped by their resolved result. {}",
                late, dropped - resolved, resolved, p.report().summary());
        } catch (RuntimeException | LinkageError e) {
            lastFailure = "late pass: " + e;
            FirmagesCore.LOGGER.error("Recipe gate late pass failed; recipes added after the recipe load are NOT Age-filtered", e);
        }
    }

    /** Late pass, IE and Occultism recipes the main pass kept: true (and reclassified) if the resolved result is locked. */
    private static boolean resolvedLocked(RecipeHolder<?> holder, Pass p) {
        if (!p.settings().enabled()) return false;
        Recipe<?> recipe = holder.value();
        String cls = recipe.getClass().getName();
        if (LAZY_RESULT_PACKAGES.stream().noneMatch(cls::startsWith)) return false;
        String id = holder.id().toString();
        GateReport.Entry e = p.report().entry(id);
        if (e == null || !e.verdict().keep() || e.verdict().reason() == GateRules.Reason.ALLOWED
            || e.verdict().reason() == GateRules.Reason.EXEMPT) return false;
        ItemStack stack;
        try {
            stack = recipe.getResultItem(p.registries());
        } catch (RuntimeException | LinkageError ex) {
            p.report().error(id, "getResultItem in the late pass: " + ex);
            return false;
        }
        if (stack == null || stack.isEmpty()) return false;
        OutputSink sink = new OutputSink();
        ExtractSupport.item(sink, stack);
        GateRules.Verdict v = GateRules.decide(id, e.type(), e.serializer(), sink, p.settings(), p.lookup(), p.snap());
        if (v.keep()) return false;
        p.report().reclassify(id, v, e.outputs() + "; resolved result " + sink.describe());
        return true;
    }

    private static void extract(Recipe<?> recipe, OutputSink sink, HolderLookup.Provider registries, GateReport report, String id) {
        for (OutputExtractor e : extractors()) {
            try {
                if (e.extract(recipe, sink, registries)) return;
            } catch (Exception | LinkageError ex) {
                report.error(id, e.name() + " extractor: " + ex);
                return;
            }
        }
        String cls = recipe.getClass().getName();
        if (LAZY_RESULT_PACKAGES.stream().anyMatch(cls::startsWith)) return;
        try {
            ExtractSupport.item(sink, recipe.getResultItem(registries));
        } catch (RuntimeException | LinkageError ex) {
            report.error(id, "getResultItem: " + ex);
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
