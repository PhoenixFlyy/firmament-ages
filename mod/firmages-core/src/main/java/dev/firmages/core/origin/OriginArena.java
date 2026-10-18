package dev.firmages.core.origin;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.ark.ArkRegistry;
import dev.firmages.core.compat.sgjourney.SgjGates;
import dev.firmages.core.shrine.Consecration;
import dev.firmages.core.shrine.ShrineRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Builds The Origin's layout once into the void dimension (SPEC §16): a floating arena disc of radius 20 at
 * y 64 around (0, 0) with the {@code origin_altar} on a crying-obsidian dais in its centre, nine small floating
 * islands, one per shrine Age, in a loose ring 45 to 75 blocks out, and (with Stargate Journey) a Milky Way
 * stargate with its DHD on a pedestal south of the arena, connected by a bridge. Version 2 (Origin echoes): the
 * islands are the shrine's last ring, built from its consecrated family (stone with a trim rim, accent = the Age),
 * and above each one the Age's signature item turns in the air ({@link OriginEchoes}). The rest is vanilla blocks,
 * so the layout depends on no beta mod; the gate is Stargate Journey's own pedestal template. Version 3 (the
 * Archipelago, SPEC §31.8): each island widens to radius 9..11 and gets an {@code ark_plinth} one block north of its
 * echo; on an existing version-2 world this runs as a migration that only fills air ({@link #migrateV3}), so a
 * player's build on the disc or the islands never changes.
 */
public final class OriginArena {
    /** Bump to rebuild the arena on existing worlds (blocks only, the gate is tracked separately). */
    public static final int VERSION = 3;
    /** The islands, one per shrine Age 0..8. */
    public static final int ISLANDS = 9;
    /** Radius of the classic arena (layout {@link OriginLayout#CLASSIC}); {@link #radius()} is the world's. */
    public static final int RADIUS = 20;
    public static final int FLOOR_Y = 64;
    public static final BlockPos ALTAR = new BlockPos(0, FLOOR_Y + 1, 0);
    /** Stargate Journey's pedestal template (7 x 9 x 13; gate base at template (3, 2, 9) facing north, DHD at (3, 1, 3)). */
    public static final ResourceLocation GATE_TEMPLATE = ResourceLocation.fromNamespaceAndPath("sgjourney", "stargate/milky_way/pedestal/stargate_pedestal_1");

    /** The running world's layout (set by {@link OriginService#ensureBuilt} from {@link OriginSavedData}); new worlds are grand. */
    private static volatile OriginLayout layout = OriginLayout.GRAND;

    public static OriginLayout layout() {
        return layout;
    }

    public static void setLayout(OriginLayout l) {
        layout = l;
    }

    /** The arena radius of this world. */
    public static int radius() {
        return layout.radius;
    }

    /** Where {@code /firmages origin tp} puts players: outside the Gathering (classic: on the disc; grand: on the arrival platform). */
    public static BlockPos arrival() {
        return layout.arrival;
    }

    public static BlockPos gateOrigin() {
        return layout.gateOrigin;
    }

    /** The return gate's base block (template offset (3, 2, 9) from {@link #gateOrigin}). */
    public static BlockPos gateBase() {
        return layout.gateBase();
    }

    public static BlockPos dhd() {
        return layout.dhd();
    }

    /** The four fight altars of the grand arena (north, east, south, west) on their podiums; empty in a classic arena. */
    public static List<BlockPos> altars() {
        if (!layout.hasAltars()) return List.of();
        List<BlockPos> out = new java.util.ArrayList<>();
        for (int[] a : GrandArenaPlan.ALTARS) out.add(new BlockPos(a[0], GrandArenaPlan.ALTAR_Y, a[1]));
        return out;
    }

    private static final BlockState[] ROCK = {
        Blocks.DEEPSLATE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
        Blocks.STONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState()};

    private OriginArena() {}

    /** Arena, altar and islands (not the gate) of the world's layout. */
    public static void buildArena(ServerLevel level) {
        if (layout == OriginLayout.GRAND) {
            buildGrand(level);
            return;
        }
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
                    if (pillar(x, z)) set(level, x, FLOOR_Y + 2, z, dev.firmages.core.boss.ArenaLights.LIT);
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
            for (int z = r - 1; z <= gateOrigin().getZ() + 13; z++) {
                if (z <= r && x * x + z * z <= (r + 0.5) * (r + 0.5) && Math.abs(x) > 2) continue;
                set(level, x, FLOOR_Y, z, Math.abs(x) == 4 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                set(level, x, FLOOR_Y - 1, z, rock(x, FLOOR_Y - 1, z));
            }
        }
        for (int i = 0; i < ISLANDS; i++) buildIslandV2(level, islandTop(i), i);
        FirmagesCore.LOGGER.info("The Origin: arena v{} built in {} ms", VERSION, (System.nanoTime() - t0) / 1_000_000);
    }

    /**
     * The version-3 step (SPEC §31.8): widens every island with {@link #widenIsland} and stands a plinth on each
     * ({@link #placePlinth}). Only air is ever set, so it is safe on a played world and runs again harmlessly.
     * @return the plinth position per island (null where no free spot was found)
     */
    public static BlockPos[] migrateV3(ServerLevel level) {
        long t0 = System.nanoTime();
        BlockPos[] plinths = new BlockPos[ISLANDS];
        int filled = 0;
        for (int i = 0; i < ISLANDS; i++) {
            filled += widenIsland(level, islandTop(i), i);
            plinths[i] = placePlinth(level, islandTop(i));
            if (plinths[i] == null) {
                FirmagesCore.LOGGER.warn("The Origin: no free spot for the plinth of island age_{} near {}; blocks stand there", i, islandTop(i).toShortString());
            }
        }
        FirmagesCore.LOGGER.info("The Origin: Archipelago step (arena v3): {} air blocks filled, {} plinths, in {} ms", filled,
            java.util.Arrays.stream(plinths).filter(java.util.Objects::nonNull).count(), (System.nanoTime() - t0) / 1_000_000);
        return plinths;
    }

    /**
     * Places Stargate Journey's pedestal gate and finishes it like SGJ's worldgen (network entry), then takes the DHD's
     * fusion core and charges the gate for one trip home instead (SPEC §16.2a). Call only with Stargate Journey
     * loaded. @return false if the template is missing
     */
    public static boolean buildGate(ServerLevel level) {
        Optional<StructureTemplate> t = level.getStructureManager().get(GATE_TEMPLATE);
        if (t.isEmpty()) return false;
        // Air in the template is skipped, so the bridge floor under the pedestal stays.
        StructurePlaceSettings settings = new StructurePlaceSettings().addProcessor(BlockIgnoreProcessor.AIR);
        BlockPos origin = gateOrigin();
        t.get().placeInWorld(level, origin, origin, settings, level.random, Block.UPDATE_CLIENTS);
        int generated = SgjGates.finishPlacement(level, t.get().getBoundingBox(settings, origin));
        boolean core = SgjGates.removeEnergyCore(level, dhd());
        long added = SgjGates.keepCharged(level, gateBase());
        FirmagesCore.LOGGER.info("The Origin: return stargate placed at {} (DHD {}), {} Stargate Journey parts generated, in the network: {}; "
            + "DHD energy core taken: {}, gate charged with {} FE for one trip home", gateBase(), dhd(), generated,
            SgjGates.inNetwork(level.getServer(), level.dimension()), core, added);
        return true;
    }

    /**
     * The grand arena (rounds G3 and G4, SPEC §16.5): every cell of {@link GrandArenaPlan} (the terraced disc, the altar
     * podiums, domes, towers, the arrival platform far below and the ascent), the Gathering altar in the centre and the
     * islands. Sets every block (a new Origin, {@code origin rebuild}).
     */
    public static void buildGrand(ServerLevel level) {
        long t0 = System.nanoTime();
        java.util.Map<String, BlockState> states = new java.util.HashMap<>();
        int n = 0;
        for (var e : GrandArenaPlan.cells().entrySet()) {
            long p = e.getKey();
            BlockState s = states.computeIfAbsent(e.getValue(), OriginArena::grandState);
            level.setBlock(new BlockPos(GrandArenaPlan.unpackX(p), GrandArenaPlan.unpackY(p), GrandArenaPlan.unpackZ(p)), s, Block.UPDATE_CLIENTS);
            n++;
        }
        level.setBlock(ALTAR, OriginRegistry.ORIGIN_ALTAR.get().defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 0; i < ISLANDS; i++) buildIslandV2(level, islandTop(i), i);
        FirmagesCore.LOGGER.info("The Origin: grand arena (radius {}) built, {} blocks, in {} ms", GrandArenaPlan.RADIUS, n, (System.nanoTime() - t0) / 1_000_000);
    }

    /**
     * The arena stone of a consecrated role per accent group (accent mod 3: dark, moss, purple). Felix: the arena in
     * stone, dark stone, moss and purple tones instead of the shrines' colourful family.
     */
    private static final java.util.Map<Consecration.Role, String[]> ARENA_PALETTE = java.util.Map.of(
        Consecration.Role.STONE, new String[] {"polished_deepslate", "mossy_stone_bricks", "deepslate_tiles"},
        Consecration.Role.BRICK, new String[] {"deepslate_bricks", "stone_bricks", "polished_blackstone_bricks"},
        Consecration.Role.TRIM, new String[] {"purpur_block", "moss_block", "amethyst_block"},
        Consecration.Role.PILLAR, new String[] {"purpur_pillar", "purpur_pillar", "purpur_pillar"},
        Consecration.Role.METAL, new String[] {"chiseled_deepslate", "mossy_cobblestone", "chiseled_deepslate"},
        Consecration.Role.LAMP, new String[] {"pearlescent_froglight", "pearlescent_froglight", "pearlescent_froglight"},
        Consecration.Role.GLASS, new String[] {"tinted_glass", "tinted_glass", "tinted_glass"},
        Consecration.Role.SCAFFOLD, new String[] {"polished_deepslate", "mossy_stone_bricks", "deepslate_tiles"});

    /**
     * The block state of a {@link GrandArenaPlan} material key: {@code rock_N} mixed rock, {@code dais}, {@code lantern},
     * {@code arena_altar}, else {@code <role>_<accent>}, an unbreakable arena stone of {@link #ARENA_PALETTE}.
     */
    public static BlockState grandState(String key) {
        if (key.startsWith("rock_")) return ROCK[Integer.parseInt(key.substring(5))];
        switch (key) {
            case "dais": return Blocks.CRYING_OBSIDIAN.defaultBlockState();
            case "lantern": return dev.firmages.core.boss.ArenaLights.LIT;
            case "arena_altar": return OriginRegistry.ARENA_ALTAR.get().defaultBlockState();
            default: break;
        }
        int us = key.lastIndexOf('_');
        Optional<Consecration.Role> role = us > 0 ? Consecration.Role.byId(key.substring(0, us)) : Optional.empty();
        if (role.isEmpty()) throw new IllegalArgumentException("unknown grand arena material " + key);
        return OriginRegistry.arenaStone(ARENA_PALETTE.get(role.get())[Integer.parseInt(key.substring(us + 1)) % 3]);
    }

    private static BlockState floor(int x, int z, double d) {
        if (d > RADIUS - 1.5) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        boolean spoke = x == 0 || z == 0 || Math.abs(x) == Math.abs(z);
        if (Math.abs(d - 10) < 0.5 && spoke) return dev.firmages.core.boss.ArenaLights.LIT;
        if (Math.abs(d - 10) < 0.6 || Math.abs(d - 4) < 0.6) return Blocks.AMETHYST_BLOCK.defaultBlockState();
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

    /** The top centre of island {@code i} (0..8, the shrine Age it stands for), a fixed spot of the ring. */
    public static BlockPos islandTop(int i) {
        double angle = Math.toRadians(i * 40 + 12);
        int dist = 45 + (i % 3) * 15 + layout.islandOffset;
        return new BlockPos((int) Math.round(Math.cos(angle) * dist), FLOOR_Y - 6 + (i * 7) % 17, (int) Math.round(Math.sin(angle) * dist));
    }

    /** Radius of island {@code i} in arena version 2 (its trim rim stays as an inner ring in version 3). */
    public static int islandRadius(int i) {
        return 4 + i % 3;
    }

    /** Radius of island {@code i} from arena version 3 on (the Archipelago, SPEC §31.8): 9 to 11. */
    public static int arkRadius(int i) {
        return 9 + i % 3;
    }

    /** True for an island top position {@code (x, z)} off the centre that belongs to the trim rim. */
    static boolean islandRim(int x, int z, int r) {
        return Math.sqrt(x * x + z * z) > r - 0.7;
    }

    /** Rock below an island column at distance {@code d} from the centre of an island of radius {@code r}. */
    static int depth(double d, int r) {
        return (int) Math.round((r + 2) * (1 - d / (r + 1))) + 1;
    }

    /**
     * Island {@code i} of version 2 with its top centre at {@code c}: consecrated stone with a trim rim in Age
     * {@code i}'s accent, mixed rock below. Sets every block (a fresh arena, {@code origin rebuild}, GameTests).
     */
    public static void buildIslandV2(ServerLevel level, BlockPos c, int i) {
        int r = islandRadius(i);
        BlockState stone = ShrineRegistry.consecrated(Consecration.Role.STONE, i);
        BlockState trim = ShrineRegistry.consecrated(Consecration.Role.TRIM, i);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.3) continue;
                set(level, c.getX() + x, c.getY(), c.getZ() + z, islandRim(x, z, r) ? trim : stone);
                int depth = depth(d, r);
                for (int dy = 1; dy <= depth; dy++) set(level, c.getX() + x, c.getY() - dy, c.getZ() + z, rock(c.getX() + x, c.getY() - dy, c.getZ() + z));
            }
        }
        // Version 1 had an end rod in the centre; the Age's signature item floats there now (OriginEchoes).
        set(level, c.getX(), c.getY() + 1, c.getZ(), Blocks.AIR.defaultBlockState());
    }

    /**
     * Widens island {@code i} (top centre {@code c}) from its version-2 radius to {@link #arkRadius}: a new ring of
     * consecrated stone with a trim rim, and a deeper rock cone below. Air only: a block of any kind (a player's
     * build, a later stage) is never replaced, and the old footprint gets no new top blocks (a hole a player dug
     * there stays open); below the old footprint the cone only grows downward.
     * @return the number of blocks set
     */
    public static int widenIsland(ServerLevel level, BlockPos c, int i) {
        int r0 = islandRadius(i);
        int r = arkRadius(i);
        BlockState stone = ShrineRegistry.consecrated(Consecration.Role.STONE, i);
        BlockState trim = ShrineRegistry.consecrated(Consecration.Role.TRIM, i);
        int n = 0;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.3) continue;
                int from = 1;
                if (d <= r0 + 0.3) from = depth(d, r0) + 1;
                else if (setAir(level, c.getX() + x, c.getY(), c.getZ() + z, islandRim(x, z, r) ? trim : stone)) n++;
                int depth = depth(d, r);
                for (int dy = from; dy <= depth; dy++) {
                    if (setAir(level, c.getX() + x, c.getY() - dy, c.getZ() + z, rock(c.getX() + x, c.getY() - dy, c.getZ() + z))) n++;
                }
            }
        }
        return n;
    }

    /** Where the plinth of the island with top centre {@code c} may stand, in order: one block north of the echo first. */
    public static List<BlockPos> plinthSpots(BlockPos c) {
        BlockPos a = c.above();
        return List.of(a.north(), a.north(2), a.east(), a.west(), a.south(), a.north().east(), a.north().west(), a.north(3));
    }

    /**
     * Stands the plinth on the island with top centre {@code c} (air only): on the first spot of {@link #plinthSpots}
     * that already holds a plinth, else on the first that is air above a block. @return its position, or null if every
     * spot is taken
     */
    @org.jetbrains.annotations.Nullable
    public static BlockPos placePlinth(ServerLevel level, BlockPos c) {
        BlockState plinth = ArkRegistry.ARK_PLINTH.get().defaultBlockState();
        for (BlockPos p : plinthSpots(c)) if (level.getBlockState(p).is(plinth.getBlock())) return p;
        for (BlockPos p : plinthSpots(c)) {
            if (level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir()) {
                level.setBlock(p, plinth, Block.UPDATE_ALL);
                return p;
            }
        }
        return null;
    }

    private static boolean setAir(ServerLevel level, int x, int y, int z, BlockState state) {
        BlockPos p = new BlockPos(x, y, z);
        if (!level.getBlockState(p).isAir()) return false;
        level.setBlock(p, state, Block.UPDATE_CLIENTS);
        return true;
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }
}
