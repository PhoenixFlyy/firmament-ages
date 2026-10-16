package dev.firmages.core.machine;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Player lines of the Ore Processor and the Arcane Catalyst (Jade, right-click), lang keys {@code firmages.processor.*}, {@code firmages.catalyst.*}. */
public final class MachineText {
    private MachineText() {}

    public static String n(long v) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(v);
    }

    /** "Hydrofluoric Acid", from Mekanism's lang key {@code chemical.<ns>.<path>}. */
    public static Component chemicalName(ResourceLocation id) {
        return Component.translatable("chemical." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
    }

    public static MutableComponent processorState(OreProcessorBlockEntity be) {
        ProcessorRules.Reason r = be.reason();
        ItemStack out = be.pendingResult();
        if (r == ProcessorRules.Reason.WORKING) {
            return Component.translatable(be.lubricated() ? "firmages.processor.working.lubricated" : "firmages.processor.working",
                out.getCount(), out.getHoverName(), be.percent()).withStyle(ChatFormatting.GREEN);
        }
        return Component.translatable("firmages.processor.reason." + r.key()).withStyle(ChatFormatting.GRAY);
    }

    /** The Jade lines below the state: energy, chemical, fluid, lubricant. */
    public static List<Component> processorTanks(OreProcessorBlockEntity be) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("firmages.processor.energy", n(be.energy()), n(be.energyCapacity())));
        ResourceLocation chem = be.chemical();
        if (chem != null && be.chemicalAmount() > 0) out.add(Component.translatable("firmages.processor.tank", chemicalName(chem), n(be.chemicalAmount())));
        FluidStack f = be.fluid();
        if (!f.isEmpty()) out.add(Component.translatable("firmages.processor.tank", f.getHoverName(), n(f.getAmount())));
        FluidStack l = be.lubricant();
        if (!l.isEmpty()) out.add(Component.translatable("firmages.processor.tank", l.getHoverName(), n(l.getAmount())));
        return out;
    }

    public static MutableComponent catalystState(ArcaneCatalystBlockEntity be) {
        ArcaneCatalystBlockEntity.Status s = be.status();
        if (s == ArcaneCatalystBlockEntity.Status.QUICKENING) {
            int pct = (int) Math.round(ServerConfig.catalystExtraTicks() * 100);
            return Component.translatable("firmages.catalyst.quickening", pct).withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        if (s == ArcaneCatalystBlockEntity.Status.NO_ROTATION) {
            return Component.translatable("firmages.catalyst.no_rotation", ServerConfig.catalystMinRpm()).withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("firmages.catalyst." + s.key()).withStyle(ChatFormatting.GRAY);
    }
}
