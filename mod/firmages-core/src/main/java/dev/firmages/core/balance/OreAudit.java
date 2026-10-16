package dev.firmages.core.balance;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.gate.RecipeGate;
import dev.firmages.core.gate.RecipeMaster;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * {@code /firmages ore audit} (SPEC §38): the ore ladder of the live server. Every recipe the server knows (all Ages:
 * the recipe gate's master list when the gate ran, else the recipe manager) is encoded to JSON with its own codec and
 * measured by {@link OreLadder} against the metal table {@code data/firmages/balance/metals.json} (the build copies
 * {@code kubejs/data/firmages/balance/metals.json} into the jar; a datapack may override it). Same logic as the L- checks of
 * {@code dev/poc_analyze.py}, so the numbers match the offline check. Written to {@code logs/firmages-ore-audit.json}.
 */
public final class OreAudit {
    public static final ResourceLocation TABLE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "balance/metals.json");

    /** The audit's findings: the report lines, the per-Age numbers, the ladder faults. */
    public record Result(List<String> lines, Map<String, double[]> perAge, List<String> table, List<String> stepsBack, List<String> magic,
                         List<OreLadder.Measure> unlisted,
                         int recipes, int measured, Path file) {}

    private OreAudit() {}

    public static Optional<JsonObject> table(MinecraftServer server) {
        Optional<Resource> r = server.getResourceManager().getResource(TABLE);
        if (r.isEmpty()) return Optional.empty();
        try (Reader reader = r.get().openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            FirmagesCore.LOGGER.warn("Ore audit: cannot read {}", TABLE, e);
            return Optional.empty();
        }
    }

    /** Every recipe of every Age as JSON (id to JSON with its {@code type}); a recipe its codec cannot encode is skipped. */
    public static Map<String, JsonObject> recipes(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        List<RecipeHolder<?>> holders = new ArrayList<>();
        RecipeMaster master = RecipeGate.master(manager);
        if (master != null) {
            for (RecipeMaster.Entry e : master.entries()) holders.add(e.holder());
        } else {
            holders.addAll(manager.getRecipes());
        }
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        Map<String, JsonObject> out = new LinkedHashMap<>();
        int failed = 0;
        for (RecipeHolder<?> h : holders) {
            try {
                JsonElement j = Recipe.CODEC.encodeStart(ops, h.value()).result().orElse(null);
                if (j instanceof JsonObject o) out.put(h.id().toString(), o);
                else failed++;
            } catch (RuntimeException e) {
                failed++;
            }
        }
        if (failed > 0) FirmagesCore.LOGGER.debug("Ore audit: {} recipes without a JSON encoding skipped", failed);
        return out;
    }

    public static Optional<Result> run(MinecraftServer server) {
        Optional<JsonObject> t = table(server);
        if (t.isEmpty()) return Optional.empty();
        OreLadder ladder = new OreLadder(t.get());
        Map<String, JsonObject> recipes = recipes(server);
        List<OreLadder.Measure> measures = ladder.measure(recipes);
        List<OreLadder.Entry> entries = ladder.entries(measures);
        Map<String, double[]> perAge = ladder.perAge(entries);
        List<String> table = ladder.tableDeviations(measures);
        List<String> back = ladder.stepsBack(entries);
        List<String> magic = ladder.magicAbove(entries);
        List<OreLadder.Measure> unlisted = ladder.unlisted(measures);

        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "Ore audit: %d recipes, %d ore-piece measurements, %d ore pieces in the metal table",
            recipes.size(), measures.size(), ladder.pieces().size()));
        for (Map.Entry<String, double[]> e : perAge.entrySet()) {
            double[] v = e.getValue();
            lines.add(String.format(Locale.ROOT, "  %s: best tech factor %.3f to %.3f over %d pieces, best magic %.3f",
                e.getKey(), v[0], v[1], (int) v[3], v[2]));
        }
        Map<String, double[]> perStation = new TreeMap<>();
        for (OreLadder.Entry e : entries) {
            perStation.merge(e.station(), new double[] {e.factor(), e.factor()}, (a, b) -> new double[] {Math.min(a[0], b[0]), Math.max(a[1], b[1])});
        }
        for (Map.Entry<String, OreLadder.Station> s : ladder.stations().entrySet()) {
            double[] f = perStation.get(s.getKey());
            OreLadder.Station st = s.getValue();
            lines.add(f == null
                ? String.format(Locale.ROOT, "  station %s (%s, %s, %s): no recipe measured, target %.2f", st.key(), st.type(), st.age(), st.kind(), st.factor())
                : String.format(Locale.ROOT, "  station %s (%s, %s, %s): %.3f to %.3f, target %.2f", st.key(), st.type(), st.age(), st.kind(), f[0], f[1], st.factor()));
        }
        lines.add(table.isEmpty() ? "  L-1 table: every station within the tolerance" : "  L-1 table: " + table.size() + " deviation(s), first: " + table.get(0));
        lines.add(back.isEmpty() ? "  L-2 ladder: no tech step back" : "  L-2 ladder: " + back.size() + " tech step(s) back, first: " + back.get(0));
        lines.add(magic.isEmpty() ? "  L-3 magic: never above the next tech step" : "  L-3 magic: " + magic.size() + " above the next tech step, first: " + magic.get(0));
        lines.add(unlisted.isEmpty() ? "  L-4 no unlisted recipe type multiplies ore"
            : String.format(Locale.ROOT, "  L-4 %d recipe(s) of unlisted types multiply ore, first: %s (%s) %.3f on %s", unlisted.size(),
                unlisted.get(0).recipe(), unlisted.get(0).type(), unlisted.get(0).factor(), unlisted.get(0).piece()));

        JsonObject json = new JsonObject();
        json.addProperty("recipes", recipes.size());
        json.addProperty("measurements", measures.size());
        JsonObject ages = new JsonObject();
        perAge.forEach((age, v) -> {
            JsonObject o = new JsonObject();
            o.addProperty("tech_min", v[0]);
            o.addProperty("tech_max", v[1]);
            o.addProperty("magic_max", v[2]);
            o.addProperty("pieces", (int) v[3]);
            ages.add(age, o);
        });
        json.add("per_age", ages);
        JsonArray best = new JsonArray();
        for (OreLadder.Entry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("piece", e.piece());
            o.addProperty("station", e.station());
            o.addProperty("age", e.age());
            o.addProperty("kind", e.kind());
            o.addProperty("factor", e.factor());
            best.add(o);
        }
        json.add("entries", best);
        JsonArray tb = new JsonArray();
        table.forEach(tb::add);
        json.add("table_deviations", tb);
        JsonArray b = new JsonArray();
        back.forEach(b::add);
        json.add("steps_back", b);
        JsonArray mg = new JsonArray();
        magic.forEach(mg::add);
        json.add("magic_above", mg);
        JsonArray u = new JsonArray();
        for (OreLadder.Measure m : unlisted) u.add(m.recipe() + " (" + m.type() + ") " + m.factor() + " on " + m.piece());
        json.add("unlisted", u);
        Path file = server.getServerDirectory().resolve("logs").resolve("firmages-ore-audit.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FirmagesCore.LOGGER.warn("Ore audit: cannot write {}", file, e);
        }
        return Optional.of(new Result(List.copyOf(lines), perAge, table, back, magic, unlisted, recipes.size(), measures.size(), file));
    }
}
