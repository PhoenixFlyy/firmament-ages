package dev.firmages.core.sky;

import dev.firmages.core.FirmagesCore;
import net.neoforged.fml.ModList;

/** The installed {@link ClimateSource}: TFC's climate when TFC is loaded, else {@link ClimateSource#NONE}; tests replace it. */
public final class ClimateSources {
    private static volatile ClimateSource current;

    private ClimateSources() {}

    public static ClimateSource get() {
        ClimateSource c = current;
        if (c == null) {
            c = ModList.get() != null && ModList.get().isLoaded("tfc") ? new dev.firmages.core.compat.tfc.TfcClimateSource() : ClimateSource.NONE;
            current = c;
            FirmagesCore.LOGGER.debug("Sky climate source: {}", c.getClass().getSimpleName());
        }
        return c;
    }

    /** Test hook: install {@code source} (null restores the default on the next {@link #get}). */
    public static void set(ClimateSource source) {
        current = source;
    }
}
