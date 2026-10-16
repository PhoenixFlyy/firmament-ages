package dev.firmages.core.launch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A launch port's link to its control (SPEC §31.6), set by the control's complex check. Capabilities ask the linked
 * control; an unlinked port offers none, and every link change invalidates the capability caches around it.
 */
public final class LaunchPortBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos control;

    public LaunchPortBlockEntity(BlockPos pos, BlockState state) {
        super(LaunchRegistry.LAUNCH_PORT_BE.get(), pos, state);
    }

    @Nullable
    public BlockPos controlPos() {
        return control;
    }

    /** The control this port feeds, when it is loaded and still counts this port (on a client: the synced link). */
    public Optional<LaunchControlBlockEntity> control() {
        if (control == null || level == null || !level.isLoaded(control)) return Optional.empty();
        if (level.getBlockEntity(control) instanceof LaunchControlBlockEntity c && (level.isClientSide || c.feeds(worldPosition))) return Optional.of(c);
        return Optional.empty();
    }

    void link(BlockPos to) {
        if (to.equals(control)) return;
        control = to.immutable();
        changed();
    }

    void unlink(BlockPos from) {
        if (control == null || !control.equals(from)) return;
        control = null;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (control != null) tag.putLong("control", control.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        control = tag.contains("control") ? BlockPos.of(tag.getLong("control")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
