package dev.firmages.core.compat.mekanism;

import dev.firmages.core.machine.MachineRegistry;
import dev.firmages.core.machine.OreProcessorBlockEntity;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.Optional;

/**
 * The Ore Processor's chemical tank as Mekanism sees it (SPEC §40.5): one tank, insert only, any side; Mekanism pipes,
 * tubes and tanks push into it (Mekanism 10.7.19 [verified, javap]: {@code Capabilities.CHEMICAL}, {@code IChemicalHandler}).
 * The block entity keeps the chemical as id and amount, so it needs no Mekanism class. Loaded only with Mekanism.
 */
public final class ProcessorChemicals {
    private ProcessorChemicals() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.CHEMICAL.block(), MachineRegistry.ORE_PROCESSOR_BE.get(), (be, side) -> new Handler(be));
    }

    /** Puts {@code amount} of chemical {@code id} in (GameTests and the hand); returns what was taken. */
    public static long insert(OreProcessorBlockEntity be, ResourceLocation id, long amount) {
        ChemicalStack stack = stack(id, amount);
        if (stack.isEmpty()) return 0;
        ChemicalStack left = new Handler(be).insertChemical(0, stack, Action.EXECUTE);
        return amount - left.getAmount();
    }

    /** A stack of the chemical {@code id}, empty for an unknown id. */
    static ChemicalStack stack(ResourceLocation id, long amount) {
        if (id == null || amount <= 0 || id.equals(MekanismAPI.EMPTY_CHEMICAL_KEY.location())) return ChemicalStack.EMPTY;
        Optional<Holder.Reference<Chemical>> h = MekanismAPI.CHEMICAL_REGISTRY.getHolder(id);
        return h.isEmpty() ? ChemicalStack.EMPTY : new ChemicalStack(h.get(), amount);
    }

    private record Handler(OreProcessorBlockEntity be) implements IChemicalHandler {
        @Override
        public int getChemicalTanks() {
            return 1;
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank) {
            return tank != 0 ? ChemicalStack.EMPTY : stack(be.chemical(), be.chemicalAmount());
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack) {
            if (tank != 0) return;
            be.setChemical(stack.isEmpty() ? null : MekanismAPI.CHEMICAL_REGISTRY.getKey(stack.getChemical()), stack.getAmount());
        }

        @Override
        public long getChemicalTankCapacity(int tank) {
            return tank == 0 ? OreProcessorBlockEntity.CHEMICAL_CAPACITY : 0;
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack) {
            return tank == 0 && !stack.isEmpty() && be.insertChemical(MekanismAPI.CHEMICAL_REGISTRY.getKey(stack.getChemical()), 1, true) > 0;
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            if (tank != 0 || stack.isEmpty()) return stack;
            ResourceLocation id = MekanismAPI.CHEMICAL_REGISTRY.getKey(stack.getChemical());
            long took = be.insertChemical(id, stack.getAmount(), action.simulate());
            return took <= 0 ? stack : stack.getAmount() == took ? ChemicalStack.EMPTY : stack.copyWithAmount(stack.getAmount() - took);
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            return ChemicalStack.EMPTY;
        }
    }
}
