package dev.firmages.core.gate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Outputs detected for one recipe (SPEC §4.2): item ids, fluid ids, ids of unknown kind (from the JSON walk,
 * checked against both registries), and the same three kinds of tag ids. Ids are normalized
 * ({@code minecraft:} added to bare ids). Pure Java (unit-testable).
 */
public final class OutputSink {
    public enum Kind { ITEM, FLUID, ANY }

    private final Set<String> items = new LinkedHashSet<>();
    private final Set<String> fluids = new LinkedHashSet<>();
    private final Set<String> anyIds = new LinkedHashSet<>();
    private final Set<String> itemTags = new LinkedHashSet<>();
    private final Set<String> fluidTags = new LinkedHashSet<>();
    private final Set<String> anyTags = new LinkedHashSet<>();

    public void id(Kind kind, String id) {
        String n = normalize(id);
        if (n == null) return;
        switch (kind) {
            case ITEM -> items.add(n);
            case FLUID -> fluids.add(n);
            case ANY -> anyIds.add(n);
        }
    }

    public void tag(Kind kind, String tagId) {
        String raw = tagId != null && tagId.startsWith("#") ? tagId.substring(1) : tagId;
        String n = normalize(raw);
        if (n == null) return;
        switch (kind) {
            case ITEM -> itemTags.add(n);
            case FLUID -> fluidTags.add(n);
            case ANY -> anyTags.add(n);
        }
    }

    /** A string value: {@code #ns:path} is a tag, anything else an id. */
    public void value(Kind kind, String value) {
        if (value == null) return;
        if (value.startsWith("#")) tag(kind, value);
        else id(kind, value);
    }

    public Set<String> items() { return items; }
    public Set<String> fluids() { return fluids; }
    public Set<String> anyIds() { return anyIds; }
    public Set<String> itemTags() { return itemTags; }
    public Set<String> fluidTags() { return fluidTags; }
    public Set<String> anyTags() { return anyTags; }

    public boolean isEmpty() {
        return items.isEmpty() && fluids.isEmpty() && anyIds.isEmpty() && itemTags.isEmpty() && fluidTags.isEmpty() && anyTags.isEmpty();
    }

    /** Short human-readable list for the audit and {@code recipes why}. */
    public String describe() {
        List<String> parts = Stream.of(
                items.stream().map(s -> "item " + s),
                fluids.stream().map(s -> "fluid " + s),
                anyIds.stream().map(s -> s),
                itemTags.stream().map(s -> "item #" + s),
                fluidTags.stream().map(s -> "fluid #" + s),
                anyTags.stream().map(s -> "#" + s))
            .flatMap(s -> s).toList();
        return parts.isEmpty() ? "(none)" : String.join(", ", parts);
    }

    /** Adds {@code minecraft:} to bare ids; null for blank or malformed values. */
    static String normalize(String id) {
        if (id == null) return null;
        String s = id.trim();
        if (s.isEmpty() || s.indexOf(' ') >= 0) return null;
        return s.indexOf(':') < 0 ? "minecraft:" + s : s;
    }
}
