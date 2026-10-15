package dev.firmages.core.crown;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC §27.13 U: the 22 shipped {@code firmages:starforge} recipes against the chord library and the Age ladder of
 * §24.5 and {@code dev/age_map.toml} (the Ages below are copied from there: sky materials, TFC and pack metals, the
 * Arcane gems, the ring tags of §29.4). Pure JSON; the Minecraft codec runs in the GameTests and on the server.
 */
class StarforgeRecipeTest {
    private static final Path RES = Path.of("src/main/resources/data/firmages");
    private static final List<String> LENSES = List.of("stone", "bronze", "iron", "arcane", "industrial", "electric", "information", "space", "quantum");
    private static final List<String> ALLOYS = List.of("starlit_iron", "cloudsteel", "aurora_steel", "halo_steel", "void_steel", "quasar_alloy",
        "firmament_alloy");
    /** Items and tags the recipes name -> their Age number (SPEC §24.2, §24.5, dev/age_map.toml [materials]). */
    private static final Map<String, Integer> AGE = new HashMap<>();
    /** The ids firmages-core registers in M12; every other id a recipe names must be guarded by a condition. */
    private static final Set<String> OWN = new HashSet<>();

    static {
        AGE.put("firmages:meteoric_iron_ingot", 2);
        AGE.put("firmages:storm_glass", 2);
        AGE.put("firmages:firmament_crystal", 4);
        AGE.put("firmages:starsteel_ingot", 5);
        AGE.put("firmages:voidglass", 7);
        AGE.put("firmages:deep_space_sample", 8);
        AGE.put("firmages:lens_blank", 3);
        AGE.put("evolvedmekanism:alloy_singular", 8);
        AGE.put("#firmages:crown/arcane_gems", 3);
        AGE.put("#c:ingots/black_steel", 4);
        AGE.put("#c:sheets/black_steel", 4);
        AGE.put("#c:ingots/red_steel", 5);
        AGE.put("#c:plates/aluminum", 5);
        AGE.put("#c:ingots/blue_steel", 6);
        AGE.put("#c:sheets/blue_steel", 6);
        AGE.put("#c:ingots/desh", 7);
        AGE.put("#c:plates/desh", 7);
        AGE.put("#c:ingots/calorite", 8);
        AGE.put("#c:plates/calorite", 8);
        for (int n = 0; n <= 8; n++) AGE.put("#firmages:anchor/ring_" + n, n); // the ring of tier N stands by age_N
        for (int n = 0; n < LENSES.size(); n++) AGE.put("firmages:lens/" + LENSES.get(n), lensAge(n));
        for (int n = 0; n < ALLOYS.size(); n++) AGE.put("firmages:" + ALLOYS.get(n) + "_ingot", n + 3);
        OWN.add("firmages:lens_blank");
        LENSES.forEach(l -> OWN.add("firmages:lens/" + l));
        ALLOYS.forEach(a -> OWN.add("firmages:" + a + "_ingot"));
    }

    /** Lens N can be made once tier N is awakened, which is age_(N+1); the first three come with the Crown in age_3. */
    private static int lensAge(int n) {
        return Math.max(3, n + 1);
    }

    private static Map<String, JsonObject> recipes() throws IOException {
        Map<String, JsonObject> out = new TreeMap<>();
        try (Stream<Path> files = Files.list(RES.resolve("recipe/starforge"))) {
            for (Path p : files.toList()) out.put(p.getFileName().toString(), JsonParser.parseString(Files.readString(p)).getAsJsonObject());
        }
        return out;
    }

    private static String key(JsonObject ingredient) {
        return ingredient.has("item") ? ingredient.get("item").getAsString() : "#" + ingredient.get("tag").getAsString();
    }

    private static int resultAge(JsonObject r) {
        JsonObject res = r.getAsJsonObject("result");
        String id = res.get("id").getAsString();
        if (id.equals("firmages:blessing_shard")) return res.getAsJsonObject("components").get("firmages:tier").getAsInt() == 8 ? 8 : 7;
        Integer a = AGE.get(id);
        if (a == null) throw new AssertionError("no Age for result " + id);
        return a;
    }

    @Test
    void shippedRecipesParse() throws IOException {
        ChordLibrary lib = ChordLibraryTest.shipped();
        Map<String, JsonObject> all = recipes();
        assertEquals(22, all.size(), "22 library recipes");
        assertTrue(all.size() <= 25, "the cap is 25");
        Set<String> lists = new HashSet<>();
        for (Map.Entry<String, JsonObject> e : all.entrySet()) {
            String f = e.getKey();
            JsonObject r = e.getValue();
            assertEquals("firmages:starforge", r.get("type").getAsString(), f);
            Chord chord = lib.get(r.get("chord").getAsString()).orElseThrow(() -> new AssertionError(f + ": unknown chord"));
            JsonArray in = r.getAsJsonArray("ingredients");
            assertTrue(in.size() >= 1 && in.size() <= 4, f + ": one to four ingredients");
            List<String> list = new ArrayList<>();
            for (JsonElement x : in) {
                JsonObject o = x.getAsJsonObject();
                assertTrue(o.has("item") ^ o.has("tag"), f + ": item or tag");
                int count = o.has("count") ? o.get("count").getAsInt() : 1;
                assertTrue(count >= 1, f + ": count");
                list.add(key(o) + "x" + count);
            }
            list.sort(null);
            assertTrue(lists.add(String.join("|", list)), f + ": repeats another recipe's ingredient list");
            int time = r.has("time") ? r.get("time").getAsInt() : 100;
            assertTrue(time >= 20 && time <= 1200, f + ": time");
            assertTrue(r.getAsJsonObject("result").has("id"), f + ": result");
            if (r.has("fluid")) assertTrue(r.getAsJsonObject("fluid").get("amount").getAsInt() > 0, f + ": fluid amount");

            int age = resultAge(r);
            for (int lens : chord.lenses()) assertTrue(lensAge(lens) <= age, f + ": lens " + lens + " comes after the result's Age " + age);
            for (JsonElement x : in) {
                Integer a = AGE.get(key(x.getAsJsonObject()));
                assertTrue(a != null, f + ": no Age for " + key(x.getAsJsonObject()));
                assertTrue(a <= age, f + ": " + key(x.getAsJsonObject()) + " (age_" + a + ") after the result (age_" + age + ")");
            }
            if (r.has("fluid")) {
                assertEquals("firmages:aurora_light", r.getAsJsonObject("fluid").get("fluid").getAsString(), f);
                assertTrue(age >= 5, f + ": aurora light only from age_5 on");
            }
        }
    }

    @Test
    void oneAlloyPerAgeInItsTag() throws IOException {
        Map<Integer, Integer> perAge = new TreeMap<>();
        for (Map.Entry<String, JsonObject> e : recipes().entrySet()) {
            String id = e.getValue().getAsJsonObject("result").get("id").getAsString();
            int i = ALLOYS.indexOf(id.replace("firmages:", "").replace("_ingot", ""));
            if (i < 0) continue;
            perAge.merge(i + 3, 1, Integer::sum);
            JsonObject tag = JsonParser.parseString(Files.readString(RES.resolve("tags/item/celestial_alloy/age_" + (i + 3) + ".json"))).getAsJsonObject();
            assertEquals(List.of(id), tag.getAsJsonArray("values").asList().stream().map(JsonElement::getAsString).toList(), "tag of " + id);
        }
        Map<Integer, Integer> expected = new TreeMap<>();
        for (int a = 3; a <= 9; a++) expected.put(a, 1);
        assertEquals(expected, perAge, "exactly one alloy recipe per Age from age_3 to age_9");
    }

    @Test
    void foreignIdsAreGuardedByConditions() throws IOException {
        for (Map.Entry<String, JsonObject> e : recipes().entrySet()) {
            JsonObject r = e.getValue();
            Set<String> guarded = new HashSet<>();
            if (r.has("neoforge:conditions")) {
                for (JsonElement c : r.getAsJsonArray("neoforge:conditions")) guarded.add(c.getAsJsonObject().get("item").getAsString());
            }
            List<String> named = new ArrayList<>();
            for (JsonElement x : r.getAsJsonArray("ingredients")) if (x.getAsJsonObject().has("item")) named.add(x.getAsJsonObject().get("item").getAsString());
            named.add(r.getAsJsonObject("result").get("id").getAsString());
            for (String id : named) {
                assertTrue(OWN.contains(id) || guarded.contains(id), e.getKey() + ": " + id + " is not registered by the Crown and has no item_exists condition");
            }
            if (r.has("fluid")) assertTrue(guarded.contains("firmages:aurora_light_bucket"), e.getKey() + ": the fluid needs its bucket condition");
        }
    }
}
