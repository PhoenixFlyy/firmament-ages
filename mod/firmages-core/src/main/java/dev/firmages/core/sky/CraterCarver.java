package dev.firmages.core.sky;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Carves craters over ticks (SPEC §25.3): at most {@code starfall.carveBlocksPerTick} real block changes per server
 * tick across all sites, with {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE} (no neighbour updates), in plan order with
 * the core last. The cursor is saved in the site, so an unload or a restart mid-carve resumes where it stopped once the
 * box is loaded again. Unchanged positions cost a read, not a change.
 */
public final class CraterCarver {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Reads (positions looked at) per tick at most, so a crater over open air does not walk its whole plan in one tick. */
    public static final int READS_PER_TICK = 4096;

    private static final Set<Long> ACTIVE = new LinkedHashSet<>();
    private static final Map<Long, CraterPlan> PLANS = new HashMap<>();
    /** Block changes of the last tick (the GameTest checks the budget). */
    private static int lastTickChanges;
    private static int maxTickChanges;

    private CraterCarver() {}

    public static synchronized void activate(long id) {
        ACTIVE.add(id);
    }

    public static synchronized boolean active(long id) {
        return ACTIVE.contains(id);
    }

    public static synchronized void clear() {
        ACTIVE.clear();
        PLANS.clear();
    }

    public static int lastTickChanges() {
        return lastTickChanges;
    }

    /** The most block changes of one tick since the last {@link #resetMax()}. */
    public static int maxTickChanges() {
        return maxTickChanges;
    }

    public static void resetMax() {
        maxTickChanges = 0;
    }

    /** The plan of a site from the loaded data (fallbacks keep an old site carvable after a data change). */
    public static CraterPlan plan(StarfallSite s) {
        return PLANS.computeIfAbsent(s.id, id -> {
            StarfallData data = StarfallDataLoader.current();
            StarfallData.Size size = data.size(s.size).orElseGet(() -> fallbackSize(s.size));
            Map<String, Double> mass = data.metal(s.metal).map(StarfallData.Metal::mass).orElseGet(() -> fallbackMass(s.metal));
            return CraterPlan.of(s.seed, size, mass);
        });
    }

    static StarfallData.Size fallbackSize(String id) {
        return switch (id) {
            case "old" -> new StarfallData.Size("old", 4, 2, 6, 10);
            case "medium" -> new StarfallData.Size("medium", 7, 4, 16, 22);
            case "large" -> new StarfallData.Size("large", 9, 5, 24, 32);
            default -> new StarfallData.Size("small", 5, 3, 10, 14);
        };
    }

    static Map<String, Double> fallbackMass(String metal) {
        return StarfallData.STARSTEEL.equals(metal)
            ? Map.of(FirmagesCore.MOD_ID + ":meteorite", 0.6, FirmagesCore.MOD_ID + ":starsteel_meteorite", 0.4)
            : Map.of(FirmagesCore.MOD_ID + ":meteorite", 1.0);
    }

    /**
     * The ground of a column: the highest block that is not air, fluid, leaves, a log, a replaceable plant or a barrier
     * (so a crater's rim never sits on a tree trunk, and a GameTest's barrier case is no ground). Reads loaded chunks only.
     */
    public static int groundY(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        for (int i = 0; i < 48 && y > level.getMinBuildHeight(); i++) {
            p.setY(y);
            BlockState s = level.getBlockState(p);
            if (!(s.isAir() || !s.getFluidState().isEmpty() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced()
                || s.is(Blocks.BARRIER))) break;
            y--;
        }
        return y;
    }

    /** One tick of carving; returns the number of block changes. */
    public static int tick(ServerLevel level, SkySavedData data, int budget) {
        List<Long> ids;
        synchronized (CraterCarver.class) {
            ids = new ArrayList<>(ACTIVE);
        }
        int changes = 0;
        int reads = READS_PER_TICK;
        for (long id : ids) {
            if (changes >= budget || reads <= 0) break;
            Optional<StarfallSite> o = data.site(id);
            if (o.isEmpty() || o.get().state != StarfallSite.State.CARVING) {
                drop(id);
                continue;
            }
            StarfallSite s = o.get();
            CraterPlan plan = plan(s);
            if (!DeferredSites.allLoaded(level, s.chunks(plan.reach()))) {
                drop(id); // the box unloaded mid-carve: the next chunk load queues it again
                continue;
            }
            List<CraterPlan.Op> ops = plan.ops();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            Map<Long, Integer> ground = new HashMap<>();
            while (s.cursor < ops.size() && changes < budget && reads > 0) {
                CraterPlan.Op op = ops.get(s.cursor);
                int x = s.x + op.dx();
                int z = s.z + op.dz();
                int y = op.surface()
                    ? ground.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> groundY(level, x, z)) + op.dy()
                    : s.centreY + op.dy();
                p.set(x, y, z);
                reads--;
                BlockState target = target(op, s, level.getGameTime());
                if (target != null && !level.getBlockState(p).equals(target)) {
                    level.setBlock(p, target, FLAGS);
                    changes++;
                    if (op.kind() == CraterPlan.Kind.CORE && level.getBlockEntity(p) instanceof StarfallCoreBlockEntity be) be.setCooling(s.id, s.coolsAt);
                }
                s.cursor++;
            }
            data.setDirty();
            if (s.cursor >= ops.size()) StarfallService.finished(level, data, s);
        }
        lastTickChanges = changes;
        maxTickChanges = Math.max(maxTickChanges, changes);
        return changes;
    }

    private static synchronized void drop(long id) {
        ACTIVE.remove(id);
    }

    /** Called when a site is done, moved or gone (the plan is dropped with it). */
    public static synchronized void forget(long id) {
        ACTIVE.remove(id);
        PLANS.remove(id);
    }

    private static BlockState target(CraterPlan.Op op, StarfallSite s, long now) {
        return switch (op.kind()) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case SKY_ROCK -> SkyRegistry.SKY_ROCK.get().defaultBlockState();
            case METEORITE -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(op.block()))
                .map(Block::defaultBlockState).orElse(SkyRegistry.METEORITE.get().defaultBlockState());
            case CORE -> SkyRegistry.STARFALL_CORE.get().defaultBlockState()
                .setValue(StarfallCoreBlock.METAL, SkyMetal.byId(s.metal).orElse(SkyMetal.METEORIC_IRON))
                .setValue(StarfallCoreBlock.HOT, !StarfallRules.cold(now, s.coolsAt));
        };
    }
}
