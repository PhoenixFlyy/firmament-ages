package dev.firmages.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.machine.ArcaneCatalystBlockEntity;
import dev.firmages.core.machine.MachineText;
import dev.firmages.core.machine.OreProcessingRecipe;
import dev.firmages.core.machine.OreProcessorBlockEntity;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /firmages processor status <pos> | feed <pos> [item count [dry]]} and {@code /firmages catalyst status <pos>}
 * (SPEC §40.8, §41.7). {@code feed} (needs {@code debug.allowSimulate}) fills the processor's FE buffer and its three
 * tanks for the recipe of the item it holds (or of {@code item}; {@code dry}: no lubricant), and puts {@code count} of {@code item} in: the copy
 * boots run a recipe without pipes.
 */
final class MachineCommands {
    private MachineCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> processor(CommandBuildContext ctx) {
        return Commands.literal("processor")
            .then(Commands.literal("status").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(MachineCommands::processorStatus)))
            .then(Commands.literal("feed").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> feed(c, ItemStack.EMPTY, true))
                .then(Commands.argument("item", ItemArgument.item(ctx)).then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                    .executes(c -> feed(c, ItemArgument.getItem(c, "item").createItemStack(IntegerArgumentType.getInteger(c, "count"), false), true))
                    .then(Commands.literal("dry")
                        .executes(c -> feed(c, ItemArgument.getItem(c, "item").createItemStack(IntegerArgumentType.getInteger(c, "count"), false), false)))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> catalyst() {
        return Commands.literal("catalyst")
            .then(Commands.literal("status").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(MachineCommands::catalystStatus)));
    }

    private static void say(CommandSourceStack src, String l) {
        FirmagesCore.LOGGER.info(l);
        src.sendSuccess(() -> Component.literal(l), false);
    }

    private static int processorStatus(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof OreProcessorBlockEntity be)) {
            c.getSource().sendFailure(Component.literal("No Ore Processor at " + pos.toShortString()));
            return 0;
        }
        for (String l : lines(be)) say(c.getSource(), l);
        return be.percent();
    }

    static List<String> lines(OreProcessorBlockEntity be) {
        List<String> out = new ArrayList<>();
        RecipeHolder<OreProcessingRecipe> r = be.recipe();
        out.add("Ore Processor at " + be.getBlockPos().toShortString() + ": " + be.reason() + " " + be.percent() + "%" + (be.lubricated() ? " lubricated" : "")
            + ", recipe " + (r == null ? "none" : r.id()));
        StringBuilder items = new StringBuilder("  slots:");
        for (int i = 0; i <= OreProcessorBlockEntity.OUTPUTS; i++) {
            ItemStack s = be.stack(i);
            items.append(' ').append(s.isEmpty() ? "-" : s.getCount() + " " + BuiltInRegistries.ITEM.getKey(s.getItem()));
        }
        out.add(items.toString());
        out.add("  energy " + be.energy() + " of " + be.energyCapacity() + " FE; chemical " + (be.chemical() == null ? "-" : be.chemicalAmount() + " mB "
            + be.chemical()) + "; fluid " + fluid(be.fluid()) + "; lubricant " + fluid(be.lubricant()));
        return out;
    }

    private static String fluid(FluidStack f) {
        return f.isEmpty() ? "-" : f.getAmount() + " mB " + BuiltInRegistries.FLUID.getKey(f.getFluid());
    }

    private static int feed(CommandContext<CommandSourceStack> c, ItemStack item, boolean lubricant) throws CommandSyntaxException {
        if (!ServerConfig.allowSimulate()) {
            c.getSource().sendFailure(Component.literal("Disabled; set debug.allowSimulate = true in firmages-server.toml"));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(level.getBlockEntity(pos) instanceof OreProcessorBlockEntity be)) {
            c.getSource().sendFailure(Component.literal("No Ore Processor at " + pos.toShortString()));
            return 0;
        }
        if (!item.isEmpty()) {
            ItemStack left = be.itemHandler().insertItem(0, item, false);
            say(c.getSource(), "put in " + (item.getCount() - left.getCount()) + " " + BuiltInRegistries.ITEM.getKey(item.getItem()));
        }
        ItemStack probe = item.isEmpty() ? be.stack(0) : item;
        var recipe = dev.firmages.core.machine.ProcessorRecipes.forInput(level, probe);
        if (recipe.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No live ore_processing recipe takes " + BuiltInRegistries.ITEM.getKey(probe.getItem())
                + " (the Singularity Age opens them)"));
            return 0;
        }
        OreProcessingRecipe r = recipe.get().value();
        while (be.energyHandler().receiveEnergy(Integer.MAX_VALUE, false) > 0) {
            // fill the buffer
        }
        long chem = net.neoforged.fml.ModList.get().isLoaded("mekanism")
            ? dev.firmages.core.compat.mekanism.ProcessorChemicals.insert(be, r.chemical().id(), OreProcessorBlockEntity.CHEMICAL_CAPACITY) : 0;
        int fl = fill(be, r.fluid().getFluids(), OreProcessorBlockEntity.FLUID_CAPACITY);
        int lub = lubricant ? r.lubricant().map(l -> fill(be, l.getFluids(), OreProcessorBlockEntity.LUBRICANT_CAPACITY)).orElse(0) : 0;
        say(c.getSource(), "fed " + recipe.get().id() + ": energy " + be.energy() + " FE, " + chem + " mB " + r.chemical().id() + ", " + fl + " mB fluid, "
            + lub + " mB lubricant");
        return 1;
    }

    private static int fill(OreProcessorBlockEntity be, FluidStack[] options, int amount) {
        for (FluidStack f : options) {
            int n = be.fluidHandler().fill(f.copyWithAmount(amount), IFluidHandler.FluidAction.EXECUTE);
            if (n > 0) return n;
        }
        return 0;
    }

    private static int catalystStatus(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (!(c.getSource().getLevel().getBlockEntity(pos) instanceof ArcaneCatalystBlockEntity be)) {
            c.getSource().sendFailure(Component.literal("No Arcane Catalyst at " + pos.toShortString()));
            return 0;
        }
        say(c.getSource(), "Arcane Catalyst at " + pos.toShortString() + " facing " + be.facing() + ": " + be.status() + ", energy " + be.energy()
            + " FE, extra ticks given " + be.extraTicksGiven() + ", FE paid " + be.fePaid() + " (" + MachineText.catalystState(be).getString() + ")");
        return (int) Math.min(Integer.MAX_VALUE, be.extraTicksGiven());
    }
}
