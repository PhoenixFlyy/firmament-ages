package dev.firmages.core.wayshrine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.shrine.ShrineData;
import dev.firmages.core.shrine.ShrineTier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The data-driven Wayshrine content of one datapack load (SPEC-himmel §28.6), from {@code data/<ns>/firmages_wayshrine/}:
 * the types ({@code type/<type>.json}), the offering of each Age ({@code offerings.json}) and the lighting rite of each Age
 * ({@code rite/<age>.json}). Pure Java (Gson only), so the parser is unit-tested. Files sort by id, so a later namespace
 * overrides an earlier one; a bad file is rejected on its own and listed in {@link #errors()}.
 *
 * @param offerings Age id (age_1..age_9) -> item id
 * @param rites     Age id -> the rites that light a Wayshrine in that Age
 */
public record WayshrineData(Map<String, Type> types, Map<String, String> offerings, Map<String, List<ShrineTier.Rite>> rites,
                            List<String> errors, List<String> warnings) {

    public static final WayshrineData EMPTY = new WayshrineData(Map.of(), Map.of(), Map.of(), List.of(), List.of());
    /** Rites a Wayshrine can check; the shrine-only ones ({@code interact}, {@code relic_returned}) are rejected. */
    public static final Set<ShrineTier.Rite.Type> RITE_TYPES = Set.of(ShrineTier.Rite.Type.BLOCKSTATE, ShrineTier.Rite.Type.SKY,
        ShrineTier.Rite.Type.PLAYERS_PRAYING, ShrineTier.Rite.Type.ENERGY);
    private static final Pattern AGE_KEY = Pattern.compile("age_[1-9]");
    private static final Pattern COLOR = Pattern.compile("#?([0-9a-fA-F]{6})");
    private static final Pattern TYPE_ID = Pattern.compile("[a-z0-9_]+");

    /**
     * One Wayshrine type.
     *
     * @param id         the file name ({@code forest_ring}); the core's block entity names its type with it
     * @param structure  the structure template of the ruin ({@code firmages:wayshrine/forest_ring})
     * @param multiblock the restored form ({@code firmages:wayshrine_forest_ring})
     * @param minAge     the first Age in which it can be restored
     * @param lightKey   the pattern key of the light
     * @param ruinBeam   RGB of the faint beam of a ruin
     */
    public record Type(String id, String structure, String multiblock, AgeId minAge, char lightKey, int ruinBeam) {}

    public Optional<Type> type(String id) {
        return Optional.ofNullable(types.get(id));
    }

    /** The offering for the current Age: the highest key at or below it (age_8 uses age_6), empty below age_1. */
    public Optional<String> offeringFor(AgeId current) {
        return atOrBelow(offerings, current);
    }

    /** The lighting rites for the current Age (the highest key at or below it). */
    public List<ShrineTier.Rite> ritesFor(AgeId current) {
        return atOrBelow(rites, current).orElse(List.of());
    }

    /** The Age key whose entry applies in {@code current}, or empty. */
    public static <T> Optional<T> atOrBelow(Map<String, T> byAge, AgeId current) {
        for (int i = current.ordinal(); i >= AgeId.AGE_1.ordinal(); i--) {
            T v = byAge.get(AgeId.all().get(i).id());
            if (v != null) return Optional.of(v);
        }
        return Optional.empty();
    }

    /**
     * @param files id {@code ns:path} inside {@code firmages_wayshrine/} ({@code ns:type/forest_ring}, {@code ns:offerings},
     *              {@code ns:rite/age_1}) -> JSON
     */
    public static WayshrineData parse(Map<String, JsonElement> files) {
        Map<String, Type> types = new TreeMap<>();
        Map<String, String> offerings = new TreeMap<>();
        Map<String, List<ShrineTier.Rite>> rites = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : new TreeMap<>(files).entrySet()) {
            String id = e.getKey();
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            try {
                if (!e.getValue().isJsonObject()) throw new IllegalArgumentException("not a JSON object");
                JsonObject o = e.getValue().getAsJsonObject();
                if (path.equals("offerings")) {
                    Map<String, String> parsed = new TreeMap<>();
                    for (Map.Entry<String, JsonElement> m : o.entrySet()) {
                        if (!AGE_KEY.matcher(m.getKey()).matches()) throw new IllegalArgumentException("offering key '" + m.getKey() + "' is not age_1..age_9");
                        parsed.put(m.getKey(), resourceId(m.getValue().getAsString()));
                    }
                    offerings.putAll(parsed);
                } else if (path.startsWith("rite/")) {
                    String age = path.substring("rite/".length());
                    if (!AGE_KEY.matcher(age).matches()) throw new IllegalArgumentException("rite file name '" + age + "' is not age_1..age_9");
                    List<ShrineTier.Rite> list = new ArrayList<>();
                    if (!o.has("rites") || !o.get("rites").isJsonArray()) throw new IllegalArgumentException("'rites' must be an array");
                    for (JsonElement r : o.getAsJsonArray("rites")) {
                        ShrineTier.Rite rite = ShrineData.parseRite(r.getAsJsonObject());
                        if (!RITE_TYPES.contains(rite.type())) {
                            throw new IllegalArgumentException("rite type '" + rite.type().name().toLowerCase(java.util.Locale.ROOT) + "' is not used at Wayshrines");
                        }
                        list.add(rite);
                    }
                    rites.put(age, List.copyOf(list));
                } else if (path.startsWith("type/")) {
                    String tid = path.substring("type/".length());
                    if (!TYPE_ID.matcher(tid).matches()) throw new IllegalArgumentException("type id '" + tid + "' must be [a-z0-9_]");
                    if (types.containsKey(tid)) warnings.add(id + " overrides an earlier definition of type " + tid);
                    types.put(tid, parseType(tid, o));
                } else {
                    warnings.add(id + ": unknown Wayshrine data file, ignored");
                }
            } catch (RuntimeException ex) {
                errors.add(id + ": " + ex.getMessage());
            }
        }
        return new WayshrineData(Map.copyOf(types), Map.copyOf(offerings), Map.copyOf(rites), List.copyOf(errors), List.copyOf(warnings));
    }

    static Type parseType(String tid, JsonObject o) {
        String structure = resourceId(required(o, "structure"));
        String multiblock = resourceId(required(o, "multiblock"));
        String age = required(o, "min_age");
        AgeId minAge = AgeId.byId(age).filter(a -> a.ordinal() >= AgeId.AGE_1.ordinal())
            .orElseThrow(() -> new IllegalArgumentException("'min_age' " + age + " is not age_1..age_9"));
        String key = o.has("light_key") ? o.get("light_key").getAsString() : "L";
        if (key.length() != 1 || key.equals("0") || key.equals("_")) throw new IllegalArgumentException("'light_key' must be one character other than 0 and _");
        int beam = 0xD8E6F0;
        if (o.has("ruin_beam")) {
            Matcher m = COLOR.matcher(o.get("ruin_beam").getAsString().trim());
            if (!m.matches()) throw new IllegalArgumentException("'ruin_beam' is not #RRGGBB");
            beam = Integer.parseInt(m.group(1), 16);
        }
        return new Type(tid, structure, multiblock, minAge, key.charAt(0), beam);
    }

    private static String required(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) throw new IllegalArgumentException("'" + key + "' is required");
        return o.get(key).getAsString();
    }

    /** {@code ns:path}, lower case; a missing namespace means {@code minecraft}. */
    static String resourceId(String s) {
        String v = s.trim();
        if (!v.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) throw new IllegalArgumentException("'" + s + "' is not a resource id");
        return v.contains(":") ? v : "minecraft:" + v;
    }
}
