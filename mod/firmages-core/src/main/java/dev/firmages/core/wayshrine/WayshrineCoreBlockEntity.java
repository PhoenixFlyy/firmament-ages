package dev.firmages.core.wayshrine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * State of a Wayshrine core (SPEC-himmel §28.5): its {@code type}, the offering laid on it and the prayer progress
 * (saved), plus the synced view the renderer and Jade draw from: the number of missing blocks, the beam colour and width,
 * and the blessing radius. The server logic is in {@link WayshrineService}.
 */
public final class WayshrineCoreBlockEntity extends BlockEntity {
    String type = "";
    ItemStack offering = ItemStack.EMPTY;
    int prayerProgress;
    /** Player -> game time of the last prayer use (transient). */
    final Map<UUID, Long> prayers = new HashMap<>();
    /** Player -> game time of the last actionbar line (spam guard while use is held). */
    final Map<UUID, Long> lastMessage = new HashMap<>();
    /** The rotation the restored form stands in, null while it does not stand (validation cache). */
    Rotation rotation;
    boolean validationDue = true;
    long nextValidation;
    // synced view
    int missing = -1;
    int beamColor;
    float beamWidth;
    int radius;

    public WayshrineCoreBlockEntity(BlockPos pos, BlockState state) {
        super(WayshrineRegistry.CORE_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WayshrineCoreBlockEntity be) {
        WayshrineService.tick((ServerLevel) level, pos, state, be);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) WayshrineService.onCoreLoaded(sl, worldPosition, this);
    }

    public String type() {
        return type;
    }

    public ItemStack offering() {
        return offering;
    }

    public int prayerProgress() {
        return prayerProgress;
    }

    /** Blocks of the restored form that do not stand (synced; -1 before the first validation). */
    public int missing() {
        return missing;
    }

    /** ARGB beam colour, 0 = no beam (synced). */
    public int beamColor() {
        return beamColor;
    }

    public float beamWidth() {
        return beamWidth;
    }

    /** The blessing radius while lit (synced for Jade). */
    public int radius() {
        return radius;
    }

    /** Marks the cached validation stale; the next server tick validates again. */
    public void invalidate() {
        validationDue = true;
    }

    /** Persists and sends the synced view to the watching clients. */
    void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("type", type);
        if (!offering.isEmpty()) tag.put("offering", offering.save(registries));
        tag.putInt("prayerProgress", prayerProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        type = tag.getString("type");
        offering = tag.contains("offering") ? ItemStack.parseOptional(registries, tag.getCompound("offering")) : ItemStack.EMPTY;
        prayerProgress = tag.getInt("prayerProgress");
        if (tag.contains("missing")) missing = tag.getInt("missing");
        if (tag.contains("beam")) beamColor = tag.getInt("beam");
        if (tag.contains("beamWidth")) beamWidth = tag.getFloat("beamWidth");
        if (tag.contains("radius")) radius = tag.getInt("radius");
        validationDue = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type);
        if (!offering.isEmpty()) tag.put("offering", offering.save(registries));
        tag.putInt("missing", missing);
        tag.putInt("beam", beamColor);
        tag.putFloat("beamWidth", beamWidth);
        tag.putInt("radius", radius);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
