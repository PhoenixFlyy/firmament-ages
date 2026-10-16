package dev.firmages.core.anchor;

import dev.firmages.core.shrine.BlessingSources;

/**
 * The rules of a Sky Anchor (SPEC-himmel §29.3, §29.5), pure Java so they are unit-tested. An anchor holds at most one
 * shard per tier 0..8, kept as a bit mask (bit k = the shard of tier k). It acts from the Space Age while the home
 * shrine is intact, and only with the shards whose tiers are awakened.
 */
public final class AnchorRules {
    /** The Age from which anchors act. */
    public static final String FROM_AGE = "age_7";

    private AnchorRules() {}

    /** What an anchor does now, in the order the reasons are checked. */
    public enum Status {
        /** It carries at least one blessing. */
        ACTIVE,
        /** The Space Age is not open (or was revoked). */
        BEFORE_AGE,
        /** The home shrine is broken or missing, or no tier is awakened. */
        BROKEN,
        /** It holds no shard. */
        EMPTY,
        /** It holds shards, but none of an awakened tier. */
        SLEEPING
    }

    /** True when the mask holds the shard of {@code tier}. */
    public static boolean holds(int tiers, int tier) {
        return tier >= 0 && tier < BlessingSources.TIERS && (tiers & (1 << tier)) != 0;
    }

    /** The mask with the shard of {@code tier} added. */
    public static int with(int tiers, int tier) {
        return tier < 0 || tier >= BlessingSources.TIERS ? tiers : tiers | (1 << tier);
    }

    /** The highest tier held, -1 for none (sneak-use takes this one back first). */
    public static int highest(int tiers) {
        int masked = tiers & BlessingSources.allTiers(BlessingSources.TIERS);
        return masked == 0 ? -1 : 31 - Integer.numberOfLeadingZeros(masked);
    }

    /** The number of shards held. */
    public static int count(int tiers) {
        return Integer.bitCount(tiers & BlessingSources.allTiers(BlessingSources.TIERS));
    }

    /** The tiers that act now: the shards held AND the awakened tiers, 0 while the anchor is quiet. */
    public static int activeMask(boolean ageOpen, boolean shrineIntact, int awakened, int tiers) {
        if (!ageOpen || !shrineIntact) return 0;
        return tiers & BlessingSources.allTiers(awakened);
    }

    public static Status status(boolean ageOpen, boolean shrineIntact, int awakened, int tiers) {
        if (!ageOpen) return Status.BEFORE_AGE;
        if (!shrineIntact || awakened <= 0) return Status.BROKEN;
        if (count(tiers) == 0) return Status.EMPTY;
        return activeMask(true, true, awakened, tiers) == 0 ? Status.SLEEPING : Status.ACTIVE;
    }
}
