package dev.firmages.core.shrine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The data-driven shrine content of one datapack load (SPEC §2.6): tiers, the generic fallback tier, the offering
 * map and the blessings, all from {@code data/<ns>/firmages_shrine/}. Immutable; pure Java (Gson only), so the
 * parser is unit-tested. Invalid files are rejected one by one and listed in {@link #errors()}.
 *
 * @param tiers     explicit tiers by number (0..8)
 * @param fallback  definition for every tier without an own file, so the structure never blocks progression
 * @param offerings Age stage (whose signature item it is) to item id, e.g. {@code age_0 -> firmages:hearthstone}
 * @param blessings by id
 * @param consecration the ring block roles of {@code consecration.json} (SPEC §17)
 */
public record ShrineData(Map<Integer, ShrineTier> tiers, Optional<ShrineTier> fallback, Map<String, String> offerings,
                         Map<String, Blessing> blessings, List<String> errors, List<String> warnings, Consecration consecration) {

    /** Tiers are 0..8: tier N is worked in age_N and grants age_(N+1); age_9 has no tier (the Gathering is M8). */
    public static final int MAX_TIER = 8;

    public static final ShrineData EMPTY = new ShrineData(Map.of(), Optional.empty(), Map.of(), Map.of(), List.of(), List.of(), Consecration.EMPTY);

    private static final Pattern RING_FILE = Pattern.compile("tier/ring_(\\d+)");
    private static final Pattern COLOR = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    /** The definition for {@code tier}: its own file, else the fallback (renumbered), else empty. */
    public Optional<ShrineTier> tier(int tier) {
        if (tier < 0 || tier > MAX_TIER) return Optional.empty();
        ShrineTier own = tiers.get(tier);
        if (own != null) return Optional.of(own);
        return fallback.map(f -> f.asTier(tier));
    }

    /** The highest tier that has an own ring multiblock, or -1. Rings above it are not required. */
    public int highestRing() {
        return tiers.values().stream().filter(t -> t.multiblock().isPresent()).mapToInt(ShrineTier::tier).max().orElse(-1);
    }

    /** Item id the tier expects: the tier's own {@code offering}, else the offering map entry for {@code age_N}. */
    public Optional<String> offeringFor(int tier) {
        return tier(tier).flatMap(ShrineTier::offering).or(() -> Optional.ofNullable(offerings.get("age_" + tier)));
    }

    /** The {@code relic_returned} rite of {@code tier} that lends the relic of {@code relicTier}, if any (SPEC §7.4). */
    public Optional<ShrineTier.Rite> lendingRite(int tier, int relicTier) {
        return tier(tier).stream().flatMap(t -> t.rites().stream())
            .filter(r -> r.type() == ShrineTier.Rite.Type.RELIC_RETURNED && r.relicTier() == relicTier).findFirst();
    }

    /** Items every tier accepts back for the lent relic of {@code relicTier}, besides the relic's own item. */
    public Set<String> returnItems(int relicTier) {
        Set<String> out = new LinkedHashSet<>();
        for (int k = 0; k <= MAX_TIER; k++) lendingRite(k, relicTier).ifPresent(r -> out.addAll(r.items()));
        return out;
    }

    // ---------------------------------------------------------------- parsing

    /**
     * @param files resource id path inside {@code firmages_shrine/} (for example {@code tier/ring_0},
     *              {@code offerings}, {@code blessing/hearthward}) with its namespace, as {@code ns:path}, sorted
     *              so that a later namespace overrides an earlier one deterministically
     */
    public static ShrineData parse(Map<String, JsonElement> files) {
        Map<Integer, ShrineTier> tiers = new TreeMap<>();
        ShrineTier fallback = null;
        Map<String, String> offerings = new TreeMap<>();
        Map<String, Blessing> blessings = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Consecration consecration = Consecration.EMPTY;
        for (Map.Entry<String, JsonElement> e : new TreeMap<>(files).entrySet()) {
            String id = e.getKey();
            String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            try {
                if (!e.getValue().isJsonObject()) throw new IllegalArgumentException("not a JSON object");
                JsonObject o = e.getValue().getAsJsonObject();
                if (path.equals("offerings")) {
                    for (Map.Entry<String, JsonElement> m : o.entrySet()) {
                        if (!m.getKey().matches("age_[0-8]")) throw new IllegalArgumentException("offering key '" + m.getKey() + "' is not age_0..age_8");
                        offerings.put(m.getKey(), itemId(m.getValue().getAsString()));
                    }
                } else if (path.equals("consecration")) {
                    Consecration c = Consecration.parse(o);
                    c.errors().forEach(err -> errors.add(id + ": " + err));
                    consecration = c;
                } else if (path.equals("tier/fallback")) {
                    fallback = parseTier(o, -1, true, warnings, id);
                } else if (path.startsWith("tier/")) {
                    Matcher m = RING_FILE.matcher(path);
                    int fromName = m.matches() ? Integer.parseInt(m.group(1)) : -1;
                    int tier = o.has("tier") ? o.get("tier").getAsInt() : fromName;
                    if (tier < 0 || tier > MAX_TIER) throw new IllegalArgumentException("tier must be 0.." + MAX_TIER + " (field 'tier' or file name ring_<N>)");
                    if (tiers.containsKey(tier)) warnings.add(id + " overrides an earlier definition of tier " + tier);
                    tiers.put(tier, parseTier(o, tier, false, warnings, id));
                } else if (path.startsWith("blessing/")) {
                    String name = ns + ":" + path.substring("blessing/".length());
                    Blessing b = parseBlessing(name, o);
                    for (String f : b.flags()) {
                        if (!Blessing.SUPPORTED_FLAGS.contains(f)) warnings.add(id + ": blessing flag '" + f + "' is not implemented yet");
                    }
                    blessings.put(name, b);
                } else {
                    warnings.add(id + ": unknown shrine data file, ignored");
                }
            } catch (RuntimeException ex) {
                errors.add(id + ": " + ex.getMessage());
            }
        }
        for (ShrineTier t : tiers.values()) {
            t.blessing().filter(b -> !blessings.containsKey(b)).ifPresent(b -> warnings.add("tier " + t.tier() + " names unknown blessing " + b));
        }
        return new ShrineData(Map.copyOf(tiers), Optional.ofNullable(fallback), Map.copyOf(offerings), Map.copyOf(blessings),
            List.copyOf(errors), List.copyOf(warnings), consecration);
    }

    static ShrineTier parseTier(JsonObject o, int tier, boolean fallback, List<String> warnings, String id) {
        Optional<String> multiblock = optString(o, "multiblock").map(ShrineData::resourceId);
        if (!fallback && multiblock.isEmpty()) throw new IllegalArgumentException("'multiblock' is required (only tier/fallback may omit it)");
        if (fallback && multiblock.isPresent()) throw new IllegalArgumentException("the fallback tier has no own ring; remove 'multiblock'");
        if (!fallback && o.has("grants")) {
            String grants = o.get("grants").getAsString();
            if (!grants.equals("age_" + (tier + 1))) throw new IllegalArgumentException("tier " + tier + " grants age_" + (tier + 1) + ", not " + grants);
        }
        String key = optString(o, "plinth_key").orElse(String.valueOf(ShrineTier.DEFAULT_PLINTH_KEY));
        if (key.length() != 1) throw new IllegalArgumentException("'plinth_key' must be one character");
        int radius = o.has("plinth_radius") ? o.get("plinth_radius").getAsInt() : ShrineTier.DEFAULT_PLINTH_RADIUS;
        if (radius < 1 || radius > 64) throw new IllegalArgumentException("'plinth_radius' must be 1..64");
        Optional<String> offering = optString(o, "offering").map(ShrineData::itemId);
        List<ShrineTier.Rite> rites = new ArrayList<>();
        if (o.has("rites")) {
            for (JsonElement r : o.getAsJsonArray("rites")) rites.add(parseRite(r.getAsJsonObject()));
        }
        for (ShrineTier.Rite r : rites) {
            if (!r.type().supported()) warnings.add(id + ": rite '" + r.type().name().toLowerCase(Locale.ROOT) + "' is not implemented yet and does not block the prayer");
            if (r.type() == ShrineTier.Rite.Type.RELIC_RETURNED && !fallback && r.relicTier() >= tier) {
                throw new IllegalArgumentException("rite relic_returned of tier " + tier + " must lend an earlier relic, not tier " + r.relicTier());
            }
        }
        OptionalInt prayerTicks = OptionalInt.empty();
        if (o.has("prayer_ticks")) {
            int p = o.get("prayer_ticks").getAsInt();
            if (p < 1) throw new IllegalArgumentException("'prayer_ticks' must be positive");
            prayerTicks = OptionalInt.of(p);
        }
        ShrineTier.Response response = o.has("response") ? parseResponse(o.getAsJsonObject("response")) : ShrineTier.Response.DEFAULT;
        Optional<String> blessing = optString(o, "blessing").map(ShrineData::resourceId);
        return new ShrineTier(tier, multiblock, key.charAt(0), radius, offering, List.copyOf(rites), prayerTicks, response, blessing, fallback);
    }

    static ShrineTier.Rite parseRite(JsonObject r) {
        String typeName = r.get("type").getAsString();
        ShrineTier.Rite.Type type;
        try {
            type = ShrineTier.Rite.Type.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown rite type '" + typeName + "'");
        }
        String key = optString(r, "key").orElse("");
        String property = optString(r, "property").orElse("");
        String value = optString(r, "value").orElse("");
        String tag = optString(r, "tag").map(t -> t.startsWith("#") ? t.substring(1) : t).orElse("");
        long amount = r.has("amount") ? r.get("amount").getAsLong() : 0;
        int min = r.has("min") ? r.get("min").getAsInt() : 0;
        int relicTier = r.has("relic") ? r.get("relic").getAsInt() : -1;
        List<String> items = new ArrayList<>();
        if (r.has("items")) {
            for (JsonElement i : r.getAsJsonArray("items")) items.add(itemId(i.getAsString()));
        }
        String hint = optString(r, "hint").orElse("");
        switch (type) {
            case BLOCKSTATE -> {
                if (key.length() != 1 || property.isEmpty() || value.isEmpty()) {
                    throw new IllegalArgumentException("rite blockstate needs 'key' (one character), 'property' and 'value'");
                }
            }
            case INTERACT -> {
                if (tag.isEmpty()) throw new IllegalArgumentException("rite interact needs a block 'tag'");
                tag = resourceId(tag);
            }
            case PLAYERS_PRAYING -> {
                if (min < 1) throw new IllegalArgumentException("rite players_praying needs 'min' >= 1");
            }
            case ENERGY -> {
                if (key.length() != 1 || amount < 1) {
                    throw new IllegalArgumentException("rite energy needs 'key' (one character: the ring blocks that store the FE) and 'amount' >= 1");
                }
            }
            case RELIC_RETURNED -> {
                if (relicTier < 0 || relicTier > MAX_TIER) throw new IllegalArgumentException("rite relic_returned needs 'relic' (the tier 0.." + MAX_TIER + " whose relic is lent)");
            }
            default -> { }
        }
        return new ShrineTier.Rite(type, key.isEmpty() ? ' ' : key.charAt(0), property, value, tag, amount, min, relicTier, List.copyOf(items), hint);
    }

    static ShrineTier.Response parseResponse(JsonObject r) {
        ShrineTier.Response d = ShrineTier.Response.DEFAULT;
        int beam = r.has("beam_color") ? color(r.get("beam_color").getAsString(), true) : d.beamColor();
        int sky = r.has("sky_tint") ? color(r.get("sky_tint").getAsString(), false) : d.skyTint();
        List<String> particles = new ArrayList<>();
        if (r.has("particles")) {
            JsonArray a = r.getAsJsonArray("particles");
            for (JsonElement p : a) particles.add(resourceId(p.getAsString()));
        } else {
            particles.addAll(d.particles());
        }
        String sting = optString(r, "sting").map(ShrineData::resourceId).orElse(d.sting());
        Optional<String> voice = optString(r, "voice");
        boolean lightning = r.has("lightning") && r.get("lightning").getAsBoolean();
        return new ShrineTier.Response(beam, sky, List.copyOf(particles), sting, voice, lightning);
    }

    static Blessing parseBlessing(String id, JsonObject o) {
        String path = id.substring(id.indexOf(':') + 1);
        String nameKey = optString(o, "name").orElse("firmages.blessing." + path);
        String descKey = optString(o, "description").orElse("firmages.blessing." + path + ".description");
        Set<String> flags = new LinkedHashSet<>();
        if (o.has("flags")) {
            for (JsonElement f : o.getAsJsonArray("flags")) flags.add(f.getAsString());
        }
        List<Blessing.Effect> effects = new ArrayList<>();
        if (o.has("effects")) {
            for (JsonElement e : o.getAsJsonArray("effects")) effects.add(parseEffect(e.getAsJsonObject()));
        }
        return new Blessing(id, nameKey, descKey, Set.copyOf(flags), List.copyOf(effects));
    }

    static Blessing.Effect parseEffect(JsonObject e) {
        String typeName = e.get("type").getAsString();
        double amount = e.has("amount") ? e.get("amount").getAsDouble() : 0;
        return switch (typeName) {
            case "attribute" -> {
                String attr = optString(e, "attribute").map(ShrineData::resourceId)
                    .orElseThrow(() -> new IllegalArgumentException("effect attribute needs 'attribute'"));
                String op = optString(e, "operation").orElse("add_value");
                if (!Blessing.Effect.OPERATIONS.contains(op)) throw new IllegalArgumentException("effect attribute: unknown operation '" + op + "'");
                if (amount == 0 || Double.isNaN(amount) || Math.abs(amount) > 1024) throw new IllegalArgumentException("effect attribute needs a non-zero 'amount'");
                yield new Blessing.Effect(Blessing.Effect.Kind.ATTRIBUTE, attr, op, amount, 0);
            }
            case "mob_effect" -> {
                String eff = optString(e, "effect").map(ShrineData::resourceId)
                    .orElseThrow(() -> new IllegalArgumentException("effect mob_effect needs 'effect'"));
                int amp = e.has("amplifier") ? e.get("amplifier").getAsInt() : 0;
                if (amp < 0 || amp > 9) throw new IllegalArgumentException("effect mob_effect: 'amplifier' must be 0..9");
                yield new Blessing.Effect(Blessing.Effect.Kind.MOB_EFFECT, eff, "", 0, amp);
            }
            case "xp_bonus" -> {
                if (!(amount > 0 && amount <= 10)) throw new IllegalArgumentException("effect xp_bonus needs 'amount' in (0, 10]");
                yield new Blessing.Effect(Blessing.Effect.Kind.XP_BONUS, "", "", amount, 0);
            }
            default -> throw new IllegalArgumentException("unknown blessing effect type '" + typeName + "'");
        };
    }

    /** {@code #RRGGBB} (alpha FF when {@code withAlpha}) or {@code #AARRGGBB}. */
    static int color(String s, boolean withAlpha) {
        Matcher m = COLOR.matcher(s.trim());
        if (!m.matches()) throw new IllegalArgumentException("colour '" + s + "' is not #RRGGBB or #AARRGGBB");
        long v = Long.parseLong(m.group(1), 16);
        if (m.group(1).length() == 6 && withAlpha) v |= 0xFF000000L;
        if (!withAlpha) v &= 0xFFFFFFL;
        return (int) v;
    }

    private static Optional<String> optString(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? Optional.of(o.get(key).getAsString()) : Optional.empty();
    }

    private static String itemId(String s) {
        return resourceId(s);
    }

    /** {@code ns:path} with lower-case characters; a missing namespace means {@code minecraft}. */
    static String resourceId(String s) {
        String v = s.trim();
        if (!v.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) throw new IllegalArgumentException("'" + s + "' is not a resource id");
        return v.contains(":") ? v : "minecraft:" + v;
    }
}
