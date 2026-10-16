package dev.firmages.core.mixin.ie;

import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockBEHelperMaster;
import blusunrize.immersiveengineering.common.blocks.multiblocks.blockimpl.MultiblockBEHelperDummy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The master helper of an IE multiblock part (IE 12.4.2 [verified, javap]: protected
 * {@code MultiblockBEHelperDummy#getIMasterHelper()}, null when the master is not loaded), so the Arcane Catalyst can
 * give the multiblock an extra work tick from any part it faces (SPEC §41.3). Applied only when IE is loaded.
 */
@Mixin(value = MultiblockBEHelperDummy.class, remap = false)
public interface MultiblockDummyInvoker {
    @Invoker("getIMasterHelper")
    IMultiblockBEHelperMaster<?> firmages$masterHelper();
}
