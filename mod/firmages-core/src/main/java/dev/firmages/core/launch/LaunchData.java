package dev.firmages.core.launch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.firmages.core.age.AgeId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The Launch Complex data (SPEC §31.4): {@code data/<ns>/firmages_launch/fuel.json} (the fuel tag, the launch cost and
 * the throttle steps) and {@code data/<ns>/firmages_launch/module/*.json} (one file per payload module: its Age, the
 * work of a flight and the fixed returns). Pure Java (Gson only); the registry and tag answers come through
 * {@link Lookup}, so JUnit runs the rules.
 * <p>
 * Rules: a module file is rejected (ERROR) when a return item is unknown, forbidden (an ore, a raw material, the
 * {@code ad_astra} namespace or an Age block) or of a later Age than the module; a module whose own item is not
 * registered is skipped (no error, the pack may not have it); two files for one module item keep the first by id.
 */
public record LaunchData(Optional<Fuel> fuel, Map<String, Module> modules, List<String> errors, List<String> skipped) {
    public static final LaunchData EMPTY = new LaunchData(Optional.empty(), Map.of(), List.of(), List.of());
    public static final int MAX_WORK = 10_000_000;

    /**
     * @param fluidTag the fluid tag that may fill the tank (without {@code #})
     * @param launchMb fuel taken at ignition
     * @param throttle the steps, slowest first
     */
    public record Fuel(String fluidTag, int launchMb, List<LaunchRules.Step> throttle) {}

    /** One fixed return of a flight. */
    public record Return(String item, int count) {}

    /**
     * @param file       the file id ({@code ns:path})
     * @param module     the module item id (consumed per flight)
     * @param age        the module's Age
     * @param flightWork progress a flight needs
     * @param returns    what comes back with the probe, always the same
     */
    public record Module(String file, String module, AgeId age, int flightWork, List<Return> returns) {}

    /** Registry and tag answers for the checks (the loader asks the bound tags; tests answer from maps). */
    public interface Lookup {
        boolean registered(String item);

        /** Why {@code item} may not come back from a flight, or null when it may. */
        String forbidden(String item);

        /** The item's Age, or null when no Age tag holds it. */
        AgeId age(String item);
    }

    public boolean valid() {
        return fuel.isPresent();
    }

    public Optional<Module> module(String item) {
        return Optional.ofNullable(modules.get(item));
    }

    /** The throttle steps (the spec default without a fuel file). */
    public List<LaunchRules.Step> steps() {
        return fuel.map(Fuel::throttle).orElseGet(LaunchRules::defaultSteps);
    }

    /**
     * Parses one load.
     *
     * @param fuelFiles   every {@code firmages_launch/fuel.json} by file id; the {@code firmages} one is used when
     *                    present, else the first by id
     * @param moduleFiles every {@code firmages_launch/module/*.json} by file id
     */
    public static LaunchData parse(Map<String, JsonElement> fuelFiles, Map<String, JsonElement> moduleFiles, Lookup lookup) {
        List<String> errors = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Optional<Fuel> fuel = Optional.empty();
        if (fuelFiles.isEmpty()) {
            errors.add("fuel.json: missing; no probe can launch");
        } else {
            String id = fuelFiles.containsKey("firmages:fuel") ? "firmages:fuel" : new TreeMap<>(fuelFiles).firstKey();
            try {
                fuel = Optional.of(parseFuel(fuelFiles.get(id)));
            } catch (IllegalArgumentException e) {
                errors.add(id + ": " + e.getMessage());
            }
        }
        Map<String, Module> modules = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : new TreeMap<>(moduleFiles).entrySet()) {
            Module m;
            try {
                m = parseModule(e.getKey(), e.getValue(), lookup);
            } catch (IllegalArgumentException ex) {
                errors.add(e.getKey() + ": " + ex.getMessage());
                continue;
            }
            if (!lookup.registered(m.module())) {
                skipped.add(e.getKey() + " (module " + m.module() + " is not registered)");
                continue;
            }
            Module first = modules.putIfAbsent(m.module(), m);
            if (first != null) errors.add(e.getKey() + ": module " + m.module() + " already defined by " + first.file());
        }
        return new LaunchData(fuel, Collections.unmodifiableMap(modules), List.copyOf(errors), List.copyOf(skipped));
    }

    static Fuel parseFuel(JsonElement json) {
        if (json == null || !json.isJsonObject()) throw new IllegalArgumentException("not an object");
        JsonObject o = json.getAsJsonObject();
        String tag = string(o, "fluid_tag");
        if (tag.startsWith("#")) tag = tag.substring(1);
        if (!tag.contains(":")) throw new IllegalArgumentException("fluid_tag needs a namespace: " + tag);
        int launch = integer(o, "launch_mb", 0, 1_000_000);
        if (!o.has("throttle") || !o.get("throttle").isJsonArray()) throw new IllegalArgumentException("throttle: a list of steps expected");
        List<LaunchRules.Step> steps = new ArrayList<>();
        int lastProgress = 0;
        for (JsonElement s : o.getAsJsonArray("throttle")) {
            if (!s.isJsonObject()) throw new IllegalArgumentException("throttle: each step is an object");
            JsonObject so = s.getAsJsonObject();
            LaunchRules.Step step = new LaunchRules.Step(integer(so, "burn", 1, 100_000), integer(so, "progress", 1, 100_000));
            if (step.progress() <= lastProgress) throw new IllegalArgumentException("throttle: each step must fly faster than the one before");
            lastProgress = step.progress();
            steps.add(step);
        }
        if (steps.isEmpty() || steps.size() > 15) throw new IllegalArgumentException("throttle: one to 15 steps");
        return new Fuel(tag, launch, List.copyOf(steps));
    }

    static Module parseModule(String file, JsonElement json, Lookup lookup) {
        if (json == null || !json.isJsonObject()) throw new IllegalArgumentException("not an object");
        JsonObject o = json.getAsJsonObject();
        String module = id(string(o, "module"));
        String ageId = string(o, "age");
        AgeId age = AgeId.byId(ageId).orElseThrow(() -> new IllegalArgumentException("unknown Age " + ageId));
        int work = integer(o, "flight_work", 1, MAX_WORK);
        if (!o.has("returns") || !o.get("returns").isJsonArray()) throw new IllegalArgumentException("returns: a list expected");
        JsonArray arr = o.getAsJsonArray("returns");
        if (arr.isEmpty()) throw new IllegalArgumentException("returns: at least one return");
        List<Return> returns = new ArrayList<>();
        for (JsonElement r : arr) {
            if (!r.isJsonObject()) throw new IllegalArgumentException("returns: each return is an object");
            JsonObject ro = r.getAsJsonObject();
            String item = id(string(ro, "item"));
            int count = ro.has("count") ? integer(ro, "count", 1, 64) : 1;
            String why = lookup.forbidden(item);
            if (why != null) throw new IllegalArgumentException("return " + item + " is " + why + "; a probe brings only sky materials");
            if (!lookup.registered(item)) throw new IllegalArgumentException("return " + item + " is not a registered item");
            AgeId itemAge = lookup.age(item);
            if (itemAge != null && itemAge.compareTo(age) > 0) {
                throw new IllegalArgumentException("return " + item + " belongs to " + itemAge.id() + ", later than the module's " + age.id());
            }
            returns.add(new Return(item, count));
        }
        return new Module(file, module, age, work, List.copyOf(returns));
    }

    /** Forbidden by namespace alone ({@code ad_astra}: the crewed trip stays the only source of its materials). */
    public static String forbiddenNamespace(String item) {
        return item.startsWith("ad_astra:") ? "an ad_astra item" : null;
    }

    private static String id(String s) {
        String v = s.trim();
        return v.contains(":") ? v : "minecraft:" + v;
    }

    private static String string(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + ": a string expected");
        }
        return o.get(key).getAsString();
    }

    private static int integer(JsonObject o, String key, int min, int max) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + ": a number expected");
        }
        double d = o.get(key).getAsDouble();
        if (d != Math.floor(d) || d < min || d > max) throw new IllegalArgumentException(key + ": a whole number from " + min + " to " + max + " expected");
        return (int) d;
    }
}
