package dev.firmages.core.crown;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The chord library of the Lens Crown (SPEC §27.6): {@code data/<ns>/firmages_crown/chords.json}, at most 15 chords
 * with unique signals 1..15 and unique lens sets. A file with any error is rejected as a whole, so the library never
 * holds half a file. Pure Java (Gson); {@link ChordLibraryLoader} reads the files on every datapack load.
 *
 * @param chords id -> chord, in file order
 * @param errors why files were rejected
 */
public record ChordLibrary(Map<String, Chord> chords, List<String> errors) {
    public static final int MAX_CHORDS = 15;
    public static final ChordLibrary EMPTY = new ChordLibrary(Map.of(), List.of());
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");

    /** The library of the last completed datapack load. */
    private static volatile ChordLibrary current = EMPTY;
    /**
     * The library of the datapack load in progress, set at the end of its prepare phase. Every prepare phase ends
     * before the first apply phase starts, so the recipe codec (applied in {@code RecipeManager#apply}) already sees
     * the chords of its own load.
     */
    private static volatile ChordLibrary pending = EMPTY;

    public static ChordLibrary current() {
        return current;
    }

    /** The library the recipe codec resolves chords against: the load in progress, else the current one (also for scripts that run during a load). */
    public static ChordLibrary latest() {
        return pending;
    }

    static void setPending(ChordLibrary lib) {
        pending = lib;
    }

    static void setCurrent(ChordLibrary lib) {
        current = lib;
        pending = lib;
    }

    public Optional<Chord> get(String id) {
        return Optional.ofNullable(chords.get(id));
    }

    /** The chord with exactly this lens set, if the library names one. */
    public Optional<Chord> byMask(int mask) {
        for (Chord c : chords.values()) if (c.mask() == mask) return Optional.of(c);
        return Optional.empty();
    }

    /**
     * Parses the chord files of one load, in id order. Each file is checked on its own and against the files accepted
     * before it (shared signal, shared lens set, more than 15 chords in all).
     *
     * @param files file id ({@code <ns>:chords}) -> JSON
     */
    public static ChordLibrary parse(Map<String, JsonElement> files) {
        Map<String, Chord> all = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, JsonElement> f : new java.util.TreeMap<>(files).entrySet()) {
            String file = f.getKey();
            String ns = file.contains(":") ? file.substring(0, file.indexOf(':')) : "minecraft";
            List<String> fileErrors = new ArrayList<>();
            Map<String, Chord> fileChords = parseFile(ns, f.getValue(), fileErrors);
            Map<Integer, String> signals = new HashMap<>();
            Map<Integer, String> masks = new HashMap<>();
            for (Chord c : all.values()) {
                signals.put(c.signal(), c.id());
                masks.put(c.mask(), c.id());
            }
            for (Chord c : fileChords.values()) {
                if (all.containsKey(c.id())) fileErrors.add(c.id() + " is defined twice");
                String s = signals.put(c.signal(), c.id());
                if (s != null) fileErrors.add(c.id() + " shares signal " + c.signal() + " with " + s);
                String m = masks.put(c.mask(), c.id());
                if (m != null) fileErrors.add(c.id() + " has the same lenses as " + m);
            }
            if (all.size() + fileChords.size() > MAX_CHORDS) fileErrors.add("more than " + MAX_CHORDS + " chords");
            if (fileErrors.isEmpty()) {
                all.putAll(fileChords);
            } else {
                errors.add(file + ": " + String.join("; ", fileErrors));
            }
        }
        return new ChordLibrary(Collections.unmodifiableMap(all), List.copyOf(errors));
    }

    private static Map<String, Chord> parseFile(String ns, JsonElement json, List<String> errors) {
        Map<String, Chord> out = new LinkedHashMap<>();
        if (json == null || !json.isJsonObject()) {
            errors.add("not a JSON object");
            return out;
        }
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
            String name = e.getKey();
            if (!NAME.matcher(name).matches()) {
                errors.add("bad chord name '" + name + "'");
                continue;
            }
            if (!e.getValue().isJsonObject()) {
                errors.add(name + ": not an object");
                continue;
            }
            JsonObject o = e.getValue().getAsJsonObject();
            if (!o.has("lenses") || !o.get("lenses").isJsonArray()) {
                errors.add(name + ": no lens list");
                continue;
            }
            JsonArray lenses = o.getAsJsonArray("lenses");
            int mask = 0;
            boolean bad = false;
            for (JsonElement l : lenses) {
                if (!l.isJsonPrimitive() || !l.getAsJsonPrimitive().isNumber()) {
                    errors.add(name + ": lens " + l + " is not a number");
                    bad = true;
                    continue;
                }
                int n = l.getAsInt();
                if (n < 0 || n >= Chord.LENSES || l.getAsDouble() != n) {
                    errors.add(name + ": lens " + l + " is not in 0..8");
                    bad = true;
                    continue;
                }
                if ((mask & (1 << n)) != 0) {
                    errors.add(name + ": lens " + n + " twice");
                    bad = true;
                }
                mask |= 1 << n;
            }
            if (mask == 0) {
                errors.add(name + ": empty chord");
                bad = true;
            }
            int signal = o.has("signal") && o.get("signal").isJsonPrimitive() && o.getAsJsonPrimitive("signal").isNumber()
                ? o.get("signal").getAsInt() : -1;
            if (signal < 1 || signal > 15) {
                errors.add(name + ": signal must be 1..15");
                bad = true;
            }
            if (!bad) out.put(ns + ":" + name, new Chord(ns + ":" + name, mask, signal));
        }
        return out;
    }
}
