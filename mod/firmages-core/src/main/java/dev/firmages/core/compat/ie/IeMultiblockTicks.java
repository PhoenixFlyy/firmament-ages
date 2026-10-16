package dev.firmages.core.compat.ie;

import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockBEHelper;
import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockBEHelperMaster;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE;
import dev.firmages.core.mixin.ie.MultiblockDummyInvoker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * One extra work tick of an Immersive Engineering multiblock for the Arcane Catalyst (SPEC §41.3): a part forwards to
 * its master helper, whose {@code tickServer()} runs the multiblock's logic once (IE 12.4.2 [verified, javap]). Loaded
 * only with IE.
 */
public final class IeMultiblockTicks {
    private IeMultiblockTicks() {}

    /** True when {@code be} is an IE multiblock and its master ticked once. */
    public static boolean tick(Level level, BlockEntity be) {
        if (!(be instanceof IMultiblockBE<?> mb)) return false;
        IMultiblockBEHelper<?> helper = mb.getHelper();
        IMultiblockBEHelperMaster<?> master = helper instanceof IMultiblockBEHelperMaster<?> m ? m
            : helper instanceof MultiblockDummyInvoker d ? d.firmages$masterHelper() : null;
        if (master == null) return false;
        master.tickServer();
        return true;
    }
}
