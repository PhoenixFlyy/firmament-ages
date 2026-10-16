package dev.firmages.core.command;

import dev.firmages.core.sky.HarvestRegistry;
import dev.firmages.core.sky.SkyHarvestDataLoader;
import dev.firmages.core.sky.SkyRegistry;
import dev.firmages.core.sky.StormRodBlockEntity;
import dev.firmages.core.sky.StarfallData;
import dev.firmages.core.sky.StarfallDataLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.firmages.core.command.SelfTest.check;
import static dev.firmages.core.command.SelfTest.checkEquals;

/**
 * Suite {@code sky} (SPEC §24.7, §25.11): the sky materials against the running server. No sky item is an ore or raw
 * material, no Starfall block can start a TFC collapse or landslide, no recipe makes a raw sky material (the one-source
 * rule), the tool tiers, the loot tables, the Starfall data and the IE mineral mixes.
 */
public final class SkySuite implements SelfTest.Suite {
    private final MinecraftServer server;

    public SkySuite(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String name() {
        return "sky";
    }

    private static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }

    private static TagKey<Block> blockTag(String id) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(id));
    }

    @Override
    public void run(SelfTest.Recorder r) {
        r.test("starfall_data", () -> {
            StarfallData d = StarfallDataLoader.current();
            check(d.valid(), "data/firmages/firmages_sky/starfall.json not loaded");
            for (StarfallData.Metal m : d.metals().values()) {
                for (String b : m.mass().keySet()) check(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(b)), "metal " + m.id() + ": unknown block " + b);
            }
            return "Ages " + d.ages().keySet() + ", sizes " + d.sizes().keySet();
        });
        r.test("not_ores", () -> {
            List<String> bad = new ArrayList<>();
            for (var i : SkyRegistry.items()) {
                ItemStack s = new ItemStack(i.get());
                if (s.is(itemTag("c:ores")) || s.is(itemTag("c:raw_materials"))) bad.add(i.getId().toString());
            }
            for (var b : SkyRegistry.blocks()) {
                if (b.get().defaultBlockState().is(blockTag("c:ores")) || b.get().defaultBlockState().is(blockTag("c:stones"))) bad.add(b.getId() + " (block)");
            }
            checkEquals(List.of(), bad, "sky items or blocks in c:ores, c:raw_materials or c:stones");
            return SkyRegistry.items().size() + " items, " + SkyRegistry.blocks().size() + " blocks";
        });
        r.test("no_collapse_tags", () -> {
            List<String> bad = new ArrayList<>();
            List<TagKey<Block>> canTags = List.of(blockTag("tfc:can_collapse"), blockTag("tfc:can_landslide"), blockTag("tfc:can_trigger_collapse"),
                blockTag("tfc:can_start_collapse"));
            for (TagKey<Block> t : canTags) {
                for (var b : SkyRegistry.blocks()) if (b.get().defaultBlockState().is(t)) bad.add(b.getId() + " in #" + t.location());
            }
            checkEquals(List.of(), bad, "Starfall blocks in TFC collapse or landslide tags");
            return ModList.get().isLoaded("tfc") ? "checked against TFC's collapse and landslide tags" : "TFC not loaded";
        });
        r.test("tool_tiers", () -> {
            check(SkyRegistry.SKY_ROCK.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "sky_rock not mineable with a pickaxe");
            for (var b : List.of(SkyRegistry.METEORITE, SkyRegistry.STARSTEEL_METEORITE, SkyRegistry.STARFALL_CORE)) {
                check(b.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), b.getId() + " not mineable with a pickaxe");
                check(b.get().defaultBlockState().is(BlockTags.NEEDS_DIAMOND_TOOL), b.getId() + " not in needs_diamond_tool (wrought iron and up)");
            }
            check(SkyRegistry.STARSTEEL_METEORITE.get().defaultBlockState().is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL),
                "starsteel_meteorite not in incorrect_for_diamond_tool (steel and up)");
            check(!SkyRegistry.METEORITE.get().defaultBlockState().is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL), "meteorite needs more than wrought iron");
            return "ok";
        });
        r.test("one_source", () -> {
            Set<Item> raw = Set.of(SkyRegistry.METEORIC_IRON_CHUNK.get(), SkyRegistry.STARSTEEL_CHUNK.get(), SkyRegistry.METEORITE_ITEM.get(),
                SkyRegistry.STARSTEEL_METEORITE_ITEM.get(), SkyRegistry.STARFALL_CORE_ITEM.get());
            List<String> bad = new ArrayList<>();
            for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) {
                ItemStack out;
                try {
                    out = h.value().getResultItem(server.registryAccess());
                } catch (RuntimeException e) {
                    continue;
                }
                if (out != null && raw.contains(out.getItem())) bad.add(h.id().toString());
            }
            checkEquals(List.of(), bad, "recipes that make a raw sky material (only craters give them)");
            return server.getRecipeManager().getRecipes().size() + " recipes checked";
        });
        r.test("sky_harvest", () -> {
            check(SkyHarvestDataLoader.loaded(), "data/firmages/firmages_sky/sky_harvest.json not loaded");
            check(StormRodBlockEntity.result() == HarvestRegistry.STORM_GLASS_ITEM.get(), "storm.result is not firmages:storm_glass");
            String in = SkyHarvestDataLoader.current().storm().input();
            boolean any = false;
            for (Item i : BuiltInRegistries.ITEM) {
                if (StormRodBlockEntity.isInput(new ItemStack(i))) {
                    any = true;
                    break;
                }
            }
            check(any, "storm.input " + in + " matches no item");
            List<String> bad = new ArrayList<>();
            for (var i : HarvestRegistry.items()) {
                ItemStack st = new ItemStack(i.get());
                if (st.is(itemTag("c:ores")) || st.is(itemTag("c:raw_materials"))) bad.add(i.getId().toString());
            }
            checkEquals(List.of(), bad, "Sky Harvest items in c:ores or c:raw_materials");
            return "storm input " + in + ", aurora band at or below " + String.format(java.util.Locale.ROOT, "%.2f", dev.firmages.core.sky.AuroraService.band(server)) + " C at Y "
                + SkyHarvestDataLoader.current().aurora().referenceY();
        });
        r.test("no_energy", () -> {
            // SPEC §24.3 rule 3: no sky block has an energy capability on any side
            List<String> bad = new ArrayList<>();
            List<Block> blocks = new ArrayList<>();
            SkyRegistry.blocks().forEach(b -> blocks.add(b.get()));
            blocks.addAll(List.of(HarvestRegistry.STORM_GLASS.get(), HarvestRegistry.STORM_ROD.get(), HarvestRegistry.AURORA_NET.get()));
            var level = server.overworld();
            for (Block b : blocks) {
                var state = b.defaultBlockState();
                var be = b instanceof net.minecraft.world.level.block.EntityBlock eb ? eb.newBlockEntity(net.minecraft.core.BlockPos.ZERO, state) : null;
                // a detached block entity with the overworld as its level: other mods' capability providers read be.getLevel()
                if (be != null) be.setLevel(level);
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    if (net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK.getCapability(level, net.minecraft.core.BlockPos.ZERO, state, be, d) != null) {
                        bad.add(BuiltInRegistries.BLOCK.getKey(b) + " " + d);
                    }
                }
            }
            checkEquals(List.of(), bad, "sky blocks with an energy capability");
            return blocks.size() + " blocks, six sides each";
        });
        r.test("harvest_one_source", () -> {
            Set<Item> made = Set.of(HarvestRegistry.STORM_GLASS_ITEM.get(), HarvestRegistry.AURORA_LIGHT_BUCKET.get());
            List<String> bad = new ArrayList<>();
            for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) {
                ItemStack out;
                try {
                    out = h.value().getResultItem(server.registryAccess());
                } catch (RuntimeException e) {
                    continue;
                }
                if (out != null && made.contains(out.getItem())) bad.add(h.id().toString());
            }
            checkEquals(List.of(), bad, "recipes that make storm glass or aurora light (only the rod and the net do)");
            return "ok";
        });
        r.test("loot_tables", () -> {
            List<String> missing = new ArrayList<>();
            List<Block> withHarvest = new ArrayList<>();
            SkyRegistry.blocks().forEach(b -> withHarvest.add(b.get()));
            withHarvest.addAll(List.of(HarvestRegistry.STORM_GLASS.get(), HarvestRegistry.STORM_ROD.get(), HarvestRegistry.AURORA_NET.get()));
            for (Block b : withHarvest) {
                ResourceKey<LootTable> key = b.getLootTable();
                if (server.reloadableRegistries().getLootTable(key) == LootTable.EMPTY) missing.add(key.location().toString());
            }
            checkEquals(List.of(), missing, "sky blocks without a loot table");
            return "ok";
        });
        r.test("mineral_mixes", () -> {
            if (!ModList.get().isLoaded("immersiveengineering")) return "Immersive Engineering not loaded";
            List<String> missing = new ArrayList<>();
            for (StarfallData.Metal m : StarfallDataLoader.current().metals().values()) {
                if (server.getRecipeManager().byKey(ResourceLocation.parse(m.mix())).isEmpty()) missing.add(m.mix());
            }
            checkEquals(List.of(), missing, "IE mineral mixes of the Starfall metals");
            return "ok";
        });
    }
}
