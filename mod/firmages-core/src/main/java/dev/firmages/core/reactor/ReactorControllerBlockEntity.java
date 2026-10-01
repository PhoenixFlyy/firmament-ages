package dev.firmages.core.reactor;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs {@link ReactorCycle} every 10 ticks against the reactor of a touching stabilizer or injector. The swap pulls
 * awakened draconium from slot 0 and then from touching inventories (never from reactor parts), and puts chaos
 * fragments into slots 1..3 and then into touching inventories. Pipes may insert fuel into slot 0 and extract from
 * slots 1..3 on every side.
 */
public class ReactorControllerBlockEntity extends BlockEntity {
    public static final int FUEL_SLOT = 0;
    public static final int FIRST_OUT = 1;
    public static final int SLOTS = 4;
    private static final int PERIOD = 10;

    /** Loaded controllers, for {@code /firmages reactor status}. */
    private static final Set<ReactorControllerBlockEntity> LOADED = ConcurrentHashMap.newKeySet();

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler external = new External();
    private final ReactorCycle cycle = new ReactorCycle();
    private int comparator;
    /** The state last marked for saving, so a steady controller does not dirty its chunk every 10 ticks. */
    private ReactorCycle.State savedState = ReactorCycle.State.MONITOR;

    public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ReactorRegistry.REACTOR_CONTROLLER_BE.get(), pos, state);
    }

    public static Set<ReactorControllerBlockEntity> loaded() {
        return LOADED;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    public ItemStackHandler items() {
        return items;
    }

    /** The view pipes and hoppers get (capability). */
    public IItemHandler external() {
        return external;
    }

    public ReactorCycle cycle() {
        return cycle;
    }

    public int comparator() {
        return comparator;
    }

    public static ReactorCycle.Settings settings() {
        if (!ServerConfig.loaded()) return ReactorCycle.Settings.DEFAULT;
        return new ReactorCycle.Settings(ServerConfig.REACTOR_SHUTDOWN_AT_CONVERSION.get(), ServerConfig.REACTOR_MIN_FUEL.get(),
            ServerConfig.REACTOR_MAX_CHAOS.get());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReactorControllerBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % PERIOD != 0) return;
        be.step();
    }

    /** One controller step (also called directly by GameTests). */
    public void step() {
        if (level == null || level.isClientSide) return;
        if (ServerConfig.loaded() && !ServerConfig.REACTOR_CONTROLLER_ENABLED.get()) {
            cycle.tick(null, settings(), (r, s) -> {});
            apply(null);
            return;
        }
        ReactorView r = ReactorLookup.touching(level, worldPosition);
        cycle.tick(r, settings(), this::swap);
        apply(r);
    }

    private void apply(@Nullable ReactorView r) {
        BlockState state = getBlockState();
        boolean ready = cycle.ready();
        if (state.getValue(ReactorControllerBlock.READY) != ready) {
            level.setBlock(worldPosition, state.setValue(ReactorControllerBlock.READY, ready), Block.UPDATE_ALL);
        }
        int c = r == null ? 0 : ReactorFuel.comparator(r.fuel(), r.chaos());
        if (c != comparator) {
            comparator = c;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        if (cycle.state() != savedState) {
            savedState = cycle.state();
            setChanged();
        }
    }

    // ---- swap ------------------------------------------------------------------------------------------------

    private void swap(ReactorView r, ReactorCycle.Settings s) {
        removeChaos(r, s);
        addFuel(r, s);
    }

    private void removeChaos(ReactorView r, ReactorCycle.Settings s) {
        int[] split = ReactorFuel.chaosSplit(r.chaos() - s.maxChaos());
        for (int i = 0; i < 3; i++) {
            int left = split[i];
            while (left > 0) {
                int n = Math.min(64, left);
                ItemStack stack = ReactorItems.chaos(i, n);
                if (stack.isEmpty()) return;
                ItemStack rest = store(stack);
                int moved = n - rest.getCount();
                if (moved <= 0) return;
                r.removeChaos(moved * ReactorFuel.UNITS[i]);
                left -= moved;
            }
        }
    }

    /** Own output slots first, then touching inventories. @return what did not fit */
    private ItemStack store(ItemStack stack) {
        ItemStack rest = stack;
        for (int slot = FIRST_OUT; slot < SLOTS && !rest.isEmpty(); slot++) rest = items.insertItem(slot, rest, false);
        for (IItemHandler h : neighbours()) {
            if (rest.isEmpty()) break;
            for (int slot = 0; slot < h.getSlots() && !rest.isEmpty(); slot++) rest = h.insertItem(slot, rest, false);
        }
        return rest;
    }

    private void addFuel(ReactorView r, ReactorCycle.Settings s) {
        List<IItemHandler> sources = new ArrayList<>();
        sources.add(new FuelSlotOnly(items));
        sources.addAll(neighbours());
        for (int unit : ReactorFuel.UNITS) {
            for (IItemHandler h : sources) {
                for (int slot = 0; slot < h.getSlots(); slot++) {
                    double need = s.minFuel() - r.fuel();
                    if (need <= 0) return;
                    ItemStack in = h.getStackInSlot(slot);
                    if (ReactorItems.fuelValue(in) != unit) continue;
                    int n = Math.min(ReactorFuel.fits(unit, in.getCount(), r.fuel(), r.chaos()), (int) Math.ceil(need / unit));
                    if (n <= 0) continue;
                    ItemStack got = h.extractItem(slot, n, false);
                    if (ReactorItems.fuelValue(got) != unit) {
                        if (!got.isEmpty()) store(got);
                        continue;
                    }
                    r.addFuel(got.getCount() * unit);
                }
            }
        }
    }

    /** Item handlers of touching blocks, except reactor parts and other controllers' own fuel paths. */
    private List<IItemHandler> neighbours() {
        List<IItemHandler> out = new ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (!level.isLoaded(p) || ReactorLookup.at(level, p) != null) continue;
            if (level.getBlockEntity(p) instanceof ReactorControllerBlockEntity) continue;
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, p, d.getOpposite());
            if (h != null) out.add(h);
        }
        return out;
    }

    // ---- status ----------------------------------------------------------------------------------------------

    public List<String> statusLines() {
        List<String> out = new ArrayList<>();
        ReactorView r = level == null ? null : ReactorLookup.touching(level, worldPosition);
        out.add(String.format(Locale.ROOT, "Reactor controller at %d %d %d: %s (%s)", worldPosition.getX(), worldPosition.getY(),
            worldPosition.getZ(), cycle.state(), cycle.detail()));
        if (r != null) {
            out.add(String.format(Locale.ROOT, "  reactor %s, fuel %.0f, chaos %.0f, conversion %.0f%%, redstone %d, comparator %d",
                r.phase(), r.fuel(), r.chaos(), ReactorFuel.conversion(r.fuel(), r.chaos()) * 100, cycle.ready() ? 15 : 0, comparator));
        } else {
            out.add("  no reactor bound to a touching stabilizer or injector");
        }
        ReactorCycle.Settings s = settings();
        out.add(String.format(Locale.ROOT, "  shutdown at %.0f%% conversion, refuel to %d, chaos allowed %d; buffer: fuel %d, chaos out %d",
            s.shutdownAtConversion() * 100, s.minFuel(), s.maxChaos(), items.getStackInSlot(FUEL_SLOT).getCount(), outputCount()));
        return out;
    }

    private int outputCount() {
        int n = 0;
        for (int i = FIRST_OUT; i < SLOTS; i++) n += items.getStackInSlot(i).getCount();
        return n;
    }

    // ---- persistence -----------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putString("state", cycle.state().name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        try {
            cycle.restore(ReactorCycle.State.valueOf(tag.getString("state")));
        } catch (IllegalArgumentException e) {
            cycle.restore(ReactorCycle.State.MONITOR);
        }
        savedState = cycle.state();
    }

    // ---- handlers --------------------------------------------------------------------------------------------

    /** Pipes: insert fuel into slot 0 only, extract from the output slots only. */
    private final class External implements IItemHandler {
        @Override public int getSlots() { return SLOTS; }
        @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != FUEL_SLOT || ReactorItems.fuelValue(stack) <= 0) return stack;
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < FIRST_OUT || slot >= SLOTS) return ItemStack.EMPTY;
            return items.extractItem(slot, amount, simulate);
        }

        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == FUEL_SLOT && ReactorItems.fuelValue(stack) > 0; }
    }

    /** The internal fuel slot as a one-slot source for {@link #addFuel}. */
    private record FuelSlotOnly(ItemStackHandler items) implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(FUEL_SLOT); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return items.extractItem(FUEL_SLOT, amount, simulate); }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }
}
