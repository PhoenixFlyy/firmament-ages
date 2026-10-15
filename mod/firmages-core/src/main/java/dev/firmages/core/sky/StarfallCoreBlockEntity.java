package dev.firmages.core.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The core's block entity (SPEC §25.4): {@code coolsAt} (game time) and the site id. The server checks every 100
 * ticks and turns {@code hot} off once the game time passes {@code coolsAt}. {@code coolsAt} rides on the vanilla
 * block-entity sync, so the Jade line counts down on the client.
 */
public final class StarfallCoreBlockEntity extends BlockEntity {
    public static final int CHECK_TICKS = 100;
    private long coolsAt;
    private long siteId;

    public StarfallCoreBlockEntity(BlockPos pos, BlockState state) {
        super(SkyRegistry.STARFALL_CORE_BE.get(), pos, state);
    }

    public long coolsAt() {
        return coolsAt;
    }

    public long siteId() {
        return siteId;
    }

    public void setCooling(long siteId, long coolsAt) {
        this.siteId = siteId;
        this.coolsAt = coolsAt;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, StarfallCoreBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % CHECK_TICKS != 0) return;
        be.check();
    }

    /** Turns {@code hot} off once cold (and on again when an operator heated the site). */
    public void check() {
        if (level == null) return;
        BlockState s = getBlockState();
        boolean hot = !StarfallRules.cold(level.getGameTime(), coolsAt);
        if (s.getValue(StarfallCoreBlock.HOT) != hot) level.setBlock(worldPosition, s.setValue(StarfallCoreBlock.HOT, hot), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("coolsAt", coolsAt);
        tag.putLong("site", siteId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        coolsAt = tag.getLong("coolsAt");
        siteId = tag.getLong("site");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = super.getUpdateTag(registries);
        t.putLong("coolsAt", coolsAt);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
