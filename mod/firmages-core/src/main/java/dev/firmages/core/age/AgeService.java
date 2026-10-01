package dev.firmages.core.age;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageChangeEvent;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.api.StagesBulkChangedEvent;
import com.enviouse.progressivestages.common.stage.StageManager;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.ceremony.CeremonyService;
import dev.firmages.core.compat.progressivestages.LockSyncDedupe;
import dev.firmages.core.shrine.ShrineService;
import dev.firmages.core.compat.ftbteams.FtbTeamsCompat;
import dev.firmages.core.config.EarlyServerConfig;
import dev.firmages.core.gate.RecipeGate;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.net.ReloadStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns the server-global Age set (SPEC §3): mirrors ProgressiveStages into {@link AgeState}, writes the mirror
 * file, answers the snapshot rules of §3.2 and drives the {@link ReloadScheduler}.
 * Everything except {@link #snapshotForReload()} runs on the server thread.
 */
public final class AgeService {

    /** Where the snapshot of the initial datapack load came from (SPEC §3.2). */
    public enum BootSource { MIRROR, FALLBACK_NO_MIRROR, FALLBACK_CORRUPT_MIRROR, FALLBACK_NOT_DEDICATED }

    public record BootSnapshot(AgeSnapshot snapshot, BootSource source, String detail, Path mirrorPath) {}

    private static volatile MinecraftServer server;
    private static volatile AgeState liveState;
    private static AgeChangeProcessor processor;
    private static ReloadScheduler scheduler;

    private static BootSnapshot boot;
    private static volatile boolean bootUsed;
    private static boolean bootReconcileRequested;

    private static final Set<UUID> teamsWithAges = new HashSet<>();
    private static boolean multiTeamDetected;
    private static String lastMirrorWrite = "not written yet";
    private static String lastReloadResult = "none since start";
    /** Server tick at which the warm-up reload is due, -1 when none is pending. */
    private static long warmupDueTick = -1;

    private AgeService() {}

    // ---------------------------------------------------------------- snapshot rules (§3.2)

    /**
     * The Age snapshot the current datapack load must use: the live AgeState while a server runs, else (initial
     * load) the mirror file on a dedicated server, else the fail-strict boot fallback. Thread-safe.
     */
    public static AgeSnapshot snapshotForReload() {
        AgeState live = liveState;
        if (live != null) return live.snapshot();
        bootUsed = true;
        return bootSnapshot().snapshot();
    }

    public static synchronized BootSnapshot bootSnapshot() {
        if (boot == null) {
            boot = computeBoot();
            FirmagesCore.LOGGER.info("Boot Age snapshot {} from {} ({}); mirror path {}", boot.snapshot().unlockedIds(),
                boot.source(), boot.detail(), boot.mirrorPath());
        }
        return boot;
    }

    private static BootSnapshot computeBoot() {
        if (!FMLEnvironment.dist.isDedicatedServer()) {
            return new BootSnapshot(fallbackSnapshot(null), BootSource.FALLBACK_NOT_DEDICATED, "singleplayer or dev client", null);
        }
        Path worldDir = EarlyServerConfig.dedicatedWorldDir();
        Path mirror = worldDir.resolve(AgeMirror.RELATIVE_PATH);
        AgeMirror.ReadResult read = AgeMirror.read(mirror);
        AgeSnapshot snap = AgeMirror.snapshotOrFallback(read, read.status() == AgeMirror.Status.OK ? null : fallbackSnapshot(worldDir));
        return switch (read.status()) {
            case OK -> new BootSnapshot(snap, BootSource.MIRROR, "mirror ok", mirror);
            case MISSING -> new BootSnapshot(snap, BootSource.FALLBACK_NO_MIRROR, read.detail(), mirror);
            case CORRUPT -> {
                FirmagesCore.LOGGER.error("Age mirror {} is corrupt ({}); using the strict boot fallback", mirror, read.detail());
                yield new BootSnapshot(snap, BootSource.FALLBACK_CORRUPT_MIRROR, read.detail(), mirror);
            }
        };
    }

    /**
     * {@code gate.bootFallbackStages}. Server configs load only after the initial datapack load, so on a dedicated
     * server the config file is read directly (world override, then config/, then defaultconfigs/); otherwise the spec default {@code [dawn]}.
     */
    private static AgeSnapshot fallbackSnapshot(Path worldDir) {
        List<String> stages = ServerConfig.loaded() ? new ArrayList<>(ServerConfig.BOOT_FALLBACK_STAGES.get())
            : EarlyServerConfig.stringList(worldDir, "gate.bootFallbackStages").orElse(null);
        if (stages == null) stages = ServerConfig.DEFAULT_BOOT_FALLBACK;
        EnumSet<AgeId> set = EnumSet.of(AgeId.DAWN);
        for (String s : stages) {
            AgeId.byId(s).ifPresentOrElse(set::add,
                () -> FirmagesCore.LOGGER.warn("gate.bootFallbackStages: ignoring unknown stage '{}'", s));
        }
        return new AgeSnapshot(set, 0, 0);
    }

    // ---------------------------------------------------------------- locked ore blocks (binding)

    /** lockedOreBlocks() answers given during the load in progress, checked against the final index. */
    private static final List<Answer> loadAnswers = new ArrayList<>();
    private static volatile boolean bootAnswersStale;

    /**
     * One lockedOreBlocks() answer given during a load. {@code captured}: given after this mod captured the load's
     * resource manager (a runtime reload's pre-capture, or tag handlers that KubeJS runs inside the tag loader).
     */
    private record Answer(List<String> ids, boolean captured) {}

    /**
     * Registered block ids of {@code age_blocks} of every locked Age, for the load in progress (SPEC §3.4).
     * KubeJS 2101 runs tag handlers as a pre-capture: on a reload at the start of {@code loadResources} (this mod's
     * capture runs first, so the answer uses that load's tag JSON), on the initial load before the resource manager
     * exists (the answer then comes from an empty index). A handler that reads tag contents ({@code getObjectIds()},
     * as the m1 prospecting script does) makes KubeJS drop the pre-capture of that registry and run all its handlers
     * inside the tag loader, where the answer is right on the initial load too. Every answer given during a load is
     * compared with the final index; a wrong answer that took effect triggers one reload right after start
     * (fail-strict), see {@link #checkLoadAnswers}. Scripts must call this only inside {@code ServerEvents.tags}.
     */
    public static List<String> lockedOreBlocks() {
        AgeSnapshot snap = snapshotForReload();
        AgeIndex idx = AgeIndex.loading();
        List<String> ids = lockedBlockIds(idx, snap);
        boolean captured = AgeIndex.loadInProgress();
        boolean inLoad = captured || liveState == null;
        if (inLoad) {
            synchronized (loadAnswers) {
                loadAnswers.add(new Answer(ids, captured));
            }
        }
        FirmagesCore.LOGGER.debug("FirmAges.lockedOreBlocks(): {} blocks (index gen {}, Ages {}, during load: {}, captured: {})",
            ids.size(), idx.generation(), snap.unlockedIds(), inLoad, captured);
        return ids;
    }

    private static List<String> lockedBlockIds(AgeIndex idx, AgeSnapshot snap) {
        return idx.blocks().entrySet().stream()
            .filter(e -> !snap.isUnlocked(e.getValue()))
            .map(e -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.getKey()).toString())
            .sorted()
            .toList();
    }

    /** Apply phase of every datapack load: were the binding's answers during this load right? */
    static void checkLoadAnswers(AgeIndex finalIndex) {
        List<Answer> answers;
        synchronized (loadAnswers) {
            answers = List.copyOf(loadAnswers);
            loadAnswers.clear();
        }
        if (answers.isEmpty()) return;
        if (liveState == null && answers.stream().anyMatch(Answer::captured)) {
            // Initial load, and the block tag handlers also ran inside the tag loader: KubeJS dropped the
            // pre-capture (and with it the answers of its pre-run), so only the in-loader answers took effect.
            long early = answers.stream().filter(a -> !a.captured()).count();
            if (early > 0) {
                FirmagesCore.LOGGER.info("FirmAges.lockedOreBlocks(): ignoring {} pre-capture answer(s) of the initial load; "
                    + "KubeJS ran the tag handlers inside the tag loader", early);
            }
            answers = answers.stream().filter(Answer::captured).toList();
        }
        List<String> correct = lockedBlockIds(finalIndex, snapshotForReload());
        long wrong = answers.stream().filter(a -> !a.ids().equals(correct)).count();
        if (wrong == 0) return;
        if (liveState == null) {
            bootAnswersStale = true;
            FirmagesCore.LOGGER.info("FirmAges.lockedOreBlocks() answered {} time(s) before the initial load's tags were readable "
                + "(KubeJS pre-capture); one reload follows after start", wrong);
        } else {
            FirmagesCore.LOGGER.error("FirmAges.lockedOreBlocks() gave {} stale answer(s) during a runtime reload: the resource "
                + "manager capture ran after the KubeJS pre-capture. Locked ores may leak until the next reload.", wrong);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new AgeReloadListener());
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer s = event.getServer();
        server = s;
        scheduler = new ReloadScheduler(s::getTickCount, ServerConfig::reloadDelayTicks, ServerConfig::coalesceTicks,
            System::nanoTime, AgeService::startReload, AgeService::onReloadFinished);
    }

    public static void onServerStarted(ServerStartedEvent event) {
        reconcileBoot(event.getServer());
    }

    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        server = null;
        liveState = null;
        processor = null;
        scheduler = null;
        boot = null;
        bootUsed = false;
        bootReconcileRequested = false;
        bootAnswersStale = false;
        synchronized (loadAnswers) {
            loadAnswers.clear();
        }
        teamsWithAges.clear();
        multiTeamDetected = false;
        lastMirrorWrite = "not written yet";
        lastReloadResult = "none since start";
        warmupDueTick = -1;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (scheduler == null) return;
        if (warmupDueTick >= 0) warmupTick(event.getServer());
        scheduler.tick();
    }

    // ---------------------------------------------------------------- warm-up reload

    /**
     * {@code gate.warmupReload}: the first reload after a boot is about 1 s slower than the next ones (JIT and class
     * loading of the reload path: KubeJS, recipe parsing, the reload workers; {@code dev/poc-results.md}). On a
     * dedicated server one reload with the same Ages right after start, while nobody is online, takes that cost.
     */
    private static void scheduleWarmup(MinecraftServer s) {
        if (!ServerConfig.warmupReload() || !s.isDedicatedServer() || bootReconcileRequested) return;
        warmupDueTick = s.getTickCount() + Math.max(0, ServerConfig.warmupDelayTicks());
        FirmagesCore.LOGGER.info("Warm-up reload due in {} ticks unless a player joins first (gate.warmupReload)", ServerConfig.warmupDelayTicks());
    }

    private static void warmupTick(MinecraftServer s) {
        if (s.getTickCount() < warmupDueTick) return;
        warmupDueTick = -1;
        if (s.getPlayerCount() > 0) {
            FirmagesCore.LOGGER.info("Warm-up reload skipped: a player is online");
        } else if (scheduler.reloadsStarted() > 0 || scheduler.phase() != ReloadScheduler.Phase.IDLE) {
            FirmagesCore.LOGGER.info("Warm-up reload skipped: a reload already ran or is pending");
        } else {
            scheduler.requestNow("warm-up after boot");
        }
    }

    private static void cancelWarmup(String why) {
        if (warmupDueTick < 0) return;
        warmupDueTick = -1;
        FirmagesCore.LOGGER.info("Warm-up reload skipped: {}", why);
    }

    /** Loads AgeState on the server thread (once per server). */
    public static AgeState state(MinecraftServer s) {
        AgeState st = liveState;
        if (st == null) {
            st = AgeState.get(s);
            processor = new AgeChangeProcessor(st.ledger(), new Sink(s, st));
            liveState = st;
        }
        return st;
    }

    private static void reconcileBoot(MinecraftServer s) {
        boolean wasUsed = bootUsed;
        BootSnapshot b = bootSnapshot();
        AgeState st = state(s);
        // Union with what ProgressiveStages already stores for every known team.
        List<String> fromTeams = new ArrayList<>();
        for (UUID team : knownTeams()) {
            Set<String> stages = stagesOfTeam(team);
            noteTeam(s, team, stages);
            fromTeams.addAll(stages);
        }
        List<AgeId> added = processor.onStagesPresent(fromTeams, "boot union");
        if (!added.isEmpty()) FirmagesCore.LOGGER.info("Boot: ProgressiveStages teams hold Ages missing from AgeState: {}", added);
        AgeSnapshot now = st.snapshot();
        Path mirror = mirrorPath(s);
        AgeMirror.ReadResult current = AgeMirror.read(mirror);
        if (current.snapshot().map(m -> !m.sameAges(now)).orElse(true)) writeMirror(s, now);
        if (!added.isEmpty()) {
            bootReconcileRequested = true;
        } else if (wasUsed && !b.snapshot().sameAges(now)) {
            FirmagesCore.LOGGER.info("Boot: the initial datapack load used {} from {}, AgeState is {}; reloading once",
                b.snapshot().unlockedIds(), b.source(), now.unlockedIds());
            bootReconcileRequested = true;
            scheduler.requestNow("boot reconcile");
        } else if (bootAnswersStale) {
            bootReconcileRequested = true;
            scheduler.requestNow("boot reconcile: FirmAges answers of the initial load");
        } else if (wasUsed) {
            FirmagesCore.LOGGER.info("Boot: AgeState {} matches the boot snapshot ({}) used during the initial load; no reload",
                now.unlockedIds(), b.source());
        } else {
            FirmagesCore.LOGGER.info("Boot: nothing read the boot snapshot {} ({}) during the initial load; AgeState {}; no reload",
                b.snapshot().unlockedIds(), b.source(), now.unlockedIds());
        }
        AgeCoverage.check(AgeIndex.current());
        if (AgeIndex.current().misconfigured()) {
            FirmagesCore.LOGGER.error("All firmages:age_* tags are empty: the Age gate cannot lock anything");
        }
        scheduleWarmup(s);
    }

    // ---------------------------------------------------------------- ProgressiveStages events (§3.1)

    public static void onStageChange(StageChangeEvent event) {
        MinecraftServer s = server;
        if (s == null) return;
        state(s);
        String stage = event.getStageId().getPath();
        if (event.wasGranted() && AgeId.byId(stage).isPresent()) {
            noteTeam(s, event.getTeamId(), Set.of(stage));
        }
        processor.onStageChange(stage, event.wasGranted(), s.getTickCount());
    }

    public static void onStagesBulkChanged(StagesBulkChangedEvent event) {
        MinecraftServer s = server;
        if (s == null) return;
        state(s);
        Set<String> stages = paths(event.getCurrentStages());
        noteTeam(s, event.getTeamId(), stages);
        processor.onStagesPresent(stages, "bulk " + event.getReason());
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        MinecraftServer s = server;
        if (s == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        cancelWarmup(player.getGameProfile().getName() + " joined");
        state(s);
        processor.onStagesPresent(paths(ProgressiveStagesAPI.getStages(player)), "login of " + player.getGameProfile().getName());
        if (s.getPlayerList().isOp(player.getGameProfile())) {
            if (AgeIndex.current().misconfigured()) {
                player.sendSystemMessage(warn("firmages.warn.misconfigured",
                    "[Firmament Ages] All firmages:age_* tags are empty: the Age gate cannot lock anything."));
            }
            if (RecipeGate.lastFailure() != null) {
                player.sendSystemMessage(warn("firmages.warn.gate_failed",
                    "[Firmament Ages] The recipe Age gate failed on the last datapack load; recipes are not Age-filtered. See the server log."));
            }
            if (multiTeamDetected) {
                player.sendSystemMessage(warn("firmages.warn.multiple_teams",
                    "[Firmament Ages] More than one team holds Age stages. The pack expects one shared team; Ages are the union of all teams."));
            }
        }
    }

    private static Set<String> paths(Collection<StageId> ids) {
        return ids.stream().map(StageId::getPath).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ---------------------------------------------------------------- one-team guard (§2.3)

    private static List<UUID> knownTeams() {
        Set<UUID> ids = new LinkedHashSet<>(teamsWithAges);
        if (ModList.get().isLoaded(FtbTeamsCompat.MOD_ID)) {
            try {
                ids.addAll(FtbTeamsCompat.teamIds());
            } catch (RuntimeException | LinkageError e) {
                FirmagesCore.LOGGER.warn("Cannot list FTB teams: {}", e.toString());
            }
        }
        return new ArrayList<>(ids);
    }

    private static Set<String> stagesOfTeam(UUID team) {
        try {
            return paths(StageManager.getInstance().getStages(team));
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private static void noteTeam(MinecraftServer s, UUID team, Collection<String> stages) {
        if (team == null) return;
        boolean hasAge = stages.stream().anyMatch(id -> AgeId.byId(id).filter(a -> a != AgeId.DAWN).isPresent());
        if (!hasAge || !teamsWithAges.add(team) || teamsWithAges.size() < 2) return;
        if (!multiTeamDetected) {
            multiTeamDetected = true;
            FirmagesCore.LOGGER.warn("One-team guard: {} teams hold Age stages ({}). The pack expects one shared team; "
                + "AgeState stays the union of all teams.", teamsWithAges.size(), teamsWithAges);
            messageOps(s, warn("firmages.warn.multiple_teams",
                "[Firmament Ages] More than one team holds Age stages. The pack expects one shared team; Ages are the union of all teams."));
        }
    }

    public static boolean multiTeamDetected() {
        return multiTeamDetected;
    }

    public static Set<UUID> teamsWithAges() {
        return Set.copyOf(teamsWithAges);
    }

    // ---------------------------------------------------------------- operator actions

    /** {@code /firmages ages sync}: adds every Age that ProgressiveStages knows (teams, online players), then reloads. */
    public static List<AgeId> sync(MinecraftServer s) {
        state(s);
        List<String> stages = new ArrayList<>();
        for (UUID team : knownTeams()) stages.addAll(stagesOfTeam(team));
        for (ServerPlayer p : s.getPlayerList().getPlayers()) stages.addAll(paths(ProgressiveStagesAPI.getStages(p)));
        List<AgeId> added = processor.onStagesPresent(stages, "sync");
        if (added.isEmpty()) scheduler.requestNow("sync");
        return added;
    }

    /** {@code /firmages ages simulate}: changes AgeState without ProgressiveStages. @return true if it changed. */
    public static boolean simulate(MinecraftServer s, AgeId age, boolean grant) {
        state(s);
        return processor.onStageChange(age.id(), grant, s.getTickCount());
    }

    /** {@code /firmages reload}: filtered reload on the next tick. */
    public static void forceReload(String reason) {
        if (scheduler != null) scheduler.requestNow(reason);
    }

    // ---------------------------------------------------------------- reload (§3.3)

    private static java.util.concurrent.CompletableFuture<?> startReload(String reason) {
        MinecraftServer s = server;
        FirmagesCore.LOGGER.info("Age reload starting ({}), Ages {}", reason, snapshotForReload().unlockedIds());
        PacketDistributor.sendToAllPlayers(new ReloadStatePayload(true));
        return s.reloadResources(s.getPackRepository().getSelectedIds());
    }

    private static void onReloadFinished(String reason, long millis, Throwable error) {
        MinecraftServer s = server;
        if (s == null) return;
        int gen = CacheGeneration.bump();
        PacketDistributor.sendToAllPlayers(new ReloadStatePayload(false));
        if (error != null) {
            lastReloadResult = "FAILED after " + millis + " ms: " + error;
            FirmagesCore.LOGGER.error("Age reload ({}) failed after {} ms", reason, millis, error);
            messageOps(s, warn("firmages.reload.failed", "[Firmament Ages] Age reload failed, see the server log."));
            return;
        }
        lastReloadResult = "ok in " + millis + " ms (" + reason + ")";
        FirmagesCore.LOGGER.info("Age reload ({}) finished in {} ms; reload {} since start; cache generation {}; duplicate PS lock syncs "
            + "skipped so far: {} (and {} on join)", reason, millis, scheduler == null ? -1 : scheduler.reloadsStarted(), gen,
            LockSyncDedupe.skipped(), LockSyncDedupe.skippedOnJoin());
        AgeState st = state(s);
        st.ledger().setLastReloadGameTime(s.overworld().getGameTime());
        st.setDirty();
        writeMirror(s, st.snapshot());
    }

    // ---------------------------------------------------------------- persistence

    public static Path mirrorPath(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve(AgeMirror.RELATIVE_PATH).normalize();
    }

    private static void writeMirror(MinecraftServer s, AgeSnapshot snap) {
        Path p = mirrorPath(s);
        try {
            AgeMirror.write(p, snap, Instant.now());
            lastMirrorWrite = "ok at " + Instant.now() + " (version " + snap.version() + ")";
        } catch (IOException e) {
            lastMirrorWrite = "FAILED: " + e;
            FirmagesCore.LOGGER.error("Cannot write the Age mirror {}", p, e);
        }
    }

    private record Sink(MinecraftServer s, AgeState st) implements AgeChangeProcessor.Sink {
        @Override
        public void persist() {
            st.setDirty();
            writeMirror(s, st.snapshot());
            FirmagesCore.LOGGER.info("AgeState now {} (version {})", st.snapshot().unlockedIds(), st.snapshot().version());
        }

        @Override
        public void requestReload(String reason, boolean revoke) {
            if (revoke && !ServerConfig.reloadOnRevoke()) {
                FirmagesCore.LOGGER.info("Not reloading after '{}' (gate.reloadOnRevoke = false)", reason);
                return;
            }
            if (scheduler != null) scheduler.request(reason);
        }

        @Override
        public void revoked(AgeId age) {
            FirmagesCore.LOGGER.warn("Age {} revoked: restart the server to clear in-progress items", age.id());
            messageOps(s, warn("firmages.age.revoked.warning",
                "[Firmament Ages] Age revoked: restart the server to clear in-progress items"));
            ShrineService.onAgeChanged(s);
        }

        @Override
        public void granted(AgeId age) {
            CeremonyService.onAgeGranted(s, age);
            ShrineService.onAgeChanged(s);
        }
    }

    // ---------------------------------------------------------------- status

    public record Status(AgeSnapshot live, BootSnapshot boot, boolean bootUsed, boolean bootReconcileRequested, boolean bootAnswersStale,
                         Path mirrorPath, AgeMirror.ReadResult mirrorOnDisk, String lastMirrorWrite,
                         ReloadScheduler.Phase phase, long ticksUntilDue, boolean followUpQueued, int reloadsStarted,
                         long lastReloadMillis, String lastReloadResult) {}

    public static Status status(MinecraftServer s) {
        AgeState st = state(s);
        Path mp = mirrorPath(s);
        ReloadScheduler sch = scheduler;
        return new Status(st.snapshot(), bootSnapshot(), bootUsed, bootReconcileRequested, bootAnswersStale, mp, AgeMirror.read(mp), lastMirrorWrite,
            sch == null ? ReloadScheduler.Phase.IDLE : sch.phase(), sch == null ? -1 : sch.ticksUntilDue(),
            sch != null && sch.followUpQueued(), sch == null ? 0 : sch.reloadsStarted(),
            sch == null ? -1 : sch.lastDurationMillis(), lastReloadResult);
    }

    // ---------------------------------------------------------------- helpers

    static Component warn(String key, String fallback) {
        return Component.translatableWithFallback(key, fallback).withStyle(ChatFormatting.GOLD);
    }

    static void messageOps(MinecraftServer s, Component msg) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (s.getPlayerList().isOp(p.getGameProfile())) p.sendSystemMessage(msg);
        }
    }
}
