package dev.firmages.core.command;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageId;
import dev.firmages.core.age.AgeCoverage;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import dev.firmages.core.age.AgeMirror;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.compat.kubejs.FirmAgesJS;
import dev.firmages.core.compat.recipes.CraftProbe;
import dev.firmages.core.compat.recipes.RecipeCaches;
import dev.firmages.core.gate.GateReport;
import dev.firmages.core.gate.RecipeGate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.firmages.core.command.SelfTest.check;
import static dev.firmages.core.command.SelfTest.checkEquals;

/** Suite {@code server}: checks against the running server (SPEC §12 P tests of the core). */
public final class ServerSuite implements SelfTest.Suite {
    /** Recipes per side (live, Age-locked) the crafting-table case probes. */
    private static final int PROBES = 150;
    private final MinecraftServer server;

    public ServerSuite(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String name() {
        return "server";
    }

    @Override
    public void run(SelfTest.Recorder r) {
        r.test("nights_table", () -> {
            // Creeping Darkness (SPEC §19.7): data/firmages/balance/nights.json is loaded and keeps rules N-1 to N-5, N-8
            check(dev.firmages.core.hardship.NightsService.tablePresent(server), "data/firmages/balance/nights.json missing");
            dev.firmages.core.hardship.Nights t = dev.firmages.core.hardship.NightsService.table(server);
            checkEquals(List.of(), t.problems(), "night table rules");
            dev.firmages.core.hardship.Nights.Row now = dev.firmages.core.hardship.NightsService.row(server);
            return now.age().id() + ": health x" + now.health() + ", damage x" + now.damage() + ", gear " + now.gear() + " " + now.gearChance()
                + ", apoth " + now.apothTier();
        });
        r.test("dawn_unlocked", () -> {
            check(AgeService.state(server).snapshot().isUnlocked(AgeId.DAWN), "dawn locked");
            return "ok";
        });
        r.test("mirror_matches_state", () -> {
            AgeSnapshot live = AgeService.state(server).snapshot();
            AgeMirror.ReadResult m = AgeMirror.read(AgeService.mirrorPath(server));
            checkEquals(AgeMirror.Status.OK, m.status(), "mirror status (" + m.detail() + ")");
            checkEquals(live.unlockedIds(), m.snapshot().orElseThrow().unlockedIds(), "mirror Ages");
            checkEquals(live.version(), m.snapshot().orElseThrow().version(), "mirror version");
            return live.unlockedIds() + " v" + live.version();
        });
        r.test("boot_snapshot_fail_strict", () -> {
            AgeService.Status st = AgeService.status(server);
            boolean moreOpenThanState = !st.live().unlocked().containsAll(st.boot().snapshot().unlocked());
            check(!(st.bootUsed() && moreOpenThanState && !st.bootReconcileRequested()),
                "boot snapshot " + st.boot().snapshot().unlockedIds() + " unlocked more than AgeState " + st.live().unlockedIds() + " and no reload was requested");
            check(!st.bootAnswersStale() || st.bootReconcileRequested(),
                "FirmAges.lockedOreBlocks() answered from an incomplete index during the initial load and no reload was requested");
            return st.boot().source() + " " + st.boot().snapshot().unlockedIds() + ", used during load: " + st.bootUsed()
                + ", boot reload requested: " + st.bootReconcileRequested();
        });
        r.test("ps_stages_in_age_state", () -> {
            AgeSnapshot live = AgeService.state(server).snapshot();
            List<String> missing = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                for (StageId id : ProgressiveStagesAPI.getStages(p)) {
                    AgeId.byId(id.getPath()).filter(a -> !live.isUnlocked(a)).ifPresent(a -> missing.add(p.getGameProfile().getName() + ":" + a));
                }
            }
            checkEquals(List.of(), missing, "Ages known to ProgressiveStages but missing from AgeState");
            return server.getPlayerList().getPlayerCount() + " players checked";
        });
        r.test("age_index_built", () -> {
            AgeIndex idx = AgeIndex.current();
            check(idx.generation() > 0 && idx.report() != null, "no AgeIndex was built during the datapack load");
            return "gen " + idx.generation() + ": " + idx.items().size() + " items, " + idx.blocks().size() + " blocks, "
                + idx.fluids().size() + " fluids in " + idx.report().buildMillis() + " ms";
        });
        r.test("age_index_not_empty", () -> {
            AgeIndex idx = AgeIndex.current();
            check(!idx.misconfigured(), "all firmages:age_* tags are empty (gate misconfigured)");
            return "items per Age " + idx.report().itemTags().perAge() + ", blocks per Age " + idx.report().blockTags().perAge();
        });
        r.test("age_tags_present", () -> {
            AgeIndex idx = AgeIndex.current();
            check(idx.report() != null, "no index");
            checkEquals(List.of(), idx.report().itemTags().missingTags(), "Ages without firmages:age_items tag");
            checkEquals(List.of(), idx.report().blockTags().missingTags(), "Ages without firmages:age_blocks tag");
            return "all 11 item and block tags exist";
        });
        r.test("age_coverage_vs_progressivestages", () -> {
            AgeCoverage.Result c = AgeCoverage.check(AgeIndex.current());
            check(c != null, "coverage check could not run");
            check(c.clean(), c.untaggedItems().size() + " PS-locked items without age tag, " + c.ageMismatches().size()
                + " Age mismatches, " + c.untaggedOreBlocks().size() + " ore blocks without age_blocks tag (logs/" + AgeCoverage.REPORT_FILE + ")");
            return c.psAgeLocks() + " PS Age item locks covered";
        });
        r.test("gate_ran_with_live_ages", () -> {
            check(RecipeGate.lastFailure() == null, "the last recipe gate run failed: " + RecipeGate.lastFailure());
            GateReport rep = RecipeGate.lastReport();
            check(rep != null, "the recipe gate did not run during the last datapack load (mixin not applied?)");
            check(!rep.misconfigured(), "gate misconfigured: all age tags are empty");
            AgeSnapshot live = AgeService.state(server).snapshot();
            AgeService.Status st = AgeService.status(server);
            boolean pending = st.phase() != dev.firmages.core.age.ReloadScheduler.Phase.IDLE;
            check(pending || rep.unlocked().equals(live.unlockedIds()),
                "last filter ran with " + rep.unlocked() + " but AgeState is " + live.unlockedIds() + " and no reload is pending");
            return rep.summary();
        });
        r.test("gate_exempt_types_intact", () -> {
            GateReport rep = RecipeGate.lastReport();
            check(rep != null, "no gate report");
            List<String> hit = new ArrayList<>();
            for (String t : dev.firmages.core.config.ServerConfig.EXEMPT_RECIPE_TYPES.get()) {
                GateReport.TypeStats s = rep.types().get(t);
                if (s != null && s.dropped > 0) hit.add(t + " lost " + s.dropped);
            }
            checkEquals(List.of(), hit, "exempt types with dropped recipes");
            return "exempt types untouched";
        });
        r.test("recipe_caches_fresh", () -> {
            List<String> stale = RecipeCaches.stale(server);
            checkEquals(List.of(), stale, "recipe caches that disagree with the live recipes");
            return "caches of " + RecipeCaches.checkable() + " match the live recipes";
        });
        r.test("crafting_table_follows_ages", () -> {
            // /firmages recipes craft for a spread of recipes: a live one is found and is live, an Age-locked one is not found.
            RecipeManager rm = server.getRecipeManager();
            List<ResourceLocation> live = CraftProbe.sample(server, true, PROBES);
            List<ResourceLocation> locked = CraftProbe.sample(server, false, PROBES);
            check(!live.isEmpty(), "no live shaped or shapeless crafting recipe to probe");
            List<String> bad = new ArrayList<>();
            for (ResourceLocation id : live) {
                CraftProbe.probe(server, id).ifPresent(p -> {
                    if (p.found().isEmpty()) bad.add(id + ": no recipe matches");
                    else if (rm.byKey(p.found().get().id()).isEmpty()) bad.add(id + ": crafts " + p.found().get().id() + ", which is not live");
                });
            }
            for (ResourceLocation id : locked) {
                CraftProbe.probe(server, id).flatMap(CraftProbe.Result::found).ifPresent(h -> {
                    if (h.id().equals(id) || rm.byKey(h.id()).isEmpty()) bad.add(id + ": locked by its Age, crafts " + h.id());
                });
            }
            checkEquals(List.of(), bad.stream().limit(10).toList(), bad.size() + " crafting lookups that do not follow the Ages");
            return live.size() + " live recipes craft, " + locked.size() + " Age-locked recipes do not";
        });
        r.test("binding_consistent", () -> {
            AgeSnapshot snap = AgeService.snapshotForReload();
            for (AgeId a : AgeId.all()) checkEquals(snap.isUnlocked(a), FirmAgesJS.isUnlocked(a.id()), "isUnlocked(" + a + ")");
            check(!FirmAgesJS.isUnlocked("mob_1") && !FirmAgesJS.isUnlocked("nonsense"), "non-Age ids must be false");
            Set<String> all = AgeId.all().stream().map(AgeId::id).collect(Collectors.toSet());
            List<String> union = new ArrayList<>(FirmAgesJS.unlockedAges());
            union.addAll(FirmAgesJS.lockedAges());
            checkEquals(all, Set.copyOf(union), "unlockedAges + lockedAges");
            checkEquals(all.size(), union.size(), "no Age in both lists");
            List<String> ores = FirmAgesJS.lockedOreBlocks();
            for (String id : ores) {
                AgeId age = AgeIndex.current().ageOf(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)));
                check(age != null && !snap.isUnlocked(age), id + " is not in a locked Age");
            }
            long expected = AgeIndex.current().blocks().values().stream().filter(a -> !snap.isUnlocked(a)).count();
            checkEquals(expected, (long) ores.size(), "lockedOreBlocks size");
            return ores.size() + " locked ore blocks";
        });
        r.test("grid_lock_hooked", () -> {
            // SPEC 19.3: the grid menus carry the lock (mixins applied), the status agrees with the server, TFC piles are hooked
            check(dev.firmages.core.hardship.GridLockMenu.class.isAssignableFrom(net.minecraft.world.inventory.InventoryMenu.class),
                "InventoryMenu is not a GridLockMenu (mixin not applied)");
            String curios = "Curios not loaded";
            if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
                try {
                    Class<?> c = Class.forName("top.theillusivec4.curios.common.inventory.container.CuriosContainer");
                    check(dev.firmages.core.hardship.GridLockMenu.class.isAssignableFrom(c), "CuriosContainer is not a GridLockMenu (mixin not applied)");
                    curios = "Curios grid hooked";
                } catch (ClassNotFoundException e) {
                    check(false, "Curios loaded but CuriosContainer missing: " + e);
                }
            }
            String tfc = "TFC not loaded";
            if (net.neoforged.fml.ModList.get().isLoaded("tfc")) {
                check(dev.firmages.core.compat.tfc.TfcPiles.hooked(), "TFC placed-item pile not hooked (block or accessor missing)");
                tfc = "TFC piles hooked";
            }
            boolean active = dev.firmages.core.hardship.GridLockService.active(server);
            checkEquals(active, dev.firmages.core.immersion.ImmersionService.status(server).gridLock(), "status gridLock");
            return "locked " + active + " (until " + dev.firmages.core.config.ServerConfig.gridLockUntil().map(AgeId::id).orElse("never") + "), " + curios + ", " + tfc;
        });
        dev.firmages.core.loot.LootSelfTest.run(r, server); // loot Age gate (SPEC 19.5)
    }
}
