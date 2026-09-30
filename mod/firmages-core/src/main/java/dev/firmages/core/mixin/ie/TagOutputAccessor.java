package dev.firmages.core.mixin.ie;

import blusunrize.immersiveengineering.api.crafting.IngredientWithSize;
import blusunrize.immersiveengineering.api.crafting.TagOutput;
import com.mojang.datafixers.util.Either;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the unresolved content of an IE {@link TagOutput} (IE 12.4.2 [verified, javap]: private field
 * {@code rawData}). {@code TagOutput#get} would resolve the tag and cache the stack while the load's tags are not
 * bound yet. Applied only when IE is loaded ({@code FirmagesMixinPlugin}).
 */
@Mixin(value = TagOutput.class, remap = false)
public interface TagOutputAccessor {
    @Accessor("rawData")
    Either<IngredientWithSize, ItemStack> firmages$rawData();
}
