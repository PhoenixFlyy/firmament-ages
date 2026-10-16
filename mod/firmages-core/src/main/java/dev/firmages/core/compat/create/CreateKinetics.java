package dev.firmages.core.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Create's rotation speed at a block (the Archipelago's {@code kinetic} work rule, SPEC §31.8). Loaded only with Create. */
public final class CreateKinetics {
    private CreateKinetics() {}

    /** RPM of the kinetic block entity at {@code pos}, 0 for none. */
    public static double speed(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KineticBlockEntity k ? Math.abs(k.getSpeed()) : 0;
    }
}
