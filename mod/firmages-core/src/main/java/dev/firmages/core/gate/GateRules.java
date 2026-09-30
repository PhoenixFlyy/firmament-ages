package dev.firmages.core.gate;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;

import java.util.Collection;
import java.util.Set;

/**
 * The m2 decision for one recipe (SPEC §4.3), first rule that applies:
 * <ol>
 *   <li>{@code gate.denyRecipes} lists the recipe: DROP.</li>
 *   <li>{@code gate.allowRecipes} lists the recipe: KEEP (byproduct cases).</li>
 *   <li>{@code gate.exemptRecipeTypes} lists the type or serializer: KEEP (world data).</li>
 *   <li>Any output item or fluid is in a locked Age or in {@code age_items/disabled} ("never"): DROP. A tag output
 *       counts as locked only if it has members and all of them are locked.</li>
 *   <li>Otherwise KEEP; no detected output at all is recorded as undetected.</li>
 * </ol>
 * Recipe inputs are never checked. Pure Java (unit-testable).
 */
public final class GateRules {

    /** Bucket name of recipes whose output is in {@code age_items/disabled}. Sorts after every Age. */
    public static final String DISABLED = "disabled";

    /** Where the gate looks up Ages and tag members (backed by the {@link dev.firmages.core.age.AgeIndex} and the load's tags). */
    public interface Lookup {
        AgeId itemAge(String id);

        AgeId fluidAge(String id);

        boolean itemDisabled(String id);

        boolean fluidDisabled(String id);

        /** Member ids of an item tag; empty if the tag does not exist. */
        Collection<String> itemTag(String tagId);

        /** Member ids of a fluid tag; empty if the tag does not exist. */
        Collection<String> fluidTag(String tagId);
    }

    public record Settings(boolean enabled, Set<String> allow, Set<String> deny, Set<String> exemptTypes) {}

    public enum Reason { DENIED, ALLOWED, EXEMPT, LOCKED, UNLOCKED, UNDETECTED }

    /**
     * @param keep   whether the recipe stays
     * @param bucket for LOCKED: the Age whose unlock brings the recipe back ({@link #DISABLED} for "never"), else null
     * @param entry  for LOCKED: the output that locks it (id or #tag)
     */
    public record Verdict(boolean keep, Reason reason, String bucket, String entry) {
        static final Verdict DENY = new Verdict(false, Reason.DENIED, null, null);
        static final Verdict ALLOW = new Verdict(true, Reason.ALLOWED, null, null);
        static final Verdict EXEMPT = new Verdict(true, Reason.EXEMPT, null, null);
        static final Verdict UNLOCKED = new Verdict(true, Reason.UNLOCKED, null, null);
        static final Verdict UNDETECTED = new Verdict(true, Reason.UNDETECTED, null, null);
    }

    private GateRules() {}

    /** Rules 1 to 3, which need no outputs. Null if none applies. */
    public static Verdict byConfig(String recipeId, String typeId, String serializerId, Settings s) {
        if (s.deny().contains(recipeId)) return Verdict.DENY;
        if (s.allow().contains(recipeId)) return Verdict.ALLOW;
        if (s.exemptTypes().contains(typeId) || (serializerId != null && s.exemptTypes().contains(serializerId))) return Verdict.EXEMPT;
        return null;
    }

    public static Verdict decide(String recipeId, String typeId, String serializerId, OutputSink out, Settings s,
                                 Lookup lookup, AgeSnapshot snap) {
        Verdict v = byConfig(recipeId, typeId, serializerId, s);
        if (v != null) return v;
        if (out.isEmpty()) return Verdict.UNDETECTED;
        Lock worst = null;
        for (String id : out.items()) worst = later(worst, itemLock(id, lookup, snap), id);
        for (String id : out.fluids()) worst = later(worst, fluidLock(id, lookup, snap), id);
        for (String id : out.anyIds()) {
            worst = later(worst, itemLock(id, lookup, snap), id);
            worst = later(worst, fluidLock(id, lookup, snap), id);
        }
        for (String t : out.itemTags()) worst = later(worst, tagLock(lookup.itemTag(t), true, lookup, snap), "#" + t);
        for (String t : out.fluidTags()) worst = later(worst, tagLock(lookup.fluidTag(t), false, lookup, snap), "#" + t);
        for (String t : out.anyTags()) {
            Collection<String> members = lookup.itemTag(t);
            boolean item = !members.isEmpty();
            if (!item) members = lookup.fluidTag(t);
            worst = later(worst, tagLock(members, item, lookup, snap), "#" + t);
        }
        return worst == null ? Verdict.UNLOCKED : new Verdict(false, Reason.LOCKED, worst.bucket, worst.entry);
    }

    /** A locked output: rank 0..10 for the Ages, 11 for disabled. */
    private record Lock(int rank, String bucket, String entry) {}

    private static final int DISABLED_RANK = AgeId.all().size();

    private static Lock itemLock(String id, Lookup l, AgeSnapshot snap) {
        if (l.itemDisabled(id)) return new Lock(DISABLED_RANK, DISABLED, null);
        return ageLock(l.itemAge(id), snap);
    }

    private static Lock fluidLock(String id, Lookup l, AgeSnapshot snap) {
        if (l.fluidDisabled(id)) return new Lock(DISABLED_RANK, DISABLED, null);
        return ageLock(l.fluidAge(id), snap);
    }

    private static Lock ageLock(AgeId age, AgeSnapshot snap) {
        return age != null && !snap.isUnlocked(age) ? new Lock(age.ordinal(), age.id(), null) : null;
    }

    /** Locked only if the tag has members and all are locked; the recipe returns with the earliest member's Age. */
    private static Lock tagLock(Collection<String> members, boolean item, Lookup l, AgeSnapshot snap) {
        if (members.isEmpty()) return null;
        Lock earliest = null;
        for (String m : members) {
            Lock lock = item ? itemLock(m, l, snap) : fluidLock(m, l, snap);
            if (lock == null) return null;
            if (earliest == null || lock.rank < earliest.rank) earliest = lock;
        }
        return earliest;
    }

    private static Lock later(Lock current, Lock candidate, String entry) {
        if (candidate == null) return current;
        if (current == null || candidate.rank > current.rank) return new Lock(candidate.rank, candidate.bucket, entry);
        return current;
    }

    /** Bucket order for reports: dawn..age_9, then disabled. */
    public static int bucketRank(String bucket) {
        if (DISABLED.equals(bucket)) return DISABLED_RANK;
        return AgeId.byId(bucket).map(Enum::ordinal).orElse(DISABLED_RANK + 1);
    }
}
