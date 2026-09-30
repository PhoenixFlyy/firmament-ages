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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.firmages.core.command.SelfTest.check;
import static dev.firmages.core.command.SelfTest.checkEquals;

/** Suite {@code server}: checks against the running server (SPEC §12 P tests of the core). */
public final class ServerSuite implements SelfTest.Suite {
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
    }
}
