package dev.firmages.core.crown;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Star Anvil's state (SPEC §27.6, §27.10): four input slots, one output slot, a 4,000 mB tank, the forge cycle of
 * {@link CrownRules} and the crown it anchors (formed, missing blocks, the mounts on its sockets). Everything a client
 * needs (inventory, tank, reason, progress, chord, mount positions) rides on vanilla block-entity sync.
 */
public final class StarAnvilBlockEntity extends BlockEntity {
    public static final int INPUTS = 4;
    public static final int OUTPUT = 4;
    public static final int CAPACITY = 4000;
    /** Sync the progress to clients in steps of this many percent. */
    private static final int PCT_STEP = 5;

    private final ItemStackHandler items = new ItemStackHandler(INPUTS + 1) {
        @Override
        protected void onContentsChanged(int slot) {
            contentsChanged();
        }
    };
    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            contentsChanged();
        }
    };
    private final IItemHandler inputHandler = new InputHandler();
    private final IItemHandler outputHandler = new OutputHandler();
    private final IFluidHandler fluidInput = new FluidInput(false);
    private final IFluidHandler handTank = new FluidInput(true);

    // forge cycle (server)
    private CrownRules.Forge forge = CrownRules.Forge.IDLE;
    @Nullable
    private RecipeHolder<StarforgeRecipe> pending;
    private boolean recipeDirty = true;
    private int recipeGeneration = -1;
    // crown (server: from validation; client: synced)
    private boolean validateRequested = true;
    private long nextValidate;
    private int missing = -1;
    private List<BlockPos> mounts = List.of();
    // synced view
    private CrownRules.Reason reason = CrownRules.Reason.UNFORMED;
    private int reasonLens = -1;
    private int percent;
    private int lensesIn;
    private String chord = "";
    private int recipeMask;
    private int signal;
    private boolean swingOutState;
    private ItemStack pendingResult = ItemStack.EMPTY;
    private boolean dirtySync;
    /** Client: game time until which the completion flash runs down the beams. */
    private long flashUntil;

    public StarAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(CrownRegistry.STAR_ANVIL_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- accessors

    public IItemHandler inputHandler() {
        return inputHandler;
    }

    public IItemHandler outputHandler() {
        return outputHandler;
    }

    public IFluidHandler fluidInput() {
        return fluidInput;
    }

    /** The tank as a player's bucket sees it: fill with a recipe fluid, or empty it. */
    public IFluidHandler handTank() {
        return handTank;
    }

    public ItemStack stack(int slot) {
        return items.getStackInSlot(slot);
    }

    public FluidStack fluid() {
        return tank.getFluid();
    }

    public List<ItemStack> inputs() {
        List<ItemStack> out = new ArrayList<>(INPUTS);
        for (int i = 0; i < INPUTS; i++) out.add(items.getStackInSlot(i));
        return out;
    }

    public CrownRules.Reason reason() {
        return reason;
    }

    /** The lens the reason names (swing in or out), or -1. */
    public int reasonLens() {
        return reasonLens;
    }

    public int percent() {
        return percent;
    }

    public int lensesIn() {
        return lensesIn;
    }

    /** The pending recipe's chord id, or "". */
    public String chord() {
        return chord;
    }

    public int recipeMask() {
        return recipeMask;
    }

    public int signal() {
        return signal;
    }

    public ItemStack pendingResult() {
        return pendingResult;
    }

    /** Blocks the crown still lacks (-1 before the first validation). */
    public int missing() {
        return missing;
    }

    public List<BlockPos> mounts() {
        return mounts;
    }

    public double progress() {
        return forge.progress();
    }

    @Nullable
    public RecipeHolder<StarforgeRecipe> pendingRecipe() {
        return pending;
    }

    public long flashUntil() {
        return flashUntil;
    }

    public boolean formed() {
        return getBlockState().hasProperty(StarAnvilBlock.FORMED) && getBlockState().getValue(StarAnvilBlock.FORMED);
    }

    // ---------------------------------------------------------------- crown

    public void requestValidation() {
        validateRequested = true;
    }

    /** Called by {@link CrownService#validate} with the result of the multiblock check. */
    void setCrown(boolean formed, int missing, List<BlockPos> mounts) {
        this.missing = missing;
        if (!this.mounts.equals(mounts)) {
            this.mounts = List.copyOf(mounts);
            dirtySync = true;
        }
        BlockState s = getBlockState();
        if (level != null && s.getValue(StarAnvilBlock.FORMED) != formed) {
            level.setBlock(worldPosition, s.setValue(StarAnvilBlock.FORMED, formed).setValue(StarAnvilBlock.ACTIVE, false), 3);
        }
    }

    // ---------------------------------------------------------------- tick

    void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        if (validateRequested || now >= nextValidate) {
            validateRequested = false;
            nextValidate = now + ServerConfig.crownValidateTicks();
            CrownService.validate(level, worldPosition, this);
        }
        int gen = CrownService.recipeGeneration(level);
        if (recipeDirty || gen != recipeGeneration) {
            recipeDirty = false;
            recipeGeneration = gen;
            pending = CrownService.match(level, inputs()).orElse(null);
        }
        int[] lenses = new int[mounts.size()];
        boolean[] in = new boolean[mounts.size()];
        for (int i = 0; i < mounts.size(); i++) {
            lenses[i] = -1;
            if (level.getBlockEntity(mounts.get(i)) instanceof LensMountBlockEntity m) {
                lenses[i] = m.lensIndex();
                in[i] = m.fullyIn();
            }
        }
        int playing = CrownRules.lensesIn(lenses, in);
        StarforgeRecipe r = pending == null ? null : pending.value();
        boolean skyOpen = CrownService.skyOpen(level, worldPosition);
        CrownRules.View view = new CrownRules.View(ServerConfig.crownEnabled(), formed(), pending == null ? null : pending.id().toString(),
            r == null ? 0 : r.chordMask(), r == null ? 1 : r.time(), playing, r != null && r.fluidMatches(tank.getFluid()),
            r != null && outputFits(r.result()), skyOpen || !ServerConfig.crownRequireOpenSky(), skyOpen && level.isNight(),
            ServerConfig.crownNightTimeFactor());
        CrownRules.Step step = CrownRules.tick(forge, view);
        forge = step.next();
        if (step.done() && r != null) complete(level, r, playing);

        int lens = switch (step.reason()) {
            case WRONG_CHORD -> {
                int m = CrownRules.firstMissing(playing, r.chordMask());
                yield m >= 0 ? m : CrownRules.firstExtra(playing, r.chordMask());
            }
            default -> -1;
        };
        boolean swingOut = step.reason() == CrownRules.Reason.WRONG_CHORD && CrownRules.firstMissing(playing, r.chordMask()) < 0;
        int pct = step.done() ? 0 : CrownRules.percent(forge.progress(), view.time());
        int sig = r == null ? 0 : r.signal();
        String chordId = r == null ? "" : r.chord().toString();
        ItemStack result = r == null ? ItemStack.EMPTY : r.result();
        if (step.reason() != reason || lens != reasonLens || swingOut != swingOutState || Math.abs(pct - percent) >= PCT_STEP
            || (pct == 0) != (percent == 0) || playing != lensesIn || !chordId.equals(chord) || !ItemStack.matches(result, pendingResult)) {
            dirtySync = true;
        }
        reason = step.reason();
        reasonLens = lens;
        swingOutState = swingOut;
        percent = pct;
        lensesIn = playing;
        chord = chordId;
        recipeMask = r == null ? 0 : r.chordMask();
        pendingResult = result.copy();
        if (sig != signal) {
            signal = sig;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        BlockState s = getBlockState();
        boolean active = reason.forging();
        if (s.getValue(StarAnvilBlock.ACTIVE) != active) level.setBlock(worldPosition, s.setValue(StarAnvilBlock.ACTIVE, active), 3);
        if (dirtySync) {
            dirtySync = false;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** True when the chord is wrong only because a lens too many is in (Jade says "Swing out"). */
    public boolean swingOut() {
        return swingOutState;
    }

    private boolean outputFits(ItemStack result) {
        ItemStack out = items.getStackInSlot(OUTPUT);
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    /** Consumes the ingredients and the fluid, puts the result out, flashes the beams and plays the chord once. */
    private void complete(ServerLevel level, StarforgeRecipe r, int playing) {
        List<ItemStack> in = inputs();
        int[] assign = r.assignment(in);
        if (assign == null) return;
        int[] filled = java.util.stream.IntStream.range(0, INPUTS).filter(i -> !in.get(i).isEmpty()).toArray();
        for (int k = 0; k < filled.length; k++) {
            ItemStack st = items.getStackInSlot(filled[k]).copy();
            st.shrink(r.ingredients().get(assign[k]).count());
            items.setStackInSlot(filled[k], st.isEmpty() ? ItemStack.EMPTY : st);
        }
        r.fluid().ifPresent(f -> tank.drain(f.amount(), IFluidHandler.FluidAction.EXECUTE));
        ItemStack out = items.getStackInSlot(OUTPUT);
        ItemStack res = r.result().copy();
        if (out.isEmpty()) items.setStackInSlot(OUTPUT, res);
        else items.setStackInSlot(OUTPUT, out.copyWithCount(out.getCount() + res.getCount()));
        level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
        for (int n : Chord.lenses(playing)) {
            level.playSound(null, worldPosition, CrownRegistry.CHORD_SOUND.get(), SoundSource.BLOCKS, 1.0F, 0.5F + n * 0.1875F);
        }
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            if (level != null && level.isClientSide) flashUntil = level.getGameTime() + 12;
            return true;
        }
        return super.triggerEvent(id, type);
    }

    private void contentsChanged() {
        recipeDirty = true;
        dirtySync = true;
        setChanged();
    }

    // ---------------------------------------------------------------- hand

    /** Puts as much of {@code stack} as the open recipe set takes into the input slots; returns the rest. */
    public ItemStack insertFromHand(ItemStack stack) {
        ItemStack left = stack;
        for (int i = 0; i < INPUTS && !left.isEmpty(); i++) left = inputHandler.insertItem(i, left, false);
        return left;
    }

    /** The result if there is one, else the last filled input. */
    public ItemStack takeByHand() {
        if (!items.getStackInSlot(OUTPUT).isEmpty()) return items.extractItem(OUTPUT, 64, false);
        for (int i = INPUTS - 1; i >= 0; i--) if (!items.getStackInSlot(i).isEmpty()) return items.extractItem(i, 64, false);
        return ItemStack.EMPTY;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i <= OUTPUT; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, s.copy());
        }
    }

    // ---------------------------------------------------------------- handlers

    /** How many of {@code stack} input slot {@code slot} takes without leaving every open recipe. */
    int acceptable(int slot, ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty() || slot < 0 || slot >= INPUTS) return 0;
        ItemStack cur = items.getStackInSlot(slot);
        if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, stack)) return 0;
        List<ItemStack> in = inputs();
        if (cur.isEmpty()) {
            for (int i = 0; i < INPUTS; i++) if (i != slot && ItemStack.isSameItemSameComponents(in.get(i), stack)) return 0;
        }
        List<RecipeHolder<StarforgeRecipe>> recipes = CrownService.recipes(level);
        int cap = 0;
        for (RecipeHolder<StarforgeRecipe> h : recipes) {
            for (var g : h.value().ingredients()) if (g.ingredient().test(stack)) cap = Math.max(cap, g.count());
        }
        int room = Math.min(stack.getMaxStackSize(), cap) - cur.getCount();
        for (int n = Math.min(stack.getCount(), room); n >= 1; n--) {
            List<ItemStack> trial = new ArrayList<>(in);
            trial.set(slot, stack.copyWithCount(cur.getCount() + n));
            for (RecipeHolder<StarforgeRecipe> h : recipes) if (h.value().accepts(trial)) return n;
        }
        return 0;
    }

    private final class InputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return INPUTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int n = acceptable(slot, stack);
            if (n <= 0) return stack;
            if (!simulate) {
                ItemStack cur = items.getStackInSlot(slot);
                items.setStackInSlot(slot, stack.copyWithCount(cur.getCount() + n));
            }
            return stack.getCount() == n ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - n);
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
            return acceptable(slot, stack.copyWithCount(1)) > 0;
        }
    }

    private final class OutputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(OUTPUT);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return items.extractItem(OUTPUT, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    /** The tank from outside: only a fluid some live starforge recipe takes goes in; only a hand may empty it. */
    private final class FluidInput implements IFluidHandler {
        private final boolean drainable;

        FluidInput(boolean drainable) {
            this.drainable = drainable;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int t) {
            return CAPACITY;
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return level != null && !level.isClientSide && CrownService.fluidAccepted(level, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource)) return 0;
            return tank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return drainable ? tank.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return drainable ? tank.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }

    // ---------------------------------------------------------------- lifecycle and sync

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) CrownService.track(sl, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) CrownService.untrack(sl, worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel sl) CrownService.untrack(sl, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        if (forge.recipe() != null) {
            tag.putString("recipe", forge.recipe());
            tag.putInt("chordIn", forge.lensesIn());
            tag.putDouble("progress", forge.progress());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        tank.readFromNBT(registries, tag.getCompound("tank"));
        forge = tag.contains("recipe") ? new CrownRules.Forge(tag.getString("recipe"), tag.getInt("chordIn"), tag.getDouble("progress"))
            : CrownRules.Forge.IDLE;
        recipeDirty = true;
        if (tag.contains("view")) {
            CompoundTag v = tag.getCompound("view");
            int r = v.getInt("reason");
            reason = r >= 0 && r < CrownRules.Reason.values().length ? CrownRules.Reason.values()[r] : CrownRules.Reason.UNFORMED;
            reasonLens = v.getInt("lens");
            swingOutState = v.getBoolean("swingOut");
            percent = v.getInt("percent");
            lensesIn = v.getInt("in");
            chord = v.getString("chord");
            recipeMask = v.getInt("mask");
            missing = v.getInt("missing");
            pendingResult = v.contains("result") ? ItemStack.parseOptional(registries, v.getCompound("result")) : ItemStack.EMPTY;
            List<BlockPos> m = new ArrayList<>();
            for (long l : v.getLongArray("mounts")) m.add(BlockPos.of(l));
            mounts = List.copyOf(m);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        CompoundTag v = new CompoundTag();
        v.putInt("reason", reason.ordinal());
        v.putInt("lens", reasonLens);
        v.putBoolean("swingOut", swingOutState);
        v.putInt("percent", percent);
        v.putInt("in", lensesIn);
        v.putString("chord", chord);
        v.putInt("mask", recipeMask);
        v.putInt("missing", missing);
        if (!pendingResult.isEmpty()) v.put("result", pendingResult.save(registries));
        v.put("mounts", new LongArrayTag(mounts.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.put("view", v);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
