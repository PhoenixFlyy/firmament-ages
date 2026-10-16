package dev.firmages.core.command;

import dev.firmages.core.ark.ArkData;
import dev.firmages.core.ark.ArkDataLoader;
import dev.firmages.core.ark.ArkGrowth;
import dev.firmages.core.ark.ArkProgress;
import dev.firmages.core.ark.ArkRegistry;
import dev.firmages.core.ark.ArkRules;
import dev.firmages.core.ark.ArkService;
import dev.firmages.core.ark.ArkWork;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginSavedData;
import dev.firmages.core.origin.OriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.firmages.core.command.SelfTest.check;

/**
 * Suite {@code ark} (SPEC §31.10, level P): the island rules and their ids, the 27 stage templates, the version-3
 * arena with its nine plinths, and the socket ring. Read only: it never grows or grants anything.
 */
public final class ArkSuite implements SelfTest.Suite {
    private final MinecraftServer server;

    public ArkSuite(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String name() {
        return "ark";
    }

    @Override
    public void run(SelfTest.Recorder r) {
        ArkData d = ArkDataLoader.current();
        r.test("data_loaded", () -> {
            check(d.errors().isEmpty(), "errors " + d.errors());
            check(d.islands().size() == ArkData.ISLANDS, "rules for " + d.islands().keySet());
            for (int i = 0; i < ArkData.ISLANDS; i++) {
                ArkData.Island isl = d.island(i).orElseThrow();
                check(isl.hasCrown() == (i >= 3), "age_" + i + ": a crown from the Arcane island on, and only there");
            }
            return d.islands().size() + " islands" + (d.skipped().isEmpty() ? "" : "; left out until their items exist: " + d.skipped());
        });
        r.test("sample_ids", () -> {
            List<String> out = new ArrayList<>();
            for (ArkData.Island isl : d.islands().values()) {
                for (ArkData.Flora f : isl.flora()) {
                    int n = members(f.what());
                    check(n >= (f.distinct() > 0 ? f.distinct() : 1), isl.age() + ": flora " + f.what() + " has " + n + " registered items, needs " + f.needed());
                }
                for (String e : isl.fauna()) {
                    ResourceLocation id = ResourceLocation.tryParse(e);
                    check(id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id), isl.age() + ": fauna " + e + " is not registered");
                }
                check(isl.work().stream().anyMatch(ArkWork::resolvable), isl.age() + ": no work rule names a registered block " + isl.work());
                for (ArkData.Work w : isl.work()) {
                    if (w.type() != ArkData.WorkType.BLOCKSTATE || !ArkWork.resolvable(w)) continue;
                    check(ArkWork.propertyFits(w), isl.age() + ": no block of " + w + " has that property and value");
                }
                for (ArkData.CrownEntry c : isl.crown()) check(members(c.what()) > 0, isl.age() + ": crown " + c.what() + " resolves to no item");
                List<String> unknown = ArkWork.unknownBlocks(isl.work());
                out.add(isl.age() + (unknown.isEmpty() ? "" : " (work alternatives not registered: " + unknown + ")"));
            }
            return "ids resolve on " + String.join(", ", out);
        });
        r.test("stage_templates", () -> {
            ServerLevel level = OriginService.level(server);
            check(level != null, "The Origin is not loaded");
            List<String> out = new ArrayList<>();
            for (ArkData.Island isl : d.islands().values()) {
                BlockPos top = OriginArena.islandTop(isl.island());
                int sizes = 0;
                for (String s : isl.stages()) {
                    Optional<List<ArkGrowth.Step>> blocks = ArkGrowth.blocks(level, ResourceLocation.parse(s), top);
                    check(blocks.isPresent() && !blocks.get().isEmpty(), s + " missing or empty");
                    int reach = OriginArena.arkRadius(isl.island()) + 1;
                    for (ArkGrowth.Step st : blocks.get()) {
                        int dx = st.pos().getX() - top.getX();
                        int dz = st.pos().getZ() - top.getZ();
                        check(dx * dx + dz * dz <= reach * reach, s + ": block at " + dx + " " + dz + " lies beyond the island");
                    }
                    sizes += blocks.get().size();
                }
                out.add(isl.age() + " " + sizes);
            }
            return "blocks per island: " + String.join(", ", out);
        });
        r.test("arena_v3", () -> {
            ServerLevel level = OriginService.level(server);
            check(level != null, "The Origin is not loaded");
            OriginSavedData sd = OriginSavedData.get(server);
            check(sd.arenaVersion() == OriginArena.VERSION, "arena v" + sd.arenaVersion() + ", expected v" + OriginArena.VERSION);
            List<String> missing = new ArrayList<>();
            for (int i = 0; i < ArkData.ISLANDS; i++) {
                ArkProgress p = sd.ark().island(i);
                if (p.plinth() == Long.MIN_VALUE || !level.getBlockState(BlockPos.of(p.plinth())).is(ArkRegistry.ARK_PLINTH.get())) missing.add("age_" + i);
            }
            check(missing.isEmpty(), "no plinth on " + missing);
            return "arena v" + sd.arenaVersion() + ", nine plinths stand";
        });
        r.test("sockets", () -> {
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < ArkData.ISLANDS; i++) {
                int[] s = ArkRules.socket(i);
                check(seen.add(((long) s[0] << 32) ^ (s[1] & 0xFFFFFFFFL)), "socket " + i + " shares its cell");
                check(s[0] * s[0] + s[1] * s[1] <= 10, "socket " + i + " off the ring");
            }
            int mask = OriginSavedData.get(server).ark().completeMask();
            return "nine sockets around the altar; lit " + Integer.bitCount(mask) + (ArkService.awake(server) ? "" : " (the islands sleep until the finale)");
        });
    }

    /** Registered items of a selector. */
    private static int members(ArkData.Selector s) {
        if (!s.byTag()) {
            return (int) s.ids().stream().map(ResourceLocation::tryParse).filter(id -> id != null && BuiltInRegistries.ITEM.containsKey(id)).count();
        }
        ResourceLocation tag = ResourceLocation.tryParse(s.tag());
        return tag == null ? 0 : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag)).map(h -> h.size()).orElse(0);
    }
}
