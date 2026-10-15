package dev.firmages.core.crown;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One lens mount: its lens, whether it is powered, and the swing (0 = out .. {@code crown.swingTicks} = fully in).
 * The swing runs on both sides (the client animates it); the server sets the blockstate {@code in} when the lens is
 * fully in or leaves that position. Synced to clients by vanilla block-entity sync.
 */
public final class LensMountBlockEntity extends BlockEntity {
    private ItemStack lens = ItemStack.EMPTY;
    private boolean powered;
    private int swing;
    private int max = ServerConfig.crownSwingTicks();
    /** Debug override of the redstone line ({@code /firmages crown swing}); not saved. */
    @Nullable
    private Boolean forced;

    public LensMountBlockEntity(BlockPos pos, BlockState state) {
        super(CrownRegistry.LENS_MOUNT_BE.get(), pos, state);
    }

    public ItemStack lens() {
        return lens;
    }

    /** The lens index 0..8, or -1 when empty. */
    public int lensIndex() {
        return lens.getItem() instanceof LensItem l ? l.index() : -1;
    }

    /** The lens swings in now (redstone, or the debug override). */
    public boolean target() {
        return forced != null ? forced : powered;
    }

    public int swing() {
        return swing;
    }

    public int swingMax() {
        return max;
    }

    /** A lens is in the chord only once it is fully in. */
    public boolean fullyIn() {
        return lensIndex() >= 0 && CrownRules.fullyIn(swing, max);
    }

    public void setLens(ItemStack stack) {
        lens = stack.copyWithCount(1);
        changed(true);
    }

    public ItemStack takeLens() {
        ItemStack out = lens;
        lens = ItemStack.EMPTY;
        changed(true);
        return out;
    }

    /** Returns the lens while the block is being removed; touches no world state. */
    public ItemStack drainForRemoval() {
        ItemStack out = lens;
        lens = ItemStack.EMPTY;
        return out;
    }

    public void setPowered(boolean p) {
        if (p == powered) return;
        powered = p;
        changed(false);
    }

    public void setForced(@Nullable Boolean f) {
        forced = f;
        changed(false);
    }

    void tick() {
        if (level == null) return;
        if (!level.isClientSide) max = ServerConfig.crownSwingTicks();
        boolean wasIn = CrownRules.fullyIn(swing, max);
        int next = CrownRules.swing(swing, target(), max);
        if (next == swing) return;
        swing = next;
        if (level.isClientSide) return;
        setChanged();
        boolean in = CrownRules.fullyIn(swing, max);
        if (in != wasIn) updateState();
    }

    private void changed(boolean lensChanged) {
        setChanged();
        if (level == null || level.isClientSide) return;
        if (lensChanged) updateState();
        else level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void updateState() {
        BlockState s = getBlockState();
        if (!s.hasProperty(LensMountBlock.LENS)) return;
        int idx = lensIndex();
        BlockState n = s.setValue(LensMountBlock.LENS, idx < 0 ? LensMountBlock.EMPTY : idx).setValue(LensMountBlock.IN, CrownRules.fullyIn(swing, max));
        if (n != s) level.setBlock(worldPosition, n, 3);
        else level.sendBlockUpdated(worldPosition, s, s, 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!lens.isEmpty()) tag.put("lens", lens.save(registries));
        tag.putBoolean("powered", powered);
        tag.putInt("swing", swing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lens = tag.contains("lens") ? ItemStack.parseOptional(registries, tag.getCompound("lens")) : ItemStack.EMPTY;
        powered = tag.getBoolean("powered");
        swing = tag.getInt("swing");
        if (tag.contains("max")) max = Math.max(1, tag.getInt("max"));
        forced = tag.contains("forced") ? tag.getBoolean("forced") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putInt("max", max);
        if (forced != null) tag.putBoolean("forced", forced);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
