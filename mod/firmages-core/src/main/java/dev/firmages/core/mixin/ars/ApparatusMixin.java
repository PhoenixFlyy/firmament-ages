package dev.firmages.core.mixin.ars;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.age.CacheGeneration;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * SPEC §4.6: the Ars Nouveau enchanting apparatus memoizes its recipe lookup per input for the lifetime of the
 * block entity (Ars 5.13.2 [verified, javap]: field {@code memo = Memoizer.memoize(<static lookup>)} set in the
 * constructor, read by {@code getRecipe(ItemStack, Player)}; {@code Memoizer} is a {@code ConcurrentHashMap}
 * {@code computeIfAbsent} cache). The constructor's lookup function is captured, and after an Age reload
 * ({@link CacheGeneration} changed) {@code memo} is replaced by a fresh memo of the same function. Targets by name,
 * so the build needs no Ars jar; applied only when Ars Nouveau is loaded.
 */
@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.common.block.tile.EnchantingApparatusTile", remap = false)
public abstract class ApparatusMixin {
    @Shadow
    Function<Object, Object> memo;

    @Unique
    private Function<Object, Object> firmages$lookup;
    @Unique
    private int firmages$cacheGeneration;

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
        target = "Lcom/hollingsworth/arsnouveau/common/util/Memoizer;memoize(Ljava/util/function/Function;)Ljava/util/function/Function;"))
    private Function<Object, Object> firmages$captureLookup(Function<Object, Object> lookup, Operation<Function<Object, Object>> original) {
        firmages$lookup = lookup;
        firmages$cacheGeneration = CacheGeneration.get();
        return original.call(lookup);
    }

    @Inject(method = "getRecipe(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Lcom/hollingsworth/arsnouveau/common/crafting/recipes/IEnchantingRecipe;",
        at = @At("HEAD"))
    private void firmages$flushStaleMemo(ItemStack stack, Player player, CallbackInfoReturnable<Object> cir) {
        int gen = CacheGeneration.get();
        if (gen != firmages$cacheGeneration && firmages$lookup != null) {
            firmages$cacheGeneration = gen;
            Function<Object, Object> lookup = firmages$lookup;
            Map<Object, Object> cache = new ConcurrentHashMap<>();
            memo = key -> cache.computeIfAbsent(key, lookup);
        }
    }
}
