package dev.firmages.core.compat.ftbchunks;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * FTB Chunks claims for Starfall's site choice (SPEC §25.3): {@code getChunk(ChunkDimPos)} is a map lookup and loads
 * nothing [verified, javap 2101.1.22]. Loaded only when FTB Chunks is present ({@link #MOD_ID}).
 */
public final class Claims {
    public static final String MOD_ID = "ftbchunks";

    private Claims() {}

    /** True when a claimed chunk lies within {@code margin} chunks of the column's chunk. */
    public static boolean claimedNear(ResourceKey<Level> dimension, int x, int z, int margin) {
        if (!FTBChunksAPI.api().isManagerLoaded()) return false;
        var manager = FTBChunksAPI.api().getManager();
        int cx = x >> 4;
        int cz = z >> 4;
        for (int dx = -margin; dx <= margin; dx++) {
            for (int dz = -margin; dz <= margin; dz++) {
                if (manager.getChunk(new ChunkDimPos(dimension, cx + dx, cz + dz)) != null) return true;
            }
        }
        return false;
    }
}
