package dev.firmages.core.boss;

import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * "Nine Lights": the arena's sea lanterns (eight spoke marks in the floor, the rim lanterns on their pillars, built by
 * {@link OriginArena}) go out while The Primordial is in phase 2 or 3 and come back when the fight ends (death, wipe,
 * removal). Only in The Origin: a boss summoned anywhere else leaves the world's lights alone. The positions are found
 * by scanning the arena layers, so a rebuilt or edited arena still relights exactly what went out.
 */
public final class ArenaLights {
    private ArenaLights() {}

    static final BlockState LIT = Blocks.SEA_LANTERN.defaultBlockState();
    static final BlockState OUT = Blocks.POLISHED_BLACKSTONE.defaultBlockState();

    public static boolean applies(ServerLevel level) {
        return level.dimension() == OriginRegistry.ORIGIN;
    }

    /** A living Primordial within 48 blocks of {@code pos} (OriginSpawns: no spawns in its arena while it fights). */
    public static boolean bossNear(ServerLevel level, BlockPos pos) {
        return !level.getEntities(BossRegistry.PRIMORDIAL.get(), new AABB(pos).inflate(48), PrimordialEntity::isAlive).isEmpty();
    }

    /** The lit lanterns of the arena: the floor layer and the two layers above it, within the arena radius. */
    public static List<BlockPos> lit(ServerLevel level) {
        List<BlockPos> out = new ArrayList<>();
        int r = OriginArena.radius() + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = OriginArena.FLOOR_Y; y <= OriginArena.FLOOR_Y + 2; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    p.set(x, y, z);
                    if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.SEA_LANTERN)) out.add(p.immutable());
                }
            }
        }
        return out;
    }

    /** Puts the lanterns out. @return the positions that went out (to be relit later) */
    public static List<BlockPos> extinguish(ServerLevel level) {
        if (!applies(level)) return List.of();
        List<BlockPos> out = lit(level);
        for (BlockPos p : out) level.setBlock(p, OUT, Block.UPDATE_ALL);
        return out;
    }

    /** Relights what {@link #extinguish} put out (only blocks still dark: a player's change stays). @return relit count */
    public static int relight(ServerLevel level, List<BlockPos> positions) {
        int n = 0;
        for (BlockPos p : positions) {
            if (level.isLoaded(p) && level.getBlockState(p).is(OUT.getBlock())) {
                level.setBlock(p, LIT, Block.UPDATE_ALL);
                n++;
            }
        }
        return n;
    }
}
