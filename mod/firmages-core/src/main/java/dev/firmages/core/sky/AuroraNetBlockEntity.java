package dev.firmages.core.sky;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The Aurora Net's tank and catch (SPEC §26.4). Every 20 ticks it checks height, open sky and {@link AuroraRules} at its
 * column and adds {@code mb_per_hour * intensity / 50} mB, divided by 1 + the other nets within {@code share_radius},
 * to its tank (fractions carry over). The tank is drained from any side and never filled from outside. The reason
 * ({@link Reason}) rides on block-entity sync for Jade.
 */
public final class AuroraNetBlockEntity extends BlockEntity {
    /** Why the net catches or not; the Jade line. */
    public enum Reason {
        CATCHING, NO_AURORA, TOO_LOW, NO_SKY;

        static Reason byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : NO_AURORA;
        }
    }

    private final FluidTank tank = new FluidTank(SkyHarvestData.DEFAULT.aurora().netCapacity(),
        f -> f.getFluid() == HarvestRegistry.AURORA_LIGHT.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler drainOnly = new DrainOnly();
    private double carry;
    private Reason reason = Reason.NO_AURORA;
    private int minY = SkyHarvestData.DEFAULT.aurora().minY();
    /** Steps that added light since load (the GameTest reads it). */
    private long catches;

    public AuroraNetBlockEntity(BlockPos pos, BlockState state) {
        super(HarvestRegistry.AURORA_NET_BE.get(), pos, state);
    }

    public IFluidHandler drainOnly() {
        return drainOnly;
    }

    public FluidStack fluid() {
        return tank.getFluid();
    }

    public Reason reason() {
        return reason;
    }

    /** The {@code min_y} the reason refers to (synced for the "Too low" line). */
    public int minY() {
        return minY;
    }

    public long catches() {
        return catches;
    }

    void serverTick(ServerLevel level) {
        if ((level.getGameTime() + worldPosition.asLong()) % 20 != 0) return;
        step(level);
    }

    /** One 20-tick step; returns the mB added. Public for the GameTests. */
    public int step(ServerLevel level) {
        SkyHarvestData.Aurora a = SkyHarvestDataLoader.current().aurora();
        tank.setCapacity(a.netCapacity());
        Reason r;
        double add = 0;
        if (worldPosition.getY() < a.minY()) {
            r = Reason.TOO_LOW;
        } else if (!level.canSeeSky(worldPosition.above())) {
            r = Reason.NO_SKY;
        } else {
            AuroraRules.Result res = AuroraService.at(level, worldPosition.getX(), worldPosition.getZ());
            if (ServerConfig.skyHarvestEnabled() && res.active()) {
                r = Reason.CATCHING;
                add = AuroraRules.fillPerStep(a, res.intensity(), otherNets(level, a.shareRadius()), res.coldHalf());
            } else {
                r = Reason.NO_AURORA;
            }
        }
        int filled = 0;
        if (add > 0) {
            carry += add;
            int whole = (int) carry;
            if (whole > 0) {
                filled = tank.fill(new FluidStack(HarvestRegistry.AURORA_LIGHT.get(), whole), IFluidHandler.FluidAction.EXECUTE);
                carry = filled < whole ? 0 : carry - whole;
            }
            catches++;
        }
        setReason(level, r, a.minY());
        return filled;
    }

    private int otherNets(ServerLevel level, int radius) {
        if (radius <= 0) return 0;
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-radius, -radius, -radius), worldPosition.offset(radius, radius, radius))) {
            if (!p.equals(worldPosition) && level.getBlockState(p).is(HarvestRegistry.AURORA_NET.get())) n++;
        }
        return n;
    }

    private void setReason(ServerLevel level, Reason r, int minY) {
        boolean catching = r == Reason.CATCHING;
        BlockState s = getBlockState();
        if (s.hasProperty(AuroraNetBlock.CATCHING) && s.getValue(AuroraNetBlock.CATCHING) != catching) {
            level.setBlock(worldPosition, s.setValue(AuroraNetBlock.CATCHING, catching), Block.UPDATE_ALL);
            if (catching) level.playSound(null, worldPosition, HarvestRegistry.AURORA_NET_CATCH.get(), SoundSource.BLOCKS, 0.6F, 1.0F);
        }
        if (r != reason || minY != this.minY) {
            reason = r;
            this.minY = minY;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("carry", carry);
        tag.putInt("reason", reason.ordinal());
        tag.putInt("minY", minY);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("tank"));
        carry = tag.getDouble("carry");
        reason = Reason.byOrdinal(tag.getInt("reason"));
        if (tag.contains("minY")) minY = tag.getInt("minY");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = super.getUpdateTag(registries);
        t.putInt("reason", reason.ordinal());
        t.putInt("minY", minY);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Pipes and buckets take aurora light out; nothing goes in (SPEC §26.4). */
    private final class DrainOnly implements IFluidHandler {
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
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return tank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return tank.drain(maxDrain, action);
        }
    }
}
