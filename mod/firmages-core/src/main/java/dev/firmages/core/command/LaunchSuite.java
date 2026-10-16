package dev.firmages.core.command;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.gate.RecipeGate;
import dev.firmages.core.gate.RecipeMaster;
import dev.firmages.core.launch.LaunchControlBlockEntity;
import dev.firmages.core.launch.LaunchData;
import dev.firmages.core.launch.LaunchDataLoader;
import dev.firmages.core.launch.LaunchRegistry;
import dev.firmages.core.launch.LaunchService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.firmages.core.command.SelfTest.check;

/**
 * Suite {@code launch} (SPEC §31.10 P, §24.7): the fuel and module data, the fuel tag in the pack, the returns (sky
 * materials only, no recipe makes them), the complex multiblock and its block tags, and the module recipes with the
 * deadlock rule (the Space Age payload needs no Space Age alloy). The recipes are pack data (M15); until then that case
 * fails and says so.
 */
public final class LaunchSuite implements SelfTest.Suite {
    /** Counted positions of {@code firmages:launch_complex}: the 5 by 5 pad, the control and the gantry frame. */
    public static final int COUNTED = 25 + 1 + 4 * 20 + 4;

    private final MinecraftServer server;

    public LaunchSuite(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String name() {
        return "launch";
    }

    private List<RecipeHolder<?>> allRecipes() {
        RecipeMaster m = RecipeGate.master(server.getRecipeManager());
        if (m != null) return m.entries().stream().<RecipeHolder<?>>map(RecipeMaster.Entry::holder).toList();
        return new ArrayList<>(server.getRecipeManager().getRecipes());
    }

    @Override
    public void run(SelfTest.Recorder r) {
        r.test("data", () -> {
            LaunchData d = LaunchDataLoader.current();
            check(d.valid(), "fuel.json not loaded: " + d.errors());
            check(d.errors().isEmpty(), "rejected: " + d.errors());
            check(d.module("firmages:sampler_module").isPresent(), "the sampler module has no module file");
            check(d.module("firmages:deep_space_module").isPresent(), "the deep-space module has no module file");
            return "fuel #" + d.fuel().get().fluidTag() + ", " + d.fuel().get().launchMb() + " mB, " + d.steps().size() + " steps; modules " + d.modules().keySet();
        });
        r.test("fuel_tag", () -> {
            TagKey<Fluid> tag = LaunchControlBlockEntity.fuelTag();
            check(tag != null, "no fuel tag");
            List<String> fluids = new ArrayList<>();
            BuiltInRegistries.FLUID.getTagOrEmpty(tag).forEach(h -> fluids.add(BuiltInRegistries.FLUID.getKey(h.value()).toString()));
            check(!fluids.isEmpty(), "#" + tag.location() + " holds no fluid");
            if (ModList.get().isLoaded("immersivepetroleum")) {
                check(fluids.contains("immersivepetroleum:diesel") && fluids.contains("immersivepetroleum:kerosene"), "diesel and kerosene missing: " + fluids);
            }
            return fluids.size() + " fluids: " + fluids;
        });
        r.test("returns_sky_only", () -> {
            Set<String> sky = Set.of("firmages:voidglass", "firmages:deep_space_sample");
            List<String> bad = new ArrayList<>();
            for (LaunchData.Module m : LaunchDataLoader.current().modules().values()) {
                for (LaunchData.Return ret : m.returns()) if (!sky.contains(ret.item())) bad.add(m.file() + " returns " + ret.item());
            }
            check(bad.isEmpty(), String.join("; ", bad));
            return "voidglass and deep-space samples only";
        });
        r.test("one_source", () -> {
            Set<Item> sky = Set.of(LaunchRegistry.VOIDGLASS.get(), LaunchRegistry.DEEP_SPACE_SAMPLE.get());
            List<String> bad = new ArrayList<>();
            for (RecipeHolder<?> h : allRecipes()) {
                ItemStack out;
                try {
                    out = h.value().getResultItem(server.registryAccess());
                } catch (RuntimeException e) {
                    continue;
                }
                if (out != null && sky.contains(out.getItem())) bad.add(h.id().toString());
            }
            check(bad.isEmpty(), "recipes that make voidglass or deep-space samples (only probes bring them): " + bad);
            return "no recipe makes them";
        });
        r.test("not_ores", () -> {
            TagKey<Item> ores = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ores"));
            TagKey<Item> raw = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:raw_materials"));
            List<String> bad = new ArrayList<>();
            for (var i : LaunchRegistry.items()) {
                ItemStack s = new ItemStack((Item) i.get());
                if (s.is(ores) || s.is(raw)) bad.add(i.getId().toString());
            }
            check(bad.isEmpty(), "in c:ores or c:raw_materials: " + bad);
            return LaunchRegistry.items().size() + " items";
        });
        r.test("multiblock", () -> {
            check(ShrineMultiblocks.get(LaunchService.COMPLEX_ID).isPresent(), LaunchService.COMPLEX_ID + " not loaded");
            ShrineMultiblocks.RingCheck c = ShrineMultiblocks.check(server.overworld(), new BlockPos(0, server.overworld().getMaxBuildHeight() - 24, 0),
                LaunchService.COMPLEX_ID);
            check(c.total() == COUNTED, COUNTED + " counted positions expected, got " + c.total());
            return COUNTED + " positions";
        });
        r.test("block_tags", () -> {
            List<String> empty = new ArrayList<>();
            for (String t : List.of("firmages:launch/pad", "firmages:launch/rim", "firmages:launch/frame", "firmages:launch/trench")) {
                TagKey<Block> key = TagKey.create(Registries.BLOCK, ResourceLocation.parse(t));
                int n = 0;
                for (var ignored : BuiltInRegistries.BLOCK.getTagOrEmpty(key)) n++;
                if (n == 0) empty.add(t);
            }
            check(empty.isEmpty(), "block tags without a block: " + empty);
            return "pad, rim, frame and trench resolve";
        });
        r.test("module_recipes", () -> {
            RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            List<String> problems = new ArrayList<>();
            for (var target : List.of(LaunchRegistry.PROBE, LaunchRegistry.SAMPLER_MODULE, LaunchRegistry.DEEP_SPACE_MODULE, LaunchRegistry.LAUNCH_CONTROL_ITEM,
                LaunchRegistry.PROBE_CRADLE_ITEM, LaunchRegistry.LAUNCH_PORT_ITEM)) {
                Item item = (Item) target.get();
                List<RecipeHolder<?>> makers = new ArrayList<>();
                for (RecipeHolder<?> h : allRecipes()) {
                    try {
                        if (h.value().getResultItem(server.registryAccess()).is(item)) makers.add(h);
                    } catch (RuntimeException ignored) {
                        // recipes without a plain result
                    }
                }
                if (makers.isEmpty()) {
                    problems.add(target.getId() + " has no recipe");
                    continue;
                }
                // SPEC §31.4 deadlock rule: the age_7 payload never needs the age_7 alloy; the age_8 payload never the age_8 alloy
                String forbidden = item == LaunchRegistry.SAMPLER_MODULE.get() ? "void_steel_ingot|celestial_alloy/age_7"
                    : item == LaunchRegistry.DEEP_SPACE_MODULE.get() ? "quasar_alloy_ingot|celestial_alloy/age_8" : null;
                if (forbidden == null) continue;
                for (RecipeHolder<?> h : makers) {
                    String json = Recipe.CODEC.encodeStart(ops, h.value()).result().map(Object::toString).orElse("");
                    for (String f : forbidden.split("\\|")) if (json.contains(f)) problems.add(h.id() + " needs " + f + " (deadlock with V4A)");
                }
            }
            check(problems.isEmpty(), "recipes (pack data, M15): " + String.join("; ", problems));
            return "every part has a recipe; no payload deadlock";
        });
    }
}
