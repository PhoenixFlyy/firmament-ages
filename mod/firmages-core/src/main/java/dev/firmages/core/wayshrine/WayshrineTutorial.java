package dev.firmages.core.wayshrine;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * The tutorial Wayshrine (SPEC-himmel §28.4): a Forest Ring placed by code 300 to 800 blocks from the shared spawn at the
 * first start with this feature, also on an existing world. Worldgen cannot do it: TFC spawns players anywhere within
 * 4,000 blocks of its spawn centre, and its biome source defeats a biome search. Up to sixteen bearings are tried; a
 * site must be land (no fluid at the surface) with at most three blocks of height spread over the footprint. The
 * chunks load once (the build-once pattern of The Origin's arena).
 */
public final class WayshrineTutorial {
    public static final String TYPE = "forest_ring";

    private WayshrineTutorial() {}

    /** {@code ServerStartedEvent}: places it once when {@code wayshrine.tutorial} is on. */
    public static void onServerStarted(MinecraftServer s) {
        if (!ServerConfig.wayshrineTutorial()) return;
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
     * Places the tutorial ruin with its core at column (x, z) when the site is fit (or always, when {@code check} is
     * false: {@code /firmages wayshrine tutorial here}) and records it.
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
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0 + dx, z0 + dz);
                heights[dx * sz + dz] = h;
                BlockPos top = new BlockPos(x0 + dx, h - 1, z0 + dz);
                if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.above()).isEmpty()) fluid = true;
            }
        }
        if (check && !WayshrineRules.fitSite(heights, fluid)) return Optional.empty();
        BlockPos core = new BlockPos(x, WayshrineRules.floorY(heights), z);
        Rotation rot = Rotation.values()[rand.nextInt(4)];
        Optional<BlockPos> placed = WayshrinePlacement.placeRuin(level, TYPE, core, rot, rand, null);
        if (placed.isEmpty()) return placed;
        WayshrineSavedData sd = WayshrineSavedData.get(level.getServer());
        sd.setTutorial(core);
        sd.addFound(new WayshrineSavedData.Found(core, TYPE));
        FirmagesCore.LOGGER.info("Tutorial Wayshrine ({}) placed at {}, {} blocks from the spawn", TYPE, core.toShortString(),
            (int) Math.sqrt(core.distSqr(level.getSharedSpawnPos().atY(core.getY()))));
        return placed;
    }
}
