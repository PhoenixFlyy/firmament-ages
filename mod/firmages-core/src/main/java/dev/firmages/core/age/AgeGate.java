package dev.firmages.core.age;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Runtime lock checks (SPEC §2.2): an entry is locked when an Age tag contains it and that Age is not unlocked in
 * the snapshot the current reload uses. Untagged entries are never locked.
 */
public final class AgeGate {
    private AgeGate() {}

    public static boolean isLocked(Item item) {
        return locked(AgeIndex.current().ageOf(item));
    }

    public static boolean isLocked(Block block) {
        return locked(AgeIndex.current().ageOf(block));
    }

    public static boolean isLocked(Fluid fluid) {
        return locked(AgeIndex.current().ageOf(fluid));
    }

    private static boolean locked(AgeId age) {
        return age != null && !AgeService.snapshotForReload().isUnlocked(age);
    }
}
