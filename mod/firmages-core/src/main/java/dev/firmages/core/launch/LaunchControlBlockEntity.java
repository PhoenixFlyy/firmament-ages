package dev.firmages.core.launch;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The launch control's state and the mission state machine (SPEC §31.5): a probe slot, a module slot, nine output
 * slots, a 16,000 mB fuel tank, the hand throttle, the countdown and the {@link Mission} in flight.
 * <p>
 * {@code IDLE} waits on the start conditions of {@link LaunchRules#startWait}. The countdown takes nothing; a failing
 * condition aborts it. Ignition takes one module, the probe and {@code launch_mb}; {@code ASCENT} climbs for
 * {@link LaunchRules#ASCENT_TICKS}; {@code FLIGHT} burns fuel at the throttle and pauses on an empty tank; {@code DESCENT}
 * falls for {@link LaunchRules#DESCENT_TICKS} and then unloads the fixed returns and the probe (waiting while the output
 * is full). During the countdown and the climb the control gives redstone 15 on its back (the silo hatch).
 */
public final class LaunchControlBlockEntity extends BlockEntity {
    public static final int PROBE_SLOT = 0;
    public static final int MODULE_SLOT = 1;
    public static final int OUT_FIRST = 2;
    public static final int OUT_SLOTS = 9;
    public static final int SLOTS = OUT_FIRST + OUT_SLOTS;
    public static final int CAPACITY = 16_000;
    private static final int VALIDATE_TICKS = 100;
    private static final int SYNC_TICKS = 10;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            contentsChanged();
        }
    };
    private final FluidTank tank = new FluidTank(CAPACITY, LaunchControlBlockEntity::isFuel) {
        @Override
        protected void onContentsChanged() {
            contentsChanged();
        }
    };
    private final IItemHandler automationItems = new Slots(false);
    private final IItemHandler handItems = new Slots(true);
    private final IFluidHandler automationFluid = new Fuel(false);
    private final IFluidHandler handFluid = new Fuel(true);

    @Nullable
    private UUID owner;
    private int manualStep = 1;
    @Nullable
    private Mission mission;
    /** Ticks into the countdown, -1 when none runs. */
    private int countdown = -1;

    // server: the complex
    private boolean validateRequested = true;
    private long nextValidate;
    private LaunchService.Complex complex = LaunchService.Complex.NONE;
    private Set<BlockPos> linkedPorts = Set.of();
    @Nullable
    private BlockPos linkedCradle;
    private boolean emitting;
    private boolean dirtySync = true;
    private long lastSync;
    private LaunchRules.Phase sentPhase = LaunchRules.Phase.IDLE;
    private ItemStack sentProbe = ItemStack.EMPTY;
    @Nullable
    private Direction sentTrench;
    private long sentStart;

    // synced view (server: computed each tick; client: from the update tag)
    private LaunchRules.Phase viewPhase = LaunchRules.Phase.IDLE;
    private long viewPhaseStart;
    private LaunchRules.Wait viewWait = LaunchRules.Wait.NO_CRADLE;
    private int viewPercent;
    private int viewStep = 1;
    private int viewSteps = 3;
    private boolean viewRedstone;
    private int viewMissing = -1;
    private int viewLaunchMb;
    private int viewCountdownLeft;

    public LaunchControlBlockEntity(BlockPos pos, BlockState state) {
        super(LaunchRegistry.LAUNCH_CONTROL_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- fuel and items

    private static String cachedTagId = "";
    private static TagKey<Fluid> cachedTag;

    /** The fuel tag of the loaded fuel file, or null. */
    @Nullable
    public static TagKey<Fluid> fuelTag() {
        var f = LaunchDataLoader.current().fuel();
        if (f.isEmpty()) return null;
        String id = f.get().fluidTag();
        if (!id.equals(cachedTagId) || cachedTag == null) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return null;
            cachedTag = TagKey.create(Registries.FLUID, rl);
            cachedTagId = id;
        }
        return cachedTag;
    }

    /** A fluid of the fuel tag ({@code #ad_astra:fuel} in the pack). */
    public static boolean isFuel(FluidStack f) {
        TagKey<Fluid> tag = fuelTag();
        return tag != null && !f.isEmpty() && f.is(tag);
    }

    public static boolean isProbe(ItemStack s) {
        return s.is(LaunchRegistry.PROBE.get());
    }

    /** A module item that has a loaded module file. */
    public static boolean isModule(ItemStack s) {
        return !s.isEmpty() && LaunchDataLoader.current().module(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).isPresent();
    }

    // ---------------------------------------------------------------- accessors

    public IItemHandler automationItems() {
        return automationItems;
    }

    public IFluidHandler automationFluid() {
        return automationFluid;
    }

    public IItemHandler handItems() {
        return handItems;
    }

    public IFluidHandler handFluid() {
        return handFluid;
    }

    public ItemStack stack(int slot) {
        return items.getStackInSlot(slot);
    }

    public List<ItemStack> slots() {
        List<ItemStack> out = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) out.add(items.getStackInSlot(i));
        return out;
    }

    public FluidStack fluid() {
        return tank.getFluid();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public Mission mission() {
        return mission;
    }

    public LaunchRules.Phase phase() {
        return viewPhase;
    }

    /** Game time the current phase began. */
    public long phaseStart() {
        return viewPhaseStart;
    }

    public LaunchRules.Wait waitReason() {
        return viewWait;
    }

    public int percent() {
        return viewPercent;
    }

    /** The effective throttle step (1-based). */
    public int step() {
        return viewStep;
    }

    public int steps() {
        return viewSteps;
    }

    /** The redstone signal on the back sets the throttle. */
    public boolean redstoneThrottle() {
        return viewRedstone;
    }

    public int missing() {
        return viewMissing;
    }

    public int launchMb() {
        return viewLaunchMb;
    }

    public int countdownLeft() {
        return viewCountdownLeft;
    }

    public LaunchService.Complex complex() {
        return complex;
    }

    /** The ports this control feeds (server). */
    public boolean feeds(BlockPos port) {
        return linkedPorts.contains(port);
    }

    /** True while the control gives redstone on its back (countdown and climb). */
    public boolean emitting() {
        return emitting;
    }

    public void requestValidation() {
        validateRequested = true;
    }

    // ---------------------------------------------------------------- hand

    /** Right-click with an empty hand on a formed complex: the next throttle step. Returns the new step. */
    public int cycleThrottle() {
        manualStep = LaunchRules.nextStep(manualStep, LaunchDataLoader.current().steps().size());
        setChanged();
        dirtySync = true;
        return manualStep;
    }

    /** Puts a probe or a module from the hand in; returns the rest. */
    public ItemStack insertFromHand(ItemStack stack) {
        ItemStack left = handItems.insertItem(PROBE_SLOT, stack, false);
        return handItems.insertItem(MODULE_SLOT, left, false);
    }

    /** The outputs first, then the probe, then the modules. */
    public ItemStack takeByHand() {
        for (int i = OUT_FIRST; i < SLOTS; i++) if (!items.getStackInSlot(i).isEmpty()) return items.extractItem(i, 64, false);
        if (!items.getStackInSlot(PROBE_SLOT).isEmpty()) return items.extractItem(PROBE_SLOT, 64, false);
        return items.extractItem(MODULE_SLOT, 64, false);
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, s.copy());
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    /** Debug ({@code /firmages launch finish}): the flight is done; the probe lands on the next tick. */
    public boolean finishNow() {
        if (mission == null) return false;
        mission = mission.with(LaunchRules.Phase.DESCENT, LaunchRules.DESCENT_TICKS, mission.work());
        setChanged();
        dirtySync = true;
        return true;
    }

    /** A mission carried by the control's item (SPEC §31.5), resumed where it stood. */
    public void restoreMission(@Nullable Mission m) {
        if (m == null) return;
        mission = m;
        countdown = -1;
        setChanged();
        dirtySync = true;
    }

    // ---------------------------------------------------------------- tick

    void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        if (validateRequested || now >= nextValidate) {
            validateRequested = false;
            nextValidate = now + VALIDATE_TICKS;
            validate(level);
        }
        LaunchData data = LaunchDataLoader.current();
        List<LaunchRules.Step> steps = data.steps();
        Direction back = getBlockState().getValue(LaunchControlBlock.FACING).getOpposite();
        int rsStep = emitting ? 0 : LaunchRules.stepFromRedstone(level.getSignal(worldPosition.relative(back), back), steps.size());
        int step = LaunchRules.effectiveStep(manualStep, rsStep, steps.size());
        LaunchRules.Wait wait;
        int launchMb = data.fuel().map(LaunchData.Fuel::launchMb).orElse(0);

        if (mission != null) {
            wait = tickMission(level, steps.get(step - 1));
        } else {
            wait = startWait(level, data, launchMb, back);
            if (countdown >= 0) {
                if (wait != LaunchRules.Wait.NONE) {
                    countdown = -1; // aborted: nothing was taken
                } else if (++countdown >= ServerConfig.launchCountdownTicks()) {
                    ignite(level, data);
                }
            } else if (wait == LaunchRules.Wait.NONE) {
                countdown = 0;
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
            }
        }

        LaunchRules.Phase phase = mission != null ? mission.phase() : countdown >= 0 ? LaunchRules.Phase.COUNTDOWN : LaunchRules.Phase.IDLE;
        long phaseStart = now - (mission != null ? mission.phaseTime() : Math.max(0, countdown));
        boolean emit = phase == LaunchRules.Phase.COUNTDOWN || phase == LaunchRules.Phase.ASCENT;
        if (emit != emitting) {
            emitting = emit;
            BlockPos behind = worldPosition.relative(back);
            level.neighborChanged(behind, getBlockState().getBlock(), worldPosition);
            level.updateNeighborsAtExceptFromFacing(behind, getBlockState().getBlock(), back.getOpposite());
        }
        int pct = mission == null ? 0 : LaunchRules.percent(mission.progress(), mission.work());
        int left = countdown >= 0 ? ServerConfig.launchCountdownTicks() - countdown : 0;
        if (phase != viewPhase || wait != viewWait || Math.abs(pct - viewPercent) >= 5 || step != viewStep || (rsStep > 0) != viewRedstone
            || complex.missing() != viewMissing || launchMb != viewLaunchMb || (phase == LaunchRules.Phase.COUNTDOWN && left / 20 != viewCountdownLeft / 20)) {
            dirtySync = true;
        }
        boolean phaseChanged = phase != viewPhase;
        viewPhase = phase;
        viewPhaseStart = phaseStart;
        viewWait = wait;
        viewPercent = pct;
        viewStep = step;
        viewSteps = steps.size();
        viewRedstone = rsStep > 0;
        viewMissing = complex.missing();
        viewLaunchMb = launchMb;
        viewCountdownLeft = left;
        BlockState s = getBlockState();
        int shown = Math.max(1, Math.min(3, step));
        if (s.getValue(LaunchControlBlock.THROTTLE) != shown) level.setBlock(worldPosition, s.setValue(LaunchControlBlock.THROTTLE, shown), Block.UPDATE_CLIENTS);
        updateCradle(level, phase, phaseStart);
        if (dirtySync && (phaseChanged || now - lastSync >= SYNC_TICKS)) {
            dirtySync = false;
            lastSync = now;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private LaunchRules.Wait startWait(ServerLevel level, LaunchData data, int launchMb, Direction back) {
        boolean room = data.module(moduleId()).map(m -> fits(m.returns().stream().map(r -> itemStack(r.item(), r.count())).toList(),
            ServerConfig.launchAutoRelaunch() ? ItemStack.EMPTY : items.getStackInSlot(PROBE_SLOT))).orElse(true);
        BlockPos cradle = complex.cradle();
        return LaunchRules.startWait(new LaunchRules.Start(ServerConfig.launchEnabled() && data.valid(), cradle != null, complex.formed(), complex.ports().size(),
            complex.trench() != null, isProbe(items.getStackInSlot(PROBE_SLOT)), isModule(items.getStackInSlot(MODULE_SLOT)),
            isFuel(tank.getFluid()) && tank.getFluidAmount() >= launchMb, room, cradle != null && LaunchService.skyOpen(level, cradle), held(level, back)));
    }

    /** A redstone signal on any face but the back holds the launch. */
    private boolean held(Level level, Direction back) {
        for (Direction d : Direction.values()) {
            if (d != back && level.getSignal(worldPosition.relative(d), d) > 0) return true;
        }
        return false;
    }

    private String moduleId() {
        ItemStack m = items.getStackInSlot(MODULE_SLOT);
        return m.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(m.getItem()).toString();
    }

    private static ItemStack itemStack(String id, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item it = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        return it == null ? ItemStack.EMPTY : new ItemStack(it, count);
    }

    /** Ignition: one module, the probe and {@code launch_mb} go; the mission begins. */
    private void ignite(ServerLevel level, LaunchData data) {
        countdown = -1;
        LaunchData.Module module = data.module(moduleId()).orElse(null);
        if (module == null) return;
        int launchMb = data.fuel().map(LaunchData.Fuel::launchMb).orElse(0);
        ItemStack probe = items.getStackInSlot(PROBE_SLOT).copy();
        items.setStackInSlot(PROBE_SLOT, ItemStack.EMPTY);
        items.extractItem(MODULE_SLOT, 1, false);
        tank.drain(launchMb, IFluidHandler.FluidAction.EXECUTE);
        List<ItemStack> returns = module.returns().stream().map(r -> itemStack(r.item(), r.count())).filter(s -> !s.isEmpty()).toList();
        mission = new Mission(LaunchRules.Phase.ASCENT, 0, 0, module.flightWork(), module.module(), probe, returns);
        BlockPos at = complex.cradle() != null ? complex.cradle() : worldPosition;
        level.playSound(null, at, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 12.0F, 0.5F);
        level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 6.0F, 0.4F);
        FirmagesCore.LOGGER.info("Launch Complex at {}: probe launched with {} (flight work {}, owner {})", worldPosition.toShortString(), module.module(),
            module.flightWork(), owner);
        setChanged();
        dirtySync = true;
    }

    private LaunchRules.Wait tickMission(ServerLevel level, LaunchRules.Step step) {
        Mission m = mission;
        if (!complex.formed() || complex.cradle() == null) return complex.cradle() == null ? LaunchRules.Wait.NO_CRADLE : LaunchRules.Wait.UNFORMED;
        LaunchRules.Wait wait = LaunchRules.Wait.NONE;
        switch (m.phase()) {
            case ASCENT -> mission = m.phaseTime() + 1 >= LaunchRules.ASCENT_TICKS ? m.with(LaunchRules.Phase.FLIGHT, 0, m.progress())
                : m.with(LaunchRules.Phase.ASCENT, m.phaseTime() + 1, m.progress());
            case FLIGHT -> {
                long fuel = isFuel(tank.getFluid()) ? tank.getFluidAmount() : 0;
                LaunchRules.FlightTick ft = LaunchRules.flight(m.progress(), m.work(), fuel, step);
                if (ft.burnt() > 0) tank.drain(ft.burnt(), IFluidHandler.FluidAction.EXECUTE);
                if (ft.paused()) wait = LaunchRules.Wait.FUEL_EMPTY;
                mission = ft.done() ? m.with(LaunchRules.Phase.DESCENT, 0, ft.progress()) : m.with(LaunchRules.Phase.FLIGHT, m.phaseTime() + 1, ft.progress());
            }
            case DESCENT -> {
                if (m.phaseTime() < LaunchRules.DESCENT_TICKS) {
                    mission = m.with(LaunchRules.Phase.DESCENT, m.phaseTime() + 1, m.progress());
                } else if (!unload(level, m)) {
                    wait = LaunchRules.Wait.OUTPUT_FULL;
                }
            }
            default -> mission = m.with(LaunchRules.Phase.FLIGHT, 0, m.progress());
        }
        setChanged();
        return wait;
    }

    /** Puts the returns and the probe away; false (nothing moved) when they do not all fit. */
    private boolean unload(ServerLevel level, Mission m) {
        boolean relaunch = ServerConfig.launchAutoRelaunch() && items.getStackInSlot(PROBE_SLOT).isEmpty();
        if (!fits(m.returns(), relaunch ? ItemStack.EMPTY : m.probe())) return false;
        for (ItemStack r : m.returns()) insertOutput(r.copy(), false);
        if (relaunch) items.setStackInSlot(PROBE_SLOT, m.probe().copy());
        else insertOutput(m.probe().copy(), false);
        mission = null;
        long n = owner == null ? 0 : LaunchService.countFlight(level.getServer(), owner, m.module());
        FirmagesCore.LOGGER.info("Launch Complex at {}: probe returned with {} (module {}, team flights of it {})", worldPosition.toShortString(),
            m.returns().stream().map(s -> s.getCount() + "x" + BuiltInRegistries.ITEM.getKey(s.getItem())).toList(), m.module(), n);
        level.playSound(null, complex.cradle() != null ? complex.cradle() : worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 0.7F);
        dirtySync = true;
        return true;
    }

    /** All of {@code stacks} plus {@code extra} fit the nine output slots together. */
    private boolean fits(List<ItemStack> stacks, ItemStack extra) {
        ItemStackHandler sim = new ItemStackHandler(OUT_SLOTS);
        for (int i = 0; i < OUT_SLOTS; i++) sim.setStackInSlot(i, items.getStackInSlot(OUT_FIRST + i).copy());
        List<ItemStack> all = new ArrayList<>(stacks);
        if (!extra.isEmpty()) all.add(extra);
        for (ItemStack s : all) {
            ItemStack left = s.copy();
            for (int i = 0; i < OUT_SLOTS && !left.isEmpty(); i++) left = sim.insertItem(i, left, false);
            if (!left.isEmpty()) return false;
        }
        return true;
    }

    private ItemStack insertOutput(ItemStack s, boolean simulate) {
        ItemStack left = s;
        for (int i = OUT_FIRST; i < SLOTS && !left.isEmpty(); i++) left = items.insertItem(i, left, simulate);
        return left;
    }

    // ---------------------------------------------------------------- the complex, ports and cradle

    private void validate(ServerLevel level) {
        complex = LaunchService.check(level, worldPosition);
        Set<BlockPos> ports = complex.formed() ? new HashSet<>(complex.ports()) : Set.of();
        for (BlockPos p : linkedPorts) {
            if (!ports.contains(p) && level.isLoaded(p) && level.getBlockEntity(p) instanceof LaunchPortBlockEntity pb) pb.unlink(worldPosition);
        }
        for (BlockPos p : ports) {
            if (level.getBlockEntity(p) instanceof LaunchPortBlockEntity pb) pb.link(worldPosition);
        }
        linkedPorts = Set.copyOf(ports);
        BlockPos cradle = complex.cradle();
        if (cradle != null) {
            Direction toward = Direction.getNearest(cradle.getX() - worldPosition.getX(), 0, cradle.getZ() - worldPosition.getZ());
            BlockState s = getBlockState();
            if (complex.formed() && toward.getAxis().isHorizontal() && s.getValue(LaunchControlBlock.FACING) != toward) {
                level.setBlock(worldPosition, s.setValue(LaunchControlBlock.FACING, toward), Block.UPDATE_ALL);
            }
        }
        if (linkedCradle != null && !linkedCradle.equals(cradle)) clearCradle(level, linkedCradle);
        linkedCradle = cradle;
        dirtySync = true;
    }

    /** Keeps the cradle's render state (and the far players' effects) in step with the control. */
    private void updateCradle(ServerLevel level, LaunchRules.Phase phase, long phaseStart) {
        BlockPos cradle = linkedCradle;
        if (cradle == null || !level.isLoaded(cradle) || !(level.getBlockEntity(cradle) instanceof ProbeCradleBlockEntity cb)) return;
        ItemStack probe = mission != null ? mission.probe() : items.getStackInSlot(PROBE_SLOT);
        Direction trench = complex.trench();
        boolean probeChanged = !ItemStack.isSameItemSameComponents(probe, sentProbe);
        if (phase == sentPhase && !probeChanged && trench == sentTrench && Math.abs(phaseStart - sentStart) <= 20) return;
        boolean announce = phase != sentPhase || probeChanged;
        sentPhase = phase;
        sentProbe = probe.copy();
        sentTrench = trench;
        sentStart = phaseStart;
        cb.setFx(worldPosition, phase, phaseStart, probe, trench);
        if (announce) LaunchService.sendFx(level, cradle, phase, phaseStart, probe, trench);
    }

    private void clearCradle(ServerLevel level, BlockPos cradle) {
        if (level.isLoaded(cradle) && level.getBlockEntity(cradle) instanceof ProbeCradleBlockEntity cb && worldPosition.equals(cb.control())) {
            cb.setFx(null, LaunchRules.Phase.IDLE, 0, ItemStack.EMPTY, null);
            LaunchService.sendFx(level, cradle, LaunchRules.Phase.IDLE, 0, ItemStack.EMPTY, null);
        }
        sentPhase = LaunchRules.Phase.IDLE;
        sentProbe = ItemStack.EMPTY;
    }

    private void contentsChanged() {
        dirtySync = true;
        setChanged();
    }

    // ---------------------------------------------------------------- handlers

    /** The control's slots as pipes see them ({@code hand} = a player's hand, which may also take probe and modules). */
    private final class Slots implements IItemHandler {
        private final boolean hand;

        Slots(boolean hand) {
            this.hand = hand;
        }

        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= OUT_FIRST || hand) return items.extractItem(slot, amount, simulate);
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == PROBE_SLOT ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case PROBE_SLOT -> isProbe(stack);
                case MODULE_SLOT -> isModule(stack);
                default -> false;
            };
        }
    }

    /** The tank: only fuel goes in; only a hand takes fuel out again. */
    private final class Fuel implements IFluidHandler {
        private final boolean hand;

        Fuel(boolean hand) {
            this.hand = hand;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int t) {
            return CAPACITY;
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return isFuel(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return isFuel(resource) ? tank.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return hand ? tank.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return hand ? tank.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }

    // ---------------------------------------------------------------- lifecycle, save and sync

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) LaunchService.track(sl, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) {
            LaunchService.untrack(sl, worldPosition);
            if (linkedCradle != null) clearCradle(sl, linkedCradle);
            for (BlockPos p : linkedPorts) {
                if (sl.isLoaded(p) && sl.getBlockEntity(p) instanceof LaunchPortBlockEntity pb) pb.unlink(worldPosition);
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel sl) LaunchService.untrack(sl, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        if (owner != null) tag.putUUID("owner", owner);
        tag.putInt("throttle", manualStep);
        if (countdown >= 0) tag.putInt("countdown", countdown);
        if (mission != null) {
            Mission.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), mission).result().ifPresent(t -> tag.put("mission", t));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        tank.readFromNBT(registries, tag.getCompound("tank"));
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        manualStep = Math.max(1, tag.getInt("throttle"));
        countdown = tag.contains("countdown") ? tag.getInt("countdown") : -1;
        mission = tag.contains("mission") ? Mission.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("mission")).result().orElse(null)
            : null;
        if (tag.contains("view")) {
            CompoundTag v = tag.getCompound("view");
            viewPhase = LaunchRules.Phase.byOrdinal(v.getInt("phase"));
            viewPhaseStart = v.getLong("start");
            viewWait = LaunchRules.Wait.byOrdinal(v.getInt("wait"));
            viewPercent = v.getInt("pct");
            viewStep = v.getInt("step");
            viewSteps = v.getInt("steps");
            viewRedstone = v.getBoolean("rs");
            viewMissing = v.getInt("missing");
            viewLaunchMb = v.getInt("launch");
            viewCountdownLeft = v.getInt("left");
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (mission != null) components.set(LaunchRegistry.MISSION.get(), mission);
        if (!tank.isEmpty()) components.set(LaunchRegistry.LAUNCH_FUEL.get(), net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(tank.getFluid()));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        restoreMission(input.get(LaunchRegistry.MISSION.get()));
        var fuel = input.get(LaunchRegistry.LAUNCH_FUEL.get());
        if (fuel != null && !fuel.isEmpty()) tank.setFluid(fuel.copy());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("mission");
        tag.remove("tank");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        CompoundTag v = new CompoundTag();
        v.putInt("phase", viewPhase.ordinal());
        v.putLong("start", viewPhaseStart);
        v.putInt("wait", viewWait.ordinal());
        v.putInt("pct", viewPercent);
        v.putInt("step", viewStep);
        v.putInt("steps", viewSteps);
        v.putBoolean("rs", viewRedstone);
        v.putInt("missing", viewMissing);
        v.putInt("launch", viewLaunchMb);
        v.putInt("left", viewCountdownLeft);
        tag.put("view", v);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
