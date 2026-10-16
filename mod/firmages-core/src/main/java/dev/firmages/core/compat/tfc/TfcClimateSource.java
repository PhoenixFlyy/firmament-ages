package dev.firmages.core.compat.tfc;

import dev.firmages.core.sky.ClimateSource;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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

    /**
     * TFC's {@code ChunkData.get} loads the chunk ({@code level.getChunk}); far columns instead take a fresh chunk data
     * from the generator ({@code ChunkDataGenerator.createAndGenerate}, regions only) and TFC's elevation rule with
     * sea level 63, as {@code OverworldClimateModel.getAverageTemperature} does [javap, TFC 4.2.11].
     */
    @Override
    public float averageTempUnloaded(Level level, int x, int y, int z) {
        if (level.hasChunk(x >> 4, z >> 4)) return averageTemp(level, x, y, z);
        if (level instanceof ServerLevel sl && sl.getChunkSource().getGenerator() instanceof ChunkGeneratorExtension ext) {
            ChunkData d = ext.chunkDataGenerator().createAndGenerate(new ChunkPos(x >> 4, z >> 4));
            return Helpers.adjustAverageTemperatureByElevation(y, d.getAverageSeaLevelTemp(new BlockPos(x, y, z)), 63.0F);
        }
        return Float.NaN;
    }
}
