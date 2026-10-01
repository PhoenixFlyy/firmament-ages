package dev.firmages.core.origin;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.sgjourney.SgjGates;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Builds The Origin's MVP layout once into the void dimension (SPEC §16): a floating arena disc of radius 20 at
 * y 64 around (0, 0) with the {@code origin_altar} on a crying-obsidian dais in its centre, nine small floating
 * islands, one per shrine Age, in a loose ring 45 to 75 blocks out, and (with Stargate Journey) a Milky Way
 * stargate with its DHD on a pedestal south of the arena, connected by a bridge. Only vanilla blocks, so the
 * layout does not depend on any beta mod; the gate is Stargate Journey's own pedestal template.
 */
public final class OriginArena {
    /** Bump to rebuild the arena on existing worlds (blocks only, the gate is tracked separately). */
    public static final int VERSION = 1;
    public static final int RADIUS = 20;
    public static final int FLOOR_Y = 64;
    public static final BlockPos ALTAR = new BlockPos(0, FLOOR_Y + 1, 0);
    /** Where {@code /firmages origin tp} puts players: on the arena, between gate and altar, outside the Gathering. */
    public static final BlockPos ARRIVAL = new BlockPos(0, FLOOR_Y + 1, 14);
    /** Stargate Journey's pedestal template (7 x 9 x 13; gate base at template (3, 2, 9) facing north, DHD at (3, 1, 3)). */
    public static final ResourceLocation GATE_TEMPLATE = ResourceLocation.fromNamespaceAndPath("sgjourney", "stargate/milky_way/pedestal/stargate_pedestal_1");
    public static final BlockPos GATE_ORIGIN = new BlockPos(-3, FLOOR_Y, 22);
    /** The gate's base block (template offset (3, 2, 9) from {@link #GATE_ORIGIN}). */
    public static final BlockPos GATE_BASE = GATE_ORIGIN.offset(3, 2, 9);
    public static final BlockPos DHD = GATE_ORIGIN.offset(3, 1, 3);

    private static final BlockState[] ISLAND_TOPS = {
        Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.CUT_COPPER.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState(),
        Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.BRICKS.defaultBlockState(), Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState(),
        Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState()};
    private static final BlockState[] ROCK = {
        Blocks.DEEPSLATE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
        Blocks.STONE.defaultBlockState(), Blocks.CALCITE.defaultBlockState()};

    private OriginArena() {}

    /** Arena, altar and islands (not the gate). */
    public static void buildArena(ServerLevel level) {
        long t0 = System.nanoTime();
        int r = RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.5) continue;
                set(level, x, FLOOR_Y, z, floor(x, z, d));
                boolean rim = d > r - 0.5;
                boolean southGap = z > 0 && Math.abs(x) <= 2;
                if (rim && !southGap) {
                    set(level, x, FLOOR_Y + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    if (pillar(x, z)) set(level, x, FLOOR_Y + 2, z, Blocks.SEA_LANTERN.defaultBlockState());
                }
                // Underside: an inverted cone of mixed rock, so the arena reads as a floating island.
                int depth = (int) Math.round(12 * (1 - d / (r + 1)) + (Math.floorMod(x * 31 + z * 17, 3)));
                for (int dy = 1; dy <= depth; dy++) set(level, x, FLOOR_Y - dy, z, rock(x, FLOOR_Y - dy, z));
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x * x + z * z <= 5) set(level, x, FLOOR_Y, z, Blocks.CRYING_OBSIDIAN.defaultBlockState());
            }
        }
        level.setBlock(ALTAR, OriginRegistry.ORIGIN_ALTAR.get().defaultBlockState(), Block.UPDATE_ALL);
        // Bridge to the gate pedestal (also under the pedestal, whose own floor is one layer thick).
        for (int x = -4; x <= 4; x++) {
            for (int z = r - 1; z <= GATE_ORIGIN.getZ() + 13; z++) {
                if (z <= r && x * x + z * z <= (r + 0.5) * (r + 0.5) && Math.abs(x) > 2) continue;
                set(level, x, FLOOR_Y, z, Math.abs(x) == 4 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                set(level, x, FLOOR_Y - 1, z, rock(x, FLOOR_Y - 1, z));
            }
        }
        for (int i = 0; i < ISLAND_TOPS.length; i++) island(level, i);
        FirmagesCore.LOGGER.info("The Origin: arena v{} built in {} ms", VERSION, (System.nanoTime() - t0) / 1_000_000);
    }

    /**
     * Places Stargate Journey's pedestal gate and finishes it like SGJ's worldgen (network entry, DHD energy core).
     * Call only with Stargate Journey loaded. @return false if the template is missing
     */
    public static boolean buildGate(ServerLevel level) {
        Optional<StructureTemplate> t = level.getStructureManager().get(GATE_TEMPLATE);
        if (t.isEmpty()) return false;
        // Air in the template is skipped, so the bridge floor under the pedestal stays.
        StructurePlaceSettings settings = new StructurePlaceSettings().addProcessor(BlockIgnoreProcessor.AIR);
        t.get().placeInWorld(level, GATE_ORIGIN, GATE_ORIGIN, settings, level.random, Block.UPDATE_CLIENTS);
        int generated = SgjGates.finishPlacement(level, t.get().getBoundingBox(settings, GATE_ORIGIN));
        FirmagesCore.LOGGER.info("The Origin: return stargate placed at {} (DHD {}), {} Stargate Journey parts generated, in the network: {}",
            GATE_BASE, DHD, generated, SgjGates.inNetwork(level.getServer(), level.dimension()));
        return true;
    }

    private static BlockState floor(int x, int z, double d) {
        if (d > RADIUS - 1.5) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        boolean spoke = x == 0 || z == 0 || Math.abs(x) == Math.abs(z);
        if (Math.abs(d - 10) < 0.5 && spoke) return Blocks.SEA_LANTERN.defaultBlockState();
        if (Math.abs(d - 10) < 0.6 || Math.abs(d - 4) < 0.6) return Blocks.GILDED_BLACKSTONE.defaultBlockState();
        return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    }

    private static boolean pillar(int x, int z) {
        double a = Math.toDegrees(Math.atan2(z, x));
        double m = Math.floorMod((int) Math.round(a), 45);
        return m == 0;
    }

    private static BlockState rock(int x, int y, int z) {
        int h = Math.floorMod(x * 73428767 ^ y * 912931 ^ z * 42317861, 97);
        return ROCK[h < 50 ? 0 : h < 70 ? 1 : h < 85 ? 2 : h < 93 ? 3 : 4];
    }

    /** Island {@code i} (0..8, the shrine Age it stands for) at a fixed spot of the ring. */
    private static void island(ServerLevel level, int i) {
        double angle = Math.toRadians(i * 40 + 12);
        int dist = 45 + (i % 3) * 15;
        int cx = (int) Math.round(Math.cos(angle) * dist);
        int cz = (int) Math.round(Math.sin(angle) * dist);
        int cy = FLOOR_Y - 6 + (i * 7) % 17;
        int r = 4 + i % 3;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.3) continue;
                set(level, cx + x, cy, cz + z, ISLAND_TOPS[i]);
                int depth = (int) Math.round((r + 2) * (1 - d / (r + 1))) + 1;
                for (int dy = 1; dy <= depth; dy++) set(level, cx + x, cy - dy, cz + z, rock(cx + x, cy - dy, cz + z));
            }
        }
        set(level, cx, cy + 1, cz, Blocks.END_ROD.defaultBlockState());
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }
}
