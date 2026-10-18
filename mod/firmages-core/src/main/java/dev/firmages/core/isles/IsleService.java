package dev.firmages.core.isles;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.compat.ftbchunks.Claims;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.shrine.ShrineSavedData;
import dev.firmages.core.sky.DeferredSites;
import dev.firmages.core.sky.SkyTags;
import dev.firmages.core.sky.StarfallService;
import dev.firmages.core.wayshrine.WayshrinePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Drifting Isles at run time (SPEC §30.3, §30.5): the lode re-forms on the next tick; the fallback cluster of an
 * existing world is recorded once the world is in the Industrial Age and placed through {@link DeferredSites} once its
 * chunks are loaded, at most {@link #BLOCKS_PER_TICK} block changes per tick; the Starfall probe keeps craters from
 * under isles; a player sent to the world spawn who lands on an isle is set down on the ground below it.
 */
public final class IsleService {
    public static final String KIND = "isle";
    public static final int BLOCKS_PER_TICK = 64;
    /** No fallback cluster within this distance of the world spawn (a fresh player must never spawn on top of it). */
    public static final int SPAWN_CLEARANCE = 96;
    static final int FALLBACK_MIN = 300;

    /** False in the GameTest run: no fallback check (the tests place clusters themselves). */
    private static volatile boolean naturalEvents = true;
    private static boolean checkedThisStart;
    private static final Deque<Job> JOBS = new ArrayDeque<>();
    private static final Set<Long> ACTIVE = new HashSet<>();
    /** Most block changes the placer made in one tick since start (the GameTest reads it). */
    private static int maxPerTick;
    private static final StarfallService.IsleProbe PROBE = IsleService::insideIsle;

    private IsleService() {}

    public static void setNaturalEvents(boolean on) {
        naturalEvents = on;
    }

    public static void register(IEventBus bus) {
        bus.addListener(IsleService::onServerStarted);
        bus.addListener(IsleService::onServerStopped);
        bus.addListener(IsleService::onServerTick);
        bus.addListener(IsleService::onRespawn);
        bus.addListener(IsleService::onLogin);
        DeferredSites.handler(KIND, IsleService::ready);
        StarfallService.registerIsleProbe(PROBE);
    }

    // ---- events

    private static void onServerStarted(ServerStartedEvent event) {
        checkedThisStart = false;
        IsleSavedData d = IsleSavedData.get(event.getServer());
        int n = 0;
        for (IsleSavedData.Cluster c : d.clusters()) n += registerCluster(c);
        if (n > 0) FirmagesCore.LOGGER.info("Drifting Isles: {} isles wait for their chunks", n);
        if (!d.reform().isEmpty()) FirmagesCore.LOGGER.info("Drifting Isles: {} lodes wait to re-form", d.reform().size());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        JOBS.clear();
        ACTIVE.clear();
        checkedThisStart = false;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        reformStep(server);
        placeStep(server);
        fallbackStep(server);
    }

    // ---- the re-forming lode (SPEC §30.5)

    /** Called by the lode's {@code onRemove}: the next server tick sets it back. */
    public static void queueReform(ServerLevel level, BlockPos pos) {
        IsleSavedData.get(level.getServer()).queueReform(GlobalPos.of(level.dimension(), pos.immutable()));
    }

    static void reformStep(MinecraftServer server) {
        IsleSavedData d = IsleSavedData.get(server);
        if (d.reform().isEmpty()) return;
        for (GlobalPos g : List.copyOf(d.reform())) {
            ServerLevel level = server.getLevel(g.dimension());
            if (level == null) {
                d.unqueueReform(g);
                continue;
            }
            if (!level.isLoaded(g.pos())) continue; // waits for its chunk
            d.unqueueReform(g);
            BlockState now = level.getBlockState(g.pos());
            if (!FirmamentLodeBlock.reformsInto(now)) continue; // something took its place: the lode stays gone
            level.setBlock(g.pos(), IsleRegistry.LODE.get().defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, g.pos(), IsleRegistry.LODE_REFORM.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    // ---- placement by code (fallback and operator clusters)

    private interface Step {
        int cost();

        /** Runs the step; false when a chunk it needs is not loaded (the job pauses). */
        boolean run(ServerLevel level);
    }

    private record BlockStep(BlockPos pos, BlockState state) implements Step {
        @Override
        public int cost() {
            return 1;
        }

        @Override
        public boolean run(ServerLevel level) {
            if (!level.isLoaded(pos)) return false;
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            return true;
        }
    }

    private record TreeStep(IsleShape shape, IsleShape.Tree tree) implements Step {
        @Override
        public int cost() {
            return 16;
        }

        @Override
        public boolean run(ServerLevel level) {
            BlockPos p = new BlockPos(tree.x(), tree.y(), tree.z());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) if (!level.isLoaded(p.offset(dx * 8, 0, dz * 8))) return false;
            }
            IsleWriter.tree(level, level.getChunkSource().getGenerator(), shape, tree);
            return true;
        }
    }

    private record ShrineStep(IsleSpec spec, BoundingBox slice) implements Step {
        @Override
        public int cost() {
            return slice.getXSpan() * slice.getYSpan() * slice.getZSpan();
        }

        @Override
        public boolean run(ServerLevel level) {
            if (!level.isLoaded(new BlockPos(slice.minX(), slice.minY(), slice.minZ())) || !level.isLoaded(new BlockPos(slice.maxX(), slice.minY(), slice.maxZ()))) {
                return false;
            }
            IsleWriter.shrine(level, spec, slice);
            return true;
        }
    }

    private record RuinStep(IsleSpec spec, BoundingBox slice) implements Step {
        @Override
        public int cost() {
            return slice.getXSpan() * slice.getYSpan() * slice.getZSpan();
        }

        @Override
        public boolean run(ServerLevel level) {
            if (!level.isLoaded(new BlockPos(slice.minX(), slice.minY(), slice.minZ())) || !level.isLoaded(new BlockPos(slice.maxX(), slice.minY(), slice.maxZ()))) {
                return false;
            }
            IsleRuin.place(level, spec, slice);
            return true;
        }
    }

    /** The isle ruin's box (G3) cut into one-layer slices. */
    static List<BoundingBox> ruinSlices(ServerLevel level, IsleSpec spec) {
        Optional<StructureTemplate> t = level.getStructureManager().get(IsleRuin.TEMPLATE);
        if (t.isEmpty()) return List.of();
        Optional<BoundingBox> box = IsleRuin.box(t.get(), spec);
        if (box.isEmpty()) return List.of();
        List<BoundingBox> out = new ArrayList<>();
        BoundingBox b = box.get();
        for (int y = b.minY(); y <= b.maxY(); y++) out.add(new BoundingBox(b.minX(), y, b.minZ(), b.maxX(), y, b.maxZ()));
        return out;
    }

    private static final class Job {
        final long cluster;
        final int isle;
        final List<Step> steps;
        int next;

        Job(long cluster, int isle, List<Step> steps) {
            this.cluster = cluster;
            this.isle = isle;
            this.steps = steps;
        }
    }

    static long siteId(long cluster, int isle) {
        return cluster * 16 + isle;
    }

    /** The chunks under an isle's box (its body, trees and the shrine). */
    static long[] chunks(IsleSpec s) {
        BoundingBox b = IslePiece.box(s);
        List<Long> out = new ArrayList<>();
        for (int cx = (b.minX() - 8) >> 4; cx <= (b.maxX() + 8) >> 4; cx++) {
            for (int cz = (b.minZ() - 8) >> 4; cz <= (b.maxZ() + 8) >> 4; cz++) out.add(ChunkPos.asLong(cx, cz));
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    /** Registers every isle of the cluster that does not stand yet; returns how many. */
    static int registerCluster(IsleSavedData.Cluster c) {
        int n = 0;
        for (int i = 0; i < c.isles.size(); i++) {
            if (c.placed[i]) continue;
            DeferredSites.register(Level.OVERWORLD, KIND, siteId(c.id, i), chunks(c.isles.get(i)));
            n++;
        }
        return n;
    }

    /** {@link DeferredSites} handler: every chunk of the isle is loaded at FULL. */
    static void ready(ServerLevel level, long id) {
        long clusterId = id / 16;
        int isle = (int) (id % 16);
        IsleSavedData d = IsleSavedData.get(level.getServer());
        Optional<IsleSavedData.Cluster> oc = d.cluster(clusterId);
        if (oc.isEmpty() || isle >= oc.get().isles.size() || oc.get().placed[isle]) {
            DeferredSites.unregister(Level.OVERWORLD, KIND, id);
            return;
        }
        if (!ACTIVE.add(id)) return;
        DeferredSites.unregister(Level.OVERWORLD, KIND, id);
        IsleSavedData.Cluster c = oc.get();
        IsleSpec spec = c.isles.get(isle);
        IsleShape shape = IsleShape.of(spec);
        String blocker = obstruction(level, shape);
        if (blocker != null) {
            ACTIVE.remove(id);
            if (spec.shrine() && c.origin == IsleSavedData.Origin.FALLBACK) {
                FirmagesCore.LOGGER.warn("Drifting Isles: the fallback cluster at {} {} is blocked ({}); choosing another site", c.x, c.z, blocker);
                d.remove(c.id);
                for (int i = 0; i < c.isles.size(); i++) DeferredSites.unregister(Level.OVERWORLD, KIND, siteId(c.id, i));
                newFallback(level, anchor(level.getServer()), RandomSource.create());
            } else {
                FirmagesCore.LOGGER.warn("Drifting Isles: isle {} of cluster {} at {} {} skipped ({})", isle, c.id, spec.x(), spec.z(), blocker);
                d.markPlaced(c.id, isle, true);
            }
            return;
        }
        List<Step> steps = new ArrayList<>();
        shape.emitAll((x, y, z, kind) -> steps.add(new BlockStep(new BlockPos(x, y, z), IsleWriter.state(kind))));
        for (IsleShape.Tree t : shape.trees()) steps.add(new TreeStep(shape, t));
        if (spec.shrine()) for (BoundingBox slice : shrineSlices(level, spec)) steps.add(new ShrineStep(spec, slice));
        if (spec.ruin()) for (BoundingBox slice : ruinSlices(level, spec)) steps.add(new RuinStep(spec, slice));
        JOBS.add(new Job(c.id, isle, steps));
        FirmagesCore.LOGGER.info("Drifting Isles: placing isle {} of cluster {} at {} {} (top {}, {} steps)", isle, c.id, spec.x(), spec.z(), spec.top(),
            steps.size());
    }

    /** Why the isle cannot stand here (a block in its body or above the shrine's floor), or null. */
    static String obstruction(ServerLevel level, IsleShape shape) {
        String[] out = new String[1];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        shape.emitAll((x, y, z, kind) -> {
            if (out[0] == null && !level.getBlockState(p.set(x, y, z)).isAir()) out[0] = "a block at " + p.toShortString();
        });
        if (out[0] != null || !shape.spec().shrine()) return out[0];
        for (BoundingBox slice : shrineSlices(level, shape.spec())) {
            for (BlockPos q : BlockPos.betweenClosed(slice.minX(), Math.max(slice.minY(), shape.spec().top() + 1), slice.minZ(), slice.maxX(),
                slice.maxY(), slice.maxZ())) {
                if (!level.getBlockState(q).isAir()) return "a block above the shrine at " + q.toShortString();
            }
        }
        return null;
    }

    /** The Wind Shrine's ruin box cut into slices of at most {@link #BLOCKS_PER_TICK} blocks (one layer, a few rows). */
    static List<BoundingBox> shrineSlices(ServerLevel level, IsleSpec spec) {
        Optional<StructureTemplate> t = WayshrinePlacement.template(level, WayshrinePlacement.ISLE_TYPE);
        if (t.isEmpty()) return List.of();
        BlockPos core = IsleWriter.shrineCore(spec);
        var rot = IsleWriter.shrineRotation(spec);
        BlockPos origin = core.subtract(WayshrinePlacement.coreOffset(t.get()).rotate(rot));
        BoundingBox box = t.get().getBoundingBox(new StructurePlaceSettings().setRotation(rot), origin);
        int width = box.getXSpan();
        int rows = Math.max(1, BLOCKS_PER_TICK / width);
        List<BoundingBox> out = new ArrayList<>();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z += rows) {
                out.add(new BoundingBox(box.minX(), y, z, box.maxX(), y, Math.min(box.maxZ(), z + rows - 1)));
            }
        }
        return out;
    }

    static void placeStep(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        ServerLevel level = server.overworld();
        int budget = BLOCKS_PER_TICK;
        int done = 0;
        while (!JOBS.isEmpty()) {
            Job job = JOBS.peek();
            if (job.next >= job.steps.size()) {
                JOBS.poll();
                finish(server, job);
                continue;
            }
            Step step = job.steps.get(job.next);
            if (step.cost() > budget && done > 0) break;
            if (!step.run(level)) {
                JOBS.poll();
                ACTIVE.remove(siteId(job.cluster, job.isle));
                IsleSavedData.get(server).cluster(job.cluster).ifPresent(c -> {
                    DeferredSites.register(Level.OVERWORLD, KIND, siteId(c.id, job.isle), chunks(c.isles.get(job.isle)));
                    FirmagesCore.LOGGER.info("Drifting Isles: isle {} of cluster {} paused (its chunks unloaded); it resumes when they load", job.isle, c.id);
                });
                continue;
            }
            job.next++;
            budget -= step.cost();
            done += step.cost();
            if (budget <= 0) break;
        }
        maxPerTick = Math.max(maxPerTick, done);
    }

    private static void finish(MinecraftServer server, Job job) {
        ACTIVE.remove(siteId(job.cluster, job.isle));
        IsleSavedData d = IsleSavedData.get(server);
        d.markPlaced(job.cluster, job.isle, false);
        d.cluster(job.cluster).ifPresent(c -> FirmagesCore.LOGGER.info("Drifting Isles: isle {} of cluster {} stands at {} {}{}", job.isle, c.id,
            c.isles.get(job.isle).x(), c.isles.get(job.isle).z(), c.isles.get(job.isle).shrine() ? " with the Wind Shrine" : ""));
    }

    public static int maxPerTick() {
        return maxPerTick;
    }

    public static void resetMaxPerTick() {
        maxPerTick = 0;
    }

    public static int jobs() {
        return JOBS.size();
    }

    // ---- the fallback cluster of an existing world (SPEC §30.3)

    /** The heart of the shrine in the overworld, else the world spawn. */
    public static BlockPos anchor(MinecraftServer server) {
        return ShrineSavedData.get(server).heart().filter(h -> h.dimension() == Level.OVERWORLD).map(GlobalPos::pos)
            .orElse(server.overworld().getSharedSpawnPos());
    }

    private static void fallbackStep(MinecraftServer server) {
        if (checkedThisStart || !naturalEvents || !ServerConfig.islesFallback()) return;
        if (server.overworld().getGameTime() % 100 != 0) return;
        if (StarfallService.currentAge(server).ordinal() < AgeId.AGE_4.ordinal()) return;
        checkedThisStart = true;
        FirmagesCore.LOGGER.info("Drifting Isles: fallback check: {}", ensureFallback(server));
    }

    /**
     * The fallback check: a fallback record or a known isle start within {@code isles.fallbackRadius} of the heart, else
     * a structure search over the isle set, else a new fallback cluster 300 to 600 blocks from the heart.
     */
    public static String ensureFallback(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        BlockPos anchor = anchor(server);
        int radius = ServerConfig.islesFallbackRadius();
        IsleSavedData d = IsleSavedData.get(server);
        Optional<IsleSavedData.Cluster> fb = d.fallback();
        if (fb.isPresent()) return "fallback cluster " + fb.get().id + " at " + fb.get().x + " " + fb.get().z + (fb.get().done() ? " stands" : " waits");
        Optional<BlockPos> known = d.knownStart();
        if (known.isPresent() && horizontal(known.get(), anchor) <= radius) return "known isle start at " + known.get().toShortString();
        List<IsleSearch.Found> found = IsleSearch.nearest(ow, anchor, radius, c -> true, 1);
        if (!found.isEmpty()) {
            IsleSpec m = found.get(0).main();
            d.setKnownStart(new BlockPos(m.x(), m.top(), m.z()));
            return "isle start at " + m.x() + " " + m.z() + ", " + (int) found.get(0).distance() + " blocks from " + anchor.toShortString();
        }
        return newFallback(ow, anchor, RandomSource.create()).map(c -> "new fallback cluster " + c.id + " at " + c.x + " " + c.z + " ("
            + (int) Math.hypot(c.x - anchor.getX(), c.z - anchor.getZ()) + " blocks from " + anchor.toShortString() + ", " + c.isles.size() + " isles)")
            .orElse("no site for a fallback cluster");
    }

    /** Chooses a site for the fallback cluster around {@code anchor} and records it; empty when 16 bearings fail. */
    public static Optional<IsleSavedData.Cluster> newFallback(ServerLevel ow, BlockPos anchor, RandomSource rand) {
        int radius = ServerConfig.islesFallbackRadius();
        int min = Math.min(FALLBACK_MIN, radius / 2);
        BlockPos spawn = ow.getSharedSpawnPos();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2;
            int dist = min + rand.nextInt(Math.max(1, radius - min + 1));
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * dist);
            Optional<IsleSavedData.Cluster> c = placeCluster(ow, x, z, IsleSavedData.Origin.FALLBACK, true, spawn, true);
            if (c.isPresent()) return c;
        }
        FirmagesCore.LOGGER.warn("Drifting Isles: no site for the fallback cluster around {} (16 bearings)", anchor.toShortString());
        return Optional.empty();
    }

    /**
     * Records a cluster centred on ({@code x}, {@code z}) with the generator's terrain heights and registers it for
     * placement. Empty when the main isle has no room, or (with {@code checks}) the cluster would cover the world spawn
     * or claimed land.
     */
    public static Optional<IsleSavedData.Cluster> placeCluster(ServerLevel ow, int x, int z, IsleSavedData.Origin origin, boolean shrine, BlockPos spawn,
                                                              boolean checks) {
        IsleSettings s = IsleSearch.settings(ow);
        long seed = IsleLayout.mix(ow.getSeed() ^ origin.ordinal(), ((long) x << 32) ^ (z & 0xFFFFFFFFL));
        List<IsleLayout.Plan> plan = IsleLayout.plan(seed, x, z, s, shrine);
        if (checks) {
            for (IsleLayout.Plan p : plan) {
                if (Math.hypot(p.x() - spawn.getX(), p.z() - spawn.getZ()) < p.radius() + SPAWN_CLEARANCE) return Optional.empty();
                if (ModList.get().isLoaded(Claims.MOD_ID) && Claims.claimedNear(Level.OVERWORLD, p.x(), p.z(), 1 + p.radius() / 16)) return Optional.empty();
            }
        }
        var gen = ow.getChunkSource().getGenerator();
        var rs = ow.getChunkSource().randomState();
        List<IsleSpec> specs = IsleLayout.resolve(plan, s, (hx, hz) -> gen.getBaseHeight(hx, hz, Heightmap.Types.WORLD_SURFACE_WG, ow, rs));
        if (specs.isEmpty()) return Optional.empty();
        IsleSavedData.Cluster c = IsleSavedData.get(ow.getServer()).add(origin, x, z, specs);
        registerCluster(c);
        FirmagesCore.LOGGER.info("Drifting Isles: {} cluster {} recorded at {} {} with {} isles (tops {})", origin, c.id, x, z, specs.size(),
            specs.stream().map(IsleSpec::top).toList());
        return Optional.of(c);
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    // ---- the Wind Shrine for the pointer (SPEC §28.4a)

    /**
     * The nearest Wind Shrine core of an isle within {@code radius} blocks: the clusters placed by code, then the isle
     * starts whose Wind Shrine roll is true (only those starts are loaded).
     *
     * @param lit true for a core position that belongs to a lit Wayshrine (skipped)
     */
    public static Optional<BlockPos> nearestShrine(ServerLevel ow, BlockPos from, int radius, Predicate<BlockPos> lit) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (IsleSavedData.Cluster c : IsleSavedData.get(ow.getServer()).clusters()) {
            Optional<BlockPos> core = c.shrineCore();
            if (core.isEmpty() || lit.test(core.get())) continue;
            double d = horizontal(from, core.get());
            if (d <= radius && d < bestD) {
                best = core.get();
                bestD = d;
            }
        }
        IsleSettings s = IsleSearch.settings(ow);
        long seed = ow.getChunkSource().getGeneratorState().getLevelSeed();
        int limit = 4;
        for (IsleSearch.Found f : IsleSearch.nearest(ow, from, Math.min(radius, (int) bestD), c -> IsleLayout.shrineRoll(seed, c.x, c.z, s.windShrineChance()), limit)) {
            Optional<IsleSpec> host = f.shrineIsle();
            if (host.isEmpty()) continue;
            BlockPos core = IsleWriter.shrineCore(host.get());
            if (lit.test(core)) continue;
            double d = horizontal(from, core);
            if (d < bestD) {
                best = core;
                bestD = d;
            }
            break;
        }
        return Optional.ofNullable(best);
    }

    // ---- Starfall probe (SPEC §25.3)

    /** True when the column lies under a cluster placed by code or the planned footprint of an isle start. */
    static boolean insideIsle(ServerLevel level, int x, int z) {
        if (level.dimension() != Level.OVERWORLD) return false;
        for (IsleSavedData.Cluster c : IsleSavedData.get(level.getServer()).clusters()) {
            for (IsleSpec s : c.isles) {
                long dx = x - s.x();
                long dz = z - s.z();
                long r = s.radius() + 3;
                if (dx * dx + dz * dz <= r * r) return true;
            }
        }
        return IsleSearch.plannedOver(level, x, z);
    }

    // ---- no spawn on an isle

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.getRespawnPosition() != null && p.getRespawnDimension() == p.level().dimension()) {
            // a bed or anchor that still stands keeps the player where they chose to sleep
            BlockPos rp = p.getRespawnPosition();
            if (p.blockPosition().distManhattan(rp) <= 4) return;
        }
        setDownFromIsle(p);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0) return; // only the very first join
        setDownFromIsle(p);
    }

    /** A player placed at the world spawn on top of an isle is set down on the ground below it. */
    public static boolean setDownFromIsle(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return false;
        BlockPos feet = p.blockPosition();
        if (feet.getY() < IsleSettings.TIP_MIN - 8 || !level.getBlockState(feet.below()).is(SkyTags.ISLE_BODY)) return false;
        BlockPos.MutableBlockPos m = feet.below().mutable();
        while (m.getY() > level.getMinBuildHeight() && !level.getBlockState(m).isAir()) m.move(0, -1, 0); // through the isle
        while (m.getY() > level.getMinBuildHeight() && level.getBlockState(m).isAir()) m.move(0, -1, 0); // down the open air
        BlockPos ground = m.above().immutable();
        if (ground.getY() >= feet.getY() - 8) return false;
        p.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        FirmagesCore.LOGGER.info("Drifting Isles: {} spawned on an isle at {}; set down at {}", p.getGameProfile().getName(), feet.toShortString(),
            ground.toShortString());
        return true;
    }
}
