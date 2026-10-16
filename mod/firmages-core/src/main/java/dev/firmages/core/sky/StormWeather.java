package dev.firmages.core.sky;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * The weather a Storm Rod sees (SPEC §26.5): the overworld's own rain and thunder flags, unless an operator forced a
 * storm with {@code /firmages sky storm rain|thunder|clear [seconds]}. TerraFirmaCraft drives the overworld's weather
 * from its climate model and refuses vanilla {@code /weather} (its {@code /time set rain} jumps the calendar instead),
 * so a test storm is a timed override that only the rods read; the world's weather stays TFC's. It applies to
 * overworld rods only, is not saved, and ends when the server stops.
 */
public final class StormWeather {
    private StormWeather() {}

    public enum Mode {
        AUTO, CLEAR, RAIN, THUNDER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static volatile Mode mode = Mode.AUTO;
    private static volatile long until;

    /** Forces {@code m} for {@code ticks} overworld ticks from now ({@link Mode#AUTO} ends an override). */
    public static void force(ServerLevel overworld, Mode m, long ticks) {
        mode = m;
        until = overworld.getGameTime() + ticks;
    }

    public static void reset() {
        mode = Mode.AUTO;
        until = 0;
    }

    /** The override in force for {@code level}, {@link Mode#AUTO} when none (or outside the overworld). */
    public static Mode active(Level level) {
        Mode m = mode;
        if (m == Mode.AUTO || level.dimension() != Level.OVERWORLD) return Mode.AUTO;
        if (level.getGameTime() >= until) {
            mode = Mode.AUTO;
            return Mode.AUTO;
        }
        return m;
    }

    /** Ticks the override still runs (0 without one). */
    public static long remaining(Level level) {
        return active(level) == Mode.AUTO ? 0 : until - level.getGameTime();
    }

    public static boolean raining(Level level) {
        Mode m = active(level);
        return m == Mode.AUTO ? level.isRaining() : m != Mode.CLEAR;
    }

    public static boolean thundering(Level level) {
        Mode m = active(level);
        return m == Mode.AUTO ? level.isThundering() : m == Mode.THUNDER;
    }
}
