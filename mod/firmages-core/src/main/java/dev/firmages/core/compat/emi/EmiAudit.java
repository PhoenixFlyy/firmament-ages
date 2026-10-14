package dev.firmages.core.compat.emi;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.immersion.EmiAgeView;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Proof that EMI shows nothing of a locked Age (SPEC §21.4). A GameTest cannot open EMI, so the client checks itself:
 * after every EMI reload that ran {@link FirmagesEmiPlugin} it walks every category, recipe (inputs, outputs,
 * catalysts, tag members), workstation and index stack against the view of that reload and logs one line
 * {@code EMI audit <age>: ...}; any offender logs at WARN with the first 20. {@code /firmages_client emi_audit} writes the
 * full result to {@code logs/firmages-emi-audit-<age>.json}. Runs off the render thread (the baked lists do not
 * change; a new reload replaces them).
 */
public final class EmiAudit {
    private static final int SHOWN = 20;
    private static volatile boolean auditPending;
    private static boolean wasLoaded = true;

    private EmiAudit() {}

    /** From {@link FirmagesEmiPlugin#initialize}: audit once this reload has finished. */
    static void reloadStarted() {
        auditPending = true;
    }

    /** Client tick: EMI finished a reload that ran the plugin, so log the audit line. */
    public static void tick() {
        boolean loaded = EmiReloadManager.isLoaded();
        if (loaded && !wasLoaded && auditPending) {
            auditPending = false;
            EmiAgeView v = FirmagesEmiPlugin.view();
            CompletableFuture.runAsync(() -> log(run(v)), Util.backgroundExecutor()).exceptionally(e -> {
                FirmagesCore.LOGGER.warn("EMI audit failed: {}", e.toString());
                return null;
            });
        }
        wasLoaded = loaded;
    }

    /** {@code /firmages_client emi_audit}: the full audit as JSON. @return the file, or empty when EMI is not loaded */
    public static CompletableFuture<Path> writeJson() {
        if (!EmiReloadManager.isLoaded()) return CompletableFuture.completedFuture(null);
        EmiAgeView v = FirmagesEmiPlugin.view();
        return CompletableFuture.supplyAsync(() -> {
            Result r = run(v);
            Path file = FMLPaths.GAMEDIR.get().resolve("logs").resolve("firmages-emi-audit-" + v.age().id() + ".json");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(r.json(v)),
                    StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            log(r);
            return file;
        }, Util.backgroundExecutor());
    }

    record Category(String id, int recipes, List<String> workstations) {}

    record Result(String age, List<Category> categories, int indexSize, Map<String, Integer> indexByNamespace,
                  Map<String, Integer> indexByType, List<String> lockedIndex, List<String> lockedRecipes,
                  List<String> lockedWorkstations, List<String> lockedCategories) {
        boolean clean() {
            return lockedIndex.isEmpty() && lockedRecipes.isEmpty() && lockedWorkstations.isEmpty() && lockedCategories.isEmpty();
        }

        JsonObject json(EmiAgeView v) {
            JsonObject o = new JsonObject();
            o.addProperty("age", age);
            o.addProperty("view", v.summary());
            o.addProperty("clean", clean());
            JsonArray cats = new JsonArray();
            for (Category c : categories) {
                JsonObject co = new JsonObject();
                co.addProperty("id", c.id);
                co.addProperty("recipes", c.recipes);
                JsonArray ws = new JsonArray();
                c.workstations.forEach(ws::add);
                co.add("workstations", ws);
                cats.add(co);
            }
            o.add("categories", cats);
            JsonObject index = new JsonObject();
            index.addProperty("stacks", indexSize);
            JsonObject byNs = new JsonObject();
            indexByNamespace.forEach(byNs::addProperty);
            index.add("by_namespace", byNs);
            JsonObject byType = new JsonObject();
            indexByType.forEach(byType::addProperty);
            index.add("by_type", byType);
            o.add("index", index);
            JsonObject off = new JsonObject();
            off.add("index_stacks", array(lockedIndex));
            off.add("recipes", array(lockedRecipes));
            off.add("workstations", array(lockedWorkstations));
            off.add("categories", array(lockedCategories));
            o.add("offenders", off);
            return o;
        }
    }

    private static JsonArray array(List<String> list) {
        JsonArray a = new JsonArray();
        list.forEach(a::add);
        return a;
    }

    static Result run(EmiAgeView v) {
        EmiRecipeManager rm = EmiApi.getRecipeManager();
        List<Category> cats = new ArrayList<>();
        List<String> lockedRecipes = new ArrayList<>();
        List<String> lockedWs = new ArrayList<>();
        List<String> lockedCats = new ArrayList<>();
        for (EmiRecipeCategory cat : rm.getCategories()) {
            String catId = cat.getId().toString();
            List<EmiRecipe> recipes = rm.getRecipes(cat);
            List<String> ws = new ArrayList<>();
            for (EmiIngredient w : rm.getWorkstations(cat)) {
                for (EmiStack s : w.getEmiStacks()) {
                    ws.add(name(s));
                    if (FirmagesEmiPlugin.locked(v, s)) lockedWs.add(catId + " <- " + name(s));
                }
            }
            if (recipes.isEmpty()) continue;
            cats.add(new Category(catId, recipes.size(), ws));
            if (v.categoryLocked(catId)) lockedCats.add(catId + " (" + recipes.size() + " recipes)");
            for (EmiRecipe r : recipes) {
                if (FirmagesEmiPlugin.recipeLocked(v, r)) {
                    lockedRecipes.add(id(r) + " (locked mod or loot table)");
                    continue;
                }
                String bad = firstLocked(v, r);
                if (bad != null) lockedRecipes.add(id(r) + " [" + catId + "] " + bad);
            }
        }
        List<EmiStack> index = EmiApi.getIndexStacks();
        List<String> lockedIndex = new ArrayList<>();
        Map<String, Integer> byNs = new TreeMap<>();
        Map<String, Integer> byType = new TreeMap<>();
        for (EmiStack s : index) {
            ResourceLocation id = s.getId();
            byNs.merge(id == null ? "?" : id.getNamespace(), 1, Integer::sum);
            byType.merge(s.getClass().getSimpleName(), 1, Integer::sum);
            if (FirmagesEmiPlugin.locked(v, s)) lockedIndex.add(name(s));
        }
        return new Result(v.age().id(), cats, index.size(), byNs, byType, lockedIndex, lockedRecipes, lockedWs, lockedCats);
    }

    private static String firstLocked(EmiAgeView v, EmiRecipe r) {
        Set<String> found = new LinkedHashSet<>();
        scan(v, r.getInputs(), "in ", found);
        scan(v, r.getCatalysts(), "catalyst ", found);
        for (EmiStack s : r.getOutputs()) if (FirmagesEmiPlugin.locked(v, s)) found.add("out " + name(s));
        return found.isEmpty() ? null : String.join(", ", found);
    }

    private static void scan(EmiAgeView v, List<EmiIngredient> ingredients, String role, Set<String> found) {
        if (ingredients == null) return;
        for (EmiIngredient ing : ingredients) {
            for (EmiStack s : ing.getEmiStacks()) if (FirmagesEmiPlugin.locked(v, s)) found.add(role + name(s));
        }
    }

    private static String id(EmiRecipe r) {
        return r.getId() == null ? "<no id>" : r.getId().toString();
    }

    private static String name(EmiStack s) {
        ResourceLocation id = s.getId();
        return id == null ? s.getClass().getSimpleName() : id.toString();
    }

    private static void log(Result r) {
        List<String> ids = new ArrayList<>();
        for (Category c : r.categories) ids.add(c.id);
        String line = String.format("EMI audit %s: %d categories %s, index %d stacks, locked stacks visible %d, recipes with a locked "
                + "stack %d, locked workstations %d, locked categories %d", r.age, r.categories.size(), ids, r.indexSize,
            r.lockedIndex.size(), r.lockedRecipes.size(), r.lockedWorkstations.size(), r.lockedCategories.size());
        if (r.clean()) {
            FirmagesCore.LOGGER.info(line);
            return;
        }
        FirmagesCore.LOGGER.warn(line);
        warnFirst("locked index stacks", r.lockedIndex);
        warnFirst("recipes with a locked stack", r.lockedRecipes);
        warnFirst("locked workstations", r.lockedWorkstations);
        warnFirst("locked categories", r.lockedCategories);
    }

    private static void warnFirst(String what, List<String> list) {
        if (list.isEmpty()) return;
        FirmagesCore.LOGGER.warn("EMI audit {}: {} (first {}): {}", what, list.size(), Math.min(SHOWN, list.size()),
            list.subList(0, Math.min(SHOWN, list.size())));
    }
}
