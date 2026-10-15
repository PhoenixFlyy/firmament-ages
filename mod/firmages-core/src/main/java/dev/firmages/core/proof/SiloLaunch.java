package dev.firmages.core.proof;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.adastra.AdAstraRockets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The silo launch (SPEC §22.4): watches every rocket while it stands on its pad and fires {@code firmages:silo_launch}
 * for its player crew at the moment it launches, when
 * <ul>
 *   <li>the pad lies at least {@link #DEPTH} blocks below the terrain on all four sides (in each direction, some
 *       column {@link #MIN_REACH} to {@link #MAX_REACH} blocks from the pad is solid from {@code pad.y + 1} to
 *       {@code pad.y + DEPTH}), and</li>
 *   <li>a moving Create contraption of at least {@link #HATCH_BLOCKS} blocks was within {@link #HATCH_RADIUS} blocks of
 *       the shaft above the pad while the rocket stood there, at most {@link #HATCH_MEMORY_TICKS} before the launch
 *       (a gantry or piston hatch disassembles once it has opened, so "seen moving shortly before" is the test).</li>
 * </ul>
 * Rockets come from Ad Astra and hatches from Create; both are looked up only when the mod is loaded, and GameTests
 * swap in stand-ins ({@link #useStandIns}).
 */
public final class SiloLaunch {
    public static final int DEPTH = 8;
    public static final int MIN_REACH = 2;
    public static final int MAX_REACH = 12;
    public static final int HATCH_BLOCKS = 100;
    public static final int HATCH_RADIUS = 16;
    public static final int HATCH_MEMORY_TICKS = 20 * 60;
    private static final int CHECK_PERIOD = 5;

    /** How rockets and their pads are recognised. */
    public interface Rockets {
        boolean isRocket(Entity e);

        boolean launched(Entity e);

        boolean isPad(BlockState state);
    }

    /** The largest moving contraption (in blocks) touching a box. */
    public interface Hatches {
        int largest(ServerLevel level, AABB box);
    }

    public enum Result { SILO, NO_CREW, SHALLOW, NO_HATCH }

    private static final Map<UUID, Watch> watched = new LinkedHashMap<>();
    private static Rockets rockets = defaultRockets();
    private static Hatches hatches = defaultHatches();

    private SiloLaunch() {}

    public static void register(IEventBus bus) {
        bus.addListener(SiloLaunch::onJoin);
        bus.addListener(SiloLaunch::onServerTick);
        bus.addListener(SiloLaunch::onServerStopped);
    }

    private static Rockets defaultRockets() {
        return ModList.get().isLoaded(AdAstraRockets.MOD_ID) ? new AdAstraRockets() : null;
    }

    private static Hatches defaultHatches() {
        return ModList.get().isLoaded("create") ? dev.firmages.core.compat.create.CreateHatches::largest : (level, box) -> 0;
    }

    /** GameTests: recognise stand-in rockets and hatches; {@code null, null} restores Ad Astra and Create. */
    public static void useStandIns(@Nullable Rockets r, @Nullable Hatches h) {
        rockets = r != null ? r : defaultRockets();
        hatches = h != null ? h : defaultHatches();
        watched.clear();
    }

    private static final class Watch {
        final Entity rocket;
        @Nullable
        BlockPos pad;
        long hatchSeen = Long.MIN_VALUE;

        Watch(Entity rocket) {
            this.rocket = rocket;
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || rockets == null) return;
        Entity e = event.getEntity();
        if (rockets.isRocket(e) && !rockets.launched(e)) watched.put(e.getUUID(), new Watch(e));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_PERIOD == 0) tick(event.getServer());
    }

    /** One check of every watched rocket (every {@value #CHECK_PERIOD} ticks; GameTests call it directly). */
    public static void tick(MinecraftServer server) {
        if (rockets == null || watched.isEmpty()) return;
        List<Runnable> launches = new ArrayList<>();
        for (Iterator<Watch> it = watched.values().iterator(); it.hasNext(); ) {
            Watch w = it.next();
            Entity e = w.rocket;
            if (e.isRemoved() || !(e.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            if (!rockets.launched(e)) {
                BlockPos pad = findPad(level, e.blockPosition());
                if (pad != null) {
                    w.pad = pad;
                    if (hatches.largest(level, hatchBox(pad)) >= HATCH_BLOCKS) w.hatchSeen = level.getGameTime();
                }
                continue;
            }
            it.remove();
            if (w.pad == null) continue;
            BlockPos pad = w.pad;
            boolean hatch = (w.hatchSeen != Long.MIN_VALUE && level.getGameTime() - w.hatchSeen <= HATCH_MEMORY_TICKS)
                || hatches.largest(level, hatchBox(pad)) >= HATCH_BLOCKS;
            List<ServerPlayer> crew = crew(e);
            launches.add(() -> launched(level, pad, crew, hatch));
        }
        launches.forEach(Runnable::run);
    }

    private static void launched(ServerLevel level, BlockPos pad, List<ServerPlayer> crew, boolean hatch) {
        Result r = judge(level, pad, crew, hatch);
        FirmagesCore.LOGGER.info("Rocket launched from {} in {} with {} player(s): {}", pad, level.dimension().location(), crew.size(), r);
        if (r == Result.SILO) award(crew);
    }

    /** The decision for a launch from {@code pad} (pure apart from the block reads). */
    public static Result judge(BlockGetter level, BlockPos pad, List<ServerPlayer> crew, boolean hatch) {
        if (crew.isEmpty()) return Result.NO_CREW;
        if (!deepEnough(level, pad)) return Result.SHALLOW;
        if (!hatch) return Result.NO_HATCH;
        return Result.SILO;
    }

    public static void award(List<ServerPlayer> crew) {
        for (ServerPlayer p : crew) ProofRegistry.SILO_LAUNCH.get().trigger(p);
    }

    /** True when the pad lies at least {@link #DEPTH} blocks below the terrain on all four sides. */
    public static boolean deepEnough(BlockGetter level, BlockPos pad) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            boolean wall = false;
            for (int r = MIN_REACH; r <= MAX_REACH && !wall; r++) wall = solidColumn(level, pad.relative(d, r));
            if (!wall) return false;
        }
        return true;
    }

    private static boolean solidColumn(BlockGetter level, BlockPos base) {
        for (int y = 1; y <= DEPTH; y++) {
            BlockPos p = base.above(y);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.isSolidRender(level, p)) return false;
        }
        return true;
    }

    /** The space a hatch moves in: {@link #HATCH_RADIUS} around the shaft, from the pad up to {@code HATCH_RADIUS} above the rim. */
    public static AABB hatchBox(BlockPos pad) {
        return new AABB(pad.getX() - HATCH_RADIUS, pad.getY(), pad.getZ() - HATCH_RADIUS,
            pad.getX() + HATCH_RADIUS + 1, pad.getY() + DEPTH + HATCH_RADIUS + 1, pad.getZ() + HATCH_RADIUS + 1);
    }

    /** The pad under a rocket: its own block (the pad is two pixels high) or the one below. */
    @Nullable
    private static BlockPos findPad(ServerLevel level, BlockPos at) {
        if (rockets.isPad(level.getBlockState(at))) return at;
        if (rockets.isPad(level.getBlockState(at.below()))) return at.below();
        return null;
    }

    private static List<ServerPlayer> crew(Entity rocket) {
        List<ServerPlayer> out = new ArrayList<>();
        for (Entity p : rocket.getIndirectPassengers()) if (p instanceof ServerPlayer sp) out.add(sp);
        return out;
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        watched.clear();
    }
}
