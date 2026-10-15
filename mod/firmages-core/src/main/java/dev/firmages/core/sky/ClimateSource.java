package dev.firmages.core.sky;

import net.minecraft.world.level.Level;

/**
 * Temperatures for the sky features (SPEC §24.4), in °C. The pack reads TerraFirmaCraft's climate
 * ({@code compat/tfc/TfcClimateSource}); the GameTest run has no TFC and installs a source whose values the test sets.
 * Common code never touches TFC classes. Both values are read at a fixed height chosen by the caller (the aurora reads
 * them at its {@code reference_y}, SPEC §26.3), never at the camera's or a block's own height.
 */
public interface ClimateSource {
    /** Yearly average temperature at the column, at height {@code y}. */
    float averageTemp(Level level, int x, int y, int z);

    /** Temperature right now (season and time of day included) at the column, at height {@code y}. */
    float instantTemp(Level level, int x, int y, int z);

    /** No climate model: a mild constant (vanilla worlds without TFC and without a test source). */
    ClimateSource NONE = new ClimateSource() {
        @Override
        public float averageTemp(Level level, int x, int y, int z) {
            return 10.0F;
        }

        @Override
        public float instantTemp(Level level, int x, int y, int z) {
            return 10.0F;
        }
    };
}
