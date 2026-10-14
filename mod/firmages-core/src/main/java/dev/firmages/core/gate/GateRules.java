package dev.firmages.core.gate;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The m2 decision for one recipe (SPEC §4.3). A recipe is classified once per datapack load ({@link #classify}) into
 * what it needs, independent of the Ages; {@link Classification#verdict} then answers for any Age set, so an Age change
 * re-filters the kept master list without a reload ({@code age/AgeSwap}, SPEC §3.3). First rule that applies:
 * <ol>
 *   <li>{@code gate.denyRecipes} lists the recipe: DROP.</li>
 *   <li>{@code gate.allowRecipes} lists the recipe: KEEP (byproduct cases).</li>
 *   <li>{@code gate.exemptRecipeTypes} lists the type or serializer: KEEP (world data).</li>
 *   <li>The recipe's station is locked: DROP. Stations are the {@code FirmAges.stationAge} types of the pack scripts,
 *       the {@code FirmAges.requireAge} recipe ids, and the Create heat levels ({@code create:mixing} and
 *       {@code create:compacting}: {@code heated} needs a heater or a Blaze Burner, {@code superheated} a Blaze Burner,
 *       their Ages read from the Age index). A {@code FirmAges.untilAge} recipe is dropped once that Age is unlocked
 *       (a stand-in that a later variant replaces).</li>
 *   <li>Any output item or fluid is in a locked Age or in {@code age_items/disabled} ("never"): DROP. A tag output
 *       counts as locked only if it has members and all of them are locked.</li>
 *   <li>Otherwise KEEP; no detected output at all is recorded as undetected.</li>
 * </ol>
 * Recipe inputs are never checked. Pure Java (unit-testable).
 */
public final class GateRules {

    /** Bucket name of recipes whose output is in {@code age_items/disabled}. Sorts after every Age. */
    public static final String DISABLED = "disabled";
    /** Bucket name of {@code FirmAges.untilAge} recipes whose Age is unlocked (a later variant took over). */
    public static final String SUPERSEDED = "superseded";

    /** Serializers (or types) whose {@code heat_requirement} is a station (Create basin recipes). */
    public static final Set<String> HEAT_TYPES = Set.of("create:mixing", "create:compacting");
    /** Items that provide each heat level, earliest first. Their Ages come from the Age index. */
    public static final Map<String, List<String>> HEAT_SOURCES = Map.of(
        "heated", List.of("tfcreate:primitive_heater", "create:blaze_burner"),
        "superheated", List.of("create:blaze_burner"));

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

    public enum Reason { DENIED, ALLOWED, EXEMPT, LOCKED, UNLOCKED, UNDETECTED, SUPERSEDED }

    /**
     * @param keep   whether the recipe stays
     * @param bucket for LOCKED: the Age whose unlock brings the recipe back ({@link #DISABLED} for "never"); for
     *               SUPERSEDED: {@link #SUPERSEDED}; else null
     * @param entry  for LOCKED: the output or station that locks it (id, #tag, {@code station <type>}, ...)
     */
    public record Verdict(boolean keep, Reason reason, String bucket, String entry) {
        static final Verdict DENY = new Verdict(false, Reason.DENIED, null, null);
        static final Verdict ALLOW = new Verdict(true, Reason.ALLOWED, null, null);
        static final Verdict EXEMPT = new Verdict(true, Reason.EXEMPT, null, null);
        static final Verdict UNLOCKED = new Verdict(true, Reason.UNLOCKED, null, null);
        static final Verdict UNDETECTED = new Verdict(true, Reason.UNDETECTED, null, null);
    }

    /**
     * One condition of a recipe: met when any Age of {@code mask} is unlocked (bit = {@link AgeId#ordinal()}). An item
     * output is a one-bit clause, a tag output the bits of its members, a mask of 0 never unlocks ({@code disabled}).
     */
    public record Clause(int mask, String entry) {
        boolean met(int unlocked) {
            return (mask & unlocked) != 0;
        }

        /** Rank of the Age that meets it first: 0..10, {@link #DISABLED_RANK} for never. */
        int rank() {
            return mask == 0 ? DISABLED_RANK : Integer.numberOfTrailingZeros(mask);
        }
    }

    /** The station Ages of one datapack load: the pack scripts' markers plus the Create heat levels. */
    public record Stations(Map<String, AgeId> byType, Map<String, Set<AgeId>> requireById, Map<String, Set<AgeId>> untilById,
                           Clause heated, Clause superheated) {
        public static final Stations NONE = new Stations(Map.of(), Map.of(), Map.of(), null, null);

        /** Markers of the load plus the heat clauses, whose Ages come from {@code lookup}. */
        public static Stations of(RecipeMarkers.Snapshot markers, Lookup lookup) {
            return new Stations(markers.stationAges(), markers.requireAges(), markers.untilAges(),
                anyOf(HEAT_SOURCES.get("heated"), lookup, "heat heated"), anyOf(HEAT_SOURCES.get("superheated"), lookup, "heat superheated"));
        }

        /** A clause met by any of the items' Ages; null when one of them has no Age (always there). */
        static Clause anyOf(List<String> items, Lookup lookup, String entry) {
            int mask = 0;
            for (String id : items) {
                if (lookup.itemDisabled(id)) continue;
                AgeId a = lookup.itemAge(id);
                if (a == null) return null;
                mask |= bit(a);
            }
            return new Clause(mask, entry);
        }
    }

    /**
     * What a recipe needs, for every Age set. {@code fixed} is the config verdict (deny, allow, exempt) that ignores
     * the Ages; else the recipe is kept while no {@code until} Age is unlocked, every station clause and every output
     * clause is met. {@code resolved} is the late pass's clause for the result an IE or Occultism tag output resolves to.
     */
    public record Classification(Verdict fixed, List<Clause> stations, int untilMask, String untilEntry, List<Clause> outputs,
                                 boolean undetected, Clause resolved) {

        /** Fast path of {@link #verdict}: kept at this Age mask? */
        public boolean keep(int unlocked) {
            if (fixed != null) return fixed.keep();
            if ((untilMask & unlocked) != 0) return false;
            for (Clause c : stations) if (!c.met(unlocked)) return false;
            for (Clause c : outputs) if (!c.met(unlocked)) return false;
            return resolved == null || resolved.met(unlocked);
        }

        /** Kept at this Age mask without the late pass's resolved result? */
        public boolean keepWithoutResolved(int unlocked) {
            return withResolved(null).keep(unlocked);
        }

        public Verdict verdict(AgeSnapshot snap) {
            return verdict(mask(snap));
        }

        public Verdict verdict(int unlocked) {
            if (fixed != null) return fixed;
            if ((untilMask & unlocked) != 0) return new Verdict(false, Reason.SUPERSEDED, SUPERSEDED, untilEntry);
            Clause worst = null;
            for (Clause c : outputs) worst = later(worst, c, unlocked);
            if (resolved != null) worst = later(worst, resolved, unlocked);
            for (Clause c : stations) worst = later(worst, c, unlocked);
            if (worst != null) {
                int rank = worst.rank();
                return new Verdict(false, Reason.LOCKED, rank == DISABLED_RANK ? DISABLED : AgeId.all().get(rank).id(), worst.entry());
            }
            return undetected ? Verdict.UNDETECTED : Verdict.UNLOCKED;
        }

        public Classification withResolved(Clause clause) {
            return new Classification(fixed, stations, untilMask, untilEntry, outputs, undetected, clause);
        }

        /** The unmet clause with the latest Age wins; on equal Ages the first one. */
        private static Clause later(Clause current, Clause candidate, int unlocked) {
            if (candidate.met(unlocked)) return current;
            if (current == null || candidate.rank() > current.rank()) return candidate;
            return current;
        }
    }

    private GateRules() {}

    /** Bit mask of the unlocked Ages ({@code dawn} is always set). */
    public static int mask(AgeSnapshot snap) {
        int m = 0;
        for (AgeId a : snap.unlocked()) m |= bit(a);
        return m;
    }

    static int bit(AgeId a) {
        return 1 << a.ordinal();
    }

    /** Rules 1 to 3, which need no outputs. Null if none applies. */
    public static Verdict byConfig(String recipeId, String typeId, String serializerId, Settings s) {
        if (s.deny().contains(recipeId)) return Verdict.DENY;
        if (s.allow().contains(recipeId)) return Verdict.ALLOW;
        if (s.exemptTypes().contains(typeId) || (serializerId != null && s.exemptTypes().contains(serializerId))) return Verdict.EXEMPT;
        return null;
    }

    /** The verdict for one Age set (classification without stations). */
    public static Verdict decide(String recipeId, String typeId, String serializerId, OutputSink out, Settings s,
                                 Lookup lookup, AgeSnapshot snap) {
        return classify(recipeId, typeId, serializerId, null, out, s, lookup, Stations.NONE).verdict(snap);
    }

    /**
     * Classifies one recipe for every Age set.
     *
     * @param heat the recipe's {@code heat_requirement} (Create basin recipes), else null
     */
    public static Classification classify(String recipeId, String typeId, String serializerId, String heat, OutputSink out,
                                          Settings s, Lookup lookup, Stations st) {
        Verdict fixed = byConfig(recipeId, typeId, serializerId, s);
        if (fixed != null) return new Classification(fixed, List.of(), 0, null, List.of(), false, null);

        List<Clause> stations = new ArrayList<>(2);
        station(stations, st.byType().get(typeId), "station " + typeId);
        if (serializerId != null && !serializerId.equals(typeId)) station(stations, st.byType().get(serializerId), "station " + serializerId);
        for (AgeId a : st.requireById().getOrDefault(recipeId, Set.of())) stations.add(new Clause(bit(a), "requireAge " + a.id()));
        if (heat != null && (HEAT_TYPES.contains(typeId) || HEAT_TYPES.contains(serializerId))) {
            Clause h = "superheated".equals(heat) ? st.superheated() : "heated".equals(heat) ? st.heated() : null;
            if (h != null) stations.add(h);
        }
        int until = 0;
        for (AgeId a : st.untilById().getOrDefault(recipeId, Set.of())) until |= bit(a);
        String untilEntry = until == 0 ? null : "untilAge " + st.untilById().get(recipeId).stream().map(AgeId::id).sorted().toList();

        List<Clause> outputs = new ArrayList<>(2);
        for (String id : out.items()) item(outputs, id, lookup);
        for (String id : out.fluids()) fluid(outputs, id, lookup);
        for (String id : out.anyIds()) {
            item(outputs, id, lookup);
            fluid(outputs, id, lookup);
        }
        for (String t : out.itemTags()) tag(outputs, lookup.itemTag(t), true, lookup, "#" + t);
        for (String t : out.fluidTags()) tag(outputs, lookup.fluidTag(t), false, lookup, "#" + t);
        for (String t : out.anyTags()) {
            Collection<String> members = lookup.itemTag(t);
            boolean item = !members.isEmpty();
            if (!item) members = lookup.fluidTag(t);
            tag(outputs, members, item, lookup, "#" + t);
        }
        return new Classification(null, List.copyOf(stations), until, untilEntry, List.copyOf(outputs), out.isEmpty(), null);
    }

    /** The clause of one output item (the late pass's resolved result), or null when it has no Age. */
    public static Clause itemClause(String id, Lookup lookup) {
        List<Clause> one = new ArrayList<>(1);
        item(one, id, lookup);
        return one.isEmpty() ? null : one.get(0);
    }

    private static void station(List<Clause> out, AgeId age, String entry) {
        if (age != null) out.add(new Clause(bit(age), entry));
    }

    private static final int DISABLED_RANK = AgeId.all().size();

    private static void item(List<Clause> out, String id, Lookup l) {
        if (l.itemDisabled(id)) out.add(new Clause(0, id));
        else if (l.itemAge(id) != null) out.add(new Clause(bit(l.itemAge(id)), id));
    }

    private static void fluid(List<Clause> out, String id, Lookup l) {
        if (l.fluidDisabled(id)) out.add(new Clause(0, id));
        else if (l.fluidAge(id) != null) out.add(new Clause(bit(l.fluidAge(id)), id));
    }

    /** Locked only if the tag has members and all are locked; the recipe returns with the earliest member's Age. */
    private static void tag(List<Clause> out, Collection<String> members, boolean item, Lookup l, String entry) {
        if (members.isEmpty()) return;
        int mask = 0;
        for (String m : members) {
            if (item ? l.itemDisabled(m) : l.fluidDisabled(m)) continue;
            AgeId a = item ? l.itemAge(m) : l.fluidAge(m);
            if (a == null) return; // a member of no Age is always there
            mask |= bit(a);
        }
        out.add(new Clause(mask, entry));
    }

    /** Bucket order for reports: dawn..age_9, then disabled. */
    public static int bucketRank(String bucket) {
        if (DISABLED.equals(bucket)) return DISABLED_RANK;
        return AgeId.byId(bucket).map(Enum::ordinal).orElse(DISABLED_RANK + 1);
    }
}
