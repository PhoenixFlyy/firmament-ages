package dev.firmages.core.mixin.tfcrf;

import com.llamalad7.mixinextras.sugar.Local;
import dev.firmages.core.perf.RegrowthGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TFC Regrowing Forests 2.2 [verified, javap]: {@code RegrowthRecipe.tryToSpread(BlockState, Level, BlockPos,
 * RandomSource)}, called from a leaf's random tick, picks a column at a Gaussian offset (spread distance plus the
 * square of the leaf's height over the ground) and calls {@code Level.getHeight(MOTION_BLOCKING_NO_LEAVES, x, z)} on it
 * (the second {@code getHeight} of the method). That call loads and, if needed, generates the chunk on the main
 * thread; beside a forceloaded area with Chunky filling the worker queue one tick waited 120 s (round C2). The spread is
 * skipped when that chunk is not loaded ({@link RegrowthGuard}). Targets by name, so the build needs no jar; applied
 * only with the mod (package {@code tfcrf}).
 */
@Pseudo
@Mixin(targets = "tfcrf.common.recipes.RegrowthRecipe", remap = false)
public abstract class RegrowthSpreadMixin {
    @Inject(method = "tryToSpread(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I", ordinal = 1),
        cancellable = true)
    private static void firmages$onlyLoadedChunks(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci,
                                                  @Local(ordinal = 1) int x, @Local(ordinal = 2) int z) {
        if (!RegrowthGuard.mayLook(level, x, z)) ci.cancel();
    }
}
