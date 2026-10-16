package dev.firmages.core.compat.create;

import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Create probes of the energy reference runs ({@code balance/ReferenceRun}, harmony package A8): a fluid tank reports
 * its boiler (Create 6.0.10 [verified, javap {@code BoilerData}]: heat, water supply, size, engines and the level), a
 * kinetic block its speed and stress and, through {@link AlternatorFuel}, its network's fuelled share. Loaded only
 * with Create.
 */
public final class CreateReference {
    private CreateReference() {}

    @Nullable
    public static String probe(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FluidTankBlockEntity tank) {
            FluidTankBlockEntity c = tank.getControllerBE();
            if (c == null) return "fluid tank without controller";
            BoilerData b = c.boiler;
            int size = c.getTotalTankSize();
            return String.format(Locale.ROOT, "boiler: level %d (size %d, water %d, heat %d), active heat %d, passive %s, water supply %.1f mB/t, engines %d, "
                    + "tank %d blocks, %d mB",
                Math.min(b.activeHeat, Math.min(b.getMaxHeatLevelForBoilerSize(size), b.getMaxHeatLevelForWaterSupply())), b.getMaxHeatLevelForBoilerSize(size),
                b.getMaxHeatLevelForWaterSupply(), b.activeHeat, b.activeHeat, b.passiveHeat,
                b.waterSupply, b.attachedEngines, size, c.getTankInventory().getFluidAmount());
        }
        if (be instanceof KineticBlockEntity k) {
            String share = AlternatorFuel.describe(level, pos);
            return String.format(Locale.ROOT, "speed %.1f rpm, stress applied %.1f, capacity added %.1f, overstressed %s; %s", k.getSpeed(),
                k.calculateStressApplied(), k.calculateAddedStressCapacity(), k.isOverStressed(), share);
        }
        return null;
    }
}
