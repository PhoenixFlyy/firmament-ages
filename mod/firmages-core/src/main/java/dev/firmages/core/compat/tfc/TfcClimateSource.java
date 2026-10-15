package dev.firmages.core.compat.tfc;

import dev.firmages.core.sky.ClimateSource;
import net.dries007.tfc.util.climate.Climate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * TerraFirmaCraft's climate for the sky features (SPEC §24.4). {@code Climate} works on the server and, for loaded
 * chunks, on the client (TFC syncs its climate model and chunk data) [verified]. Loaded only with TFC present.
 */
public final class TfcClimateSource implements ClimateSource {
    @Override
    public float averageTemp(Level level, int x, int y, int z) {
        return Climate.getAverageTemperature(level, new BlockPos(x, y, z));
    }

    @Override
    public float instantTemp(Level level, int x, int y, int z) {
        return Climate.getInstantTemperature(level, new BlockPos(x, y, z));
    }
}
