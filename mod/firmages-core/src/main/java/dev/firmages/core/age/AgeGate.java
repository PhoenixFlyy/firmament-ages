package dev.firmages.core.age;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Runtime lock checks (SPEC §2.2): an entry is locked when an Age tag contains it and that Age is not unlocked in
 * the snapshot the current reload uses, or when it is in {@code age_items/disabled} / {@code age_fluids/disabled}
 * ("never"). Untagged entries are never locked.
 */
public final class AgeGate {
    private AgeGate() {}

    public static boolean isLocked(Item item) {
        AgeIndex idx = AgeIndex.current();
        return idx.isDisabled(item) || locked(idx.ageOf(item));
    }

    public static boolean isLocked(Block block) {
        return locked(AgeIndex.current().ageOf(block));
    }

    public static boolean isLocked(Fluid fluid) {
        AgeIndex idx = AgeIndex.current();
        return idx.isDisabled(fluid) || locked(idx.ageOf(fluid));
    }

    private static boolean locked(AgeId age) {
        return age != null && !AgeService.snapshotForReload().isUnlocked(age);
    }
}
