package dev.firmages.core.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Moving Create contraptions for the silo launch (SPEC §22.4). Create 6.0.10 [verified, javap]: an assembled contraption
 * is an {@code AbstractContraptionEntity} (piston, gantry, bearing, pulley) whose {@code getContraption().getBlocks()}
 * holds every moved block. Only loaded when Create is present.
 */
public final class CreateHatches {
    private CreateHatches() {}

    /** The block count of the largest contraption touching {@code box}, 0 for none. */
    public static int largest(ServerLevel level, AABB box) {
        int max = 0;
        for (AbstractContraptionEntity e : level.getEntitiesOfClass(AbstractContraptionEntity.class, box)) {
            if (e.getContraption() != null) max = Math.max(max, e.getContraption().getBlocks().size());
        }
        return max;
    }
}
