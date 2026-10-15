package dev.firmages.core.crown;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code firmages:starforge} (SPEC §27.7): one to four sized ingredients in the Star Anvil, an optional fluid in its
 * tank, a named chord of lenses, a forge time of 20..1200 ticks and an item result. The chord is resolved against
 * the chord library of the same datapack load ({@link ChordLibrary#latest()}); an unknown chord rejects the file.
 * The resolved lens mask and comparator signal travel with the recipe to clients (EMI dots).
 */
public final class StarforgeRecipe implements Recipe<StarforgeRecipe.Input> {
    public static final int MAX_INGREDIENTS = 4;
    public static final int MIN_TIME = 20;
    public static final int MAX_TIME = 1200;
    public static final int DEFAULT_TIME = 100;

    private final ResourceLocation chord;
    private final int chordMask;
    private final int signal;
    private final List<SizedIngredient> ingredients;
    private final Optional<SizedFluidIngredient> fluid;
    private final int time;
    private final ItemStack result;

    public StarforgeRecipe(ResourceLocation chord, int chordMask, int signal, List<SizedIngredient> ingredients,
                           Optional<SizedFluidIngredient> fluid, int time, ItemStack result) {
        this.chord = chord;
        this.chordMask = chordMask;
        this.signal = signal;
        this.ingredients = List.copyOf(ingredients);
        this.fluid = fluid;
        this.time = time;
        this.result = result;
    }

    /** The anvil's input slots. */
    public record Input(List<ItemStack> items) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return items.get(index);
        }

        @Override
        public int size() {
            return items.size();
        }
    }

    public ResourceLocation chord() {
        return chord;
    }

    public int chordMask() {
        return chordMask;
    }

    public int signal() {
        return signal;
    }

    public List<SizedIngredient> ingredients() {
        return ingredients;
    }

    public Optional<SizedFluidIngredient> fluid() {
        return fluid;
    }

    public int time() {
        return time;
    }

    public ItemStack result() {
        return result;
    }

    // ---------------------------------------------------------------- matching

    private static int[] filled(List<ItemStack> slots) {
        boolean[] empty = new boolean[slots.size()];
        for (int i = 0; i < empty.length; i++) empty[i] = slots.get(i).isEmpty();
        return CrownRules.filled(empty);
    }

    /**
     * The ingredient each filled slot stands for, when the slots hold this recipe completely (every ingredient with at
     * least its count, nothing else), or null.
     */
    public int[] assignment(List<ItemStack> slots) {
        int[] f = filled(slots);
        return CrownRules.assign(f, ingredients.size(), true, (s, g) -> ingredients.get(g).test(slots.get(s)));
    }

    /** The slots hold part of this recipe: every filled slot is its own ingredient, with at most the needed count. */
    public boolean accepts(List<ItemStack> slots) {
        int[] f = filled(slots);
        return CrownRules.assign(f, ingredients.size(), false, (s, g) -> {
            ItemStack st = slots.get(s);
            return ingredients.get(g).ingredient().test(st) && st.getCount() <= ingredients.get(g).count();
        }) != null;
    }

    public boolean fluidMatches(FluidStack tank) {
        return fluid.map(f -> f.test(tank)).orElse(true);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return assignment(input.items()) != null;
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> out = NonNullList.create();
        ingredients.forEach(i -> out.add(i.ingredient()));
        return out;
    }

    /** Not a recipe-book recipe (the client book would warn about an unknown category). */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CrownRegistry.STARFORGE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return CrownRegistry.STARFORGE_TYPE.get();
    }

    // ---------------------------------------------------------------- serialization

    private record Raw(ResourceLocation chord, List<SizedIngredient> ingredients, Optional<SizedFluidIngredient> fluid, int time,
                       ItemStack result) {}

    private static final MapCodec<Raw> RAW = RecordCodecBuilder.mapCodec(i -> i.group(
        ResourceLocation.CODEC.fieldOf("chord").forGetter(Raw::chord),
        SizedIngredient.FLAT_CODEC.listOf().fieldOf("ingredients").forGetter(Raw::ingredients),
        SizedFluidIngredient.FLAT_CODEC.optionalFieldOf("fluid").forGetter(Raw::fluid),
        Codec.intRange(MIN_TIME, MAX_TIME).optionalFieldOf("time", DEFAULT_TIME).forGetter(Raw::time),
        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(Raw::result)
    ).apply(i, Raw::new));

    public static final MapCodec<StarforgeRecipe> CODEC = RAW.flatXmap(StarforgeRecipe::resolve,
        r -> DataResult.success(new Raw(r.chord, r.ingredients, r.fluid, r.time, r.result)));

    private static DataResult<StarforgeRecipe> resolve(Raw raw) {
        if (raw.ingredients().isEmpty() || raw.ingredients().size() > MAX_INGREDIENTS) {
            return DataResult.error(() -> "a starforge recipe takes one to four ingredients, not " + raw.ingredients().size());
        }
        Optional<Chord> chord = ChordLibrary.latest().get(raw.chord().toString());
        if (chord.isEmpty()) return DataResult.error(() -> "unknown chord " + raw.chord());
        return DataResult.success(new StarforgeRecipe(raw.chord(), chord.get().mask(), chord.get().signal(), raw.ingredients(), raw.fluid(),
            raw.time(), raw.result()));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, StarforgeRecipe> STREAM_CODEC = StreamCodec.of(
        (buf, r) -> {
            buf.writeResourceLocation(r.chord);
            buf.writeVarInt(r.chordMask);
            buf.writeVarInt(r.signal);
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.ingredients);
            ByteBufCodecs.optional(SizedFluidIngredient.STREAM_CODEC).encode(buf, r.fluid);
            buf.writeVarInt(r.time);
            ItemStack.STREAM_CODEC.encode(buf, r.result);
        },
        buf -> {
            ResourceLocation chord = buf.readResourceLocation();
            int mask = buf.readVarInt();
            int signal = buf.readVarInt();
            List<SizedIngredient> in = new ArrayList<>(SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
            Optional<SizedFluidIngredient> fluid = ByteBufCodecs.optional(SizedFluidIngredient.STREAM_CODEC).decode(buf);
            int time = buf.readVarInt();
            ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
            return new StarforgeRecipe(chord, mask, signal, in, fluid, time, result);
        });

    public static final class Serializer implements RecipeSerializer<StarforgeRecipe> {
        @Override
        public MapCodec<StarforgeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, StarforgeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
