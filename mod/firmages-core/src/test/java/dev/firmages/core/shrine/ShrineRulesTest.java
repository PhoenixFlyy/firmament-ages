package dev.firmages.core.shrine;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level U tests of the shrine rules and the shrine data parser (SPEC §12: state machine math, tier JSON). */
class ShrineRulesTest {

    private static EnumSet<AgeId> upTo(AgeId last) {
        EnumSet<AgeId> s = EnumSet.noneOf(AgeId.class);
        for (AgeId a : AgeId.all()) {
            if (a.ordinal() <= last.ordinal()) s.add(a);
        }
        return s;
    }

    @Test
    void currentTierFollowsTheHighestAge() {
        assertEquals(Optional.empty(), ShrineRules.currentTier(upTo(AgeId.DAWN)), "Dawn: the First Spark is a quest");
        assertEquals(Optional.of(0), ShrineRules.currentTier(upTo(AgeId.AGE_0)));
        assertEquals(Optional.of(2), ShrineRules.currentTier(upTo(AgeId.AGE_2)));
        assertEquals(Optional.of(8), ShrineRules.currentTier(upTo(AgeId.AGE_8)));
        assertEquals(Optional.empty(), ShrineRules.currentTier(upTo(AgeId.AGE_9)), "no Age after the Singularity Age");
        // A gap (admin grant) still uses the highest Age.
        assertEquals(Optional.of(3), ShrineRules.currentTier(EnumSet.of(AgeId.DAWN, AgeId.AGE_0, AgeId.AGE_3)));
    }

    @Test
    void tierGrantingAndAwakened() {
        assertEquals(Optional.empty(), ShrineRules.tierGranting(AgeId.AGE_0));
        assertEquals(Optional.of(0), ShrineRules.tierGranting(AgeId.AGE_1));
        assertEquals(Optional.of(8), ShrineRules.tierGranting(AgeId.AGE_9));
        assertEquals(0, ShrineRules.awakened(upTo(AgeId.AGE_0)));
        assertEquals(1, ShrineRules.awakened(upTo(AgeId.AGE_1)));
        assertEquals(9, ShrineRules.awakened(upTo(AgeId.AGE_9)));
    }

    @Test
    void prayerTakesTenSecondsPerPlayerCountWithAFourSecondMinimum() {
        int p = 200;
        int min = 80;
        assertEquals(200, ShrineRules.prayerDuration(p, min, 1));
        assertEquals(100, ShrineRules.prayerDuration(p, min, 2));
        assertEquals(80, ShrineRules.prayerDuration(p, min, 3), "3 players: 240 / 3");
        assertEquals(80, ShrineRules.prayerDuration(p, min, 4), "minimum 4 s");
        assertEquals(80, ShrineRules.prayerDuration(p, min, 8));
    }

    @Test
    void progressGrowsWithPrayersAndDecaysWithout() {
        assertEquals(3, ShrineRules.stepProgress(1, 2));
        assertEquals(4, ShrineRules.stepProgress(5, 0));
        assertEquals(0, ShrineRules.stepProgress(0, 0));
        // Interrupted prayer: 50 ticks of one player, 20 ticks pause, 10 more ticks: 40 progress.
        int prog = 0;
        for (int i = 0; i < 50; i++) prog = ShrineRules.stepProgress(prog, 1);
        for (int i = 0; i < 20; i++) prog = ShrineRules.stepProgress(prog, 0);
        for (int i = 0; i < 10; i++) prog = ShrineRules.stepProgress(prog, 1);
        assertEquals(40, prog);
    }

    @Test
    void sanctuaryAndBar() {
        assertEquals(0, ShrineRules.sanctuaryRadius(0, 12, 4));
        assertEquals(12, ShrineRules.sanctuaryRadius(1, 12, 4));
        assertEquals(44, ShrineRules.sanctuaryRadius(9, 12, 4));
        assertEquals("▮▮▯▯", ShrineRules.bar(50, 100, 4));
    }

    // ---------------------------------------------------------------- data parser

    /** The shipped data files from src/main/resources parse without errors. */
    @Test
    void shippedShrineDataParses() throws IOException {
        Path root = Path.of("src/main/resources/data/firmages/firmages_shrine");
        Map<String, JsonElement> files = new TreeMap<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json")).toList()) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                files.put("firmages:" + rel.substring(0, rel.length() - 5), JsonParser.parseString(Files.readString(p)));
            }
        }
        ShrineData d = ShrineData.parse(files);
        assertEquals(java.util.List.of(), d.errors());
        assertEquals(java.util.List.of(), d.warnings());
        assertEquals(java.util.Set.of(0, 1, 2), d.tiers().keySet());
        assertEquals(2, d.highestRing());
        assertEquals(Optional.of("firmages:hearthstone"), d.offeringFor(0));
        assertEquals(Optional.of("firmages:steel_heart"), d.offeringFor(2));
        assertEquals("age_1", d.tier(0).orElseThrow().grants());
        ShrineTier t0 = d.tier(0).orElseThrow();
        assertEquals(ShrineTier.Rite.Type.BLOCKSTATE, t0.rites().get(0).type());
        assertEquals('0', t0.rites().get(0).key());
        assertEquals(Optional.of("firmages:hearthward"), t0.blessing());
        assertTrue(d.blessings().get("firmages:hearthward").has(Blessing.SANCTUARY));
        assertEquals(ShrineTier.Rite.Type.INTERACT, d.tier(1).orElseThrow().rites().get(0).type());
        assertEquals("firmages:shrine/bells", d.tier(1).orElseThrow().rites().get(0).tag());
        // Tiers 3..8 fall back to the generic definition, so the structure never blocks progression.
        ShrineTier t5 = d.tier(5).orElseThrow();
        assertTrue(t5.fallback());
        assertEquals("age_6", t5.grants());
        assertEquals(Optional.empty(), t5.multiblock());
        assertEquals("firmages.shrine.voice.age_6", t5.voiceKey());
        assertEquals(Optional.of("firmages:humming_core"), d.offeringFor(5));
        assertEquals(Optional.empty(), d.tier(9));
        assertEquals(0xFFFFB347, t0.response().beamColor());
        assertEquals(0xFF8C3A, t0.response().skyTint());
    }

    private static ShrineData parseOne(String id, String json) {
        return ShrineData.parse(Map.of(id, JsonParser.parseString(json)));
    }

    @Test
    void invalidTierFilesAreRejected() {
        assertFalse(parseOne("firmages:tier/ring_0", "{\"multiblock\":\"firmages:x\",\"grants\":\"age_2\"}").errors().isEmpty(), "grants mismatch");
        assertFalse(parseOne("firmages:tier/ring_0", "{}").errors().isEmpty(), "multiblock missing");
        assertFalse(parseOne("firmages:tier/ring_12", "{\"multiblock\":\"firmages:x\"}").errors().isEmpty(), "tier out of range");
        assertFalse(parseOne("firmages:tier/fallback", "{\"multiblock\":\"firmages:x\"}").errors().isEmpty(), "fallback with a ring");
        assertFalse(parseOne("firmages:tier/ring_1", "{\"multiblock\":\"firmages:x\",\"rites\":[{\"type\":\"dance\"}]}").errors().isEmpty(), "unknown rite");
        assertFalse(parseOne("firmages:tier/ring_1", "{\"multiblock\":\"firmages:x\",\"rites\":[{\"type\":\"blockstate\",\"key\":\"L\"}]}").errors().isEmpty(),
            "blockstate rite without property");
        assertFalse(parseOne("firmages:tier/ring_1", "{\"multiblock\":\"firmages:x\",\"response\":{\"beam_color\":\"red\"}}").errors().isEmpty(), "bad colour");
        assertFalse(parseOne("firmages:offerings", "{\"age_9\":\"firmages:x\"}").errors().isEmpty(), "age_9 has no tier");
        assertFalse(parseOne("firmages:tier/ring_1", "{\"multiblock\":\"Not An Id\"}").errors().isEmpty(), "bad id");
        ShrineData ok = parseOne("firmages:tier/ring_4", "{\"multiblock\":\"firmages:shrine_ring_4\",\"rites\":[{\"type\":\"energy\",\"amount\":1000000}]}");
        assertTrue(ok.errors().isEmpty());
        assertEquals(1, ok.warnings().size(), "unsupported rite is a warning, not an error");
        assertEquals("firmages:shrine_ring_4", ok.tier(4).orElseThrow().multiblock().orElseThrow());
        assertEquals(Optional.empty(), ok.tier(3), "no fallback file: no definition");
    }
}
