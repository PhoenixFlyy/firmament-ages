package dev.firmages.core.proof;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Tally Chute's state (SPEC §22.1): its owner (the placer; the team is resolved at each count, so a player who
 * joins a party later counts for the party), how many items passed this chute, and for Jade the last item and the
 * team's total of it. A chute that feeds another Tally Chute does not count, so a stack of them counts once.
 */
public class TallyChuteBlockEntity extends BlockEntity {
    /** Ticks between two client updates while items pass. */
    private static final int SYNC_TICKS = 10;

    private final Inlet inlet = new Inlet();
    @Nullable
    private UUID owner;
    private long passed;
    private String lastItem = "";
    private long lastTeamCount;
    private boolean syncPending;
    private long lastSync;
    @Nullable
    private BlockCapabilityCache<IItemHandler, Direction> outlet;

    public TallyChuteBlockEntity(BlockPos pos, BlockState state) {
        super(ProofRegistry.TALLY_CHUTE_BE.get(), pos, state);
    }

    public IItemHandler inlet() {
        return inlet;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public long passed() {
        return passed;
    }

    /** Item id of the last counted stack, empty before the first. */
    public String lastItem() {
        return lastItem;
    }

    /** The owner's team total of {@link #lastItem} (all Ages) when it was last counted. */
    public long lastTeamCount() {
        return lastTeamCount;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
        sync();
    }

    @Nullable
    private IItemHandler target() {
        if (!(level instanceof ServerLevel sl)) return null;
        if (outlet == null) outlet = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, sl, worldPosition.below(), Direction.UP);
        return outlet.getCapability();
    }

    private void counted(ItemStack stack, int n) {
        passed += n;
        if (owner != null && level instanceof ServerLevel sl && !(level.getBlockEntity(worldPosition.below()) instanceof TallyChuteBlockEntity)) {
            lastItem = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            lastTeamCount = TallyService.count(sl.getServer(), owner, lastItem, n);
        }
        setChanged();
        syncPending = true;
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, TallyChuteBlockEntity be) {
        if (be.syncPending && level.getGameTime() - be.lastSync >= SYNC_TICKS) be.sync();
    }

    private void sync() {
        syncPending = false;
        if (level == null || level.isClientSide) return;
        lastSync = level.getGameTime();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        passed = tag.getLong("passed");
        lastItem = tag.getString("last_item");
        lastTeamCount = tag.getLong("last_team_count");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putLong("passed", passed);
        tag.putString("last_item", lastItem);
        tag.putLong("last_team_count", lastTeamCount);
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

    /**
     * The chute's item handler: one always-empty slot that hands every insert straight on to the inventory below
     * (its top face). What the outlet refuses is refused here, so hoppers, funnels and pipes keep their items.
     */
    private final class Inlet implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            IItemHandler out = target();
            if (out == null) return stack;
            ItemStack rest = ItemHandlerHelper.insertItem(out, stack.copy(), simulate);
            int moved = stack.getCount() - rest.getCount();
            if (!simulate && moved > 0) counted(stack, moved);
            return rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
