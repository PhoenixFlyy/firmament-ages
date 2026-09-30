package dev.firmages.core.gate.extract;

import dev.firmages.core.gate.OutputExtractor;
import dev.firmages.core.gate.OutputSink;
import mekanism.api.recipes.MekanismRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Mekanism 10.7.19 [verified, javap]: every {@code MekanismRecipe} declares its outputs through public no-argument
 * {@code get*OutputDefinition()} methods ({@code getOutputDefinition}, sawmill {@code getMainOutputDefinition} /
 * {@code getSecondaryOutputDefinition}). They return lists of {@link ItemStack}, {@link FluidStack}, chemical stacks
 * or small output records (PRC {@code item + chemical}, {@code fluid + optional item}); items and fluids are
 * collected, chemicals are not mapped to Ages (SPEC §1.2). The method name convention also covers the Mekanism
 * addons (MoreMachine, Evolved), which are unverified in source.
 */
public final class MekanismExtractor implements OutputExtractor {
    private static final Pattern DEFINITION = Pattern.compile("get\\w*OutputDefinition");
    private static final Map<Class<?>, List<Method>> METHODS = new ConcurrentHashMap<>();

    @Override
    public boolean extract(Recipe<?> recipe, OutputSink sink, HolderLookup.Provider registries) throws ReflectiveOperationException {
        if (!(recipe instanceof MekanismRecipe<?>)) return false;
        for (Method m : definitions(recipe.getClass())) value(m.invoke(recipe), sink, 0);
        return true;
    }

    private static List<Method> definitions(Class<?> cls) {
        return METHODS.computeIfAbsent(cls, c -> {
            List<Method> out = new ArrayList<>();
            for (Method m : c.getMethods()) {
                if (m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers()) && DEFINITION.matcher(m.getName()).matches()) {
                    m.trySetAccessible();
                    out.add(m);
                }
            }
            return List.copyOf(out);
        });
    }

    private static void value(Object v, OutputSink sink, int depth) throws ReflectiveOperationException {
        if (v == null || depth > 4) return;
        if (v instanceof ItemStack s) ExtractSupport.item(sink, s);
        else if (v instanceof FluidStack f) ExtractSupport.fluid(sink, f);
        else if (v instanceof Iterable<?> it) {
            for (Object e : it) value(e, sink, depth + 1);
        } else if (v instanceof Record r) {
            for (RecordComponent c : r.getClass().getRecordComponents()) {
                Method acc = c.getAccessor();
                acc.trySetAccessible();
                value(acc.invoke(r), sink, depth + 1);
            }
        }
    }

    @Override
    public String name() {
        return "mekanism";
    }
}
