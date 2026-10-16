package dev.firmages.core.launch;

import dev.firmages.core.text.Numbers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Player text of the Launch Complex (SPEC §31.5), shared by the Jade lines (client, from the synced control) and the
 * actionbar at the control (server). Common code: no client classes.
 */
public final class LaunchText {
    private LaunchText() {}

    /** "The complex lacks three blocks." */
    public static MutableComponent lacks(int missing) {
        return missing == 1 ? Component.translatable("firmages.launch.lacks_one")
            : Component.translatable("firmages.launch.lacks", Numbers.words(Math.max(0, missing)));
    }

    /** Why the control waits. */
    public static MutableComponent waitLine(LaunchRules.Wait wait, int missing, int launchMb) {
        return switch (wait) {
            case NONE -> Component.translatable("firmages.launch.ready");
            case UNFORMED -> missing < 0 ? Component.translatable("firmages.launch.wait.unformed") : lacks(missing);
            case FUEL -> Component.translatable("firmages.launch.wait.fuel", launchMb);
            default -> Component.translatable("firmages.launch.wait." + wait.name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    /** What the probe does now ("Ignition in five seconds", "In flight, 45 %", ...). */
    public static MutableComponent phaseLine(LaunchRules.Phase phase, int countdownLeftTicks, int percent) {
        return switch (phase) {
            case IDLE -> Component.translatable("firmages.launch.phase.idle");
            case COUNTDOWN -> {
                int seconds = Math.max(1, (countdownLeftTicks + 19) / 20);
                yield seconds == 1 ? Component.translatable("firmages.launch.phase.countdown_one")
                    : Component.translatable("firmages.launch.phase.countdown", Numbers.words(seconds));
            }
            case ASCENT -> Component.translatable("firmages.launch.phase.ascent");
            case FLIGHT -> Component.translatable("firmages.launch.phase.flight", percent);
            case DESCENT -> Component.translatable("firmages.launch.phase.descent");
        };
    }

    /** "Throttle: two of three" (with "by redstone" when the back signal sets it). */
    public static MutableComponent throttle(int step, int steps, boolean redstone) {
        return Component.translatable(redstone ? "firmages.launch.throttle_redstone" : "firmages.launch.throttle", Numbers.words(step), Numbers.words(steps));
    }
}
