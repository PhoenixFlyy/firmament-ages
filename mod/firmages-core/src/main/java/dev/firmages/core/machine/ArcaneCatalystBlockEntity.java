package dev.firmages.core.machine;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The Arcane Catalyst (SPEC §41): faces one Immersive Engineering or Mekanism machine (block tag
 * {@code firmages:catalysable}) and, while Source and a turning shaft charge it, gives that machine extra work ticks
 * ({@link CatalystRules}). It pays for each extra tick from its own FE buffer, as much as the machine drew in it times
 * {@code machines.catalystFeSurcharge}. Optional: no recipe and no Age needs it.
 */
public final class ArcaneCatalystBlockEntity extends BlockEntity {
    public static final int CAPACITY = 400_000;

    /** Why the catalyst works or waits (Jade line, right-click line). */
    public enum Status {
        NO_MACHINE("no_machine"), NO_ROTATION("no_rotation"), NO_SOURCE("no_source"), NO_POWER("no_power"), QUICKENING("quickening");

        private final String key;

        Status(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Status byOrdinal(int o) {
            Status[] all = values();
            return o >= 0 && o < all.length ? all[o] : NO_MACHINE;
        }
    }

    private int energy;
    private boolean charged;
    private double accumulated;
    private long lastSurcharge = -1;
    private Status status = Status.NO_MACHINE;
    // counters for /firmages catalyst and the GameTests
    private long extraTicksGiven;
    private long fePaid;
    private final IEnergyStorage energyHandler = new Energy();

    public ArcaneCatalystBlockEntity(BlockPos pos, BlockState state) {
        super(MachineRegistry.ARCANE_CATALYST_BE.get(), pos, state);
    }

    public IEnergyStorage energyHandler() {
        return energyHandler;
    }

    public int energy() {
        return energy;
    }

    public Status status() {
        return status;
    }

    public long extraTicksGiven() {
        return extraTicksGiven;
    }

    public long fePaid() {
        return fePaid;
    }

    public Direction facing() {
        return getBlockState().getValue(ArcaneCatalystBlock.FACING);
    }

    // ---------------------------------------------------------------- tick

    void serverTick(ServerLevel level) {
        Direction front = facing();
        BlockPos target = worldPosition.relative(front);
        BlockState ts = level.getBlockState(target);
        BlockEntity tbe = level.getBlockEntity(target);
        Status next;
        if (tbe == null || !ts.is(MachineRegistry.CATALYSABLE)) {
            charged = false;
            next = Status.NO_MACHINE;
        } else {
            if (level.getGameTime() % 20 == 0 || status == Status.NO_MACHINE) {
                double rpm = 0;
                for (Direction d : Direction.values()) {
                    if (d != front) rpm = Math.max(rpm, CatalystHooks.rotation.rpm(level, worldPosition.relative(d)));
                }
                boolean turning = rpm >= ServerConfig.catalystMinRpm();
                int src = ServerConfig.catalystSourcePerSecond();
                boolean paid = turning && (src <= 0 || CatalystHooks.source.take(level, worldPosition, ServerConfig.catalystSourceRange(), src));
                charged = CatalystRules.charged(paid, rpm, ServerConfig.catalystMinRpm());
                status = !turning ? Status.NO_ROTATION : !paid ? Status.NO_SOURCE : Status.QUICKENING;
            }
            next = charged ? quicken(level, target, tbe, front.getOpposite()) : status;
        }
        if (next != status) {
            status = next;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        BlockState s = getBlockState();
        boolean lit = status == Status.QUICKENING;
        if (s.getValue(ArcaneCatalystBlock.LIT) != lit) level.setBlock(worldPosition, s.setValue(ArcaneCatalystBlock.LIT, lit), 3);
    }

    private Status quicken(ServerLevel level, BlockPos target, BlockEntity tbe, Direction side) {
        long[] t = CatalystRules.extraTicks(accumulated, ServerConfig.catalystExtraTicks());
        accumulated = t[1] / 1_000_000.0;
        long estimate = lastSurcharge >= 0 ? lastSurcharge : ServerConfig.catalystFlatFe();
        if (t[0] <= 0) return energy < estimate ? Status.NO_POWER : Status.QUICKENING;
        for (long i = 0; i < t[0]; i++) {
            estimate = lastSurcharge >= 0 ? lastSurcharge : ServerConfig.catalystFlatFe();
            if (energy < estimate) return Status.NO_POWER;
            long before = stored(level, target, side);
            if (!tickOnce(level, target, tbe)) return Status.NO_MACHINE;
            long after = stored(level, target, side);
            long use = before >= 0 && after >= 0 ? Math.max(0, before - after) : -1;
            long pay = Math.min(energy, CatalystRules.surcharge(use, ServerConfig.catalystFeSurcharge(), ServerConfig.catalystFlatFe()));
            energy -= (int) pay;
            fePaid += pay;
            lastSurcharge = pay;
            extraTicksGiven++;
        }
        setChanged();
        return Status.QUICKENING;
    }

    /** The machine's stored FE through the face that touches the catalyst, else its null side; -1 when unreadable. */
    private static long stored(Level level, BlockPos pos, Direction side) {
        IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        if (e == null) e = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        return e == null ? -1 : e.getEnergyStored();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean tickOnce(Level level, BlockPos pos, BlockEntity be) {
        if (CatalystHooks.multiblock.tick(level, be)) return true;
        BlockState state = level.getBlockState(pos);
        BlockEntityTicker ticker = state.getTicker(level, be.getType());
        if (ticker == null) return false;
        ticker.tick(level, pos, state, be);
        return true;
    }

    // ---------------------------------------------------------------- energy

    private final class Energy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int n = Math.max(0, Math.min(CAPACITY - energy, toReceive));
            if (!simulate && n > 0) {
                energy += n;
                setChanged();
            }
            return n;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    // ---------------------------------------------------------------- save and sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy);
        tag.putInt("status", status.ordinal());
        tag.putLong("extra_ticks", extraTicksGiven);
        tag.putLong("fe_paid", fePaid);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = tag.getInt("energy");
        status = Status.byOrdinal(tag.getInt("status"));
        extraTicksGiven = tag.getLong("extra_ticks");
        fePaid = tag.getLong("fe_paid");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
