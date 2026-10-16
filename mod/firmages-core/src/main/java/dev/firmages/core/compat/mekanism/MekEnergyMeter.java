package dev.firmages.core.compat.mekanism;

import dev.firmages.core.balance.EnergyMeter;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IEnergyConversionHelper;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Mekanism's own energy link (strict energy, in Joules) for the FE cap and the energy audit (SPEC §37, §38): Mekanism
 * cables, generators and machines talk to each other through {@code mekanism:strict_energy}, not through the FE
 * capability, so the meter wraps that capability the same way ({@link EnergyMeter}). Amounts are converted with
 * Mekanism's FE conversion (Mekanism 10.7.19 [verified, javap]: {@code IEnergyConversionHelper.feConversion}).
 * Loaded only with Mekanism.
 */
public final class MekEnergyMeter {
    private MekEnergyMeter() {}

    public static void register(RegisterCapabilitiesEvent event) {
        BlockCapability<IStrictEnergyHandler, Direction> cap = Capabilities.STRICT_ENERGY.block();
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            event.registerBlockEntity(cap, type, MekEnergyMeter::provide);
        }
    }

    @Nullable
    private static IStrictEnergyHandler provide(BlockEntity be, @Nullable Direction side) {
        EnergyMeter.Watch w = EnergyMeter.watch(be, side);
        if (w == null) return null;
        BlockCapability<IStrictEnergyHandler, Direction> cap = Capabilities.STRICT_ENERGY.block();
        IStrictEnergyHandler own = EnergyMeter.unwrapped(() -> w.level().getCapability(cap, w.pos(), be.getBlockState(), be, side));
        return own == null ? null : new Metered(own, w);
    }

    static IEnergyConversion fe() {
        return IEnergyConversionHelper.INSTANCE.feConversion();
    }

    /** Joules allowed by an FE room (Long.MAX_VALUE stays unlimited). */
    static long joules(long feRoom) {
        return feRoom == Long.MAX_VALUE ? Long.MAX_VALUE : fe().convertFrom(feRoom);
    }

    static long feOf(long joules) {
        return fe().convertTo(joules);
    }

    private static final class Metered implements IStrictEnergyHandler {
        private final IStrictEnergyHandler own;
        private final EnergyMeter.Watch w;

        Metered(IStrictEnergyHandler own, EnergyMeter.Watch w) {
            this.own = own;
            this.w = w;
        }

        @Override
        public int getEnergyContainerCount() {
            return own.getEnergyContainerCount();
        }

        @Override
        public long getEnergy(int container) {
            return own.getEnergy(container);
        }

        @Override
        public void setEnergy(int container, long energy) {
            own.setEnergy(container, energy);
        }

        @Override
        public long getMaxEnergy(int container) {
            return own.getMaxEnergy(container);
        }

        @Override
        public long getNeededEnergy(int container) {
            return own.getNeededEnergy(container);
        }

        /** Insert returns the remainder (what was not accepted). */
        @Override
        public long insertEnergy(int container, long amount, Action action) {
            long allowed = Math.min(amount, joules(w.sourceRoom()));
            long rest = own.insertEnergy(container, allowed, action);
            long took = allowed - rest;
            if (action.execute()) w.took(feOf(took));
            return amount - took;
        }

        @Override
        public long insertEnergy(long amount, Action action) {
            long allowed = Math.min(amount, joules(w.sourceRoom()));
            long rest = own.insertEnergy(allowed, action);
            long took = allowed - rest;
            if (action.execute()) w.took(feOf(took));
            return amount - took;
        }

        /** Extract returns what was taken. */
        @Override
        public long extractEnergy(int container, long amount, Action action) {
            long got = own.extractEnergy(container, Math.min(amount, joules(w.ownRoom())), action);
            if (action.execute()) w.gave(feOf(got));
            return got;
        }

        @Override
        public long extractEnergy(long amount, Action action) {
            long got = own.extractEnergy(Math.min(amount, joules(w.ownRoom())), action);
            if (action.execute()) w.gave(feOf(got));
            return got;
        }
    }
}
