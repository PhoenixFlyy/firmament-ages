package dev.firmages.core.machine;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The Ore Processor's state (SPEC §40): one input slot, four output slots, a chemical tank (id and amount, filled
 * through Mekanism's chemical capability, {@code compat/mekanism/ProcessorChemicals}), a working-fluid tank, a lubricant
 * tank and an FE buffer of one second at full speed. {@link ProcessorRules} runs the operation. Inputs come from any
 * side, the results leave from any side; nothing else can be taken out. Clients get the view (input, tanks, energy,
 * progress, reason) through vanilla block-entity sync for Jade.
 */
public final class OreProcessorBlockEntity extends BlockEntity {
    public static final int OUTPUTS = 4;
    public static final long CHEMICAL_CAPACITY = 16_000;
    public static final int FLUID_CAPACITY = 16_000;
    public static final int LUBRICANT_CAPACITY = 8_000;
    private static final int PCT_STEP = 5;

    private final ItemStackHandler items = new ItemStackHandler(1 + OUTPUTS) {
        @Override
        protected void onContentsChanged(int slot) {
            changed(slot == 0);
        }
    };
    private final FluidTank fluid = new FluidTank(FLUID_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            changed(false);
        }
    };
    private final FluidTank lubricant = new FluidTank(LUBRICANT_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            changed(false);
        }
    };
    @Nullable
    private ResourceLocation chemical;
    private long chemicalAmount;
    private int energy;

    private final IItemHandler itemHandler = new Items();
    private final IFluidHandler fluidHandler = new Fluids();
    private final IEnergyStorage energyHandler = new Energy();

    // operation (server)
    private ProcessorRules.State state = ProcessorRules.State.IDLE;
    @Nullable
    private RecipeHolder<OreProcessingRecipe> recipe;
    private boolean recipeDirty = true;
    private int recipeGeneration = -1;
    // synced view
    private ProcessorRules.Reason reason = ProcessorRules.Reason.IDLE;
    private int percent;
    private boolean lubricated;
    private ItemStack pendingResult = ItemStack.EMPTY;
    private int syncedEnergy;
    private boolean dirtySync;

    public OreProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(MachineRegistry.ORE_PROCESSOR_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- accessors

    public IItemHandler itemHandler() {
        return itemHandler;
    }

    public IFluidHandler fluidHandler() {
        return fluidHandler;
    }

    public IEnergyStorage energyHandler() {
        return energyHandler;
    }

    public ItemStack stack(int slot) {
        return items.getStackInSlot(slot);
    }

    public FluidStack fluid() {
        return fluid.getFluid();
    }

    public FluidStack lubricant() {
        return lubricant.getFluid();
    }

    @Nullable
    public ResourceLocation chemical() {
        return chemical;
    }

    public long chemicalAmount() {
        return chemicalAmount;
    }

    public int energy() {
        return energy;
    }

    public int energyCapacity() {
        return capacity();
    }

    public ProcessorRules.Reason reason() {
        return reason;
    }

    public int percent() {
        return percent;
    }

    public boolean lubricated() {
        return lubricated;
    }

    public ItemStack pendingResult() {
        return pendingResult;
    }

    @Nullable
    public RecipeHolder<OreProcessingRecipe> recipe() {
        return recipe;
    }

    static int capacity() {
        long c = ServerConfig.processorFePerTick() * (long) Math.ceil(ServerConfig.processorLubricantSpeed()) * 20L;
        return (int) Math.min(Integer.MAX_VALUE, c);
    }

    // ---------------------------------------------------------------- chemical (Mekanism side in compat)

    /** Chemical the tank takes: one a live recipe uses, the same as what it holds. Returns the amount taken. */
    public long insertChemical(ResourceLocation id, long amount, boolean simulate) {
        if (level == null || amount <= 0) return 0;
        if (chemical != null && chemicalAmount > 0 && !chemical.equals(id)) return 0;
        if (!ProcessorRecipes.chemicalUsed(level, id)) return 0;
        long take = Math.min(amount, CHEMICAL_CAPACITY - chemicalAmount);
        if (take <= 0) return 0;
        if (!simulate) {
            chemical = id;
            chemicalAmount += take;
            changed(false);
        }
        return take;
    }

    /** Empties the chemical tank by {@code amount} (for the chemical handler's extract; the processor gives nothing back). */
    public void setChemical(@Nullable ResourceLocation id, long amount) {
        chemical = amount <= 0 ? null : id;
        chemicalAmount = Math.max(0, amount);
        changed(false);
    }

    // ---------------------------------------------------------------- tick

    void serverTick(ServerLevel level) {
        int gen = ProcessorRecipes.generation(level);
        if (recipeDirty || gen != recipeGeneration) {
            recipeDirty = false;
            recipeGeneration = gen;
            RecipeHolder<OreProcessingRecipe> next = ProcessorRecipes.forInput(level, items.getStackInSlot(0)).orElse(null);
            if (next == null || recipe == null || !next.id().equals(recipe.id())) state = ProcessorRules.State.IDLE;
            recipe = next;
        }
        OreProcessingRecipe r = recipe == null ? null : recipe.value();
        ItemStack in = items.getStackInSlot(0);
        ProcessorRules.View v = new ProcessorRules.View(in.isEmpty(), r != null && in.getCount() >= r.input().count(),
            r != null && chemicalReady(r), r != null && fluidReady(r), r != null && outputFits(r.result()),
            r != null && lubricantReady(r), energy, ServerConfig.processorFePerTick(), ServerConfig.processorLubricantSpeed(),
            r == null ? 1 : r.time());
        ProcessorRules.Step step = ProcessorRules.tick(state, v);
        if (step.takeLubricant() && r != null) r.lubricant().ifPresent(l -> lubricant.drain(l.amount(), IFluidHandler.FluidAction.EXECUTE));
        energy -= (int) Math.min(energy, step.energyUsed());
        state = step.next();
        if (step.done() && r != null) complete(r);

        int pct = step.done() ? 0 : ProcessorRules.percent(state.progress(), r == null ? 1 : r.time());
        boolean lub = state.started() && state.lubricated();
        ItemStack result = r == null ? ItemStack.EMPTY : r.result();
        if (step.reason() != reason || Math.abs(pct - percent) >= PCT_STEP || (pct == 0) != (percent == 0) || lub != lubricated
            || !ItemStack.matches(result, pendingResult) || (level.getGameTime() % 20 == 0 && energy != syncedEnergy)) {
            dirtySync = true;
        }
        reason = step.reason();
        percent = pct;
        lubricated = lub;
        pendingResult = result.copy();
        boolean active = reason == ProcessorRules.Reason.WORKING;
        BlockState s = getBlockState();
        if (s.hasProperty(OreProcessorBlock.ACTIVE) && s.getValue(OreProcessorBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, s.setValue(OreProcessorBlock.ACTIVE, active), 3);
        }
        if (dirtySync) {
            dirtySync = false;
            syncedEnergy = energy;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private boolean chemicalReady(OreProcessingRecipe r) {
        return chemical != null && chemical.equals(r.chemical().id()) && chemicalAmount >= r.chemical().amount();
    }

    private boolean fluidReady(OreProcessingRecipe r) {
        return r.fluid().test(fluid.getFluid());
    }

    private boolean lubricantReady(OreProcessingRecipe r) {
        return r.lubricant().map(l -> l.test(lubricant.getFluid())).orElse(false);
    }

    private boolean outputFits(ItemStack result) {
        int left = result.getCount();
        for (int i = 1; i <= OUTPUTS && left > 0; i++) {
            ItemStack o = items.getStackInSlot(i);
            if (o.isEmpty()) left -= result.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(o, result)) left -= o.getMaxStackSize() - o.getCount();
        }
        return left <= 0;
    }

    private void complete(OreProcessingRecipe r) {
        items.extractItem(0, r.input().count(), false);
        chemicalAmount -= r.chemical().amount();
        if (chemicalAmount <= 0) {
            chemicalAmount = 0;
            chemical = null;
        }
        fluid.drain(r.fluid().amount(), IFluidHandler.FluidAction.EXECUTE);
        ItemStack left = r.result().copy();
        for (int i = 1; i <= OUTPUTS && !left.isEmpty(); i++) left = items.insertItem(i, left, false);
        recipeDirty = true;
        changed(false);
    }

    private void changed(boolean input) {
        if (input) recipeDirty = true;
        dirtySync = true;
        setChanged();
    }

    // ---------------------------------------------------------------- hand

    /** Puts what the input slot takes of {@code stack} in; returns the rest. */
    public ItemStack insertFromHand(ItemStack stack) {
        return itemHandler.insertItem(0, stack, false);
    }

    /** The results, then the input. */
    public ItemStack takeByHand() {
        for (int i = 1; i <= OUTPUTS; i++) if (!items.getStackInSlot(i).isEmpty()) return items.extractItem(i, 64, false);
        return items.extractItem(0, 64, false);
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i <= OUTPUTS; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s.copy());
        }
    }

    // ---------------------------------------------------------------- handlers

    private final class Items implements IItemHandler {
        @Override
        public int getSlots() {
            return 1 + OUTPUTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || level == null || level.isClientSide) return stack;
            if (ProcessorRecipes.forInput(level, stack).isEmpty()) return stack;
            return items.insertItem(0, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 1 || slot > OUTPUTS) return ItemStack.EMPTY;
            return items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && level != null && ProcessorRecipes.forInput(level, stack).isPresent();
        }
    }

    /** Fill only: a recipe's lubricant goes into the lubricant tank, a recipe's working fluid into the fluid tank. */
    private final class Fluids implements IFluidHandler {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? fluid.getFluid() : lubricant.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? FLUID_CAPACITY : LUBRICANT_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (level == null) return false;
            return tank == 0 ? ProcessorRecipes.fluidUsed(level, stack) : ProcessorRecipes.lubricantUsed(level, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || level == null) return 0;
            if (ProcessorRecipes.lubricantUsed(level, resource)) return lubricant.fill(resource, action);
            if (ProcessorRecipes.fluidUsed(level, resource)) return fluid.fill(resource, action);
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    private final class Energy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int room = capacity() - energy;
            int n = Math.max(0, Math.min(room, toReceive));
            if (!simulate && n > 0) {
                energy += n;
                setChanged();
            }
            return n;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    // ---------------------------------------------------------------- save and sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.put("fluid", fluid.writeToNBT(registries, new CompoundTag()));
        tag.put("lubricant", lubricant.writeToNBT(registries, new CompoundTag()));
        if (chemical != null) tag.putString("chemical", chemical.toString());
        tag.putLong("chemical_amount", chemicalAmount);
        tag.putInt("energy", energy);
        tag.putDouble("progress", state.progress());
        tag.putBoolean("started", state.started());
        tag.putBoolean("lubricated", state.lubricated());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        fluid.readFromNBT(registries, tag.getCompound("fluid"));
        lubricant.readFromNBT(registries, tag.getCompound("lubricant"));
        chemical = tag.contains("chemical") ? ResourceLocation.tryParse(tag.getString("chemical")) : null;
        chemicalAmount = tag.getLong("chemical_amount");
        energy = tag.getInt("energy");
        state = new ProcessorRules.State(tag.getDouble("progress"), tag.getBoolean("started"), tag.getBoolean("lubricated"));
        if (tag.contains("view_reason")) {
            reason = ProcessorRules.Reason.byOrdinal(tag.getInt("view_reason"));
            percent = tag.getInt("view_percent");
            lubricated = tag.getBoolean("view_lubricated");
            pendingResult = ItemStack.parseOptional(registries, tag.getCompound("view_result"));
        }
        recipeDirty = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        tag.putInt("view_reason", reason.ordinal());
        tag.putInt("view_percent", percent);
        tag.putBoolean("view_lubricated", lubricated);
        tag.put("view_result", pendingResult.saveOptional(registries));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
