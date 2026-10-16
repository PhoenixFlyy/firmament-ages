package dev.firmages.core.shrine;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
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
    void gatheringConditions() {
        java.util.Set<Integer> all = new java.util.HashSet<>();
        for (int k = 0; k <= ShrineData.MAX_TIER; k++) all.add(k);
        java.util.Set<Integer> none = java.util.Set.of();
        assertEquals(Optional.empty(), ShrineRules.gatheringBlock(upTo(AgeId.AGE_9), false, false, true, all, none), "age_9, intact, nine relics: heard");
        assertEquals(Optional.of(ShrineRules.GatheringBlock.NOT_SINGULARITY), ShrineRules.gatheringBlock(upTo(AgeId.AGE_8), false, false, true, all, none));
        assertEquals(Optional.of(ShrineRules.GatheringBlock.DONE), ShrineRules.gatheringBlock(upTo(AgeId.AGE_9), true, false, true, all, none), "only once");
        assertEquals(Optional.of(ShrineRules.GatheringBlock.MAINTENANCE), ShrineRules.gatheringBlock(upTo(AgeId.AGE_9), false, true, true, all, none));
        assertEquals(Optional.of(ShrineRules.GatheringBlock.BROKEN), ShrineRules.gatheringBlock(upTo(AgeId.AGE_9), false, false, false, all, none));
        java.util.Set<Integer> eight = new java.util.HashSet<>(all);
        eight.remove(3);
        assertEquals(Optional.of(ShrineRules.GatheringBlock.RELIC_LENT), ShrineRules.gatheringBlock(upTo(AgeId.AGE_9), false, false, true, eight, java.util.Set.of(3)),
            "a lent relic: bring it back first");
        assertEquals(Optional.of(ShrineRules.GatheringBlock.RELIC_MISSING), ShrineRules.gatheringBlock(upTo(AgeId.AGE_9), false, false, true, eight, none));
        assertEquals(Optional.of(3), ShrineRules.firstMissingRelic(eight));
        assertEquals(Optional.empty(), ShrineRules.firstMissingRelic(all));
        assertEquals(1, ShrineRules.gatheringChorus(1));
        assertEquals(2, ShrineRules.gatheringChorus(2));
        assertEquals(2, ShrineRules.gatheringChorus(7));
        assertEquals(9, ShrineRules.heartAwakened(9, false));
        assertEquals(10, ShrineRules.heartAwakened(9, true), "the heart shows awakened=10 after the Gathering");
        assertEquals(3, ShrineRules.heartAwakened(3, true), "10 only in the Singularity Age");
    }

    @Test
    void idleBeamColour() {
        int[] colors = {0xFFFFB347, 0xFFCD7F32, 0xFFB0B8C0, 0xFF8A4FFF, 0xFF777777, 0xFF55AAFF, 0xFF00FFAA, 0xFF2244FF, 0xFFFFFFFF};
        assertEquals(0, ShrineRules.idleBeamColor(0, true, true, false, colors), "no beam before the first awakening");
        assertEquals(0x90CD7F32, ShrineRules.idleBeamColor(2, true, true, false, colors), "the latest awakened tier's colour, alpha 0x90");
        assertEquals(0x90FFFFFF, ShrineRules.idleBeamColor(9, true, true, false, colors));
        assertEquals(0x90FFFFFF, ShrineRules.idleBeamColor(10, true, true, false, colors), "awakened=10 (Gathering) keeps the last colour");
        assertEquals(0, ShrineRules.idleBeamColor(3, false, true, false, colors), "dark while the shrine is broken");
        assertEquals(0, ShrineRules.idleBeamColor(3, true, false, false, colors), "dark while the heart is cold");
        assertEquals(ShrineRules.FINALE_BEAM, ShrineRules.idleBeamColor(9, true, true, true, colors), "gold after the finale");
        assertEquals(0, ShrineRules.idleBeamColor(9, false, true, true, colors), "a broken shrine is dark after the finale too");
        assertEquals(0, ShrineRules.idleBeamColor(3, true, true, false, new int[0]), "no colours synced: no beam");
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
        assertEquals(java.util.Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8), d.tiers().keySet(), "every tier has its own ring");
        assertEquals(8, d.highestRing());
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
        // Every tier 0..8 has its own ring, grants the next Age, takes the signature item of its Age, carries a known
        // blessing and only rites that the mod implements. The generic fallback stays for packs that remove a ring.
        String[] gifts = {"hearthstone", "sky_disc", "steel_heart", "arcane_keystone", "pressure_core", "humming_core",
            "data_matrix", "star_chart", "quantum_core"};
        for (int k = 0; k <= ShrineData.MAX_TIER; k++) {
            ShrineTier t = d.tier(k).orElseThrow();
            assertFalse(t.fallback(), "tier " + k + " has its own file");
            assertEquals("age_" + (k + 1), t.grants());
            assertEquals(Optional.of("firmages:shrine_ring_" + k), t.multiblock());
            assertEquals(Optional.of("firmages:" + gifts[k]), d.offeringFor(k), "offering of age_" + k);
            assertEquals("firmages.shrine.voice.age_" + (k + 1), t.voiceKey());
            assertTrue(t.blessing().map(d.blessings()::containsKey).orElse(false), "tier " + k + " blessing " + t.blessing());
            assertFalse(t.rites().isEmpty(), "tier " + k + " has a rite");
            for (ShrineTier.Rite r : t.rites()) assertTrue(r.type().supported(), "tier " + k + " rite " + r.type());
        }
        assertEquals(9, d.offerings().size(), "offerings for age_0..age_8");
        assertTrue(d.fallback().isPresent());
        assertEquals(9, d.blessings().size());
        assertEquals(ShrineTier.Rite.Type.PLAYERS_PRAYING, d.tier(6).orElseThrow().rites().get(0).type());
        assertEquals(2, d.tier(6).orElseThrow().rites().get(0).min());
        assertEquals(ShrineTier.Rite.Type.SKY, d.tier(7).orElseThrow().rites().get(0).type());
        // M6: the Tesla crown's capacitors hold 1 M FE (Doc 11 "Herz mit 1 Mio. FE laden"); tier 8 lends the Arcane
        // Keystone (relic of tier 3) for the Marid ritual and wants it, or the Awakened Keystone, back.
        ShrineTier.Rite energy = d.tier(5).orElseThrow().rites().stream().filter(r -> r.type() == ShrineTier.Rite.Type.ENERGY).findFirst().orElseThrow();
        assertEquals('V', energy.key());
        assertEquals(1_000_000L, energy.amount());
        assertTrue(d.lendingRite(8, 3).isPresent());
        assertEquals(java.util.Set.of("firmages:awakened_keystone"), d.returnItems(3));
        for (int k = 0; k < 8; k++) assertTrue(d.tier(k).orElseThrow().rites().stream().noneMatch(r -> r.type() == ShrineTier.Rite.Type.RELIC_RETURNED));
        // Every blessing does something real: Hearthward the sanctuary, the others their effects (core-shrine.md budget:
        // +15 % max health, +10 % XP, +5 % break speed, +0.5 reach, -20 % fall damage, +1 luck, plus +5 % speed).
        for (Blessing b : d.blessings().values()) assertTrue(b.has(Blessing.SANCTUARY) || !b.effects().isEmpty(), b.id() + " has an effect");
        assertTrue(d.blessings().get("firmages:hearthward").effects().isEmpty());
        double health = 0;
        for (Blessing b : d.blessings().values()) {
            for (Blessing.Effect e : b.effects()) {
                if (e.target().equals("minecraft:generic.max_health")) health += e.amount();
            }
            if (!b.id().equals("firmages:hearthward")) assertFalse(b.has(Blessing.SANCTUARY), b.id() + " no longer stands in for the ward");
        }
        assertEquals(0.15, health, 1e-9, "max health +15 % over the game");
        assertEquals(Blessing.Effect.Kind.XP_BONUS, d.blessings().get("firmages:attunement").effects().get(0).kind());
        assertEquals(0.10, d.blessings().get("firmages:attunement").effects().get(0).amount(), 1e-9);
        assertEquals(Optional.empty(), d.tier(9));
        assertEquals(0xFFFFB347, t0.response().beamColor());
        assertEquals(0xFF8C3A, t0.response().skyTint());
        // Felix H5A (SPEC §35): from the Industrial Age every offering waits for a tally of the Age's product, counted
        // since the Age dawned; before it no offering asks for one. The counts are about two hours of one reference
        // line of the Age (dev/harmonie-r2.md section 7, review fix 0.12.0).
        String[][] proof = {{"4", "c:ingots/steel", "384"}, {"5", "c:ingots/aluminum", "512"}, {"6", "c:ingots/osmium", "1024"},
            {"7", "c:ingots/desh", "1024"}, {"8", "c:ingots/calorite", "2048"}};
        for (int k = 0; k < 4; k++) assertEquals(Optional.empty(), d.tallyFor(k), "no proof before the Industrial Age: tier " + k);
        long last = 0;
        for (String[] p : proof) {
            int k = Integer.parseInt(p[0]);
            TallyRequirement r = d.tallyFor(k).orElseThrow();
            assertEquals(Optional.of(p[1]), r.tag(), "tier " + k);
            assertEquals(Long.parseLong(p[2]), r.count(), "tier " + k);
            assertEquals("age_" + k, r.age(), "counted since the Age dawned");
            assertTrue(r.count() >= last, "the proof never shrinks: tier " + k);
            last = r.count();
        }
        // Harmony plan table 2.1: the heart draws 10 M FE in ring 6, 100 M FE in ring 8 and 1 G FE for the Gathering
        // (SPEC §36); ring 5 keeps its four capacitors (the hold-only energy rite).
        assertEquals(10_000_000L, consume(d.tier(6).orElseThrow().rites()));
        assertEquals(100_000_000L, consume(d.tier(8).orElseThrow().rites()));
        assertEquals(1_000_000_000L, consume(d.gatheringRites()));
        for (int k : new int[] {0, 1, 2, 3, 4, 5, 7}) assertEquals(0L, consume(d.tier(k).orElseThrow().rites()), "tier " + k + " draws nothing");
    }

    private static long consume(List<ShrineTier.Rite> rites) {
        return rites.stream().filter(r -> r.type() == ShrineTier.Rite.Type.ENERGY_CONSUME).mapToLong(ShrineTier.Rite::amount).sum();
    }

    /** tallies.json: one of item and tag, a positive count, an Age; a later namespace replaces single keys, null removes one. */
    @Test
    void talliesParseMergeAndReject() {
        ShrineData d = ShrineData.parse(new TreeMap<>(Map.of(
            "firmages:tallies", JsonParser.parseString("{\"age_4\":{\"tag\":\"#c:ingots/steel\",\"count\":512},"
                + "\"age_5\":{\"item\":\"minecraft:iron_ingot\",\"count\":8,\"age\":\"age_3\"},\"age_6\":{\"tag\":\"c:ingots/osmium\",\"count\":4096}}"),
            "zz_pack:tallies", JsonParser.parseString("{\"age_4\":{\"tag\":\"c:ingots/steel\",\"count\":256},\"age_6\":null}"))));
        assertEquals(List.of(), d.errors());
        assertEquals(new TallyRequirement(Optional.empty(), Optional.of("c:ingots/steel"), 256, "age_4"), d.tallyFor(4).orElseThrow(), "the later namespace wins");
        assertEquals(new TallyRequirement(Optional.of("minecraft:iron_ingot"), Optional.empty(), 8, "age_3"), d.tallyFor(5).orElseThrow());
        assertEquals(Optional.empty(), d.tallyFor(6), "null switches a requirement off");
        assertEquals("tag #c:ingots/steel x256 since age_4", d.tallyFor(4).orElseThrow().describe());
        for (String bad : new String[] {
            "{\"age_9\":{\"tag\":\"c:x\",\"count\":1}}",
            "{\"age_4\":{\"count\":1}}",
            "{\"age_4\":{\"tag\":\"c:x\",\"item\":\"minecraft:stone\",\"count\":1}}",
            "{\"age_4\":{\"tag\":\"c:x\"}}",
            "{\"age_4\":{\"tag\":\"c:x\",\"count\":0}}",
            "{\"age_4\":{\"tag\":\"c:x\",\"count\":5,\"age\":\"age_10\"}}"}) {
            assertFalse(parseOne("firmages:tallies", bad).errors().isEmpty(), "rejected: " + bad);
        }
    }

    /** energy_consume draws into the heart: an amount and no key; the Gathering takes only that rite. */
    @Test
    void energyConsumeRite() {
        ShrineData ok = parseOne("firmages:tier/ring_6", "{\"multiblock\":\"firmages:x\",\"rites\":[{\"type\":\"energy_consume\",\"amount\":10000000}]}");
        assertEquals(List.of(), ok.errors());
        assertEquals(List.of(), ok.warnings(), "energy_consume is implemented");
        assertEquals(10_000_000L, consume(ok.tier(6).orElseThrow().rites()));
        assertFalse(parseOne("firmages:tier/ring_6", "{\"multiblock\":\"firmages:x\",\"rites\":[{\"type\":\"energy_consume\"}]}").errors().isEmpty(),
            "energy_consume without an amount");
        assertFalse(parseOne("firmages:tier/ring_6", "{\"multiblock\":\"firmages:x\",\"rites\":[{\"type\":\"energy_consume\",\"key\":\"V\",\"amount\":5}]}")
            .errors().isEmpty(), "energy_consume has no key: the FE goes into the heart");
        ShrineData g = parseOne("firmages:gathering", "{\"rites\":[{\"type\":\"energy_consume\",\"amount\":1000000000}]}");
        assertEquals(List.of(), g.errors());
        assertEquals(1_000_000_000L, consume(g.gatheringRites()));
        assertFalse(parseOne("firmages:gathering", "{\"rites\":[{\"type\":\"sky\"}]}").errors().isEmpty(), "the Gathering takes only energy_consume");
        assertEquals(List.of(), ShrineData.EMPTY.gatheringRites());
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
        assertFalse(parseOne("firmages:tier/ring_4", "{\"multiblock\":\"firmages:x\",\"rites\":[{\"type\":\"energy\",\"amount\":1000000}]}").errors().isEmpty(),
            "energy rite without the key of its storage blocks");
        assertFalse(parseOne("firmages:tier/ring_4", "{\"multiblock\":\"firmages:x\",\"rites\":[{\"type\":\"energy\",\"key\":\"V\"}]}").errors().isEmpty(),
            "energy rite without an amount");
        assertFalse(parseOne("firmages:tier/ring_8", "{\"multiblock\":\"firmages:x\",\"rites\":[{\"type\":\"relic_returned\"}]}").errors().isEmpty(),
            "relic_returned without the relic's tier");
        assertFalse(parseOne("firmages:tier/ring_3", "{\"multiblock\":\"firmages:x\",\"rites\":[{\"type\":\"relic_returned\",\"relic\":3}]}").errors().isEmpty(),
            "a tier cannot lend its own or a later relic");
        ShrineData ok = parseOne("firmages:tier/ring_4", "{\"multiblock\":\"firmages:shrine_ring_4\",\"rites\":[{\"type\":\"energy\",\"key\":\"V\",\"amount\":1000000}]}");
        assertTrue(ok.errors().isEmpty(), "errors " + ok.errors());
        assertEquals(List.of(), ok.warnings(), "energy is implemented now");
        ShrineTier.Rite e = ok.tier(4).orElseThrow().rites().get(0);
        assertEquals(ShrineTier.Rite.Type.ENERGY, e.type());
        assertEquals('V', e.key());
        assertEquals(1_000_000L, e.amount());
        assertEquals("firmages:shrine_ring_4", ok.tier(4).orElseThrow().multiblock().orElseThrow());
        assertEquals(Optional.empty(), ok.tier(3), "no fallback file: no definition");
    }

    @Test
    void blessingEffectsParseAndRejectNonsense() {
        ShrineData d = parseOne("firmages:blessing/test", "{\"effects\":[{\"type\":\"attribute\",\"attribute\":\"generic.max_health\","
            + "\"operation\":\"add_multiplied_base\",\"amount\":0.05},{\"type\":\"mob_effect\",\"effect\":\"minecraft:haste\",\"amplifier\":1},"
            + "{\"type\":\"xp_bonus\",\"amount\":0.1}]}");
        assertEquals(List.of(), d.errors());
        Blessing b = d.blessings().get("firmages:test");
        assertEquals("firmages.blessing.test", b.nameKey());
        assertEquals(3, b.effects().size());
        assertEquals(new Blessing.Effect(Blessing.Effect.Kind.ATTRIBUTE, "minecraft:generic.max_health", "add_multiplied_base", 0.05, 0), b.effects().get(0));
        assertEquals(new Blessing.Effect(Blessing.Effect.Kind.MOB_EFFECT, "minecraft:haste", "", 0, 1), b.effects().get(1));
        assertEquals(new Blessing.Effect(Blessing.Effect.Kind.XP_BONUS, "", "", 0.1, 0), b.effects().get(2));
        for (String bad : new String[] {
            "{\"effects\":[{\"type\":\"fly\"}]}",
            "{\"effects\":[{\"type\":\"attribute\",\"amount\":1}]}",
            "{\"effects\":[{\"type\":\"attribute\",\"attribute\":\"generic.luck\",\"operation\":\"times\",\"amount\":1}]}",
            "{\"effects\":[{\"type\":\"attribute\",\"attribute\":\"generic.luck\"}]}",
            "{\"effects\":[{\"type\":\"mob_effect\"}]}",
            "{\"effects\":[{\"type\":\"mob_effect\",\"effect\":\"minecraft:speed\",\"amplifier\":12}]}",
            "{\"effects\":[{\"type\":\"xp_bonus\",\"amount\":0}]}"}) {
            assertFalse(parseOne("firmages:blessing/bad", bad).errors().isEmpty(), "rejected: " + bad);
        }
    }

    @Test
    void lendingRiteLookup() {
        ShrineData d = ShrineData.parse(Map.of(
            "firmages:tier/ring_8", JsonParser.parseString("{\"multiblock\":\"firmages:x\",\"rites\":[{\"type\":\"relic_returned\",\"relic\":3,"
                + "\"items\":[\"firmages:awakened_keystone\"]}]}"),
            "firmages:tier/ring_7", JsonParser.parseString("{\"multiblock\":\"firmages:y\"}")));
        assertEquals(List.of(), d.errors());
        assertTrue(d.lendingRite(8, 3).isPresent(), "tier 8 lends the relic of tier 3");
        assertTrue(d.lendingRite(8, 2).isEmpty() && d.lendingRite(7, 3).isEmpty(), "nothing else is lent");
        assertEquals(java.util.Set.of("firmages:awakened_keystone"), d.returnItems(3));
        assertEquals(java.util.Set.of(), d.returnItems(2));
    }
}
