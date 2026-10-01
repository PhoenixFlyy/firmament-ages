package dev.firmages.core.shrine;

import dev.firmages.core.age.AgeId;

import java.util.Optional;
import java.util.Set;

/**
 * Pure shrine rules (SPEC §7.3), unit-tested without Minecraft.
 * <ul>
 *   <li>ProgressiveStages (through AgeState) is the only truth for progress: the shrine works on the tier of the
 *       highest unlocked Age and stores no competing counter.</li>
 *   <li>Prayer: per tick the progress grows by the number of praying players and decays by 1 when nobody prays;
 *       it is complete at {@code max(prayerTicks, n * minPrayerTicks)}, which gives 10 s / n with a 4 s minimum.</li>
 * </ul>
 */
public final class ShrineRules {
    private ShrineRules() {}

    /** Highest unlocked Age (dawn when nothing else). */
    public static AgeId highest(Set<AgeId> unlocked) {
        AgeId best = AgeId.DAWN;
        for (AgeId a : unlocked) {
            if (a.ordinal() > best.ordinal()) best = a;
        }
        return best;
    }

    /**
     * The tier the shrine works on: N while {@code age_N} (N = 0..8) is the highest unlocked Age. Empty at Dawn
     * (the First Spark is a quest) and from age_9 on (no Age left to grant).
     */
    public static Optional<Integer> currentTier(Set<AgeId> unlocked) {
        AgeId h = highest(unlocked);
        int n = h.ordinal() - 1; // AGE_0 has ordinal 1
        return n >= 0 && n <= ShrineData.MAX_TIER ? Optional.of(n) : Optional.empty();
    }

    /** Tier that grants {@code age}: age_(N+1) comes from tier N. Empty for dawn and age_0. */
    public static Optional<Integer> tierGranting(AgeId age) {
        int n = age.ordinal() - 2;
        return n >= 0 && n <= ShrineData.MAX_TIER ? Optional.of(n) : Optional.empty();
    }

    /** Heart blockstate {@code awakened}: the number of tiers whose Age is granted (age_1 = 1 ... age_9 = 9). */
    public static int awakened(Set<AgeId> unlocked) {
        return Math.max(0, highest(unlocked).ordinal() - 1);
    }

    /** Progress needed with {@code praying} players. */
    public static int prayerTarget(int prayerTicks, int minPrayerTicks, int praying) {
        return Math.max(prayerTicks, Math.max(1, praying) * minPrayerTicks);
    }

    /** One tick of prayer: grows by the praying count, decays by 1 without prayers, never below 0. */
    public static int stepProgress(int progress, int praying) {
        return praying > 0 ? progress + praying : Math.max(0, progress - 1);
    }

    /** Ticks a prayer of {@code praying} players takes from zero. */
    public static int prayerDuration(int prayerTicks, int minPrayerTicks, int praying) {
        int target = prayerTarget(prayerTicks, minPrayerTicks, praying);
        return (target + praying - 1) / praying;
    }

    /** Sanctuary radius for {@code awakened} tiers (0 = no sanctuary). */
    public static int sanctuaryRadius(int awakened, int base, int perTier) {
        return awakened <= 0 ? 0 : base + perTier * (awakened - 1);
    }

    /** Text progress bar, {@code width} cells. */
    public static String bar(int progress, int target, int width) {
        int filled = target <= 0 ? width : (int) Math.min(width, (long) progress * width / target);
        return "▮".repeat(filled) + "▯".repeat(width - filled);
    }
}
