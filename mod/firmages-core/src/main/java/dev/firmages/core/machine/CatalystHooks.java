package dev.firmages.core.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The Arcane Catalyst's links to other mods (SPEC §41.3), set in {@code FirmagesCore} when the mod is loaded and
 * replaced by the GameTests: Create's rotation at a block, Ars Nouveau's Source from nearby jars, and an extra work
 * tick of an Immersive Engineering multiblock (its parts forward to the master). Without the mod: no rotation, no
 * Source, the vanilla ticker.
 */
public final class CatalystHooks {
    private CatalystHooks() {}

    /** RPM at a block. */
    public interface Rotation {
        double rpm(Level level, BlockPos pos);
    }

    /** Takes {@code amount} Source from jars within {@code range} blocks of {@code pos}; true when it was paid. */
    public interface Source {
        boolean take(Level level, BlockPos pos, int range, int amount);
    }

    /** Runs one extra work tick of a block entity; false when it cannot (then the vanilla ticker is tried). */
    public interface Ticker {
        boolean tick(Level level, BlockEntity be);
    }

    public static volatile Rotation rotation = (l, p) -> 0;
    public static volatile Source source = (l, p, r, a) -> false;
    public static volatile Ticker multiblock = (l, be) -> false;
}
