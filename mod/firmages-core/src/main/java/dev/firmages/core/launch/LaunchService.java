package dev.firmages.core.launch;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.net.LaunchFxPayload;
import dev.firmages.core.net.ShrinePreviewPayload;
import dev.firmages.core.proof.TallyService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Launch Complex on the server (SPEC §31.3, §31.6): the complex check (the Modonomicon multiblock
 * {@code firmages:launch_complex} through {@link ShrineMultiblocks}, then the control at the tower's foot, the port count
 * and the flame trench in code), the triggers that re-validate a control, the probe count per team with the
 * {@code firmages:probe} criterion, the launch effects payload and the status lines.
 */
public final class LaunchService {
    public static final ResourceLocation COMPLEX_ID = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "launch_complex");
    public static final char CONTROL_KEY = 'C';
    public static final char RIM_KEY = 'R';
    /** The cradle stands this many blocks before the control. */
    public static final int CRADLE_DISTANCE = 3;
    /** Horizontal reach and height of the complex around its control (re-validation box). */
    public static final int BOX_RADIUS = 9;
    public static final int BOX_BELOW = TrenchShape.DEPTH + 2;
    public static final int BOX_ABOVE = 21;
    public static final TagKey<Block> TRENCH = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "launch/trench"));
    private static final int FIRE_PERIOD = 20;

    /** Loaded controls per dimension (server thread only). */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> CONTROLS = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static boolean warnedUnknownMultiblock;

    private LaunchService() {}

    public static void register(IEventBus bus) {
        bus.addListener(LaunchService::onReloadListeners);
        bus.addListener(LaunchDataLoader::onTagsUpdated);
        bus.addListener(LaunchService::onBreak);
        bus.addListener(LaunchService::onPlace);
        bus.addListener(LaunchService::onExplosion);
        bus.addListener(LaunchService::onServerTick);
        bus.addListener(LaunchService::onLogin);
        bus.addListener(LaunchService::onServerStopped);
    }

    private static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new LaunchDataLoader());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        CONTROLS.clear();
        DIRTY.clear();
    }

    // ---------------------------------------------------------------- tracking and triggers

    static void track(ServerLevel level, BlockPos pos) {
        CONTROLS.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    static void untrack(ServerLevel level, BlockPos pos) {
        Set<BlockPos> s = CONTROLS.get(level.dimension());
        if (s != null) s.remove(pos);
    }

    /** Every loaded launch control of this level. */
    public static List<BlockPos> controls(ServerLevel level) {
        return List.copyOf(CONTROLS.getOrDefault(level.dimension(), Set.of()));
    }

    /** A block near a loaded control changed: validate that control on its next tick. */
    public static void blockChanged(ServerLevel level, BlockPos pos) {
        Set<BlockPos> s = CONTROLS.get(level.dimension());
        if (s == null || s.isEmpty()) return;
        for (BlockPos c : s) {
            int dy = pos.getY() - c.getY();
            if (Math.abs(pos.getX() - c.getX()) <= BOX_RADIUS && Math.abs(pos.getZ() - c.getZ()) <= BOX_RADIUS && dy >= -BOX_BELOW && dy <= BOX_ABOVE
                && level.getBlockEntity(c) instanceof LaunchControlBlockEntity be) {
                be.requestValidation();
            }
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel sl) blockChanged(sl, event.getPos());
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel sl) blockChanged(sl, event.getPos());
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        for (BlockPos p : event.getAffectedBlocks()) blockChanged(sl, p);
    }

    // ---------------------------------------------------------------- the complex

    /**
     * Result of a complex check.
     *
     * @param cradle  the probe cradle three blocks before the control, or null
     * @param ring    the multiblock check around the cradle, or null without a cradle
     * @param formed  the pattern stands and this control is its {@code C} cell
     * @param ports   the launch ports on the pad rim
     * @param trench  the direction the flame trench opens to, or null
     * @param problem the first structural problem ({@code NONE} when the complex is complete)
     */
    public record Complex(@Nullable BlockPos cradle, @Nullable ShrineMultiblocks.RingCheck ring, boolean formed, List<BlockPos> ports,
                          @Nullable Direction trench, @Nullable TrenchShape.Cell trenchFailure, LaunchRules.Wait problem) {
        static final Complex NONE = new Complex(null, null, false, List.of(), null, null, LaunchRules.Wait.NO_CRADLE);

        /** Counted blocks the pattern still lacks (-1 without a cradle or multiblock). */
        public int missing() {
            return ring == null || !ring.known() ? -1 : Math.max(0, ring.total() - ring.matched()) + (ring.valid() && !formed ? 1 : 0);
        }

        public boolean complete() {
            return problem == LaunchRules.Wait.NONE;
        }
    }

    /** The cradle three blocks before {@code control} on any side, or null. */
    @Nullable
    public static BlockPos findCradle(Level level, BlockPos control) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = control.relative(d, CRADLE_DISTANCE);
            if (level.getBlockState(p).is(LaunchRegistry.PROBE_CRADLE.get())) return p;
        }
        return null;
    }

    /** The complex of the control at {@code control}. */
    public static Complex check(Level level, BlockPos control) {
        BlockPos cradle = findCradle(level, control);
        if (cradle == null) return Complex.NONE;
        ShrineMultiblocks.RingCheck c = ShrineMultiblocks.check(level, cradle, COMPLEX_ID);
        if (!c.known()) {
            if (!warnedUnknownMultiblock) {
                warnedUnknownMultiblock = true;
                FirmagesCore.LOGGER.error("Launch Complex: multiblock {} is not loaded; no complex can form", COMPLEX_ID);
            }
            return new Complex(cradle, c, false, List.of(), null, null, LaunchRules.Wait.UNFORMED);
        }
        boolean atFoot = c.cells().stream().anyMatch(cell -> cell.key() == CONTROL_KEY && cell.pos().equals(control));
        List<BlockPos> ports = new ArrayList<>();
        for (ShrineMultiblocks.Cell cell : c.cells()) {
            if (cell.key() == RIM_KEY && level.getBlockState(cell.pos()).is(LaunchRegistry.LAUNCH_PORT.get())) ports.add(cell.pos());
        }
        boolean formed = c.valid() && atFoot;
        if (!formed) return new Complex(cradle, c, false, List.copyOf(ports), null, null, LaunchRules.Wait.UNFORMED);
        if (!LaunchRules.portsOk(ports.size())) return new Complex(cradle, c, true, List.copyOf(ports), null, null, LaunchRules.Wait.PORTS);
        TrenchShape.Cell firstFailure = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            TrenchShape.Cell f = trenchFailure(level, cradle, d);
            if (f == null) return new Complex(cradle, c, true, List.copyOf(ports), d, null, LaunchRules.Wait.NONE);
            if (firstFailure == null) firstFailure = f;
        }
        return new Complex(cradle, c, true, List.copyOf(ports), null, firstFailure, LaunchRules.Wait.TRENCH);
    }

    /** The world position of a trench cell for a trench that opens toward {@code d}. */
    public static BlockPos trenchPos(BlockPos cradle, Direction d, TrenchShape.Cell cell) {
        return cradle.relative(d, cell.along()).relative(d.getClockWise(), cell.across()).below(cell.depth());
    }

    /** The first trench cell that does not fit for a trench toward {@code d}, or null. */
    @Nullable
    public static TrenchShape.Cell trenchFailure(Level level, BlockPos cradle, Direction d) {
        return TrenchShape.firstFailure(cell -> {
            BlockPos p = trenchPos(cradle, d, cell);
            BlockState s = level.getBlockState(p);
            return switch (cell.kind()) {
                case CHANNEL, OPENING -> s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
                case WALL -> s.is(TRENCH);
            };
        });
    }

    /** Open sky from the cradle to the build limit (motion-blocking heightmap). */
    public static boolean skyOpen(Level level, BlockPos cradle) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, cradle.getX(), cradle.getZ()) <= cradle.getY() + 1;
    }

    /** The ghost of an unfinished complex ({@code firmages:shrine_preview}, anchored at the cradle). */
    public static Optional<ShrinePreviewPayload> preview(Complex c) {
        if (c.cradle() == null || c.ring() == null || !c.ring().known() || c.ring().valid()) return Optional.empty();
        return Optional.of(new ShrinePreviewPayload(COMPLEX_ID, c.cradle(), c.ring().bestRotation().ordinal(), false));
    }

    // ---------------------------------------------------------------- effects

    /** Sends {@code firmages:launch_fx} to the players within {@code launch.fxRange} of the cradle. */
    static void sendFx(ServerLevel level, BlockPos cradle, LaunchRules.Phase phase, long phaseStart, ItemStack probe, @Nullable Direction trench) {
        LaunchFxPayload p = new LaunchFxPayload(cradle, phase.ordinal(), phaseStart, probe.copy(), trench == null ? -1 : trench.get3DDataValue());
        int r = ServerConfig.launchFxRange();
        PacketDistributor.sendToPlayersNear(level, null, cradle.getX() + 0.5, cradle.getY() + 0.5, cradle.getZ() + 0.5, r, p);
    }

    // ---------------------------------------------------------------- probe counts and the criterion

    /** Counts one flight of {@code module} for {@code owner}'s team; returns the team's new count of that module. */
    public static long countFlight(MinecraftServer server, UUID owner, String module) {
        UUID team = TallyService.teamOf(owner);
        LaunchSavedData data = LaunchSavedData.get(server);
        data.add(team, module, 1);
        DIRTY.add(team);
        return data.of(team).getOrDefault(module, 0L);
    }

    /** Fires the criterion now for every online member of the teams counted since the last fire (GameTests call it). */
    public static void fireDirty(MinecraftServer server) {
        if (DIRTY.isEmpty()) return;
        Set<UUID> teams = new HashSet<>(DIRTY);
        DIRTY.clear();
        LaunchSavedData data = LaunchSavedData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID team = TallyService.teamOf(p.getUUID());
            if (teams.contains(team)) LaunchRegistry.PROBE_TRIGGER.get().trigger(p, data.of(team));
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % FIRE_PERIOD == 0) fireDirty(event.getServer());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            LaunchRegistry.PROBE_TRIGGER.get().trigger(p, LaunchSavedData.get(p.server).of(TallyService.teamOf(p.getUUID())));
        }
    }

    // ---------------------------------------------------------------- status (command)

    public static List<Component> status(ServerLevel level, BlockPos pos) {
        List<Component> out = new ArrayList<>();
        if (!(level.getBlockEntity(pos) instanceof LaunchControlBlockEntity be)) {
            out.add(Component.literal("No launch control at " + pos.toShortString()));
            return out;
        }
        Complex c = check(level, pos);
        out.add(Component.literal("Launch Complex at " + pos.toShortString() + " in " + level.dimension().location()).withStyle(ChatFormatting.GOLD));
        String ring = c.ring() == null ? "no cradle three blocks before the control"
            : !c.ring().known() ? "multiblock " + COMPLEX_ID + " is not loaded"
            : c.ring().valid() ? "pattern complete, rotation " + c.ring().rotation() + (c.formed() ? "" : ", but this control is not the tower's foot")
            : "pattern " + c.ring().matched() + "/" + c.ring().total() + ", missing " + c.ring().missing();
        out.add(Component.literal("Cradle " + (c.cradle() == null ? "-" : c.cradle().toShortString()) + "; " + ring));
        out.add(Component.literal("Ports " + c.ports().size() + " " + c.ports().stream().map(BlockPos::toShortString).toList() + "; trench "
            + (c.trench() != null ? "opens " + c.trench().getSerializedName()
            : c.trenchFailure() == null ? "-" : "fails at " + c.trenchFailure()) + "; sky " + (c.cradle() != null && skyOpen(level, c.cradle()) ? "open" : "closed")));
        Mission m = be.mission();
        LaunchRules.Phase phase = be.phase();
        out.add(Component.literal("Phase " + phase + (m == null ? "" : " (" + m.phaseTime() + " ticks in, progress " + m.progress() + "/" + m.work() + ", module "
            + m.module() + ", returns " + m.returns().stream().map(s -> s.getCount() + "x" + BuiltInRegistries.ITEM.getKey(s.getItem())).toList() + ")")
            + "; wait " + be.waitReason() + "; throttle " + be.step() + " of " + LaunchDataLoader.current().steps().size()));
        var f = be.fluid();
        out.add(Component.literal("Tank " + (f.isEmpty() ? "empty" : f.getAmount() + " mB " + BuiltInRegistries.FLUID.getKey(f.getFluid())) + "; slots "
            + be.slots().stream().map(s -> s.isEmpty() ? "-" : s.getCount() + "x" + BuiltInRegistries.ITEM.getKey(s.getItem())).toList()
            + "; owner " + (be.owner() == null ? "none" : be.owner())));
        LaunchData d = LaunchDataLoader.current();
        out.add(Component.literal("Data: fuel " + d.fuel().map(x -> "#" + x.fluidTag() + ", launch " + x.launchMb() + " mB").orElse("missing")
            + "; modules " + d.modules().keySet() + (d.errors().isEmpty() ? "" : "; rejected " + d.errors())));
        return out;
    }
}
