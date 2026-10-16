package dev.firmages.core.balance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ore ladder measured from recipe JSON (SPEC §38): the same logic as the {@code L-} checks of
 * {@code dev/poc_analyze.py} on the metal table ({@code data/firmages/balance/metals.json}, a copy of
 * {@code kubejs/data/firmages/balance/metals.json}). Every recipe that consumes an ore piece of the table yields some metal in mB; the
 * factor is that metal over the piece's metal ({@code grade_mb}). Each station of the table has an Age and a kind
 * (tech or magic); the best tech factor a piece has by an Age is the ladder. Pure Java (Gson only), unit-tested.
 */
public final class OreLadder {
    public static final List<String> AGES = List.of("dawn", "age_0", "age_1", "age_2", "age_3", "age_4", "age_5", "age_6", "age_7", "age_8", "age_9");
    private static final String FORMS = "ingot|nugget|dust|dirty_dust|clump|shard|crystal|sheet|double_ingot|double_sheet|rod|plate|block|"
        + "storage_block|raw|alchemical_sulfur";
    private static final Set<String> YIELD_KEYS = Set.of("result", "results", "output", "outputs", "secondaries", "result_fluid", "output_fluid",
        "output_item", "result_item", "chemical_output");
    private static final Set<String> RESULT_KEYS = Set.of("result", "results", "output", "outputs", "ritual_dummy", "entity_to_summon",
        "secondaryOutputs", "secondaries", "slag", "byproducts", "spellData", "target", "trader_id", "table", "highlight", "entity_outputs", "item_outputs");
    private static final Set<String> WORLD_TYPES = Set.of("immersiveengineering:mineral_mix", "occultism:miner", "tfc:collapse", "tfc:landslide");
    private static final Pattern PIECE = Pattern.compile("\"item\":\"([a-z0-9_]+:ore/(?:small|poor|normal|rich)_[a-z_]+)\"");
    private static final Pattern POWDER = Pattern.compile("\"item\":\"([a-z0-9_]+:powder/[a-z_]+)\"");
    private static final Pattern BLOCK_FORM = Pattern.compile("(^|[:/_])(storage_)?block([/_]|$)");
    private static final Pattern SLURRY = Pattern.compile(":(dirty|clean)_");

    /** One station of the table. */
    public record Station(String key, String type, String age, String kind, double factor) {}

    /**
     * One measured recipe on one piece: the metal it yields directly over the piece's metal, and the metal of the quern
     * powder it yields (at the powder station's mB per powder, the Enrichment Chamber, counted from {@code powderAge}) over the same.
     */
    public record Measure(String piece, String metal, String recipe, String type, double direct, double powder, String powderAge) {
        public double factor() {
            return direct + powder;
        }
    }

    /** The best factor of a piece at a station (the station's effective Age is the later of its own and the metal's first). */
    public record Entry(String piece, String station, String age, String kind, double factor) {}

    private final Map<String, Integer> grades = new LinkedHashMap<>();
    private final Map<String, Station> stations = new LinkedHashMap<>();
    private final Map<String, Station> byType = new HashMap<>();
    private final Map<String, String> pieceMetal = new TreeMap<>();
    private final Map<String, String> pieceGrade = new HashMap<>();
    private final Map<String, String> metalFirst = new HashMap<>();
    private final Map<String, List<String>> metalStations = new HashMap<>();
    private final Map<String, Pattern[]> metalRes = new HashMap<>();
    private final Set<String> neutral;
    private final String powderType;
    private final String powderAge;
    private final double itemMb;
    private final double nuggetMb;
    private final double slurryMb;
    private final double tolerance;

    public OreLadder(JsonObject table) {
        table.getAsJsonObject("grade_mb").entrySet().forEach(e -> grades.put(e.getKey(), e.getValue().getAsInt()));
        tolerance = table.get("tolerance").getAsDouble();
        JsonObject units = table.getAsJsonObject("units");
        itemMb = units.get("item_mb").getAsDouble();
        nuggetMb = units.get("nugget_mb").getAsDouble();
        slurryMb = units.get("slurry_mb_per_ingot").getAsDouble();
        JsonObject powder = table.getAsJsonObject("powder");
        powderType = powder.get("station_type").getAsString();
        powderAge = powder.has("age") ? powder.get("age").getAsString() : "age_6"; // the Enrichment Chamber (station_ages.js)
        neutral = new java.util.HashSet<>();
        table.getAsJsonObject("neutral_types").getAsJsonArray("types").forEach(t -> neutral.add(t.getAsString()));
        for (Map.Entry<String, JsonElement> e : table.getAsJsonObject("stations").entrySet()) {
            JsonObject s = e.getValue().getAsJsonObject();
            Station st = new Station(e.getKey(), s.get("type").getAsString(), s.get("age").getAsString(), s.get("kind").getAsString(), s.get("factor").getAsDouble());
            stations.put(st.key(), st);
            byType.put(st.type(), st);
        }
        for (Map.Entry<String, JsonElement> e : table.getAsJsonObject("metals").entrySet()) {
            String m = e.getKey();
            JsonObject d = e.getValue().getAsJsonObject();
            metalFirst.put(m, d.get("first_age").getAsString());
            List<String> st = new ArrayList<>();
            d.getAsJsonArray("stations").forEach(x -> st.add(x.getAsString()));
            metalStations.put(m, st);
            List<String> aliases = new ArrayList<>();
            if (d.has("aliases")) d.getAsJsonArray("aliases").forEach(x -> aliases.add(x.getAsString()));
            else aliases.add(m);
            aliases.sort((a, b) -> b.length() - a.length());
            String al = String.join("|", aliases);
            metalRes.put(m, new Pattern[] {
                Pattern.compile("^[a-z0-9_]+:(?:.*/)?(?:" + FORMS + ")[/_](?:" + al + ")$|^[a-z0-9_]+:(?:.*/)?(?:raw_)?(?:" + al + ")_(?:ingot|nugget|dust|block|sheet|plate|rod)$"),
                Pattern.compile("^[a-z0-9_]+:(?:metal/(?:" + al + ")|molten_(?:" + al + ")|(?:dirty|clean)_(?:" + al + "))$")});
            for (JsonElement pre : d.getAsJsonArray("ores")) {
                for (String g : grades.keySet()) {
                    String piece = pre.getAsString().replace("{g}", g);
                    pieceMetal.put(piece, m);
                    pieceGrade.put(piece, g);
                }
            }
        }
    }

    public Map<String, Station> stations() {
        return stations;
    }

    public Set<String> pieces() {
        return pieceMetal.keySet();
    }

    public double tolerance() {
        return tolerance;
    }

    /** mB of {@code metal} in {@code amount} of output {@code id}. */
    double metalMb(String metal, String id, double amount) {
        Pattern[] re = metalRes.get(metal);
        if (re[1].matcher(id).matches()) return SLURRY.matcher(id).find() ? amount * itemMb / slurryMb : amount;
        if (re[0].matcher(id).matches()) {
            if (id.contains("nugget")) return amount * nuggetMb;
            return amount * itemMb * (BLOCK_FORM.matcher(id).find() ? 9 : 1);
        }
        return 0;
    }

    /** (id, expected amount) of every output: count x chance for items, mB for fluids and chemicals; IE wrappers multiply. */
    static List<Map.Entry<String, Double>> yields(JsonElement o) {
        List<Map.Entry<String, Double>> out = new ArrayList<>();
        yields(o, out, false, 1.0);
        return out;
    }

    private static void yields(JsonElement o, List<Map.Entry<String, Double>> out, boolean inres, double mult) {
        if (o.isJsonObject()) {
            JsonObject j = o.getAsJsonObject();
            String id = str(j, "id");
            if (id == null) id = str(j, "item");
            if (inres && id != null) {
                double n = num(j, "count", num(j, "amount", 1));
                double ch = num(j, "chance", 1.0);
                out.add(Map.entry(id, n * ch * mult));
                return;
            }
            if (inres) {
                if (isNum(j, "chance")) mult *= j.get("chance").getAsDouble();
                if (isNum(j, "count") && (j.get("basePredicate") instanceof JsonObject || j.get("output") instanceof JsonObject || j.get("stack") instanceof JsonObject)) {
                    mult *= j.get("count").getAsDouble();
                }
            }
            for (Map.Entry<String, JsonElement> e : j.entrySet()) yields(e.getValue(), out, inres || YIELD_KEYS.contains(e.getKey()), mult);
        } else if (o.isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray()) yields(e, out, inres, mult);
        }
    }

    /** How many of {@code item} a recipe consumes (Mekanism item_input count, sized ingredients), or 0. */
    static int inputCount(JsonElement o, String item, Integer parent) {
        if (o.isJsonObject()) {
            JsonObject j = o.getAsJsonObject();
            if (item.equals(str(j, "item"))) {
                double c = isNum(j, "count") ? j.get("count").getAsDouble() : parent == null ? 1 : parent;
                return c > 0 && c == Math.floor(c) ? (int) c : 1;
            }
            Integer own = isNum(j, "count") ? j.get("count").getAsInt() : null;
            for (Map.Entry<String, JsonElement> e : j.entrySet()) {
                if (YIELD_KEYS.contains(e.getKey()) || RESULT_KEYS.contains(e.getKey())) continue;
                boolean sized = e.getValue().isJsonObject() && (e.getKey().equals("ingredient") || e.getKey().equals("basePredicate"));
                int r = inputCount(e.getValue(), item, sized ? own : null);
                if (r > 0) return r;
            }
        } else if (o.isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray()) {
                int r = inputCount(e, item, parent);
                if (r > 0) return r;
            }
        }
        return 0;
    }

    /** The recipe JSON without its outputs, compact (the ore-piece and powder search runs on it). */
    private static String inputs(JsonObject j) {
        JsonObject c = new JsonObject();
        for (Map.Entry<String, JsonElement> e : j.entrySet()) {
            if (!YIELD_KEYS.contains(e.getKey()) && !RESULT_KEYS.contains(e.getKey())) c.add(e.getKey(), e.getValue());
        }
        return c.toString();
    }

    /**
     * Every ore-piece recipe of {@code recipes} (id -> JSON with a {@code type}) measured. Powder of a piece's recipe
     * counts at the powder station's mB once the powder recipe exists ({@code powder.station_type}).
     */
    public List<Measure> measure(Map<String, JsonObject> recipes) {
        Map<String, Double> powderMb = new HashMap<>();
        for (Map.Entry<String, JsonObject> e : recipes.entrySet()) {
            JsonObject j = e.getValue();
            if (!powderType.equals(str(j, "type"))) continue;
            Matcher m = POWDER.matcher(inputs(j));
            List<String> ins = new ArrayList<>();
            while (m.find()) ins.add(m.group(1));
            if (ins.size() != 1) continue;
            int n = Math.max(1, inputCount(j, ins.get(0), null));
            double val = 0;
            for (Map.Entry<String, Double> y : yields(j)) for (String metal : metalRes.keySet()) val += metalMb(metal, y.getKey(), y.getValue());
            if (val > 0) powderMb.merge(ins.get(0), val / n, Math::max);
        }
        List<Measure> out = new ArrayList<>();
        for (Map.Entry<String, JsonObject> e : recipes.entrySet()) {
            JsonObject j = e.getValue();
            String type = str(j, "type");
            if (type == null || WORLD_TYPES.contains(type)) continue;
            Matcher m = PIECE.matcher(inputs(j));
            java.util.Set<String> ins = new java.util.TreeSet<>();
            while (m.find()) ins.add(m.group(1));
            for (String p : ins) {
                String metal = pieceMetal.get(p);
                if (metal == null) continue;
                int n = Math.max(1, inputCount(j, p, null));
                double direct = 0, pw = 0;
                for (Map.Entry<String, Double> y : yields(j)) {
                    direct += metalMb(metal, y.getKey(), y.getValue());
                    pw += y.getValue() * powderMb.getOrDefault(y.getKey(), 0.0);
                }
                double base = n * grades.get(pieceGrade.get(p));
                out.add(new Measure(p, metal, e.getKey(), type, direct / base, pw / base, pw > 0 ? powderAge : null));
            }
        }
        return out;
    }

    /**
     * The ladder entries of every piece of the table: per station of its metal the best recipe by its direct factor, at
     * the later of the station's Age and the metal's first; a tech recipe that also yields quern powder adds a
     * {@code <station>+powder} entry with both, from the Age of the powder's furnace (L-1 of poc_analyze.py).
     */
    public List<Entry> entries(List<Measure> measures) {
        Map<String, Measure> best = bestPerStation(measures);
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, String> pm : pieceMetal.entrySet()) {
            String p = pm.getKey(), metal = pm.getValue();
            for (String k : metalStations.getOrDefault(metal, List.of())) {
                Station st = stations.get(k);
                Measure ms = st == null ? null : best.get(p + "|" + st.type());
                if (ms == null) continue;
                String eff = later(st.age(), metalFirst.get(metal));
                out.add(new Entry(p, k, eff, st.kind(), ms.direct()));
                if (ms.powder() > 0 && "tech".equals(st.kind())) out.add(new Entry(p, k + "+powder", later(eff, ms.powderAge()), "tech", ms.factor()));
            }
        }
        return out;
    }

    private static Map<String, Measure> bestPerStation(List<Measure> measures) {
        Map<String, Measure> best = new HashMap<>();
        for (Measure ms : measures) best.merge(ms.piece() + "|" + ms.type(), ms, (a, b) -> b.direct() > a.direct() ? b : a);
        return best;
    }

    /** Stations of the table whose best recipe on a piece leaves the target factor by more than the tolerance, or is missing (L-1). */
    public List<String> tableDeviations(List<Measure> measures) {
        Map<String, Measure> best = bestPerStation(measures);
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> pm : pieceMetal.entrySet()) {
            for (String k : metalStations.getOrDefault(pm.getValue(), List.of())) {
                Station st = stations.get(k);
                Measure ms = best.get(pm.getKey() + "|" + st.type());
                if (ms == null) bad.add(pm.getKey() + ": no " + k + " recipe (" + st.type() + ")");
                else if (Math.abs(ms.direct() - st.factor()) > tolerance * st.factor()) {
                    bad.add(String.format(java.util.Locale.ROOT, "%s: %s %.3f (%s), table %s", pm.getKey(), k, ms.direct(), ms.recipe(), st.factor()));
                }
            }
        }
        return bad;
    }

    /** Magic entries that yield more than the next tech step after their Age (L-3). */
    public List<String> magicAbove(List<Entry> entries) {
        List<String> bad = new ArrayList<>();
        Map<String, List<Entry>> byPiece = new TreeMap<>();
        for (Entry e : entries) byPiece.computeIfAbsent(e.piece(), k -> new ArrayList<>()).add(e);
        for (Map.Entry<String, List<Entry>> p : byPiece.entrySet()) {
            for (Entry e : p.getValue()) {
                if (!"magic".equals(e.kind())) continue;
                int next = Integer.MAX_VALUE;
                for (Entry x : p.getValue()) {
                    int ix = AGES.indexOf(x.age());
                    if ("tech".equals(x.kind()) && ix > AGES.indexOf(e.age())) next = Math.min(next, ix);
                }
                if (next == Integer.MAX_VALUE) continue;
                double top = 0;
                for (Entry x : p.getValue()) if ("tech".equals(x.kind()) && AGES.indexOf(x.age()) == next) top = Math.max(top, x.factor());
                if (e.factor() > top * (1 + tolerance)) {
                    bad.add(String.format(java.util.Locale.ROOT, "%s: %s at %s gives %.3f, the next tech step (%s) only %.3f", p.getKey(), e.station(),
                        e.age(), e.factor(), AGES.get(next), top));
                }
            }
        }
        return bad;
    }

    /** Recipes of a type outside the table (the neutral types included) that multiply ore: factor above 1 + tolerance (L-4). */
    public List<Measure> unlisted(List<Measure> measures) {
        List<Measure> out = new ArrayList<>();
        for (Measure ms : measures) {
            if (!byType.containsKey(ms.type()) && ms.factor() > 1.0 + tolerance) out.add(ms);
        }
        return out;
    }

    /** True for a type the table lists as taking ore without multiplying it (quern, millstone, grid recipes). */
    public boolean neutral(String type) {
        return neutral.contains(type);
    }

    /** Per Age: {lowest, highest} of each piece's best tech factor by that Age, the best magic factor, and the piece count. */
    public Map<String, double[]> perAge(List<Entry> entries) {
        Map<String, double[]> out = new LinkedHashMap<>();
        Map<String, List<Entry>> byPiece = new TreeMap<>();
        for (Entry e : entries) byPiece.computeIfAbsent(e.piece(), k -> new ArrayList<>()).add(e);
        for (String age : AGES.subList(1, AGES.size())) {
            double lo = Double.MAX_VALUE, hi = 0, magic = 0;
            int pieces = 0;
            for (List<Entry> es : byPiece.values()) {
                double best = 0;
                for (Entry e : es) {
                    if (AGES.indexOf(e.age()) > AGES.indexOf(age)) continue;
                    if ("tech".equals(e.kind())) best = Math.max(best, e.factor());
                    else magic = Math.max(magic, e.factor());
                }
                if (best > 0) {
                    lo = Math.min(lo, best);
                    hi = Math.max(hi, best);
                    pieces++;
                }
            }
            if (pieces > 0) out.put(age, new double[] {lo, hi, magic, pieces});
        }
        return out;
    }

    /** Pieces whose tech ladder steps back (a later station yields less than an earlier best). */
    public List<String> stepsBack(List<Entry> entries) {
        List<String> bad = new ArrayList<>();
        Map<String, List<Entry>> byPiece = new TreeMap<>();
        for (Entry e : entries) if ("tech".equals(e.kind())) byPiece.computeIfAbsent(e.piece(), k -> new ArrayList<>()).add(e);
        for (Map.Entry<String, List<Entry>> p : byPiece.entrySet()) {
            for (Entry e : p.getValue()) {
                double before = 0;
                for (Entry x : p.getValue()) if (AGES.indexOf(x.age()) < AGES.indexOf(e.age())) before = Math.max(before, x.factor());
                if (before > 0 && e.factor() < before * (1 - tolerance)) {
                    bad.add(String.format(java.util.Locale.ROOT, "%s: %s at %s gives %.3f, an earlier Age already %.3f", p.getKey(), e.station(), e.age(), e.factor(), before));
                }
            }
        }
        return bad;
    }

    static String later(String a, String b) {
        return AGES.indexOf(a) >= AGES.indexOf(b) ? a : b;
    }

    private static String str(JsonObject j, String k) {
        JsonElement v = j.get(k);
        return v instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    private static boolean isNum(JsonObject j, String k) {
        JsonElement v = j.get(k);
        return v instanceof JsonPrimitive p && p.isNumber();
    }

    private static double num(JsonObject j, String k, double def) {
        return isNum(j, k) ? j.get(k).getAsDouble() : def;
    }
}
