package dev.firmages.core.anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * One Sky Anchor: the shards it holds (bit mask, saved) and the synced view the renderer and Jade draw from (status,
 * the tiers in force, their blessings' name keys, the beam colour and the radius), refreshed once a second on the server by
 * {@link AnchorService#refresh}. The shards are also mirrored into {@link AnchorSavedData}, which the blessing index
 * reads in every dimension, loaded or not.
 */
public final class SkyAnchorBlockEntity extends BlockEntity {
    int tiers;
    // synced view
    AnchorRules.Status status = AnchorRules.Status.BEFORE_AGE;
    int activeMask;
    List<String> blessings = List.of();
    int beamColor;
    int radius;

    public SkyAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(AnchorRegistry.SKY_ANCHOR_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SkyAnchorBlockEntity be) {
        if (level.getGameTime() % 20L == Math.floorMod(pos.asLong(), 20L)) AnchorService.refresh((ServerLevel) level, pos, be);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) AnchorService.onLoaded(sl, worldPosition, this);
    }

    /** The shards held (bit k = tier k). */
    public int tiers() {
        return tiers;
    }

    public AnchorRules.Status status() {
        return status;
    }

    /** The tiers in force now (synced). */
    public int activeMask() {
        return activeMask;
    }

    /** The name keys of the blessings in force now, in tier order (synced). */
    public List<String> blessings() {
        return blessings;
    }

    /** ARGB beam colour, 0 = no beam (synced). */
    public int beamColor() {
        return beamColor;
    }

    /** The blessing radius (synced). */
    public int radius() {
        return radius;
    }

    /** Sets the shards; {@link AnchorService} updates the SavedData and the index. */
    void setTiers(int t) {
        tiers = t;
        changed();
    }

    void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("tiers", tiers);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tiers = tag.getInt("tiers");
        if (tag.contains("status")) {
            AnchorRules.Status[] all = AnchorRules.Status.values();
            status = all[Math.max(0, Math.min(all.length - 1, tag.getInt("status")))];
        }
        if (tag.contains("active")) activeMask = tag.getInt("active");
        if (tag.contains("blessings")) {
            List<String> b = new ArrayList<>();
            ListTag l = tag.getList("blessings", Tag.TAG_STRING);
            for (int i = 0; i < l.size(); i++) b.add(l.getString(i));
            blessings = List.copyOf(b);
        }
        if (tag.contains("beam")) beamColor = tag.getInt("beam");
        if (tag.contains("radius")) radius = tag.getInt("radius");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tiers", tiers);
        tag.putInt("status", status.ordinal());
        tag.putInt("active", activeMask);
        ListTag l = new ListTag();
        blessings.forEach(b -> l.add(StringTag.valueOf(b)));
        tag.put("blessings", l);
        tag.putInt("beam", beamColor);
        tag.putInt("radius", radius);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
