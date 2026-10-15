package dev.firmages.core.command;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.crown.ChordLibrary;
import dev.firmages.core.crown.CrownRegistry;
import dev.firmages.core.crown.CrownService;
import dev.firmages.core.crown.StarforgeRecipe;
import dev.firmages.core.gate.RecipeGate;
import dev.firmages.core.gate.RecipeMaster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import static dev.firmages.core.command.SelfTest.check;

/**
 * Suite {@code crown} (SPEC §27.13 P, §24.7 criteria 1 and 4): the chord library, the crown multiblock, the 22 library
 * recipes, one starforge recipe per alloy and no alloy from anywhere else, the live count after age_9, and the eight
 * V4A recipe changes of §27.8 holding their alloys. The library recipes load only once every sky material exists
 * (their {@code neoforge:conditions}), and the V4A changes are pack data (M15); until then those cases fail and say why.
 */
public final class CrownSuite implements SelfTest.Suite {
    public static final List<String> LIBRARY = List.of("starlit_iron", "cloudsteel", "arcane_lens", "aurora_steel", "industrial_lens", "halo_steel",
        "electric_lens", "void_steel", "information_lens", "quasar_alloy", "space_lens", "firmament_alloy", "quantum_lens",
        "blessing_shard_0", "blessing_shard_1", "blessing_shard_2", "blessing_shard_3", "blessing_shard_4", "blessing_shard_5",
        "blessing_shard_6", "blessing_shard_7", "blessing_shard_8");
    /** SPEC §27.8: recipe id -> the alloy it must hold. */
    public static final Map<String, String> V4A = new java.util.LinkedHashMap<>();

    static {
        V4A.put("firmages:sequenced_assembly/arcane_keystone", "starlit_iron");
        V4A.put("firmages:ritual/arcane_keystone", "starlit_iron");
        V4A.put("firmages:crafting/pressure_core", "cloudsteel");
        V4A.put("firmages:arc_furnace/humming_core", "aurora_steel");
        V4A.put("firmages:crafting/data_matrix", "halo_steel");
        V4A.put("firmages:fusion/star_chart", "void_steel");
        V4A.put("firmages:fusion/quantum_core", "quasar_alloy");
        V4A.put("firmages:fusion/ultimate_singularity", "firmament_alloy");
    }

    private final MinecraftServer server;

    public CrownSuite(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String name() {
        return "crown";
    }

    /** Every recipe of this load, whatever its Age (the gate's master list), else the live ones. */
    private List<RecipeHolder<?>> allRecipes() {
        RecipeMaster m = RecipeGate.master(server.getRecipeManager());
        if (m != null) return m.entries().stream().<RecipeHolder<?>>map(RecipeMaster.Entry::holder).toList();
        return new ArrayList<>(server.getRecipeManager().getRecipes());
    }

    private static boolean isStarforge(RecipeHolder<?> h) {
        return h.value() instanceof StarforgeRecipe;
    }

    @Override
    public void run(SelfTest.Recorder r) {
        r.test("chord_library", () -> {
            ChordLibrary lib = ChordLibrary.current();
            check(lib.errors().isEmpty(), "rejected: " + lib.errors());
            check(lib.chords().size() == 14, "14 chords expected, got " + lib.chords().size());
            return lib.chords().size() + " chords";
        });
        r.test("multiblock", () -> {
            check(ShrineMultiblocks.get(CrownService.CROWN_ID).isPresent(), CrownService.CROWN_ID + " not loaded");
            ShrineMultiblocks.RingCheck c = CrownService.check(server.overworld(), new BlockPos(0, server.overworld().getMaxBuildHeight() - 4, 0));
            check(c.total() == 53, "53 counted positions expected, got " + c.total());
            return "53 positions";
        });
        r.test("library_loaded", () -> {
            TreeSet<String> loaded = new TreeSet<>();
            for (RecipeHolder<?> h : allRecipes()) {
                if (isStarforge(h) && h.id().getNamespace().equals("firmages")) loaded.add(h.id().getPath());
            }
            List<String> missing = LIBRARY.stream().filter(n -> !loaded.contains("starforge/" + n)).toList();
            check(missing.isEmpty(), (LIBRARY.size() - missing.size()) + "/22 library recipes loaded; skipped by their conditions (sky material"
                + " missing): " + missing);
            check(loaded.size() <= 25, "more than 25 starforge recipes: " + loaded.size());
            return "22 recipes";
        });
        r.test("one_recipe_per_alloy", () -> {
            List<String> problems = new ArrayList<>();
            Map<ResourceLocation, Integer> forged = new java.util.HashMap<>();
            java.util.Set<ResourceLocation> alloys = new java.util.HashSet<>();
            CrownRegistry.ALLOY_NAMES.forEach(a -> alloys.add(ResourceLocation.fromNamespaceAndPath("firmages", a + "_ingot")));
            for (RecipeHolder<?> h : allRecipes()) {
                ResourceLocation out;
                try {
                    out = BuiltInRegistries.ITEM.getKey(h.value().getResultItem(server.registryAccess()).getItem());
                } catch (RuntimeException e) {
                    continue;
                }
                if (!alloys.contains(out)) continue;
                if (isStarforge(h)) forged.merge(out, 1, Integer::sum);
                else problems.add(h.id() + " makes " + out + " outside the Lens Crown");
            }
            forged.forEach((id, n) -> {
                if (n > 1) problems.add(id + " has " + n + " starforge recipes");
            });
            check(problems.isEmpty(), String.join("; ", problems));
            return "no alloy outside firmages:starforge, at most one recipe each";
        });
        r.test("live_after_age_9", () -> {
            if (!AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9)) return "skipped: age_9 is not unlocked (ages simulate grant age_9 first)";
            long live = server.getRecipeManager().getAllRecipesFor(CrownRegistry.STARFORGE_TYPE.get()).stream()
                .filter(h -> h.id().getNamespace().equals("firmages")).count();
            check(live == 22, "22 live starforge recipes expected in age_9, got " + live);
            return "22 live";
        });
        r.test("v4a_chain", () -> {
            RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            List<String> problems = new ArrayList<>();
            for (Map.Entry<String, String> e : V4A.entrySet()) {
                Optional<RecipeHolder<?>> h = allRecipes().stream().filter(x -> x.id().toString().equals(e.getKey())).findFirst();
                if (h.isEmpty()) {
                    problems.add(e.getKey() + " missing");
                    continue;
                }
                String json = Recipe.CODEC.encodeStart(ops, h.get().value()).result().map(Object::toString).orElse("");
                String alloy = "firmages:" + e.getValue() + "_ingot";
                int age = 3 + CrownRegistry.ALLOY_NAMES.indexOf(e.getValue());
                if (!json.contains(alloy) && !json.contains("firmages:celestial_alloy/age_" + age)) problems.add(e.getKey() + " lacks " + alloy);
            }
            check(problems.isEmpty(), "V4A (pack data, M15): " + String.join("; ", problems));
            return V4A.size() + " recipes hold their alloys";
        });
    }
}
