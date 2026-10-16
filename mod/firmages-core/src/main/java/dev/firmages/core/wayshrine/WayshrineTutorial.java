package dev.firmages.core.wayshrine;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.ftbchunks.Claims;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.sky.SkyTags;
import dev.firmages.core.sky.StarfallService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The tutorial Wayshrine (SPEC-himmel §28.4): a Forest Ring placed by code 300 to 800 blocks from the shared spawn at the
 * first start of a new world. Worldgen cannot do it: TFC spawns players anywhere within 4,000 blocks of its spawn
 * centre, and its biome source defeats a biome search. Up to sixteen bearings are tried; a site must be land (no fluid
 * at the surface) with at most three blocks of height spread over the footprint, and it must carry no trace of players
 * (Starfall's base check, §25.3): no chunk a player stayed in, no player block or player block entity within eight
 * blocks of the footprint, no claimed land nearby. A world older than three days never gets one by itself (an existing
 * world updating to this feature); {@code /firmages wayshrine tutorial} places one there on purpose. The chunks load
 * once (the build-once pattern of The Origin's arena).
 */
public final class WayshrineTutorial {
    public static final String TYPE = "forest_ring";

    private WayshrineTutorial() {}

    /** {@code ServerStartedEvent}: places it once when {@code wayshrine.tutorial} is on and the world is new. */
    public static void onServerStarted(MinecraftServer s) {
        if (!ServerConfig.wayshrineTutorial()) return;
        WayshrineSavedData sd = WayshrineSavedData.get(s);
        if (sd.tutorialPlaced()) return;
        long time = s.overworld().getGameTime();
        if (!WayshrineRules.tutorialWorldFresh(time)) {
            FirmagesCore.LOGGER.info("Tutorial Wayshrine: not placed, the world is {} days old (only a new world gets one; "
                + "/firmages wayshrine tutorial places it on purpose)", time / 24000L);
            return;
        }
        ensure(s);
    }

    /** Places the tutorial Wayshrine unless the world has one. @return the core placed now */
    public static Optional<BlockPos> ensure(MinecraftServer s) {
        WayshrineSavedData sd = WayshrineSavedData.get(s);
        if (sd.tutorialPlaced()) return Optional.empty();
        ServerLevel level = s.overworld();
        Optional<BlockPos> core = placeAround(level, level.getSharedSpawnPos(), level.getRandom());
        if (core.isEmpty()) FirmagesCore.LOGGER.warn("Tutorial Wayshrine: no fit site 300 to 800 blocks from {}; trying again at the next start",
            level.getSharedSpawnPos());
        return core;
    }

    /** Sixteen bearings around {@code spawn}, 300 to 800 blocks out; the first fit site gets the ruin. */
    public static Optional<BlockPos> placeAround(ServerLevel level, BlockPos spawn, RandomSource rand) {
        double angle = rand.nextDouble() * Math.PI * 2;
        double distance = WayshrineRules.TUTORIAL_MIN + rand.nextDouble() * (WayshrineRules.TUTORIAL_MAX - WayshrineRules.TUTORIAL_MIN);
        for (int i = 0; i < WayshrineRules.TUTORIAL_TRIES; i++) {
            int[] xz = WayshrineRules.tutorialPoint(spawn.getX(), spawn.getZ(), angle, distance, i);
            Optional<BlockPos> core = placeAt(level, xz[0], xz[1], rand, true);
            if (core.isPresent()) return core;
        }
        return Optional.empty();
    }

    /**
     * Places the tutorial ruin with its core at column (x, z) when the site is fit and free of players' traces (or
     * always, when {@code check} is false: {@code /firmages wayshrine tutorial here}) and records it.
     */
    public static Optional<BlockPos> placeAt(ServerLevel level, int x, int z, RandomSource rand, boolean check) {
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
        if (check && (isle || !WayshrineRules.fitSite(heights, fluid))) return Optional.empty();
        BlockPos core = new BlockPos(x, WayshrineRules.floorY(heights), z);
        if (check) {
            String blocker = siteBlocker(level, x0, z0, sx, sz, core.getY());
            if (blocker != null) {
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
        FirmagesCore.LOGGER.info("Tutorial Wayshrine ({}) placed at {}, {} blocks from the spawn", TYPE, core.toShortString(),
            (int) Math.sqrt(core.distSqr(level.getSharedSpawnPos().atY(core.getY()))));
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
