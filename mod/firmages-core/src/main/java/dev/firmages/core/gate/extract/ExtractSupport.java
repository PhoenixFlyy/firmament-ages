package dev.firmages.core.gate.extract;

import dev.firmages.core.gate.OutputSink;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared helpers for the typed extractors: stacks to ids, ingredient values without resolving tags, field scans. */
public final class ExtractSupport {
    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    private ExtractSupport() {}

    public static void item(OutputSink sink, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) sink.id(OutputSink.Kind.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static void fluid(OutputSink sink, FluidStack stack) {
        if (stack != null && !stack.isEmpty()) fluid(sink, stack.getFluid());
    }

    public static void fluid(OutputSink sink, Fluid fluid) {
        if (fluid != null) sink.id(OutputSink.Kind.FLUID, BuiltInRegistries.FLUID.getKey(fluid).toString());
    }

    /**
     * An ingredient used as an output (IE tag outputs): its item and tag values, read without resolving the tag
     * ({@link Ingredient#getItems()} would cache an empty or stale result). Custom ingredients are left to the JSON walk.
     */
    public static void ingredient(OutputSink sink, Ingredient ing) {
        if (ing == null || ing.isCustom()) return;
        for (Ingredient.Value v : ing.getValues()) {
            if (v instanceof Ingredient.TagValue t) sink.tag(OutputSink.Kind.ITEM, t.tag().location().toString());
            else if (v instanceof Ingredient.ItemValue i) item(sink, i.item());
        }
    }

    /**
     * Instance fields of {@code cls} and its superclasses up to (and including) {@code stopAt}, made accessible,
     * whose name does not describe an input. Cached per class.
     */
    public static List<Field> outputFields(Class<?> cls, Class<?> stopAt) {
        return FIELDS.computeIfAbsent(cls, c -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                    if (n.contains("input") || n.contains("ingredient") || n.contains("catalyst") || n.contains("reagent")) continue;
                    try {
                        f.setAccessible(true);
                        out.add(f);
                    } catch (RuntimeException e) {
                        // inaccessible field: the JSON walk still covers the recipe
                    }
                }
                if (k == stopAt) break;
            }
            return List.copyOf(out);
        });
    }

    /** Elements of a collection or array value, else the value itself. */
    public static Collection<?> elements(Object v) {
        if (v instanceof Collection<?> c) return c;
        if (v instanceof Object[] a) return java.util.Arrays.asList(a);
        return v == null ? List.of() : List.of(v);
    }
}
