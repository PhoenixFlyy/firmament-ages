package dev.firmages.core.sky;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The Storm Rod's basin (SPEC §26.5): 16 input slots for {@code storm.input} ({@code #c:sands}) and 16 output slots.
 * Every {@link StormRules#ROLL_TICKS} ticks it rolls for a strike while the overworld rains and the sky above the tip is
 * open; a strike spawns a visual-only bolt at the tip (no fire, no damage, no charged creeper, no vanilla rod pulse)
 * and fuses {@code sand_per_strike} sand into storm glass. No energy, no redstone count, no forecast.
 */
public final class StormRodBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUTS = 16;
    public static final int OUTPUTS = 16;

    /** Why the rod is struck or not; the Jade line. */
    public enum Reason {
        STORM, WAITING, NO_SKY, NO_SAND, FULL;

        static Reason byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : WAITING;
        }
    }

    private final ItemStackHandler items = new ItemStackHandler(INPUTS + OUTPUTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= INPUTS || isInput(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler inputHandler = new Part(true);
    private final IItemHandler outputHandler = new Part(false);
    private Reason reason = Reason.WAITING;
    private long strikes;

    public StormRodBlockEntity(BlockPos pos, BlockState state) {
        super(HarvestRegistry.STORM_ROD_BE.get(), pos, state);
    }

    /** Whether the rod takes {@code stack} ({@code storm.input}: a {@code #tag} or an item id). */
    public static boolean isInput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String in = SkyHarvestDataLoader.current().storm().input();
        if (in.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(in.substring(1));
            return id != null && stack.is(TagKey.create(Registries.ITEM, id));
        }
        ResourceLocation id = ResourceLocation.tryParse(in);
        return id != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    /** The fused result ({@code storm.result}); storm glass unless the data says otherwise. */
    public static Item result() {
        ResourceLocation id = ResourceLocation.tryParse(SkyHarvestDataLoader.current().storm().result());
        Item i = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        return i == Items.AIR ? HarvestRegistry.STORM_GLASS_ITEM.get() : i;
    }

    public IItemHandler inputHandler() {
        return inputHandler;
    }

    public IItemHandler outputHandler() {
        return outputHandler;
    }

    /** The raw 32 slots (the menu). */
    public ItemStackHandler items() {
        return items;
    }

    public Reason reason() {
        return reason;
    }

    public long strikes() {
        return strikes;
    }

    /** The rod's tip (the upper half); the bolt lands on top of it. */
    public BlockPos tip() {
        return worldPosition.above();
    }

    public boolean skyOpen(ServerLevel level) {
        return level.canSeeSky(tip().above());
    }

    public int sand() {
        int n = 0;
        for (int i = 0; i < INPUTS; i++) n += items.getStackInSlot(i).getCount();
        return n;
    }

    /** How many results the output still takes. */
    public int outputRoom() {
        ItemStack probe = new ItemStack(result());
        int room = 0;
        for (int i = INPUTS; i < INPUTS + OUTPUTS; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty()) room += probe.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(s, probe)) room += s.getMaxStackSize() - s.getCount();
        }
        return room;
    }

    void serverTick(ServerLevel level) {
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), (long) StormRules.ROLL_TICKS) != 0) return;
        boolean sky = skyOpen(level);
        Reason r = reasonNow(level.isRaining(), sky);
        setReason(level, r);
        if (r == Reason.STORM && ServerConfig.skyHarvestEnabled()
            && roll(level.getRandom(), level.isRaining(), level.isThundering(), sky)) {
            strike(level);
        }
    }

    Reason reasonNow(boolean raining, boolean sky) {
        if (!sky) return Reason.NO_SKY;
        if (!raining) return Reason.WAITING;
        if (sand() == 0) return Reason.NO_SAND;
        if (outputRoom() == 0) return Reason.FULL;
        return Reason.STORM;
    }

    /** One weather roll ({@link StormRules#strikeChance}); public for the GameTests' seeded rolls. */
    public static boolean roll(RandomSource random, boolean raining, boolean thundering, boolean sky) {
        double p = StormRules.strikeChance(SkyHarvestDataLoader.current().storm(), raining, thundering, sky);
        return p > 0 && random.nextDouble() < p;
    }

    /**
     * A strike: a visual-only bolt at the tip and {@code sand_per_strike} sand fused into the result. Returns the
     * number fused. Public for {@code /firmages sky storm strike} and the GameTests.
     */
    public int strike(ServerLevel level) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(tip().getX() + 0.5, tip().getY() + 1.0, tip().getZ() + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        strikes++;
        int n = StormRules.fused(SkyHarvestDataLoader.current().storm(), sand(), outputRoom());
        int left = n;
        for (int i = 0; i < INPUTS && left > 0; i++) left -= items.extractItem(i, left, false).getCount();
        int fused = n - left;
        ItemStack out = new ItemStack(result(), fused);
        for (int i = INPUTS; i < INPUTS + OUTPUTS && !out.isEmpty(); i++) out = items.insertItem(i, out, false);
        if (!out.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, out);
        setChanged();
        setReason(level, reasonNow(level.isRaining(), skyOpen(level)));
        return fused;
    }

    private void setReason(ServerLevel level, Reason r) {
        if (r == reason) return;
        reason = r;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    void dropContents() {
        if (level == null) return;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s.copy());
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.firmages.storm_rod");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new StormRodMenu(id, inv, items, ContainerLevelAccess.create(player.level(), worldPosition));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("reason", reason.ordinal());
        tag.putLong("strikes", strikes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        reason = Reason.byOrdinal(tag.getInt("reason"));
        strikes = tag.getLong("strikes");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = super.getUpdateTag(registries);
        t.putInt("reason", reason.ordinal());
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The input half (insert sand only) or the output half (extract only) of the 32 slots. */
    private final class Part implements IItemHandler {
        private final boolean input;

        Part(boolean input) {
            this.input = input;
        }

        private int base() {
            return input ? 0 : INPUTS;
        }

        @Override
        public int getSlots() {
            return input ? INPUTS : OUTPUTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(base() + slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return input && isInput(stack) ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return input ? ItemStack.EMPTY : items.extractItem(INPUTS + slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(base() + slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return input && isInput(stack);
        }
    }
}
