package dev.firmages.core.gate;

import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC §12 m2 U test: {@link JsonOutputWalker} on recipe JSON copied verbatim from the pinned jars
 * ({@code src/test/resources/fixtures}: Create 6.0.10, IE 12.4.2, Mekanism 10.7.19, Occultism 1.224.4,
 * Ars Nouveau 5.13.2, TFC 4.2.11, Draconic Evolution 3.1.4). Each case lists exactly what must be detected;
 * inputs, catalysts, conditions and counts must not appear.
 */
class JsonOutputWalkerFixtureTest {

    private static OutputSink walk(String fixture) throws Exception {
        try (InputStream in = JsonOutputWalkerFixtureTest.class.getResourceAsStream("/fixtures/" + fixture)) {
            assertNotNull(in, "fixture " + fixture);
            OutputSink s = new OutputSink();
            JsonOutputWalker.walk(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)), s);
            return s;
        }
    }

    private static void expect(OutputSink s, Set<String> items, Set<String> fluids, Set<String> ids, Set<String> tags) {
        assertEquals(items, s.items(), "items: " + s.describe());
        assertEquals(fluids, s.fluids(), "fluids: " + s.describe());
        assertEquals(ids, s.anyIds(), "ids: " + s.describe());
        assertEquals(tags, s.anyTags(), "tags: " + s.describe());
        assertTrue(s.itemTags().isEmpty() && s.fluidTags().isEmpty(), "typed tags: " + s.describe());
    }

    @Test
    void createMixingFluidResult() throws Exception {
        expect(walk("create_mixing_lava.json"), Set.of(), Set.of(), Set.of("minecraft:lava"), Set.of());
    }

    @Test
    void createCrushingWithChances() throws Exception {
        expect(walk("create_crushing_raw_iron.json"), Set.of(), Set.of(),
            Set.of("create:crushed_raw_iron", "create:experience_nugget"), Set.of());
    }

    @Test
    void createSequencedAssembly() throws Exception {
        OutputSink s = walk("create_sequenced_precision.json");
        expect(s, Set.of(), Set.of(), Set.of("create:precision_mechanism", "create:golden_sheet", "create:andesite_alloy",
            "create:cogwheel", "minecraft:gold_nugget", "create:shaft", "create:crushed_raw_gold", "minecraft:iron_ingot",
            "minecraft:clock", "create:incomplete_precision_mechanism"), Set.of());
        assertFalse(s.describe().contains("large_cogwheel"), "step ingredients are inputs");
    }

    @Test
    void ieCrusherTagOutputsAndSecondaries() throws Exception {
        expect(walk("ie_crusher_ore_iron.json"), Set.of(), Set.of(), Set.of(), Set.of("c:dusts/iron", "c:dusts/nickel"));
    }

    @Test
    void ieArcFurnaceResults() throws Exception {
        // The slag key is not an output key; the IE extractor reads the slag field.
        expect(walk("ie_arcfurnace_ore_iron.json"), Set.of(), Set.of(), Set.of(), Set.of("c:ingots/iron"));
    }

    @Test
    void mekanismEnriching() throws Exception {
        expect(walk("mekanism_enriching.json"), Set.of(), Set.of(), Set.of("mekanism:dust_charcoal"), Set.of());
    }

    @Test
    void mekanismReactionItemAndChemicalOutput() throws Exception {
        expect(walk("mekanism_reaction.json"), Set.of(), Set.of(), Set.of("mekanism:hydrogen", "mekanism:dust_sulfur"), Set.of());
    }

    @Test
    void occultismMinerWeightedTag() throws Exception {
        expect(walk("occultism_miner.json"), Set.of(), Set.of(), Set.of(), Set.of("c:storage_blocks/prosperity_shard"));
    }

    @Test
    void occultismRitual() throws Exception {
        expect(walk("occultism_ritual.json"), Set.of(), Set.of(), Set.of("occultism:dimensional_mineshaft"), Set.of());
    }

    @Test
    void occultismCrushingTagResult() throws Exception {
        expect(walk("occultism_crushing_tag.json"), Set.of(), Set.of(), Set.of(), Set.of("c:dusts/topaz"));
    }

    @Test
    void arsApparatus() throws Exception {
        expect(walk("ars_apparatus.json"), Set.of(), Set.of(), Set.of("ars_nouveau:enchanters_sword"), Set.of());
    }

    @Test
    void tfcHeatingResultFluid() throws Exception {
        expect(walk("tfc_heating.json"), Set.of(), Set.of("tfc:metal/black_steel"), Set.of(), Set.of());
    }

    @Test
    void tfcBarrelOutputItemNotInputFluid() throws Exception {
        expect(walk("tfc_barrel.json"), Set.of(), Set.of(), Set.of("minecraft:yellow_terracotta"), Set.of());
    }

    @Test
    void draconicFusion() throws Exception {
        expect(walk("de_fusion_chaotic_core.json"), Set.of(), Set.of(), Set.of("draconicevolution:chaotic_core"), Set.of());
    }

    /** End to end on fixtures: detected outputs through the rules with a two-Age lookup. */
    @Test
    void fixturesThroughTheRules() throws Exception {
        GateRules.Lookup lookup = new GateRules.Lookup() {
            final Map<String, AgeId> items = Map.of("draconicevolution:chaotic_core", AgeId.AGE_9, "create:crushed_raw_iron", AgeId.AGE_2);
            final Map<String, List<String>> tags = Map.of("c:dusts/iron", List.of("mekanism:dust_iron"),
                "c:dusts/nickel", List.of("mekanism:dust_nickel"));
            public AgeId itemAge(String id) {
                return switch (id) {
                    case "mekanism:dust_iron" -> AgeId.AGE_2;
                    case "mekanism:dust_nickel" -> AgeId.AGE_4;
                    default -> items.get(id);
                };
            }
            public AgeId fluidAge(String id) { return id.equals("tfc:metal/black_steel") ? AgeId.AGE_3 : null; }
            public boolean itemDisabled(String id) { return false; }
            public boolean fluidDisabled(String id) { return false; }
            public Collection<String> itemTag(String t) { return tags.getOrDefault(t, List.of()); }
            public Collection<String> fluidTag(String t) { return List.of(); }
        };
        GateRules.Settings settings = new GateRules.Settings(true, Set.of(), Set.of(), Set.of());
        AgeSnapshot age2 = AgeSnapshot.of(List.of(AgeId.AGE_0, AgeId.AGE_1, AgeId.AGE_2), 0, 0);
        GateRules.Verdict fusion = GateRules.decide("de:c", "draconicevolution:fusion_crafting", null, walk("de_fusion_chaotic_core.json"), settings, lookup, age2);
        assertFalse(fusion.keep());
        assertEquals("age_9", fusion.bucket());
        GateRules.Verdict crusher = GateRules.decide("ie:c", "immersiveengineering:crusher", null, walk("ie_crusher_ore_iron.json"), settings, lookup, age2);
        assertFalse(crusher.keep(), "the nickel byproduct (age_4) is locked: " + crusher);
        assertEquals("age_4", crusher.bucket());
        GateRules.Verdict heating = GateRules.decide("tfc:h", "tfc:heating", null, walk("tfc_heating.json"), settings, lookup, age2);
        assertFalse(heating.keep());
        assertEquals("age_3", heating.bucket());
        GateRules.Verdict crushing = GateRules.decide("create:c", "create:crushing", null, walk("create_crushing_raw_iron.json"), settings, lookup, age2);
        assertTrue(crushing.keep());
    }
}
