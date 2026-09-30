package dev.firmages.core.age;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves tag JSON files to element ids without bound tags (SPEC §2.2): during the datapack load the
 * age tags are read straight from the resource manager, so nothing depends on the tag manager's order.
 * Supports vanilla {@code replace}, {@code values} (plain ids, {@code #tag} refs, {@code {"id","required"}}
 * objects) and the NeoForge {@code remove} list. Nested tags are resolved recursively with a cycle guard.
 * Pure Java (unit-testable); one instance per tag directory and load.
 */
public final class AgeTagResolver {

    /** Returns the parsed JSON of every pack's copy of a tag file, lowest-priority pack first (empty if none). */
    public interface Source {
        List<JsonElement> layers(String tagId);
    }

    private final Source source;
    private final Map<String, Set<String>> cache = new HashMap<>();
    private final Deque<String> stack = new ArrayDeque<>();
    private final List<String> warnings = new ArrayList<>();

    public AgeTagResolver(Source source) {
        this.source = source;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** True if at least one pack defines the tag. */
    public boolean exists(String tagId) {
        return !source.layers(tagId).isEmpty();
    }

    /** Element ids of the tag (insertion order), empty if the tag does not exist. */
    public Set<String> resolve(String tagId) {
        Set<String> cached = cache.get(tagId);
        if (cached != null) return cached;
        if (stack.contains(tagId)) {
            warnings.add("tag cycle: " + String.join(" -> ", stack) + " -> " + tagId);
            return Set.of();
        }
        stack.push(tagId);
        try {
            List<Entry> values = new ArrayList<>();
            List<Entry> removes = new ArrayList<>();
            for (JsonElement layer : source.layers(tagId)) {
                if (layer == null || !layer.isJsonObject()) {
                    warnings.add("#" + tagId + ": a tag file is not a JSON object");
                    continue;
                }
                JsonObject o = layer.getAsJsonObject();
                if (o.has("replace") && o.get("replace").isJsonPrimitive() && o.get("replace").getAsBoolean()) {
                    values.clear();
                }
                parseEntries(tagId, o.get("values"), values);
                parseEntries(tagId, o.get("remove"), removes);
            }
            Set<String> out = new LinkedHashSet<>();
            for (Entry e : values) {
                if (e.tag) {
                    if (!exists(e.id)) {
                        if (e.required) warnings.add("#" + tagId + ": referenced tag #" + e.id + " does not exist");
                        continue;
                    }
                    out.addAll(resolve(e.id));
                } else {
                    out.add(e.id);
                }
            }
            for (Entry e : removes) {
                if (e.tag) out.removeAll(resolve(e.id));
                else out.remove(e.id);
            }
            Set<String> result = Collections.unmodifiableSet(out);
            cache.put(tagId, result);
            return result;
        } finally {
            stack.pop();
        }
    }

    private record Entry(String id, boolean tag, boolean required) {}

    private void parseEntries(String tagId, JsonElement arr, List<Entry> into) {
        if (arr == null) return;
        if (!arr.isJsonArray()) {
            warnings.add("#" + tagId + ": 'values'/'remove' is not an array");
            return;
        }
        for (JsonElement el : arr.getAsJsonArray()) {
            String raw;
            boolean required = true;
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                raw = el.getAsString();
            } else if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
                JsonObject o = el.getAsJsonObject();
                raw = o.get("id").getAsString();
                if (o.has("required")) required = o.get("required").getAsBoolean();
            } else {
                warnings.add("#" + tagId + ": unsupported entry " + el);
                continue;
            }
            boolean isTag = raw.startsWith("#");
            String id = normalize(isTag ? raw.substring(1) : raw);
            into.add(new Entry(id, isTag, required));
        }
    }

    /** Adds the {@code minecraft} namespace to bare ids, like ResourceLocation does. */
    static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }
}
