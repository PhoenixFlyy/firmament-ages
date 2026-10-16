package dev.firmages.core.machine;

import com.mojang.serialization.Codec;
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
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.Optional;

/**
 * {@code firmages:ore_processing} (SPEC §40.3): the Ore Processor's recipe. One sized item input (TFC ore pieces), one
 * item result, a Mekanism chemical and a fluid used up per operation, an optional lubricant that makes the operation run
 * faster, and the operation's time in ticks at base speed. The pack writes one recipe per ore piece from the metal
 * table ({@code kubejs/server_scripts/recipes/age_9/ore_processor.js}, {@code metals.json} "ore_processor").
 *
 * <pre>{"type": "firmages:ore_processing", "input": {"item": "tfc:ore/rich_hematite", "count": 10},
 *  "result": {"id": "mekanism:dust_iron", "count": 21}, "chemical": {"id": "mekanism:hydrofluoric_acid", "amount": 210},
 *  "fluid": {"tag": "c:kerosene", "amount": 105}, "lubricant": {"tag": "c:lubricant", "amount": 21}, "time": 210}</pre>
 */
public final class OreProcessingRecipe implements Recipe<SingleRecipeInput> {
    public static final int MIN_TIME = 1;
    public static final int MAX_TIME = 72_000;

    /** A chemical by id and amount (mB); a plain record so the mod needs no Mekanism class to read it. */
    public record ChemicalAmount(ResourceLocation id, long amount) {
        public static final Codec<ChemicalAmount> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(ChemicalAmount::id),
            Codec.LONG.validate(a -> a > 0 ? com.mojang.serialization.DataResult.success(a)
                : com.mojang.serialization.DataResult.error(() -> "chemical amount must be positive")).fieldOf("amount").forGetter(ChemicalAmount::amount)
        ).apply(i, ChemicalAmount::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChemicalAmount> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ChemicalAmount::id, ByteBufCodecs.VAR_LONG, ChemicalAmount::amount, ChemicalAmount::new);
    }

    private final SizedIngredient input;
    private final ItemStack result;
    private final ChemicalAmount chemical;
    private final SizedFluidIngredient fluid;
    private final Optional<SizedFluidIngredient> lubricant;
    private final int time;

    public OreProcessingRecipe(SizedIngredient input, ItemStack result, ChemicalAmount chemical, SizedFluidIngredient fluid,
                               Optional<SizedFluidIngredient> lubricant, int time) {
        this.input = input;
        this.result = result;
        this.chemical = chemical;
        this.fluid = fluid;
        this.lubricant = lubricant;
        this.time = time;
    }

    public SizedIngredient input() {
        return input;
    }

    public ItemStack result() {
        return result;
    }

    public ChemicalAmount chemical() {
        return chemical;
    }

    public SizedFluidIngredient fluid() {
        return fluid;
    }

    public Optional<SizedFluidIngredient> lubricant() {
        return lubricant;
    }

    public int time() {
        return time;
    }

    /** The item fits this recipe's input (any count). */
    public boolean takes(ItemStack stack) {
        return input.ingredient().test(stack);
    }

    /** The fluid is this recipe's working fluid (any amount). */
    public boolean takesFluid(FluidStack stack) {
        return !stack.isEmpty() && fluid.ingredient().test(stack);
    }

    /** The fluid is this recipe's lubricant (any amount). */
    public boolean takesLubricant(FluidStack stack) {
        return !stack.isEmpty() && lubricant.map(l -> l.ingredient().test(stack)).orElse(false);
    }

    @Override
    public boolean matches(SingleRecipeInput in, Level level) {
        return input.test(in.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput in, HolderLookup.Provider registries) {
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
        return NonNullList.of(Ingredient.EMPTY, input.ingredient());
    }

    /** Not a recipe-book recipe. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MachineRegistry.ORE_PROCESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return MachineRegistry.ORE_PROCESSING_TYPE.get();
    }

    public static final MapCodec<OreProcessingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        SizedIngredient.FLAT_CODEC.fieldOf("input").forGetter(OreProcessingRecipe::input),
        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(OreProcessingRecipe::result),
        ChemicalAmount.CODEC.fieldOf("chemical").forGetter(OreProcessingRecipe::chemical),
        SizedFluidIngredient.FLAT_CODEC.fieldOf("fluid").forGetter(OreProcessingRecipe::fluid),
        SizedFluidIngredient.FLAT_CODEC.optionalFieldOf("lubricant").forGetter(OreProcessingRecipe::lubricant),
        Codec.intRange(MIN_TIME, MAX_TIME).fieldOf("time").forGetter(OreProcessingRecipe::time)
    ).apply(i, OreProcessingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, OreProcessingRecipe> STREAM_CODEC = StreamCodec.composite(
        SizedIngredient.STREAM_CODEC, OreProcessingRecipe::input,
        ItemStack.STREAM_CODEC, OreProcessingRecipe::result,
        ChemicalAmount.STREAM_CODEC, OreProcessingRecipe::chemical,
        SizedFluidIngredient.STREAM_CODEC, OreProcessingRecipe::fluid,
        ByteBufCodecs.optional(SizedFluidIngredient.STREAM_CODEC), OreProcessingRecipe::lubricant,
        ByteBufCodecs.VAR_INT, OreProcessingRecipe::time,
        OreProcessingRecipe::new);

    public static final class Serializer implements RecipeSerializer<OreProcessingRecipe> {
        @Override
        public MapCodec<OreProcessingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, OreProcessingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
