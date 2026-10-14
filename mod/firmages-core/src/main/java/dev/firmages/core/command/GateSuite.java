package dev.firmages.core.command;

import com.google.gson.JsonParser;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.gate.GateReport;
import dev.firmages.core.gate.GateRules;
import dev.firmages.core.gate.JsonOutputWalker;
import dev.firmages.core.gate.OutputSink;
import dev.firmages.core.miner.OreRoll;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.firmages.core.command.SelfTest.check;
import static dev.firmages.core.command.SelfTest.checkEquals;

/**
 * Suite {@code gate}: the pure m2/m3 logic (SPEC §12 U tests): the JSON output walk, the decision rules (deny,
 * allow, exempt, locked, tag outputs, disabled, undetected), the classification the Age swap filters on (stations,
 * requireAge, untilAge, Create heat levels), the report, the excavator pick. No Minecraft classes, so JUnit
 * ({@code GateSuiteTest}) and {@code /firmages selftest gate} run the same cases.
 */
public final class GateSuite implements SelfTest.Suite {

    @Override
    public String name() {
        return "gate";
    }

    @Override
    public void run(SelfTest.Recorder r) {
        r.test("walker_output_keys", GateSuite::walkerOutputKeys);
        r.test("walker_skips_inputs_and_conditions", GateSuite::walkerSkipsInputs);
        r.test("walker_fluids_and_strings", GateSuite::walkerFluidsAndStrings);
        r.test("rule_deny_before_allow", GateSuite::denyBeforeAllow);
        r.test("rule_allow_keeps_locked", GateSuite::allowKeepsLocked);
        r.test("rule_exempt_type_and_serializer", GateSuite::exempt);
        r.test("rule_locked_and_unlocked_item", GateSuite::lockedItem);
        r.test("rule_latest_age_is_the_bucket", GateSuite::latestBucket);
        r.test("rule_tag_all_members_locked", GateSuite::tagAllLocked);
        r.test("rule_tag_with_unlocked_member_keeps", GateSuite::tagMixed);
        r.test("rule_tag_empty_or_unknown_keeps", GateSuite::tagEmpty);
        r.test("rule_disabled_never_unlocks", GateSuite::disabled);
        r.test("rule_any_id_checks_fluids", GateSuite::anyIdFluid);
        r.test("rule_undetected", GateSuite::undetected);
        r.test("report_counts_and_audit_text", GateSuite::report);
        r.test("swap_keep_equals_verdict_at_every_age_set", GateSuite::keepEqualsVerdict);
        r.test("swap_station_by_type_or_serializer", GateSuite::stationByTypeOrSerializer);
        r.test("swap_require_age_adds_up", GateSuite::requireAgeAddsUp);
        r.test("swap_until_age_supersedes", GateSuite::untilAgeSupersedes);
        r.test("swap_byproduct_copies_one_at_a_time", GateSuite::byproductCopies);
        r.test("swap_heat_levels_need_their_heater", GateSuite::heatLevels);
        r.test("swap_config_rules_beat_stations", GateSuite::configBeatsStations);
        r.test("swap_bucket_is_latest_unmet", GateSuite::bucketLatestUnmet);
        r.test("excavator_pick", GateSuite::excavatorPick);
    }

    // ---------------------------------------------------------------- fixtures

    static final GateRules.Settings DEFAULTS = new GateRules.Settings(true, Set.of(), Set.of(),
        Set.of("immersiveengineering:mineral_mix", "tfc:collapse", "tfc:landslide"));

    /** age_0: copper; age_2: iron, #c:ingots/iron; age_5: gold; fluid age_4: lava; disabled: bedrock item, fluid firmages:void. */
    static final GateRules.Lookup LOOKUP = new GateRules.Lookup() {
        final Map<String, AgeId> items = Map.of("minecraft:copper_ingot", AgeId.AGE_0, "minecraft:iron_ingot", AgeId.AGE_2,
            "minecraft:iron_nugget", AgeId.AGE_2, "minecraft:gold_ingot", AgeId.AGE_5);
        final Map<String, AgeId> fluids = Map.of("minecraft:lava", AgeId.AGE_4);
        final Map<String, List<String>> itemTags = Map.of(
            "c:ingots/iron", List.of("minecraft:iron_ingot"),
            "c:metal_bits", List.of("minecraft:iron_nugget", "minecraft:gold_ingot"),
            "c:ingots", List.of("minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:netherite_ingot"),
            "c:empty", List.of());
        final Map<String, List<String>> fluidTags = Map.of("c:hot", List.of("minecraft:lava"));

        public AgeId itemAge(String id) { return items.get(id); }
        public AgeId fluidAge(String id) { return fluids.get(id); }
        public boolean itemDisabled(String id) { return id.equals("minecraft:bedrock"); }
        public boolean fluidDisabled(String id) { return id.equals("firmages:void"); }
        public Collection<String> itemTag(String t) { return itemTags.getOrDefault(t, List.of()); }
        public Collection<String> fluidTag(String t) { return fluidTags.getOrDefault(t, List.of()); }
    };

    static final AgeSnapshot DAWN = AgeSnapshot.of(List.of(AgeId.DAWN), 0, 0);
    static final AgeSnapshot ALL = AgeSnapshot.of(AgeId.all(), 0, 0);

    static OutputSink walk(String json) {
        OutputSink s = new OutputSink();
        JsonOutputWalker.walk(JsonParser.parseString(json), s);
        return s;
    }

    static GateRules.Verdict decide(OutputSink s, AgeSnapshot snap) {
        return GateRules.decide("t:r", "t:type", "t:serializer", s, DEFAULTS, LOOKUP, snap);
    }

    static GateRules.Verdict decide(String json, AgeSnapshot snap) {
        return decide(walk(json), snap);
    }

    // ---------------------------------------------------------------- walker

    private static String walkerOutputKeys() {
        OutputSink s = walk("""
            {"type":"x:y","result":{"id":"a:one","count":2},"results":[{"item":"a:two"},{"id":"a:three","chance":0.5}],
             "item_output":{"id":"a:four"},"output_item":{"id":"a:five"},"secondaries":[{"chance":0.1,"output":{"tag":"c:six"}}],
             "result_item":{"stack":{"id":"a:seven"}},"extra":{"nested":{"outputs":["a:eight"]}}}""");
        checkEquals(Set.of("a:one", "a:three", "a:four", "a:five", "a:seven", "a:eight"), s.anyIds(), "ids");
        checkEquals(Set.of("a:two"), s.items(), "item values");
        checkEquals(Set.of("c:six"), s.anyTags(), "tags");
        return s.describe();
    }

    private static String walkerSkipsInputs() {
        OutputSink s = walk("""
            {"ingredients":[{"item":"a:in1"}],"input":{"tag":"c:in2"},"item_input":{"id":"a:in3"},"input_fluid":{"fluid":"a:in4"},
             "catalyst":{"item":"a:in5"},"reagent":{"item":"a:in6"},"pedestalItems":[{"tag":"c:in7"}],
             "neoforge:conditions":[{"type":"neoforge:item_exists","item":"a:cond"}],"key":{"A":{"item":"a:in8"}},
             "result":{"id":"a:out","components":{"minecraft:custom_data":{"id":"a:not_an_output"}},
                       "conditions":[{"tag":"c:cond"}]}}""");
        checkEquals(Set.of("a:out"), s.anyIds(), "only the output");
        check(s.items().isEmpty() && s.anyTags().isEmpty() && s.fluids().isEmpty() && s.itemTags().isEmpty(), "nothing else: " + s.describe());
        return s.describe();
    }

    private static String walkerFluidsAndStrings() {
        OutputSink s = walk("""
            {"result_fluid":{"id":"tfc:metal/copper","amount":100},"output_fluid":{"fluid":"a:f2","amount":1},
             "results":[{"id":"minecraft:lava","amount":50}],"output":"minecraft:stone","extra_result":"#c:gems","bare_output":"stone"}""");
        checkEquals(Set.of("tfc:metal/copper", "a:f2"), s.fluids(), "fluids");
        checkEquals(Set.of("minecraft:lava", "minecraft:stone"), s.anyIds(), "ids of unknown kind (bare id gets minecraft:)");
        checkEquals(Set.of("c:gems"), s.anyTags(), "string tag");
        return s.describe();
    }

    // ---------------------------------------------------------------- rules

    private static String denyBeforeAllow() {
        GateRules.Settings s = new GateRules.Settings(true, Set.of("t:r"), Set.of("t:r"), Set.of());
        GateRules.Verdict v = GateRules.decide("t:r", "t:type", null, walk("{\"result\":\"minecraft:stone\"}"), s, LOOKUP, ALL);
        checkEquals(GateRules.Reason.DENIED, v.reason(), "deny wins");
        check(!v.keep(), "denied recipe dropped");
        return "ok";
    }

    private static String allowKeepsLocked() {
        GateRules.Settings s = new GateRules.Settings(true, Set.of("t:r"), Set.of(), Set.of());
        GateRules.Verdict v = GateRules.decide("t:r", "t:type", null, walk("{\"result\":\"minecraft:gold_ingot\"}"), s, LOOKUP, DAWN);
        checkEquals(GateRules.Reason.ALLOWED, v.reason(), "allowlisted");
        check(v.keep(), "kept despite a locked output");
        GateRules.Verdict other = GateRules.decide("t:other", "t:type", null, walk("{\"result\":\"minecraft:gold_ingot\"}"), s, LOOKUP, DAWN);
        check(!other.keep(), "other recipe with the same output still dropped");
        return "ok";
    }

    private static String exempt() {
        OutputSink locked = walk("{\"result\":\"minecraft:iron_ingot\"}");
        GateRules.Verdict byType = GateRules.decide("ie:mix", "immersiveengineering:mineral_mix", "x", locked, DEFAULTS, LOOKUP, DAWN);
        GateRules.Verdict bySer = GateRules.decide("tfc:c", "tfc:other", "tfc:collapse", locked, DEFAULTS, LOOKUP, DAWN);
        checkEquals(GateRules.Reason.EXEMPT, byType.reason(), "exempt by type");
        checkEquals(GateRules.Reason.EXEMPT, bySer.reason(), "exempt by serializer");
        check(byType.keep() && bySer.keep(), "exempt recipes kept");
        return "ok";
    }

    private static String lockedItem() {
        GateRules.Verdict v = decide("{\"result\":{\"id\":\"minecraft:iron_ingot\"}}", DAWN);
        check(!v.keep(), "iron (age_2) locked at dawn");
        checkEquals("age_2", v.bucket(), "bucket");
        checkEquals("minecraft:iron_ingot", v.entry(), "entry");
        GateRules.Verdict u = decide("{\"result\":{\"id\":\"minecraft:iron_ingot\"}}", AgeSnapshot.of(List.of(AgeId.AGE_2), 0, 0));
        check(u.keep() && u.reason() == GateRules.Reason.UNLOCKED, "kept once age_2 is unlocked");
        GateRules.Verdict untagged = decide("{\"result\":{\"id\":\"minecraft:stone\"}}", DAWN);
        check(untagged.keep(), "untagged output is always unlocked");
        return "ok";
    }

    private static String latestBucket() {
        GateRules.Verdict v = decide("{\"results\":[{\"id\":\"minecraft:iron_ingot\"},{\"id\":\"minecraft:gold_ingot\"},{\"id\":\"minecraft:copper_ingot\"}]}", DAWN);
        checkEquals("age_5", v.bucket(), "the recipe returns with the latest locked output Age");
        GateRules.Verdict byproduct = decide("{\"results\":[{\"id\":\"minecraft:stone\"},{\"id\":\"minecraft:gold_ingot\"}]}",
            AgeSnapshot.of(List.of(AgeId.AGE_2), 0, 0));
        check(!byproduct.keep(), "a locked byproduct drops the recipe (allowRecipes handles exceptions)");
        return v.bucket();
    }

    private static String tagAllLocked() {
        GateRules.Verdict v = decide("{\"result\":{\"tag\":\"c:metal_bits\"}}", DAWN);
        check(!v.keep(), "all members locked -> dropped");
        checkEquals("age_2", v.bucket(), "a tag output returns with its earliest member");
        checkEquals("#c:metal_bits", v.entry(), "entry");
        GateRules.Verdict v2 = decide("{\"result\":{\"tag\":\"c:metal_bits\"}}", AgeSnapshot.of(List.of(AgeId.AGE_2), 0, 0));
        check(v2.keep(), "one member unlocked -> kept");
        GateRules.Verdict fluid = decide("{\"result_fluid\":{\"tag\":\"c:hot\"}}", DAWN);
        check(!fluid.keep() && "age_4".equals(fluid.bucket()), "fluid tag with only lava locked: " + fluid);
        return "ok";
    }

    private static String tagMixed() {
        GateRules.Verdict v = decide("{\"result\":{\"basePredicate\":{\"tag\":\"c:ingots\"},\"count\":2}}", DAWN);
        check(v.keep(), "c:ingots has an untagged member (netherite) -> kept");
        return "ok";
    }

    private static String tagEmpty() {
        check(decide("{\"result\":{\"tag\":\"c:empty\"}}", DAWN).keep(), "empty tag keeps");
        check(decide("{\"result\":{\"tag\":\"c:unknown\"}}", DAWN).keep(), "unknown tag keeps (the recipe outputs nothing)");
        return "ok";
    }

    private static String disabled() {
        GateRules.Verdict v = decide("{\"result\":{\"id\":\"minecraft:bedrock\"}}", ALL);
        check(!v.keep(), "disabled output dropped with every Age unlocked");
        checkEquals(GateRules.DISABLED, v.bucket(), "bucket");
        GateRules.Verdict f = decide("{\"result_fluid\":{\"id\":\"firmages:void\",\"amount\":1}}", ALL);
        check(!f.keep() && GateRules.DISABLED.equals(f.bucket()), "disabled fluid dropped");
        GateRules.Verdict mixed = decide("{\"results\":[{\"id\":\"minecraft:gold_ingot\"},{\"id\":\"minecraft:bedrock\"}]}", DAWN);
        checkEquals(GateRules.DISABLED, mixed.bucket(), "disabled sorts after every Age");
        check(GateRules.bucketRank(GateRules.DISABLED) > GateRules.bucketRank("age_9"), "rank");
        return "ok";
    }

    private static String anyIdFluid() {
        GateRules.Verdict v = decide("{\"results\":[{\"id\":\"minecraft:lava\",\"amount\":50}]}", DAWN);
        check(!v.keep() && "age_4".equals(v.bucket()), "Create-style fluid result (id + amount) checked as a fluid: " + v);
        return "ok";
    }

    private static String undetected() {
        GateRules.Verdict v = decide("{\"type\":\"minecraft:crafting_special_firework_rocket\",\"category\":\"misc\"}", DAWN);
        checkEquals(GateRules.Reason.UNDETECTED, v.reason(), "no output");
        check(v.keep(), "undetected recipes are kept");
        return "ok";
    }

    private static String report() {
        GateReport rep = new GateReport(Instant.parse("2026-09-30T12:00:00Z"), List.of("dawn"), true, false, "test");
        rep.record("a:iron", "minecraft:smelting", "minecraft:smelting", decide("{\"result\":{\"id\":\"minecraft:iron_ingot\"}}", DAWN), "iron");
        rep.record("a:gold", "minecraft:smelting", "minecraft:smelting", decide("{\"result\":{\"id\":\"minecraft:gold_ingot\"}}", DAWN), "gold");
        rep.record("a:stone", "minecraft:smelting", "minecraft:smelting", decide("{\"result\":{\"id\":\"minecraft:stone\"}}", DAWN), "stone");
        rep.record("a:rocket", "minecraft:crafting", "minecraft:crafting_special_firework_rocket", decide("{}", DAWN), "(none)");
        rep.finish(5);
        checkEquals(4, rep.total(), "total");
        checkEquals(2, rep.dropped(), "dropped");
        checkEquals(1, rep.undetected(), "undetected");
        checkEquals(Map.of("age_2", 1, "age_5", 1), Map.copyOf(rep.droppedPerBucket()), "per Age");
        checkEquals(List.of("minecraft:crafting"), rep.typesWithoutDetectedOutput(), "types without output");
        checkEquals(List.of("minecraft:crafting_special_firework_rocket"), rep.serializersWithoutDetectedOutput(), "serializers without output");
        checkEquals(GateRules.Reason.LOCKED, rep.entry("a:iron").verdict().reason(), "why");
        String text = rep.toText();
        check(text.contains("a:rocket") && text.contains("age_5") && text.contains("minecraft:smelting"), "audit text:\n" + text);
        return rep.summary();
    }

    // ---------------------------------------------------------------- classification (Age swap)

    /** Heater age_2, Blaze Burner age_3 (as in the pack). */
    static final GateRules.Lookup HEAT_LOOKUP = new GateRules.Lookup() {
        final Map<String, AgeId> items = Map.of("tfcreate:primitive_heater", AgeId.AGE_2, "create:blaze_burner", AgeId.AGE_3);

        public AgeId itemAge(String id) { return items.containsKey(id) ? items.get(id) : LOOKUP.itemAge(id); }
        public AgeId fluidAge(String id) { return LOOKUP.fluidAge(id); }
        public boolean itemDisabled(String id) { return LOOKUP.itemDisabled(id); }
        public boolean fluidDisabled(String id) { return LOOKUP.fluidDisabled(id); }
        public Collection<String> itemTag(String t) { return LOOKUP.itemTag(t); }
        public Collection<String> fluidTag(String t) { return LOOKUP.fluidTag(t); }
    };

    static GateRules.Stations stations(Map<String, AgeId> byType, Map<String, Set<AgeId>> require, Map<String, Set<AgeId>> until) {
        return GateRules.Stations.of(new dev.firmages.core.gate.RecipeMarkers.Snapshot(byType, require, until, List.of()), HEAT_LOOKUP);
    }

    static GateRules.Classification classify(String id, String type, String heat, String json, GateRules.Stations st) {
        return GateRules.classify(id, type, type, heat, walk(json), DEFAULTS, HEAT_LOOKUP, st);
    }

    static AgeSnapshot upTo(AgeId last) {
        return AgeSnapshot.of(AgeId.all().subList(0, last.ordinal() + 1), 0, 0);
    }

    /** The fast keep() equals verdict().keep() for all 1,024 Age sets (dawn always set). */
    private static String keepEqualsVerdict() {
        GateRules.Stations st = stations(Map.of("t:machine", AgeId.AGE_3), Map.of("t:r", Set.of(AgeId.AGE_6)), Map.of("t:r", Set.of(AgeId.AGE_8)));
        List<GateRules.Classification> cases = List.of(
            classify("t:r", "t:machine", null, "{\"results\":[{\"id\":\"minecraft:iron_ingot\"},{\"tag\":\"c:metal_bits\"}]}", st),
            classify("t:x", "create:mixing", "heated", "{\"result\":{\"id\":\"minecraft:gold_ingot\"}}", st),
            classify("t:y", "t:other", null, "{\"result\":{\"id\":\"minecraft:bedrock\"}}", st),
            classify("t:z", "t:machine", null, "{}", st));
        int checked = 0;
        for (int m = 0; m < (1 << 10); m++) {
            int mask = 1 | (m << 1);
            for (GateRules.Classification c : cases) {
                checkEquals(c.verdict(mask).keep(), c.keep(mask), "mask " + Integer.toBinaryString(mask) + " " + c);
                checked++;
            }
        }
        return checked + " checks";
    }

    private static String stationByTypeOrSerializer() {
        GateRules.Stations st = stations(Map.of("ie:crusher", AgeId.AGE_4), Map.of(), Map.of());
        GateRules.Classification byType = GateRules.classify("p:a", "ie:crusher", "ie:crusher", null, walk("{\"result\":\"minecraft:stone\"}"), DEFAULTS, LOOKUP, st);
        GateRules.Classification bySer = GateRules.classify("p:b", "mek:crushing", "ie:crusher", null, walk("{\"result\":\"minecraft:stone\"}"), DEFAULTS, LOOKUP, st);
        GateRules.Classification other = GateRules.classify("p:c", "mek:crushing", "mek:crushing", null, walk("{\"result\":\"minecraft:stone\"}"), DEFAULTS, LOOKUP, st);
        for (GateRules.Classification c : List.of(byType, bySer)) {
            GateRules.Verdict v = c.verdict(upTo(AgeId.AGE_3));
            check(!v.keep() && "age_4".equals(v.bucket()) && v.entry().startsWith("station "), "station locks until age_4: " + v);
            check(c.verdict(upTo(AgeId.AGE_4)).keep(), "kept with age_4");
        }
        check(other.verdict(DAWN).keep(), "another type is not touched");
        GateRules.Verdict undetected = GateRules.classify("p:d", "ie:crusher", "ie:crusher", null, walk("{}"), DEFAULTS, LOOKUP, st).verdict(DAWN);
        check(!undetected.keep() && undetected.reason() == GateRules.Reason.LOCKED, "a station locks a recipe without detected outputs too: " + undetected);
        return "ok";
    }

    private static String requireAgeAddsUp() {
        GateRules.Stations st = stations(Map.of(), Map.of("p:ladder", Set.of(AgeId.AGE_7, AgeId.AGE_5)), Map.of());
        GateRules.Classification c = classify("p:ladder", "t:any", null, "{\"result\":{\"id\":\"minecraft:iron_ingot\"}}", st);
        checkEquals("age_7", c.verdict(upTo(AgeId.AGE_6)).bucket(), "waits for the latest required Age");
        check(!c.verdict(AgeSnapshot.of(List.of(AgeId.AGE_7), 0, 0)).keep(), "age_7 alone is not enough (age_2 output, age_5)");
        check(c.verdict(upTo(AgeId.AGE_7)).keep(), "kept with every required Age and the output's");
        check(classify("p:other", "t:any", null, "{\"result\":{\"id\":\"minecraft:iron_ingot\"}}", st).verdict(upTo(AgeId.AGE_2)).keep(),
            "markers name one recipe id only");
        return "ok";
    }

    private static String untilAgeSupersedes() {
        GateRules.Stations st = stations(Map.of(), Map.of(), Map.of("p:copy", Set.of(AgeId.AGE_2)));
        GateRules.Classification c = classify("p:copy", "t:any", null, "{\"result\":{\"id\":\"minecraft:stone\"}}", st);
        check(c.verdict(upTo(AgeId.AGE_1)).keep(), "present while age_2 is locked");
        GateRules.Verdict v = c.verdict(upTo(AgeId.AGE_2));
        check(!v.keep() && v.reason() == GateRules.Reason.SUPERSEDED && GateRules.SUPERSEDED.equals(v.bucket()), "gone once age_2 is unlocked: " + v);
        check(GateRules.bucketRank(GateRules.SUPERSEDED) > GateRules.bucketRank(GateRules.DISABLED), "superseded sorts last");
        return "ok";
    }

    /**
     * byproducts.js for create:crushing/tuff (iron nugget age_2, electrum nugget age_4): the copy without both, the copy
     * with the nugget, the original. For every run of Ages exactly one of them is live.
     */
    private static String byproductCopies() {
        String main = "{\"id\":\"minecraft:stone\"}";
        GateRules.Stations st = stations(Map.of(), Map.of(), Map.of("p:before_age_2", Set.of(AgeId.AGE_2), "p:before_age_4", Set.of(AgeId.AGE_4)));
        GateRules.Classification none = classify("p:before_age_2", "create:crushing", null, "{\"results\":[" + main + "]}", st);
        GateRules.Classification nugget = classify("p:before_age_4", "create:crushing", null,
            "{\"results\":[" + main + ",{\"id\":\"minecraft:iron_nugget\",\"chance\":0.1}]}", st);
        GateRules.Classification original = classify("p:original", "create:crushing", null,
            "{\"results\":[" + main + ",{\"id\":\"minecraft:iron_nugget\",\"chance\":0.1},{\"id\":\"minecraft:lava\",\"amount\":1}]}", st);
        List<String> live = new java.util.ArrayList<>();
        for (AgeId last : AgeId.all()) {
            AgeSnapshot snap = upTo(last);
            int n = 0;
            String which = "";
            for (Map.Entry<String, GateRules.Classification> e : Map.of("none", none, "nugget", nugget, "original", original).entrySet()) {
                if (e.getValue().verdict(snap).keep()) {
                    n++;
                    which = e.getKey();
                }
            }
            checkEquals(1, n, "exactly one variant live up to " + last);
            live.add(last.id() + "=" + which);
        }
        checkEquals("none", live.get(AgeId.AGE_1.ordinal()).split("=")[1], "age_1");
        checkEquals("nugget", live.get(AgeId.AGE_2.ordinal()).split("=")[1], "age_2");
        checkEquals("original", live.get(AgeId.AGE_4.ordinal()).split("=")[1], "age_4 (lava stands in for the age_4 byproduct)");
        return String.join(", ", live);
    }

    private static String heatLevels() {
        GateRules.Stations st = stations(Map.of(), Map.of(), Map.of());
        String out = "{\"results\":[{\"id\":\"minecraft:stone\"}]}";
        GateRules.Classification heated = classify("c:h", "create:mixing", "heated", out, st);
        GateRules.Classification superheated = classify("c:s", "create:compacting", "superheated", out, st);
        GateRules.Classification none = classify("c:n", "create:mixing", null, out, st);
        GateRules.Classification otherType = classify("c:o", "woodencog:heated_mixing", "heated", out, st);
        GateRules.Verdict h1 = heated.verdict(upTo(AgeId.AGE_1));
        check(!h1.keep() && "age_2".equals(h1.bucket()) && "heat heated".equals(h1.entry()), "heated waits for the heater (age_2): " + h1);
        check(heated.verdict(upTo(AgeId.AGE_2)).keep(), "heated with the heater");
        check(heated.verdict(AgeSnapshot.of(List.of(AgeId.AGE_3), 0, 0)).keep(), "heated with a Blaze Burner alone");
        GateRules.Verdict s2 = superheated.verdict(upTo(AgeId.AGE_2));
        check(!s2.keep() && "age_3".equals(s2.bucket()), "superheated waits for the Blaze Burner (age_3): " + s2);
        check(superheated.verdict(upTo(AgeId.AGE_3)).keep(), "superheated with the Blaze Burner");
        check(none.verdict(DAWN).keep() && otherType.verdict(DAWN).keep(), "unheated basin recipes and other types are not touched");
        GateRules.Classification noCreate = GateRules.classify("c:h", "create:mixing", "create:mixing", "heated", walk(out), DEFAULTS, LOOKUP,
            GateRules.Stations.of(dev.firmages.core.gate.RecipeMarkers.Snapshot.EMPTY, LOOKUP));
        check(noCreate.verdict(DAWN).keep(), "a heat source without an Age (mod absent) locks nothing");
        return "ok";
    }

    private static String configBeatsStations() {
        GateRules.Stations st = stations(Map.of("t:machine", AgeId.AGE_9), Map.of(), Map.of());
        GateRules.Settings allow = new GateRules.Settings(true, Set.of("t:r"), Set.of(), Set.of("t:world"));
        GateRules.Verdict a = GateRules.classify("t:r", "t:machine", "t:machine", null, walk("{}"), allow, LOOKUP, st).verdict(DAWN);
        GateRules.Verdict e = GateRules.classify("t:w", "t:world", "t:machine", null, walk("{}"), allow, LOOKUP, st).verdict(DAWN);
        check(a.keep() && a.reason() == GateRules.Reason.ALLOWED, "gate.allowRecipes keeps a recipe of a locked station: " + a);
        check(e.keep() && e.reason() == GateRules.Reason.EXEMPT, "an exempt type is never filtered: " + e);
        return "ok";
    }

    private static String bucketLatestUnmet() {
        GateRules.Stations st = stations(Map.of("t:machine", AgeId.AGE_6), Map.of(), Map.of());
        GateRules.Classification c = classify("t:r", "t:machine", null, "{\"result\":{\"id\":\"minecraft:iron_ingot\"}}", st);
        checkEquals("age_6", c.verdict(DAWN).bucket(), "station later than the output");
        GateRules.Verdict v = c.verdict(AgeSnapshot.of(List.of(AgeId.AGE_6), 0, 0));
        check("age_2".equals(v.bucket()) && "minecraft:iron_ingot".equals(v.entry()), "only the output unmet: " + v);
        return "ok";
    }

    private static String excavatorPick() {
        int iron = 0;
        for (int i = 0; i < 1000; i++) {
            String rolled = i % 2 == 0 ? "iron" : "stone";
            String out = OreRoll.pick(rolled, "iron"::equals, () -> "gravel");
            if (out.equals("iron")) iron++;
        }
        checkEquals(0, iron, "locked ore never passes");
        checkEquals("stone", OreRoll.pick("stone", "iron"::equals, () -> "gravel"), "unlocked ore passes");
        return "ok";
    }
}
