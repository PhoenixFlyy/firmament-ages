package dev.firmages.core.command;

import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.wayshrine.WayshrineData;
import dev.firmages.core.wayshrine.WayshrineDataLoader;
import dev.firmages.core.wayshrine.WayshrinePlacement;
import dev.firmages.core.wayshrine.WayshrinePointer;
import dev.firmages.core.wayshrine.WayshrineRegistry;
import dev.firmages.core.wayshrine.WayshrineService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.firmages.core.command.SelfTest.check;

/**
 * Suite {@code wayshrines} (SPEC-himmel §28.10, §29.8, level P): the data, the restored forms, the ruin templates, the
 * Sky Anchor's nine shard recipes and ring tags and its records, the TFC structures and sets, and a locate of every
 * worldgen type from the world spawn (found, or reported out of reach).
 */
public final class WayshrineSuite implements SelfTest.Suite {
    /** The types that generate as structures (the Wind Shrine comes with the Drifting Isles). */
    static final List<String> WORLDGEN = List.of("forest_ring", "steppe_stones", "swamp_altar", "desert_arch", "mountain_spire", "reef_altar", "volcanic_ring");
    private final MinecraftServer server;

    public WayshrineSuite(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String name() {
        return "wayshrines";
    }

    /** Every recipe of this load, whatever its Age (the gate's master list), else the live ones. */
    private List<RecipeHolder<?>> allRecipes() {
        dev.firmages.core.gate.RecipeMaster m = dev.firmages.core.gate.RecipeGate.master(server.getRecipeManager());
        if (m != null) return m.entries().stream().<RecipeHolder<?>>map(dev.firmages.core.gate.RecipeMaster.Entry::holder).toList();
        return new ArrayList<>(server.getRecipeManager().getRecipes());
    }

    @Override
    public void run(SelfTest.Recorder r) {
        WayshrineData d = WayshrineDataLoader.current();
        r.test("data_loaded", () -> {
            check(d.errors().isEmpty(), "errors " + d.errors());
            for (String t : WORLDGEN) check(d.type(t).isPresent(), "type " + t + " missing");
            check(d.type(WayshrinePlacement.ISLE_TYPE).isPresent(), "the Wind Shrine type is missing");
            for (int a = 1; a <= 6; a++) {
                check(d.offerings().containsKey("age_" + a), "no offering for age_" + a);
                check(d.rites().containsKey("age_" + a), "no rite for age_" + a);
            }
            return d.types().size() + " types, offerings " + d.offerings().keySet() + ", rites " + d.rites().keySet();
        });
        r.test("offering_of_current_age", () -> {
            Map<String, String> unknown = WayshrineDataLoader.unknownOfferings(d);
            String now = WayshrineService.currentAge(server).id();
            Optional<String> want = d.offeringFor(WayshrineService.currentAge(server));
            check(want.isEmpty() || BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(want.get())), "the offering of " + now + " " + want + " is not registered");
            return now + " wants " + want.orElse("nothing yet") + (unknown.isEmpty() ? "; every offering item is registered" : "; not registered yet: " + unknown);
        });
        r.test("restored_forms", () -> {
            ServerLevel level = server.overworld();
            List<String> out = new ArrayList<>();
            for (WayshrineData.Type t : d.types().values()) {
                ResourceLocation mb = ResourceLocation.parse(t.multiblock());
                check(ShrineMultiblocks.get(mb).isPresent(), mb + " not loaded by Modonomicon");
                BlockPos anchor = level.getSharedSpawnPos().atY(level.getMinBuildHeight() + 1); // a loaded chunk
                check(ShrineMultiblocks.positions(level, anchor, mb, Rotation.NONE, '0').equals(List.of(anchor)), mb + ": the core is not the anchor");
                check(ShrineMultiblocks.positions(level, anchor, mb, Rotation.NONE, t.lightKey()).size() == 1, mb + ": no single light " + t.lightKey());
                out.add(t.id());
            }
            return "forms of " + out;
        });
        r.test("ruin_templates", () -> {
            ServerLevel level = server.overworld();
            List<String> out = new ArrayList<>();
            for (WayshrineData.Type t : d.types().values()) {
                Optional<StructureTemplate> tpl = WayshrinePlacement.template(level, t.id());
                check(tpl.isPresent(), t.structure() + " missing");
                var size = tpl.get().getSize();
                check(size.getX() <= 11 && size.getY() <= 8 && size.getZ() <= 11, t.id() + " is larger than 11 by 8 by 11: " + size);
                var cores = tpl.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), WayshrineRegistry.CORE.get());
                check(cores.size() == 1 && cores.get(0).pos().equals(WayshrinePlacement.coreOffset(tpl.get())), t.id() + ": one core in the bottom-layer centre, found "
                    + cores.stream().map(x -> x.pos().toShortString()).toList());
                check(cores.get(0).nbt() != null && t.id().equals(cores.get(0).nbt().getString("type")), t.id() + ": the core names its type");
                out.add(t.id() + " " + size.getX() + "x" + size.getY() + "x" + size.getZ());
            }
            return String.join(", ", out);
        });
        r.test("anchor_shards", () -> {
            // SPEC-himmel 29.4: nine anchor_chord recipes, one per tier, each from a non-empty #firmages:anchor/ring_N
            List<String> problems = new ArrayList<>();
            java.util.Set<Integer> tiers = new java.util.TreeSet<>();
            for (RecipeHolder<?> h : allRecipes()) {
                if (!(h.value() instanceof dev.firmages.core.crown.StarforgeRecipe sf)) continue;
                int t = dev.firmages.core.anchor.BlessingShardItem.tier(sf.result());
                if (t < 0) continue;
                if (!sf.chord().equals(ResourceLocation.fromNamespaceAndPath("firmages", "anchor_chord"))) problems.add(h.id() + " is not on anchor_chord");
                if (!tiers.add(t)) problems.add("a second recipe for tier " + t + ": " + h.id());
            }
            for (int n = 0; n < dev.firmages.core.shrine.BlessingSources.TIERS; n++) {
                var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("firmages", "anchor/ring_" + n));
                if (BuiltInRegistries.ITEM.getTag(tag).map(x -> x.size()).orElse(0) == 0) problems.add("#" + tag.location() + " is empty");
                if (!tiers.contains(n)) problems.add("no shard recipe for tier " + n);
            }
            check(problems.isEmpty(), String.join("; ", problems));
            return "nine shard recipes, nine ring tags";
        });
        r.test("anchor_records", () -> {
            // SPEC-himmel 29.3: every anchor record in a loaded chunk stands on an anchor block
            List<String> stale = new ArrayList<>();
            var all = dev.firmages.core.anchor.AnchorSavedData.get(server).anchors();
            all.forEach((where, t) -> {
                ServerLevel l = server.getLevel(where.dimension());
                if (l != null && l.isLoaded(where.pos()) && !l.getBlockState(where.pos()).is(dev.firmages.core.anchor.AnchorRegistry.SKY_ANCHOR.get())) {
                    stale.add(where.dimension().location() + " " + where.pos().toShortString());
                }
            });
            check(stale.isEmpty(), "anchor records without an anchor: " + stale);
            return all.size() + " anchors, Space Age " + (dev.firmages.core.anchor.AnchorService.ageOpen(server) ? "open" : "closed");
        });
        if (!ModList.get().isLoaded("tfc")) {
            r.test("structures", () -> "skipped: no TerraFirmaCraft (the worldgen files need it)");
            return;
        }
        r.test("structures", () -> {
            var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            var sets = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
            ResourceLocation climate = ResourceLocation.fromNamespaceAndPath("tfc", "climate");
            for (String t : WORLDGEN) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("firmages", "wayshrine/" + t);
                check(structures.containsKey(id), "structure " + id + " not loaded");
                StructureSet set = sets.get(ResourceKey.create(Registries.STRUCTURE_SET, id));
                check(set != null, "structure set " + id + " not loaded");
                ResourceLocation type = BuiltInRegistries.STRUCTURE_PLACEMENT.getKey(set.placement().type());
                check(climate.equals(type), id + " placement is " + type + ", not tfc:climate");
            }
            int tagged = structures.getTag(WayshrineService.STRUCTURES).map(x -> x.size()).orElse(0);
            check(tagged == WORLDGEN.size(), "#firmages:wayshrine holds " + tagged);
            return WORLDGEN.size() + " structures with tfc:climate sets";
        });
        r.test("locate_from_spawn", () -> {
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            List<String> out = new ArrayList<>();
            for (String t : WORLDGEN) {
                long t0 = System.nanoTime();
                Optional<WayshrinePointer.Target> found = WayshrinePointer.search(level, spawn, t, false);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                out.add(t + " " + found.map(f -> "at " + f.pos().getX() + " " + f.pos().getZ() + ", "
                    + (int) Math.sqrt(f.pos().distSqr(spawn.atY(f.pos().getY()))) + " blocks").orElse("out of reach") + " (" + ms + " ms)");
            }
            return String.join("; ", out);
        });
    }
}
