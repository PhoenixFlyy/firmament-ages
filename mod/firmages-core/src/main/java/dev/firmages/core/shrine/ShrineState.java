package dev.firmages.core.shrine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Cheap cache of the last validation for hot event paths (spawn checks, block changes), so they never touch the
 * SavedData or the multiblocks. Updated by {@link ShrineService#validate}; restored from the SavedData at start.
 */
public final class ShrineState {
    public record Snapshot(ResourceKey<Level> dimension, BlockPos heart, boolean intact, int awakened) {}

    @Nullable private static volatile Snapshot current;

    private ShrineState() {}

    @Nullable
    public static Snapshot get() {
        return current;
    }

    static void set(ResourceKey<Level> dimension, BlockPos heart, boolean intact, int awakened) {
        current = new Snapshot(dimension, heart.immutable(), intact, awakened);
    }

    public static void clear() {
        current = null;
    }
}
