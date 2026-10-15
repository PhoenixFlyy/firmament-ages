package dev.firmages.core.compat.ie;

import blusunrize.immersiveengineering.api.excavator.ExcavatorHandler;
import blusunrize.immersiveengineering.api.excavator.MineralVein;
import blusunrize.immersiveengineering.common.IESaveData;
import dev.firmages.core.FirmagesCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The IE excavator veins of Starfall craters (SPEC §25.6). {@code ExcavatorHandler.addVein} puts the vein into IE's
 * list and resets its cache but does not mark IE's save data dirty [verified, javap 12.4.2], so every add is followed by
 * {@code IESaveData.markInstanceDirty()}. Loaded only with Immersive Engineering present.
 */
public final class StarfallVeins {
    public static final String MOD_ID = "immersiveengineering";

    private StarfallVeins() {}

    /** True when IE's overworld list holds a vein of {@code mix} at the column. */
    public static boolean present(int x, int z, ResourceLocation mix) {
        Collection<MineralVein> veins = ExcavatorHandler.getMineralVeinList().get(Level.OVERWORLD);
        synchronized (ExcavatorHandler.getMineralVeinList()) {
            for (MineralVein v : veins) {
                if (v.getPos().x() == x && v.getPos().z() == z && mix.equals(v.getMineralName())) return true;
            }
        }
        return false;
    }

    /** Adds the vein (idempotent: nothing happens when it is present) and marks IE's save data dirty. */
    public static boolean ensure(int x, int z, ResourceLocation mix, int radius) {
        if (present(x, z, mix)) return false;
        ExcavatorHandler.addVein(Level.OVERWORLD, new MineralVein(new ColumnPos(x, z), mix, radius));
        IESaveData.markInstanceDirty();
        FirmagesCore.LOGGER.info("Starfall: excavator vein {} (radius {}) at {}, {}", mix, radius, x, z);
        return true;
    }

    /** Removes every vein at the column (the GameTest takes one away by hand to see the reconcile restore it). */
    public static int remove(int x, int z) {
        List<MineralVein> gone = new ArrayList<>();
        synchronized (ExcavatorHandler.getMineralVeinList()) {
            for (MineralVein v : ExcavatorHandler.getMineralVeinList().get(Level.OVERWORLD)) {
                if (v.getPos().x() == x && v.getPos().z() == z) gone.add(v);
            }
            for (MineralVein v : gone) ExcavatorHandler.getMineralVeinList().remove(Level.OVERWORLD, v);
        }
        ExcavatorHandler.resetCache();
        IESaveData.markInstanceDirty();
        return gone.size();
    }

    /** The veins at the column, as "mix radius" lines. */
    public static List<String> describe(int x, int z) {
        List<String> out = new ArrayList<>();
        synchronized (ExcavatorHandler.getMineralVeinList()) {
            for (MineralVein v : ExcavatorHandler.getMineralVeinList().get(Level.OVERWORLD)) {
                if (v.getPos().x() == x && v.getPos().z() == z) out.add(v.getMineralName() + " r" + v.getRadius() + " depletion " + v.getDepletion());
            }
        }
        return out;
    }
}
