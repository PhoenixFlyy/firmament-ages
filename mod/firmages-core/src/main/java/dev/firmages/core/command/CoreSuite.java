package dev.firmages.core.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeAssignment;
import dev.firmages.core.age.AgeChangeProcessor;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeLedger;
import dev.firmages.core.age.AgeMirror;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.age.AgeTagResolver;
import dev.firmages.core.age.ReloadScheduler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static dev.firmages.core.command.SelfTest.check;
import static dev.firmages.core.command.SelfTest.checkEquals;

/**
 * Suite {@code core}: the pure core logic (SPEC §12 U tests). No Minecraft classes, so JUnit runs the same cases
 * ({@code CoreSuiteTest}) and {@code /firmages selftest core} runs them in the pack.
 */
public final class CoreSuite implements SelfTest.Suite {

    @Override
    public String name() {
        return "core";
    }

    @Override
    public void run(SelfTest.Recorder r) {
        r.test("mirror_round_trip", CoreSuite::mirrorRoundTrip);
        r.test("mirror_missing_uses_fallback", CoreSuite::mirrorMissing);
        r.test("mirror_corrupt_uses_fallback", CoreSuite::mirrorCorrupt);
        r.test("mirror_atomic_write", CoreSuite::mirrorAtomicWrite);
        r.test("scheduler_delay", CoreSuite::schedulerDelay);
        r.test("scheduler_coalescing", CoreSuite::schedulerCoalescing);
        r.test("scheduler_coalesce_cap", CoreSuite::schedulerCoalesceCap);
        r.test("scheduler_one_follow_up", CoreSuite::schedulerFollowUp);
        r.test("scheduler_failure_recovers", CoreSuite::schedulerFailure);
        r.test("stage_change_dedup_same_tick", CoreSuite::dedupSameTick);
        r.test("stage_change_ignores_non_age", CoreSuite::ignoresNonAge);
        r.test("revoke_reloads_and_warns", CoreSuite::revoke);
        r.test("dawn_never_revoked", CoreSuite::dawnNeverRevoked);
        r.test("bulk_only_adds", CoreSuite::bulkOnlyAdds);
        r.test("tag_resolver_nested_replace_remove", CoreSuite::tagResolver);
        r.test("tag_resolver_cycle_and_missing", CoreSuite::tagResolverCycle);
        r.test("age_assignment_earliest_wins", CoreSuite::earliestWins);
    }

    // ---------------------------------------------------------------- mirror

    private static AgeSnapshot snap(long version, AgeId... ages) {
        return AgeSnapshot.of(List.of(ages), version, 1234);
    }

    private static String mirrorRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("firmages-selftest");
        try {
            Path f = dir.resolve(AgeMirror.RELATIVE_PATH);
            AgeSnapshot s = snap(7, AgeId.AGE_0, AgeId.AGE_1);
            AgeMirror.write(f, s, Instant.parse("2026-09-30T12:00:00Z"));
            AgeMirror.ReadResult r = AgeMirror.read(f);
            checkEquals(AgeMirror.Status.OK, r.status(), "status");
            AgeSnapshot back = r.snapshot().orElseThrow();
            checkEquals(s.unlocked(), back.unlocked(), "unlocked");
            checkEquals(7L, back.version(), "version");
            checkEquals(1234L, back.lastReloadGameTime(), "lastReloadGameTime");
            check(Files.readString(f).contains("\"timestamp\""), "timestamp missing");
            return "unlocked " + back.unlockedIds();
        } finally {
            deleteTree(dir);
        }
    }

    private static String mirrorMissing() throws Exception {
        Path dir = Files.createTempDirectory("firmages-selftest");
        try {
            AgeMirror.ReadResult r = AgeMirror.read(dir.resolve("nope.json"));
            checkEquals(AgeMirror.Status.MISSING, r.status(), "status");
            AgeSnapshot fallback = AgeSnapshot.DAWN_ONLY;
            checkEquals(fallback, AgeMirror.snapshotOrFallback(r, fallback), "snapshot");
            return "missing -> fallback";
        } finally {
            deleteTree(dir);
        }
    }

    private static String mirrorCorrupt() throws Exception {
        Path dir = Files.createTempDirectory("firmages-selftest");
        try {
            AgeSnapshot fallback = AgeSnapshot.DAWN_ONLY;
            String[] bad = {
                "{ not json",
                "[]",
                "{}",
                "{\"unlocked\": \"age_3\"}",
                "{\"unlocked\": [\"dawn\", 3]}",
                "{\"unlocked\": [\"dawn\", \"age_42\"]}",
                "{\"unlocked\": [\"dawn\", \"age_1\"], \"version\": \"seven\"}",
                ""
            };
            Path f = dir.resolve("ages.json");
            for (String b : bad) {
                Files.writeString(f, b, StandardCharsets.UTF_8);
                AgeMirror.ReadResult r = AgeMirror.read(f);
                checkEquals(AgeMirror.Status.CORRUPT, r.status(), "status for " + b);
                checkEquals(fallback, AgeMirror.snapshotOrFallback(r, fallback), "snapshot for " + b);
            }
            // a mirror without dawn is valid; dawn is implied
            Files.writeString(f, "{\"unlocked\": [\"age_0\"]}", StandardCharsets.UTF_8);
            AgeMirror.ReadResult ok = AgeMirror.read(f);
            checkEquals(AgeMirror.Status.OK, ok.status(), "status without dawn");
            checkEquals(EnumSet.of(AgeId.DAWN, AgeId.AGE_0), ok.snapshot().orElseThrow().unlocked(), "dawn implied");
            return bad.length + " corrupt variants -> fallback";
        } finally {
            deleteTree(dir);
        }
    }

    private static String mirrorAtomicWrite() throws Exception {
        Path dir = Files.createTempDirectory("firmages-selftest");
        try {
            Path f = dir.resolve("firmages").resolve("ages.json");
            AgeMirror.write(f, snap(1, AgeId.AGE_0), Instant.now());
            AgeMirror.write(f, snap(2, AgeId.AGE_0, AgeId.AGE_1, AgeId.AGE_2), Instant.now());
            AgeSnapshot back = AgeMirror.read(f).snapshot().orElseThrow();
            checkEquals(2L, back.version(), "overwritten version");
            checkEquals(EnumSet.of(AgeId.DAWN, AgeId.AGE_0, AgeId.AGE_1, AgeId.AGE_2), back.unlocked(), "overwritten ages");
            try (Stream<Path> s = Files.list(f.getParent())) {
                List<String> names = s.map(p -> p.getFileName().toString()).toList();
                checkEquals(List.of("ages.json"), names, "files in mirror dir (no temp files left)");
            }
            return "overwrite ok, no temp files";
        } finally {
            deleteTree(dir);
        }
    }

    private static void deleteTree(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) Files.deleteIfExists(p);
        }
    }

    // ---------------------------------------------------------------- scheduler (fake clock)

    /** Fake server: a tick clock, a reloader whose futures the test completes, and finish records. */
    private static final class FakeServer {
        long tick;
        final List<CompletableFuture<Void>> started = new ArrayList<>();
        final List<String> reasons = new ArrayList<>();
        final List<Throwable> finished = new ArrayList<>();
        final ReloadScheduler scheduler;

        FakeServer(int delay, int coalesce, boolean completeImmediately) {
            scheduler = new ReloadScheduler(() -> tick, () -> delay, () -> coalesce, () -> tick * 50_000_000L, reason -> {
                CompletableFuture<Void> f = new CompletableFuture<>();
                started.add(f);
                reasons.add(reason);
                if (completeImmediately) f.complete(null);
                return f;
            }, (reason, ms, err) -> finished.add(err));
        }

        void runUntil(long endTick) {
            for (; tick <= endTick; tick++) scheduler.tick();
            tick = endTick;
        }
    }

    private static String schedulerDelay() {
        FakeServer s = new FakeServer(60, 100, true);
        s.scheduler.request("a");
        s.runUntil(59);
        checkEquals(0, s.started.size(), "reloads before the delay");
        s.tick = 60;
        s.scheduler.tick();
        checkEquals(1, s.started.size(), "reloads at the delay");
        return "reload at tick 60";
    }

    private static String schedulerCoalescing() {
        FakeServer s = new FakeServer(60, 100, true);
        s.scheduler.request("granted age_1");
        s.runUntil(30);
        s.scheduler.request("granted mob_1");
        s.runUntil(300);
        checkEquals(1, s.started.size(), "reloads for 2 requests within 100 ticks");
        check(s.reasons.get(0).contains("age_1") && s.reasons.get(0).contains("mob_1"), "reasons merged: " + s.reasons);
        return "1 reload, reasons " + s.reasons.get(0);
    }

    private static String schedulerCoalesceCap() {
        FakeServer s = new FakeServer(60, 100, true);
        long firstStart = -1;
        s.scheduler.request("r0");
        for (int t = 1; t <= 300; t++) {
            s.tick = t;
            if (t % 20 == 0 && t < 200) s.scheduler.request("r" + t);
            int before = s.started.size();
            s.scheduler.tick();
            if (before == 0 && s.started.size() == 1) firstStart = t;
        }
        checkEquals(100L, firstStart, "first reload capped at first request + coalesceTicks");
        return "first reload at tick " + firstStart + ", total " + s.started.size();
    }

    private static String schedulerFollowUp() {
        FakeServer s = new FakeServer(60, 100, false);
        s.scheduler.request("a");
        s.runUntil(60);
        checkEquals(1, s.started.size(), "first reload started");
        checkEquals(ReloadScheduler.Phase.RUNNING, s.scheduler.phase(), "phase");
        s.scheduler.request("b");
        s.scheduler.request("c");
        s.scheduler.requestNow("d");
        s.runUntil(200);
        checkEquals(1, s.started.size(), "no second reload while running");
        s.started.get(0).complete(null);
        checkEquals(1, s.finished.size(), "finish callback");
        checkEquals(ReloadScheduler.Phase.PENDING, s.scheduler.phase(), "follow-up pending");
        s.runUntil(400);
        checkEquals(2, s.started.size(), "exactly one follow-up");
        s.started.get(1).complete(null);
        s.runUntil(800);
        checkEquals(2, s.started.size(), "nothing after the follow-up");
        return "follow-up reason: " + s.reasons.get(1);
    }

    private static String schedulerFailure() {
        FakeServer s = new FakeServer(0, 0, false);
        s.scheduler.request("a");
        s.runUntil(1);
        s.started.get(0).completeExceptionally(new IllegalStateException("boom"));
        checkEquals(1, s.finished.size(), "finish called on failure");
        check(s.finished.get(0) != null, "error passed to the listener");
        checkEquals(ReloadScheduler.Phase.IDLE, s.scheduler.phase(), "idle after failure");
        s.scheduler.request("b");
        s.runUntil(3);
        checkEquals(2, s.started.size(), "next request still runs");
        return "recovered";
    }

    // ---------------------------------------------------------------- stage changes

    private static final class CountingSink implements AgeChangeProcessor.Sink {
        int persists;
        final List<String> reloads = new ArrayList<>();
        final List<AgeId> revoked = new ArrayList<>();

        @Override
        public void persist() {
            persists++;
        }

        @Override
        public void requestReload(String reason, boolean revoke) {
            reloads.add(reason);
        }

        @Override
        public void revoked(AgeId age) {
            revoked.add(age);
        }
    }

    private static String dedupSameTick() {
        AgeLedger ledger = new AgeLedger();
        CountingSink sink = new CountingSink();
        AgeChangeProcessor p = new AgeChangeProcessor(ledger, sink);
        FakeServer server = new FakeServer(60, 100, true);
        AgeChangeProcessor viaScheduler = new AgeChangeProcessor(new AgeLedger(), new AgeChangeProcessor.Sink() {
            public void persist() {}
            public void requestReload(String reason, boolean revoke) { server.scheduler.request(reason); }
            public void revoked(AgeId age) {}
        });
        // Team sync fires the same GRANTED once per member within one tick.
        for (int member = 0; member < 3; member++) {
            p.onStageChange("age_1", true, 5);
            viaScheduler.onStageChange("progressivestages:age_1", true, 5);
        }
        p.onStageChange("age_1", true, 6);
        checkEquals(1, sink.persists, "persists");
        checkEquals(1, sink.reloads.size(), "reload requests");
        server.runUntil(500);
        checkEquals(1, server.started.size(), "reloads");
        checkEquals(EnumSet.of(AgeId.DAWN, AgeId.AGE_1), ledger.snapshot().unlocked(), "ages");
        checkEquals(1L, ledger.snapshot().version(), "version");
        return "3 events -> 1 persist, 1 reload";
    }

    private static String ignoresNonAge() {
        AgeLedger ledger = new AgeLedger();
        CountingSink sink = new CountingSink();
        AgeChangeProcessor p = new AgeChangeProcessor(ledger, sink);
        for (String s : List.of("mob_1", "tool_ie_capacitor", "disabled", "finale_won", "age_10", "")) {
            check(!p.onStageChange(s, true, 1), "changed by " + s);
        }
        checkEquals(0, sink.persists, "persists");
        return "non-Age stages ignored";
    }

    private static String revoke() {
        AgeLedger ledger = new AgeLedger(snap(3, AgeId.AGE_0, AgeId.AGE_1));
        CountingSink sink = new CountingSink();
        AgeChangeProcessor p = new AgeChangeProcessor(ledger, sink);
        check(p.onStageChange("age_1", false, 1), "revoke changes state");
        checkEquals(List.of(AgeId.AGE_1), sink.revoked, "revoke warning");
        checkEquals(1, sink.reloads.size(), "reload on revoke");
        check(!p.onStageChange("age_1", false, 2), "second revoke is a no-op");
        checkEquals(EnumSet.of(AgeId.DAWN, AgeId.AGE_0), ledger.snapshot().unlocked(), "ages");
        return "revoked age_1";
    }

    private static String dawnNeverRevoked() {
        AgeLedger ledger = new AgeLedger();
        CountingSink sink = new CountingSink();
        check(!new AgeChangeProcessor(ledger, sink).onStageChange("dawn", false, 1), "dawn revoke changed state");
        check(ledger.snapshot().isUnlocked(AgeId.DAWN), "dawn unlocked");
        check(AgeSnapshot.of(List.of(), 0, 0).isUnlocked(AgeId.DAWN), "empty snapshot contains dawn");
        return "dawn stays";
    }

    private static String bulkOnlyAdds() {
        AgeLedger ledger = new AgeLedger(snap(1, AgeId.AGE_0, AgeId.AGE_1, AgeId.AGE_2));
        CountingSink sink = new CountingSink();
        AgeChangeProcessor p = new AgeChangeProcessor(ledger, sink);
        // A new player's own team on login: only dawn. Must not remove anything.
        checkEquals(List.of(), p.onStagesPresent(List.of("dawn"), "bulk LOGIN"), "added");
        checkEquals(0, sink.persists, "persists");
        checkEquals(List.of(AgeId.AGE_3), p.onStagesPresent(List.of("dawn", "age_0", "age_3", "mob_3"), "bulk TEAM_SYNC"), "added");
        checkEquals(1, sink.reloads.size(), "reload requests");
        checkEquals(EnumSet.of(AgeId.DAWN, AgeId.AGE_0, AgeId.AGE_1, AgeId.AGE_2, AgeId.AGE_3), ledger.snapshot().unlocked(), "ages");
        return "bulk added age_3 only";
    }

    // ---------------------------------------------------------------- tags

    private static AgeTagResolver.Source source(Map<String, List<String>> files) {
        return tagId -> {
            List<String> texts = files.get(tagId);
            if (texts == null) return List.of();
            List<JsonElement> out = new ArrayList<>();
            for (String t : texts) out.add(JsonParser.parseString(t));
            return out;
        };
    }

    private static String tagResolver() {
        Map<String, List<String>> files = new HashMap<>();
        files.put("firmages:age_blocks/age_2", List.of(
            "{\"values\": [\"tfc:ore/normal_tin/granite\", \"#c:ores/tin\", {\"id\": \"#c:ores/optional\", \"required\": false}]}"));
        files.put("c:ores/tin", List.of(
            "{\"values\": [\"oldmod:tin_ore\"]}",
            "{\"replace\": true, \"values\": [\"tfc:ore/rich_tin/granite\", \"#c:ores/tin_deep\", \"stone\"], \"remove\": [\"minecraft:stone\"]}"));
        files.put("c:ores/tin_deep", List.of("{\"values\": [\"tfc:ore/poor_tin/granite\"]}"));
        AgeTagResolver r = new AgeTagResolver(source(files));
        Set<String> got = r.resolve("firmages:age_blocks/age_2");
        checkEquals(Set.of("tfc:ore/normal_tin/granite", "tfc:ore/rich_tin/granite", "tfc:ore/poor_tin/granite"), got, "resolved");
        checkEquals(List.of(), r.warnings(), "warnings (optional missing tag is silent)");
        check(!r.exists("firmages:age_blocks/age_3"), "missing tag exists");
        checkEquals(Set.of(), r.resolve("firmages:age_blocks/age_3"), "missing tag resolves empty");
        return got.size() + " ids";
    }

    private static String tagResolverCycle() {
        Map<String, List<String>> files = new HashMap<>();
        files.put("a:x", List.of("{\"values\": [\"a:one\", \"#a:y\", \"#a:missing\"]}"));
        files.put("a:y", List.of("{\"values\": [\"a:two\", \"#a:x\"]}"));
        AgeTagResolver r = new AgeTagResolver(source(files));
        Set<String> got = r.resolve("a:x");
        check(got.containsAll(Set.of("a:one", "a:two")), "resolved " + got);
        check(r.warnings().stream().anyMatch(w -> w.contains("cycle")), "cycle warning in " + r.warnings());
        check(r.warnings().stream().anyMatch(w -> w.contains("#a:missing")), "missing-tag warning in " + r.warnings());
        return r.warnings().size() + " warnings";
    }

    private static String earliestWins() {
        Map<String, List<String>> files = new HashMap<>();
        files.put("firmages:age_items/age_1", List.of("{\"values\": [\"tfc:metal/ingot/copper\", \"tfc:metal/ingot/bronze\"]}"));
        files.put("firmages:age_items/age_2", List.of("{\"values\": [\"tfc:metal/ingot/bronze\", \"tfc:metal/ingot/wrought_iron\"]}"));
        files.put("firmages:age_items/dawn", List.of("{\"values\": []}"));
        AgeAssignment a = AgeAssignment.assign(new AgeTagResolver(source(files)), "age_items");
        checkEquals(AgeId.AGE_1, a.byId().get("tfc:metal/ingot/bronze"), "bronze");
        checkEquals(AgeId.AGE_2, a.byId().get("tfc:metal/ingot/wrought_iron"), "wrought iron");
        checkEquals(1, a.duplicates().size(), "duplicates");
        checkEquals(2, a.perAge().get(AgeId.AGE_1), "age_1 count");
        checkEquals(1, a.perAge().get(AgeId.AGE_2), "age_2 count");
        check(a.missingTags().contains(AgeId.AGE_9) && !a.missingTags().contains(AgeId.DAWN), "missing tags " + a.missingTags());
        return a.duplicates().get(0);
    }
}
