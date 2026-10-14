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

    // ---------------------------------------------------------------- the Gathering (SPEC §7.5)

    /** Why the Gathering prayer cannot be heard now, in the order the shrine checks it. */
    public enum GatheringBlock { NOT_SINGULARITY, DONE, MAINTENANCE, BROKEN, RELIC_LENT, RELIC_MISSING }

    /**
     * The Gathering's conditions: {@code age_9} is the highest Age, it has not happened yet, no maintenance, the
     * shrine is intact (rings 0..8), no relic is lent, and the relic of every tier 0..8 rests on its plinth.
     *
     * @param present tiers whose relic rests on its plinth now
     * @param lent    tiers whose relic is lent out
     */
    public static Optional<GatheringBlock> gatheringBlock(Set<AgeId> unlocked, boolean gathered, boolean maintenance, boolean intact,
                                                          Set<Integer> present, Set<Integer> lent) {
        if (highest(unlocked) != AgeId.AGE_9) return Optional.of(GatheringBlock.NOT_SINGULARITY);
        if (gathered) return Optional.of(GatheringBlock.DONE);
        if (maintenance) return Optional.of(GatheringBlock.MAINTENANCE);
        if (!intact) return Optional.of(GatheringBlock.BROKEN);
        if (!lent.isEmpty()) return Optional.of(GatheringBlock.RELIC_LENT);
        return firstMissingRelic(present).isPresent() ? Optional.of(GatheringBlock.RELIC_MISSING) : Optional.empty();
    }

    /** The lowest tier 0..8 whose relic is not in {@code present}. */
    public static Optional<Integer> firstMissingRelic(Set<Integer> present) {
        for (int k = 0; k <= ShrineData.MAX_TIER; k++) {
            if (!present.contains(k)) return Optional.of(k);
        }
        return Optional.empty();
    }

    /** Players the Gathering prayer needs together: two once two or more are online, else one. */
    public static int gatheringChorus(int online) {
        return online >= 2 ? 2 : 1;
    }

    /** Heart blockstate {@code awakened}: the awakened tiers, 10 once the Gathering is done. */
    public static int heartAwakened(int awakened, boolean gathered) {
        return gathered && awakened >= ShrineData.MAX_TIER + 1 ? 10 : Math.min(ShrineData.MAX_TIER + 1, Math.max(0, awakened));
    }

    // ---------------------------------------------------------------- the living shrine (SPEC §7.10)

    /** Colour of the idle beam after the finale ("Crowned"): the finale's gold. */
    public static final int FINALE_BEAM = 0xFFFFE9A8;
    /** Alpha of the idle beam in an Age's colour. */
    public static final int IDLE_BEAM_ALPHA = 0x90;

    /**
     * The idle beam's ARGB colour, or 0 for no beam: none before the first awakening, while the heart is cold or
     * while the shrine is broken; the colour of the latest awakened tier's ceremony beam; the finale's gold after
     * the finale.
     *
     * @param tierColors ARGB beam colour of tier 0..8 (the ceremony beam of the Age it granted)
     */
    public static int idleBeamColor(int awakened, boolean intact, boolean lit, boolean finale, int[] tierColors) {
        if (awakened <= 0 || !intact || !lit) return 0;
        if (finale) return FINALE_BEAM;
        int k = Math.min(awakened, tierColors.length) - 1;
        if (k < 0) return 0;
        return (IDLE_BEAM_ALPHA << 24) | (tierColors[k] & 0xFFFFFF);
    }

    /** Text progress bar, {@code width} cells. */
    public static String bar(int progress, int target, int width) {
        int filled = target <= 0 ? width : (int) Math.min(width, (long) progress * width / target);
        return "▮".repeat(filled) + "▯".repeat(width - filled);
    }
}
