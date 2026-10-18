package dev.firmages.core.command;

import dev.firmages.core.isles.DriftingIsleStructure;
import dev.firmages.core.isles.IsleRegistry;
import dev.firmages.core.isles.IsleSearch;
import dev.firmages.core.isles.IsleSpec;
import dev.firmages.core.sky.SkyTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

import static dev.firmages.core.command.SelfTest.check;
import static dev.firmages.core.command.SelfTest.checkEquals;

/**
 * Suite {@code isles} (SPEC §30.10, level P): the structure and its set load (random spread 20/6 over the isle biome
 * tag), the isle blocks are no ores, stones or collapse blocks, skystone lets sky light through and refuses spawns, the
 * lode is immune and immovable, the loot tables exist, no recipe makes firmament crystal (the one source), the Age tags
 * hold the crystal (age_4) and the two crystal blocks, and three isle clusters are found around the world spawn.
 */
public final class IsleSuite implements SelfTest.Suite {
    private final MinecraftServer server;

    public IsleSuite(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String name() {
        return "isles";
    }

    private static TagKey<Block> blockTag(String id) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(id));
    }

    private static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
    }

    @Override
    public void run(SelfTest.Recorder r) {
        r.test("structure_loaded", () -> {
            var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Structure st = structures.get(IsleSearch.STRUCTURE);
            check(st instanceof DriftingIsleStructure, "firmages:drifting_isle missing or not a drifting isle: " + st);
            DriftingIsleStructure d = (DriftingIsleStructure) st;
            StructureSet set = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceLocation.fromNamespaceAndPath("firmages", "drifting_isles"));
            check(set != null, "structure set firmages:drifting_isles missing");
            check(set.placement() instanceof RandomSpreadStructurePlacement, "the set is not random_spread");
            RandomSpreadStructurePlacement p = (RandomSpreadStructurePlacement) set.placement();
            int biomes = d.biomes().size();
            if (ModList.get().isLoaded("tfc")) check(biomes > 100, "the isle biome tag holds " + biomes + " biomes");
            return "spacing " + p.spacing() + ", separation " + p.separation() + ", " + biomes + " biomes, top " + d.isles().topY().min() + ".."
                + d.isles().topY().max() + ", step " + d.step().getName();
        });
        r.test("not_ores", () -> {
            List<String> bad = new ArrayList<>();
            for (var i : IsleRegistry.items()) {
                ItemStack s = new ItemStack(i.get());
                if (s.is(itemTag("c:ores")) || s.is(itemTag("c:raw_materials")) || s.is(itemTag("c:gems"))) bad.add(i.getId().toString());
            }
            for (var b : IsleRegistry.blocks()) {
                BlockState st = b.get().defaultBlockState();
                if (st.is(blockTag("c:ores")) || st.is(blockTag("c:stones"))) bad.add(b.getId() + " (block)");
                for (String t : List.of("tfc:can_collapse", "tfc:can_landslide", "tfc:can_trigger_collapse", "tfc:can_start_collapse")) {
                    if (st.is(blockTag(t))) bad.add(b.getId() + " in #" + t);
                }
            }
            checkEquals(List.of(), bad, "isle items or blocks in ore, stone, gem or collapse tags");
            return IsleRegistry.items().size() + " items, " + IsleRegistry.blocks().size() + " blocks";
        });
        r.test("skystone", () -> {
            BlockState s = IsleRegistry.SKYSTONE.get().defaultBlockState();
            check(s.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), "skystone stops sky light");
            check(s.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == 0, "skystone blocks light");
            check(!s.isValidSpawn(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, EntityType.ZOMBIE), "a zombie may spawn on skystone");
            check(s.canOcclude(), "skystone lost its face culling");
            int body = 0;
            for (var b : IsleRegistry.blocks()) if (b.get().defaultBlockState().is(SkyTags.ISLE_BODY)) body++;
            check(body == 3, "#firmages:isle/body holds " + body + " of the three isle blocks");
            return "sky light passes, no spawns, face culling kept";
        });
        r.test("lode", () -> {
            BlockState l = IsleRegistry.LODE.get().defaultBlockState();
            check(l.getBlock().defaultDestroyTime() == 50.0F, "lode hardness " + l.getBlock().defaultDestroyTime());
            check(l.getBlock().getExplosionResistance() >= 1200.0F, "lode blast resistance " + l.getBlock().getExplosionResistance());
            check(l.is(BlockTags.WITHER_IMMUNE) && l.is(BlockTags.DRAGON_IMMUNE), "the lode is not Wither and Dragon immune");
            check(l.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK, "pistons can move the lode");
            if (ModList.get().isLoaded("create")) check(l.is(blockTag("create:non_movable")), "the lode is not in #create:non_movable");
            check(l.is(BlockTags.MINEABLE_WITH_PICKAXE), "the lode is not mineable with a pickaxe");
            return "hardness 50, immune, immovable";
        });
        r.test("loot_tables", () -> {
            List<String> missing = new ArrayList<>();
            for (var b : IsleRegistry.blocks()) {
                ResourceKey<LootTable> key = b.get().getLootTable();
                // the isle ruin's Wind Seals are unbreakable and drop nothing by design (noLootTable, round G3)
                if (key.equals(net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY)) continue;
                if (server.reloadableRegistries().getLootTable(key) == LootTable.EMPTY) missing.add(key.location().toString());
            }
            checkEquals(List.of(), missing, "isle blocks without a loot table");
            return "ok";
        });
        r.test("one_source", () -> {
            List<String> bad = new ArrayList<>();
            for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) {
                ItemStack out;
                try {
                    out = h.value().getResultItem(server.registryAccess());
                } catch (RuntimeException e) {
                    continue;
                }
                if (out != null && out.is(IsleRegistry.FIRMAMENT_CRYSTAL.get())) bad.add(h.id().toString());
            }
            checkEquals(List.of(), bad, "recipes that make firmament crystal (only the isles give it)");
            return server.getRecipeManager().getRecipes().size() + " recipes checked";
        });
        r.test("age_tags", () -> {
            TagKey<Item> items = itemTag("firmages:age_items/age_4");
            TagKey<Block> blocks = blockTag("firmages:age_blocks/age_4");
            boolean packTags = BuiltInRegistries.ITEM.getTag(items).map(t -> t.size() > 0).orElse(false);
            if (!packTags) return "the pack's Age tags are not loaded";
            check(new ItemStack(IsleRegistry.FIRMAMENT_CRYSTAL.get()).is(items), "firmament_crystal not in #firmages:age_items/age_4");
            check(IsleRegistry.CRYSTAL_CLUSTER.get().defaultBlockState().is(blocks), "the cluster is not in #firmages:age_blocks/age_4");
            check(IsleRegistry.LODE.get().defaultBlockState().is(blocks), "the lode is not in #firmages:age_blocks/age_4");
            check(new ItemStack(IsleRegistry.SKYSTONE_ITEM.get()).is(itemTag("firmages:age_items/dawn")), "skystone not in #firmages:age_items/dawn");
            return "crystal and crystal blocks age_4, skystone Dawn";
        });
        r.test("locate_three", () -> {
            if (!ModList.get().isLoaded("tfc")) return "TFC not loaded (no isle biome)";
            ServerLevel ow = server.overworld();
            BlockPos spawn = ow.getSharedSpawnPos();
            long t0 = System.nanoTime();
            List<IsleSearch.Found> found = IsleSearch.nearest(ow, spawn, 2048, c -> true, 3);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            check(found.size() == 3, "found " + found.size() + " isle clusters within 2,048 blocks of the spawn");
            List<String> out = new ArrayList<>();
            for (IsleSearch.Found f : found) {
                IsleSpec m = f.main();
                out.add((int) f.distance() + " blocks: " + f.isles().size() + " isles, main " + m.size().id() + " top " + m.top() + (m.shrine() ? ", Wind Shrine" : ""));
            }
            return String.join("; ", out) + " (" + ms + " ms)";
        });
    }
}
