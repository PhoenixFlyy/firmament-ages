package dev.firmages.core.mixin.ie;

import blusunrize.immersiveengineering.api.excavator.MineralMix;
import dev.firmages.core.miner.ExcavatorFilter;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

/**
 * m3 (SPEC §5): the IE excavator (and TFC-IE Crossover veins, which are mineral mixes too) only yields ores of
 * unlocked Ages. IE 12.4.2 [verified, javap]: {@code public ItemStack getRandomOre(java.util.Random)} and
 * {@code getRandomSpoil(java.util.Random)}; the caller is {@code ExcavatorLogic#fillBucket}.
 */
@Mixin(value = MineralMix.class, remap = false)
public abstract class MineralMixMixin {
    @Shadow
    public abstract ItemStack getRandomSpoil(Random rand);

    @Inject(method = "getRandomOre(Ljava/util/Random;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true)
    private void firmages$filterLockedOre(Random rand, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack rolled = cir.getReturnValue();
        ItemStack out = ExcavatorFilter.filter(rolled, () -> getRandomSpoil(rand));
        if (out != rolled) cir.setReturnValue(out);
    }
}
