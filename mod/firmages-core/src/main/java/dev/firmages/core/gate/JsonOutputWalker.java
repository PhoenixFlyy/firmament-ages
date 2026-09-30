package dev.firmages.core.gate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Output detection on the recipe JSON (SPEC §4.2 step 3), the catch-all next to the typed extractors.
 * <ul>
 *   <li>Output keys: {@code result(s)}, {@code output(s)}, {@code *_output(s)}, {@code *_result(s)},
 *       {@code output(s)_*}, {@code result(s)_*} and {@code *_fluid} (e.g. TFC {@code result_fluid},
 *       {@code output_item}, Mekanism {@code item_output}). A key that names an input
 *       ({@code input}, {@code ingredient}, {@code catalyst}, {@code reagent}) is never an output key.</li>
 *   <li>Inside an output subtree every {@code id}, {@code item}, {@code fluid} and {@code tag} string is collected,
 *       as are plain string values ({@code "result": "minecraft:stone"}, {@code "#tag"}). Counts, chances, components
 *       and conditions are ignored.</li>
 *   <li>Outside output subtrees the walker descends into every key except inputs and conditions, so nested
 *       structures (IE crusher {@code secondaries[].output}, Create sequence steps) are found.</li>
 * </ul>
 * Values under a key or object that mentions a fluid are collected as fluids, {@code item} values as items,
 * the rest as ids of unknown kind (the gate checks those against items and fluids). Pure Java (unit-testable).
 */
public final class JsonOutputWalker {
    private static final Pattern OUTPUT_KEY = Pattern.compile(
        "^(results?|outputs?|.*_outputs?|.*_results?|outputs?_.*|results?_.*|.*_fluid)$");
    private static final int MAX_DEPTH = 32;

    private JsonOutputWalker() {}

    public static void walk(JsonElement recipe, OutputSink sink) {
        if (recipe != null) search(recipe, sink, 0);
    }

    static boolean isOutputKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return !isInputKey(k) && OUTPUT_KEY.matcher(k).matches();
    }

    private static boolean isInputKey(String k) {
        return k.contains("input") || k.contains("ingredient") || k.contains("catalyst") || k.contains("reagent");
    }

    private static boolean isSkippedKey(String k) {
        return isInputKey(k) || k.contains("condition") || k.equals("key") || k.equals("pattern") || k.equals("components")
            || k.contains("pedestal");
    }

    /** Outside outputs: find output keys. */
    private static void search(JsonElement el, OutputSink sink, int depth) {
        if (depth > MAX_DEPTH) return;
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) search(e, sink, depth + 1);
        } else if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String k = e.getKey().toLowerCase(Locale.ROOT);
                if (isOutputKey(k)) {
                    collect(e.getValue(), sink, k.contains("fluid") ? OutputSink.Kind.FLUID : OutputSink.Kind.ANY, depth + 1);
                } else if (!isSkippedKey(k)) {
                    search(e.getValue(), sink, depth + 1);
                }
            }
        }
    }

    /** Inside an output subtree: collect ids and tags. */
    private static void collect(JsonElement el, OutputSink sink, OutputSink.Kind kind, int depth) {
        if (depth > MAX_DEPTH || el == null || el.isJsonNull()) return;
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isString()) sink.value(kind, p.getAsString());
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) collect(e, sink, kind, depth + 1);
            return;
        }
        JsonObject o = el.getAsJsonObject();
        OutputSink.Kind here = kind == OutputSink.Kind.ANY && o.has("fluid") ? OutputSink.Kind.FLUID : kind;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            String k = e.getKey().toLowerCase(Locale.ROOT);
            JsonElement v = e.getValue();
            switch (k) {
                case "id" -> string(v, s -> sink.id(here, s));
                case "item" -> string(v, s -> sink.value(OutputSink.Kind.ITEM, s));
                case "fluid" -> string(v, s -> sink.value(OutputSink.Kind.FLUID, s));
                case "tag" -> string(v, s -> sink.tag(here, s));
                default -> {
                    if (v.isJsonObject() || v.isJsonArray()) {
                        if (!isSkippedKey(k)) collect(v, sink, k.contains("fluid") ? OutputSink.Kind.FLUID : here, depth + 1);
                    }
                }
            }
        }
    }

    private static void string(JsonElement v, java.util.function.Consumer<String> to) {
        if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) to.accept(v.getAsString());
    }
}
