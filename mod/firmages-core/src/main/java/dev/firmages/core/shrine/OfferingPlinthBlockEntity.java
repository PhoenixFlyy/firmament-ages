package dev.firmages.core.shrine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One offering plinth: holds exactly one item. While the prayer is not complete the item is an offering and can be
 * taken back (sneak-use); once its tier awakened it is a relic, locked forever ({@code /firmages shrine extract}
 * is the only way out). Synced to clients for the renderer.
 */
public final class OfferingPlinthBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;
    private boolean relic;
    private int tier = -1;

    public OfferingPlinthBlockEntity(BlockPos pos, BlockState state) {
        super(ShrineRegistry.OFFERING_PLINTH_BE.get(), pos, state);
    }

    public ItemStack item() {
        return item;
    }

    public boolean isEmpty() {
        return item.isEmpty();
    }

    /** True once the item is enshrined as a relic (its tier awakened). */
    public boolean isRelic() {
        return relic;
    }

    /** Tier the item was offered for, or -1. */
    public int tier() {
        return tier;
    }

    /** Puts one offering (a copy of count 1) on the plinth. */
    public void offer(ItemStack stack, int forTier) {
        item = stack.copyWithCount(1);
        relic = false;
        tier = forTier;
        changed();
    }

    /** Locks the offering as a relic. */
    public void enshrine(int forTier) {
        relic = true;
        tier = forTier;
        changed();
    }

    /** Removes and returns the item (offering take-back or operator extraction). */
    public ItemStack take() {
        ItemStack out = item;
        item = ItemStack.EMPTY;
        relic = false;
        tier = -1;
        changed();
        return out;
    }

    /** Returns the item while the block is being removed; touches no world state. */
    public ItemStack drainForRemoval() {
        ItemStack out = item;
        item = ItemStack.EMPTY;
        relic = false;
        return out;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState s = getBlockState();
            if (s.hasProperty(OfferingPlinthBlock.AWAKENED) && s.getValue(OfferingPlinthBlock.AWAKENED) != relic) {
                level.setBlock(worldPosition, s.setValue(OfferingPlinthBlock.AWAKENED, relic), 3);
            } else {
                level.sendBlockUpdated(worldPosition, s, s, 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!item.isEmpty()) tag.put("item", item.save(registries));
        tag.putBoolean("relic", relic);
        tag.putInt("tier", tier);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        item = tag.contains("item") ? ItemStack.parseOptional(registries, tag.getCompound("item")) : ItemStack.EMPTY;
        relic = tag.getBoolean("relic") && !item.isEmpty();
        tier = tag.contains("tier") ? tag.getInt("tier") : -1;
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
