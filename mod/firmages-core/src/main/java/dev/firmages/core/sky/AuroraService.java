package dev.firmages.core.sky;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Server side of the aurora (SPEC §26.3): {@link AuroraRules} fed with the overworld's time and rain, the seed and the
 * operator override of {@code firmages_sky}, and both temperatures read through {@link ClimateSources} at the
 * reference height. The aurora is an overworld sky: other dimensions never have one.
 */
public final class AuroraService {
    private AuroraService() {}

    /** The verdict for the column (x, z) of {@code level}; outside the overworld it is never active. */
    public static AuroraRules.Result at(ServerLevel level, int x, int z) {
        MinecraftServer server = level.getServer();
        ServerLevel ow = server.overworld();
        SkySavedData sky = SkySavedData.get(server);
        AuroraRules.Override override = AuroraRules.Override.parse(sky.auroraOverride());
        if (level.dimension() != Level.OVERWORLD) {
            // never read the overworld's climate for another dimension's column (it would load that overworld chunk)
            return new AuroraRules.Result(false, AuroraRules.night(ow.getDayTime()), false, false, 0, 1, 0, ow.isRaining(), override);
        }
        SkyHarvestData.Aurora a = SkyHarvestDataLoader.current().aurora();
        ClimateSource climate = ClimateSources.get();
        float avg = climate.averageTemp(ow, x, a.referenceY(), z);
        float inst = climate.instantTemp(ow, x, a.referenceY(), z);
        return AuroraRules.evaluate(a, new AuroraRules.Input(sky.auroraSeed(), override, ow.getDayTime(), ow.isRaining(), avg, inst, x, z));
    }
}
