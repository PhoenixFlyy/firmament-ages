package dev.firmages.core.perf;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The guard of {@code mixin/tfcrf/RegrowthSpreadMixin} (SPEC §20.1): a forest spreads a sapling only into a loaded
 * chunk. Counts what it let through and what it skipped for {@code /firmages perf regrowth}.
 */
public final class RegrowthGuard {
    private static final AtomicLong LOOKED = new AtomicLong();
    private static final AtomicLong SKIPPED = new AtomicLong();

    private RegrowthGuard() {}

    /** True when the spread may look at block column {@code x}, {@code z}: its chunk is loaded or the guard is off. */
    public static boolean mayLook(Level level, int x, int z) {
        if (!ServerConfig.regrowthLoadedChunksOnly() || level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
            LOOKED.incrementAndGet();
            return true;
        }
        SKIPPED.incrementAndGet();
        return false;
    }

    /** {spreads that looked at a column, spreads skipped because the column's chunk was not loaded}. */
    public static long[] counts() {
        return new long[] {LOOKED.get(), SKIPPED.get()};
    }

    public static void reset() {
        LOOKED.set(0);
        SKIPPED.set(0);
    }
}
