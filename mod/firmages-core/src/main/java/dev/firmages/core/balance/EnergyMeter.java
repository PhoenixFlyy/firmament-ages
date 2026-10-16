package dev.firmages.core.balance;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The FE safety net and the meter of the energy audit (SPEC §37, §38): one capability provider, registered first for
 * every block entity type, that answers only while it has a reason to and otherwise lets the block's own provider
 * answer (it returns null). It has a reason for
 * <ul>
 *   <li>a block whose id is listed in {@code balance.feCap}: what is drawn out of it through the capability is capped
 *   per game tick;</li>
 *   <li>a block looked up from the side of such a block (a neighbour it pushes into: the lookup's side faces the
 *   pusher, so {@code pos.relative(side)} is the source): what it receives from that side counts against the source's
 *   cap, so a generator that pushes on its own is capped too;</li>
 *   <li>a block inside a running {@code /firmages energy audit}: it counts what goes in and out.</li>
 * </ul>
 * The wrapper asks the block's own capability (the provider steps aside for that one nested lookup) and delegates
 * everything else. Caches made before a cap or an audit keep the plain storage until they are invalidated; the audit
 * invalidates the capabilities in its radius at start and end, and a changed {@code balance.feCap} applies to new
 * lookups (a chunk reload, a placed block, a restart). Mekanism's own energy link (strict energy) is metered and capped
 * the same way by {@code compat/mekanism/MekEnergyMeter}.
 */
public final class EnergyMeter {
    private static final ThreadLocal<Boolean> INNER = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile Map<Block, Integer> caps = Map.of();
    /** Per capped block position: {tick, FE given in that tick}. */
    private static final Map<Level, Map<Long, long[]>> BUDGETS = new ConcurrentHashMap<>();
    @Nullable
    static volatile EnergyAudit audit;

    private EnergyMeter() {}

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EventPriority.HIGHEST, EnergyMeter::capabilities);
        modBus.addListener(ModConfigEvent.Loading.class, e -> configChanged(e.getConfig().getSpec()));
        modBus.addListener(ModConfigEvent.Reloading.class, e -> configChanged(e.getConfig().getSpec()));
        gameBus.addListener(EnergyMeter::onServerTick);
        gameBus.addListener(EnergyMeter::onServerStopped);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        int n = 0;
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, EnergyMeter::provide);
            n++;
        }
        if (ModList.get().isLoaded("mekanism")) dev.firmages.core.compat.mekanism.MekEnergyMeter.register(event);
        FirmagesCore.LOGGER.debug("Energy meter: provider registered first for {} block entity types", n);
    }

    private static void configChanged(net.neoforged.fml.config.IConfigSpec spec) {
        if (spec != ServerConfig.SPEC) return;
        FeCaps parsed = FeCaps.parse(ServerConfig.BALANCE_FE_CAP.get());
        Map<Block, Integer> m = new IdentityHashMap<>();
        parsed.errors().forEach(e -> FirmagesCore.LOGGER.warn("balance.feCap: {}", e));
        parsed.caps().forEach((id, cap) -> {
            ResourceLocation rl = ResourceLocation.parse(id);
            if (BuiltInRegistries.BLOCK.containsKey(rl)) m.put(BuiltInRegistries.BLOCK.get(rl), cap);
            else FirmagesCore.LOGGER.warn("balance.feCap: {} is no registered block; ignored", id);
        });
        caps = Map.copyOf(m);
        if (!m.isEmpty()) FirmagesCore.LOGGER.info("balance.feCap: {} block type(s) capped: {}", m.size(), parsed.caps());
    }

    /** The cap of {@code block} in FE per tick, or null. */
    @Nullable
    static Integer capOf(Block block) {
        Map<Block, Integer> c = caps;
        return c.isEmpty() ? null : c.get(block);
    }

    /** What the meter wants to know about a lookup at {@code pos} from {@code side}; null when it steps aside. */
    @Nullable
    public static Watch watch(BlockEntity be, @Nullable Direction side) {
        EnergyAudit a = audit;
        if (a == null && caps.isEmpty()) return null;
        if (INNER.get()) return null;
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return null;
        BlockPos pos = be.getBlockPos();
        Integer own = capOf(be.getBlockState().getBlock());
        BlockPos src = null;
        Integer srcCap = null;
        if (side != null && !caps.isEmpty()) {
            BlockPos s = pos.relative(side);
            if (level.isLoaded(s)) {
                srcCap = capOf(level.getBlockState(s).getBlock());
                if (srcCap != null) src = s.immutable();
            }
        }
        // inside the audit, or facing a block inside it (a pusher in the radius feeding a block just outside)
        boolean audited = a != null && (a.covers(level, pos) || (side != null && a.covers(level, pos.relative(side))));
        if (!audited && own == null && srcCap == null) return null;
        BlockPos pushedFrom = side == null ? null : pos.relative(side).immutable();
        return new Watch(level, pos.immutable(), own, src, srcCap, audited ? a : null, pushedFrom);
    }

    /** Runs {@code lookup} with the meter stepped aside, so the block's own provider answers. */
    public static <T> T unwrapped(java.util.function.Supplier<T> lookup) {
        INNER.set(Boolean.TRUE);
        try {
            return lookup.get();
        } finally {
            INNER.set(Boolean.FALSE);
        }
    }

    @Nullable
    private static IEnergyStorage provide(BlockEntity be, @Nullable Direction side) {
        Watch w = watch(be, side);
        if (w == null) return null;
        IEnergyStorage own = unwrapped(() -> w.level().getCapability(Capabilities.EnergyStorage.BLOCK, w.pos(), be.getBlockState(), be, side));
        return own == null ? null : new Metered(own, w);
    }

    /**
     * One metered lookup.
     *
     * @param ownCap     the cap of the block itself (FE per tick it may give), or null
     * @param source     the capped block this lookup faces (a pusher), or null
     * @param sourceCap  its cap
     * @param audit      the running audit that covers the block, or null
     * @param pushedFrom the position the lookup faces (the presumed pusher), or null without a side
     */
    public record Watch(Level level, BlockPos pos, @Nullable Integer ownCap, @Nullable BlockPos source, @Nullable Integer sourceCap,
                        @Nullable EnergyAudit audit, @Nullable BlockPos pushedFrom) {
        /** FE the block may still give now (Long.MAX_VALUE without a cap). */
        public long ownRoom() {
            return ownCap == null ? Long.MAX_VALUE : room(level, pos, ownCap);
        }

        /** FE the facing pusher may still give now (Long.MAX_VALUE without a cap). */
        public long sourceRoom() {
            return sourceCap == null ? Long.MAX_VALUE : room(level, source, sourceCap);
        }

        /** {@code fe} FE went out of the block (drawn through its capability). */
        public void gave(long fe) {
            if (fe <= 0) return;
            if (ownCap != null) spend(level, pos, fe);
            if (audit != null) audit.out(pos, fe);
        }

        /** {@code fe} FE came into the block (from the facing side when the lookup had one). */
        public void took(long fe) {
            if (fe <= 0) return;
            if (sourceCap != null) spend(level, source, fe);
            if (audit != null) {
                audit.in(pos, fe);
                if (pushedFrom != null) audit.pushed(level, pushedFrom, fe);
            }
        }
    }

    static long room(Level level, BlockPos pos, int cap) {
        long[] b = BUDGETS.computeIfAbsent(level, l -> new ConcurrentHashMap<>()).get(pos.asLong());
        return b == null ? cap : FeCaps.room(cap, level.getGameTime(), b[0], b[1]);
    }

    static void spend(Level level, BlockPos pos, long fe) {
        long now = level.getGameTime();
        BUDGETS.computeIfAbsent(level, l -> new ConcurrentHashMap<>()).compute(pos.asLong(), (k, b) -> {
            if (b == null || b[0] != now) return new long[] {now, fe};
            b[1] += fe;
            return b;
        });
    }

    /** The FE storage of a block, wrapped: caps what it gives and what a capped neighbour pushes into it, counts for the audit. */
    static final class Metered implements IEnergyStorage {
        private final IEnergyStorage own;
        private final Watch w;

        Metered(IEnergyStorage own, Watch w) {
            this.own = own;
            this.w = w;
        }

        @Override
        public int receiveEnergy(int max, boolean simulate) {
            int allowed = (int) Math.min(max, w.sourceRoom());
            int got = own.receiveEnergy(allowed, simulate);
            if (!simulate) w.took(got);
            return got;
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            int allowed = (int) Math.min(max, w.ownRoom());
            int got = own.extractEnergy(allowed, simulate);
            if (!simulate) w.gave(got);
            return got;
        }

        @Override
        public int getEnergyStored() {
            return own.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return own.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return own.canExtract();
        }

        @Override
        public boolean canReceive() {
            return own.canReceive();
        }
    }

    // ---------------------------------------------------------------- audit lifecycle

    /** Starts an audit (replacing a running one). */
    public static void start(EnergyAudit a) {
        EnergyAudit old = audit;
        if (old != null) old.cancel("replaced by a new audit");
        audit = a;
        a.begin();
    }

    @Nullable
    public static EnergyAudit running() {
        return audit;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        EnergyAudit a = audit;
        if (a != null && a.due(event.getServer())) {
            audit = null;
            a.finish();
        }
        if (event.getServer().getTickCount() % 200 == 0) {
            // budgets of past ticks are dead weight
            for (Map.Entry<Level, Map<Long, long[]>> e : BUDGETS.entrySet()) {
                long now = e.getKey().getGameTime();
                e.getValue().values().removeIf(b -> b[0] < now - 1);
            }
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        audit = null;
        BUDGETS.clear();
    }

    /** For the GameTests: the caps from a list of entries now (the config file keeps its value). */
    public static void setCapsForTest(List<String> entries) {
        Map<Block, Integer> m = new HashMap<>();
        FeCaps.parse(entries).caps().forEach((id, cap) -> m.put(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)), cap));
        caps = Map.copyOf(m);
        BUDGETS.clear();
    }
}
