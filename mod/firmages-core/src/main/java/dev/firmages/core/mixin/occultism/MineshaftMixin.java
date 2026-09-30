package dev.firmages.core.mixin.occultism;

import com.klikli_dev.occultism.common.blockentity.DimensionalMineshaftBlockEntity;
import dev.firmages.core.age.CacheGeneration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * SPEC §4.6: the Dimensional Mineshaft memoizes its miner results in {@code possibleResults} and only rebuilds
 * them when the field is null (Occultism 1.224.4 [verified, javap]: {@code mine()} queries the recipe manager
 * only on null). After an Age reload ({@link CacheGeneration} changed) the next tick clears it, so newly unlocked
 * ores appear without breaking the block.
 */
@Mixin(value = DimensionalMineshaftBlockEntity.class, remap = false)
public abstract class MineshaftMixin {
    @Shadow
    protected List<?> possibleResults;

    @Unique
    private int firmages$cacheGeneration;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void firmages$flushStaleResults(CallbackInfo ci) {
        int gen = CacheGeneration.get();
        if (gen != firmages$cacheGeneration) {
            firmages$cacheGeneration = gen;
            possibleResults = null;
        }
    }
}
