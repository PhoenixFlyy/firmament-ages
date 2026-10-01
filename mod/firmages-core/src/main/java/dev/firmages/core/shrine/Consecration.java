package dev.firmages.core.shrine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The consecration table of one datapack load (SPEC §17, M7): which consecrated block family role each ring block
 * becomes once Caelum accepts the ring's offering. Read from {@code data/<ns>/firmages_shrine/consecration.json}:
 *
 * <pre>{@code
 * {
 *   "roles":    { "#firmages:shrine/hearth_stones": "stone", "minecraft:bell": "metal" },
 *   "patterns": { "firmages:shrine_ring_1": { "G": "metal" } }
 * }
 * }</pre>
 *
 * {@code roles} maps a ring pattern matcher (a block tag with {@code #}, or a block id) to a role; {@code patterns}
 * overrides single pattern keys of one multiblock. The ring multiblocks stay the build recipe in Age materials; this
 * table only says what each block turns into. Pure Java (Gson only), unit-tested; also holds the block-state string
 * codec used for the stored originals.
 */
public record Consecration(Map<String, Role> roles, Map<String, Map<Character, Role>> patterns, List<String> errors) {

    /** The eight roles of the consecrated family; block id {@code firmages:consecrated_<id>}. */
    public enum Role {
        STONE, BRICK, PILLAR, LAMP, METAL, GLASS, TRIM, SCAFFOLD;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Role> byId(String id) {
            for (Role r : values()) {
                if (r.id().equals(id)) return Optional.of(r);
            }
            return Optional.empty();
        }

        /** See-through roles (cutout or translucent models, no occlusion). */
        public boolean transparent() {
            return this == GLASS || this == SCAFFOLD;
        }
    }

    /** Accent index range: the ring / Age index 0..8 (ring N is built in age_N). */
    public static final int MAX_ACCENT = ShrineData.MAX_TIER;

    public static final Consecration EMPTY = new Consecration(Map.of(), Map.of(), List.of());

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern PROP = Pattern.compile("[a-z0-9_]+");

    /** Parses {@code consecration.json}; bad entries are skipped and listed in {@link #errors()}. */
    public static Consecration parse(JsonObject o) {
        Map<String, Role> roles = new TreeMap<>();
        Map<String, Map<Character, Role>> patterns = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        for (String k : o.keySet()) {
            if (!k.equals("roles") && !k.equals("patterns")) errors.add("unknown key '" + k + "'");
        }
        if (o.has("roles")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("roles").entrySet()) {
                String matcher = e.getKey();
                String plain = matcher.startsWith("#") ? matcher.substring(1) : matcher;
                if (!ID.matcher(plain).matches()) {
                    errors.add("roles: '" + matcher + "' is not a block id or #tag");
                    continue;
                }
                Optional<Role> r = roleOf(e.getValue());
                if (r.isEmpty()) errors.add("roles: '" + matcher + "' has unknown role " + e.getValue());
                else roles.put(matcher, r.get());
            }
        }
        if (o.has("patterns")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("patterns").entrySet()) {
                if (!ID.matcher(e.getKey()).matches() || !e.getValue().isJsonObject()) {
                    errors.add("patterns: '" + e.getKey() + "' must be a multiblock id with an object of pattern keys");
                    continue;
                }
                Map<Character, Role> keys = new TreeMap<>();
                for (Map.Entry<String, JsonElement> k : e.getValue().getAsJsonObject().entrySet()) {
                    Optional<Role> r = roleOf(k.getValue());
                    if (k.getKey().length() != 1 || r.isEmpty()) errors.add("patterns: " + e.getKey() + " key '" + k.getKey() + "' -> " + k.getValue() + " is not one character -> role");
                    else keys.put(k.getKey().charAt(0), r.get());
                }
                patterns.put(e.getKey(), Map.copyOf(keys));
            }
        }
        return new Consecration(Map.copyOf(roles), Map.copyOf(patterns), List.copyOf(errors));
    }

    private static Optional<Role> roleOf(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? Role.byId(e.getAsString()) : Optional.empty();
    }

    /** The matcher string of one Modonomicon mapping entry: {@code #tag} for tag matchers, else the block id (state properties cut off). */
    public static Optional<String> matcherKey(JsonObject mapping) {
        if (mapping.has("tag")) {
            String t = mapping.get("tag").getAsString();
            return Optional.of(t.startsWith("#") ? t : "#" + t);
        }
        if (mapping.has("block")) {
            String b = mapping.get("block").getAsString();
            int bracket = b.indexOf('[');
            return Optional.of(bracket >= 0 ? b.substring(0, bracket) : b);
        }
        return Optional.empty();
    }

    /**
     * Roles of the pattern keys of a ring multiblock JSON ({@code mapping} object). Keys in {@code keep} (the heart
     * {@code 0} and the ring's plinth) and keys without an entry are absent: those blocks stay what they are.
     */
    public Map<Character, Role> rolesFor(String multiblockId, JsonObject multiblock, Set<Character> keep) {
        Map<Character, Role> out = new TreeMap<>();
        if (multiblock.has("mapping")) {
            for (Map.Entry<String, JsonElement> e : multiblock.getAsJsonObject("mapping").entrySet()) {
                if (e.getKey().length() != 1 || !e.getValue().isJsonObject()) continue;
                char k = e.getKey().charAt(0);
                if (keep.contains(k)) continue;
                matcherKey(e.getValue().getAsJsonObject()).map(roles::get).ifPresent(r -> out.put(k, r));
            }
        }
        Map<Character, Role> over = patterns.getOrDefault(multiblockId, Map.of());
        over.forEach((k, r) -> {
            if (!keep.contains(k)) out.put(k, r);
        });
        return Collections.unmodifiableMap(out);
    }

    /** Mapping keys of a ring that are neither kept nor given a role (they would stay Age material). */
    public List<Character> unmapped(String multiblockId, JsonObject multiblock, Set<Character> keep) {
        Map<Character, Role> roles = rolesFor(multiblockId, multiblock, keep);
        List<Character> out = new ArrayList<>();
        if (multiblock.has("mapping")) {
            for (String k : multiblock.getAsJsonObject("mapping").keySet()) {
                if (k.length() == 1 && !keep.contains(k.charAt(0)) && !roles.containsKey(k.charAt(0))) out.add(k.charAt(0));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- stored originals

    /** A block state as id plus properties (the original block of a consecrated position). */
    public record StateSpec(String block, Map<String, String> properties) {
        public StateSpec {
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
    }

    /** {@code ns:id[a=b,c=d]} with the properties sorted by name, the format of {@code BlockStateParser}. */
    public static String encodeState(StateSpec s) {
        if (!ID.matcher(s.block()).matches()) throw new IllegalArgumentException("bad block id " + s.block());
        if (s.properties().isEmpty()) return s.block();
        StringBuilder b = new StringBuilder(s.block()).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : s.properties().entrySet()) {
            if (!PROP.matcher(e.getKey()).matches() || !PROP.matcher(e.getValue()).matches()) {
                throw new IllegalArgumentException("bad property " + e.getKey() + "=" + e.getValue());
            }
            if (!first) b.append(',');
            b.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return b.append(']').toString();
    }

    /** Parses {@link #encodeState}; empty for anything malformed. */
    public static Optional<StateSpec> decodeState(String s) {
        if (s == null || s.isEmpty()) return Optional.empty();
        int open = s.indexOf('[');
        String id = open < 0 ? s : s.substring(0, open);
        if (!ID.matcher(id).matches()) return Optional.empty();
        Map<String, String> props = new LinkedHashMap<>();
        if (open >= 0) {
            if (!s.endsWith("]")) return Optional.empty();
            String inner = s.substring(open + 1, s.length() - 1);
            if (!inner.isEmpty()) {
                for (String part : inner.split(",", -1)) {
                    int eq = part.indexOf('=');
                    if (eq <= 0) return Optional.empty();
                    String k = part.substring(0, eq);
                    String v = part.substring(eq + 1);
                    if (!PROP.matcher(k).matches() || !PROP.matcher(v).matches() || props.containsKey(k)) return Optional.empty();
                    props.put(k, v);
                }
            }
        }
        return Optional.of(new StateSpec(id, props));
    }
}
