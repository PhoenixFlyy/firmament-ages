package dev.firmages.core.perf;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /firmages perf regrowth spread <pos> <times>} (SPEC §20.1, debug.allowSimulate): calls TFC Regrowing Forests'
 * own {@code RegrowthRecipe.tryToSpread} for the leaf at {@code pos} as many times as a random tick would, and reports
 * the time it took, the chunks loaded before and after, and the guard's counts. A copy boot runs it with the guard on
 * and off while Chunky fills its queue (the hang of round C2).
 */
public final class RegrowthProbe {
    private RegrowthProbe() {}

    public static List<String> spread(ServerLevel level, BlockPos pos, int times) {
        List<String> out = new ArrayList<>();
        BlockState state = level.getBlockState(pos);
        Method m;
        try {
            m = Class.forName("tfcrf.common.recipes.RegrowthRecipe").getMethod("tryToSpread", BlockState.class, Level.class, BlockPos.class,
                RandomSource.class);
        } catch (ReflectiveOperationException e) {
            out.add("TFC Regrowing Forests not found: " + e);
            return out;
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        out.add(state + " at " + pos.toShortString() + ", ground " + ground + ", randomly ticking " + state.isRandomlyTicking());
        long[] before = RegrowthGuard.counts();
        int chunksBefore = level.getChunkSource().getLoadedChunksCount();
        long t0 = System.nanoTime();
        int errors = 0;
        for (int i = 0; i < times; i++) {
            try {
                m.invoke(null, state, level, pos, level.random);
            } catch (ReflectiveOperationException e) {
                errors++;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        long[] after = RegrowthGuard.counts();
        int chunksAfter = level.getChunkSource().getLoadedChunksCount();
        out.add(times + " spreads in " + ms + " ms (" + String.format(java.util.Locale.ROOT, "%.2f", ms / (double) Math.max(1, times))
            + " ms each); looked " + (after[0] - before[0]) + ", skipped " + (after[1] - before[1]) + "; loaded chunks " + chunksBefore + " -> "
            + chunksAfter + (errors > 0 ? "; " + errors + " errors" : ""));
        return out;
    }
}
