package dev.firmages.core.wayshrine;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.ftbchunks.Claims;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.sky.DeferredSites;
import dev.firmages.core.sky.SkyTags;
import dev.firmages.core.sky.StarfallService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Optional;

/**
 * The tutorial Wayshrine (SPEC-himmel §28.4): a Forest Ring placed by code 300 to 800 blocks from the shared spawn at the
 * first start of a new world. Worldgen cannot do it: TFC spawns players anywhere within 4,000 blocks of its spawn
 * centre, and its biome source defeats a biome search. A search pass tries 160 sites, five distances (300 to 800 blocks)
 * on each of 32 bearings ({@link WayshrineRules#tutorialPoint}), one site after the other: a ticket loads the site's chunks
 * (footprint plus margin) on the world's worker threads, and the site is checked on the first tick they are all loaded, so
 * the server thread never waits for chunk generation. A site must be land (no fluid
 * at the surface) with at most three blocks of height spread over the footprint, and it must carry no trace of players
 * (Starfall's base check, §25.3): no chunk a player stayed in, no player block or player block entity within eight
 * blocks of the footprint, no claimed land nearby. A pass without a fit site is followed by another one at a new start
 * bearing a quarter day later, as long as the world is younger than three days; then the search gives up with one WARN.
 * A world older than three days never gets one by itself (an existing world updating to this feature);
 * {@code /firmages wayshrine tutorial} places one there on purpose. The chunks load once (the build-once pattern of The
 * Origin's arena).
 */
public final class WayshrineTutorial {
    public static final String TYPE = "forest_ring";

    /** One search pass: its start bearing (radians from north) and the index of its first distance. */
    public record Pass(double angle, int firstDistance) {
        public static Pass random(RandomSource rand) {
            return new Pass(rand.nextDouble() * Math.PI * 2, rand.nextInt(WayshrineRules.TUTORIAL_DISTANCES.length));
        }

        /** Site {@code i} of this pass around {@code spawn}: {x, z}. */
        public int[] point(BlockPos spawn, int i) {
            return WayshrineRules.tutorialPoint(spawn.getX(), spawn.getZ(), angle, firstDistance, i);
        }
    }

    /** The sites a search tried: all of them, those on unfit ground (fluid, slope, isle top), those near players' traces. */
    public static final class Tally {
        int tried;
        int unfit;
        int blocked;

        @Override
        public String toString() {
            return tried + " sites tried (" + unfit + " unfit ground, " + blocked + " near players' traces)";
        }
    }

    /** Loads the chunks of the site the search checks next; removed once the site is checked. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("firmages_wayshrine_tutorial", Comparator.comparingLong(ChunkPos::toLong));

    /** The running search of the start (server thread only): one site at a time, a new pass after a pass without a site. */
    private static final class Search {
        Pass pass;
        int next;
        /** Passes begun (a pass begins with its first site). */
        int passes;
        long resumeAt;
        final Tally tally = new Tally();
        /** The site waiting for its chunks: column, chunks, ticket centre and radius; null between sites. */
        @Nullable
        int[] site;
        long[] chunks;
        ChunkPos centre;
        int radius;

        Search(Pass pass) {
            this.pass = pass;
        }
    }

    @Nullable
    private static Search search;

    private WayshrineTutorial() {}

    /** {@code ServerStartedEvent}: starts the search when {@code wayshrine.tutorial} is on and the world is new. */
    public static void onServerStarted(MinecraftServer s) {
        search = null;
        if (!ServerConfig.wayshrineTutorial()) return;
        WayshrineSavedData sd = WayshrineSavedData.get(s);
        if (sd.tutorialPlaced()) return;
        long time = s.overworld().getGameTime();
        if (!WayshrineRules.tutorialWorldFresh(time)) {
            FirmagesCore.LOGGER.info("Tutorial Wayshrine: not placed, the world is {} days old (only a new world gets one; "
                + "/firmages wayshrine tutorial places it on purpose)", time / 24000L);
            return;
        }
        search = new Search(Pass.random(s.overworld().getRandom()));
    }

    /** {@code ServerStoppedEvent}: drops the running search. */
    public static void onServerStopped() {
        search = null;
    }

    /** True while the search of the start runs (GameTests wait for it). */
    public static boolean searching() {
        return search != null;
    }

    /** {@code ServerTickEvent.Post}: puts a ticket on the next site's chunks, or checks the site once they are loaded. */
    public static void tick(MinecraftServer s) {
        Search run = search;
        if (run == null) return;
        ServerLevel level = s.overworld();
        long time = level.getGameTime();
        if (time < run.resumeAt) return;
        if (WayshrineSavedData.get(s).tutorialPlaced() || !ServerConfig.wayshrineTutorial()) {
            release(level, run);
            search = null; // placed by command meanwhile, or switched off
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        if (!WayshrineRules.tutorialWorldFresh(time)) {
            FirmagesCore.LOGGER.warn("Tutorial Wayshrine: no fit site 300 to 800 blocks from {} in {} passes, {}; the world is three "
                + "days old, the search ends (/firmages wayshrine tutorial places it on purpose)", spawn.toShortString(), run.passes, run.tally);
            release(level, run);
            search = null;
            return;
        }
        if (run.site == null) {
            Optional<StructureTemplate> t = WayshrinePlacement.template(level, TYPE);
            if (t.isEmpty()) {
                FirmagesCore.LOGGER.warn("Tutorial Wayshrine: the {} template is missing", TYPE);
                search = null;
                return;
            }
            if (run.next == 0) run.passes++;
            request(level, run, run.pass.point(spawn, run.next), t.get().getSize().getX(), t.get().getSize().getZ());
            return;
        }
        if (!DeferredSites.allLoaded(level, run.chunks)) return; // generated on the worker threads meanwhile
        int[] xz = run.site;
        run.next++;
        Optional<BlockPos> core = place(level, xz[0], xz[1], level.getRandom(), true, run.tally);
        release(level, run);
        if (core.isPresent()) {
            logPlaced(level, core.get(), "pass " + run.passes + ", " + run.tally);
            search = null;
            return;
        }
        if (run.next >= WayshrineRules.TUTORIAL_SITES) {
            FirmagesCore.LOGGER.info("Tutorial Wayshrine: pass {} had no fit site 300 to 800 blocks from {} ({} so far); next pass in {} ticks",
                run.passes, spawn.toShortString(), run.tally, WayshrineRules.TUTORIAL_RETRY_TICKS);
            run.pass = Pass.random(level.getRandom());
            run.next = 0;
            run.resumeAt = time + WayshrineRules.TUTORIAL_RETRY_TICKS;
        }
    }

    /** Puts a ticket on the chunks of the site at {@code xz}: its footprint (sx by sz) plus {@link WayshrineRules#TUTORIAL_SITE_MARGIN}. */
    private static void request(ServerLevel level, Search run, int[] xz, int sx, int sz) {
        int m = WayshrineRules.TUTORIAL_SITE_MARGIN;
        int minCx = (xz[0] - sx / 2 - m) >> 4;
        int maxCx = (xz[0] - sx / 2 + sx - 1 + m) >> 4;
        int minCz = (xz[1] - sz / 2 - m) >> 4;
        int maxCz = (xz[1] - sz / 2 + sz - 1 + m) >> 4;
        ChunkPos centre = new ChunkPos(xz[0] >> 4, xz[1] >> 4);
        long[] chunks = new long[(maxCx - minCx + 1) * (maxCz - minCz + 1)];
        int n = 0;
        int radius = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                chunks[n++] = ChunkPos.asLong(cx, cz);
                radius = Math.max(radius, Math.max(Math.abs(cx - centre.x), Math.abs(cz - centre.z)));
            }
        }
        level.getChunkSource().addRegionTicket(TICKET, centre, radius, centre);
        run.site = xz;
        run.chunks = chunks;
        run.centre = centre;
        run.radius = radius;
    }

    /** Removes the ticket of the site the search waits for, if any. */
    private static void release(ServerLevel level, Search run) {
        if (run.site == null) return;
        level.getChunkSource().removeRegionTicket(TICKET, run.centre, run.radius, run.centre);
        run.site = null;
    }

    /** Places the tutorial Wayshrine at once (one full pass) unless the world has one. @return the core placed now */
    public static Optional<BlockPos> ensure(MinecraftServer s) {
        WayshrineSavedData sd = WayshrineSavedData.get(s);
        if (sd.tutorialPlaced()) return Optional.empty();
        ServerLevel level = s.overworld();
        return placeAround(level, level.getSharedSpawnPos(), level.getRandom());
    }

    /** One full pass around {@code spawn} from a random start; the first fit site gets the ruin. */
    public static Optional<BlockPos> placeAround(ServerLevel level, BlockPos spawn, RandomSource rand) {
        return placeAround(level, spawn, Pass.random(rand), rand);
    }

    /** One full pass ({@link WayshrineRules#TUTORIAL_SITES} sites) around {@code spawn}; the first fit site gets the ruin. */
    public static Optional<BlockPos> placeAround(ServerLevel level, BlockPos spawn, Pass pass, RandomSource rand) {
        Tally tally = new Tally();
        for (int i = 0; i < WayshrineRules.TUTORIAL_SITES; i++) {
            int[] xz = pass.point(spawn, i);
            Optional<BlockPos> core = place(level, xz[0], xz[1], rand, true, tally);
            if (core.isPresent()) {
                logPlaced(level, core.get(), tally.toString());
                return core;
            }
        }
        FirmagesCore.LOGGER.info("Tutorial Wayshrine: no fit site 300 to 800 blocks from {}, {}", spawn.toShortString(), tally);
        return Optional.empty();
    }

    /**
     * Places the tutorial ruin with its core at column (x, z) when the site is fit and free of players' traces (or
     * always, when {@code check} is false: {@code /firmages wayshrine tutorial here}) and records it.
     */
    public static Optional<BlockPos> placeAt(ServerLevel level, int x, int z, RandomSource rand, boolean check) {
        Optional<BlockPos> core = place(level, x, z, rand, check, new Tally());
        core.ifPresent(c -> logPlaced(level, c, "one site"));
        return core;
    }

    private static void logPlaced(ServerLevel level, BlockPos core, String how) {
        FirmagesCore.LOGGER.info("Tutorial Wayshrine ({}) placed at {}, {} blocks from the spawn ({})", TYPE, core.toShortString(),
            (int) Math.sqrt(core.distSqr(level.getSharedSpawnPos().atY(core.getY()))), how);
    }

    private static Optional<BlockPos> place(ServerLevel level, int x, int z, RandomSource rand, boolean check, Tally tally) {
        tally.tried++;
        Optional<StructureTemplate> t = WayshrinePlacement.template(level, TYPE);
        if (t.isEmpty()) {
            FirmagesCore.LOGGER.warn("Tutorial Wayshrine: the {} template is missing", TYPE);
            return Optional.empty();
        }
        int sx = t.get().getSize().getX();
        int sz = t.get().getSize().getZ();
        int x0 = x - sx / 2;
        int z0 = z - sz / 2;
        for (int cx = x0 >> 4; cx <= (x0 + sx - 1) >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= (z0 + sz - 1) >> 4; cz++) level.getChunk(cx, cz); // generate and load once
        }
        int[] heights = new int[sx * sz];
        boolean fluid = false;
        boolean isle = false;
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0 + dx, z0 + dz);
                heights[dx * sz + dz] = h;
                BlockPos top = new BlockPos(x0 + dx, h - 1, z0 + dz);
                if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.above()).isEmpty()) fluid = true;
                if (level.getBlockState(top).is(SkyTags.ISLE_BODY)) isle = true; // the surface is a Drifting Isle's top
            }
        }
        if (check && (isle || !WayshrineRules.fitSite(heights, fluid))) {
            tally.unfit++;
            return Optional.empty();
        }
        BlockPos core = new BlockPos(x, WayshrineRules.floorY(heights), z);
        if (check) {
            String blocker = siteBlocker(level, x0, z0, sx, sz, core.getY());
            if (blocker != null) {
                tally.blocked++;
                FirmagesCore.LOGGER.info("Tutorial Wayshrine: site {} skipped ({})", core.toShortString(), blocker);
                return Optional.empty();
            }
        }
        Rotation rot = Rotation.values()[rand.nextInt(4)];
        Optional<BlockPos> placed = WayshrinePlacement.placeRuin(level, TYPE, core, rot, rand, null);
        if (placed.isEmpty()) return placed;
        WayshrineSavedData sd = WayshrineSavedData.get(level.getServer());
        sd.setTutorial(core);
        sd.addFound(new WayshrineSavedData.Found(core, TYPE));
        // Written now, not at the next autosave: the ruin's chunks are saved when they unload (the site is 300 to 800
        // blocks out), so a server killed before the autosave kept the ruin but lost the record and placed a second
        // tutorial Wayshrine at the next start (round F1, dev/offene-punkte.md).
        level.getServer().overworld().getDataStorage().save();
        return placed;
    }

    /**
     * Starfall's base check (§25.3) on the footprint (x0, z0, sx by sz) plus {@link WayshrineRules#TUTORIAL_SITE_MARGIN}:
     * a chunk a player stayed in ({@code starfall.inhabitedTicks}), a block entity that is not the world's own, a block of
     * {@code #firmages:starfall/player_blocks} from eight below to 16 above the floor, or claimed land within
     * {@code starfall.claimMarginChunks}. Loads the chunks it reads. Null when the site is free.
     */
    @Nullable
    public static String siteBlocker(ServerLevel level, int x0, int z0, int sx, int sz, int floorY) {
        int m = WayshrineRules.TUTORIAL_SITE_MARGIN;
        int minX = x0 - m;
        int maxX = x0 + sx - 1 + m;
        int minZ = z0 - m;
        int maxZ = z0 + sz - 1 + m;
        int inhabited = ServerConfig.starfallInhabitedTicks();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                if (chunk.getInhabitedTime() > inhabited) return "inhabited chunk " + cx + ", " + cz;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ) continue;
                    if (!StarfallService.natural(be)) {
                        return "block entity " + BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()) + " at " + p.toShortString();
                    }
                }
            }
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = floorY - 8; y <= floorY + 16; y++) {
                    if (level.getBlockState(p.set(x, y, z)).is(SkyTags.PLAYER_BLOCKS)) return "player block at " + p.toShortString();
                }
            }
        }
        if (level.dimension() == Level.OVERWORLD && ModList.get().isLoaded(Claims.MOD_ID)
            && Claims.claimedNear(Level.OVERWORLD, x0 + sx / 2, z0 + sz / 2, ServerConfig.starfallClaimMarginChunks())) return "claimed land";
        return null;
    }
}
