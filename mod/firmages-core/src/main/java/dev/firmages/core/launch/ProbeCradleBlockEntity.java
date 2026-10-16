package dev.firmages.core.launch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The cradle's render state (SPEC §31.7): the phase of its control, the game time that phase began, the probe and the
 * side the flame trench opens to. Set by the control on the server and carried to clients by block-entity sync, so a
 * player who arrives late sees the right moment; {@code firmages:launch_fx} carries the same to players farther away.
 * Not saved: the control sets it again after a load.
 */
public final class ProbeCradleBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos control;
    private LaunchRules.Phase phase = LaunchRules.Phase.IDLE;
    private long phaseStart;
    private ItemStack probe = ItemStack.EMPTY;
    @Nullable
    private Direction trench;

    public ProbeCradleBlockEntity(BlockPos pos, BlockState state) {
        super(LaunchRegistry.PROBE_CRADLE_BE.get(), pos, state);
    }

    @Nullable
    public BlockPos control() {
        return control;
    }

    public LaunchRules.Phase phase() {
        return phase;
    }

    public long phaseStart() {
        return phaseStart;
    }

    public ItemStack probe() {
        return probe;
    }

    @Nullable
    public Direction trench() {
        return trench;
    }

    /** Server: the control's new state (synced to the clients that track the chunk). */
    public void setFx(@Nullable BlockPos control, LaunchRules.Phase phase, long phaseStart, ItemStack probe, @Nullable Direction trench) {
        this.control = control == null ? null : control.immutable();
        this.phase = phase;
        this.phaseStart = phaseStart;
        this.probe = probe.copy();
        this.trench = trench;
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (control != null) tag.putLong("control", control.asLong());
        tag.putInt("phase", phase.ordinal());
        tag.putLong("start", phaseStart);
        if (!probe.isEmpty()) tag.put("probe", probe.save(registries));
        tag.putInt("trench", trench == null ? -1 : trench.get3DDataValue());
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains("phase")) return;
        control = tag.contains("control") ? BlockPos.of(tag.getLong("control")) : null;
        phase = LaunchRules.Phase.byOrdinal(tag.getInt("phase"));
        phaseStart = tag.getLong("start");
        probe = tag.contains("probe") ? ItemStack.parseOptional(registries, tag.getCompound("probe")) : ItemStack.EMPTY;
        int t = tag.getInt("trench");
        trench = t >= 0 && t < 6 ? Direction.from3DDataValue(t) : null;
        if (level != null && level.isClientSide) dev.firmages.core.net.LaunchFxPayload.clientUpdate(worldPosition, phase, phaseStart, probe, trench);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
