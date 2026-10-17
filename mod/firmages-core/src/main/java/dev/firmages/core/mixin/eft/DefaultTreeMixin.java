package dev.firmages.core.mixin.eft;

import dev.firmages.core.compat.eft.TreeFamily;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enhanced Falling Trees 0.7.0 fells whole TFC trees (SPEC §20.2). [verified, javap of efallingtrees-0.7.0-1.21.1+neoforge]
 * {@code DefaultTree.gatherTreeStructure} adds a block when {@code isMatchingLog(state, initialState)} holds, which is
 * {@code baseBlockCheck(state)} (the log filter, {@code #minecraft:logs} by default) and the same {@code Block} as the
 * broken one; a TFC tree's branches are a different block of the same species ({@link TreeFamily}), so they stayed in
 * the air. {@code isWithinDistance} caps the height above the broken block at the constant 32 (both {@code bipush 32}).
 * Targets by name, so the build needs no jar; applied only with the mod (package {@code eft}).
 */
@Pseudo
@Mixin(targets = "me.adda.enhanced_falling_trees.trees.DefaultTree", remap = false)
public abstract class DefaultTreeMixin {
    @Shadow
    public abstract boolean baseBlockCheck(BlockState state);

    @Inject(method = "isMatchingLog(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z",
        at = @At("RETURN"), cancellable = true)
    private void firmages$wholeTree(BlockState state, BlockState initial, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !baseBlockCheck(state)) return;
        if (TreeFamily.sameTree(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                BuiltInRegistries.BLOCK.getKey(initial.getBlock()).toString())) cir.setReturnValue(true);
    }

    @ModifyConstant(method = "isWithinDistance(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;II)Z",
        constant = @Constant(intValue = 32))
    private int firmages$tallTrees(int max) {
        return TreeFamily.MAX_HEIGHT;
    }
}
