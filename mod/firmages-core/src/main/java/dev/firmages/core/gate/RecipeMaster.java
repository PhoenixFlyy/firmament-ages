package dev.firmages.core.gate;

import dev.firmages.core.age.AgeSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every recipe of one datapack load with its {@link GateRules.Classification} (SPEC §4.8): the master list the Age
 * swap filters on an Age change instead of reloading the datapacks. Built by {@link RecipeGate} in the main pass and
 * completed in the late pass of the same load (recipes added after the recipe apply, resolved IE and Occultism
 * results); read-only once published. It holds about what an age_9 server keeps loaded anyway.
 */
public final class RecipeMaster {

    /**
     * @param outputs the detected outputs, for the audit and {@code recipes why}
     * @param late    added after the recipe apply (late pass)
     */
    public record Entry(RecipeHolder<?> holder, String type, String serializer, String outputs, GateRules.Classification cls, boolean late) {
        public ResourceLocation id() {
            return holder.id();
        }
    }

    private final GateRules.Settings settings;
    private final String tagSource;
    private final boolean misconfigured;
    private final List<String> notes;
    private final List<Entry> entries = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private int errorCount;

    RecipeMaster(GateRules.Settings settings, String tagSource, boolean misconfigured, List<String> notes) {
        this.settings = settings;
        this.tagSource = tagSource;
        this.misconfigured = misconfigured;
        this.notes = List.copyOf(notes);
    }

    // ---------------------------------------------------------------- building (RecipeGate, during the load)

    void add(Entry e) {
        entries.add(e);
    }

    List<Entry> entriesMutable() {
        return entries;
    }

    void error(String recipeId, String what) {
        errorCount++;
        if (errors.size() < 50) errors.add(recipeId + ": " + what);
    }

    // ---------------------------------------------------------------- reading

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }

    private Set<ResourceLocation> ids;

    /** Ids of every recipe in the master (read-only once published). */
    public synchronized Set<ResourceLocation> ids() {
        if (ids == null) {
            Set<ResourceLocation> s = new HashSet<>(entries.size() * 2);
            for (Entry e : entries) s.add(e.id());
            ids = Collections.unmodifiableSet(s);
        }
        return ids;
    }

    public GateRules.Settings settings() {
        return settings;
    }

    /** The recipes kept at this Age set, in load order ({@code gate.enabled = false} keeps all). */
    public List<RecipeHolder<?>> live(AgeSnapshot snap) {
        int mask = GateRules.mask(snap);
        List<RecipeHolder<?>> out = new ArrayList<>(entries.size());
        for (Entry e : entries) if (!settings.enabled() || e.cls().keep(mask)) out.add(e.holder());
        return out;
    }

    /** The audit report of this master at one Age set. */
    public GateReport report(AgeSnapshot snap, long millis) {
        int mask = GateRules.mask(snap);
        GateReport r = new GateReport(Instant.now(), snap.unlockedIds(), settings.enabled(), misconfigured, tagSource);
        notes.forEach(r::note);
        int late = 0, lateDropped = 0, resolvedDropped = 0;
        for (Entry e : entries) {
            GateRules.Verdict v = e.cls().verdict(mask);
            r.record(e.id().toString(), e.type(), e.serializer(), v, e.outputs());
            if (e.late()) {
                late++;
                if (!v.keep()) lateDropped++;
            } else if (!v.keep() && e.cls().resolved() != null && e.cls().keepWithoutResolved(mask)) {
                resolvedDropped++;
            }
        }
        for (String err : errors) {
            int colon = err.indexOf(": ");
            r.error(colon < 0 ? err : err.substring(0, colon), colon < 0 ? "" : err.substring(colon + 2));
        }
        for (int i = errors.size(); i < errorCount; i++) r.error("(more)", "not kept verbatim");
        r.finish(millis);
        if (late > 0 || resolvedDropped > 0) r.late(late, lateDropped, resolvedDropped, 0);
        return r;
    }
}
