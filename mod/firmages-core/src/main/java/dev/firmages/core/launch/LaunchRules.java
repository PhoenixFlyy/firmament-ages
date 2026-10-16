package dev.firmages.core.launch;

import java.util.List;

/**
 * The pure rules of the Launch Complex (SPEC §31.4, §31.5): the phases, the throttle steps and their redstone mapping,
 * the flight as work on fuel, the order of the start checks and the probe's height over the cradle. No Minecraft
 * types, so JUnit runs them directly.
 */
public final class LaunchRules {
    private LaunchRules() {}

    /** Ticks of the climb on the flame trail. */
    public static final int ASCENT_TICKS = 200;
    /** Ticks of the fall under the canopies. */
    public static final int DESCENT_TICKS = 160;
    /** Blocks the probe climbs above the cradle before it leaves the sight. */
    public static final int RISE = 300;

    /** The mission phases. {@code COUNTDOWN} lives on the control only: nothing is taken before ignition. */
    public enum Phase {
        IDLE, COUNTDOWN, ASCENT, FLIGHT, DESCENT;

        public static Phase byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : IDLE;
        }

        /** The probe is away from the cradle (ignition done, not yet unloaded). */
        public boolean inMission() {
            return this == ASCENT || this == FLIGHT || this == DESCENT;
        }
    }

    /** One throttle step: fuel burnt and progress gained per tick. */
    public record Step(int burn, int progress) {}

    /** Why the control does not launch or why a running mission stands still. The order is the order of the checks. */
    public enum Wait {
        NONE, DISABLED, UNFORMED, NO_CRADLE, PORTS, TRENCH, NO_PROBE, NO_MODULE, FUEL, OUTPUT_FULL, SKY_CLOSED, HELD, FUEL_EMPTY;

        public static Wait byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : NONE;
        }
    }

    /**
     * What the control sees in {@code IDLE} (SPEC §31.5 start conditions).
     *
     * @param enabled {@code launch.enabled}
     * @param formed  the pattern stands around the cradle and this control sits at the tower's foot
     * @param cradle  a probe cradle stands three blocks before the control
     * @param ports   launch ports on the pad rim
     * @param trench  the flame trench is open under the pad
     * @param probe   a probe in its slot
     * @param module  a module with a loaded module file in its slot
     * @param fuel    the tank holds {@code launch_mb}
     * @param room    the output takes the module's returns (and the probe, without auto relaunch)
     * @param sky     open sky from the cradle to the build limit
     * @param held    a redstone hold on the control
     */
    public record Start(boolean enabled, boolean cradle, boolean formed, int ports, boolean trench, boolean probe, boolean module, boolean fuel,
                        boolean room, boolean sky, boolean held) {}

    /** The first unmet start condition, or {@link Wait#NONE}. */
    public static Wait startWait(Start s) {
        if (!s.enabled()) return Wait.DISABLED;
        if (!s.cradle()) return Wait.NO_CRADLE;
        if (!s.formed()) return Wait.UNFORMED;
        if (!portsOk(s.ports())) return Wait.PORTS;
        if (!s.trench()) return Wait.TRENCH;
        if (!s.probe()) return Wait.NO_PROBE;
        if (!s.module()) return Wait.NO_MODULE;
        if (!s.fuel()) return Wait.FUEL;
        if (!s.room()) return Wait.OUTPUT_FULL;
        if (!s.sky()) return Wait.SKY_CLOSED;
        if (s.held()) return Wait.HELD;
        return Wait.NONE;
    }

    /** One to four launch ports. */
    public static boolean portsOk(int ports) {
        return ports >= 1 && ports <= 4;
    }

    /**
     * The throttle step a redstone strength on the control's back selects: 0 = none (the hand setting holds), 1..15
     * split evenly over the steps (three steps: 1..5, 6..10, 11..15).
     *
     * @return the step (1-based), or 0 for no signal
     */
    public static int stepFromRedstone(int strength, int steps) {
        if (strength <= 0 || steps <= 0) return 0;
        int s = Math.min(15, strength);
        return Math.max(1, Math.min(steps, (s * steps + 14) / 15));
    }

    /** The step after {@code step} when the control is right-clicked (1-based, wraps). */
    public static int nextStep(int step, int steps) {
        if (steps <= 0) return 1;
        return step >= steps || step < 1 ? 1 : step + 1;
    }

    /** The effective step: redstone wins over the hand setting; both clamped to 1..steps. */
    public static int effectiveStep(int manual, int redstone, int steps) {
        int s = redstone > 0 ? redstone : manual;
        return Math.max(1, Math.min(Math.max(1, steps), s));
    }

    /** Ticks a flight of {@code work} takes at {@code step} with a full tank. */
    public static long flightTicks(int work, Step step) {
        if (step.progress() <= 0) return Long.MAX_VALUE;
        return (work + (long) step.progress() - 1) / step.progress();
    }

    /** Fuel a flight of {@code work} burns at {@code step}. */
    public static long flightFuel(int work, Step step) {
        return flightTicks(work, step) * step.burn();
    }

    /** One flight tick: the progress after it, fuel burnt, and whether the flight is done or waits on fuel. */
    public record FlightTick(long progress, int burnt, boolean done, boolean paused) {}

    /**
     * A tick of {@code FLIGHT} (SPEC §31.4): burns {@code step.burn} mB and adds {@code step.progress}; with less fuel
     * than one tick needs the flight pauses and nothing is lost.
     */
    public static FlightTick flight(long progress, int work, long fuel, Step step) {
        if (progress >= work) return new FlightTick(progress, 0, true, false);
        if (fuel < step.burn()) return new FlightTick(progress, 0, false, true);
        long p = Math.min(work, progress + step.progress());
        return new FlightTick(p, step.burn(), p >= work, false);
    }

    /** Flight progress in whole percent (0..100). */
    public static int percent(long progress, int work) {
        if (work <= 0) return 0;
        return (int) Math.max(0, Math.min(100, progress * 100 / work));
    }

    /** Height of the probe over the cradle during {@code ASCENT} after {@code t} ticks: slow off the pad, then fast. */
    public static double ascentHeight(double t) {
        double u = Math.max(0, Math.min(1, t / ASCENT_TICKS));
        return RISE * u * u;
    }

    /** Height of the probe over the cradle during {@code DESCENT} after {@code t} ticks: fast from the sky, soft at the pad. */
    public static double descentHeight(double t) {
        double u = Math.max(0, Math.min(1, t / DESCENT_TICKS));
        return RISE * (1 - u) * (1 - u);
    }

    /** The throttle steps of a fuel file, or the spec default when none is loaded. */
    public static List<Step> defaultSteps() {
        return List.of(new Step(1, 1), new Step(3, 2), new Step(8, 4));
    }
}
