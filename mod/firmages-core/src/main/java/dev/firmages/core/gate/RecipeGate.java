package dev.firmages.core.gate;

import com.google.gson.JsonElement;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
 */
public final class RecipeGate {
    /** Recipe managers of live {@code ReloadableServerResources}, mapped to the tag manager of the same load. */
    private static final Map<RecipeManager, TagManager> LINKS = Collections.synchronizedMap(new WeakHashMap<>());
    /** Recipe classes whose {@code getResultItem} resolves a tag and caches it: never call it during the load. */
    private static final List<String> LAZY_RESULT_PACKAGES = List.of("blusunrize.immersiveengineering.", "com.klikli_dev.occultism.");

    private static volatile GateReport last;
    private static volatile String lastFailure;
    private static volatile List<OutputExtractor> extractors;

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
        for (RecipeHolder<?> holder : all) {
            Recipe<?> recipe = holder.value();
            String id = holder.id().toString();
            String type = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
            String serializer = String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()));
            GateRules.Verdict v = GateRules.byConfig(id, type, serializer, settings);
            String outputs = "(not examined)";
            if (v == null) {
                OutputSink sink = new OutputSink();
                extract(recipe, sink, registries, report, id);
                JsonElement j = json.get(holder.id());
                if (j != null) JsonOutputWalker.walk(j, sink);
                v = GateRules.decide(id, type, serializer, sink, settings, lookup, snap);
                outputs = sink.describe();
            }
            report.record(id, type, serializer, v, outputs);
            if (v.keep() || !settings.enabled()) kept.add(holder);
        }
        if (kept.size() != all.size()) manager.replaceRecipes(kept);
        report.finish((System.nanoTime() - t0) / 1_000_000L);
        last = report;
        FirmagesCore.LOGGER.info(report.summary());
        report.notes().forEach(n -> FirmagesCore.LOGGER.error("Recipe gate: {}", n));
        if (report.misconfigured()) {
            FirmagesCore.LOGGER.error("Recipe gate: all firmages:age_* tags are empty, so no recipe is known to belong to a locked Age");
        }
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
