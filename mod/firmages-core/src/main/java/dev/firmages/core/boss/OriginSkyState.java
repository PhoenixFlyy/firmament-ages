package dev.firmages.core.boss;

/**
 * The Origin's sky by the fight (round G3, SPEC §16.6), pure Java: which mood the sky aims for and how far each mood
 * is blended in. Phase 1 or no fight: a clear starry sky; phase 2: aurora bands in the Lens Crown's colours; phase 3:
 * an eclipse with red edges and a slowly tilting constellation; the boss's death: dawn for two minutes. Every change
 * eases over {@link #FADE_TICKS} (2 s). The client feeds it once a tick ({@code client/OriginSky}).
 */
public final class OriginSkyState {
    public enum Mood { CLEAR, AURORA, ECLIPSE, DAWN }

    public static final int FADE_TICKS = 40;
    /** Dawn lasts this long after the boss falls. */
    public static final int DAWN_TICKS = 2400;

    private final float[] weight = {1, 0, 0, 0};
    private long dawnUntil = Long.MIN_VALUE;
    private Mood target = Mood.CLEAR;

    /** The mood for a boss in {@code phase} (0 = none near), {@code dying} once it falls, at game time {@code now}. */
    public Mood aim(int phase, boolean dying, long now) {
        if (dying) dawnUntil = now + DAWN_TICKS;
        if (now < dawnUntil) return Mood.DAWN;
        return switch (phase) {
            case 2 -> Mood.AURORA;
            case 3 -> Mood.ECLIPSE;
            default -> Mood.CLEAR;
        };
    }

    /** One tick: aims at the mood of the fight and moves every weight a fortieth toward it. */
    public void tick(int phase, boolean dying, long now) {
        target = aim(phase, dying, now);
        float step = 1F / FADE_TICKS;
        for (Mood m : Mood.values()) {
            float goal = m == target ? 1 : 0;
            float w = weight[m.ordinal()];
            weight[m.ordinal()] = w < goal ? Math.min(goal, w + step) : Math.max(goal, w - step);
        }
    }

    public float weight(Mood m) {
        return weight[m.ordinal()];
    }

    public Mood target() {
        return target;
    }

    /** Leaving The Origin: back to a clear sky at once. */
    public void clear() {
        java.util.Arrays.fill(weight, 0);
        weight[Mood.CLEAR.ordinal()] = 1;
        dawnUntil = Long.MIN_VALUE;
        target = Mood.CLEAR;
    }
}
