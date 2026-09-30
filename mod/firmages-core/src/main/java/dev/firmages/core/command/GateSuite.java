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
 * allow, exempt, locked, tag outputs, disabled, undetected), the report, the excavator pick. No Minecraft classes,
 * so JUnit ({@code GateSuiteTest}) and {@code /firmages selftest gate} run the same cases.
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
