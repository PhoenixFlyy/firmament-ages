package dev.firmages.core.gate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Result of the last recipe filter run (SPEC §4.4): per recipe type the totals kept, dropped and undetected, the
 * dropped recipes per Age, the undetected recipe ids, timing and the misconfigured flag. Kept per recipe: the
 * verdict and the detected outputs, for {@code /firmages recipes why}. Pure Java (unit-testable).
 */
public final class GateReport {
    public static final String AUDIT_FILE = "firmages-recipe-audit.txt";

    public static final class TypeStats {
        public int total, kept, dropped, undetected;
    }

    public record Entry(String type, String serializer, GateRules.Verdict verdict, String outputs) {}

    private final Instant created;
    private final List<String> unlocked;
    private final boolean enabled;
    private final boolean misconfigured;
    private final String tagSource;
    private final Map<String, TypeStats> types = new TreeMap<>();
    private final Map<String, TypeStats> serializers = new TreeMap<>();
    private static final Comparator<String> BUCKET_ORDER = Comparator.comparingInt(GateRules::bucketRank).thenComparing(s -> s);
    private final Map<String, List<String>> droppedByBucket = new TreeMap<>(BUCKET_ORDER);
    private final Map<String, List<String>> undetectedByType = new TreeMap<>();
    private final Map<String, Entry> entries = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private int errorCount;
    private long millis = -1;
    private int late, lateDropped, resolvedDropped;

    public GateReport(Instant created, List<String> unlocked, boolean enabled, boolean misconfigured, String tagSource) {
        this.created = created;
        this.unlocked = List.copyOf(unlocked);
        this.enabled = enabled;
        this.misconfigured = misconfigured;
        this.tagSource = tagSource;
    }

    public void record(String recipeId, String type, String serializer, GateRules.Verdict v, String outputs) {
        count(types.computeIfAbsent(type, k -> new TypeStats()), v);
        count(serializers.computeIfAbsent(serializer, k -> new TypeStats()), v);
        if (v.reason() == GateRules.Reason.UNDETECTED) undetectedByType.computeIfAbsent(type, k -> new ArrayList<>()).add(recipeId);
        if (!v.keep()) droppedByBucket.computeIfAbsent(v.bucket() == null ? "denied" : v.bucket(), k -> new ArrayList<>()).add(recipeId);
        entries.put(recipeId, new Entry(type, serializer, v, outputs));
    }

    private static void count(TypeStats t, GateRules.Verdict v) {
        t.total++;
        if (v.keep()) t.kept++;
        else t.dropped++;
        if (v.reason() == GateRules.Reason.UNDETECTED) t.undetected++;
    }

    /** An extraction problem; the first 50 are kept verbatim. */
    public void error(String recipeId, String what) {
        errorCount++;
        if (errors.size() < 50) errors.add(recipeId + ": " + what);
    }

    public void note(String line) {
        notes.add(line);
    }

    public void finish(long millis) {
        this.millis = millis;
    }

    /**
     * The late pass of the same load: recipes added after the recipe apply (already recorded), how many of them were
     * dropped, how many kept recipes it dropped by their resolved result ({@link #reclassify}), and its time.
     */
    public void late(int recipes, int dropped, int resolved, long millis) {
        this.late += recipes;
        this.lateDropped += dropped;
        this.resolvedDropped += resolved;
        this.millis += millis;
    }

    public int lateRecipes() { return late; }
    public int lateDropped() { return lateDropped; }
    public int resolvedDropped() { return resolvedDropped; }

    /** A recipe recorded as kept is dropped after all (late pass, resolved result): moves it in every count. */
    public void reclassify(String recipeId, GateRules.Verdict v, String outputs) {
        Entry old = entries.get(recipeId);
        if (old == null || !old.verdict().keep() || v.keep()) return;
        boolean wasUndetected = old.verdict().reason() == GateRules.Reason.UNDETECTED;
        for (TypeStats t : List.of(types.get(old.type()), serializers.get(old.serializer()))) {
            t.kept--;
            t.dropped++;
            if (wasUndetected) t.undetected--;
        }
        if (wasUndetected) {
            List<String> ids = undetectedByType.get(old.type());
            if (ids != null) ids.remove(recipeId);
        }
        droppedByBucket.computeIfAbsent(v.bucket() == null ? "denied" : v.bucket(), k -> new ArrayList<>()).add(recipeId);
        entries.put(recipeId, new Entry(old.type(), old.serializer(), v, outputs));
    }

    public Instant created() { return created; }
    public List<String> unlocked() { return unlocked; }
    public boolean enabled() { return enabled; }
    public boolean misconfigured() { return misconfigured; }
    public String tagSource() { return tagSource; }
    public long millis() { return millis; }
    public int errorCount() { return errorCount; }
    public Map<String, TypeStats> types() { return types; }
    public Map<String, TypeStats> serializers() { return serializers; }
    public Map<String, Entry> entries() { return java.util.Collections.unmodifiableMap(entries); }
    public Map<String, List<String>> droppedByBucket() { return droppedByBucket; }
    public Map<String, List<String>> undetectedByType() { return undetectedByType; }
    public List<String> notes() { return notes; }

    public Entry entry(String recipeId) {
        return entries.get(recipeId);
    }

    public int total() {
        return types.values().stream().mapToInt(t -> t.total).sum();
    }

    public int dropped() {
        return types.values().stream().mapToInt(t -> t.dropped).sum();
    }

    public int undetected() {
        return types.values().stream().mapToInt(t -> t.undetected).sum();
    }

    /** Types where no recipe had a detected output (the audit list to review). */
    public List<String> typesWithoutDetectedOutput() {
        return withoutOutput(types);
    }

    /** Serializers where no recipe had a detected output (finer than types: e.g. the special crafting recipes). */
    public List<String> serializersWithoutDetectedOutput() {
        return withoutOutput(serializers);
    }

    private static List<String> withoutOutput(Map<String, TypeStats> m) {
        return m.entrySet().stream().filter(e -> e.getValue().undetected == e.getValue().total).map(Map.Entry::getKey).toList();
    }

    /** Dropped counts per bucket (Age id, {@code disabled}, {@code denied}), in progression order. */
    public Map<String, Integer> droppedPerBucket() {
        Map<String, Integer> out = new TreeMap<>(BUCKET_ORDER);
        droppedByBucket.forEach((k, v) -> out.put(k, v.size()));
        return out;
    }

    public String summary() {
        return String.format(Locale.ROOT, "%s: %d recipes, %d dropped %s, %d undetected (%d types and %d serializers without any detected output), %d extraction errors, %d ms (Ages %s, tags from %s)%s%s",
            enabled ? "Recipe gate" : "Recipe gate (log only, gate.enabled = false)", total(), dropped(), droppedPerBucket(),
            undetected(), typesWithoutDetectedOutput().size(), serializersWithoutDetectedOutput().size(), errorCount, millis, unlocked, tagSource,
            late > 0 || resolvedDropped > 0 ? String.format(Locale.ROOT, "; late pass: %d added after the recipe load (%d dropped), %d dropped by their resolved result",
                late, lateDropped, resolvedDropped) : "",
            misconfigured ? "; MISCONFIGURED: all age tags are empty" : "");
    }

    public String toText() {
        StringBuilder b = new StringBuilder();
        b.append("Firmament Ages recipe gate audit\n");
        b.append("Filter run: ").append(created).append('\n');
        b.append(summary()).append("\n\n");
        for (String n : notes) b.append("NOTE ").append(n).append('\n');
        if (!notes.isEmpty()) b.append('\n');
        b.append("Dropped per Age\n");
        droppedPerBucket().forEach((k, v) -> b.append(String.format(Locale.ROOT, "  %-10s %6d%n", k, v)));
        b.append("\nTypes with no detected output (every recipe undetected)\n");
        for (String t : typesWithoutDetectedOutput()) b.append("  ").append(t).append(" (").append(types.get(t).total).append(")\n");
        b.append("\nPer recipe type: total kept dropped undetected\n");
        types.forEach((t, s) -> b.append(String.format(Locale.ROOT, "  %-60s %6d %6d %6d %6d%n", t, s.total, s.kept, s.dropped, s.undetected)));
        b.append("\nUndetected recipes\n");
        undetectedByType.forEach((t, ids) -> {
            b.append("  ").append(t).append('\n');
            ids.stream().sorted().forEach(id -> b.append("    ").append(id).append('\n'));
        });
        b.append("\nDropped recipes per Age\n");
        droppedByBucket.forEach((k, ids) -> {
            b.append("  ").append(k).append(" (").append(ids.size()).append(")\n");
            ids.stream().sorted().forEach(id -> {
                Entry e = entries.get(id);
                b.append("    ").append(id).append(" [").append(e.type()).append("] ").append(e.verdict().entry() == null ? "" : e.verdict().entry()).append('\n');
            });
        });
        if (errorCount > 0) {
            b.append("\nExtraction errors (").append(errorCount).append(", first ").append(errors.size()).append(")\n");
            errors.forEach(e -> b.append("  ").append(e).append('\n'));
        }
        return b.toString();
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, toText(), StandardCharsets.UTF_8);
    }
}
