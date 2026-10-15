package dev.firmages.core.crown;

/**
 * The pure rules of the Lens Crown (SPEC §27.6): chord matching, the swing of a mount, the forge cycle with the night
 * factor, and the assignment of anvil slots to recipe ingredients. No Minecraft types, so JUnit runs them directly.
 */
public final class CrownRules {
    private CrownRules() {}

    // ---------------------------------------------------------------- chords

    /** A recipe matches only its exact chord: an extra lens in makes a different chord. */
    public static boolean chordMatches(int lensesIn, int recipeMask) {
        return recipeMask != 0 && lensesIn == recipeMask;
    }

    /**
     * The chord the mounts play: the set of lens indices that are fully in. Two copies of one lens count once.
     *
     * @param lenses lens index per mount (-1 = empty)
     * @param in     true when that mount's lens is fully in
     */
    public static int lensesIn(int[] lenses, boolean[] in) {
        int mask = 0;
        for (int i = 0; i < lenses.length; i++) if (in[i] && lenses[i] >= 0 && lenses[i] < Chord.LENSES) mask |= 1 << lenses[i];
        return mask;
    }

    /** The lowest lens the chord needs that is not in, or -1. */
    public static int firstMissing(int lensesIn, int recipeMask) {
        int m = recipeMask & ~lensesIn;
        return m == 0 ? -1 : Integer.numberOfTrailingZeros(m);
    }

    /** The lowest lens that is in but not part of the chord, or -1. */
    public static int firstExtra(int lensesIn, int recipeMask) {
        int m = lensesIn & ~recipeMask;
        return m == 0 ? -1 : Integer.numberOfTrailingZeros(m);
    }

    // ---------------------------------------------------------------- swing

    /** One tick of a mount's swing: toward {@code max} while powered, toward 0 otherwise. */
    public static int swing(int progress, boolean powered, int max) {
        if (powered) return Math.min(max, progress + 1);
        return Math.max(0, Math.min(progress, max) - 1);
    }

    /** A lens counts only once it is fully in. */
    public static boolean fullyIn(int progress, int max) {
        return progress >= max;
    }

    // ---------------------------------------------------------------- forge cycle

    /** Why the anvil does not forge (or that it does). The order is the order of the checks. */
    public enum Reason {
        DISABLED, UNFORMED, NO_RECIPE, WRONG_CHORD, NO_FLUID, OUTPUT_FULL, SKY_HIDDEN, FORGING;

        public boolean forging() {
            return this == FORGING;
        }
    }

    /**
     * What the anvil sees this tick.
     *
     * @param enabled     {@code crown.enabled}
     * @param formed      the crown validates
     * @param recipe      id of the matching recipe, or null
     * @param recipeMask  its chord
     * @param time        its forge time in ticks
     * @param lensesIn    the chord the mounts play now
     * @param fluidOk     the tank holds the recipe's fluid (or it needs none)
     * @param outputOk    the output slot can take the result
     * @param skyOpen     {@code canSeeSky} above the anvil, or the sky rule is off
     * @param night       night under an open sky
     * @param nightFactor {@code crown.nightTimeFactor}
     */
    public record View(boolean enabled, boolean formed, String recipe, int recipeMask, int time, int lensesIn, boolean fluidOk,
                       boolean outputOk, boolean skyOpen, boolean night, double nightFactor) {}

    /**
     * The anvil's forge state between ticks.
     *
     * @param recipe   the recipe the progress belongs to, or null
     * @param lensesIn the chord of the last tick
     * @param progress ticks of work done (a night tick counts {@code 1 / nightFactor})
     */
    public record Forge(String recipe, int lensesIn, double progress) {
        public static final Forge IDLE = new Forge(null, 0, 0);
    }

    /** One tick: the next state, why, and whether the forging completed in this tick. */
    public record Step(Forge next, Reason reason, boolean done) {}

    /** How much one tick counts (night is faster by {@code 1 / nightFactor}). */
    public static double tickWork(boolean night, double nightFactor) {
        if (!night) return 1.0;
        double f = Math.max(0.1, Math.min(1.0, nightFactor));
        return 1.0 / f;
    }

    /** Ticks a forging takes in the given light (whole ticks, rounded up). */
    public static int ticksFor(int time, boolean night, double nightFactor) {
        return (int) Math.ceil(time / tickWork(night, nightFactor) - 1e-9);
    }

    /**
     * The forge cycle (SPEC §27.6): a changed recipe or chord resets the progress to 0; a covered sky, a missing fluid
     * or a full output pause it; nothing consumes items but a completed forging.
     */
    public static Step tick(Forge prev, View v) {
        Forge base = prev;
        if (v.recipe() == null || !v.recipe().equals(prev.recipe()) || v.lensesIn() != prev.lensesIn()) {
            base = new Forge(v.recipe(), v.lensesIn(), 0);
        }
        Reason r;
        if (!v.enabled()) r = Reason.DISABLED;
        else if (!v.formed()) r = Reason.UNFORMED;
        else if (v.recipe() == null) r = Reason.NO_RECIPE;
        else if (!chordMatches(v.lensesIn(), v.recipeMask())) r = Reason.WRONG_CHORD;
        else if (!v.fluidOk()) r = Reason.NO_FLUID;
        else if (!v.outputOk()) r = Reason.OUTPUT_FULL;
        else if (!v.skyOpen()) r = Reason.SKY_HIDDEN;
        else r = Reason.FORGING;
        if (r != Reason.FORGING) return new Step(base, r, false);
        double p = base.progress() + tickWork(v.night(), v.nightFactor());
        if (p >= v.time() - 1e-9) return new Step(new Forge(v.recipe(), v.lensesIn(), 0), r, true);
        return new Step(new Forge(v.recipe(), v.lensesIn(), p), r, false);
    }

    /** Progress in whole percent (0..100). */
    public static int percent(double progress, int time) {
        if (time <= 0) return 0;
        return (int) Math.max(0, Math.min(100, Math.floor(progress * 100.0 / time)));
    }

    // ---------------------------------------------------------------- slots and ingredients

    /** Whether filled slot {@code slot} may stand for ingredient {@code ingredient}. */
    @FunctionalInterface
    public interface Fits {
        boolean test(int slot, int ingredient);
    }

    /**
     * Assigns every filled slot to its own ingredient (a bijection when {@code complete}: every ingredient is covered).
     *
     * @param filled      indices of the non-empty slots
     * @param ingredients number of ingredients of the recipe
     * @return assignment slot index (position in {@code filled}) -> ingredient, or null when none exists
     */
    public static int[] assign(int[] filled, int ingredients, boolean complete, Fits fits) {
        if (filled.length > ingredients || (complete && filled.length != ingredients)) return null;
        int[] out = new int[filled.length];
        boolean[] used = new boolean[ingredients];
        return search(filled, 0, ingredients, used, out, fits) ? out : null;
    }

    private static boolean search(int[] filled, int i, int n, boolean[] used, int[] out, Fits fits) {
        if (i == filled.length) return true;
        for (int g = 0; g < n; g++) {
            if (used[g] || !fits.test(filled[i], g)) continue;
            used[g] = true;
            out[i] = g;
            if (search(filled, i + 1, n, used, out, fits)) return true;
            used[g] = false;
        }
        return false;
    }

    /** Indices of the slots that are not empty. */
    public static int[] filled(boolean[] empty) {
        return java.util.stream.IntStream.range(0, empty.length).filter(i -> !empty[i]).toArray();
    }
}
