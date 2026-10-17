package dev.firmages.core.immersion;

/**
 * Visibility of the HUD Age indicator (SPEC §18.4). Pure Java (unit-tested); times in milliseconds.
 * <ul>
 *   <li>{@code ALWAYS}: always fully shown.</li>
 *   <li>{@code NEVER}: never shown, the key included.</li>
 *   <li>{@code FADE}: shown for {@code showMillis} after login or an Age change, the last {@link #FADE_OUT_MS} of it
 *       fading out; while the indicator key is held it is fully shown, and after release it stays for {@code showMillis}
 *       again (a tap shows it as long as a login does; round G2, Felix: the sign stands longer).</li>
 * </ul>
 */
public final class HudFade {
    public enum Mode { FADE, ALWAYS, NEVER }

    /** Length of the fade-out at the end of the shown time and after the key is released. */
    public static final long FADE_OUT_MS = 1000;

    private HudFade() {}

    /**
     * @param shownAt      when the indicator was last triggered (login, Age change), or {@code Long.MIN_VALUE} if never
     * @param keyReleaseAt when the key was last released, or {@code Long.MIN_VALUE} if never held (it stays {@code showMillis} after)
     * @return 0..1
     */
    public static float alpha(Mode mode, long now, long shownAt, long showMillis, boolean keyHeld, long keyReleaseAt) {
        return switch (mode) {
            case ALWAYS -> 1f;
            case NEVER -> 0f;
            case FADE -> keyHeld ? 1f : Math.max(window(now, shownAt, Math.max(0, showMillis)), window(now, keyReleaseAt, Math.max(0, showMillis)));
        };
    }

    /** 1 during {@code [start, start + hold)}, then a linear fade over {@link #FADE_OUT_MS}, else 0. */
    private static float window(long now, long start, long hold) {
        if (start == Long.MIN_VALUE || now < start) return 0f;
        long t = now - start;
        long fadeStart = Math.max(0, hold - FADE_OUT_MS);
        long end = Math.max(hold, FADE_OUT_MS);
        if (t < fadeStart) return 1f;
        if (t >= end) return 0f;
        return 1f - (float) (t - fadeStart) / (end - fadeStart);
    }
}
