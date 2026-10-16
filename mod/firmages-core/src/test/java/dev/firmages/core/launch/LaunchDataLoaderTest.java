package dev.firmages.core.launch;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §31.10 U: the fuel and module files, the forbidden returns, the Age order, unknown modules. */
class LaunchDataLoaderTest {
    private static final String FUEL = """
        { "fluid_tag": "ad_astra:fuel", "launch_mb": 4000,
          "throttle": [ { "burn": 1, "progress": 1 }, { "burn": 3, "progress": 2 }, { "burn": 8, "progress": 4 } ] }""";
    private static final String SAMPLER = """
        { "module": "firmages:sampler_module", "age": "age_7", "flight_work": 12000,
          "returns": [ { "item": "firmages:voidglass", "count": 4 } ] }""";
    private static final String DEEP = """
        { "module": "firmages:deep_space_module", "age": "age_8", "flight_work": 24000,
          "returns": [ { "item": "firmages:deep_space_sample", "count": 2 }, { "item": "firmages:voidglass", "count": 2 } ] }""";

    /** Registered items, their Ages, and the ores/raw materials of the test world. */
    private static final LaunchData.Lookup LOOKUP = new LaunchData.Lookup() {
        final Set<String> items = Set.of("firmages:sampler_module", "firmages:deep_space_module", "firmages:voidglass", "firmages:deep_space_sample",
            "ad_astra:desh_ore", "minecraft:raw_iron", "firmages:meteorite", "firmages:quasar_alloy_ingot");
        final Map<String, AgeId> ages = Map.of("firmages:voidglass", AgeId.AGE_7, "firmages:deep_space_sample", AgeId.AGE_8,
            "firmages:quasar_alloy_ingot", AgeId.AGE_8);

        @Override
        public boolean registered(String item) {
            return items.contains(item);
        }

        @Override
        public String forbidden(String item) {
            String ns = LaunchData.forbiddenNamespace(item);
            if (ns != null) return ns;
            if (item.equals("minecraft:raw_iron")) return "in #c:raw_materials";
            if (item.equals("firmages:meteorite")) return "in #firmages:age_blocks/age_2";
            return null;
        }

        @Override
        public AgeId age(String item) {
            return ages.get(item);
        }
    };

    private static JsonElement json(String s) {
        return JsonParser.parseString(s);
    }

    private static LaunchData load(Map<String, String> modules) {
        Map<String, JsonElement> m = new HashMap<>();
        modules.forEach((k, v) -> m.put(k, json(v)));
        return LaunchData.parse(Map.of("firmages:fuel", json(FUEL)), m, LOOKUP);
    }

    @Test
    void specFilesLoad() {
        LaunchData d = load(Map.of("firmages:module/sampler", SAMPLER, "firmages:module/deep_space", DEEP));
        assertTrue(d.valid());
        assertEquals(java.util.List.of(), d.errors());
        assertEquals("ad_astra:fuel", d.fuel().get().fluidTag());
        assertEquals(4000, d.fuel().get().launchMb());
        assertEquals(LaunchRules.defaultSteps(), d.steps());
        LaunchData.Module s = d.module("firmages:sampler_module").orElseThrow();
        assertEquals(12_000, s.flightWork());
        assertEquals(java.util.List.of(new LaunchData.Return("firmages:voidglass", 4)), s.returns());
        assertEquals(2, d.module("firmages:deep_space_module").orElseThrow().returns().size());
    }

    private static String sampler(String item) {
        return SAMPLER.replace("firmages:voidglass", item);
    }

    @Test
    void oreReturnRejected() {
        for (String bad : new String[] {"ad_astra:desh_ore", "minecraft:raw_iron", "firmages:meteorite"}) {
            LaunchData d = load(Map.of("firmages:module/sampler", sampler(bad)));
            assertTrue(d.module("firmages:sampler_module").isEmpty(), bad);
            assertEquals(1, d.errors().size(), bad);
            assertTrue(d.errors().get(0).contains(bad), d.errors().get(0));
        }
    }

    @Test
    void laterReturnRejected() {
        LaunchData d = load(Map.of("firmages:module/sampler", sampler("firmages:quasar_alloy_ingot")));
        assertTrue(d.module("firmages:sampler_module").isEmpty());
        assertTrue(d.errors().get(0).contains("later"), d.errors().get(0));
        d = load(Map.of("firmages:module/sampler", sampler("firmages:deep_space_sample")));
        assertTrue(d.module("firmages:sampler_module").isEmpty(), "a deep-space sample from an age_7 module");
    }

    @Test
    void unknownModuleSkipped() {
        LaunchData d = load(Map.of("firmages:module/x", SAMPLER.replace("firmages:sampler_module", "other:module")));
        assertTrue(d.errors().isEmpty());
        assertEquals(1, d.skipped().size());
        assertTrue(d.modules().isEmpty());
    }

    @Test
    void duplicateModuleKeepsFirst() {
        LaunchData d = load(Map.of("a:module/one", SAMPLER, "b:module/two", SAMPLER.replace("12000", "1")));
        assertEquals("a:module/one", d.module("firmages:sampler_module").orElseThrow().file());
        assertEquals(1, d.errors().size());
    }

    @Test
    void badFuel() {
        LaunchData none = LaunchData.parse(Map.of(), Map.of(), LOOKUP);
        assertFalse(none.valid());
        assertEquals(LaunchRules.defaultSteps(), none.steps());
        String slower = FUEL.replace("{ \"burn\": 8, \"progress\": 4 }", "{ \"burn\": 8, \"progress\": 2 }");
        LaunchData d = LaunchData.parse(Map.of("firmages:fuel", json(slower)), Map.of(), LOOKUP);
        assertFalse(d.valid(), "a step that is not faster");
        LaunchData noNs = LaunchData.parse(Map.of("firmages:fuel", json(FUEL.replace("ad_astra:fuel", "fuel"))), Map.of(), LOOKUP);
        assertFalse(noNs.valid());
        LaunchData hash = LaunchData.parse(Map.of("firmages:fuel", json(FUEL.replace("ad_astra:fuel", "#ad_astra:fuel"))), Map.of(), LOOKUP);
        assertEquals("ad_astra:fuel", hash.fuel().orElseThrow().fluidTag());
    }

    @Test
    void counts() {
        Map<String, Long> c = Map.of("firmages:sampler_module", 10L, "firmages:deep_space_module", 6L);
        assertEquals(16, LaunchCounts.sum(c, null));
        assertEquals(10, LaunchCounts.sum(c, "firmages:sampler_module"));
        assertEquals(0, LaunchCounts.sum(c, "other:x"));
    }
}
