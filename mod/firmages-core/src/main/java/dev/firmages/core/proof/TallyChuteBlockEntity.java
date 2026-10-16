package dev.firmages.core.proof;

import dev.firmages.core.shrine.ShrineProof;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The Tally Chute's state (SPEC §22.1): its owner (the placer; the team is resolved at each count, so a player who
 * joins a party later counts for the party), how many items it took, and for Jade the last item and the team's total
 * of it. The chute is a tithe: it takes only what the shrine's current proof of work still wants from the owner's team
 * ({@link ShrineProof#room}) and keeps nothing, so every counted item is consumed and nothing can be counted twice
 * (review fix 0.12.0: a pass-through chute counted a stack sent around a loop again on every lap).
 */
public class TallyChuteBlockEntity extends BlockEntity {
    /** Ticks between two client updates while items pass. */
    private static final int SYNC_TICKS = 10;
    /** Ticks between two looks at what the current proof still wants (for Jade). */
    private static final int WANT_TICKS = 40;

    private final Inlet inlet = new Inlet();
    @Nullable
    private UUID owner;
    private long passed;
    private String lastItem = "";
    private long lastTeamCount;
    private long want;
    private String wantItem = "";
    private boolean syncPending;
    private long lastSync;

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

    /** Items this chute took (and consumed). */
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

    /** How many more the current proof wants from the owner's team (0: none now), as of the last look. */
    public long want() {
        return want;
    }

    /** Item id the current proof names, empty when it wants nothing. */
    public String wantItem() {
        return wantItem;
    }

    private void lookAtProof(ServerLevel sl) {
        long w = 0;
        String item = "";
        if (owner != null) {
            Optional<ShrineProof.Progress> p = ShrineProof.current(sl.getServer(), owner);
            if (p.isPresent() && !p.get().met()) {
                w = p.get().req().count() - p.get().have();
                item = BuiltInRegistries.ITEM.getKey(p.get().shown()).toString();
            }
        }
        if (w != want || !item.equals(wantItem)) {
            want = w;
            wantItem = item;
            syncPending = true;
        }
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
        sync();
    }

    /** How many of {@code stack} the chute takes now: what the owner's team still owes the current proof, at most the stack. */
    int room(ItemStack stack) {
        if (stack.isEmpty() || owner == null || !(level instanceof ServerLevel sl)) return 0;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return (int) Math.min(stack.getCount(), ShrineProof.room(sl.getServer(), owner, id));
    }

    /** A hand feeds the chute: takes and consumes up to {@link #room} of {@code stack} (shrinks it); returns how many. */
    public int take(ItemStack stack) {
        int n = room(stack);
        if (n <= 0) return 0;
        counted(stack, n);
        stack.shrink(n);
        return n;
    }

    private void counted(ItemStack stack, int n) {
        passed += n;
        lastItem = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (owner != null && level instanceof ServerLevel sl) {
            lastTeamCount = TallyService.count(sl.getServer(), owner, lastItem, n);
            lookAtProof(sl);
        }
        setChanged();
        syncPending = true;
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, TallyChuteBlockEntity be) {
        if (level instanceof ServerLevel sl && (level.getGameTime() + pos.asLong()) % WANT_TICKS == 0) be.lookAtProof(sl);
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
        want = tag.getLong("want");
        wantItem = tag.getString("want_item");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putLong("passed", passed);
        tag.putString("last_item", lastItem);
        tag.putLong("last_team_count", lastTeamCount);
        tag.putLong("want", want);
        tag.putString("want_item", wantItem);
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
     * The chute's item handler: one always-empty slot that takes what the current proof wants and consumes it. What it
     * does not want (another item, a met proof, no proof in this Age, a chute nobody placed) is refused, so hoppers,
     * funnels and pipes keep their items.
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
            int n = room(stack);
            if (n <= 0) return stack;
            if (!simulate) counted(stack, n);
            return n >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - n);
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
            return room(stack) > 0;
        }
    }
}
